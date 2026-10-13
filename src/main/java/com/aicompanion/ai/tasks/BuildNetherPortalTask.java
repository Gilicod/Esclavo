package com.aicompanion.ai.tasks;

import com.aicompanion.AICompanionMod;
import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.ai.nav.MovementPolicy;
import com.aicompanion.ai.planning.PrerequisitePlanner;
import com.aicompanion.ai.planning.RecipeDependencyGraph;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.inventory.EquipmentManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise Autonomous Nether Portal Construction Task.
 * Intelligently reuses existing portals or partially built frames,
 * manages obsidian inventory without item deletion bugs, safely ignites
 * with sound and animation, and pushes missing prerequisite tasks dynamically.
 */
public class BuildNetherPortalTask extends CompanionTask {

    private enum Step {
        CHECK_EXISTING,
        CHECK_RESOURCES,
        FIND_LOCATION,
        CLEAR_AREA,
        PLACE_FRAME,
        IGNITE,
        DONE
    }

    private Step currentStep = Step.CHECK_EXISTING;
    private BlockPos originPos = null;
    private Direction.Axis frameAxis = Direction.Axis.X;
    private final List<BlockPos> framePositions = new ArrayList<>();
    private int placingIndex = 0;
    private int actionCooldown = 0;
    private int timeoutTicks = 0;

    public BuildNetherPortalTask(CompanionEntity companion) {
        super(companion);
        this.stage = "PORTAL_BUILDING";
        this.substate = "Comprobando portales existentes y materiales";
    }

