package com.aicompanion.util;

import com.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers every temporary block (pillar / bridge / rescue step) placed by the companion so that it
 * can be removed afterwards instead of leaving dirt towers all over the world.
 * Only blocks that still match what was placed are removed, so player edits are never undone.
 * Persisted in the entity NBT, so cleanup survives save/reload.
 */
public class TemporaryBlockTracker {

    private static final int MAX_TRACKED = 256;
    private final Map<BlockPos, ResourceLocation> placed = new LinkedHashMap<>();
    private int cooldown = 0;

    public void track(BlockPos pos, Block block) {
        if (pos == null || block == null) return;
        if (placed.size() >= MAX_TRACKED) {
            Iterator<BlockPos> it = placed.keySet().iterator();
            if (it.hasNext()) { it.next(); it.remove(); }
        }
        placed.put(pos.immutable(), BuiltInRegistries.BLOCK.getKey(block));
    }

    public boolean isTracked(BlockPos pos) {
        return pos != null && placed.containsKey(pos);
    }

    public void untrack(BlockPos pos) {
        if (pos != null) {
            placed.remove(pos);
            if (pos.equals(breakingPos)) {
                breakingPos = null;
                breakingTicks = 0;
                requiredTicks = 0;
            }
        }
    }

    public int size() {
        return placed.size();
    }

