package com.aicompanion.command;

import com.aicompanion.ai.building.BlueprintRegistry;
import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.ai.parser.SpanishAliases;
import com.aicompanion.ai.tasks.*;
import com.aicompanion.data.CompanionManager;
import com.aicompanion.data.CompanionSavedData;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.DefaultBehavior;
import com.aicompanion.inventory.EquipmentManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CompanionCommand {
    // Per-player delete confirmation with 30s timeout (Requirement 45)
    private static final Map<UUID, Long> pendingDeletes = new ConcurrentHashMap<>();

    private static final List<String> ORE_SUGGESTIONS = Arrays.asList(
            "coal", "carbon", "carbón", "iron", "hierro", "gold", "oro",
            "diamond", "diamante", "emerald", "esmeralda", "copper", "cobre",
            "redstone", "lapis", "lapislazuli", "netherite", "ancient_debris"
    );

    private static final List<String> WOOD_SUGGESTIONS = Arrays.asList(
            "oak", "roble", "birch", "abedul", "spruce", "abeto",
            "jungle", "jungla", "acacia", "dark_oak", "roble_oscuro",
            "mangrove", "mangle", "cherry", "cerezo"
    );

    private static final List<String> MOB_SUGGESTIONS = Arrays.asList(
            "zombie", "skeleton", "esqueleto", "creeper", "spider", "arana", "araña", "enderman", "witch", "bruja"
    );

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ORES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ORE_SUGGESTIONS, builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_WOODS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(WOOD_SUGGESTIONS, builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_MOBS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(MOB_SUGGESTIONS, builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BLUEPRINTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(BlueprintRegistry.getAvailable(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("companion")
                // /companion summon
                .then(Commands.literal("summon")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            CompanionEntity active = CompanionManager.getCompanionForOwner(player.getServer(), player.getUUID());
                            if (active != null) {
                                ctx.getSource().sendFailure(Component.translatable("message.aicompanion.already_active").withStyle(ChatFormatting.YELLOW));
                                return 0;
                            }
                            if (CompanionSavedData.get(player.serverLevel()).hasSavedCompanion(player.getUUID())) {
                                CompanionEntity restored = CompanionManager.restoreCompanion(player);
                                if (restored != null) {
                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.aicompanion.restored").withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                }
                            } else {
                                CompanionEntity summoned = CompanionManager.summonNewCompanion(player, "Esclavo");
                                if (summoned != null) {
                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.aicompanion.summoned").withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                }
                            }
                            return 0;
                        }))

                // /companion dismiss
                .then(Commands.literal("dismiss")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (CompanionManager.dismissCompanion(player)) {
                                ctx.getSource().sendSuccess(() -> Component.translatable("message.aicompanion.dismissed").withStyle(ChatFormatting.YELLOW), false);
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.translatable("message.aicompanion.not_found").withStyle(ChatFormatting.RED));
                            return 0;
                        }))

                // /companion recall
                .then(Commands.literal("recall")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (CompanionManager.recallCompanion(player)) {
                                ctx.getSource().sendSuccess(() -> Component.translatable("message.aicompanion.recalled").withStyle(ChatFormatting.GREEN), false);
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.translatable("message.aicompanion.no_safe_spawn").withStyle(ChatFormatting.RED));
                            return 0;
                        }))

                // /companion follow
                .then(Commands.literal("follow")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            CompanionEntity comp = getComp(player);
                            if (comp == null) return 0;
                            comp.setPreferredIdleBehavior(DefaultBehavior.FOLLOW);
                            comp.getCompanionBrain().getTaskManager().setTask(new FollowTask(comp));
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.aicompanion.following").withStyle(ChatFormatting.GREEN), false);
                            return 1;
                        }))

                // /companion stay
                .then(Commands.literal("stay")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            CompanionEntity comp = getComp(player);
                            if (comp == null) return 0;
                            comp.setPreferredIdleBehavior(DefaultBehavior.STAY);
                            comp.getCompanionBrain().getTaskManager().setTask(new StayTask(comp));
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.aicompanion.staying").withStyle(ChatFormatting.YELLOW), false);
                            return 1;
                        }))

                // /companion stop
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            CompanionEntity comp = getComp(player);
                            if (comp == null) return 0;
                            comp.getCompanionBrain().getTaskManager().stop();
                            comp.returnToDefaultBehavior();
                            ctx.getSource().sendSuccess(() -> Component.literal("Tarea cancelada. Esclavo vuelve a su comportamiento habitual.").withStyle(ChatFormatting.YELLOW), false);
                            return 1;
                        }))

                // /companion pause
                .then(Commands.literal("pause")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            CompanionEntity comp = getComp(player);
                            if (comp == null) return 0;
                            comp.getCompanionBrain().getTaskManager().pause();
                            ctx.getSource().sendSuccess(() -> Component.literal("Tarea de Esclavo pausada.").withStyle(ChatFormatting.YELLOW), false);
                            return 1;
                        }))

                // /companion resume
                .then(Commands.literal("resume")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            CompanionEntity comp = getComp(player);
                            if (comp == null) return 0;
                            comp.getCompanionBrain().getTaskManager().resume();
                            ctx.getSource().sendSuccess(() -> Component.literal("Tarea de Esclavo reanudada.").withStyle(ChatFormatting.GREEN), false);
                            return 1;
                        }))

                // /companion home
                .then(Commands.literal("home")
                        .then(Commands.literal("set")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity comp = getComp(player);
                                    if (comp == null) return 0;
                                    comp.setHomePos(player.blockPosition(), player.serverLevel().dimension());
                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.aicompanion.home_set").withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            CompanionEntity comp = getComp(player);
                            if (comp == null) return 0;
                            if (comp.getHomePos() != null) {
                                comp.getCompanionBrain().getTaskManager().setTask(new ReturnTask(comp, true));
                                ctx.getSource().sendSuccess(() -> Component.literal("Esclavo regresa a su base.").withStyle(ChatFormatting.GREEN), false);
                                return 1;
                            } else {
                                ctx.getSource().sendFailure(Component.translatable("message.aicompanion.no_home").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                        }))

                // /companion mine <mineral> [cantidad]
                .then(Commands.literal("mine")
                        .then(Commands.argument("mineral", StringArgumentType.word())
                                .suggests(SUGGEST_ORES)
                                .executes(ctx -> executeMine(ctx, 10))
                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 6400))
                                        .executes(ctx -> executeMine(ctx, IntegerArgumentType.getInteger(ctx, "cantidad"))))))

                // /companion chop <madera> [cantidad]
                .then(Commands.literal("chop")
                        .then(Commands.argument("madera", StringArgumentType.word())
                                .suggests(SUGGEST_WOODS)
                                .executes(ctx -> executeChop(ctx, 20))
                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 6400))
                                        .executes(ctx -> executeChop(ctx, IntegerArgumentType.getInteger(ctx, "cantidad"))))))

                // /companion kill <mob> [cantidad]
                .then(Commands.literal("kill")
                        .then(Commands.argument("mob", StringArgumentType.word())
                                .suggests(SUGGEST_MOBS)
                                .executes(ctx -> executeKill(ctx, 5))
                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> executeKill(ctx, IntegerArgumentType.getInteger(ctx, "cantidad"))))))

                // /companion build <blueprint>
                .then(Commands.literal("build")
                        .then(Commands.argument("blueprint", StringArgumentType.word())
                                .suggests(SUGGEST_BLUEPRINTS)
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity comp = getComp(player);
                                    if (comp == null) return 0;
                                    String bp = StringArgumentType.getString(ctx, "blueprint");
                                    comp.getCompanionBrain().getTaskManager().setTask(new BuildTask(comp, bp, comp.blockPosition()));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Esclavo comenzará a construir " + bp + ".").withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                })))

                // /companion goto <x> <y> <z>
                .then(Commands.literal("goto")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity comp = getComp(player);
                                    if (comp == null) return 0;
                                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                    comp.getCompanionBrain().getTaskManager().setTask(new GotoTask(comp, pos));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Esclavo viajando a " + pos.toShortString() + ".").withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                })))

                // /companion deposit
                .then(Commands.literal("deposit")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            CompanionEntity comp = getComp(player);
                            if (comp == null) return 0;
                            comp.getCompanionBrain().getTaskManager().setTask(new DepositTask(comp));
                            ctx.getSource().sendSuccess(() -> Component.literal("Esclavo buscará un cofre cercano para depositar.").withStyle(ChatFormatting.GREEN), false);
                            return 1;
                        }))

                // /companion withdraw <item> [cantidad]
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .executes(ctx -> executeWithdraw(ctx, 1))
                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 6400))
                                        .executes(ctx -> executeWithdraw(ctx, IntegerArgumentType.getInteger(ctx, "cantidad"))))))

                // /companion queue
                .then(Commands.literal("queue")
                        .then(Commands.literal("clear")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity comp = getComp(player);
                                    if (comp == null) return 0;
                                    comp.getCompanionBrain().getTaskManager().getTaskQueue().clear();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.aicompanion.queue_cleared").withStyle(ChatFormatting.YELLOW), false);
                                    return 1;
                                }))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity comp = getComp(player);
                                    if (comp == null) return 0;
                                    var tasks = comp.getCompanionBrain().getTaskManager().getTaskQueue().getTasks();
                                    if (tasks.isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("La cola de tareas está vacía.").withStyle(ChatFormatting.GRAY), false);
                                    } else {
                                        ctx.getSource().sendSuccess(() -> Component.literal("=== Cola de Tareas (" + tasks.size() + ") ===").withStyle(ChatFormatting.GOLD), false);
                                        for (int i = 0; i < tasks.size(); i++) {
                                            int idx = i;
                                            ctx.getSource().sendSuccess(() -> Component.literal((idx + 1) + ". " + tasks.get(idx).getDescription()).withStyle(ChatFormatting.WHITE), false);
                                        }
                                    }
                                    return 1;
                                })))

                // /companion name <nombre> / /companion rename <nombre> / /companion nombre <nombre>
                .then(Commands.literal("name")
                        .then(Commands.argument("nuevo_nombre", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity comp = getComp(player);
                                    if (comp == null) return 0;
                                    String newName = StringArgumentType.getString(ctx, "nuevo_nombre");
                                    comp.setCustomCompanionName(newName);
                                    CompanionSavedData.get(player.serverLevel()).saveCompanion(comp);
                                    comp.syncToOwner();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Nombre cambiado a: " + newName).withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                })))
                .then(Commands.literal("rename")
                        .then(Commands.argument("nuevo_nombre", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity comp = getComp(player);
                                    if (comp == null) return 0;
                                    String newName = StringArgumentType.getString(ctx, "nuevo_nombre");
                                    comp.setCustomCompanionName(newName);
                                    CompanionSavedData.get(player.serverLevel()).saveCompanion(comp);
                                    comp.syncToOwner();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Nombre cambiado a: " + newName).withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                })))
                .then(Commands.literal("nombre")
                        .then(Commands.argument("nuevo_nombre", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity comp = getComp(player);
                                    if (comp == null) return 0;
                                    String newName = StringArgumentType.getString(ctx, "nuevo_nombre");
                                    comp.setCustomCompanionName(newName);
                                    CompanionSavedData.get(player.serverLevel()).saveCompanion(comp);
                                    comp.syncToOwner();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Nombre cambiado a: " + newName).withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                })))

                // /companion gui
                .then(Commands.literal("gui")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            CompanionEntity comp = getComp(player);
                            if (comp == null) return 0;
                            comp.openCompanionMenu(player);
                            return 1;
                        }))

                // /companion status
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            CompanionEntity comp = getComp(player);
                            if (comp == null) return 0;
                            ctx.getSource().sendSuccess(() -> Component.literal("=== Estado de " + comp.getCustomCompanionName() + " ===").withStyle(ChatFormatting.GOLD), false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Estado: " + comp.getCurrentState().getDisplayName()).withStyle(ChatFormatting.YELLOW), false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Comportamiento: " + comp.getPreferredIdleBehavior().getDisplayName()).withStyle(ChatFormatting.WHITE), false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Nivel: " + comp.getCompanionLevel() + " | Salud: " + (int)comp.getHealth() + "/20").withStyle(ChatFormatting.GREEN), false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Posición: " + comp.blockPosition().toShortString()).withStyle(ChatFormatting.GRAY), false);
                            return 1;
                        }))

                // /companion delete [confirm]
                .then(Commands.literal("delete")
                        .then(Commands.literal("confirm")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    Long expiry = pendingDeletes.remove(player.getUUID());
                                    if (expiry == null || System.currentTimeMillis() > expiry) {
                                        ctx.getSource().sendFailure(Component.translatable("message.aicompanion.delete_expired").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    CompanionEntity active = CompanionManager.getCompanionForOwner(player.getServer(), player.getUUID());
                                    if (active != null) {
                                        CompanionManager.unregisterCompanion(active);
                                        active.discard();
                                    }
                                    CompanionSavedData.get(player.serverLevel()).deleteCompanion(player.getUUID());
                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.aicompanion.deleted").withStyle(ChatFormatting.RED), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            pendingDeletes.put(player.getUUID(), System.currentTimeMillis() + 30000L);
                            ctx.getSource().sendFailure(Component.translatable("message.aicompanion.delete_confirm").withStyle(ChatFormatting.YELLOW));
                            return 0;
                        }))

                // /companion debug ...
                .then(Commands.literal("debug")
                        .then(Commands.literal("status")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity comp = getComp(player);
                                    if (comp == null) return 0;
                                    var tm = comp.getCompanionBrain().getTaskManager();
                                    var current = tm.getCurrentTask();
                                    ctx.getSource().sendSuccess(() -> Component.literal("=== DEBUG STATUS ===").withStyle(ChatFormatting.GOLD), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Pausado: " + tm.isPaused()).withStyle(ChatFormatting.WHITE), false);
                                    if (current != null) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("Tarea: " + current.getDescription()).withStyle(ChatFormatting.YELLOW), false);
                                        ctx.getSource().sendSuccess(() -> Component.literal("Stage: " + current.getStage()).withStyle(ChatFormatting.WHITE), false);
                                        ctx.getSource().sendSuccess(() -> Component.literal("Substate: " + current.getSubstate()).withStyle(ChatFormatting.WHITE), false);
                                        ctx.getSource().sendSuccess(() -> Component.literal("Fallo: " + (current.getFailureReason().isEmpty() ? "Ninguno" : current.getFailureReason())).withStyle(ChatFormatting.RED), false);
                                        ctx.getSource().sendSuccess(() -> Component.literal("Último progreso: tick " + current.getLastProgressTick() + " (actual: " + comp.tickCount + ")").withStyle(ChatFormatting.GRAY), false);
                                    } else {
                                        ctx.getSource().sendSuccess(() -> Component.literal("Sin tarea activa.").withStyle(ChatFormatting.GRAY), false);
                                    }
                                    return 1;
                                }))
                        .then(Commands.literal("nav")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity comp = getComp(player);
                                    if (comp == null) return 0;
                                    String stats = comp.getNavManager().getDebugStats();
                                    ctx.getSource().sendSuccess(() -> Component.literal("=== DEBUG NAVEGACIÓN ===").withStyle(ChatFormatting.GOLD), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal(stats).withStyle(ChatFormatting.AQUA), false);
                                    return 1;
                                }))
                        .then(Commands.literal("path")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    CompanionEntity comp = getComp(player);
                                    if (comp == null) return 0;
                                    var path = comp.getNavManager().getCurrentPath();
                                    if (path == null || path.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("No hay ninguna ruta activa actualmente.").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    for (int i = 0; i < path.length(); i++) {
                                        var node = path.getNode(i);
                                        player.serverLevel().sendParticles(
                                                net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                                node.x + 0.5, node.y + 0.5, node.z + 0.5,
                                                5, 0.1, 0.1, 0.1, 0.02
                                        );
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("Ruta resaltada con partículas (" + path.length() + " nodos).").withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                }))
                        .then(Commands.literal("test")
                                .then(Commands.literal("follow")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            CompanionEntity comp = getComp(player);
                                            if (comp == null) return 0;
                                            comp.setPreferredIdleBehavior(DefaultBehavior.FOLLOW);
                                            comp.getCompanionBrain().getTaskManager().setTask(new FollowTask(comp));
                                            ctx.getSource().sendSuccess(() -> Component.literal("TEST: FollowTask asignada").withStyle(ChatFormatting.GREEN), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("goto")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            CompanionEntity comp = getComp(player);
                                            if (comp == null) return 0;
                                            BlockPos target = player.blockPosition().relative(player.getDirection(), 10);
                                            comp.getCompanionBrain().getTaskManager().setTask(new GotoTask(comp, target));
                                            ctx.getSource().sendSuccess(() -> Component.literal("TEST: GotoTask hacia " + target.toShortString()).withStyle(ChatFormatting.GREEN), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("mining")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            CompanionEntity comp = getComp(player);
                                            if (comp == null) return 0;
                                            comp.getCompanionBrain().getTaskManager().setTask(new MineTask(comp, MiningTarget.of("coal"), 5));
                                            ctx.getSource().sendSuccess(() -> Component.literal("TEST: MineTask (Carbón x5) iniciada").withStyle(ChatFormatting.GREEN), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("equipment")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            CompanionEntity comp = getComp(player);
                                            if (comp == null) return 0;
                                            EquipmentManager.autoEquipBestArmorAndWeapons(comp);
                                            ctx.getSource().sendSuccess(() -> Component.literal("TEST: Mejor equipamiento asignado").withStyle(ChatFormatting.GREEN), false);
                                            return 1;
                                        }))))
        );
    }

    private static int executeMine(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int amount) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            CompanionEntity comp = getComp(player);
            if (comp == null) return 0;
            String mineral = StringArgumentType.getString(ctx, "mineral");
            MiningTarget target = MiningTarget.of(mineral);
            comp.getCompanionBrain().getTaskManager().setTask(new MineTask(comp, target, amount));
            ctx.getSource().sendSuccess(() -> Component.literal("Esclavo comenzará a minar " + amount + " de " + target.getName() + ".").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int executeChop(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int amount) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            CompanionEntity comp = getComp(player);
            if (comp == null) return 0;
            String madera = StringArgumentType.getString(ctx, "madera");
            String wood = SpanishAliases.resolveWood(madera);
            comp.getCompanionBrain().getTaskManager().setTask(new ChopTask(comp, wood, amount));
            ctx.getSource().sendSuccess(() -> Component.literal("Esclavo comenzará a talar " + amount + " de madera (" + madera + ").").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int executeKill(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int amount) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            CompanionEntity comp = getComp(player);
            if (comp == null) return 0;
            String mob = StringArgumentType.getString(ctx, "mob");
            ResourceLocation mobId = SpanishAliases.resolveMob(mob);
            if (mobId == null) mobId = new ResourceLocation("minecraft", mob);
            comp.getCompanionBrain().getTaskManager().setTask(new KillTask(comp, mobId, amount));
            ctx.getSource().sendSuccess(() -> Component.literal("Esclavo cazará " + amount + " de " + mob + ".").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int executeWithdraw(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int amount) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            CompanionEntity comp = getComp(player);
            if (comp == null) return 0;
            String itemStr = StringArgumentType.getString(ctx, "item");
            ResourceLocation itemId = new ResourceLocation("minecraft", itemStr);
            comp.getCompanionBrain().getTaskManager().setTask(new WithdrawTask(comp, itemId, amount));
            ctx.getSource().sendSuccess(() -> Component.literal("Esclavo buscará " + amount + " de " + itemStr + " en cofres.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static CompanionEntity getComp(ServerPlayer player) {
        CompanionEntity comp = CompanionManager.getCompanionForOwner(player.getServer(), player.getUUID());
        if (comp == null) {
            player.sendSystemMessage(Component.translatable("message.aicompanion.not_found").withStyle(ChatFormatting.RED));
        }
        return comp;
    }
}
