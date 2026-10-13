package com.aicompanion.ai.tasks;

import com.aicompanion.AICompanionMod;
import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.nav.MovementPolicy;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.ai.equipment.ToolSelectionPolicy;
import com.aicompanion.ai.nav.NavigationRequestResult;
import com.aicompanion.util.BlockBreakHelper;
import com.aicompanion.util.TerrainPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Enterprise Autonomous Dimension Travel Task.
 * Navigates into existing Nether or End portals, initiates the dimensional transition,
 * records departure and arrival portal positions in spatial memory, and allows safe interdimensional travel.
 */
public class DimensionTravelTask extends CompanionTask {
    private ResourceKey<Level> targetDim;
    private BlockPos portalPos = null;
    private int waitInPortalTicks = 0;
    private int repathCooldown = 0;
    private int searchCooldown = 0;
    private int timeoutTicks = 0;

    // Safe cliff descent when portal is deep below (e.g. Nether terrain cliffs)
    private boolean descendingToPortal = false;
    private Direction portalDescentDir = null;
    private int portalDescentCooldown = 0;
    private int portalDescentTicks = 0;
    private int portalLastDescentY = 0;
    private int portalLastYProgressTick = 0;
    private int noPathTicks = 0;

    public DimensionTravelTask(CompanionEntity companion, ResourceKey<Level> targetDim) {
        super(companion);
        this.targetDim = targetDim;
        this.stage = "DIMENSION_TRAVEL";
        this.substate = "Localizando portal para viajar a " + targetDim.location().getPath();
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.TRAVELLING_DIMENSION);
        markProgress();
    }

    @Override
    public java.util.List<CompanionTask> checkPreconditions() {
        java.util.List<CompanionTask> prereqs = new java.util.ArrayList<>();
        if (companion.level().dimension().equals(targetDim)) {
            return prereqs; // Already in destination
        }

        BlockPos portal = findPortal();
        if (portal == null && targetDim.equals(Level.NETHER)) {
            // No portal nearby: prerequisite is to build one!
            prereqs.add(new BuildNetherPortalTask(companion));
        }
        return prereqs;
    }

    @Override
    public void tick() {
        if (paused || complete) return;

        // If already in target dimension, complete immediately
        if (companion.level().dimension().equals(targetDim)) {
            AICompanionMod.LOGGER.info("[ESCLAVO-DIM] Companion arrived in target dimension {}", targetDim.location());
            // Remember arrival portal location in new dimension
            if (companion.level() instanceof ServerLevel sl) {
                BlockPos arrivalPos = companion.blockPosition();
                companion.getMemory().getWorldModel().rememberPOI(arrivalPos, Blocks.NETHER_PORTAL, sl.getGameTime());
                companion.getMemory().getWorldModel().rememberLocation("return_portal", arrivalPos);
            }
            companion.setCurrentState(CompanionState.IDLE);
            complete = true;
            return;
        }

        if (++timeoutTicks > 20 * 120) { // 2 minutes timeout
            AICompanionMod.LOGGER.warn("[ESCLAVO-DIM] DimensionTravelTask timed out waiting to enter portal.");
            fail("Tiempo de espera agotado sin lograr atravesar el portal hacia " + targetDim.location().getPath());
            return;
        }

        if (portalPos == null || --searchCooldown <= 0) {
            searchCooldown = 30;
            portalPos = findPortal();
            if (portalPos == null) {
                this.substate = "Buscando portal cercano a " + targetDim.location().getPath();
                return;
            }
        }

        this.substate = "Caminando hacia el portal en " + portalPos.toShortString();
        double distSq = companion.blockPosition().distSqr(portalPos);

        // Strict physical bounding box check for portal contact
        boolean insidePortal = com.aicompanion.ai.dimension.PortalHelper.isEntityInPortal(companion);

        if (!insidePortal) {
            waitInPortalTicks = Math.max(0, waitInPortalTicks - 1);
            if (distSq <= 9.0D) {
                this.descendingToPortal = false;
                this.noPathTicks = 0;
                repathCooldown = 0;
                com.aicompanion.ai.dimension.PortalHelper.guideIntoPortal(companion, portalPos);
            } else if (this.descendingToPortal && companion.level() instanceof ServerLevel sl) {
                tickPortalDescent(sl);
            } else {
                if (--repathCooldown <= 0) {
                    repathCooldown = 15;
                    com.aicompanion.ai.dimension.PortalHelper.guideIntoPortal(companion, portalPos);
                }

                if (companion.level() instanceof ServerLevel sl) {
                    var navStatus = companion.getNavManager().getActiveProvider().getStatus();
                    if (navStatus == NavigationRequestResult.NO_PATH || companion.getNavManager().isStuck()) {
                        noPathTicks++;
                    } else {
                        noPathTicks = Math.max(0, noPathTicks - 1);
                    }

                    if (noPathTicks >= 20 && companion.getBlockY() - portalPos.getY() > 3) {
                        initiatePortalDescent(sl);
                    }
                }
            }
        } else {
            // Physically standing inside the portal: cancel momentum, hold position centered in portal, wait 10 ticks
            waitInPortalTicks++;
            com.aicompanion.ai.dimension.PortalHelper.holdInPortalCenter(companion, portalPos);
            this.substate = "Atravesando portal dimensional hacia " + targetDim.location().getPath() + "... (" + waitInPortalTicks + "/10)";

            if (waitInPortalTicks >= 10) { // 10 ticks continuous physical contact
                if (companion.getServer() != null) {
                    ServerLevel targetLevel = companion.getServer().getLevel(targetDim);
                    if (targetLevel != null) {
                        AICompanionMod.LOGGER.info("[ESCLAVO-DIM] Entrando físicamente en portal.");
                        AICompanionMod.LOGGER.info("[ESCLAVO-DIM] Origen: {} en {}", companion.level().dimension().location(), companion.blockPosition());
                        AICompanionMod.LOGGER.info("[ESCLAVO-DIM] Destino solicitado: {}", targetDim.location());

                        companion.getMemory().getWorldModel().rememberDimensionalPortal(companion.level().dimension().location().toString(), portalPos);
                        companion.getMemory().getWorldModel().rememberPOI(portalPos, Blocks.NETHER_PORTAL, companion.level().getGameTime());
                        companion.getMemory().getWorldModel().rememberLocation("departure_portal", portalPos);

                        CompanionEntity newCompanion = com.aicompanion.ai.dimension.PortalHelper.executeTransition(companion, targetLevel, portalPos);

                        if (newCompanion != null) {
                            AICompanionMod.LOGGER.info("[ESCLAVO-DIM] Destino real confirmado: {}", newCompanion.level().dimension().location());

                            BlockPos arrivalPos = newCompanion.blockPosition();
                            newCompanion.getMemory().getWorldModel().rememberDimensionalPortal(targetLevel.dimension().location().toString(), arrivalPos);
                            newCompanion.getMemory().getWorldModel().rememberPOI(arrivalPos, Blocks.NETHER_PORTAL, targetLevel.getGameTime());
                            newCompanion.getMemory().getWorldModel().rememberLocation("return_portal", arrivalPos);
                            newCompanion.setCurrentState(CompanionState.IDLE);

                            complete = true;
                            return;
                        } else {
                            AICompanionMod.LOGGER.error("[ESCLAVO-DIM] Error en transferencia dimensional: entidad resultante inválida o nula. Permaneciendo en origen para reintentar.");
                            waitInPortalTicks = 0;
                            // DO NOT set complete = true! Keep task active to retry safely
                        }
                    }
                }
            }
        }
    }

    private BlockPos findPortal() {
        return com.aicompanion.ai.dimension.PortalHelper.findPortal(companion, targetDim);
    }

    private void initiatePortalDescent(ServerLevel sl) {
        this.descendingToPortal = true;
        this.portalDescentTicks = 0;
        this.portalDescentCooldown = 0;
        this.portalLastDescentY = companion.getBlockY();
        this.portalLastYProgressTick = 0;
        this.noPathTicks = 0;

        int dx = portalPos.getX() - companion.getBlockX();
        int dz = portalPos.getZ() - companion.getBlockZ();
        this.portalDescentDir = Direction.getNearest(dx, 0, dz);
        if (this.portalDescentDir == Direction.UP || this.portalDescentDir == Direction.DOWN) {
            this.portalDescentDir = companion.getDirection();
        }

        this.stage = "PORTAL_DESCENT";
        this.substate = "Descendiendo acantilado hacia el portal (Y=" + portalPos.getY() + ")";
        this.companion.tellOwner("Terreno escarpado detectado hacia el portal (desnivel de " + (companion.getBlockY() - portalPos.getY()) + "m). Iniciando descenso seguro.");
        AICompanionMod.LOGGER.info("[ESCLAVO-DIM] Iniciando descenso seguro hacia portal en {} desde Y={} hasta Y={}",
                portalPos, companion.getBlockY(), portalPos.getY());
    }

    private void tickPortalDescent(ServerLevel sl) {
        this.portalDescentTicks++;
        int currentY = companion.getBlockY();
        if (this.portalDescentTicks % 20 == 0) {
            if (currentY < this.portalLastDescentY) {
                this.portalLastDescentY = currentY;
                this.portalLastYProgressTick = this.portalDescentTicks;
            }
        }

        // Watchdog: If stuck in descent for 45s without Y progress or 90s total
        if ((this.portalDescentTicks - this.portalLastYProgressTick) > 20 * 45 || this.portalDescentTicks > 20 * 90) {
            AICompanionMod.LOGGER.warn("[ESCLAVO-DIM] Watchdog de descenso hacia portal activado (Y={}). Reintentando navegación.", currentY);
            this.descendingToPortal = false;
            this.stage = "DIMENSION_TRAVEL";
            this.portalDescentTicks = 0;
            return;
        }

        // Complete descent only when physically near portal altitude or close to portal
        if (currentY <= portalPos.getY() + 3 || companion.blockPosition().distSqr(portalPos) <= 16.0) {
            AICompanionMod.LOGGER.info("[ESCLAVO-DIM] Descenso completado a nivel de portal (Y={}, PortalY={}).", currentY, portalPos.getY());
            this.descendingToPortal = false;
            this.stage = "DIMENSION_TRAVEL";
            this.portalDescentTicks = 0;
            this.noPathTicks = 0;
            return;
        }

        if (--this.portalDescentCooldown <= 0) {
            BlockPos selfPos = companion.blockPosition();
            int dx = portalPos.getX() - selfPos.getX();
            int dz = portalPos.getZ() - selfPos.getZ();
            Direction preferred = Direction.getNearest(dx, 0, dz);
            if (preferred != Direction.UP && preferred != Direction.DOWN) {
                this.portalDescentDir = preferred;
            }

            BlockPos stepBelow = selfPos.relative(this.portalDescentDir).below();
            BlockPos headBlock = stepBelow.above(2);
            BlockPos feetBlock = stepBelow.above(1);
            BlockState headState = sl.getBlockState(headBlock);
            BlockState feetState = sl.getBlockState(feetBlock);
            BlockState floorState = sl.getBlockState(stepBelow);

            // Lava safety check!
            if (headState.is(Blocks.LAVA) || feetState.is(Blocks.LAVA) || floorState.is(Blocks.LAVA)) {
                this.portalDescentDir = this.portalDescentDir.getClockWise();
                this.portalDescentCooldown = 5;
                return;
            }

            if (!headState.isAir() && headState.blocksMotion()) {
                if (TerrainPolicy.mayBreakImplicitly(sl, headBlock, headState, companion.getTemporaryBlocks())) {
                    ToolSelectionPolicy.ensurePickaxeEquipped(companion, headState);
                    BlockBreakHelper.finishBreaking(companion, sl, headBlock);
                    this.portalDescentCooldown = 4;
                } else {
                    this.portalDescentDir = this.portalDescentDir.getClockWise();
                    this.portalDescentCooldown = 5;
                }
            } else if (feetState.isAir() || !feetState.blocksMotion()) {
                if (floorState.isAir() || !floorState.blocksMotion()) {
                    // Open drop: bridge if blocks available or turn along wall
                    ItemStack blockItem = findPlacableBlock(companion);
                    if (!blockItem.isEmpty()) {
                        sl.setBlockAndUpdate(stepBelow, Blocks.COBBLESTONE.defaultBlockState());
                        blockItem.shrink(1);
                        this.portalDescentCooldown = 4;
                    } else {
                        this.portalDescentDir = this.portalDescentDir.getClockWise();
                        this.portalDescentCooldown = 5;
                    }
                } else {
                    companion.getNavManager().moveTo(stepBelow.above(), MovementPolicy.walk());
                    this.portalDescentCooldown = 10;
                }
            } else if (TerrainPolicy.mayBreakImplicitly(sl, feetBlock, feetState, companion.getTemporaryBlocks())) {
                ToolSelectionPolicy.ensurePickaxeEquipped(companion, feetState);
                BlockBreakHelper.finishBreaking(companion, sl, feetBlock);
                this.portalDescentCooldown = 4;
            } else {
                this.portalDescentDir = this.portalDescentDir.getClockWise();
                this.portalDescentCooldown = 5;
            }
        }
    }

    private ItemStack findPlacableBlock(CompanionEntity companion) {
        com.aicompanion.inventory.CompanionInventoryHandler inv = companion.getInventoryHandler();
        for (int i = 0; i < com.aicompanion.inventory.CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
                if (bi.getBlock() == Blocks.COBBLESTONE || bi.getBlock() == Blocks.NETHERRACK
                        || bi.getBlock() == Blocks.DIRT || bi.getBlock() == Blocks.STONE
                        || bi.getBlock() == Blocks.DEEPSLATE || bi.getBlock() == Blocks.BLACKSTONE) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public String getDescription() { return "Viajar a " + targetDim.location().getPath(); }
    @Override
    public String getProgress() { return substate; }
    @Override
    public String getTaskType() { return "DIMENSION_TRAVEL"; }

    @Override
    public boolean isStationaryWorking() {
        return this.descendingToPortal;
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("TargetDim", targetDim.location().toString());
        if (portalPos != null) tag.putLong("PortalPos", portalPos.asLong());
        tag.putInt("WaitTicks", waitInPortalTicks);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("TargetDim")) {
            this.targetDim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("TargetDim")));
        }
        if (tag.contains("PortalPos")) {
            this.portalPos = BlockPos.of(tag.getLong("PortalPos"));
        }
        this.waitInPortalTicks = tag.getInt("WaitTicks");
    }
}
