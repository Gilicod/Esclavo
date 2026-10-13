package com.aicompanion.ai;

import com.aicompanion.debug.EsclavoTrace;
import com.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;

public class TaskProgressWatchdog {
   private final CompanionEntity companion;
   private long lastProgressTime = 0L;
   private String lastStage = "";
   private BlockPos lastPos = BlockPos.ZERO;
   private int stallCount = 0;
   private static final long MAX_STALL_TICKS = 300L;

   public TaskProgressWatchdog(CompanionEntity var1) {
      this.companion = var1;
      this.lastProgressTime = var1 != null ? var1.tickCount : 0L;
   }

   public void tick(CompanionTask var1) {
      if (var1 == null || var1.isComplete() || var1.isPaused()) {
         this.stallCount = 0;
         this.lastProgressTime = this.companion.tickCount;
      } else if (!"FOLLOW".equalsIgnoreCase(var1.getTaskType()) && !"STAY".equalsIgnoreCase(var1.getTaskType())) {
         BlockPos var2 = this.companion.blockPosition();
         String var3 = var1.getStage();
         if (!var3.equals(this.lastStage) || var2.distSqr(this.lastPos) > 4.0 || var1.getLastProgressTick() > this.lastProgressTime) {
            this.lastProgressTime = this.companion.tickCount;
            this.lastStage = var3;
            this.lastPos = var2;
            this.stallCount = 0;
         } else if (this.companion.tickCount - this.lastProgressTime > 300L) {
            this.stallCount++;
            this.lastProgressTime = this.companion.tickCount;
            EsclavoTrace.log(
               "SIN-PROGRESO",
               this.companion,
               "La tarea '"
                  + var1.getDescription()
                  + "' no avanza hace 15 s (etapa="
                  + var3
                  + ", sub='"
                  + var1.getSubstate()
                  + "'). intento "
                  + this.stallCount
                  + "/3"
                  + (this.stallCount > 3 ? " -> SE CANCELA LA TAREA" : " -> recalcula ruta")
            );
            if (this.stallCount <= 3) {
               if (this.companion.getStuckDetector() != null) {
                  this.companion.getStuckDetector().notifyStalled(var1);
               }

               if (this.companion.getNavManager() != null) {
                  this.companion.getNavManager().recomputePath();
               }
            } else {
               var1.fail("Sin progreso tras varios intentos (" + var3 + ").");
               this.stallCount = 0;
            }
         }
      } else {
         this.stallCount = 0;
      }
   }
}
