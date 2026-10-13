package com.aicompanion.ai;

import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import com.aicompanion.ai.tasks.FightTask;
import com.aicompanion.ai.tasks.FleeTask;
import com.aicompanion.ai.tasks.FollowTask;
import com.aicompanion.ai.tasks.ReturnTask;
import java.util.List;

public class TaskManager {
    private static final int MAX_GOAP_DEPTH = 8;
    private final CompanionEntity companion;
    private final TaskQueue taskQueue = new TaskQueue();
    private final TaskProgressWatchdog watchdog;
    private CompanionTask currentTask = null;
    private boolean paused = false;
    private int currentGoapDepth = 0;
    private int lastHealInjectTick = -100000;

    public TaskManager(CompanionEntity companion) {
        this.companion = companion;
        this.watchdog = new TaskProgressWatchdog(companion);
    }

    public void tick() {
        if (paused) {
            companion.setCurrentState(CompanionState.PAUSED);
            return;
        }

        if (currentTask != null) {
            if (companion.tickCount % 60 == 0) {
                com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] TaskManager tick: task='{}', stage='{}', state={}",
                        currentTask.getDescription(), currentTask.getStage(), companion.getCurrentState());
            }
            if (currentTask.isComplete()) {
                if (currentTask.isFailed()) {
                    com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-GOAP] Task failed: '{}'. Reason: {}",
                            currentTask.getDescription(), currentTask.getFailureReason());
                    com.aicompanion.ai.coordination.CompanionCoordinator.markSubtaskProgress(companion.getUUID(), false, true, companion.level().getGameTime());

