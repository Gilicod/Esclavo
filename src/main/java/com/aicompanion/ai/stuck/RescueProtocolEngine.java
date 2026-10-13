package com.aicompanion.ai.stuck;

import com.aicompanion.AICompanionMod;
import com.aicompanion.ai.nav.MovementPolicy;
import com.aicompanion.ai.nav.ScaffoldBlockHelper;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.internal.baritone.costs.HazardCostEvaluator;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.inventory.EquipmentManager;
import com.aicompanion.util.BlockBreakHelper;
import com.aicompanion.util.TerrainPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Set;

/**
 * Autonomous Emergency Rescue Protocol Engine (Pilar 3).
 * Triggers when STUCK_CRITICAL is declared (1x1 hole, ravine, wedged in geometry...).
 *
 * All movement is physical: the companion walks / jumps into the next cell (no setPos teleports).
 * Pillar blocks are tracked as temporary blocks and removed later. Only natural terrain is dug when
 * protectPlayerBuilds is enabled. When every protocol fails the owner is informed with coordinates.
 *
 * - Fallback 1: Pillar Up (real jump, place block under feet once risen)
 * - Fallback 2: Staircase Tunneling (2x1 upward stairs, avoiding lava and player builds)
 */
public class RescueProtocolEngine {

    public enum Protocol {
        NONE,
        /** Open terrain: just walk / hop into a neighbouring free cell (never pillar in the open). */
        SIDESTEP,
        /** 1.13.11: in water: swim to the nearest shore, otherwise fill the water below and pillar out. */
        WATER_ESCAPE,
        PILLAR_UP,
        STAIRCASE_TUNNELING
    }

    private final CompanionEntity companion;
    private Protocol currentProtocol = Protocol.NONE;
    private int protocolTicks = 0;
    private int stepCooldown = 0;
    private int currentStep = 0;
    private static final int MAX_PILLAR_STEPS = 16;
    private static final int MAX_STAIRCASE_STEPS = 28;
    private static final int MAX_PROTOCOL_TICKS = 20 * 90; // 90 s hard cap per rescue

    private Direction staircaseDir = Direction.NORTH;
    private final Set<Direction> rejectedDirections = EnumSet.noneOf(Direction.class);
    private BlockPos currentDigTarget = null;
    private int digTicks = 0;

    // Physical step state (walk / jump into a neighbouring cell)
    private BlockPos stepTarget = null;
    private boolean stepFinishesRescue = false;
    private int stepTicks = 0;
    private final Set<BlockPos> failedSideExits = new java.util.HashSet<>();

    // Pillar state (jumped, waiting to be high enough to place the block)
    private BlockPos pillarBase = null;
    private int pillarWait = 0;
    /** Relaxed pillar: allowed outside 1x1 pits (escalation after other protocols failed / water). */
    private boolean pillarRelaxed = false;

    // 1.13.11: escalation. Each rescue tries every protocol (once) before giving up; the owner is only
    // told after several complete rescues fail, with the exact cause (no whistle request).
    private final Set<Protocol> triedProtocols = EnumSet.noneOf(Protocol.class);
    private boolean startedInWater = false;
    private int waterSwimAttempts = 0;
    private int consecutiveFailedRescues = 0;
    private int lastOwnerNotifyTick = -100000;
    private String lastFailureCause = null;
    private boolean emergencyPickaxeTried = false;
    private BlockPos rescueOrigin = null;

    public RescueProtocolEngine(CompanionEntity companion) {
        this.companion = companion;
    }

    public boolean isRescuing() {
        return currentProtocol != Protocol.NONE;
    }

    public Protocol getCurrentProtocol() {
        return currentProtocol;
    }

    private void enter(Protocol p) {
        currentProtocol = p;
        triedProtocols.add(p);
        currentStep = 0;
        stepCooldown = 0;
        pillarBase = null;
        stepTarget = null;
    }

    private boolean inWaterNow(ServerLevel level) {
        if (companion.isInWater()) return true;
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(companion.blockPosition());
        return state.is(net.minecraft.world.level.block.Blocks.WATER) || state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
    }