    /** Nearest tracked block to the given point (null when nothing is tracked). */
    public BlockPos nearest(net.minecraft.world.phys.Vec3 from) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : placed.keySet()) {
            double d = p.distToCenterSqr(from);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    /**
     * Prunes tracked entries that are already air, no longer match the placed block, or lie outside
     * {@code maxAnchorDistSq} of {@code anchor}, and returns the nearest valid remaining block.
     */
    public BlockPos purgeInvalidAndNearest(ServerLevel sl, net.minecraft.world.phys.Vec3 from, BlockPos anchor, double maxAnchorDistSq) {
        if (placed.isEmpty()) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        Iterator<Map.Entry<BlockPos, ResourceLocation>> it = placed.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, ResourceLocation> e = it.next();
            BlockPos p = e.getKey();
            if (anchor != null && p.distSqr(anchor) > maxAnchorDistSq) {
                it.remove();
                continue;
            }
            if (sl != null) {
                if (!sl.isLoaded(p)) {
                    it.remove();
                    continue;
                }
                BlockState st = sl.getBlockState(p);
                Block expected = BuiltInRegistries.BLOCK.get(e.getValue());
                if (st.isAir() || expected == Blocks.AIR || !st.is(expected)) {
                    it.remove();
                    continue;
                }
            }
            double d = p.distToCenterSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }


    /** True when the companion stands on top of one of its own temporary blocks that can be safely descended. */
    public boolean isStandingOnTracked(CompanionEntity companion) {
        if (placed.isEmpty() || companion == null) return false;
        BlockPos belowFeet = companion.blockPosition().below();
        if (!placed.containsKey(belowFeet)) return false;
        if (companion.level() instanceof ServerLevel sl) {
            return hasSolidGroundBelow(sl, belowFeet, 3);
        }
        return true;
    }

    private boolean hasSolidGroundBelow(ServerLevel sl, BlockPos pos, int maxDrop) {
        for (int i = 1; i <= maxDrop; i++) {
            BlockPos check = pos.below(i);
            if (!sl.getBlockState(check).getCollisionShape(sl, check).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private BlockPos breakingPos = null;
    private int breakingTicks = 0;
    private int requiredTicks = 0;

    /**
     * Cancels any active visual block breaking progress.
     */
    public void cancelCleanup(CompanionEntity companion, ServerLevel sl) {
        if (breakingPos != null && companion != null && sl != null) {
            BlockBreakHelper.cancelBreaking(companion, sl, breakingPos);
        }
        breakingPos = null;
        breakingTicks = 0;
        requiredTicks = 0;
    }

    /**
     * Removes at most one nearby temporary block using physical survival mechanics
     * (proper tool, real break time, arm swing, particles, sounds, tool damage, Forge event).
     * Only runs when the companion is not standing on / inside the block.
     * The recovered item goes back into the companion inventory (or drops if full).
     */
    public void tickCleanup(CompanionEntity companion, boolean allowed) {
        tickCleanup(companion, allowed, false);
    }

    /**
     * @param descendOnly when true only the block under the companion's feet is considered
     *                    (safe while other tasks run: never removes blocks it may still walk on).
     */
    public void tickCleanup(CompanionEntity companion, boolean allowed, boolean descendOnly) {
        if (!allowed || placed.isEmpty() || !(companion.level() instanceof ServerLevel sl)) {
            if (breakingPos != null && companion != null && companion.level() instanceof ServerLevel s) {
                cancelCleanup(companion, s);
            }
            return;
        }

        BlockPos feet = companion.blockPosition();

        // If currently breaking a block, ensure it's still valid and in reach
        if (breakingPos != null) {
            if (!placed.containsKey(breakingPos) || !sl.isLoaded(breakingPos)) {
                cancelCleanup(companion, sl);
            } else {
                BlockState currentSt = sl.getBlockState(breakingPos);
                if (currentSt.isAir()) {
                    placed.remove(breakingPos);
                    cancelCleanup(companion, sl);
                    return;
                }
                if (breakingPos.distToCenterSqr(companion.position()) > 22.0) {
                    cancelCleanup(companion, sl);
                    return;
                }
            }
        }

        // Select best target if not currently breaking or breaking completed
        if (breakingPos == null) {
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            Iterator<Map.Entry<BlockPos, ResourceLocation>> it = placed.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, ResourceLocation> e = it.next();
                BlockPos p = e.getKey();
                if (!sl.isLoaded(p)) continue;
                BlockState st = sl.getBlockState(p);
                Block expected = BuiltInRegistries.BLOCK.get(e.getValue());
                if (st.isAir() || expected == Blocks.AIR || !st.is(expected)) {
                    it.remove(); // already gone or modified by someone else -> forget it
                    continue;
                }
                if (p.equals(feet) || p.equals(feet.above())) continue;
                if (p.equals(feet.below())) {
                    // Standing on our own pillar: descend one block at a time (only when on the ground)
                    if (!companion.onGround()) continue;
                    int maxSafeDrop = descendOnly ? 3 : 1;
                    if (!hasSolidGroundBelow(sl, p, maxSafeDrop)) continue; // bridge over a deep gap
                    best = p;
                    bestDist = -1;
                    break;
                }
                if (descendOnly) continue;
                double d = p.distToCenterSqr(companion.position());
                if (d < bestDist) { bestDist = d; best = p; }
            }
            if (best == null || bestDist > 20.25) return; // within ~4.5 blocks reach

            breakingPos = best.immutable();
            breakingTicks = 0;
            BlockState targetState = sl.getBlockState(breakingPos);
            com.aicompanion.ai.equipment.ToolSelectionPolicy.ensureToolForBlock(companion, targetState);
            requiredTicks = BlockBreakHelper.calculateBreakTicks(companion, breakingPos, targetState);
            if (requiredTicks <= 0) requiredTicks = 4;
        }

        // Active physical breaking tick
        BlockState st = sl.getBlockState(breakingPos);
        com.aicompanion.ai.equipment.ToolSelectionPolicy.ensureToolForBlock(companion, st);
        companion.getLookControl().setLookAt(breakingPos.getX() + 0.5, breakingPos.getY() + 0.5, breakingPos.getZ() + 0.5);

        breakingTicks++;
        BlockBreakHelper.tickBreaking(companion, sl, breakingPos, breakingTicks, requiredTicks);

        if (breakingTicks >= requiredTicks) {
            BlockBreakHelper.finishBreaking(companion, sl, breakingPos);
            placed.remove(breakingPos);
            breakingPos = null;
            breakingTicks = 0;
            requiredTicks = 0;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, ResourceLocation> e : placed.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("p", e.getKey().asLong());
            t.putString("b", e.getValue().toString());
            list.add(t);
        }
        tag.put("blocks", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        placed.clear();
        if (tag == null) return;
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ResourceLocation rl = ResourceLocation.tryParse(t.getString("b"));
            if (rl != null) {
                placed.put(BlockPos.of(t.getLong("p")), rl);
            }
        }
    }
}
