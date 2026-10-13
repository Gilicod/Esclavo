package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.building.Blueprint;
import com.aicompanion.ai.building.BlueprintRegistry;
import com.aicompanion.ai.building.BlueprintTransformer;
import com.aicompanion.ai.building.BuildingConfig;
import com.aicompanion.ai.building.BuildPlacementPlanner;
import com.aicompanion.ai.building.BuildProject;
import com.aicompanion.ai.building.BuildSiteValidator;
import com.aicompanion.ai.building.MaterialAcquisitionPlanner;
import com.aicompanion.ai.building.ProceduralTemplates;
import com.aicompanion.ai.building.SchematicImporter;
import com.aicompanion.ai.building.SurvivalBlockBreakOperation;
import com.aicompanion.ai.nav.InteractionPositionPlanner;
import com.aicompanion.ai.nav.MovementPolicy;
import com.aicompanion.ai.nav.ScaffoldBlockHelper;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.entity.DefaultBehavior;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.inventory.EquipmentManager;
import com.aicompanion.util.TerrainPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Staged survival construction of a blueprint.
 *
 * 1.10.0 fixes:
 *  - the building site is fixed once chosen (no longer moved in front of the player on every start/reload);
 *  - the site is validated: player constructions are never destroyed (only natural terrain, natural trees,
 *    replaceable plants and the companion's own temporary blocks may be cleared);
 *  - clearing is paced by block hardness, gaps are filled only with blocks really owned (no free dirt);
 *  - blocks are placed only within real reach (4.5 blocks); high blocks are reached with tracked
 *    jump-pillars which are removed at the end (cleanup stage);
 *  - beds and doors are placed with both halves; fences/doors/ladders/sticks use vanilla recipe costs;
 *  - if the requested wood species can't be found, another species is used and the owner is told;
 *  - a verification pass re-checks every block and retries missing ones; optional elements (furniture,
 *    windows, door, ladders) never block the structure and are reported honestly if missing.
 */
public class BuildTask extends CompanionTask {
    private static final double REACH_SQ = 4.5 * 4.5;
    private static final int MAX_VERIFY_PASSES = 2;
    private static final int MAX_ACQUISITION_ROUNDS = 4;
    private static final int BLOCK_TIMEOUT_TICKS = 600;

    private String blueprintName;
    private BlockPos originPos;
    private boolean fixedOrigin = false;
    private int currentBlockIndex = 0;
    private Blueprint blueprint;
    private List<Blueprint.BlockEntry> plannedBlocks = null;
    private int missingMaterialsCooldown = 0;

    // Terrain Leveling
    private boolean terrainLevelingDone = false;
    private List<BlockPos> levelingClearList = new ArrayList<>();
    private List<BlockPos> levelingFillList = new ArrayList<>();
    private int levelingIndex = 0;
    private int levelingBreakTicks = 0;
    private boolean siteValidated = false;

    // Verification / cleanup / reporting
    private int verifyPass = 0;
    private boolean cleaningUp = false;
    private int cleanupTicks = 0;
    private BlockPos lastCleanupPos = null;
    private int cleanupBlockTicks = 0;
    private int skippedProtected = 0;
    private final Map<Item, Integer> optionalMissing = new LinkedHashMap<>();

    // Material handling
    private final Map<Block, Block> substitutions = new HashMap<>();
    private int acquisitionRounds = 0;
    private int waitingTicks = 0;

    // Movement / reach
    private BlockPos pillarBase = null;
    private int pillarWaitTicks = 0;
    private int navCooldown = 0;
    private int blockTicks = 0;
    private int lastIndexForTimer = -1;

    // Survival block breaker
    private final SurvivalBlockBreakOperation survivalBreaker = new SurvivalBlockBreakOperation();
    private BuildingConfig buildingConfig = null;
    private com.aicompanion.ai.building.BuildProject buildProject = null;

    public BuildTask(CompanionEntity companion, String blueprintName, BlockPos originPos) {
        this(companion, blueprintName, originPos, false);
    }

    /**
     * @param fixedOrigin true when the location was chosen explicitly by the player
     *                    (interactive build dialogue) and must be respected.
     */
    public BuildTask(CompanionEntity companion, String blueprintName, BlockPos originPos, boolean fixedOrigin) {
        super(companion);
        this.blueprintName = blueprintName;
        this.blueprint = BlueprintRegistry.get(blueprintName);
        this.fixedOrigin = fixedOrigin && originPos != null;
        this.originPos = originPos != null ? originPos : BuildSiteValidator.calculateSafeBuildOrigin(companion, null, this.blueprint);
    }

    public BuildTask(CompanionEntity companion, Blueprint blueprint, BlockPos originPos) {
        this(companion, blueprint, originPos, (BuildingConfig) null);
    }

    public BuildTask(CompanionEntity companion, Blueprint blueprint, BlockPos originPos, BuildingConfig buildingConfig) {
        super(companion);
        this.blueprint = blueprint;
        this.blueprintName = blueprint != null ? blueprint.getName() : "custom_build";
        this.fixedOrigin = originPos != null;
        this.originPos = originPos != null ? originPos : BuildSiteValidator.calculateSafeBuildOrigin(companion, null, this.blueprint);
        this.buildingConfig = buildingConfig;
    }

    public BuildingConfig getBuildingConfig() {
        return buildingConfig;
    }

    public void setBuildingConfig(BuildingConfig buildingConfig) {
        this.buildingConfig = buildingConfig;
    }

    public List<Blueprint.BlockEntry> getPlannedBlocks() {
        if (plannedBlocks == null && blueprint != null) {
            plannedBlocks = BuildPlacementPlanner.planConstructionOrder(blueprint);
        }
        return plannedBlocks != null ? plannedBlocks : Collections.emptyList();
    }

    @Override
    public List<CompanionTask> checkPreconditions() {
        if (blueprint == null) {
            blueprint = BlueprintRegistry.get(blueprintName);
        }
        if (blueprint == null) return Collections.emptyList();

        if (plannedBlocks == null) {
            plannedBlocks = BuildPlacementPlanner.planConstructionOrder(blueprint);
        }

        if (companion.level() instanceof ServerLevel sl && originPos != null) {
            reconcileWorldState(sl);
        }

        List<Blueprint.BlockEntry> remaining = (plannedBlocks != null && currentBlockIndex < plannedBlocks.size())
                ? plannedBlocks.subList(currentBlockIndex, plannedBlocks.size())
                : Collections.emptyList();

        // Pilar 7: MaterialAcquisitionPlanner auditing on remaining unplaced blocks
        MaterialAcquisitionPlanner.AcquisitionPlan plan = MaterialAcquisitionPlanner.planMaterialsForEntries(companion, remaining, null);
        if (!plan.is100PercentStocked() && acquisitionRounds < MAX_ACQUISITION_ROUNDS) {
            List<CompanionTask> prereqs = plan.generatePrerequisiteTasks(companion);
            if (!prereqs.isEmpty()) {
                acquisitionRounds++;
                com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-GOAP] BuildTask '{}' lacks materials: {}. Injecting {} gathering tasks.",
                        blueprintName, plan.getMissingReport(), prereqs.size());
                tell("Planificando construcción: " + plan.getMissingReport() + ". Iniciando recolección previa...", net.minecraft.ChatFormatting.AQUA);
                return prereqs;
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.BUILDING);
        if (blueprint == null) blueprint = BlueprintRegistry.get(blueprintName);
        if (blueprint != null && plannedBlocks == null) {
            plannedBlocks = BuildPlacementPlanner.planConstructionOrder(blueprint);
        }

        // 1. Choose or link the permanent build project anchor
        if (companion.getCompanionBrain() != null) {
            com.aicompanion.ai.building.BuildProject existingProj = companion.getCompanionBrain().getActiveBuildProject();
            if (existingProj != null && existingProj.isActive() && existingProj.getBlueprintName().equalsIgnoreCase(this.blueprintName)) {
                this.buildProject = existingProj;
                this.originPos = existingProj.getOriginPos();
                this.fixedOrigin = true;
                if (this.buildingConfig == null) this.buildingConfig = existingProj.getBuildingConfig();
            } else {
                if (!fixedOrigin) {
                    net.minecraft.world.entity.player.Player owner = companion.getOwnerPlayer();
                    if (owner != null && owner.level() == companion.level()) {
                        Direction facing = owner.getDirection();
                        BlockPos inFrontOfPlayer = owner.blockPosition().relative(facing, 6);
                        this.originPos = BuildSiteValidator.findSolidGround((ServerLevel) companion.level(), inFrontOfPlayer);
                    } else if (this.originPos == null) {
                        this.originPos = BuildSiteValidator.calculateSafeBuildOrigin(companion, null, this.blueprint);
                    }
                    fixedOrigin = true;
                }
                this.buildProject = new com.aicompanion.ai.building.BuildProject(
                        blueprintName,
                        blueprint != null ? blueprint.getName() : blueprintName,
                        originPos,
                        companion.level().dimension().location(),
                        buildingConfig,
                        plannedBlocks != null ? plannedBlocks.size() : 0
                );
                companion.getCompanionBrain().setActiveBuildProject(this.buildProject);
            }
        } else if (!fixedOrigin) {
            net.minecraft.world.entity.player.Player owner = companion.getOwnerPlayer();
            if (owner != null && owner.level() == companion.level()) {
                Direction facing = owner.getDirection();
                BlockPos inFrontOfPlayer = owner.blockPosition().relative(facing, 6);
                this.originPos = BuildSiteValidator.findSolidGround((ServerLevel) companion.level(), inFrontOfPlayer);
            } else if (this.originPos == null) {
                this.originPos = BuildSiteValidator.calculateSafeBuildOrigin(companion, null, this.blueprint);
            }
            fixedOrigin = true;
        }

        // Block-by-block world reconciliation
        if (companion.level() instanceof ServerLevel sl) {
            reconcileWorldState(sl);
        }

        // 2. Announce exact location to player
        if (originPos != null && currentBlockIndex == 0 && !terrainLevelingDone) {
            tell("Voy a construir en: X=" + originPos.getX() + ", Y=" + originPos.getY() + ", Z=" + originPos.getZ(),
                    net.minecraft.ChatFormatting.YELLOW);
        }

        // 3. Setup terrain preparation / leveling (recomputed from the real world on every start)
        if (!terrainLevelingDone) {
            setupTerrainLeveling();
        }
    }

    private void reconcileWorldState(ServerLevel sl) {
        if (plannedBlocks == null || plannedBlocks.isEmpty() || originPos == null) return;
        int matched = 0;
        while (currentBlockIndex < plannedBlocks.size()) {
            Blueprint.BlockEntry entry = plannedBlocks.get(currentBlockIndex);
            if (SchematicImporter.isTechnicalOrUnobtainable(entry.state())
                    || SchematicImporter.isSecondaryMultiBlockPart(entry.state())) {
                currentBlockIndex++;
                matched++;
                continue;
            }
            BlockPos targetPos = originPos.offset(entry.offset());
            BlockState current = sl.getBlockState(targetPos);
            BlockState desired = substitute(entry.state());
            if (isBlockMatching(current, desired, entry.state())) {
                currentBlockIndex++;
                matched++;
            } else {
                break;
            }
        }
        if (matched > 0) {
            com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-BUILD] Reconciled {} pre-existing blocks at {}. Starting at block index {}/{}.",
                    matched, originPos, currentBlockIndex, plannedBlocks.size());
        }
        if (buildProject != null) {
            buildProject.setCurrentBlockIndex(currentBlockIndex);
        }
    }

    private boolean isBlockMatching(BlockState current, BlockState desired, BlockState original) {
        if (current.is(desired.getBlock()) || current.is(original.getBlock())) {
            return true;
        }
        // Allow dirt/grass/path/farmland interchangeability in survival
        if (isGrassOrDirt(desired.getBlock()) && isGrassOrDirt(current.getBlock())) {
            return true;
        }
        // Allow equivalent wood shapes (e.g. stripped vs unstripped logs, or substituted wood species)
        if (desired.is(net.minecraft.tags.BlockTags.LOGS) && current.is(net.minecraft.tags.BlockTags.LOGS)) return true;
        if (desired.is(net.minecraft.tags.BlockTags.PLANKS) && current.is(net.minecraft.tags.BlockTags.PLANKS)) return true;
        if (desired.is(net.minecraft.tags.BlockTags.WOODEN_STAIRS) && current.is(net.minecraft.tags.BlockTags.WOODEN_STAIRS)) return true;
        if (desired.is(net.minecraft.tags.BlockTags.WOODEN_SLABS) && current.is(net.minecraft.tags.BlockTags.WOODEN_SLABS)) return true;
        if (desired.is(net.minecraft.tags.BlockTags.WOODEN_FENCES) && current.is(net.minecraft.tags.BlockTags.WOODEN_FENCES)) return true;
        if (desired.is(net.minecraft.tags.BlockTags.WOODEN_DOORS) && current.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)) return true;
        if (desired.is(net.minecraft.tags.BlockTags.WOODEN_TRAPDOORS) && current.is(net.minecraft.tags.BlockTags.WOODEN_TRAPDOORS)) return true;
        if (desired.is(net.minecraft.tags.BlockTags.WOODEN_PRESSURE_PLATES) && current.is(net.minecraft.tags.BlockTags.WOODEN_PRESSURE_PLATES)) return true;
        if (desired.is(net.minecraft.tags.BlockTags.WOODEN_BUTTONS) && current.is(net.minecraft.tags.BlockTags.WOODEN_BUTTONS)) return true;
        if (desired.is(net.minecraft.tags.BlockTags.BEDS) && current.is(net.minecraft.tags.BlockTags.BEDS)) return true;
        if (desired.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock
                && current.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) return true;
        return false;
    }

    private boolean isGrassOrDirt(Block block) {
        return block == Blocks.DIRT || block == Blocks.GRASS_BLOCK || block == Blocks.COARSE_DIRT
                || block == Blocks.ROOTED_DIRT || block == Blocks.DIRT_PATH || block == Blocks.FARMLAND
                || block == Blocks.PODZOL || block == Blocks.MYCELIUM;
    }

    private boolean hasSilkTouch() {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && net.minecraft.world.item.enchantment.EnchantmentHelper.hasSilkTouch(stack)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------
    // Site preparation
    // ---------------------------------------------------------------------------------------

    private void setupTerrainLeveling() {
        if (blueprint == null || originPos == null) {
            terrainLevelingDone = true;
            return;
        }

        int minX = 0, maxX = 0, minZ = 0, maxZ = 0, maxY = 3;
        for (Blueprint.BlockEntry entry : blueprint.getBlocks()) {
            minX = Math.min(minX, entry.offset().getX());
            maxX = Math.max(maxX, entry.offset().getX());
            minZ = Math.min(minZ, entry.offset().getZ());
            maxZ = Math.max(maxZ, entry.offset().getZ());
            maxY = Math.max(maxY, entry.offset().getY());
        }

        ServerLevel level = (ServerLevel) companion.level();
        levelingClearList = new ArrayList<>();
        levelingFillList = new ArrayList<>();
        int protectedCount = 0;
        BlockPos firstProtected = null;

        // Only the real footprint is cleared (plus 1 block of walking space at ground level)
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                boolean inside = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
                int topY = inside ? maxY : 2;
                for (int y = 1; y <= topY; y++) {
                    BlockPos pos = originPos.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.canBeReplaced()) continue;
                    if (TerrainPolicy.mayClearForBuilding(level, pos, state, companion.getTemporaryBlocks())) {
                        levelingClearList.add(pos);
                    } else if (inside) {
                        protectedCount++;
                        if (firstProtected == null) firstProtected = pos;
                    }
                }

                // Fill hollows under the foundation (only with blocks the companion really owns)
                if (inside) {
                    BlockPos belowPos = originPos.offset(x, -1, z);
                    BlockState belowState = level.getBlockState(belowPos);
                    if (belowState.isAir() || belowState.canBeReplaced()) {
                        levelingFillList.add(belowPos);
                    }
                }
            }
        }

        if (protectedCount > 0 && !siteValidated) {
            // Never destroy player constructions: abort and ask for another location
            fail("Área ocupada por construcciones");
            complete = true;
            tell("No puedo construir aquí: hay " + protectedCount + " bloques que parecen construcciones (por ejemplo en X="
                    + firstProtected.getX() + " Y=" + firstProtected.getY() + " Z=" + firstProtected.getZ()
                    + "). No los voy a romper. Elige otro lugar.", net.minecraft.ChatFormatting.RED);
            return;
        }
        siteValidated = true;

        // Clear from top to bottom so nothing is left floating
        levelingClearList.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
        levelingIndex = 0;
        levelingBreakTicks = 0;
        if (levelingClearList.isEmpty() && levelingFillList.isEmpty()) {
            terrainLevelingDone = true;
        }
    }

    private void tickTerrainLeveling() {
        int totalLeveling = levelingClearList.size() + levelingFillList.size();
        if (levelingIndex >= totalLeveling) {
            terrainLevelingDone = true;
            this.stage = "FOUNDATION";
            this.substate = "Terreno preparado. Comenzando construcción...";
            return;
        }

        this.stage = "CLEARING";
        this.substate = "Preparando terreno (" + (levelingIndex + 1) + "/" + totalLeveling + ")";

        boolean isClearing = levelingIndex < levelingClearList.size();
        BlockPos targetPos = isClearing ? levelingClearList.get(levelingIndex)
                : levelingFillList.get(levelingIndex - levelingClearList.size());
        if (!(companion.level() instanceof ServerLevel sl)) return;

        BlockState state = sl.getBlockState(targetPos);
        if (isClearing && (state.isAir() || state.canBeReplaced())) { levelingIndex++; levelingBreakTicks = 0; return; }
        if (!isClearing && !(state.isAir() || state.canBeReplaced())) { levelingIndex++; return; }

        if (!ensureReach(sl, targetPos)) return;

        companion.setCurrentState(CompanionState.BUILDING);
        companion.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

        if (isClearing) {
            SurvivalBlockBreakOperation.Status status = survivalBreaker.tick(
                    companion, sl, targetPos, SurvivalBlockBreakOperation.BreakReason.TERRAIN_LEVELING
            );
            if (status == SurvivalBlockBreakOperation.Status.IN_PROGRESS) {
                return;
            } else if (status == SurvivalBlockBreakOperation.Status.SUCCESS) {
                companion.getTemporaryBlocks().untrack(targetPos);
                levelingIndex++;
                markProgress();
            } else if (status == SurvivalBlockBreakOperation.Status.BLOCKED) {
                skippedProtected++;
                levelingIndex++;
                markProgress();
            }
            return;
        } else {
            int slot = ScaffoldBlockHelper.findBestScaffoldSlot(companion);
            ItemStack st = slot >= 0 ? companion.getInventoryHandler().getStackInSlot(slot) : ItemStack.EMPTY;
            if (st.getItem() instanceof BlockItem bi && companion.getSettings().isAllowBlockPlacement()) {
                BlockState fill = bi.getBlock().defaultBlockState();
                companion.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                companion.swing(InteractionHand.MAIN_HAND, true);
                sl.setBlock(targetPos, fill, 3);
                companion.playSound(fill.getSoundType().getPlaceSound(), 1.0f, 1.0f);
                st.shrink(1);
                if (st.isEmpty()) companion.getInventoryHandler().setStackInSlot(slot, ItemStack.EMPTY);
            }
            // No filler block owned: the foundation is simply placed over the gap (no free blocks)
        }
        levelingIndex++;
        markProgress();
    }

    // ---------------------------------------------------------------------------------------
    // Main loop
    // ---------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (paused || complete) return;
        if (blueprint == null) {
            blueprint = BlueprintRegistry.get(blueprintName);
        }
        if (plannedBlocks == null && blueprint != null) {
            plannedBlocks = BuildPlacementPlanner.planConstructionOrder(blueprint);
        }
        if (blueprint == null || plannedBlocks == null || plannedBlocks.isEmpty()) {
            complete = true;
            return;
        }
        if (!(companion.level() instanceof ServerLevel sl)) return;

        if (tickPendingPillar(sl)) return;

        // 1. Terrain Leveling & Preparation Phase
        if (!terrainLevelingDone) {
            tickTerrainLeveling();
            return;
        }

        // 2. Cleanup of temporary scaffolds, then finish
        if (cleaningUp) {
            tickCleanup(sl);
            return;
        }

        // 3. End of a pass: verify and retry missing blocks
        if (currentBlockIndex >= plannedBlocks.size()) {
            int missingStructural = countMissing(sl, false);
            if (missingStructural > 0 && verifyPass < MAX_VERIFY_PASSES) {
                verifyPass++;
                currentBlockIndex = 0;
                tell("Verificación: faltan " + missingStructural + " bloques de la estructura, los vuelvo a colocar.", net.minecraft.ChatFormatting.YELLOW);
                return;
            }
            cleaningUp = true;
            cleanupTicks = 0;
            return;
        }

        // 4. Process next block
        Blueprint.BlockEntry entry = plannedBlocks.get(currentBlockIndex);
        if (SchematicImporter.isTechnicalOrUnobtainable(entry.state())
                || SchematicImporter.isSecondaryMultiBlockPart(entry.state())) {
            advance();
            return;
        }
        BlockPos targetPos = originPos.offset(entry.offset());
        BlockState desiredState = substitute(entry.state());
        Blueprint.BuildPhase phase = entry.phase() != null ? entry.phase() : Blueprint.BuildPhase.WALLS;
        boolean optional = phase == Blueprint.BuildPhase.FURNISHING || SchematicImporter.isOptionalFurnishing(desiredState);
        this.stage = phase.name();
        this.substate = "Fase " + phase.name() + " (" + (currentBlockIndex + 1) + "/" + plannedBlocks.size() + ")";

        // Per-block watchdog: never stay forever on one unreachable block
        if (lastIndexForTimer != currentBlockIndex) { lastIndexForTimer = currentBlockIndex; blockTicks = 0; }
        if (++blockTicks > BLOCK_TIMEOUT_TICKS) {
            com.aicompanion.AICompanionMod.LOGGER.debug("[ESCLAVO-BUILD] Skipping unreachable block {} (will retry in verification)", targetPos);
            advance();
            return;
        }

        BlockState current = sl.getBlockState(targetPos);
        // Already correct (checked before walking there) -> next
        if (isBlockMatching(current, desiredState, entry.state())) {
            advance();
            return;
        }
        // Never destroy player constructions to place a blueprint block
        if (!TerrainPolicy.mayClearForBuilding(sl, targetPos, current, companion.getTemporaryBlocks())) {
            skippedProtected++;
            advance();
            return;
        }

        Item requiredItem = desiredState.getBlock().asItem();
        Block dBlock = desiredState.getBlock();
        if (dBlock == Blocks.DIRT_PATH || dBlock == Blocks.FARMLAND
                || ((dBlock == Blocks.GRASS_BLOCK || dBlock == Blocks.PODZOL || dBlock == Blocks.MYCELIUM) && !hasSilkTouch())) {
            requiredItem = Items.DIRT;
            if (dBlock == Blocks.GRASS_BLOCK || dBlock == Blocks.PODZOL || dBlock == Blocks.MYCELIUM) {
                desiredState = Blocks.DIRT.defaultBlockState();
            }
        }
        if (requiredItem != Items.AIR && !ensureItemAvailable(requiredItem)) {
            // Try another wood species / stone type / bed color before asking for gathering
            Block alt = findSubstitute(desiredState.getBlock());
            if (alt != null) {
                substitutions.put(entry.state().getBlock(), alt);
                tell("No tengo " + new ItemStack(requiredItem).getHoverName().getString() + "; usaré "
                        + new ItemStack(alt.asItem()).getHoverName().getString() + " en su lugar.", net.minecraft.ChatFormatting.YELLOW);
                return;
            }
            // Optional furnishings (bed, torch, glass, door, ladder) do not block construction if missing
            if (optional) {
                if (verifyPass == 0) {
                    optionalMissing.merge(requiredItem, 1, Integer::sum);
                }
                advance();
                return;
            }

            // Pilar 7: Phase lock and autonomous dynamic gathering injection (bounded)
            if (acquisitionRounds < MAX_ACQUISITION_ROUNDS) {
                List<Blueprint.BlockEntry> remainingEntries = plannedBlocks.subList(currentBlockIndex, plannedBlocks.size());
                MaterialAcquisitionPlanner.AcquisitionPlan phasePlan = MaterialAcquisitionPlanner.planMaterialsForEntries(companion, remainingEntries, phase);
                List<CompanionTask> subtasks = phasePlan.generatePrerequisiteTasks(companion);
                if (subtasks.isEmpty()) {
                    if (isWoodItem(requiredItem)) {
                        subtasks = List.of(new ChopTask(companion, "any", 4));
                    } else if (isStoneItem(requiredItem)) {
                        subtasks = List.of(new MineTask(companion, new ResourceLocation("minecraft", "cobblestone"), 8));
                    }
                }
                if (!subtasks.isEmpty()) {
                    acquisitionRounds++;
                    tell("Fase " + phase.name() + ": falta " + new ItemStack(requiredItem).getHoverName().getString() + ". Voy a recolectar más recursos para fabricarlo...", net.minecraft.ChatFormatting.YELLOW);
                    companion.getCompanionBrain().getTaskManager().pushSubtasks(subtasks);
                    return;
                }
            }

            companion.setCurrentState(CompanionState.WAITING);
            this.substate = "Esperando material: " + new ItemStack(requiredItem).getHoverName().getString();
            com.aicompanion.util.CompanionNotificationManager.notifyOwner(
                    companion,
                    "missing_build_materials",
                    Component.literal("[" + companion.getCustomCompanionName() + "] ")
                            .withStyle(net.minecraft.ChatFormatting.GOLD)
                            .append(Component.literal("Falta material para construir: " + new ItemStack(requiredItem).getHoverName().getString()
                                    + ". Dámelo (ponlo en mi inventario) o dime 'cancelar'.").withStyle(net.minecraft.ChatFormatting.RED)),
                    400
            );
            blockTicks = 0; // waiting for the player is not "stuck"
            if (++waitingTicks % 200 == 0) {
                acquisitionRounds = Math.max(0, acquisitionRounds - 1); // allow a new gathering attempt later
            }
            return;
        }
        waitingTicks = 0;

        // 5. Reach the block (walk / pillar), then place
        if (!ensureReach(sl, targetPos)) return;

        // Don't place a solid block inside our own body
        if (!desiredState.getCollisionShape(sl, targetPos).isEmpty()
                && companion.getBoundingBox().intersects(new AABB(targetPos))) {
            stepAwayFrom(targetPos);
            return;
        }

        companion.getNavManager().stop();
        companion.setCurrentState(CompanionState.BUILDING);
        companion.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

        if (!current.isAir() && !current.canBeReplaced()) {
            if (!TerrainPolicy.mayClearForBuilding(sl, targetPos, current, companion.getTemporaryBlocks())) {
                skippedProtected++;
                advance();
                return;
            }
            SurvivalBlockBreakOperation.Status status = survivalBreaker.tick(
                    companion, sl, targetPos, SurvivalBlockBreakOperation.BreakReason.OBSTACLE_CLEARING
            );
            if (status == SurvivalBlockBreakOperation.Status.IN_PROGRESS) {
                return;
            } else if (status == SurvivalBlockBreakOperation.Status.BLOCKED) {
                skippedProtected++;
                advance();
                return;
            } else {
                companion.getTemporaryBlocks().untrack(targetPos);
            }
        }

        if (placeBlock(sl, targetPos, desiredState, requiredItem)) {
            advance();
        } else {
            // Second half of a bed/door is blocked: optional element skipped
            if (optional && verifyPass == 0) optionalMissing.merge(requiredItem, 1, Integer::sum);
            advance();
        }
    }

    private void advance() {
        currentBlockIndex++;
        blockTicks = 0;
        if (buildProject != null) {
            buildProject.setCurrentBlockIndex(currentBlockIndex);
        }
        markProgress();
    }

    /** Places the block consuming exactly one item. Handles both halves of beds and doors. */
    private boolean placeBlock(ServerLevel sl, BlockPos pos, BlockState state, Item item) {
        BlockPos second = null;
        BlockState secondState = null;
        if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.FACING)) {
            second = pos.relative(state.getValue(BedBlock.FACING));
            state = state.setValue(BedBlock.PART, BedPart.FOOT);
            secondState = state.setValue(BedBlock.PART, BedPart.HEAD);
        } else if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)) {
            second = pos.above();
            state = state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            secondState = state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        }
        if (second != null) {
            BlockState there = sl.getBlockState(second);
            if (!there.isAir() && !there.canBeReplaced()) return false;
        }

        if ((state.getBlock() == Blocks.GRASS_BLOCK || state.getBlock() == Blocks.PODZOL || state.getBlock() == Blocks.MYCELIUM) && !hasSilkTouch()) {
            state = Blocks.DIRT.defaultBlockState();
            item = Items.DIRT;
        }

        if (item != Items.AIR) {
            boolean removed = companion.getInventoryHandler().removeItem(item, 1);
            if (!removed && isGrassOrDirt(state.getBlock())) {
                for (Item dirtAlt : new Item[]{Items.DIRT, Items.COARSE_DIRT, Items.ROOTED_DIRT, Items.GRASS_BLOCK}) {
                    if (companion.getInventoryHandler().removeItem(dirtAlt, 1)) {
                        removed = true;
                        item = dirtAlt;
                        break;
                    }
                }
            }
            if (!removed) return false;
            companion.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        }
        companion.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        companion.swing(InteractionHand.MAIN_HAND, true);
        sl.setBlock(pos, state, 3);
        if (second != null) sl.setBlock(second, secondState, 3);
        companion.playSound(state.getSoundType().getPlaceSound(), 1.0f, 1.0f);
        return true;
    }

    // ---------------------------------------------------------------------------------------
    // Reach, pillars and cleanup
    // ---------------------------------------------------------------------------------------

    /** Returns true when the target is within real reach; otherwise moves / pillars towards it. */
    private boolean ensureReach(ServerLevel sl, BlockPos target) {
        Vec3 eye = companion.getEyePosition();
        Vec3 center = Vec3.atCenterOf(target);
        if (eye.distanceToSqr(center) <= REACH_SQ) return true;

        double dx = center.x - companion.getX();
        double dz = center.z - companion.getZ();
        double horizSq = dx * dx + dz * dz;
        int feetY = companion.blockPosition().getY();

        // Too high: pillar up with a tracked temporary block
        if (horizSq <= 9.0 && target.getY() - feetY >= 4 && companion.onGround()) {
            if (startPillar(sl)) return false;
        }
        // Too low while standing on our own pillar: come down one block at a time
        if (target.getY() < feetY - 3 && companion.getTemporaryBlocks().isStandingOnTracked(companion)) {
            companion.getNavManager().stop();
            companion.getTemporaryBlocks().tickCleanup(companion, true, true);
            return false;
        }

        companion.setCurrentState(CompanionState.NAVIGATING);
        if (--navCooldown <= 0 || companion.getNavManager().isDone()) {
            navCooldown = 20;
            BlockPos stand = InteractionPositionPlanner.findStandPosition(sl, target, companion.blockPosition(), 4.0);
            if (stand != null) {
                companion.getNavManager().moveTo(stand, MovementPolicy.walk());
            } else {
                companion.getNavManager().moveTo(target, MovementPolicy.walk());
            }
        }
        return false;
    }

    private boolean startPillar(ServerLevel sl) {
        if (!companion.getSettings().isAllowBlockPlacement()) return false;
        int slot = ScaffoldBlockHelper.findBestScaffoldSlot(companion);
        if (slot == -1) {
            ScaffoldBlockHelper.acquireScaffoldBlockAround(companion, sl);
            slot = ScaffoldBlockHelper.findBestScaffoldSlot(companion);
        }
        if (slot == -1) return false;
        BlockPos feet = companion.blockPosition();
        if (!sl.getBlockState(feet.above(2)).isAir()) return false;
        companion.getNavManager().stop();
        companion.getJumpControl().jump();
        pillarBase = feet.immutable();
        pillarWaitTicks = 0;
        return true;
    }

    /** Places the block under our feet once the jump has lifted us. */
    private boolean tickPendingPillar(ServerLevel sl) {
        if (pillarBase == null) return false;
        if (companion.getY() >= pillarBase.getY() + 1.0 && sl.getBlockState(pillarBase).canBeReplaced()) {
            int slot = ScaffoldBlockHelper.findBestScaffoldSlot(companion);
            ItemStack st = slot >= 0 ? companion.getInventoryHandler().getStackInSlot(slot) : ItemStack.EMPTY;
            if (st.getItem() instanceof BlockItem bi) {
                BlockState s = bi.getBlock().defaultBlockState();
                sl.setBlock(pillarBase, s, 3);
                companion.playSound(s.getSoundType().getPlaceSound(), 1.0F, 1.0F);
                companion.swing(InteractionHand.MAIN_HAND, true);
                companion.getTemporaryBlocks().track(pillarBase, bi.getBlock());
                st.shrink(1);
                if (st.isEmpty()) companion.getInventoryHandler().setStackInSlot(slot, ItemStack.EMPTY);
                markProgress();
            }
            pillarBase = null;
            return true;
        }
        if (++pillarWaitTicks > 12 || (companion.onGround() && pillarWaitTicks > 3)) {
            pillarBase = null; // jump failed (ceiling?)
            return false;
        }
        return true;
    }

    private void stepAwayFrom(BlockPos target) {
        Vec3 away = companion.position().subtract(Vec3.atCenterOf(target));
        Vec3 dir = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
        BlockPos dest = BlockPos.containing(companion.getX() + dir.x * 2.0, companion.getY(), companion.getZ() + dir.z * 2.0);
        if (--navCooldown <= 0 || companion.getNavManager().isDone()) {
            navCooldown = 10;
            companion.getNavManager().moveTo(dest, MovementPolicy.walk());
        }
    }

    private void tickCleanup(ServerLevel sl) {
        this.stage = "CLEANUP";
        var tracker = companion.getTemporaryBlocks();
        cleanupTicks++;
        BlockPos nearest = tracker.purgeInvalidAndNearest(sl, companion.position(), originPos, 24.0 * 24.0);
        if (nearest == null || tracker.size() == 0 || cleanupTicks > 600) {
            survivalBreaker.reset(companion, sl);
            finish();
            return;
        }
        this.substate = "Retirando andamios temporales (" + tracker.size() + ")";

        if (!nearest.equals(lastCleanupPos)) {
            lastCleanupPos = nearest.immutable();
            cleanupBlockTicks = 0;
        } else if (++cleanupBlockTicks > 80) {
            // Skip unreachable or obstructed temporary scaffold block after 4 seconds
            tracker.untrack(nearest);
            lastCleanupPos = null;
            cleanupBlockTicks = 0;
            return;
        }

        if (nearest.distToCenterSqr(companion.position()) > 20.25 && !tracker.isStandingOnTracked(companion)) {
            companion.setCurrentState(CompanionState.NAVIGATING);
            if (--navCooldown <= 0 || companion.getNavManager().isDone()) {
                navCooldown = 20;
                companion.getNavManager().moveTo(nearest, MovementPolicy.walk());
            }
            return;
        }
        companion.getNavManager().stop();
        companion.setCurrentState(CompanionState.BUILDING);
        SurvivalBlockBreakOperation.Status status = survivalBreaker.tick(
                companion, sl, nearest, SurvivalBlockBreakOperation.BreakReason.SCAFFOLD_CLEANUP
        );
        if (status == SurvivalBlockBreakOperation.Status.SUCCESS || status == SurvivalBlockBreakOperation.Status.BLOCKED) {
            tracker.untrack(nearest);
            lastCleanupPos = null;
            cleanupBlockTicks = 0;
            markProgress();
        }
    }

    private void finish() {
        if (companion.level() instanceof ServerLevel sl) {
            survivalBreaker.reset(companion, sl);
            companion.getTemporaryBlocks().cancelCleanup(companion, sl);
        }
        complete = true;
        if (buildProject != null) {
            buildProject.setStatus(com.aicompanion.ai.building.BuildProject.Status.COMPLETED);
            buildProject.setCurrentBlockIndex(plannedBlocks != null ? plannedBlocks.size() : currentBlockIndex);
        }
        if (companion.getCompanionBrain() != null && companion.getCompanionBrain().getActiveBuildProject() != null) {
            companion.getCompanionBrain().getActiveBuildProject().setStatus(com.aicompanion.ai.building.BuildProject.Status.COMPLETED);
        }
        companion.addBuildingXP(plannedBlocks != null ? plannedBlocks.size() * 5 : 50);
        companion.incrementTasksCompleted();
        StringBuilder sb = new StringBuilder("¡Construcción completada!");
        if (!optionalMissing.isEmpty()) {
            sb.append(" No pude colocar (faltan materiales): ");
            boolean first = true;
            for (Map.Entry<Item, Integer> e : optionalMissing.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(e.getValue()).append(" x ").append(new ItemStack(e.getKey()).getHoverName().getString());
                first = false;
            }
            sb.append('.');
        }
        if (skippedProtected > 0) {
            sb.append(" Respeté ").append(skippedProtected).append(" bloques existentes que no eran míos.");
        }
        tell(sb.toString(), net.minecraft.ChatFormatting.GREEN);
    }

    private int countMissing(ServerLevel sl, boolean includeOptional) {
        if (plannedBlocks == null || originPos == null) return 0;
        int missing = 0;
        for (Blueprint.BlockEntry e : plannedBlocks) {
            if (SchematicImporter.isTechnicalOrUnobtainable(e.state())
                    || SchematicImporter.isSecondaryMultiBlockPart(e.state())) continue;
            if (!includeOptional && (e.phase() == Blueprint.BuildPhase.FURNISHING || SchematicImporter.isOptionalFurnishing(e.state()))) continue;
            BlockPos p = originPos.offset(e.offset());
            BlockState s = sl.getBlockState(p);
            BlockState desired = substitute(e.state());
            if (isBlockMatching(s, desired, e.state())) continue;
            if (!TerrainPolicy.mayClearForBuilding(sl, p, s, companion.getTemporaryBlocks())) continue; // protected
            missing++;
        }
        return missing;
    }

    // ---------------------------------------------------------------------------------------
    // Materials
    // ---------------------------------------------------------------------------------------

    private BlockState substitute(BlockState state) {
        if (state == null) return Blocks.AIR.defaultBlockState();
        if ((state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) && !hasSilkTouch()) {
            return Blocks.DIRT.defaultBlockState();
        }
        Block alt = substitutions.get(state.getBlock());
        if (alt == null) return state;
        BlockState out = alt.defaultBlockState();
        for (var prop : state.getProperties()) {
            if (out.hasProperty(prop)) out = copyProperty(state, out, prop);
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copyProperty(BlockState from, BlockState to, net.minecraft.world.level.block.state.properties.Property prop) {
        return to.setValue(prop, from.getValue(prop));
    }

    /** Another species of the same wood shape (planks/log/stripped_log/stairs/slab/fence/gate/door/trapdoor/plate/button) or another stone that the companion owns. */
    private Block findSubstitute(Block wanted) {
        if (wanted == null || wanted == Blocks.AIR) return null;
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        BlockState wantedState = wanted.defaultBlockState();

        // 1. Stone / Cobblestone substitutions
        if (wanted == Blocks.COBBLESTONE || wanted == Blocks.STONE || wanted == Blocks.STONE_BRICKS
                || wanted == Blocks.MOSSY_COBBLESTONE || wanted == Blocks.SMOOTH_STONE
                || wanted == Blocks.ANDESITE || wanted == Blocks.DIORITE || wanted == Blocks.GRANITE) {
            for (Block cand : new Block[]{Blocks.COBBLESTONE, Blocks.STONE, Blocks.STONE_BRICKS, Blocks.COBBLED_DEEPSLATE}) {
                if (cand != wanted && inv.countItem(cand.asItem()) > 0) return cand;
            }
            return null;
        }
        if (wanted == Blocks.STONE_STAIRS || wanted == Blocks.STONE_BRICK_STAIRS || wanted == Blocks.MOSSY_COBBLESTONE_STAIRS) {
            if (inv.countItem(Items.COBBLESTONE_STAIRS) > 0 || inv.countItem(Items.COBBLESTONE) >= 6) return Blocks.COBBLESTONE_STAIRS;
            if (inv.countItem(Items.COBBLED_DEEPSLATE) >= 6) return Blocks.COBBLED_DEEPSLATE_STAIRS;
        }
        if (wanted == Blocks.STONE_SLAB || wanted == Blocks.STONE_BRICK_SLAB || wanted == Blocks.SMOOTH_STONE_SLAB) {
            if (inv.countItem(Items.COBBLESTONE_SLAB) > 0 || inv.countItem(Items.COBBLESTONE) >= 3) return Blocks.COBBLESTONE_SLAB;
            if (inv.countItem(Items.COBBLED_DEEPSLATE) >= 3) return Blocks.COBBLED_DEEPSLATE_SLAB;
        }

        // 2. Log / Stripped Log / Wood / Stripped Wood substitutions
        if (wantedState.is(net.minecraft.tags.BlockTags.LOGS)) {
            ResourceLocation wantedKey = BuiltInRegistries.BLOCK.getKey(wanted);
            boolean wantsStripped = wantedKey != null && wantedKey.getPath().startsWith("stripped_");

            // Check same-species unstripped/stripped counterpart first
            Item unstripped = getUnstrippedCounterpart(wanted.asItem());
            if (unstripped != null && inv.countItem(unstripped) > 0) {
                Block b = Block.byItem(unstripped);
                if (b != Blocks.AIR && b != wanted) return b;
            }
            Item stripped = getStrippedCounterpart(wanted.asItem());
            if (stripped != null && inv.countItem(stripped) > 0) {
                Block b = Block.byItem(stripped);
                if (b != Blocks.AIR && b != wanted) return b;
            }

            // Check all vanilla wood types
            for (ProceduralTemplates.WoodType w : ProceduralTemplates.WoodType.values()) {
                Block wLogBlock = w.log.getBlock();
                Item wLogItem = wLogBlock.asItem();
                Item wStrippedItem = getStrippedCounterpart(wLogItem);
                if (inv.countItem(wLogItem) > 0 || (wStrippedItem != null && inv.countItem(wStrippedItem) > 0)) {
                    if (wantsStripped && wStrippedItem != null) {
                        Block sb = Block.byItem(wStrippedItem);
                        if (sb != Blocks.AIR && sb != wanted) return sb;
                    }
                    if (wLogBlock != wanted) return wLogBlock;
                }
            }

            // Check any log item in inventory (including modded logs in ItemTags.LOGS)
            Item anyLog = findAnyLogInInventory(inv);
            if (anyLog != null) {
                if (wantsStripped) {
                    Item anyStripped = getStrippedCounterpart(anyLog);
                    if (anyStripped != null && Block.byItem(anyStripped) != Blocks.AIR && Block.byItem(anyStripped) != wanted) {
                        return Block.byItem(anyStripped);
                    }
                }
                Block anyBlock = Block.byItem(anyLog);
                if (anyBlock != Blocks.AIR && anyBlock != wanted) return anyBlock;
            }
            return null;
        }

        // 2b. Bed color substitutions (any bed in inventory can substitute another bed color)
        if (wantedState.is(net.minecraft.tags.BlockTags.BEDS)) {
            for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
                ItemStack st = inv.getStackInSlot(i);
                if (!st.isEmpty() && st.is(net.minecraft.tags.ItemTags.BEDS)) {
                    Block b = Block.byItem(st.getItem());
                    if (b != Blocks.AIR && b != wanted) return b;
                }
            }
            return null;
        }

        // 3. Other Wood Shapes (_planks, _stairs, _slab, _fence_gate, _fence, _door, _trapdoor, _pressure_plate, _button)
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(wanted);
        if (key == null) return null;
        String path = key.getPath();
        String suffix = null;
        for (String s : new String[]{"_fence_gate", "_pressure_plate", "_trapdoor", "_planks", "_stairs", "_slab", "_fence", "_door", "_button"}) {
            if (path.endsWith(s)) {
                suffix = s;
                break;
            }
        }
        if (suffix == null) return null;

        for (ProceduralTemplates.WoodType w : ProceduralTemplates.WoodType.values()) {
            String prefix = w.name().toLowerCase(Locale.ROOT);
            Block candidate = BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft", prefix + suffix));
            if (candidate == Blocks.AIR || candidate == wanted) continue;
            Item wPlanks = w.planks.getBlock().asItem();
            Item wLog = w.log.getBlock().asItem();
            Item wStripped = getStrippedCounterpart(wLog);
            if (inv.countItem(candidate.asItem()) > 0 || inv.countItem(wPlanks) > 0
                    || inv.countItem(wLog) > 0 || (wStripped != null && inv.countItem(wStripped) > 0)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Ensures the required item is present in inventory, crafting it from raw resources with
     * vanilla recipe costs (logs -> stripped logs / wood / planks -> stairs/slabs/fences/gates/doors/trapdoors/chests/tables,
     * cobblestone -> stairs/slabs/walls, stone -> stone bricks, etc.).
     */
    private boolean ensureItemAvailable(Item requiredItem) {
        if (requiredItem == Items.AIR) return true;
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        if ((requiredItem == Items.GRASS_BLOCK || requiredItem == Items.PODZOL || requiredItem == Items.MYCELIUM) && !hasSilkTouch()) {
            requiredItem = Items.DIRT;
        }
        if (requiredItem == Items.DIRT_PATH || requiredItem == Items.FARMLAND) {
            requiredItem = Items.DIRT;
        }
        if (inv.countItem(requiredItem) > 0) return true;
        if (requiredItem == Items.DIRT && (inv.countItem(Items.COARSE_DIRT) > 0 || inv.countItem(Items.ROOTED_DIRT) > 0 || inv.countItem(Items.GRASS_BLOCK) > 0)) {
            return true;
        }

        Block block = Block.byItem(requiredItem);
        BlockState defaultState = block != null ? block.defaultBlockState() : null;
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(requiredItem);
        String itemPath = itemKey != null ? itemKey.getPath() : "";

        // 0a. Stripped Logs / Stems / Wood (1 unstripped log -> 1 stripped log using axe)
        if (itemPath.startsWith("stripped_")) {
            Item unstripped = getUnstrippedCounterpart(requiredItem);
            if (unstripped != null && inv.countItem(unstripped) > 0) {
                damageAxeIfPresent();
                return craft(Map.of(unstripped, 1), requiredItem, 1);
            }
            Item baseLog = getBaseLogForWood(requiredItem);
            if (baseLog != null && inv.countItem(baseLog) > 0) {
                damageAxeIfPresent();
                if (inv.countItem(baseLog) >= 4 && (itemPath.endsWith("_wood") || itemPath.endsWith("_hyphae"))) {
                    return craft(Map.of(baseLog, 4), requiredItem, 3);
                }
                return craft(Map.of(baseLog, 1), requiredItem, 1);
            }
            // Also allow stripping any available log in inventory if no exact species log is present
            Item anyLog = findAnyLogInInventory(inv);
            if (anyLog != null && inv.countItem(anyLog) > 0) {
                damageAxeIfPresent();
                return craft(Map.of(anyLog, 1), requiredItem, 1);
            }
        }

        // 0b. 6-sided Wood / Hyphae blocks (*_wood, *_hyphae) or unstripped logs when another log is in inventory
        if (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.LOGS)) {
            Item baseLog = getBaseLogForWood(requiredItem);
            if (baseLog != null && baseLog != requiredItem && inv.countItem(baseLog) > 0) {
                if (inv.countItem(baseLog) >= 4) {
                    return craft(Map.of(baseLog, 4), requiredItem, 3);
                }
                return craft(Map.of(baseLog, 1), requiredItem, 1);
            }
            Item stripped = getStrippedCounterpart(requiredItem);
            if (stripped != null && inv.countItem(stripped) > 0) {
                return craft(Map.of(stripped, 1), requiredItem, 1);
            }
        }

        // 1. Planks from logs (1 log -> 4 planks)
        if (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.PLANKS)) {
            return ensureCount(requiredItem, 1);
        }
        Item sourceLog = getSourceLogForPlanks(requiredItem);
        if (sourceLog != null && inv.countItem(sourceLog) > 0) {
            return craft(Map.of(sourceLog, 1), requiredItem, 4);
        }

        // 2. Wooden Stairs: 6 planks -> 4 stairs
        Item stairPlanks = getSourcePlanks(requiredItem);
        if (stairPlanks != null && (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.WOODEN_STAIRS))) {
            if (ensureCount(stairPlanks, 6)) {
                return craft(Map.of(stairPlanks, 6), requiredItem, 4);
            }
            return false;
        }

        // 3. Wooden Slabs: 3 planks -> 6 slabs
        if (stairPlanks != null && (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.WOODEN_SLABS))) {
            if (ensureCount(stairPlanks, 3)) {
                return craft(Map.of(stairPlanks, 3), requiredItem, 6);
            }
            return false;
        }

        // 4. Wooden Fence Gates: 2 planks + 4 sticks -> 1 fence gate
        if (stairPlanks != null && (block instanceof net.minecraft.world.level.block.FenceGateBlock)) {
            if (ensureCount(stairPlanks, 2) && ensureSticks(4)) {
                return craft(Map.of(stairPlanks, 2, Items.STICK, 4), requiredItem, 1);
            }
            return false;
        }

        // 5. Wooden Trapdoors: 6 planks -> 2 trapdoors
        if (stairPlanks != null && (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.WOODEN_TRAPDOORS))) {
            if (ensureCount(stairPlanks, 6)) {
                return craft(Map.of(stairPlanks, 6), requiredItem, 2);
            }
            return false;
        }

        // 6. Wooden Fences: 4 planks + 2 sticks -> 3 fences
        if (stairPlanks != null && (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.WOODEN_FENCES))) {
            if (ensureCount(stairPlanks, 4) && ensureSticks(2)) {
                return craft(Map.of(stairPlanks, 4, Items.STICK, 2), requiredItem, 3);
            }
            return false;
        }

        // 7. Wooden Doors: 6 planks -> 3 doors
        if (stairPlanks != null && (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.WOODEN_DOORS))) {
            if (ensureCount(stairPlanks, 6)) {
                return craft(Map.of(stairPlanks, 6), requiredItem, 3);
            }
            return false;
        }

        // 7b. Wooden Pressure Plates: 2 planks -> 1 pressure plate
        if (stairPlanks != null && (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.WOODEN_PRESSURE_PLATES))) {
            if (ensureCount(stairPlanks, 2)) {
                return craft(Map.of(stairPlanks, 2), requiredItem, 1);
            }
            return false;
        }

        // 7c. Wooden Buttons: 1 plank -> 1 button
        if (stairPlanks != null && (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.WOODEN_BUTTONS))) {
            if (ensureCount(stairPlanks, 1)) {
                return craft(Map.of(stairPlanks, 1), requiredItem, 1);
            }
            return false;
        }

        // 7d. Wooden Signs: 6 planks + 1 stick -> 3 signs
        if (stairPlanks != null && (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.ALL_SIGNS))) {
            if (ensureCount(stairPlanks, 6) && ensureSticks(1)) {
                return craft(Map.of(stairPlanks, 6, Items.STICK, 1), requiredItem, 3);
            }
            return false;
        }

        // 8. Stone Stairs: 6 stone -> 4 stairs
        Item stoneSource = getSourceStone(requiredItem);
        if (stoneSource != null && (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.STAIRS))) {
            if (ensureStoneCount(stoneSource, 6)) {
                return craft(Map.of(stoneSource, 6), requiredItem, 4);
            }
            return false;
        }

        // 9. Stone Slabs: 3 stone -> 6 slabs
        if (stoneSource != null && (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.SLABS))) {
            if (ensureStoneCount(stoneSource, 3)) {
                return craft(Map.of(stoneSource, 3), requiredItem, 6);
            }
            return false;
        }

        // 10. Stone Walls: 6 stone -> 6 walls
        if (stoneSource != null && (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.WALLS))) {
            if (ensureStoneCount(stoneSource, 6)) {
                return craft(Map.of(stoneSource, 6), requiredItem, 6);
            }
            return false;
        }

        // 11. Stone Bricks: 4 stone -> 4 stone bricks
        if (requiredItem == Items.STONE_BRICKS) {
            if (inv.countItem(Items.STONE) >= 4) {
                return craft(Map.of(Items.STONE, 4), Items.STONE_BRICKS, 4);
            }
            return false;
        }

        // 12. Glass Panes: 6 glass -> 16 glass panes
        if (requiredItem == Items.GLASS_PANE) {
            if (inv.countItem(Items.GLASS) >= 6) {
                return craft(Map.of(Items.GLASS, 6), Items.GLASS_PANE, 16);
            }
            return false;
        }

        // 13. Crafting Table (4 planks) / Chest (8 planks) / Barrel (6 planks + 2 slabs)
        if (requiredItem == Items.CRAFTING_TABLE || requiredItem == Items.CHEST || requiredItem == Items.BARREL) {
            int cost = requiredItem == Items.CRAFTING_TABLE ? 4 : 8;
            Item planks = findPlanksWithAtLeast(cost);
            if (planks != null) return craft(Map.of(planks, cost), requiredItem, 1);
            return false;
        }

        // 14. Ladders: 7 sticks -> 3 ladders
        if (requiredItem == Items.LADDER) {
            if (ensureSticks(7)) return craft(Map.of(Items.STICK, 7), Items.LADDER, 3);
            return false;
        }

        // 14b. Beds: 3 wool + 3 planks -> 1 bed
        if (defaultState != null && defaultState.is(net.minecraft.tags.BlockTags.BEDS)) {
            Item wool = findWoolWithAtLeast(inv, 3);
            Item planks = findPlanksWithAtLeast(3);
            if (wool != null && planks != null) {
                return craft(Map.of(wool, 3, planks, 3), requiredItem, 1);
            }
            return false;
        }

        // 15. Torches: 1 coal/charcoal + 1 stick -> 4 torches
        if (requiredItem == Items.TORCH) { // wall torches also use the torch item
            Item coal = inv.countItem(Items.COAL) > 0 ? Items.COAL : (inv.countItem(Items.CHARCOAL) > 0 ? Items.CHARCOAL : null);
            if (coal == null) {
                // Smelt 1 log into 1 charcoal using 1 plank as fuel when coal is missing
                Item log = findAnyLogInInventory(inv);
                Item fuelPlank = findPlanksWithAtLeast(1);
                if (log != null && fuelPlank != null && (log != fuelPlank || inv.countItem(log) >= 2)) {
                    if (craft(Map.of(log, 1, fuelPlank, 1), Items.CHARCOAL, 1)) {
                        coal = Items.CHARCOAL;
                    }
                }
            }
            if (coal != null && ensureSticks(1)) return craft(Map.of(coal, 1, Items.STICK, 1), Items.TORCH, 4);
            return false;
        }

        // 16. Fallback: EmergencyCrafting with server recipe manager
        if (com.aicompanion.util.EmergencyCrafting.craftOnce(companion, requiredItem)) {
            return true;
        }

        return inv.countItem(requiredItem) > 0;
    }

    private void damageAxeIfPresent() {
        int axeSlot = EquipmentManager.findBestAxeSlot(companion);
        if (axeSlot >= 0) {
            ItemStack axe = companion.getInventoryHandler().getStackInSlot(axeSlot);
            if (!axe.isEmpty() && axe.isDamageableItem()) {
                axe.hurtAndBreak(1, companion, e -> {});
            }
        }
    }

    private static Item getUnstrippedCounterpart(Item strippedItem) {
        if (strippedItem == null) return null;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(strippedItem);
        if (key == null || !key.getPath().startsWith("stripped_")) return null;
        String basePath = key.getPath().substring("stripped_".length());
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(key.getNamespace(), basePath));
        return item != Items.AIR ? item : null;
    }

    private static Item getStrippedCounterpart(Item unstrippedItem) {
        if (unstrippedItem == null) return null;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(unstrippedItem);
        if (key == null || key.getPath().startsWith("stripped_")) return null;
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(key.getNamespace(), "stripped_" + key.getPath()));
        return item != Items.AIR ? item : null;
    }

    private static Item getBaseLogForWood(Item woodOrStrippedItem) {
        if (woodOrStrippedItem == null) return null;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(woodOrStrippedItem);
        if (key == null) return null;
        String path = key.getPath();
        if (path.startsWith("stripped_")) {
            path = path.substring("stripped_".length());
        }
        if (path.endsWith("_wood")) {
            path = path.substring(0, path.length() - "_wood".length()) + "_log";
        } else if (path.endsWith("_hyphae")) {
            path = path.substring(0, path.length() - "_hyphae".length()) + "_stem";
        }
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(key.getNamespace(), path));
        return item != Items.AIR ? item : null;
    }

    private static Item findAnyLogInInventory(CompanionInventoryHandler inv) {
        if (inv == null) return null;
        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
            ItemStack st = inv.getStackInSlot(i);
            if (!st.isEmpty() && (st.is(net.minecraft.tags.ItemTags.LOGS)
                    || st.is(net.minecraft.tags.ItemTags.CRIMSON_STEMS)
                    || st.is(net.minecraft.tags.ItemTags.WARPED_STEMS))) {
                return st.getItem();
            }
        }
        return null;
    }

    /** Atomically consumes the inputs and adds the output; nothing changes if an input is missing. */
    private boolean craft(Map<Item, Integer> inputs, Item output, int count) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        for (Map.Entry<Item, Integer> e : inputs.entrySet()) {
            if (inv.countItem(e.getKey()) < e.getValue()) return false;
        }
        for (Map.Entry<Item, Integer> e : inputs.entrySet()) {
            inv.removeItem(e.getKey(), e.getValue());
        }
        ItemStack rest = inv.addItem(new ItemStack(output, count));
        if (!rest.isEmpty()) companion.spawnAtLocation(rest);
        return true;
    }

    private boolean ensureCount(Item planks, int needed) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        if (inv.countItem(planks) >= needed) return true;
        Item log = getSourceLogForPlanks(planks);
        Item strippedLog = getStrippedCounterpart(log);
        int guard = 0;
        while (inv.countItem(planks) < needed && log != null && inv.countItem(log) > 0 && guard++ < 16) {
            craft(Map.of(log, 1), planks, 4);
        }
        while (inv.countItem(planks) < needed && strippedLog != null && inv.countItem(strippedLog) > 0 && guard++ < 16) {
            craft(Map.of(strippedLog, 1), planks, 4);
        }
        while (inv.countItem(planks) < needed && guard++ < 16) {
            Item anyLog = findAnyLogInInventory(inv);
            if (anyLog == null) break;
            int yield = (anyLog == Items.BAMBOO_BLOCK || anyLog == Items.STRIPPED_BAMBOO_BLOCK) ? 2 : 4;
            if (!craft(Map.of(anyLog, 1), planks, yield)) break;
        }
        return inv.countItem(planks) >= needed;
    }

    private boolean ensureStoneCount(Item stone, int needed) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        if (inv.countItem(stone) >= needed) return true;
        if (stone == Items.STONE_BRICKS) {
            int guard = 0;
            while (inv.countItem(Items.STONE_BRICKS) < needed && inv.countItem(Items.STONE) >= 4 && guard++ < 8) {
                craft(Map.of(Items.STONE, 4), Items.STONE_BRICKS, 4);
            }
        }
        return inv.countItem(stone) >= needed;
    }

    private boolean ensureSticks(int needed) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int guard = 0;
        while (inv.countItem(Items.STICK) < needed && guard++ < 8) {
            Item planks = findPlanksWithAtLeast(2);
            if (planks == null) break;
            craft(Map.of(planks, 2), Items.STICK, 4);
        }
        return inv.countItem(Items.STICK) >= needed;
    }

    private Item findPlanksWithAtLeast(int amount) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
            ItemStack st = inv.getStackInSlot(i);
            if (!st.isEmpty() && st.is(net.minecraft.tags.ItemTags.PLANKS) && inv.countItem(st.getItem()) >= amount) {
                return st.getItem();
            }
        }
        for (ProceduralTemplates.WoodType w : ProceduralTemplates.WoodType.values()) {
            Item p = w.planks.getBlock().asItem();
            if (ensureCount(p, amount)) return p;
        }
        return null;
    }

    private static Item findWoolWithAtLeast(CompanionInventoryHandler inv, int amount) {
        if (inv == null) return null;
        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
            ItemStack st = inv.getStackInSlot(i);
            if (!st.isEmpty() && st.is(net.minecraft.tags.ItemTags.WOOL) && inv.countItem(st.getItem()) >= amount) {
                return st.getItem();
            }
        }
        return null;
    }

    private static Item getSourceLogForPlanks(Item planks) {
        if (planks == Items.OAK_PLANKS) return Items.OAK_LOG;
        if (planks == Items.BIRCH_PLANKS) return Items.BIRCH_LOG;
        if (planks == Items.SPRUCE_PLANKS) return Items.SPRUCE_LOG;
        if (planks == Items.DARK_OAK_PLANKS) return Items.DARK_OAK_LOG;
        if (planks == Items.ACACIA_PLANKS) return Items.ACACIA_LOG;
        if (planks == Items.CHERRY_PLANKS) return Items.CHERRY_LOG;
        if (planks == Items.JUNGLE_PLANKS) return Items.JUNGLE_LOG;
        if (planks == Items.MANGROVE_PLANKS) return Items.MANGROVE_LOG;
        if (planks == Items.BAMBOO_PLANKS) return Items.BAMBOO_BLOCK;
        if (planks == Items.CRIMSON_PLANKS) return Items.CRIMSON_STEM;
        if (planks == Items.WARPED_PLANKS) return Items.WARPED_STEM;
        return null;
    }

    private static Item getSourcePlanks(Item item) {
        if (item == null) return null;
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        // Check DARK_OAK before OAK so dark_oak_* doesn't match oak accidentally
        if (id.startsWith("dark_oak_")) return Items.DARK_OAK_PLANKS;
        for (ProceduralTemplates.WoodType w : ProceduralTemplates.WoodType.values()) {
            String prefix = w.name().toLowerCase(Locale.ROOT);
            if (id.startsWith(prefix + "_")) {
                return w.planks.getBlock().asItem();
            }
        }
        if (id.startsWith("crimson_")) return Items.CRIMSON_PLANKS;
        if (id.startsWith("warped_")) return Items.WARPED_PLANKS;
        return null;
    }

    private static Item getSourceStone(Item item) {
        if (item == null) return null;
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (id.startsWith("cobblestone_")) return Items.COBBLESTONE;
        if (id.startsWith("stone_brick_")) return Items.STONE_BRICKS;
        if (id.startsWith("stone_") && !id.startsWith("stone_brick")) return Items.STONE;
        if (id.startsWith("deepslate_brick_")) return Items.DEEPSLATE_BRICKS;
        if (id.startsWith("cobbled_deepslate_")) return Items.COBBLED_DEEPSLATE;
        if (id.startsWith("sandstone_")) return Items.SANDSTONE;
        if (id.startsWith("red_sandstone_")) return Items.RED_SANDSTONE;
        if (id.startsWith("brick_")) return Items.BRICKS;
        if (id.startsWith("nether_brick_")) return Items.NETHER_BRICKS;
        return null;
    }

    private static boolean isWoodItem(Item item) {
        if (item == null) return false;
        Block b = Block.byItem(item);
        if (b == null || b == Blocks.AIR) return false;
        BlockState s = b.defaultBlockState();
        return s.is(net.minecraft.tags.BlockTags.PLANKS)
                || s.is(net.minecraft.tags.BlockTags.WOODEN_STAIRS)
                || s.is(net.minecraft.tags.BlockTags.WOODEN_SLABS)
                || s.is(net.minecraft.tags.BlockTags.WOODEN_FENCES)
                || b instanceof net.minecraft.world.level.block.FenceGateBlock
                || s.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)
                || s.is(net.minecraft.tags.BlockTags.WOODEN_TRAPDOORS)
                || s.is(net.minecraft.tags.BlockTags.WOODEN_PRESSURE_PLATES)
                || s.is(net.minecraft.tags.BlockTags.WOODEN_BUTTONS)
                || s.is(net.minecraft.tags.BlockTags.ALL_SIGNS)
                || s.is(net.minecraft.tags.BlockTags.LOGS)
                || b == Blocks.CHEST
                || b == Blocks.BARREL
                || b == Blocks.CRAFTING_TABLE;
    }

    private static boolean isStoneItem(Item item) {
        if (item == null) return false;
        Block b = Block.byItem(item);
        if (b == null || b == Blocks.AIR) return false;
        BlockState s = b.defaultBlockState();
        return s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || s.is(net.minecraft.tags.BlockTags.STONE_BRICKS)
                || s.is(net.minecraft.tags.BlockTags.STAIRS)
                || s.is(net.minecraft.tags.BlockTags.SLABS)
                || s.is(net.minecraft.tags.BlockTags.WALLS)
                || b == Blocks.COBBLESTONE
                || b == Blocks.STONE;
    }

    public Map<Item, Integer> calculateMissingMaterials() {
        Map<Item, Integer> needed = new HashMap<>();
        if (plannedBlocks != null) {
            for (int i = currentBlockIndex; i < plannedBlocks.size(); i++) {
                BlockState st = plannedBlocks.get(i).state();
                if (st == null || st.isAir()
                        || SchematicImporter.isTechnicalOrUnobtainable(st)
                        || SchematicImporter.isSecondaryMultiBlockPart(st)) continue;
                Item item = st.getBlock().asItem();
                if (item != Items.AIR) {
                    needed.put(item, needed.getOrDefault(item, 0) + 1);
                }
            }
        }
        Map<Item, Integer> missing = new HashMap<>();
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            int inInv = companion.getInventoryHandler().countItem(entry.getKey());
            if (inInv < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - inInv);
            }
        }
        return missing;
    }

    public boolean requiresItem(Item item) {
        if (item == null || item == Items.AIR) return false;
        ItemStack defaultStack = item.getDefaultInstance();
        boolean isLogOrPlankOrStick = defaultStack.is(net.minecraft.tags.ItemTags.LOGS)
                || defaultStack.is(net.minecraft.tags.ItemTags.PLANKS)
                || defaultStack.is(net.minecraft.tags.ItemTags.CRIMSON_STEMS)
                || defaultStack.is(net.minecraft.tags.ItemTags.WARPED_STEMS)
                || item == Items.STICK;
        boolean isStoneVariant = item == Items.COBBLESTONE || item == Items.STONE
                || item == Items.STONE_BRICKS || item == Items.COBBLED_DEEPSLATE;
        boolean isDirtVariant = isGrassOrDirt(Block.byItem(item));

        List<Blueprint.BlockEntry> checkList = null;
        int startIdx = 0;
        if (plannedBlocks != null) {
            checkList = plannedBlocks;
            startIdx = currentBlockIndex;
        } else if (blueprint != null) {
            checkList = blueprint.getBlocks();
        }
        if (checkList != null) {
            for (int i = startIdx; i < checkList.size(); i++) {
                Blueprint.BlockEntry entry = checkList.get(i);
                if (entry == null || entry.state() == null) continue;
                Block block = entry.state().getBlock();
                Item blockItem = block.asItem();
                if (blockItem == item) return true;
                if (isLogOrPlankOrStick && (isWoodItem(blockItem) || block instanceof net.minecraft.world.level.block.BedBlock)) return true;
                if (isStoneVariant && isStoneItem(blockItem)) return true;
                if (isDirtVariant && isGrassOrDirt(block)) return true;
                if ((item == Items.GLASS || item == Items.SAND) && (block == Blocks.GLASS || block == Blocks.GLASS_PANE)) return true;
                if (block instanceof net.minecraft.world.level.block.BedBlock
                        && (defaultStack.is(net.minecraft.tags.ItemTags.WOOL) || defaultStack.is(net.minecraft.tags.ItemTags.BEDS))) {
                    return true;
                }
                if ((block == Blocks.TORCH || block == Blocks.WALL_TORCH || block == Blocks.LANTERN)
                        && (item == Items.COAL || item == Items.CHARCOAL || item == Items.TORCH || item == Items.LANTERN)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onStuckTargetUnreachable() {
        if (cleaningUp) {
            if (lastCleanupPos != null) {
                companion.getTemporaryBlocks().untrack(lastCleanupPos);
                lastCleanupPos = null;
                cleanupBlockTicks = 0;
            } else {
                BlockPos n = companion.getTemporaryBlocks().nearest(companion.position());
                if (n != null) companion.getTemporaryBlocks().untrack(n);
            }
            return;
        }
        if (!terrainLevelingDone) {
            levelingIndex++;
            return;
        }
        advance();
    }

    private void tell(String text, net.minecraft.ChatFormatting color) {
        if (companion.getOwnerPlayer() != null) {
            companion.getOwnerPlayer().sendSystemMessage(
                    Component.literal("[" + companion.getCustomCompanionName() + "] ")
                            .withStyle(net.minecraft.ChatFormatting.GOLD)
                            .append(Component.literal(text).withStyle(color)));
        }
    }

    @Override
    public void stop() {
        if (companion.level() instanceof ServerLevel sl) {
            survivalBreaker.reset(companion, sl);
            companion.getTemporaryBlocks().cancelCleanup(companion, sl);
        }
        pillarBase = null;
        companion.getNavManager().stop();
    }

    @Override
    public void pause() {
        super.pause();
        if (companion.level() instanceof ServerLevel sl) {
            survivalBreaker.reset(companion, sl);
            companion.getTemporaryBlocks().cancelCleanup(companion, sl);
        }
        pillarBase = null;
        companion.getNavManager().stop();
        if (buildProject != null) {
            buildProject.setStatus(com.aicompanion.ai.building.BuildProject.Status.PAUSED);
        }
        logEvent("PAUSA_TRABAJO civil=BUILD blueprint=" + blueprintName + " blockIndex=" + currentBlockIndex);
    }

    @Override
    public void cancel() {
        super.cancel();
        if (companion.level() instanceof ServerLevel sl) {
            survivalBreaker.reset(companion, sl);
            companion.getTemporaryBlocks().cancelCleanup(companion, sl);
        }
        pillarBase = null;
        companion.getNavManager().stop();
        if (buildProject != null) {
            buildProject.setStatus(com.aicompanion.ai.building.BuildProject.Status.CANCELLED);
        }
        if (companion.getCompanionBrain() != null && companion.getCompanionBrain().getActiveBuildProject() == buildProject) {
            companion.getCompanionBrain().clearActiveBuildProject();
        }
    }

    @Override
    public void resume() {
        super.resume();
        this.paused = false;
        if (buildProject != null) {
            buildProject.setStatus(com.aicompanion.ai.building.BuildProject.Status.IN_PROGRESS);
        }
        companion.setCurrentState(CompanionState.BUILDING);
        navCooldown = 0;
        EquipmentManager.autoEquipBestArmorAndWeapons(companion, false);
        if (companion.level() instanceof ServerLevel sl) {
            reconcileWorldState(sl);
        }
        if (originPos != null && companion.blockPosition().distSqr(originPos) > 16) {
            companion.getNavManager().moveTo(originPos, MovementPolicy.sprint());
        }
        logEvent("REANUDAR_TRABAJO civil=BUILD blueprint=" + blueprintName + " blockIndex=" + currentBlockIndex);
    }

    @Override
    public String getDescription() { return "Construir " + (com.aicompanion.ai.building.HouseSpec.isEncoded(blueprintName) ? "casa" : blueprintName); }
    @Override
    public String getProgress() {
        if (!terrainLevelingDone) {
            int total = levelingClearList.size() + levelingFillList.size();
            return "Nivelando: " + levelingIndex + " / " + total;
        }
        if (cleaningUp) return "Retirando andamios";
        return currentBlockIndex + " / " + (plannedBlocks != null ? plannedBlocks.size() : 0)
                + (verifyPass > 0 ? " (verificación " + verifyPass + ")" : "");
    }
    @Override
    public String getTaskType() { return "BUILD"; }
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("BlueprintName", blueprintName);
        if (buildingConfig != null) {
            tag.putString("BuildingConfig", buildingConfig.serialize());
        }
        if (originPos != null) tag.put("OriginPos", NbtUtils.writeBlockPos(originPos));
        if (buildProject != null) {
            tag.put("BuildProject", buildProject.serialize());
        }
        tag.putInt("CurrentBlockIndex", currentBlockIndex);
        tag.putBoolean("TerrainLevelingDone", terrainLevelingDone);
        tag.putInt("LevelingIndex", levelingIndex);
        tag.putBoolean("FixedOrigin", fixedOrigin);
        tag.putBoolean("SiteValidated", siteValidated);
        tag.putInt("VerifyPass", verifyPass);
        tag.putBoolean("CleaningUp", cleaningUp);
        tag.putInt("SkippedProtected", skippedProtected);
        tag.putInt("AcquisitionRounds", acquisitionRounds);
        CompoundTag subs = new CompoundTag();
        for (Map.Entry<Block, Block> e : substitutions.entrySet()) {
            subs.putString(BuiltInRegistries.BLOCK.getKey(e.getKey()).toString(), BuiltInRegistries.BLOCK.getKey(e.getValue()).toString());
        }
        tag.put("Substitutions", subs);
        return tag;
    }
    @Override
    public void load(CompoundTag tag) {
        this.blueprintName = tag.getString("BlueprintName");
        if (tag.contains("BuildingConfig")) {
            this.buildingConfig = BuildingConfig.deserialize(tag.getString("BuildingConfig"));
        }
        if (tag.contains("OriginPos")) this.originPos = NbtUtils.readBlockPos(tag.getCompound("OriginPos"));
        if (tag.contains("BuildProject")) {
            this.buildProject = com.aicompanion.ai.building.BuildProject.deserialize(tag.getCompound("BuildProject"));
            if (this.originPos == null && this.buildProject != null) {
                this.originPos = this.buildProject.getOriginPos();
                this.fixedOrigin = true;
            }
        }
        this.currentBlockIndex = tag.getInt("CurrentBlockIndex");
        this.terrainLevelingDone = tag.getBoolean("TerrainLevelingDone");
        this.levelingIndex = tag.getInt("LevelingIndex");
        // Old saves (no flag) with progress also keep their site
        this.fixedOrigin = tag.contains("FixedOrigin") ? tag.getBoolean("FixedOrigin")
                : (originPos != null && (currentBlockIndex > 0 || terrainLevelingDone));
        this.siteValidated = tag.getBoolean("SiteValidated");
        this.verifyPass = tag.getInt("VerifyPass");
        this.cleaningUp = tag.getBoolean("CleaningUp");
        this.skippedProtected = tag.getInt("SkippedProtected");
        this.acquisitionRounds = tag.getInt("AcquisitionRounds");
        this.substitutions.clear();
        CompoundTag subs = tag.getCompound("Substitutions");
        for (String k : subs.getAllKeys()) {
            Block from = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(k));
            Block to = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(subs.getString(k)));
            if (from != Blocks.AIR && to != Blocks.AIR) substitutions.put(from, to);
        }

        if (this.buildingConfig != null) {
            Blueprint base = BlueprintRegistry.get(this.buildingConfig.getBlueprintId());
            if (base == null) base = BlueprintRegistry.get(this.blueprintName);
            if (base != null) {
                this.blueprint = BlueprintTransformer.transform(base, this.buildingConfig);
            } else {
                this.blueprint = BlueprintRegistry.get(blueprintName);
            }
        } else {
            this.blueprint = BlueprintRegistry.get(blueprintName);
        }

        if (this.blueprint != null) {
            this.plannedBlocks = BuildPlacementPlanner.planConstructionOrder(blueprint);
        }
    }
}