    public void startRescue(ServerLevel level) {
        if (level == null || companion == null || !companion.isAlive()) return;

        BlockPos currentPos = companion.blockPosition();
        this.rescueOrigin = currentPos.immutable();
        AICompanionMod.LOGGER.warn("[ESCLAVO-RESCUE] Emergency rescue initiated at {}", currentPos);

        if (currentDigTarget != null) {
            BlockBreakHelper.cancelBreaking(companion, level, currentDigTarget);
            currentDigTarget = null;
        }
        stepTarget = null;
        pillarBase = null;
        failedSideExits.clear();
        rejectedDirections.clear();
        triedProtocols.clear();
        pillarRelaxed = false;
        waterSwimAttempts = 0;
        lastFailureCause = null;
        emergencyPickaxeTried = false;
        protocolTicks = 0;
        startedInWater = inWaterNow(level);

        // 1.13.11: in water the old protocols failed ("Physical step failed" in every direction).
        if (startedInWater) {
            enter(Protocol.WATER_ESCAPE);
            AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] Selected Protocol: WATER_ESCAPE (en agua en {})", currentPos);
            return;
        }

        // Open terrain (a free neighbouring cell exists): the problem is the route, not an enclosure.
        // Pillaring here only left dirt towers behind, so just step aside and let the task re-plan.
        if (findAnyExit(level, currentPos) != null) {
            enter(Protocol.SIDESTEP);
            AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] Selected Protocol: SIDESTEP (open terrain)");
            return;
        }

        if (canPillarUp(level, currentPos, false)) {
            enter(Protocol.PILLAR_UP);
            AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] Selected Protocol: FALLBACK 1 (Pillar Up / Vertical Escape)");
            return;
        }

        initStaircaseTunneling(level);
    }

    public boolean tick(ServerLevel level) {
        if (!isRescuing() || level == null || companion == null || !companion.isAlive()) {
            return false;
        }

        protocolTicks++;
        if (protocolTicks > MAX_PROTOCOL_TICKS) {
            failRescue("tiempo de rescate agotado con " + currentProtocol);
            return isRescuing();
        }

        // A physical step in progress has priority
        if (stepTarget != null) {
            tickStep(level);
            return isRescuing();
        }

        if (currentProtocol == Protocol.SIDESTEP) {
            BlockPos exit = findAnyExit(level, companion.blockPosition());
            if (exit != null && currentStep < 4) {
                currentStep++;
                beginStep(exit, true);
            } else {
                // Every neighbour refused us: give control back silently, the task picks another target
                AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] SIDESTEP exhausted, returning control to the task");
                cancel();
            }
        } else if (currentProtocol == Protocol.WATER_ESCAPE) {
            tickWaterEscape(level);
        } else if (currentProtocol == Protocol.PILLAR_UP) {
            tickPillarUp(level);
        } else if (currentProtocol == Protocol.STAIRCASE_TUNNELING) {
            tickStaircaseTunneling(level);
        }

        return isRescuing();
    }

    // ==========================================
    // 1.13.11: WATER ESCAPE
    // ==========================================

    private void tickWaterEscape(ServerLevel level) {
        if (!inWaterNow(level) && companion.onGround()) {
            finishRescue("Fuera del agua en " + companion.blockPosition());
            return;
        }
        if (stepCooldown > 0) {
            stepCooldown--;
            return;
        }

        // 1) Swim to the nearest dry, walkable cell (up to 3 different shores)
        if (waterSwimAttempts < 3) {
            BlockPos shore = findNearestShore(level, companion.blockPosition(), 7);
            if (shore != null) {
                waterSwimAttempts++;
                AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] WATER_ESCAPE: nadando hacia la orilla {} (intento {})", shore, waterSwimAttempts);
                beginStep(shore, true);
                return;
            }
            waterSwimAttempts = 3;
        }

        // 2) No reachable shore: build footing with disposable blocks and pillar out of the water
        BlockPos feet = companion.blockPosition();
        BlockPos below = feet.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.liquid() || belowState.canBeReplaced()) {
            if (ensureScaffoldAvailable(level)) {
                placeScaffold(level, below);
                AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] WATER_ESCAPE: bloque de apoyo colocado bajo los pies en {}", below);
                stepCooldown = 4;
                return;
            }
            failRescue("estoy en agua profunda, no hay orilla cerca y no tengo ni encuentro bloques para construir un apoyo");
            return;
        }
        AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] WATER_ESCAPE: hay apoyo sólido, subiendo en pilar para salir del agua");
        pillarRelaxed = true;
        enter(Protocol.PILLAR_UP);
    }

    /** Nearest walkable dry cell within radius (dy -1..+3) reachable by swimming, closest first. */
    private BlockPos findNearestShore(ServerLevel level, BlockPos from, int radius) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    BlockPos p = from.offset(dx, dy, dz);
                    if (failedSideExits.contains(p)) continue;
                    if (!isWalkableEscape(level, p)) continue;
                    double d = from.distSqr(p) + dy * 2.0; // climbing out costs a bit more
                    if (d < bestDist) {
                        bestDist = d;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }

    // ==========================================
    // Physical step (replaces the old setPos teleports)
    // ==========================================

    private void beginStep(BlockPos target, boolean finishesRescue) {
        this.stepTarget = target.immutable();
        this.stepFinishesRescue = finishesRescue;
        this.stepTicks = 0;
    }

    private void tickStep(ServerLevel level) {
        stepTicks++;
        Vec3 c = Vec3.atBottomCenterOf(stepTarget);
        companion.getLookControl().setLookAt(c.x, c.y + 1.0, c.z);
        companion.getMoveControl().setWantedPosition(c.x, c.y, c.z, MovementPolicy.walk());
        boolean wet = companion.isInWater();
        if (stepTarget.getY() > companion.getBlockY() && (companion.onGround() || wet)) {
            companion.getJumpControl().jump();
        }
        if (wet) {
            // Ground mobs swim very slowly with MoveControl alone: add a moderate swimming impulse
            double sx = c.x - companion.getX();
            double sz = c.z - companion.getZ();
            double mag = Math.sqrt(sx * sx + sz * sz);
            if (mag > 0.05) {
                Vec3 cur = companion.getDeltaMovement();
                double vy = stepTarget.getY() >= companion.getBlockY() ? Math.max(cur.y, 0.06) : cur.y;
                if (companion.horizontalCollision && stepTarget.getY() >= companion.getBlockY()) {
                    vy = Math.max(vy, 0.3); // climb out over the shore edge
                }
                companion.setDeltaMovement(cur.x * 0.6 + sx / mag * 0.09, vy, cur.z * 0.6 + sz / mag * 0.09);
                companion.hasImpulse = true;
            }
        }

        double dx = c.x - companion.getX();
        double dz = c.z - companion.getZ();
        boolean reached = companion.blockPosition().equals(stepTarget) || (dx * dx + dz * dz < 0.2 && Math.abs(c.y - companion.getY()) < 0.6);
        if (reached) {
            BlockPos done = stepTarget;
            stepTarget = null;
            if (stepFinishesRescue) {
                finishRescue("Salida alcanzada caminando (" + done + ")");
            } else {
                currentStep++;
                stepCooldown = 2;
            }
            return;
        }
        int limit = currentProtocol == Protocol.WATER_ESCAPE ? 120 : (wet ? 70 : 40);
        if (stepTicks > limit) {
            // Could not physically enter the cell: remember it and try something else
            AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] Physical step to {} failed ({}, enAgua={})", stepTarget, currentProtocol, wet);
            failedSideExits.add(stepTarget);
            BlockPos failed = stepTarget;
            stepTarget = null;
            if (!stepFinishesRescue && currentProtocol == Protocol.STAIRCASE_TUNNELING) {
                rejectedDirections.add(staircaseDir);
                pivotStaircase(level);
            }
            if (failed != null) {
                stepCooldown = 2;
            }
        }
    }

    /**
     * Makes sure there is a placeable block: disposable block in the inventory -> mine a nearby natural block ->
     * 1.13.12 emergency: turn a log into planks -> any safe full block from the inventory (planks/logs included).
     */
    private boolean ensureScaffoldAvailable(ServerLevel level) {
        if (!companion.getSettings().isAllowBlockPlacement()) return false;
        int slot = ScaffoldBlockHelper.findBestScaffoldSlot(companion);
        if (slot == -1 && companion.getSettings().isAllowBlockBreaking()) {
            ScaffoldBlockHelper.acquireScaffoldBlockAround(companion, level);
            slot = ScaffoldBlockHelper.findBestScaffoldSlot(companion);
        }
        if (slot == -1) {
            int em = ScaffoldBlockHelper.findEmergencyScaffoldSlot(companion);
            if (em == -1 || companion.getInventoryHandler().getStackInSlot(em).is(net.minecraft.tags.ItemTags.LOGS)) {
                com.aicompanion.util.EmergencyCrafting.planksFromLog(companion); // 1 tronco = 4 bloques
            }
        }
        if (slot == -1) {
            slot = ScaffoldBlockHelper.findEmergencyScaffoldSlot(companion);
            if (slot != -1) {
                AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] Emergencia: uso {} del inventario como bloque de apoyo",
                        companion.getInventoryHandler().getStackInSlot(slot).getHoverName().getString());
            }
        }
        return slot != -1;
    }

    /**
     * Guarantees a solid floor under {@code pos} (fills water / air gaps with a disposable block).
     * Returns false when no footing could be created.
     */
    private boolean ensureFooting(ServerLevel level, BlockPos pos) {
        BlockPos floor = pos.below();
        BlockState fs = level.getBlockState(floor);
        if (fs.isSolid() || fs.isFaceSturdy(level, floor, Direction.UP)) return true;
        if (!(fs.liquid() || fs.canBeReplaced())) return false;
        if (!ensureScaffoldAvailable(level)) return false;
        placeScaffold(level, floor);
        BlockState after = level.getBlockState(floor);
        return after.isSolid();
    }

    public static boolean isEnclosedInPit(ServerLevel level, BlockPos pos) {
        int solidWalls = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(d);
            BlockState s = level.getBlockState(side);
            if (s.blocksMotion() && !s.isAir()) {
                solidWalls++;
            }
        }
        return solidWalls >= 3;
    }

    private boolean canPillarUp(ServerLevel level, BlockPos pos, boolean relaxed) {
        if (!companion.getSettings().isAllowBlockPlacement()) return false;
        // Never pillar up on open terrain! Only when strictly enclosed in a 1x1 hole or deep trench (3+ walls).
        // 1.13.11: relaxed (escalation / water) skips the enclosure check.
        if (!relaxed && !isEnclosedInPit(level, pos)) return false;
        if (!level.canSeeSky(pos.above(2))) {
            for (int dy = 2; dy <= 4; dy++) {
                BlockPos check = pos.above(dy);
                BlockState s = level.getBlockState(check);
                if (s.blocksMotion() && !s.isAir()) {
                    if (relaxed && dy == 2 && diggable(level, check) && HazardCostEvaluator.isSafeToExcavate(level, check)) {
                        continue; // the pillar loop digs its own headroom
                    }
                    if (relaxed && dy > 2) break; // there is at least one free step; good enough to try
                    return false; // Solid ceiling overhead, cannot pillar up
                }
            }
        }

        return ensureScaffoldAvailable(level);
    }

    private BlockPos findSideExit(ServerLevel level, BlockPos currentPos, boolean requireHeadroom) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = currentPos.relative(dir);
            if (failedSideExits.contains(side)) continue;
            if (isWalkableEscape(level, side) && (!requireHeadroom || !level.getBlockState(side.above()).blocksMotion())) {
                return side;
            }
        }
        return null;
    }

    /**
     * Finds any walkable escape: same level, safe step down (1-3 blocks), step up 1 block,
     * or clearing soft vegetation obstacles.
     */
    private BlockPos findAnyExit(ServerLevel level, BlockPos currentPos) {
        // 1. Same level exit
        BlockPos side = findSideExit(level, currentPos, false);
        if (side != null) return side;

        // 2. Safe step-down (descending slopes, hillsides, or stepping off a 1-3 block ledge)
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos horiz = currentPos.relative(dir);
            BlockState horizState = level.getBlockState(horiz);
            if (!horizState.blocksMotion() || canClearObstacle(level, horiz, horizState)) {
                for (int drop = 1; drop <= 3; drop++) {
                    BlockPos dropPos = horiz.below(drop);
                    if (!failedSideExits.contains(dropPos) && isWalkableEscape(level, dropPos)) {
                        return dropPos;
                    }
                }
            }
        }

        // 3. Step up 1 block
        boolean headFree = !level.getBlockState(currentPos.above(2)).blocksMotion();
        if (headFree) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos up = currentPos.relative(dir).above();
                if (!failedSideExits.contains(up) && isWalkableEscape(level, up)) {
                    return up;
                }
            }
        }

        // 4. Clear blocking soft obstacle (leaves/vines) to open an exit
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos check = currentPos.relative(dir);
            BlockState s = level.getBlockState(check);
            if (canClearObstacle(level, check, s)) {
                BlockBreakHelper.finishBreaking(companion, level, check);
                if (isWalkableEscape(level, check)) return check;
            }
        }

        return null;
    }

    private boolean canClearObstacle(ServerLevel level, BlockPos pos, BlockState state) {
        if (state == null || state.isAir() || !state.blocksMotion()) return false;
        if (!companion.getSettings().isAllowBlockBreaking()) return false;
        if (!TerrainPolicy.mayBreakImplicitly(level, pos, state, companion.getTemporaryBlocks())) return false;
        float speed = state.getDestroySpeed(level, pos);
        return speed >= 0 && speed <= 0.8f;
    }

    private int pillarJumpFails = 0;

    private void tickPillarUp(ServerLevel level) {
        BlockPos currentPos = companion.blockPosition();
        boolean wet = inWaterNow(level);

        // 1.13.11: started in water and now standing dry on the pillar -> let the task re-plan from here
        if (startedInWater && !wet && companion.onGround() && pillarBase == null && currentStep > 0) {
            finishRescue("Salí del agua subiendo en pilar (" + currentPos + ")");
            return;
        }

        // Waiting for the jump to rise high enough to place the block below
        if (pillarBase != null) {
            if (companion.getY() >= pillarBase.getY() + 1.0 && level.getBlockState(pillarBase).canBeReplaced()) {
                placeScaffold(level, pillarBase);
                pillarBase = null;
                pillarJumpFails = 0;
                currentStep++;
                stepCooldown = 4;
            } else if (++pillarWait > (wet ? 40 : 14) || (companion.onGround() && pillarWait > 4)) {
                pillarBase = null; // jump did not rise (ceiling / slab) -> retry or fall back
                stepCooldown = 4;
                if (++pillarJumpFails >= 6) {
                    pillarJumpFails = 0;
                    AICompanionMod.LOGGER.warn("[ESCLAVO-RESCUE] Pillar Up: el salto no sube (techo/agua), cambio de estrategia");
                    initStaircaseTunneling(level);
                }
            }
            return;
        }

        // A safe horizontal exit is reachable: walk onto it (only after at least one pillar step has been completed)
        BlockPos exit = findSideExit(level, currentPos, false);
        if (exit != null && currentStep > 0) {
            beginStep(exit, true);
            return;
        }

        if (currentStep >= MAX_PILLAR_STEPS) {
            AICompanionMod.LOGGER.warn("[ESCLAVO-RESCUE] Pillar Up reached step limit without escape, transitioning to Staircase Tunneling");
            initStaircaseTunneling(level);
            return;
        }

        if (stepCooldown > 0) {
            stepCooldown--;
            return;
        }

        if (!ensureScaffoldAvailable(level)) {
            AICompanionMod.LOGGER.warn("[ESCLAVO-RESCUE] Out of disposable blocks for Pillar Up, transitioning to Staircase Tunneling");
            lastFailureCause = "no tengo bloques para construir y no hay tierra/piedra natural cerca que pueda picar";
            initStaircaseTunneling(level);
            return;
        }

        // Headroom: only natural terrain may be broken (timed)
        BlockPos headClear = currentPos.above(2);
        BlockState headState = level.getBlockState(headClear);
        if (!headState.isAir() && headState.blocksMotion()) {
            if (TerrainPolicy.mayBreakImplicitly(level, headClear, headState, companion.getTemporaryBlocks())
                    && HazardCostEvaluator.isSafeToExcavate(level, headClear)) {
                digTimed(level, headClear, headState);
            } else {
                lastFailureCause = "el techo sobre mí es " + headState.getBlock().getName().getString() + " y no puedo romperlo";
                initStaircaseTunneling(level);
            }
            return;
        }

        if (!companion.onGround() && !wet) return;
        companion.getLookControl().setLookAt(companion.getX(), companion.getY() - 1.0, companion.getZ());
        companion.getJumpControl().jump();
        if (wet) {
            Vec3 cur = companion.getDeltaMovement();
            companion.setDeltaMovement(cur.x * 0.5, Math.max(cur.y, 0.12), cur.z * 0.5);
            companion.hasImpulse = true;
        }
        pillarBase = currentPos.immutable();
        pillarWait = 0;
    }

    private void placeScaffold(ServerLevel level, BlockPos pos) {
        int slot = ScaffoldBlockHelper.findEmergencyScaffoldSlot(companion);
        if (slot == -1) return;
        if (!level.getBlockState(pos).canBeReplaced()) return; // never overwrite a real block
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        ItemStack stack = inv.getStackInSlot(slot);
        if (stack.getItem() instanceof BlockItem bi) {
            BlockState placeState = bi.getBlock().defaultBlockState();
            level.setBlock(pos, placeState, 3);
            SoundType st = placeState.getSoundType();
            level.playSound(null, pos, st.getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            companion.swing(InteractionHand.MAIN_HAND);
            companion.getTemporaryBlocks().track(pos, bi.getBlock());
            stack.shrink(1);
            if (stack.isEmpty()) {
                inv.setStackInSlot(slot, ItemStack.EMPTY);
            }
            AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] Bloque de apoyo {} colocado en {} ({})", bi.getBlock().getName().getString(), pos, currentProtocol);
        }
    }

    /** Timed (non-instant) break of one block. Returns true when the block is gone. */
    private boolean digTimed(ServerLevel level, BlockPos target, BlockState state) {
        com.aicompanion.ai.equipment.ToolSelectionPolicy.ensureToolForBlock(companion, state);
        int toolSlot = EquipmentManager.findBestPickaxeSlot(companion, state);
        if (toolSlot == -1 && !emergencyPickaxeTried && state.requiresCorrectToolForDrops()) {
            emergencyPickaxeTried = true;
            if (com.aicompanion.util.EmergencyCrafting.ensurePickaxe(companion)) {
                toolSlot = EquipmentManager.findBestPickaxeSlot(companion, state);
            }
        }
        if (toolSlot != -1 && com.aicompanion.ai.equipment.ToolSelectionPolicy.isPickaxePreferred(state)) {
            EquipmentManager.swapMainHandSafely(companion, toolSlot);
        }
        if (currentDigTarget == null || !currentDigTarget.equals(target)) {
            if (currentDigTarget != null) {
                BlockBreakHelper.cancelBreaking(companion, level, currentDigTarget);
            }
            currentDigTarget = target.immutable();
            digTicks = 0;
        }
        digTicks++;
        int requiredTicks = BlockBreakHelper.calculateBreakTicks(companion, target, state);
        if (requiredTicks <= 0) {
            currentDigTarget = null;
            return false;
        }
        if (BlockBreakHelper.tickBreaking(companion, level, target, digTicks, requiredTicks)) {
            BlockBreakHelper.finishBreaking(companion, level, target);
            currentDigTarget = null;
            digTicks = 0;
            return true;
        }
        return false;
    }

    // ==========================================
    // PROTOCOLO FALLBACK 2: STAIRCASE TUNNELING
    // ==========================================

    private void initStaircaseTunneling(ServerLevel level) {
        BlockPos current = companion.blockPosition();
        if (level.canSeeSky(current.above(2)) || (!isEnclosedInPit(level, current) && findAnyExit(level, current) != null)) {
            AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] Open terrain / surface at {}, skipping Staircase Tunneling and returning control to task", current);
            cancel();
            if (companion.getNavManager() != null) {
                companion.getNavManager().recomputePath();
            }
            return;
        }
        if (triedProtocols.contains(Protocol.STAIRCASE_TUNNELING) && currentProtocol != Protocol.STAIRCASE_TUNNELING) {
            failRescue(lastFailureCause != null ? lastFailureCause : "la escalera de emergencia ya falló");
            return;
        }
        enter(Protocol.STAIRCASE_TUNNELING);
        this.currentDigTarget = null;
        this.digTicks = 0;

        Direction dir = chooseBestStaircaseDirection(level, current);
        if (dir == null) {
            failRescue("no hay dirección segura para excavar una salida (bloques protegidos, indestructibles o lava)");
            return;
        }
        this.staircaseDir = dir;
        AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] Selected Protocol: FALLBACK 2 (Staircase Tunneling heading {})", staircaseDir);
    }

    private void pivotStaircase(ServerLevel level) {
        Direction dir = chooseBestStaircaseDirection(level, companion.blockPosition());
        if (dir == null) {
            failRescue("todas las direcciones están bloqueadas por construcciones o peligros");
            return;
        }
        staircaseDir = dir;
        currentDigTarget = null;
        digTicks = 0;
    }

    private Direction chooseBestStaircaseDirection(ServerLevel level, BlockPos current) {
        Direction best = null;
        double bestScore = -Double.MAX_VALUE;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (rejectedDirections.contains(dir)) continue;
            BlockPos step = current.relative(dir).above(1);
            if (!HazardCostEvaluator.isSafeToExcavate(level, step) ||
                !HazardCostEvaluator.isSafeToExcavate(level, step.above(1))) {
                continue; // Avoid lava
            }
            if (!diggable(level, step) || !diggable(level, step.above()) || !diggable(level, current.above(2))) {
                continue; // would break a player build
            }

            double score = 100.0;
            score -= HazardCostEvaluator.getExcavationHazardPenalty(level, step);
            score -= HazardCostEvaluator.getExcavationHazardPenalty(level, step.above(1));

            if (companion.getOwnerPlayer() != null) {
                Vec3 ownerPos = companion.getOwnerPlayer().position();
                Vec3 stepVec = Vec3.atCenterOf(step);
                score -= stepVec.distanceToSqr(ownerPos) * 0.05;
            }

            if (score > bestScore) {
                bestScore = score;
                best = dir;
            }
        }
        return best;
    }

    private boolean diggable(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir() || !s.blocksMotion()) return true;
        return companion.getSettings().isAllowBlockBreaking()
                && TerrainPolicy.mayBreakImplicitly(level, p, s, companion.getTemporaryBlocks());
    }

    private void tickStaircaseTunneling(ServerLevel level) {
        BlockPos currentPos = companion.blockPosition();

        if (level.canSeeSky(currentPos.above(2))) {
            finishRescue("Superficie alcanzada con éxito (Y=" + currentPos.getY() + ")");
            return;
        }

        BlockPos exit = findSideExit(level, currentPos, true);
        if (exit != null && currentStep > 0) {
            beginStep(exit, true);
            return;
        }

        if (currentStep >= MAX_STAIRCASE_STEPS) {
            failRescue("límite de pasos de la escalera de emergencia");
            return;
        }

        if (stepCooldown > 0) {
            stepCooldown--;
            return;
        }

        BlockPos nextStep = currentPos.relative(staircaseDir).above(1);
        BlockPos[] blocksToClear = new BlockPos[]{
                currentPos.above(2),
                nextStep.above(1),
                nextStep
        };

        for (BlockPos target : blocksToClear) {
            BlockState state = level.getBlockState(target);
            if (!state.isAir() && state.blocksMotion()) {
                if (!HazardCostEvaluator.isSafeToExcavate(level, target) || !diggable(level, target)) {
                    AICompanionMod.LOGGER.warn("[ESCLAVO-RESCUE] Unsafe/protected block at {}, pivoting staircase direction", target);
                    rejectedDirections.add(staircaseDir);
                    pivotStaircase(level);
                    return;
                }
                digTimed(level, target, state);
                return; // one block at a time
            }
        }

        // Passage clear: make sure there is something to stand on (water / cave gap -> disposable block)
        if (!ensureFooting(level, nextStep)) {
            AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] Sin apoyo bajo {} y sin bloques para construirlo, pivotando", nextStep);
            lastFailureCause = "no hay suelo firme y no tengo bloques para construir un apoyo";
            rejectedDirections.add(staircaseDir);
            pivotStaircase(level);
            return;
        }
        // physically jump/walk up into the next step
        beginStep(nextStep, false);
    }

    public static boolean isWalkableEscape(ServerLevel level, BlockPos pos) {
        if (pos == null || level == null) return false;
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());

        if (feet.blocksMotion() || head.blocksMotion() || feet.liquid()) return false;
        if (!floor.isSolid() && !floor.isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        if (HazardCostEvaluator.isLethalHazard(level, pos) || floor.is(Blocks.MAGMA_BLOCK)) return false;

        return true;
    }

    public void cancel() {
        if (currentDigTarget != null && companion != null && companion.level() instanceof ServerLevel sl) {
            BlockBreakHelper.cancelBreaking(companion, sl, currentDigTarget);
            currentDigTarget = null;
        }
        currentProtocol = Protocol.NONE;
        protocolTicks = 0;
        stepTarget = null;
        pillarBase = null;
    }

    private void finishRescue(String reason) {
        if (rescueOrigin != null && !startedInWater && currentProtocol != Protocol.SIDESTEP) {
            double distSqr = companion.blockPosition().distSqr(rescueOrigin);
            if (distSqr < 2.25 && companion.getBlockY() <= rescueOrigin.getY()) {
                AICompanionMod.LOGGER.warn("[ESCLAVO-RESCUE] False escape detected at {}: still within 1.5 blocks of origin {}. Continuing rescue.", companion.blockPosition(), rescueOrigin);
                if (currentProtocol == Protocol.PILLAR_UP && currentStep < MAX_PILLAR_STEPS) {
                    stepCooldown = 2;
                    return;
                } else if (currentProtocol != Protocol.STAIRCASE_TUNNELING && companion.level() instanceof ServerLevel sl) {
                    initStaircaseTunneling(sl);
                    return;
                }
            }
        }
        AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] Rescue Protocol FINISHED: {}", reason);
        consecutiveFailedRescues = 0;
        cancel();
        if (companion.getNavManager() != null) {
            companion.getNavManager().recomputePath();
        }
    }

    /**
     * 1.13.11: a failed protocol no longer gives up immediately. Every other untried protocol is attempted
     * first (water escape, relaxed pillar with blocks from the inventory or mined nearby, staircase in any
     * direction). Only when all of them fail the rescue ends; the owner is informed after 3 consecutive
     * failed rescues, with the exact cause and without asking for the whistle.
     */
    private void failRescue(String reason) {
        AICompanionMod.LOGGER.warn("[ESCLAVO-RESCUE] Protocolo {} falló: {}", currentProtocol, reason);
        if (currentProtocol != Protocol.NONE) triedProtocols.add(currentProtocol);
        if (lastFailureCause == null || reason.startsWith("estoy en agua")) lastFailureCause = reason;
        if (currentDigTarget != null && companion.level() instanceof ServerLevel sl0) {
            BlockBreakHelper.cancelBreaking(companion, sl0, currentDigTarget);
            currentDigTarget = null;
        }
        stepTarget = null;
        pillarBase = null;

        if (companion.level() instanceof ServerLevel level && companion.isAlive()) {
            BlockPos here = companion.blockPosition();
            if (inWaterNow(level) && !triedProtocols.contains(Protocol.WATER_ESCAPE)) {
                waterSwimAttempts = 0;
                enter(Protocol.WATER_ESCAPE);
                protocolTicks = 0;
                AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] Escalando a WATER_ESCAPE");
                return;
            }
            if (!triedProtocols.contains(Protocol.PILLAR_UP) && canPillarUp(level, here, true)) {
                pillarRelaxed = true;
                enter(Protocol.PILLAR_UP);
                protocolTicks = 0;
                AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] Escalando a PILLAR_UP (modo relajado, bloques del inventario/terreno)");
                return;
            }
            if (!triedProtocols.contains(Protocol.STAIRCASE_TUNNELING)) {
                rejectedDirections.clear();
                protocolTicks = 0;
                initStaircaseTunneling(level);
                return;
            }
        }

        consecutiveFailedRescues++;
        BlockPos p = companion.blockPosition();
        String cause = lastFailureCause != null ? lastFailureCause : reason;
        AICompanionMod.LOGGER.warn("[ESCLAVO-RESCUE] Todos los protocolos fallaron en {} (rescates fallidos seguidos: {}). Causa: {}",
                p, consecutiveFailedRescues, cause);
        if (consecutiveFailedRescues >= 3 && companion.tickCount - lastOwnerNotifyTick > 2400) {
            lastOwnerNotifyTick = companion.tickCount;
            companion.tellOwner("No logro salir de " + p.getX() + " " + p.getY() + " " + p.getZ()
                    + ": " + cause + ". Sigo intentando por otra ruta.");
        }
        cancel();
    }
}
