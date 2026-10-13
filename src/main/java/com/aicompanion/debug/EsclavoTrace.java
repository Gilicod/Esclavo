package com.aicompanion.debug;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.TaskManager;
import com.aicompanion.ai.nav.NavigationAction;
import com.aicompanion.ai.nav.NavigationManager;
import com.aicompanion.ai.stuck.StuckDetector;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.inventory.CompanionInventoryHandler;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.CommandEvent;

public final class EsclavoTrace {
   private static final Object LOCK = new Object();
   private static final long MAX_BYTES = 41943040L;
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
   private static final int KEEP_SESSIONS = 30;
   private static BufferedWriter incidents;
   private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
   private static BufferedWriter writer;
   private static boolean failed = false;
   private static boolean limitReached = false;
   private static long bytes = 0L;
   private static long lastFlushMs = 0L;
   private static Path file;
   private static final Map<Integer, EsclavoTrace.CompanionWatch> WATCH = new HashMap<>();

   private EsclavoTrace() {
   }

   public static boolean isActive() {
      return !failed && !limitReached;
   }

   public static Path getFile() {
      return file;
   }

   private static void open() throws IOException {
      Path var0 = Paths.get(System.getProperty("user.dir", "."), "esclavo-logs");
      Files.createDirectories(var0);
      pruneOld(var0);
      String var1 = LocalDateTime.now().format(FILE_TIME);
      file = var0.resolve("esclavo-" + var1 + ".log");
      writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);

      try {
         incidents = Files.newBufferedWriter(var0.resolve("esclavo-" + var1 + "-incidentes.log"), StandardCharsets.UTF_8);
         incidents.write("=== Esclavo - incidentes (solo ordenes y problemas) ===\ninicio: " + LocalDateTime.now().format(TIME) + "\n\n");
         incidents.flush();
      } catch (IOException var5) {
         incidents = null;
      }

      String var2 = "=== Esclavo - registro de diagnostico ===\ninicio: "
         + LocalDateTime.now()
         + "\njava: "
         + System.getProperty("java.version")
         + "  carpeta: "
         + System.getProperty("user.dir")
         + "\nformato: [hora] [tick] [CATEGORIA] [nombre @x,y,z] mensaje\n\n";
      writer.write(var2);
      writer.flush();
      bytes = bytes + var2.length();

