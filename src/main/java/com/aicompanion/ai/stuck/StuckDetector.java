package com.aicompanion.ai.stuck;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.util.BlockBreakHelper;
import com.aicompanion.util.SafeSpawnFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.HashSet;

public class StuckDetector {
    private final CompanionEntity companion;
    private final RescueProtocolEngine rescueEngine;
    private BlockPos lastPos;
    private int stuckTicks = 0;
    private int stuckAttempts = 0;

    // Fluctuation Variance Telemetry (Pilar 3: Motor de Rescate Dinámico)
    private int varianceWindowTicks = 0;
    private double windowStartX, windowStartZ, windowStartY;
    private double windowMinY, windowMaxY;
    private int yFluctuationCount = 0;
    private double lastTickY = 0;

    public StuckDetector(CompanionEntity companion) {
        this.companion = companion;
        this.rescueEngine = new RescueProtocolEngine(companion);
        this.lastPos = companion.blockPosition();
        this.lastTickY = companion.getY();
    }

    public RescueProtocolEngine getRescueEngine() {
        return rescueEngine;
    }

    public void tick() {
        if (!(companion.level() instanceof ServerLevel sl)) {
            return;
        }

        // 0. If rescue protocol is currently executing, yield priority to it
        if (rescueEngine.isRescuing()) {
            boolean active = rescueEngine.tick(sl);
            if (active) {
                stuckTicks = 0;
                stuckAttempts = 0;
                lastPos = companion.blockPosition();
                return;
            }
        }

        BlockPos currentPos = companion.blockPosition();
        CompanionState state = companion.getCurrentState();

        // 1. If companion is idle, staying, waiting, paused, inventory full, downed, or respawning, no stuck check is needed
        if (state == CompanionState.IDLE || state == CompanionState.STAYING || 
            state == CompanionState.WAITING || state == CompanionState.PAUSED ||
            state == CompanionState.INVENTORY_FULL || state == CompanionState.DOWNED ||
            state == CompanionState.DEAD_WAITING_RESPAWN || state == CompanionState.RESPAWNING ||
            state == CompanionState.NO_TOOL || state == CompanionState.NO_FOOD) {
            stuckTicks = 0;
            stuckAttempts = 0;
            varianceWindowTicks = 0;
            lastPos = currentPos;
            return;
        }

        // 2. If currently performing stationary work (breaking blocks, crafting, smelting, etc.), ignore lack of displacement
        CompanionTask currentTask = companion.getCompanionBrain().getTaskManager().getCurrentTask();
        if (currentTask != null && currentTask.isStationaryWorking()) {
            stuckTicks = 0;
            varianceWindowTicks = 0;
            lastPos = currentPos;
            return;
        }

        // 3. If following and already near the owner (within 5 blocks), Esclavo has successfully reached the owner!
        Player owner = companion.getOwnerPlayer();
        if (state == CompanionState.FOLLOWING && owner != null && owner.isAlive() && !owner.isDeadOrDying() && companion.distanceToSqr(owner) <= 25.0) {
            stuckTicks = 0;
            stuckAttempts = 0;
            varianceWindowTicks = 0;
            lastPos = currentPos;
            return;
        }

        // 4. If the navigation system has finished its path or is idle, Esclavo is not actively trying to move
        if (companion.getNavManager() != null && companion.getNavManager().isDone()) {
            // 1.13.12: en agua sin ruta (planificador IMPOSSIBLE -> nav "terminado") nunca se detectaba el atasco
            // y se quedaba flotando en la poza para siempre. Con una tarea de movimiento activa, rescate de agua.
            boolean movingState = state == CompanionState.NAVIGATING || state == CompanionState.FOLLOWING || state == CompanionState.SEARCHING;
            if (movingState && companion.isInWater() && currentPos.distSqr(lastPos) < 4.0) {
                if (++waterNoRouteTicks >= 80) {
                    waterNoRouteTicks = 0;
                    com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-ANTI-STUCK] En agua sin ruta durante 4 s en {} -> rescate", currentPos);
                    declareStuckCritical(sl);
                }
                return;
            }
            waterNoRouteTicks = 0;
            stuckTicks = 0;
            stuckAttempts = 0;
            varianceWindowTicks = 0;
            lastPos = currentPos;
            return;
        }
        waterNoRouteTicks = 0;