    @Override
    public List<CompanionTask> checkPreconditions() {
        List<CompanionTask> prereqs = new ArrayList<>();
        if (!(companion.level() instanceof ServerLevel level)) return prereqs;

        // Check if an active portal is already known or nearby
        String currentDim = level.dimension().location().toString();
        BlockPos memPortal = companion.getMemory().getWorldModel().getDimensionalPortal(currentDim);
        if (memPortal != null && level.isLoaded(memPortal) && level.getBlockState(memPortal).is(Blocks.NETHER_PORTAL)) {
            return prereqs; // Already satisfied!
        }

        int neededObs = 10;
        PortalCandidate cand = detectPartialFrame(level, companion.blockPosition(), 16);
        if (cand != null) {
            neededObs = Math.max(0, 10 - cand.placedCount);
        }

        int obsCount = countItem(Items.OBSIDIAN);
        if (obsCount < neededObs) {
            int missingObs = neededObs - obsCount;
            int pickTier = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
            if (pickTier < RecipeDependencyGraph.TIER_DIAMOND) {
                prereqs.addAll(PrerequisitePlanner.resolvePickaxePrerequisites(companion, pickTier, RecipeDependencyGraph.TIER_DIAMOND));
            }
            prereqs.add(new MineTask(companion, MiningTarget.of("obsidian"), missingObs));
        }

        if (findIgnitionSlot() == -1) {
            int iron = countItem(Items.IRON_INGOT);
            int flint = countItem(Items.FLINT);
            if (iron < 1) {
                int pickTier = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
                if (pickTier < RecipeDependencyGraph.TIER_STONE) {
                    prereqs.addAll(PrerequisitePlanner.resolvePickaxePrerequisites(companion, pickTier, RecipeDependencyGraph.TIER_STONE));
                }
                prereqs.add(new SmeltTask(companion, new ResourceLocation("minecraft", "raw_iron"), 1));
            }
            if (flint < 1) {
                prereqs.add(new MineTask(companion, MiningTarget.of("gravel"), 1));
            }
            prereqs.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.FLINT_AND_STEEL), 1));
        }

        return prereqs;
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.BUILDING);
        markProgress();
    }

    @Override
    public void tick() {
        if (paused || complete) return;

        if (++timeoutTicks > 20 * 180) { // 3 minutes timeout
            AICompanionMod.LOGGER.warn("[ESCLAVO-PORTAL] BuildNetherPortalTask timed out.");
            fail("No pude terminar de construir el portal al Nether a tiempo.");
            return;
        }

        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }

        ServerLevel level = (ServerLevel) companion.level();

        switch (currentStep) {
            case CHECK_EXISTING -> {
                String dimKey = level.dimension().location().toString();
                // 1. Check dimensional portal memory
                BlockPos remembered = companion.getMemory().getWorldModel().getDimensionalPortal(dimKey);
                if (remembered != null && level.isLoaded(remembered) && level.getBlockState(remembered).is(Blocks.NETHER_PORTAL)) {
                    AICompanionMod.LOGGER.info("[ESCLAVO-PORTAL] Reusing existing remembered portal at {}", remembered);
                    companion.tellOwner("¡Ya conozco un portal al Nether activo aquí cerca en " + remembered.toShortString() + "!");
                    complete = true;
                    return;
                }

                // 2. Scan physical environment for active Nether portal
                BlockPos activeNearby = findActivePortalNearby(level, companion.blockPosition(), 24);
                if (activeNearby != null) {
                    AICompanionMod.LOGGER.info("[ESCLAVO-PORTAL] Found existing active portal at {}", activeNearby);
                    companion.getMemory().getWorldModel().rememberDimensionalPortal(dimKey, activeNearby);
                    companion.getMemory().getWorldModel().rememberPOI(activeNearby, Blocks.NETHER_PORTAL, level.getGameTime());
                    companion.tellOwner("¡Encontré un portal al Nether ya activo en " + activeNearby.toShortString() + "!");
                    complete = true;
                    return;
                }

                // 3. Scan for a partially built obsidian frame
                PortalCandidate candidate = detectPartialFrame(level, companion.blockPosition(), 16);
                if (candidate != null && candidate.placedCount >= 2) {
                    AICompanionMod.LOGGER.info("[ESCLAVO-PORTAL] Found partial frame at {} with {}/10 obsidian.",
                            candidate.origin, candidate.placedCount);
                    this.originPos = candidate.origin;
                    this.frameAxis = candidate.axis;
                    setupFramePositions();
                    companion.tellOwner("Encontré un marco de portal a medio construir con " + candidate.placedCount + "/10 bloques. Lo completaré.");
                    currentStep = Step.CHECK_RESOURCES;
                    actionCooldown = 5;
                    return;
                }

                // 4. No existing partial frame, find new location
                currentStep = Step.FIND_LOCATION;
                this.substate = "Buscando terreno adecuado para el portal";
                actionCooldown = 5;
            }

            case FIND_LOCATION -> {
                BlockPos found = findSafePortalSite(level, companion.blockPosition());
                if (found == null) {
                    fail("No encontré un espacio seguro y plano para construir el portal al Nether aquí cerca.");
                    return;
                }

                this.originPos = found;
                setupFramePositions();
                currentStep = Step.CHECK_RESOURCES;
                this.substate = "Comprobando materiales requeridos";
                actionCooldown = 5;
            }

            case CHECK_RESOURCES -> {
                int neededObsidian = calculateRemainingObsidianNeeded(level);
                int ownedObsidian = countItem(Items.OBSIDIAN);
                int flintSlot = findIgnitionSlot();

                if (ownedObsidian < neededObsidian || flintSlot == -1) {
                    List<CompanionTask> missing = new ArrayList<>();
                    if (ownedObsidian < neededObsidian) {
                        int delta = neededObsidian - ownedObsidian;
                        int pickTier = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
                        if (pickTier < RecipeDependencyGraph.TIER_DIAMOND) {
                            missing.addAll(PrerequisitePlanner.resolvePickaxePrerequisites(companion, pickTier, RecipeDependencyGraph.TIER_DIAMOND));
                        }
                        missing.add(new MineTask(companion, MiningTarget.of("obsidian"), delta));
                    }
                    if (flintSlot == -1) {
                        int iron = countItem(Items.IRON_INGOT);
                        int flint = countItem(Items.FLINT);
                        if (iron < 1) {
                            int pickTier = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
                            if (pickTier < RecipeDependencyGraph.TIER_STONE) {
                                missing.addAll(PrerequisitePlanner.resolvePickaxePrerequisites(companion, pickTier, RecipeDependencyGraph.TIER_STONE));
                            }
                            missing.add(new SmeltTask(companion, new ResourceLocation("minecraft", "raw_iron"), 1));
                        }
                        if (flint < 1) {
                            missing.add(new MineTask(companion, MiningTarget.of("gravel"), 1));
                        }
                        missing.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.FLINT_AND_STEEL), 1));
                    }

                    if (!missing.isEmpty() && companion.getCompanionBrain() != null) {
                        companion.tellOwner("Preparando materiales para el portal (" +
                                (ownedObsidian < neededObsidian ? (neededObsidian - ownedObsidian) + " obsidiana" : "") +
                                (flintSlot == -1 ? " mechero" : "") + ")...");
                        companion.getCompanionBrain().getTaskManager().pushSubtasks(missing);
                        return;
                    } else {
                        fail("Necesito obsidiana y un mechero para construir el portal.");
                        return;
                    }
                }

                currentStep = Step.CLEAR_AREA;
                this.substate = "Despejando el área de construcción";
                actionCooldown = 5;
            }

            case CLEAR_AREA -> {
                // Clear any non-solid / replaceable blocks inside the frame and interior
                for (int w = 0; w < 4; w++) {
                    for (int h = 0; h < 5; h++) {
                        BlockPos p = getPortalCoord(originPos, frameAxis, w, h);
                        BlockState state = level.getBlockState(p);
                        if (!state.isAir() && state.canBeReplaced() && !state.is(Blocks.OBSIDIAN)) {
                            level.destroyBlock(p, false);
                        }
                    }
                }
                currentStep = Step.PLACE_FRAME;
                this.placingIndex = 0;
                this.substate = "Colocando marco de obsidiana";
                actionCooldown = 5;
            }

            case PLACE_FRAME -> {
                if (placingIndex >= framePositions.size()) {
                    currentStep = Step.IGNITE;
                    this.substate = "Encendiendo portal con mechero";
                    actionCooldown = 10;
                    return;
                }

                BlockPos targetPos = framePositions.get(placingIndex);
                BlockState curState = level.getBlockState(targetPos);
                if (curState.is(Blocks.OBSIDIAN)) {
                    // Already obsidian, advance immediately without consuming item
                    placingIndex++;
                    return;
                }

                double distSq = companion.blockPosition().distSqr(targetPos);
                if (distSq > 14.0) {
                    companion.getNavManager().moveTo(targetPos, MovementPolicy.walk());
                    actionCooldown = 8;
                    return;
                }

                // Check obsidian availability across ALL inventory slots (including main hand)
                int obsSlot = findItemSlot(Items.OBSIDIAN);
                if (obsSlot == -1) {
                    // Calculate remaining blocks to place and dynamically push MineTask prerequisite
                    int remaining = calculateRemainingObsidianNeeded(level);
                    if (remaining > 0 && companion.getCompanionBrain() != null) {
                        List<CompanionTask> missing = new ArrayList<>();
                        int pickTier = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
                        if (pickTier < RecipeDependencyGraph.TIER_DIAMOND) {
                            missing.addAll(PrerequisitePlanner.resolvePickaxePrerequisites(companion, pickTier, RecipeDependencyGraph.TIER_DIAMOND));
                        }
                        missing.add(new MineTask(companion, MiningTarget.of("obsidian"), remaining));
                        companion.tellOwner("Faltan " + remaining + " de obsidiana para terminar el portal. Iré a conseguirla...");
                        companion.getCompanionBrain().getTaskManager().pushSubtasks(missing);
                        return;
                    }
                    fail("Me quedé sin obsidiana mientras construía el marco del portal.");
                    return;
                }

                // Swap obsidian into main hand if not already held
                if (obsSlot != CompanionInventoryHandler.SLOT_MAINHAND) {
                    EquipmentManager.swapMainHandSafely(companion, obsSlot);
                }

                companion.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

                // Place obsidian block in world
                boolean placed = level.setBlock(targetPos, Blocks.OBSIDIAN.defaultBlockState(), 3);
                if (placed || level.getBlockState(targetPos).is(Blocks.OBSIDIAN)) {
                    // Only consume obsidian after block placement is confirmed
                    consumeOneObsidianSafely();
                    companion.swing(InteractionHand.MAIN_HAND);
                    companion.playSound(SoundEvents.STONE_PLACE, 1.0F, 0.8F);
                    placingIndex++;
                }

                actionCooldown = 6;
            }

            case IGNITE -> {
                BlockPos insideBottom = getPortalCoord(originPos, frameAxis, 1, 1);
                BlockPos altInside = getPortalCoord(originPos, frameAxis, 2, 1);

                // Check if already ignited
                if (level.getBlockState(insideBottom).is(Blocks.NETHER_PORTAL) || level.getBlockState(altInside).is(Blocks.NETHER_PORTAL)) {
                    registerPortalSuccess(level, insideBottom);
                    return;
                }

                double distSq = companion.blockPosition().distSqr(insideBottom);
                if (distSq > 9.0) {
                    companion.getNavManager().moveTo(insideBottom, MovementPolicy.walk());
                    actionCooldown = 6;
                    return;
                }

                int flintSlot = findIgnitionSlot();
                if (flintSlot == -1) {
                    // Push craft task for lighter
                    if (companion.getCompanionBrain() != null) {
                        List<CompanionTask> craftLighter = new ArrayList<>();
                        craftLighter.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.FLINT_AND_STEEL), 1));
                        companion.getCompanionBrain().getTaskManager().pushSubtasks(craftLighter);
                        return;
                    }
                    fail("No encontré mechero para encender el portal.");
                    return;
                }

                if (flintSlot != CompanionInventoryHandler.SLOT_MAINHAND) {
                    EquipmentManager.swapMainHandSafely(companion, flintSlot);
                }

                companion.getLookControl().setLookAt(insideBottom.getX() + 0.5, insideBottom.getY() + 0.5, insideBottom.getZ() + 0.5);
                companion.swing(InteractionHand.MAIN_HAND);
                companion.playSound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);

                // Set fire to inner base block; vanilla validates the obsidian frame and forms NETHER_PORTAL
                level.setBlock(insideBottom, Blocks.FIRE.defaultBlockState(), 11);

                // Safely damage flint & steel or consume fire charge from main hand
                CompanionInventoryHandler inv = companion.getInventoryHandler();
                ItemStack held = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
                if (held.isDamageableItem()) {
                    held.hurtAndBreak(1, companion, (c) -> {});
                } else if (held.is(Items.FIRE_CHARGE)) {
                    held.shrink(1);
                }
                inv.setStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND, held.isEmpty() ? ItemStack.EMPTY : held);
                companion.setItemSlot(EquipmentSlot.MAINHAND, held.isEmpty() ? ItemStack.EMPTY : held);

                // Verify portal creation
                if (level.getBlockState(insideBottom).is(Blocks.NETHER_PORTAL)) {
                    registerPortalSuccess(level, insideBottom);
                } else {
                    // Try adjacent interior block
                    level.setBlock(altInside, Blocks.FIRE.defaultBlockState(), 11);
                    if (level.getBlockState(altInside).is(Blocks.NETHER_PORTAL)) {
                        registerPortalSuccess(level, altInside);
                    } else {
                        AICompanionMod.LOGGER.warn("[ESCLAVO-PORTAL] Could not ignite portal at {}. Frame may be blocked.", insideBottom);
                        fail("Terminé de colocar la obsidiana, pero el portal no se activó. Revisa que no haya bloques estorbando el marco.");
                    }
                }
            }

            case DONE -> complete = true;
        }
    }

    private void registerPortalSuccess(ServerLevel level, BlockPos pos) {
        String dimKey = level.dimension().location().toString();
        AICompanionMod.LOGGER.info("[ESCLAVO-PORTAL] Nether portal successfully active at {}", pos);
        companion.getMemory().getWorldModel().rememberDimensionalPortal(dimKey, pos);
        companion.getMemory().getWorldModel().rememberPOI(pos, Blocks.NETHER_PORTAL, level.getGameTime());
        companion.getMemory().getWorldModel().rememberLocation("nether_portal", pos);
        companion.tellOwner("¡He construido y encendido un portal al Nether con éxito!");
        currentStep = Step.DONE;
        complete = true;
    }

    private int calculateRemainingObsidianNeeded(ServerLevel level) {
        if (framePositions.isEmpty()) return 10;
        int missing = 0;
        for (BlockPos p : framePositions) {
            if (!level.getBlockState(p).is(Blocks.OBSIDIAN)) {
                missing++;
            }
        }
        return missing;
    }

    private boolean consumeOneObsidianSafely() {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        // 1. Check main hand
        ItemStack main = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        if (main.is(Items.OBSIDIAN)) {
            main.shrink(1);
            ItemStack remaining = main.isEmpty() ? ItemStack.EMPTY : main;
            inv.setStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND, remaining);
            companion.setItemSlot(EquipmentSlot.MAINHAND, remaining);
            return true;
        }
        // 2. Check main inventory slots (0..35)
        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.is(Items.OBSIDIAN)) {
                stack.shrink(1);
                inv.setStackInSlot(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
                return true;
            }
        }
        // 3. Check offhand
        ItemStack off = inv.getStackInSlot(CompanionInventoryHandler.SLOT_OFFHAND);
        if (off.is(Items.OBSIDIAN)) {
            off.shrink(1);
            ItemStack remaining = off.isEmpty() ? ItemStack.EMPTY : off;
            inv.setStackInSlot(CompanionInventoryHandler.SLOT_OFFHAND, remaining);
            companion.setItemSlot(EquipmentSlot.OFFHAND, remaining);
            return true;
        }
        return false;
    }

    private void setupFramePositions() {
        framePositions.clear();
        // 10 Obsidian budget frame:
        // Bottom (width 1 and 2, height 0)
        framePositions.add(getPortalCoord(originPos, frameAxis, 1, 0));
        framePositions.add(getPortalCoord(originPos, frameAxis, 2, 0));

        // Left pillar (width 0, height 1, 2, 3)
        framePositions.add(getPortalCoord(originPos, frameAxis, 0, 1));
        framePositions.add(getPortalCoord(originPos, frameAxis, 0, 2));
        framePositions.add(getPortalCoord(originPos, frameAxis, 0, 3));

        // Right pillar (width 3, height 1, 2, 3)
        framePositions.add(getPortalCoord(originPos, frameAxis, 3, 1));
        framePositions.add(getPortalCoord(originPos, frameAxis, 3, 2));
        framePositions.add(getPortalCoord(originPos, frameAxis, 3, 3));

        // Top row (width 1 and 2, height 4)
        framePositions.add(getPortalCoord(originPos, frameAxis, 1, 4));
        framePositions.add(getPortalCoord(originPos, frameAxis, 2, 4));
    }

    public static BlockPos getPortalCoord(BlockPos origin, Direction.Axis axis, int width, int height) {
        if (axis == Direction.Axis.X) {
            return origin.offset(width, height, 0);
        } else {
            return origin.offset(0, height, width);
        }
    }

    private BlockPos findActivePortalNearby(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dy = -6; dy <= 6; dy += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(Blocks.NETHER_PORTAL)) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    public static record PortalCandidate(BlockPos origin, Direction.Axis axis, int placedCount) {}

    private PortalCandidate detectPartialFrame(ServerLevel level, BlockPos center, int radius) {
        PortalCandidate best = null;
        int maxPlaced = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(Blocks.OBSIDIAN)) {
                        // Test candidate frame orientations anchored with p
                        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
                            for (int testW = 0; testW < 4; testW++) {
                                for (int testH = 0; testH < 5; testH++) {
                                    BlockPos candidateOrigin = (axis == Direction.Axis.X)
                                            ? p.offset(-testW, -testH, 0)
                                            : p.offset(0, -testH, -testW);

                                    if (canCompleteFrameAt(level, candidateOrigin, axis)) {
                                        int count = countObsidianInFrame(level, candidateOrigin, axis);
                                        if (count > maxPlaced) {
                                            maxPlaced = count;
                                            best = new PortalCandidate(candidateOrigin, axis, count);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean canCompleteFrameAt(ServerLevel level, BlockPos origin, Direction.Axis axis) {
        // Ground must be solid
        for (int w = 0; w < 4; w++) {
            BlockPos ground = getPortalCoord(origin, axis, w, -1);
            BlockState gs = level.getBlockState(ground);
            if (!gs.isSolid() || gs.liquid()) return false;
        }

        // Frame positions must be obsidian or replaceable
        for (int w = 0; w < 4; w++) {
            for (int h = 0; h < 5; h++) {
                BlockPos p = getPortalCoord(origin, axis, w, h);
                BlockState s = level.getBlockState(p);
                boolean isFrame = (w == 0 || w == 3 || h == 0 || h == 4);
                if (isFrame) {
                    if (!s.isAir() && !s.canBeReplaced() && !s.is(Blocks.OBSIDIAN)) return false;
                } else {
                    // Interior must be air or replaceable
                    if (!s.isAir() && !s.canBeReplaced() && !s.is(Blocks.NETHER_PORTAL) && !s.is(Blocks.FIRE)) return false;
                }
            }
        }
        return true;
    }

    private int countObsidianInFrame(ServerLevel level, BlockPos origin, Direction.Axis axis) {
        int count = 0;
        // 10 standard positions
        int[][] coords = {{1,0}, {2,0}, {0,1}, {0,2}, {0,3}, {3,1}, {3,2}, {3,3}, {1,4}, {2,4}};
        for (int[] c : coords) {
            BlockPos p = getPortalCoord(origin, axis, c[0], c[1]);
            if (level.getBlockState(p).is(Blocks.OBSIDIAN)) {
                count++;
            }
        }
        return count;
    }

    private BlockPos findSafePortalSite(ServerLevel level, BlockPos center) {
        for (int r = 3; r <= 12; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos check = center.offset(dx, dy, dz);

                        if (canPlacePortalAt(level, check, Direction.Axis.X)) {
                            this.frameAxis = Direction.Axis.X;
                            return check;
                        }
                        if (canPlacePortalAt(level, check, Direction.Axis.Z)) {
                            this.frameAxis = Direction.Axis.Z;
                            return check;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean canPlacePortalAt(ServerLevel level, BlockPos origin, Direction.Axis axis) {
        for (int w = 0; w < 4; w++) {
            BlockPos ground = getPortalCoord(origin, axis, w, -1);
            BlockState gs = level.getBlockState(ground);
            if (!gs.isSolid() || gs.liquid()) return false;
        }

        for (int w = 0; w < 4; w++) {
            for (int h = 0; h < 5; h++) {
                BlockPos p = getPortalCoord(origin, axis, w, h);
                BlockState s = level.getBlockState(p);
                if (!s.isAir() && !s.canBeReplaced() && !s.is(Blocks.OBSIDIAN)) {
                    return false;
                }
            }
        }
        return true;
    }

    private int countItem(net.minecraft.world.item.Item item) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int total = 0;
        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private int findItemSlot(net.minecraft.world.item.Item item) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        // Priority 1: Already held in main hand
        if (inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND).getItem() == item) {
            return CompanionInventoryHandler.SLOT_MAINHAND;
        }
        // Priority 2: Main inventory (0..35)
        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            if (inv.getStackInSlot(i).getItem() == item) return i;
        }
        // Priority 3: Offhand
        if (inv.getStackInSlot(CompanionInventoryHandler.SLOT_OFFHAND).getItem() == item) {
            return CompanionInventoryHandler.SLOT_OFFHAND;
        }
        return -1;
    }

    private int findIgnitionSlot() {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        // Check main hand first
        ItemStack main = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        if (main.is(Items.FLINT_AND_STEEL) || main.is(Items.FIRE_CHARGE)) {
            return CompanionInventoryHandler.SLOT_MAINHAND;
        }
        // Check main inventory
        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.is(Items.FLINT_AND_STEEL) || s.is(Items.FIRE_CHARGE)) return i;
        }
        // Check offhand
        ItemStack off = inv.getStackInSlot(CompanionInventoryHandler.SLOT_OFFHAND);
        if (off.is(Items.FLINT_AND_STEEL) || off.is(Items.FIRE_CHARGE)) {
            return CompanionInventoryHandler.SLOT_OFFHAND;
        }
        return -1;
    }

    @Override
    public String getDescription() { return "Construir portal al Nether"; }
    @Override
    public String getProgress() { return substate; }
    @Override
    public String getTaskType() { return "BUILD_NETHER_PORTAL"; }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Step", currentStep.name());
        tag.putString("Axis", frameAxis.name());
        if (originPos != null) {
            tag.putLong("OriginPos", originPos.asLong());
        }
        tag.putInt("PlacingIndex", placingIndex);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("Step")) currentStep = Step.valueOf(tag.getString("Step"));
        if (tag.contains("Axis")) frameAxis = Direction.Axis.valueOf(tag.getString("Axis"));
        if (tag.contains("OriginPos")) originPos = BlockPos.of(tag.getLong("OriginPos"));
        this.placingIndex = tag.getInt("PlacingIndex");
        if (originPos != null) setupFramePositions();
    }
}