      try {
         Runtime.getRuntime().addShutdownHook(new Thread(EsclavoTrace::close, "esclavo-trace-close"));
      } catch (Exception var4) {
      }
   }

   private static void pruneOld(Path var0) {
      try (Stream<Path> var1 = Files.list(var0)) {
         List<Path> var2 = var1.filter(var0x -> {
            String var1x = var0x.getFileName().toString();
            return var1x.startsWith("esclavo-") && var1x.endsWith(".log") && !var1x.endsWith("-incidentes.log");
         }).sorted().collect(Collectors.toList());

         for (int var3 = 0; var3 < var2.size() - 29; var3++) {
            Path var4 = (Path)var2.get(var3);
            Files.deleteIfExists(var4);
            String var5 = var4.getFileName().toString();
            Files.deleteIfExists(var4.resolveSibling(var5.substring(0, var5.length() - 4) + "-incidentes.log"));
         }
      } catch (Throwable var8) {
      }
   }

   private static boolean isIncident(String var0, String var1) {
      if (isUrgent(var0) && !"CHAT-YO".equals(var0) && !"LOG-WARN".equals(var0)) {
         return true;
      } else if ("ORDEN-RESULTADO".equals(var0) || "SIN-PROGRESO".equals(var0) || "LLEGO".equals(var0) || "NAV-META".equals(var0) || "CHAT-YO".equals(var0)) {
         return true;
      } else if ("PICA".equals(var0) && var1 != null && var1.startsWith("CANCELADO")) {
         return true;
      } else if (var1 == null) {
         return false;
      } else if ("LOG-WARN".equals(var0) || "LOG-ERROR".equals(var0)) {
         return true;
      } else if ("LOG-INFO".equals(var0)) {
         return var1.contains("Task started")
            || var1.contains("Task completed")
            || var1.contains("executeNaturalOrder: text")
            || var1.contains("C2S_ActionPacket received")
            || var1.contains("setTask");
      } else {
         return "RUTA".equals(var0)
            ? var1.contains("resultado=IMPOSSIBLE") || var1.contains("motivo='atasco'")
            : "CHAT".equals(var0) && var1.contains("]") && !var1.startsWith("<");
      }
   }

   public static void close() {
      synchronized (LOCK) {
         if (incidents != null) {
            try {
               incidents.write(LocalDateTime.now().format(TIME) + " === fin ===\n");
               incidents.close();
            } catch (IOException var4) {
            }

            incidents = null;
         }

         if (writer != null) {
            try {
               writer.write("[" + LocalDateTime.now().format(TIME) + "] === fin del registro ===\n");
               writer.flush();
               writer.close();
            } catch (IOException var3) {
            }

            writer = null;
         }
      }
   }

   private static boolean isUrgent(String var0) {
      return "ORDEN".equals(var0)
         || "ELIMINADO".equals(var0)
         || "ATASCO".equals(var0)
         || "RENDIDO".equals(var0)
         || "FALLBACK".equals(var0)
         || "LOG-WARN".equals(var0)
         || "LOG-ERROR".equals(var0)
         || "COMANDO".equals(var0)
         || "CHAT-YO".equals(var0)
         || "QUIETO".equals(var0)
         || "ERROR".equals(var0);
   }

   public static void log(String var0, String var1) {
      write(var0, null, var1);
   }

   public static void log(String var0, CompanionEntity var1, String var2) {
      write(var0, var1, var2);
   }

   private static void write(String var0, CompanionEntity var1, String var2) {
      if (!failed && !limitReached) {
         StringBuilder var3 = new StringBuilder(160);
         var3.append('[').append(LocalDateTime.now().format(TIME)).append("] ");
         if (var1 != null) {
            var3.append('[').append(var1.tickCount).append("] ");
         } else {
            var3.append("[-] ");
         }

         var3.append('[').append(var0).append("] ");
         if (var1 != null) {
            var3.append('[').append(safeName(var1)).append(" @").append(fmt(var1.blockPosition())).append("] ");
         }

         var3.append(var2 == null ? "" : var2.replace('\n', ' ').replace('\r', ' ')).append('\n');
         String var4 = var3.toString();
         synchronized (LOCK) {
            try {
               if (writer == null) {
                  if (failed) {
                     return;
                  }

                  open();
               }

               if (bytes + var4.length() > 41943040L) {
                  limitReached = true;
                  writer.write("[" + LocalDateTime.now().format(TIME) + "] === LIMITE DE TAMANO ALCANZADO, se deja de registrar ===\n");
                  writer.flush();
               } else {
                  writer.write(var4);
                  bytes = bytes + var4.length();
                  boolean var6 = incidents != null && isIncident(var0, var2);
                  if (var6) {
                     incidents.write(var4);
                     bytes = bytes + var4.length();
                  }

                  long var7 = System.currentTimeMillis();
                  if (isUrgent(var0) || var6 || var7 - lastFlushMs > 1000L) {
                     writer.flush();
                     if (incidents != null) {
                        incidents.flush();
                     }

                     lastFlushMs = var7;
                  }

                  return;
               }
            } catch (Throwable var10) {
               failed = true;
               return;
            }
         }
      }
   }

   private static String safeName(CompanionEntity var0) {
      try {
         String var1 = var0.getCustomCompanionName();
         return var1 != null ? var1 : "Esclavo";
      } catch (Throwable var2) {
         return "Esclavo";
      }
   }

   public static String fmt(BlockPos var0) {
      return var0 == null ? "null" : var0.getX() + "," + var0.getY() + "," + var0.getZ();
   }

   private static String r1(double var0) {
      return String.valueOf(Math.round(var0 * 10.0) / 10.0);
   }

   public static void tickCompanion(CompanionEntity var0, NavigationManager var1) {
      if (isActive() && var0 != null) {
         try {
            int var2 = var0.tickCount;
            EsclavoTrace.CompanionWatch var3 = WATCH.computeIfAbsent(var0.getId(), var0x -> new EsclavoTrace.CompanionWatch());
            if (!var3.announced) {
               var3.announced = true;
               log("INICIO", var0, "Esclavo cargado en el mundo. id=" + var0.getId() + " dim=" + var0.level().dimension().location());
            }

            BlockPos var4 = var0.blockPosition();
            if (var3.lastMovePos == null || var4.distSqr(var3.lastMovePos) > 1.0) {
               var3.lastMovePos = var4;
               var3.lastMoveTick = var2;
               var3.stillReported = false;
            }

            if (var2 % 20 == 0) {
               TaskManager var5 = var0.getCompanionBrain() != null ? var0.getCompanionBrain().getTaskManager() : null;
               CompanionTask var6 = var5 != null ? var5.getCurrentTask() : null;
               String var7 = var6 != null ? String.valueOf(var6.getTaskType()) : "";
               StringBuilder var8 = new StringBuilder(200);
               var8.append("estado=").append(var0.getCurrentState());
               if (var6 != null) {
                  var8.append(" tarea='").append(var6.getDescription()).append("'");
                  var8.append(" tipo=").append(var7);
                  var8.append(" etapa=").append(var6.getStage());
                  var8.append(" sub='").append(var6.getSubstate()).append("'");
                  var8.append(" prog='").append(var6.getProgress()).append("'");
                  if (var6.getFailureReason() != null && !var6.getFailureReason().isEmpty()) {
                     var8.append(" fallo='").append(var6.getFailureReason()).append("'");
                  }

                  if (var5.isPaused()) {
                     var8.append(" PAUSADO");
                  }
               } else {
                  var8.append(" tarea=ninguna");
               }

               try {
                  if (var5 != null && var5.getTaskQueue() != null && var5.getTaskQueue().getTasks() != null) {
                     var8.append(" cola=").append(var5.getTaskQueue().getTasks().size());
                  }
               } catch (Throwable var16) {
               }

               if (var1 != null) {
                  var8.append(" | nav: ").append(var1.getDebugStats());

                  try {
                     if (var1.getBaritoneProvider() != null && var1.getBaritoneProvider().getExecutor() != null) {
                        NavigationAction var9 = var1.getBaritoneProvider().getExecutor().getCurrentAction();
                        if (var9 != null) {
                           var8.append(" accion=").append(var9);
                        }
                     }
                  } catch (Throwable var15) {
                  }
               }

               var8.append(" | vida=").append(r1(var0.getHealth())).append('/').append(r1(var0.getMaxHealth()));
               var8.append(" suelo=").append(var0.onGround()).append(" agua=").append(var0.isInWater());
               ItemStack var18 = var0.getMainHandItem();
               var8.append(" mano=").append(var18.isEmpty() ? "vacia" : var18.getItem().toString());

               try {
                  if (var0.getStuckDetector() != null) {
                     StuckDetector.RecoveryPhase var10 = var0.getStuckDetector().getCurrentRecoveryPhase();
                     if (var10 != null && !"DETECTAR_ATASCO".equals(var10.toString())) {
                        var8.append(" | rescate=").append(var10);
                     }
                  }
               } catch (Throwable var14) {
               }

               String var19 = var8.toString();
               boolean var11 = var3.lastSnapshotPos == null || !var3.lastSnapshotPos.equals(var4);
               if (!var19.equals(var3.lastSnapshot) || var2 - var3.lastSnapshotTick >= 100 || var11 && var2 - var3.lastSnapshotTick >= 20) {
                  log("ESTADO", var0, var19);
                  var3.lastSnapshot = var19;
                  var3.lastSnapshotTick = var2;
                  var3.lastSnapshotPos = var4;
               }

               boolean var12 = var6 == null || "FOLLOW".equalsIgnoreCase(var7) || "STAY".equalsIgnoreCase(var7);
               boolean var13 = false;
               if ("FOLLOW".equalsIgnoreCase(var7) && var0.getOwnerPlayer() != null && var0.getOwnerPlayer().level() == var0.level()) {
                  var13 = var0.distanceToSqr(var0.getOwnerPlayer()) > 64.0;
               }

               if ((!var12 || var13) && !var3.stillReported && var2 - var3.lastMoveTick >= 200) {
                  var3.stillReported = true;
                  log("QUIETO", var0, "Lleva " + (var2 - var3.lastMoveTick) / 20 + " s sin moverse ni 1 bloque con tarea activa. " + var19);
               }

               inventoryDiff(var0, var3);
            }
         } catch (Throwable var17) {
            log("ERROR", "tickCompanion: " + var17);
         }
      }
   }

   private static void inventoryDiff(CompanionEntity var0, EsclavoTrace.CompanionWatch var1) {
      CompanionInventoryHandler var2 = var0.getInventoryHandler();
      if (var2 != null) {
         HashMap<String, Integer> var3 = new HashMap<>();

         for (int var4 = 0; var4 < 36; var4++) {
            ItemStack var5;
            try {
               var5 = var2.getStackInSlot(var4);
            } catch (Throwable var10) {
               break;
            }

            if (var5 != null && !var5.isEmpty()) {
               var3.merge(var5.getItem().toString(), var5.getCount(), Integer::sum);
            }
         }

         if (var1.inventory == null) {
            var1.inventory = var3;
            log("INVENTARIO", var0, "inicial: " + (var3.isEmpty() ? "vacio" : new TreeMap(var3).toString()));
         } else {
            StringBuilder var11 = new StringBuilder();
            TreeSet<String> var12 = new TreeSet<>(var3.keySet());
            var12.addAll(var1.inventory.keySet());

            for (String var7 : var12) {
               int var8 = var1.inventory.getOrDefault(var7, 0);
               int var9 = var3.getOrDefault(var7, 0);
               if (var8 != var9) {
                  if (var11.length() > 0) {
                     var11.append(", ");
                  }

                  var11.append(var9 > var8 ? "+" : "").append(var9 - var8).append(' ').append(var7);
               }
            }

            if (var11.length() > 0) {
               log("INVENTARIO", var0, var11.toString());
            }

            var1.inventory = var3;
         }
      }
   }

   public static void onCommand(CommandEvent var0) {
      try {
         // 1.13.9: solo comandos escritos por jugadores; los mods (p. ej. the_obsessed) lanzan /fill cada tick
         // y llenaban el registro (y forzaban escritura a disco en cada uno).
         if (!(var0.getParseResults().getContext().getSource().getEntity() instanceof net.minecraft.world.entity.player.Player)) {
            return;
         }
         String var1 = var0.getParseResults().getReader().getString();
         log("COMANDO", "/" + var1);
      } catch (Throwable var2) {
      }
   }

   static String summarize(Map<String, Integer> var0) {
      StringBuilder var1 = new StringBuilder();

      for (Entry var3 : var0.entrySet()) {
         var1.append((String)var3.getKey()).append('=').append(var3.getValue()).append(' ');
      }

      return var1.toString().trim();
   }

   private static final class CompanionWatch {
      boolean announced;
      String lastSnapshot;
      int lastSnapshotTick = -1000;
      BlockPos lastSnapshotPos;
      BlockPos lastMovePos;
      int lastMoveTick;
      boolean stillReported;
      Map<String, Integer> inventory;
   }
}