        // 5. Only active physical movement states trigger stuck detection
        boolean isNavigatingState = state == CompanionState.NAVIGATING ||
                                    state == CompanionState.FOLLOWING ||
                                    state == CompanionState.SEARCHING;

        if (isNavigatingState) {
            // === FLUCTUATION VARIANCE TELEMETRY (Pilar 3) ===
            varianceWindowTicks++;
            if (varianceWindowTicks == 1) {
                windowStartX = companion.getX();
                windowStartZ = companion.getZ();
                windowStartY = companion.getY();
                windowMinY = companion.getY();
                windowMaxY = companion.getY();
                yFluctuationCount = 0;
                lastTickY = companion.getY();
            } else {
                windowMinY = Math.min(windowMinY, companion.getY());
                windowMaxY = Math.max(windowMaxY, companion.getY());
                if (Math.abs(companion.getY() - lastTickY) > 0.12 || companion.verticalCollision) {
                    yFluctuationCount++;
                }
                lastTickY = companion.getY();
            }

            if (varianceWindowTicks >= 60) {
                double dx = companion.getX() - windowStartX;
                double dz = companion.getZ() - windowStartZ;
                double horizDelta = Math.sqrt(dx * dx + dz * dz);
                double yVariance = windowMaxY - windowMinY;
                // 1.13.9: cavar hacia abajo / subir escalones en columna es progreso vertical real, no un atasco
                double netY = Math.abs(companion.getY() - windowStartY);

                // Condition: Horizontal delta < 0.5 in 60 ticks with continuous Y fluctuations (y sin avance vertical neto)
                if (horizDelta < 0.5 && netY < 0.9 && (yVariance >= 0.45 || yFluctuationCount >= 6)) {
                    com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-ANTI-STUCK] STUCK_CRITICAL declared! Fluctuation variance: horizDelta={} < 0.5, yVariance={}, fluctuations={}", horizDelta, yVariance, yFluctuationCount);
                    declareStuckCritical(sl);
                    varianceWindowTicks = 0;
                    stuckTicks = 0;
                    return;
                }
                varianceWindowTicks = 0;
            }