                    String fType = currentTask.getTaskType();
                    if ("FIGHT".equals(fType) || "FLEE".equals(fType) || "HEAL".equals(fType)) {
                        com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-INTERRUPT] Emergency task ended. Preserving user mission queue with {} tasks.", taskQueue.size());
                        currentTask = null;
                        advanceToNextTask();
                        return;
                    }

                    net.minecraft.world.entity.player.Player owner = companion.getOwnerPlayer();
                    if (owner != null && owner.isAlive() && !owner.isDeadOrDying()) {
                        Component msg = Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("Problema en subtarea: " + currentTask.getDescription() + " (" + currentTask.getFailureReason() + ")").withStyle(ChatFormatting.RED));
                        owner.sendSystemMessage(msg);
                    }

                    // Attempt dynamic replanning before discarding entire mission
                    boolean replanned = false;
                    if (companion.getCompanionBrain() != null && companion.getCompanionBrain().hasLastUserMission()) {
                        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-GOAP] Attempting automatic replanning of active mission following subtask failure...");
                        replanned = companion.getCompanionBrain().replanCurrentGoal();
                    }

                    if (!replanned) {
                        currentTask = null;
                        currentGoapDepth = 0;
                        taskQueue.clear();
                        companion.returnToDefaultBehavior();
                    }
                    return;
                }

                notifyTaskComplete(currentTask);
                com.aicompanion.ai.coordination.CompanionCoordinator.markSubtaskProgress(companion.getUUID(), true, false, companion.level().getGameTime());

                // Auto-equip any newly acquired tool or gear immediately upon task completion!
                com.aicompanion.inventory.EquipmentManager.autoEquipBestArmorAndWeapons(companion, false);

                currentTask = null;
                advanceToNextTask();
            } else {
                if (companion.tickCount % 20 == 0) {
                    com.aicompanion.ai.coordination.CompanionCoordinator.markSubtaskProgress(companion.getUUID(), false, false, companion.level().getGameTime());
                }
                currentTask.tick();
                watchdog.tick(currentTask);
                return;
            }
        } else if (!taskQueue.isEmpty()) {
            advanceToNextTask();
        }
    }

    private void advanceToNextTask() {
        if (!taskQueue.isEmpty()) {
            // Survival check: if companion survived an emergency and needs HealTask before resuming civil task
            // 1.13.9: también sin comida si la salud está por debajo del 50 % (descansa y regenera); con enfriamiento anti-bucle.
            boolean lowHp = companion.getHealth() < companion.getMaxHealth() * 0.5f;
            boolean canEatUp = companion.getHealth() < companion.getMaxHealth() - 2.5f && hasFoodInInventory();
            if ((lowHp || canEatUp) && companion.tickCount - lastHealInjectTick > 600) {
                CompanionTask nextCandidate = taskQueue.peek();
                if (nextCandidate != null && !"HEAL".equals(nextCandidate.getTaskType()) &&
                    !"FIGHT".equals(nextCandidate.getTaskType()) && !"FLEE".equals(nextCandidate.getTaskType())) {
                    com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-SURVIVAL] Injecting post-threat HealTask before resuming civil task '{}' (HP {}/{})",
                            nextCandidate.getDescription(), companion.getHealth(), companion.getMaxHealth());
                    lastHealInjectTick = companion.tickCount;
                    this.currentTask = new com.aicompanion.ai.tasks.HealTask(companion);
                    this.currentTask.start();
                    notifyTaskStart(this.currentTask);
                    return;
                }
            }

            currentTask = taskQueue.poll();
            if (currentTask != null) {
                // If companion is at critical low HP, skip combat tasks to avoid suicidal re-engagement loops
                if (currentTask instanceof FightTask && (companion.getHealth() <= 8.0f || (companion.getHealth() / companion.getMaxHealth() <= 0.40f))) {
                    com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-SURVIVAL] Skipping FightTask '{}' from queue due to low HP ({}/{}).",
                            currentTask.getDescription(), companion.getHealth(), companion.getMaxHealth());
                    currentTask.cancel();
                    currentTask = null;
                    advanceToNextTask();
                    return;
                }

                if (currentTask.isPaused()) {
                    // This was a paused parent task waiting on the LIFO stack!
                    // Re-evaluate preconditions to see if all prerequisites are now satisfied
                    java.util.List<CompanionTask> remainingPrereqs = currentTask.checkPreconditions();
                    if (remainingPrereqs != null && !remainingPrereqs.isEmpty()) {
                        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-GOAP] Parent task '{}' still needs {} prerequisites.",
                                currentTask.getDescription(), remainingPrereqs.size());
                        pushSubtasks(remainingPrereqs);
                        return;
                    }

                    if (currentGoapDepth > 0) {
                        currentGoapDepth--;
                    }
                    currentTask.resume();
                    notifyTaskResume(currentTask);
                } else {
                    // Precondition check before executing fresh polled task to ensure required inventory state
                    java.util.List<CompanionTask> prereqs = currentTask.checkPreconditions();
                    if (prereqs != null && !prereqs.isEmpty()) {
                        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-GOAP] Task '{}' has {} unsatisfied preconditions. Pushing to LIFO stack.",
                                currentTask.getDescription(), prereqs.size());
                        pushSubtasks(prereqs);
                        return;
                    }

                    currentTask.start();
                    notifyTaskStart(currentTask);
                }
            }
        } else {
            currentGoapDepth = 0;
            companion.returnToDefaultBehavior();
            companion.syncToOwner();
        }
    }

    public void interruptWithEmergency(CompanionTask emergencyTask) {
        if (emergencyTask == null) return;
        if (currentTask != null) {
            com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-INTERRUPT] Interrupting active task '{}' for emergency survival task '{}'",
                    currentTask.getDescription(), emergencyTask.getDescription());
            currentTask.pause();

            // When fleeing due to emergency survival (FleeTask), do NOT save a suicidal FightTask to LIFO stack!
            // Doing so causes an infinite death loop: Flee -> FightTask popped -> attack same overpowering mob -> Flee again.
            boolean isFleeing = emergencyTask instanceof FleeTask;
            if (isFleeing) {
                currentTask.onEmergencyFlee((FleeTask) emergencyTask);
            }
            boolean isFightTask = currentTask instanceof FightTask;
            boolean lowHp = companion.getHealth() <= 8.0f || (companion.getHealth() / companion.getMaxHealth() <= 0.40f);

            if (isFleeing && (isFightTask || lowHp)) {
                com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-SURVIVAL] Discarding suicidal task '{}' during tactical escape to prevent retreat-attack cycle.", currentTask.getDescription());
                currentTask.stop();
                currentTask.cancel();
            } else {
                taskQueue.push(currentTask);
            }
            currentTask = null;
        }
        this.currentTask = emergencyTask;
        this.currentTask.start();
        notifyTaskStart(this.currentTask);
    }

    private boolean hasFoodInInventory() {
        var inv = companion.getInventoryHandler();
        for (int i = 0; i < com.aicompanion.inventory.CompanionInventoryHandler.MAIN_SLOTS; i++) {
            var stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem().isEdible()) return true;
        }
        return false;
    }

    public boolean pushSubtasks(java.util.List<CompanionTask> subtasks) {
        if (subtasks == null || subtasks.isEmpty()) return false;
        if (currentGoapDepth >= MAX_GOAP_DEPTH) {
            com.aicompanion.AICompanionMod.LOGGER.error("[ESCLAVO-GOAP] Max GOAP depth {} reached! Aborting prerequisite chain.", MAX_GOAP_DEPTH);
            if (currentTask != null) {
                currentTask.fail("Profundidad máxima de planificación (" + MAX_GOAP_DEPTH + ") alcanzada.");
            }
            return false;
        }

        currentGoapDepth++;
        if (currentTask != null) {
            com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-GOAP] Pausing task '{}' and pushing {} prerequisites to LIFO stack.",
                    currentTask.getDescription(), subtasks.size());
            currentTask.pause();
            taskQueue.push(currentTask);
            currentTask = null;
        }
        taskQueue.pushSubtasks(subtasks);
        CompanionTask next = taskQueue.poll();
        if (next != null) {
            this.currentTask = next;
            this.currentTask.start();
            notifyTaskStart(this.currentTask);
        }
        return true;
    }

    private void notifyTaskStart(CompanionTask task) {
        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] Task started: {}", task.getDescription());
        net.minecraft.world.entity.player.Player owner = companion.getOwnerPlayer();
        if (owner != null && owner.isAlive() && !owner.isDeadOrDying()) {
            Component msg = Component.literal("[" + companion.getCustomCompanionName() + "] ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Comenzando: " + task.getDescription()).withStyle(ChatFormatting.WHITE));
            owner.sendSystemMessage(msg);
        }
        companion.syncToOwner();
    }

    private ReturnTask lastCompletedReturnTask = null;

    private void notifyTaskComplete(CompanionTask task) {
        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] Task completed: {}", task.getDescription());
        if (task instanceof ReturnTask rt) {
            this.lastCompletedReturnTask = rt;
        }
        net.minecraft.world.entity.player.Player owner = companion.getOwnerPlayer();
        if (owner != null && owner.isAlive() && !owner.isDeadOrDying()) {
            boolean isRootCompletion = taskQueue.isEmpty() && currentGoapDepth == 0;
            if (isRootCompletion) {
                List<com.aicompanion.ai.planning.Goal> userGoals = (companion.getCompanionBrain() != null)
                        ? companion.getCompanionBrain().getLastUserGoals()
                        : java.util.Collections.emptyList();

                boolean hasObtainGoal = false;
                boolean fullySatisfied = true;
                String deficitExplanation = null;

                for (com.aicompanion.ai.planning.Goal g : userGoals) {
                    if (g != null && g.getType() == com.aicompanion.ai.planning.Goal.Type.OBTAIN && g.getTargetResource() != null) {
                        hasObtainGoal = true;
                        int needed = g.getAmount();
                        int inInventory = countResourceInInventory(g.getTargetResource());
                        int delivered = (lastCompletedReturnTask != null) ? lastCompletedReturnTask.getDeliveredCount(g.getTargetResource()) : 0;
                        int totalObtained = inInventory + delivered;
                        if (totalObtained < needed) {
                            fullySatisfied = false;
                            String name = com.aicompanion.ai.parser.ItemSearchRegistry.getDisplayName(g.getTargetResource());
                            deficitExplanation = "Faltaron materiales (" + totalObtained + "/" + needed + " " + name + ")";
                            break;
                        }
                    }
                }

                String lastOrder = companion.getCompanionBrain() != null ? companion.getCompanionBrain().getLastUserOrderText() : "";
                if (!hasObtainGoal && !lastOrder.isEmpty() && (lastOrder.toLowerCase().contains("minar") || lastOrder.toLowerCase().contains("talar") || lastOrder.toLowerCase().contains("conseguir") || lastOrder.toLowerCase().contains("picar"))) {
                    fullySatisfied = false;
                    deficitExplanation = "No se comprobó la posesión de los recursos de la misión";
                }

                boolean isUserMissionTask = !userGoals.isEmpty() || (!lastOrder.isEmpty() && !(task instanceof ReturnTask) && !(task instanceof FollowTask) && !(task instanceof FleeTask) && !(task instanceof FightTask));

                if (isUserMissionTask) {
                    if (fullySatisfied) {
                        String missionText = !lastOrder.isEmpty() ? lastOrder : task.getDescription();
                        Component msg = Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                .withStyle(ChatFormatting.GREEN)
                                .append(Component.literal("¡Misión completada: " + missionText + "!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                        owner.sendSystemMessage(msg);
                        if (companion.getCompanionBrain() != null) {
                            companion.getCompanionBrain().recordUserMission(java.util.Collections.emptyList(), "");
                        }
                    } else {
                        Component msg = Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("Subtarea finalizada pero no se completó la misión: " + deficitExplanation + ".").withStyle(ChatFormatting.YELLOW));
                        owner.sendSystemMessage(msg);
                    }
                } else {
                    Component msg = Component.literal("[" + companion.getCustomCompanionName() + "] ")
                            .withStyle(ChatFormatting.AQUA)
                            .append(Component.literal("Subtarea completada: " + task.getDescription()).withStyle(ChatFormatting.WHITE));
                    owner.sendSystemMessage(msg);
                }

                if (com.aicompanion.AICompanionConfig.COMMON.autoOrganizeInventory.get() && companion.getInventoryHandler() != null) {
                    com.aicompanion.inventory.InventoryOrganizer.organize(companion.getInventoryHandler());
                }

                lastCompletedReturnTask = null;
            } else {
                Component msg = Component.literal("[" + companion.getCustomCompanionName() + "] ")
                        .withStyle(ChatFormatting.AQUA)
                        .append(Component.literal("Subtarea completada: " + task.getDescription()).withStyle(ChatFormatting.WHITE));
                owner.sendSystemMessage(msg);
            }
        }
        companion.syncToOwner();
    }

    private int countResourceInInventory(net.minecraft.resources.ResourceLocation resource) {
        if (companion.getInventoryHandler() == null || resource == null) return 0;
        int count = 0;
        String path = resource.getPath();
        boolean isLogTag = path.endsWith("_log") || path.equals("log") || path.equals("logs");
        for (int i = 0; i < com.aicompanion.inventory.CompanionInventoryHandler.MAIN_SLOTS; i++) {
            net.minecraft.world.item.ItemStack stack = companion.getInventoryHandler().getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (isLogTag && stack.is(net.minecraft.tags.ItemTags.LOGS)) {
                count += stack.getCount();
                continue;
            }
            net.minecraft.resources.ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (resource.equals(itemId)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void notifyTaskResume(CompanionTask task) {
        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-GOAP] Task resumed from LIFO stack: {}", task.getDescription());
        net.minecraft.world.entity.player.Player owner = companion.getOwnerPlayer();
        if (owner != null && owner.isAlive() && !owner.isDeadOrDying()) {
            Component msg = Component.literal("[" + companion.getCustomCompanionName() + "] ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Prerrequisitos listos. Reanudando: " + task.getDescription()).withStyle(ChatFormatting.GREEN));
            owner.sendSystemMessage(msg);
        }
        companion.syncToOwner();
    }

    private CompanionTask stashedTask = null;
    private final java.util.List<CompanionTask> stashedQueue = new java.util.ArrayList<>();

    public void setTask(CompanionTask task) {
        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] TaskManager.setTask: {}", (task != null ? task.getDescription() : "null"));
        if (this.paused) {
            this.paused = false;
            net.minecraft.world.entity.player.Player owner = companion.getOwnerPlayer();
            if (owner != null && owner.isAlive() && !owner.isDeadOrDying()) {
                owner.sendSystemMessage(
                        Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                .withStyle(ChatFormatting.GREEN)
                                .append(Component.literal("Esclavo reanudado para ejecutar la nueva orden.").withStyle(ChatFormatting.WHITE))
                );
            }
        }

        if (currentTask != null) {
            currentTask.stop();
            currentTask.cancel();
        }
        this.currentTask = null;
        this.currentGoapDepth = 0;

        if (task != null) {
            java.util.List<CompanionTask> prereqs = task.checkPreconditions();
            if (prereqs != null && !prereqs.isEmpty()) {
                com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-GOAP] Initial preconditions not met for '{}'. Pushing {} prerequisites to stack.",
                        task.getDescription(), prereqs.size());
                task.pause();
                this.taskQueue.push(task);
                this.taskQueue.pushSubtasks(prereqs);
                this.currentGoapDepth = 1;
                CompanionTask next = this.taskQueue.poll();
                if (next != null) {
                    this.currentTask = next;
                    this.currentTask.start();
                    notifyTaskStart(this.currentTask);
                }
            } else {
                this.currentTask = task;
                this.currentTask.start();
                notifyTaskStart(this.currentTask);
            }
        }
    }

    public void queueTask(CompanionTask task) {
        if (currentTask == null || currentTask instanceof com.aicompanion.ai.tasks.FollowTask || currentTask instanceof com.aicompanion.ai.tasks.StayTask) {
            setTask(task);
        } else {
            taskQueue.enqueue(task);
        }
    }

    public CompanionTask getCurrentTask() {
        return currentTask;
    }

    public TaskQueue getTaskQueue() {
        return taskQueue;
    }

    public void stashActiveMission() {
        if (currentTask != null && !isIdleOrUtilityTask(currentTask)) {
            this.stashedTask = currentTask;
            this.stashedQueue.clear();
            if (taskQueue.getTasks() != null) {
                this.stashedQueue.addAll(taskQueue.getTasks());
            }
            taskQueue.clear();
            com.aicompanion.AICompanionMod.LOGGER.info("[TaskManager] Stashed active mission '{}' with {} queued steps",
                    stashedTask.getDescription(), stashedQueue.size());
        }
    }

    public boolean hasStashedMission() {
        return stashedTask != null;
    }

    public CompanionTask getStashedTask() {
        return stashedTask;
    }

    public boolean isIdleOrUtilityTask(CompanionTask task) {
        return task instanceof com.aicompanion.ai.tasks.FollowTask
                || task instanceof com.aicompanion.ai.tasks.StayTask
                || task instanceof com.aicompanion.ai.tasks.ReturnTask;
    }

    public void pause() {
        this.paused = true;
        if (currentTask != null) {
            currentTask.pause();
        }
        if (companion.getNavManager() != null) {
            companion.getNavManager().stop();
        }
        companion.setCurrentState(CompanionState.PAUSED);
        if (companion.getOwnerPlayer() != null) {
            companion.getOwnerPlayer().sendSystemMessage(
                    Component.literal("[" + companion.getCustomCompanionName() + "] ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("Misión pausada.").withStyle(ChatFormatting.YELLOW))
            );
        }
    }

    public void resume() {
        this.paused = false;

        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-RESUME] Recibida solicitud RESUME para companion {} (id={}, dim={})",
                companion.getCustomCompanionName(), companion.getId(), companion.level().dimension().location());

        // 1. Validation: Is companion alive and valid?
        if (!companion.isAlive() || companion.isRemoved()) {
            com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-RESUME] No se puede reanudar: el compañero no está vivo o fue eliminado.");
            return;
        }

        // 2. Validation: State check (not incapacitated)
        if (companion.getCurrentState() == CompanionState.DOWNED ||
            companion.getCurrentState() == CompanionState.DEAD_WAITING_RESPAWN ||
            companion.getCurrentState() == CompanionState.RESPAWNING) {
            com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-RESUME] No se puede reanudar: el compañero está incapacitado (estado={}).", companion.getCurrentState());
            return;
        }

        // 3. Validation: Force chunk tickets to ensure entity-ticking
        if (companion.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            com.aicompanion.ai.chunk.CompanionChunkManager.updateChunkTickets(companion);
            com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-RESUME] Entidad activa y tickets de chunk reforzados en dimensión {}", sl.dimension().location());
        }

        // 4. If an active mission was stashed (e.g. by pressing REGRESAR):
        if (stashedTask != null) {
            CompanionTask restored = stashedTask;
            stashedTask = null;
            if (currentTask != null) {
                currentTask.stop();
            }
            this.currentTask = restored;
            this.taskQueue.clear();
            for (CompanionTask t : stashedQueue) {
                this.taskQueue.enqueue(t);
            }
            this.stashedQueue.clear();

            List<CompanionTask> prereqs = restored.checkPreconditions();
            if (prereqs != null && !prereqs.isEmpty()) {
                com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-RESUME] Misión guardada requiere {} prerrequisitos. Insertando en cola LIFO.", prereqs.size());
                pushSubtasks(prereqs);
            } else {
                restored.resume();
                notifyTaskStart(restored);
            }

            if (companion.getOwnerPlayer() != null) {
                companion.getOwnerPlayer().sendSystemMessage(
                        Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("¡Reanudando misión previa: " + restored.getDescription() + "!").withStyle(ChatFormatting.GREEN))
                );
            }
            return;
        }

        // 5. If current task is active and not idle, validate preconditions and resume:
        if (currentTask != null && !isIdleOrUtilityTask(currentTask)) {
            List<CompanionTask> prereqs = currentTask.checkPreconditions();
            if (prereqs != null && !prereqs.isEmpty()) {
                com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-RESUME] Tarea actual '{}' requiere {} prerrequisitos. Insertando en cola.",
                        currentTask.getDescription(), prereqs.size());
                pushSubtasks(prereqs);
                return;
            }

            currentTask.resume();
            if (companion.getOwnerPlayer() != null) {
                companion.getOwnerPlayer().sendSystemMessage(
                        Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("Reanudando: " + currentTask.getDescription()).withStyle(ChatFormatting.GREEN))
                );
            }
            return;
        }

        // 5b. Permanent Build Project Anchor: resume active project at original originPos
        if (companion.getCompanionBrain() != null && companion.getCompanionBrain().getActiveBuildProject() != null) {
            com.aicompanion.ai.building.BuildProject proj = companion.getCompanionBrain().getActiveBuildProject();
            if (proj.isActive()) {
                com.aicompanion.ai.building.Blueprint baseBp = com.aicompanion.ai.building.BlueprintRegistry.get(proj.getBlueprintId());
                if (baseBp == null) baseBp = com.aicompanion.ai.building.BlueprintRegistry.get(proj.getBlueprintName());
                if (baseBp != null) {
                    com.aicompanion.ai.building.Blueprint finalBp = com.aicompanion.ai.building.BlueprintTransformer.transform(baseBp, proj.getBuildingConfig());
                    com.aicompanion.ai.tasks.BuildTask bTask = new com.aicompanion.ai.tasks.BuildTask(companion, finalBp, proj.getOriginPos(), proj.getBuildingConfig());
                    setTask(bTask);
                    if (companion.getOwnerPlayer() != null) {
                        companion.getOwnerPlayer().sendSystemMessage(
                                Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                        .withStyle(ChatFormatting.GOLD)
                                        .append(Component.literal("¡Reanudando construcción de '" + proj.getBlueprintName() + "' en su ubicación fijada (X="
                                                + proj.getOriginPos().getX() + ", Y=" + proj.getOriginPos().getY() + ", Z=" + proj.getOriginPos().getZ() + ")!").withStyle(ChatFormatting.GREEN))
                        );
                    }
                    return;
                }
            }
        }

        // 6. If currentTask was idle/follow, but CompanionBrain has a recorded last order/goal:
        if (companion.getCompanionBrain() != null && companion.getCompanionBrain().resumeLastOrderIfAny()) {
            com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-RESUME] Reanudada última orden desde registro de CompanionBrain.");
            return;
        }

        // 7. Default: No previous mission found, give clear feedback to player
        if (currentTask != null) {
            currentTask.resume();
        }
        if (companion.getOwnerPlayer() != null) {
            companion.getOwnerPlayer().sendSystemMessage(
                    Component.literal("[" + companion.getCustomCompanionName() + "] ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("No hay ninguna misión previa para reanudar. Puedes darme una orden desde el menú de Órdenes.").withStyle(ChatFormatting.YELLOW))
            );
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void stop() {
        this.stashedTask = null;
        this.stashedQueue.clear();
        this.paused = false;
        this.currentGoapDepth = 0;
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        taskQueue.clear();
        companion.getNavigation().stop();
        companion.returnToDefaultBehavior();
    }

    public void stopWithoutDefault() {
        this.stashedTask = null;
        this.stashedQueue.clear();
        this.paused = false;
        this.currentGoapDepth = 0;
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        taskQueue.clear();
        companion.getNavigation().stop();
    }

    public void clear() {
        stopWithoutDefault();
    }

    public boolean isBusy() {
        return currentTask != null && !currentTask.isComplete();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Paused", paused);
        tag.putInt("GoapDepth", currentGoapDepth);

        if (currentTask != null) {
            CompoundTag cTag = com.aicompanion.ai.tasks.TaskSerializerRegistry.serialize(currentTask);
            if (!cTag.isEmpty()) {
                tag.put("CurrentTask", cTag);
            }
        }

        if (stashedTask != null) {
            CompoundTag stTag = com.aicompanion.ai.tasks.TaskSerializerRegistry.serialize(stashedTask);
            if (!stTag.isEmpty()) {
                tag.put("StashedTask", stTag);
            }
        }

        if (!stashedQueue.isEmpty()) {
            net.minecraft.nbt.ListTag sqTag = new net.minecraft.nbt.ListTag();
            for (CompanionTask t : stashedQueue) {
                if (t != null) {
                    CompoundTag tTag = com.aicompanion.ai.tasks.TaskSerializerRegistry.serialize(t);
                    if (!tTag.isEmpty()) {
                        sqTag.add(tTag);
                    }
                }
            }
            tag.put("StashedQueue", sqTag);
        }

        net.minecraft.nbt.ListTag queueTag = new net.minecraft.nbt.ListTag();
        for (CompanionTask t : taskQueue.getTasks()) {
            if (t != null) {
                CompoundTag tTag = com.aicompanion.ai.tasks.TaskSerializerRegistry.serialize(t);
                if (!tTag.isEmpty()) {
                    queueTag.add(tTag);
                }
            }
        }
        tag.put("TaskQueue", queueTag);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag == null) return;
        this.paused = tag.getBoolean("Paused");
        this.currentGoapDepth = tag.getInt("GoapDepth");

        if (tag.contains("CurrentTask")) {
            CompoundTag currentTag = tag.getCompound("CurrentTask");
            this.currentTask = com.aicompanion.ai.tasks.TaskSerializerRegistry.deserialize(companion, currentTag);
        }

        if (tag.contains("StashedTask")) {
            CompoundTag stTag = tag.getCompound("StashedTask");
            this.stashedTask = com.aicompanion.ai.tasks.TaskSerializerRegistry.deserialize(companion, stTag);
        }

        if (tag.contains("StashedQueue")) {
            this.stashedQueue.clear();
            net.minecraft.nbt.ListTag list = tag.getList("StashedQueue", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompanionTask t = com.aicompanion.ai.tasks.TaskSerializerRegistry.deserialize(companion, list.getCompound(i));
                if (t != null) {
                    this.stashedQueue.add(t);
                }
            }
        }

        if (tag.contains("TaskQueue")) {
            this.taskQueue.clear();
            net.minecraft.nbt.ListTag list = tag.getList("TaskQueue", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompanionTask t = com.aicompanion.ai.tasks.TaskSerializerRegistry.deserialize(companion, list.getCompound(i));
                if (t != null) {
                    this.taskQueue.enqueue(t);
                }
            }
        }
    }
}
