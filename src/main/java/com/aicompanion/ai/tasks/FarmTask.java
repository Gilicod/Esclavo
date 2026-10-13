package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Autonomous farming daemon for Esclavo.
 * Detects mature crops within radius, harvests them, collects the drops,
 * and replants seeds on farmland to keep farms self-sustaining indefinitely.
 */
public class FarmTask extends CompanionTask {
    private BlockPos farmCenter;
    private int radius = 16;
    private BlockPos targetCrop = null;
    private int scanCooldown = 0;
    private int harvestedCount = 0;
    private int replantDelay = 0;
    private int repathCooldown = 0;
    private BlockPos pendingReplantPos = null;

    public FarmTask(CompanionEntity companion) {
        this(companion, 16);
    }

    public FarmTask(CompanionEntity companion, int radius) {
        super(companion);
        this.radius = Math.max(4, Math.min(32, radius));
        this.farmCenter = companion.blockPosition();
    }

    @Override
    public void start() {
        if (farmCenter == null) {
            farmCenter = companion.blockPosition();
        }
        this.repathCooldown = 0;
        companion.setCurrentState(CompanionState.FARMING);
    }

    @Override
    public void tick() {
        if (paused || complete) return;
        companion.setCurrentState(CompanionState.FARMING);

        if (!(companion.level() instanceof ServerLevel sl)) return;

        // 1. Pending replant after harvesting
        if (pendingReplantPos != null && --replantDelay <= 0) {
            replantAt(sl, pendingReplantPos);
            pendingReplantPos = null;
            targetCrop = null;
        }

        // 2. Active target crop harvesting
        if (targetCrop != null) {
            double distSq = companion.blockPosition().distSqr(targetCrop);
            if (distSq > 9.0) {
                if (--repathCooldown <= 0 || companion.getNavManager().isDone()) {
                    repathCooldown = 15;
                    companion.getNavManager().moveTo(targetCrop, 1.0);
                }
                return;
            }

            // In range: harvest
            companion.getNavManager().stop();
            companion.getLookControl().setLookAt(targetCrop.getX() + 0.5, targetCrop.getY() + 0.5, targetCrop.getZ() + 0.5);
            BlockState state = sl.getBlockState(targetCrop);
            if (isMatureCrop(state)) {
                companion.swing(InteractionHand.MAIN_HAND, true);
                sl.destroyBlock(targetCrop, true, companion);
                harvestedCount++;
                markProgress();
                pendingReplantPos = targetCrop;
                replantDelay = 6; // brief pause for drop spawn before replanting
            } else {
                targetCrop = null;
            }
            return;
        }

        // 3. Periodic scan for mature crops or empty farmland needing replanting
        if (--scanCooldown <= 0) {
            scanCooldown = 40; // scan every 2 seconds
            targetCrop = findNextCropToHarvest(sl);
            if (targetCrop == null) {
                // If nothing to harvest, check if companion is too far from farm center
                if (companion.blockPosition().distSqr(farmCenter) > 36.0) {
                    companion.getNavManager().moveTo(farmCenter, 1.0);
                }
            }
        }
    }

    private boolean isMatureCrop(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) {
            return true;
        }
        return false;
    }

    private BlockPos findNextCropToHarvest(ServerLevel sl) {
        BlockPos origin = companion.blockPosition();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos p = farmCenter.offset(dx, dy, dz);
                    if (isMatureCrop(sl.getBlockState(p))) {
                        double d = origin.distSqr(p);
                        if (d < bestDistSq) {
                            bestDistSq = d;
                            best = p;
                        }
                    }
                }
            }
        }
        return best;
    }

    private void replantAt(ServerLevel sl, BlockPos pos) {
        BlockState below = sl.getBlockState(pos.below());
        if (!(below.getBlock() instanceof FarmBlock) && !below.is(Blocks.FARMLAND)) {
            return;
        }
        if (!sl.getBlockState(pos).isAir()) {
            return;
        }

        var inv = companion.getInventoryHandler();
        // Look for seeds in inventory
        for (int i = 0; i < com.aicompanion.inventory.CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            BlockState plantState = null;
            if (stack.is(Items.WHEAT_SEEDS)) plantState = Blocks.WHEAT.defaultBlockState();
            else if (stack.is(Items.CARROT)) plantState = Blocks.CARROTS.defaultBlockState();
            else if (stack.is(Items.POTATO)) plantState = Blocks.POTATOES.defaultBlockState();
            else if (stack.is(Items.BEETROOT_SEEDS)) plantState = Blocks.BEETROOTS.defaultBlockState();

            if (plantState != null) {
                inv.extractItem(i, 1, false);
                companion.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                sl.setBlock(pos, plantState, 3);
                sl.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
                companion.swing(InteractionHand.MAIN_HAND, true);
                break;
            }
        }
    }

    @Override
    public void pause() {
        super.pause();
        companion.getNavManager().stop();
        logEvent("PAUSA_TRABAJO civil=FARM harvested=" + harvestedCount);
    }

    @Override
    public void resume() {
        super.resume();
        scanCooldown = 0;
        targetCrop = null;
        logEvent("REANUDAR_TRABAJO civil=FARM harvested=" + harvestedCount);
    }

    @Override
    public void onStuckTargetUnreachable() {
        this.targetCrop = null;
        this.scanCooldown = 10;
        this.repathCooldown = 0;
    }

    @Override
    public String getDescription() {
        return "Cuidar granja y cultivos (Radio: " + radius + "m)";
    }

    @Override
    public String getProgress() {
        return targetCrop != null ? "Cosechando cultivo" : "Cosechados: " + harvestedCount + " (Vigilando granja)";
    }

    @Override
    public String getTaskType() {
        return "FARM";
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (farmCenter != null) tag.put("FarmCenter", NbtUtils.writeBlockPos(farmCenter));
        tag.putInt("Radius", radius);
        tag.putInt("HarvestedCount", harvestedCount);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("FarmCenter")) this.farmCenter = NbtUtils.readBlockPos(tag.getCompound("FarmCenter"));
        if (tag.contains("Radius")) this.radius = tag.getInt("Radius");
        if (tag.contains("HarvestedCount")) this.harvestedCount = tag.getInt("HarvestedCount");
    }
}