            if (currentPos.distSqr(lastPos) < 1.0) {
                stuckTicks++;
                if (stuckTicks >= 60) { // 3 seconds of active navigation with zero displacement
                    stuckAttempts++;
                    recover(stuckAttempts);
                    stuckTicks = 0;
                }
            } else {
                stuckTicks = 0;
                if (currentPos.distSqr(lastPos) > 4.0) {
                    stuckAttempts = 0;
                }
                lastPos = currentPos;
            }
        } else {
            stuckTicks = 0;
            varianceWindowTicks = 0;
            lastPos = currentPos;
        }
    }

    /**
     * Actively unblocks Esclavo by breaking surrounding obstacles
     * (leaves, vines, cobwebs, head clearance, and blocks obstructing movement).
     */
    public boolean tryBreakObstructionsAround(ServerLevel sl) {
        if (companion == null || sl == null) return false;
        BlockPos current = companion.blockPosition();

        // 1. Head clearance (essential for jumping up 1-block steps or out of holes)
        BlockPos headClearance = current.above(2);
        BlockState headState = sl.getBlockState(headClearance);
        if (!headState.isAir() && !headState.liquid() && isBreakableObstacle(sl, headClearance, headState)) {
            BlockBreakHelper.finishBreaking(companion, sl, headClearance);
            return true;
        }

        // 2. Clear leaves, vines, cobwebs, bushes in 3x3 around companion feet and body
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0 && dy == 0) continue; // Don't dig the block under feet
                    BlockPos p = current.offset(dx, dy, dz);
                    BlockState state = sl.getBlockState(p);
                    if (state.isAir() || state.liquid()) continue;

                    if ((state.is(BlockTags.LEAVES) ||
                        state.is(BlockTags.CLIMBABLE) ||
                        state.is(Blocks.COBWEB) ||
                        state.is(Blocks.SWEET_BERRY_BUSH) ||
                        state.is(Blocks.GRASS) ||
                        state.is(Blocks.TALL_GRASS) ||
                        state.is(Blocks.FERN) ||
                        state.is(Blocks.LARGE_FERN) ||
                        state.is(Blocks.VINE) ||
                        state.is(BlockTags.FLOWERS))
                        && companion.getSettings().isAllowBlockBreaking()
                        && com.aicompanion.util.TerrainPolicy.mayBreakImplicitly(sl, p, state, companion.getTemporaryBlocks())) {
                        BlockBreakHelper.finishBreaking(companion, sl, p);
                        return true;
                    }
                }
            }
        }

        // 3. Clear obstacles in front or in horizontal directions blocking movement
        Vec3 look = companion.getLookAngle();
        Direction facing = Direction.getNearest(look.x, 0, look.z);

        Direction[] checkDirs = {facing, facing.getClockWise(), facing.getCounterClockWise()};
        for (Direction dir : checkDirs) {
            BlockPos inFrontFeet = current.relative(dir);
            BlockPos inFrontHead = inFrontFeet.above();

            for (BlockPos front : new BlockPos[]{inFrontHead, inFrontFeet}) {
                BlockState fs = sl.getBlockState(front);
                if (!fs.isAir() && !fs.liquid() && isBreakableObstacle(sl, front, fs)) {
                    BlockBreakHelper.finishBreaking(companion, sl, front);
                    return true;
                }
            }
        }

        return false;
    }

    public enum RecoveryPhase {
        DETECTAR_ATASCO,
        DIAGNOSTICAR,
        DESPEJAR_O_RODEAR,
        RECALCULAR,
        REANUDAR_MISION
    }

    private RecoveryPhase currentPhase = RecoveryPhase.DETECTAR_ATASCO;
    private final Set<BlockPos> temporaryFailedNodes = new HashSet<>();

    public RecoveryPhase getCurrentRecoveryPhase() {
        return currentPhase;
    }

    public boolean isFailedNode(BlockPos pos) {
        return pos != null && temporaryFailedNodes.contains(pos);
    }

    private boolean isBreakableObstacle(ServerLevel sl, BlockPos pos, BlockState state) {
        if (!companion.getSettings().isAllowBlockBreaking()) return false;
        if (!com.aicompanion.util.TerrainPolicy.mayBreakImplicitly(sl, pos, state, companion.getTemporaryBlocks())) return false;
        float speed = state.getDestroySpeed(sl, pos);
        // Soft natural blocks (dirt, sand, gravel, leaves, grass, vines, snow, etc.)
        return speed >= 0 && speed <= 0.8f;
    }

    private void recoverFromWater(ServerLevel sl) {
        if (!companion.isInWater()) return;

        BlockPos current = companion.blockPosition();
        BlockPos bestShore = null;
        double bestDistSq = Double.MAX_VALUE;

        // Search for nearest solid shore with air above it within 6 blocks
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos p = current.offset(dx, dy, dz);
                    BlockState s = sl.getBlockState(p);
                    BlockState above1 = sl.getBlockState(p.above());
                    BlockState above2 = sl.getBlockState(p.above(2));

                    if (!s.isAir() && !s.liquid() && s.isSolid() &&
                        (above1.isAir() || !above1.liquid()) &&
                        (above2.isAir() || !above2.liquid())) {
                        double dist = current.distSqr(p.above());
                        if (dist < bestDistSq) {
                            bestDistSq = dist;
                            bestShore = p.above();
                        }
                    }
                }
            }
        }

        if (bestShore != null) {
            Vec3 target = Vec3.atCenterOf(bestShore);
            double dx = target.x - companion.getX();
            double dz = target.z - companion.getZ();
            double mag = Math.sqrt(dx * dx + dz * dz);
            if (mag > 0.05) {
                dx /= mag;
                dz /= mag;
            }
            // Swim toward shore with a moderate, swimming-like impulse
            Vec3 cur = companion.getDeltaMovement();
            companion.setDeltaMovement(cur.x * 0.5 + dx * 0.12D, Math.max(cur.y, 0.36D), cur.z * 0.5 + dz * 0.12D);
            companion.hasImpulse = true;
            companion.getJumpControl().jump();
            if (companion.getNavManager() != null) {
                companion.getNavManager().moveTo(bestShore, com.aicompanion.ai.nav.MovementPolicy.walk());
            }
        } else {
            Vec3 cur = companion.getDeltaMovement();
            companion.setDeltaMovement(cur.x, Math.max(cur.y, 0.3D), cur.z);
            companion.hasImpulse = true;
            companion.getJumpControl().jump();
        }
    }

    private void recover(int attempt) {
        ServerLevel sl = companion.level() instanceof ServerLevel level ? level : null;
        BlockPos currentPos = companion.blockPosition();

        // FASE 1: DETECTAR_ATASCO
        currentPhase = RecoveryPhase.DETECTAR_ATASCO;
        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-ANTI-STUCK] Fase 1 (DETECTAR_ATASCO) en {} (intento {})", currentPos, attempt);

        // FASE 2: DIAGNOSTICAR
        currentPhase = RecoveryPhase.DIAGNOSTICAR;
        boolean inWater = companion.isInWater() && sl != null;
        boolean inHole = sl != null && rescueEngine.isEnclosedInPit(sl, currentPos);

        // FASE 3: DESPEJAR O RODEAR
        currentPhase = RecoveryPhase.DESPEJAR_O_RODEAR;
        if (inWater) {
            recoverFromWater(sl);
        } else {
            companion.getJumpControl().jump();
        }

        // 3a. Despejar obstáculos blandos que bloquean físicamente el paso
        if (sl != null) {
            boolean broke = tryBreakObstructionsAround(sl);
            if (broke) {
                currentPhase = RecoveryPhase.RECALCULAR;
                if (companion.getNavManager() != null) {
                    companion.getNavManager().recomputePath();
                }
                currentPhase = RecoveryPhase.REANUDAR_MISION;
                return;
            }
        }

        // 3b. Rodear o sidestep
        if (attempt == 1) {
            // Intento 1: Salto y recálculo directo
            currentPhase = RecoveryPhase.RECALCULAR;
            if (companion.getNavManager() != null) {
                companion.getNavManager().recomputePath();
            }
            currentPhase = RecoveryPhase.REANUDAR_MISION;
        } else if (attempt == 2) {
            // Intento 2: Buscar dirección adyacente libre y dar un paso lateral
            Vec3 look = companion.getLookAngle();
            Direction facing = Direction.getNearest(look.x, 0, look.z);
            BlockPos blockedFront = currentPos.relative(facing);
            temporaryFailedNodes.add(blockedFront.immutable());
            if (temporaryFailedNodes.size() > 50) temporaryFailedNodes.clear();

            BlockPos bestExit = null;
            for (Direction dir : new Direction[]{facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite()}) {
                BlockPos target = currentPos.relative(dir);
                if (companion.level().getBlockState(target).isAir() &&
                    companion.level().getBlockState(target.above()).isAir()) {
                    BlockState floor = companion.level().getBlockState(target.below());
                    if (!floor.isAir() || !companion.level().getBlockState(target.below(2)).isAir()) {
                        bestExit = target;
                        break;
                    }
                }
            }

            if (bestExit != null && companion.getNavManager() != null) {
                companion.getNavManager().moveTo(bestExit, com.aicompanion.ai.nav.MovementPolicy.walk());
            }

            currentPhase = RecoveryPhase.RECALCULAR;
            if (companion.getNavManager() != null) {
                companion.getNavManager().recomputePath();
            }
            currentPhase = RecoveryPhase.REANUDAR_MISION;
        } else if (attempt >= 3) {
            // Intento 3+: Atasco severo o atrapado en geometría. Iniciar protocolo de rescate
            if (sl != null) {
                declareStuckCritical(sl);
            }
            stuckAttempts = 0;
            stuckTicks = 0;
            currentPhase = RecoveryPhase.REANUDAR_MISION;
        }
    }

    public void declareStuckCritical(ServerLevel sl) {
        if (companion == null || sl == null) return;

        // Progressive recovery is bounded: after N rescues without real progress, stop trying,
        // tell the owner and let the active task pick another target.
        BlockPos here = companion.blockPosition();
        if (lastRescuePos != null && here.distSqr(lastRescuePos) > 16.0) {
            rescuesWithoutProgress = 0;
            ownerNotifiedStuck = false;
        }
        lastRescuePos = here;
        rescuesWithoutProgress++;
        if (rescuesWithoutProgress > com.aicompanion.ai.nav.MovementPolicy.maxRescueAttempts()) {
            if (!ownerNotifiedStuck) {
                ownerNotifiedStuck = true;
                com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-ANTI-STUCK] Objetivo inalcanzable desde {} tras {} rescates; se elige otro objetivo", here, rescuesWithoutProgress);
                companion.tellOwner("Ese objetivo no es alcanzable desde " + here.getX() + " " + here.getY() + " " + here.getZ()
                        + "; busco otro por mi cuenta.");
            }
            CompanionTask task = companion.getCompanionBrain().getTaskManager().getCurrentTask();
            if (task != null) {
                task.onStuckTargetUnreachable();
            }
            if (companion.getNavManager() != null) companion.getNavManager().stop();
            rescuesWithoutProgress = 0;
            return;
        }

        com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-ANTI-STUCK] Emergency override: aborting route and launching RescueProtocolEngine (attempt {})", rescuesWithoutProgress);

        if (companion.getNavManager() != null) {
            companion.getNavManager().stop();
            if (companion.getNavManager().getBaritoneProvider() != null) {
                companion.getNavManager().getBaritoneProvider().getExecutor().clear();
            }
        }

        rescueEngine.startRescue(sl);
    }

    private BlockPos lastRescuePos = null;
    private int rescuesWithoutProgress = 0;
    private boolean ownerNotifiedStuck = false;
    private long lastNavStuckNotifyTick = -1000;

    public void triggerEmergencyRescue() {
        if (companion != null && companion.level() instanceof ServerLevel sl) {
            declareStuckCritical(sl);
        }
    }

    public void notifyNavigationStuck() {
        if (companion != null && companion.level() instanceof ServerLevel sl) {
            // The executor reports "stuck" every tick while blocked: count at most once per 2 s
            long now = companion.tickCount;
            if (now - lastNavStuckNotifyTick < 40) return;
            lastNavStuckNotifyTick = now;
            if (rescueEngine.isRescuing()) return;
            stuckAttempts++;
            if (stuckAttempts >= 3) {
                declareStuckCritical(sl);
                stuckAttempts = 0;
            } else {
                recover(stuckAttempts);
            }
        }
    }

    /** Called when the companion is clearly moving again (resets the owner notification). */
    public void notifyProgress() {
        ownerNotifiedStuck = false;
        rescuesWithoutProgress = 0;
        stalledCalls = 0;
    }

    public void notifyStalled(CompanionTask task) {
        if (companion != null && companion.level() instanceof ServerLevel sl) {
            stalledCalls++;
            if (companion.isInWater() || stalledCalls >= 2) {
                stalledCalls = 0;
                declareStuckCritical(sl);
            } else {
                tryBreakObstructionsAround(sl);
                companion.getJumpControl().jump();
            }
        }
    }

    private int stalledCalls = 0;
    private int waterNoRouteTicks = 0;
}
