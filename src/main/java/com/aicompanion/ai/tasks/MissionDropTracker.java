package com.aicompanion.ai.tasks;

import com.aicompanion.ai.nav.MovementPolicy;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.internal.baritone.goals.GoalNear;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.util.BlockBreakHelper;
import com.aicompanion.util.TerrainPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Predicate;

/**
 * Drop acquisition engine.
 *
 * The companion now WALKS to every mission drop and picks it up only when the item is inside the
 * vanilla player pickup box (no magnet / vacuum / 5-block instant pickup). Drops that cannot be
 * reached after several attempts are remembered by UUID for the whole task, so the task can never
 * loop forever on the same unreachable item. Obstacles on the way are only broken when they are
 * natural terrain (protectPlayerBuilds) and always with real, timed breaking.
 */
public class MissionDropTracker {
    private UUID currentDropUUID = null;
    private final Set<UUID> trackedDropUUIDs = new LinkedHashSet<>();
    /** Persistent for the life of the owning task: never retried. */
    private final Set<UUID> abandonedDrops = new HashSet<>();
    private int collectionTicks = 0;
    private int maxAllowedTicks = 140;
    private int graceTicks = 0;
    private int unreachableRetries = 0;

    // Timed obstacle breaking
    private BlockPos breakingPos = null;
    private int breakingTicks = 0;

    public void reset() {
        if (breakingPos != null) {
            breakingPos = null;
            breakingTicks = 0;
        }
        this.currentDropUUID = null;
        this.trackedDropUUIDs.clear();
        this.collectionTicks = 0;
        this.graceTicks = 0;
        this.unreachableRetries = 0;
    }

    /** Full reset including the unreachable blacklist (new task). */
    public void resetAll() {
        reset();
        abandonedDrops.clear();
    }

    public void startGracePeriod(int ticks) {
        this.graceTicks = ticks;
    }

    public boolean isBusyCollecting() {
        return (currentDropUUID != null || !trackedDropUUIDs.isEmpty() || graceTicks > 0);
    }

    public boolean isAbandoned(ItemEntity item) {
        return item != null && abandonedDrops.contains(item.getUUID());
    }

    public ItemEntity getCurrentDropEntity(ServerLevel level) {
        if (level == null || currentDropUUID == null) return null;
        Entity ent = level.getEntity(currentDropUUID);
        return (ent instanceof ItemEntity item && item.isAlive()) ? item : null;
    }

    /**
     * Checks if an ItemEntity was dropped/thrown by a player to avoid stealing or absorbing player items.
     */
    public static boolean isPlayerDrop(ItemEntity item) {
        if (item == null) return false;
        if (item.getOwner() != null) return true;
        try {
            java.lang.reflect.Field f = ItemEntity.class.getDeclaredField("thrower");
            f.setAccessible(true);
            if (f.get(item) != null) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Scans for matching drops around centerPos and the companion (falls from trees included),
     * skipping drops already declared unreachable or dropped by players.
     */
    public List<ItemEntity> scanDrops(CompanionEntity companion, BlockPos centerPos, Predicate<ItemStack> filter) {
        List<ItemEntity> results = new ArrayList<>();
        if (companion == null || !(companion.level() instanceof ServerLevel level) || centerPos == null) {
            return results;
        }

        double minX = Math.min(centerPos.getX(), companion.getBlockX()) - 8.0;
        double maxX = Math.max(centerPos.getX(), companion.getBlockX()) + 9.0;
        double minY = Math.min(centerPos.getY(), companion.getBlockY()) - 12.0;
        double maxY = Math.max(centerPos.getY(), companion.getBlockY()) + 6.0;
        double minZ = Math.min(centerPos.getZ(), companion.getBlockZ()) - 8.0;
        double maxZ = Math.max(centerPos.getZ(), companion.getBlockZ()) + 9.0;

        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        List<ItemEntity> candidates = level.getEntitiesOfClass(ItemEntity.class, box);
        for (ItemEntity item : candidates) {
            if (!item.isAlive() || item.getItem().isEmpty()) continue;
            if (isPlayerDrop(item)) continue; // Never collect items tossed by players during harvesting missions
            if (abandonedDrops.contains(item.getUUID())) continue;
            if (filter != null && !filter.test(item.getItem())) continue;
            if (!results.contains(item)) {
                results.add(item);
            }
        }

        results.sort(Comparator.comparingDouble(companion::distanceToSqr));
        return results;
    }

    /**
     * Raycasts line-of-sight between companion's eyes and the drop to prevent picking up items
     * through solid walls/obstructions.
     */
    public static boolean hasLineOfSightToDrop(CompanionEntity companion, ItemEntity item) {
        if (companion == null || item == null || !(companion.level() instanceof ServerLevel level)) return false;
        Vec3 eyePos = companion.getEyePosition();
        Vec3 dropPos = item.position().add(0, 0.25, 0);
        BlockHitResult hit = level.clip(new ClipContext(
                eyePos, dropPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                companion
        ));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        BlockPos hitPos = hit.getBlockPos();
        return hitPos.equals(item.blockPosition()) || hitPos.equals(item.blockPosition().below());
    }

    /** Vanilla-like pickup reach: player pickup box is the bounding box inflated by (1.5, 1.2, 1.5) with line-of-sight check. */
    public static boolean isWithinPickupReach(CompanionEntity companion, ItemEntity item) {
        if (companion == null || item == null) return false;
        boolean inRange = false;
        AABB reach = companion.getBoundingBox().inflate(1.5, 1.2, 1.5);
        if (reach.intersects(item.getBoundingBox())) {
            inRange = true;
        } else {
            double dx = item.getX() - companion.getX();
            double dz = item.getZ() - companion.getZ();
            double dy = Math.abs(item.getY() - companion.getY());
            double pick = Math.max(2.2, MovementPolicy.pickupWalkDistance());
            inRange = (dx * dx + dz * dz) <= pick * pick && dy <= 2.5;
        }
        if (!inRange) return false;
        return hasLineOfSightToDrop(companion, item);
    }

    /**
     * Breaks (with real, timed breaking) natural blocks that bury the drop or block the way.
     * Returns true while a block is being broken this tick.
     */
    private boolean clearObstaclesTowardsDrop(CompanionEntity companion, ServerLevel level, ItemEntity activeTarget) {
        if (companion == null || level == null || activeTarget == null || !activeTarget.isAlive()) return false;
        if (!companion.getSettings().isAllowBlockBreaking()) return false;

        if (breakingPos == null) {
            BlockPos candidate = null;
            BlockPos itemPos = activeTarget.blockPosition();
            BlockState itemBlock = level.getBlockState(itemPos);
            if (!itemBlock.isAir() && !itemBlock.getCollisionShape(level, itemPos).isEmpty()
                    && TerrainPolicy.mayBreakImplicitly(level, itemPos, itemBlock, companion.getTemporaryBlocks())) {
                candidate = itemPos;
            }
            double distSq = companion.distanceToSqr(activeTarget);
            if (candidate == null && distSq > 1.0) {
                Vec3 dir = activeTarget.position().subtract(companion.position()).normalize();
                outer:
                for (double step = 0.8; step <= Math.min(2.5, Math.sqrt(distSq)); step += 0.8) {
                    BlockPos checkPos = BlockPos.containing(companion.getX() + dir.x * step, companion.getY() + 0.1, companion.getZ() + dir.z * step);
                    for (BlockPos p : new BlockPos[]{checkPos, checkPos.above()}) {
                        BlockState bs = level.getBlockState(p);
                        if (bs.isAir() || bs.getCollisionShape(level, p).isEmpty()) continue;
                        if (TerrainPolicy.mayBreakImplicitly(level, p, bs, companion.getTemporaryBlocks())) {
                            candidate = p;
                        }
                        break outer; // first solid block decides (never dig through a player build)
                    }
                }
            }
            if (candidate == null) return false;
            breakingPos = candidate;
            breakingTicks = 0;
        }

        BlockState state = level.getBlockState(breakingPos);
        if (state.isAir() || breakingPos.distToCenterSqr(companion.position()) > 25.0) {
            BlockBreakHelper.cancelBreaking(companion, level, breakingPos);
            breakingPos = null;
            return false;
        }
        com.aicompanion.ai.equipment.ToolSelectionPolicy.ensureToolForBlock(companion, state);
        int required = BlockBreakHelper.calculateBreakTicks(companion, breakingPos, state);
        if (required <= 0) {
            breakingPos = null;
            return false;
        }
        breakingTicks++;
        companion.getLookControl().setLookAt(breakingPos.getX() + 0.5, breakingPos.getY() + 0.5, breakingPos.getZ() + 0.5);
        if (BlockBreakHelper.tickBreaking(companion, level, breakingPos, breakingTicks, required)) {
            BlockBreakHelper.finishBreaking(companion, level, breakingPos);
            breakingPos = null;
            breakingTicks = 0;
        }
        return true;
    }

    /**
     * Primary tick method for collecting drops.
     */
    public DropCollectionResult tickCollection(CompanionEntity companion, BlockPos breakPos, Predicate<ItemStack> filter) {
        if (companion == null || !(companion.level() instanceof ServerLevel level)) {
            reset();
            return DropCollectionResult.COMPLETE;
        }

        // 1. Grace period (allow entity to spawn and settle)
        if (graceTicks > 0) {
            graceTicks--;
            return DropCollectionResult.MOVING_TO_DROP;
        }

        // 2. Fetch or acquire active target
        ItemEntity activeTarget = getCurrentDropEntity(level);
        if (activeTarget == null) {
            currentDropUUID = null;
            Iterator<UUID> it = trackedDropUUIDs.iterator();
            while (it.hasNext()) {
                UUID nextId = it.next();
                it.remove();
                if (abandonedDrops.contains(nextId)) continue;
                Entity ent = level.getEntity(nextId);
                if (ent instanceof ItemEntity item && item.isAlive()) {
                    currentDropUUID = nextId;
                    activeTarget = item;
                    break;
                }
            }

            if (activeTarget == null) {
                BlockPos scanOrigin = (breakPos != null) ? breakPos : companion.blockPosition();
                List<ItemEntity> found = scanDrops(companion, scanOrigin, filter);
                if (!found.isEmpty()) {
                    activeTarget = found.get(0);
                    currentDropUUID = activeTarget.getUUID();
                    for (int i = 1; i < found.size(); i++) {
                        trackedDropUUIDs.add(found.get(i).getUUID());
                    }
                }
            }

            if (activeTarget == null) {
                reset();
                return DropCollectionResult.NO_DROPS_FOUND;
            }

            double dist = Math.sqrt(companion.distanceToSqr(activeTarget));
            this.maxAllowedTicks = (int) Math.min(400, 100 + (dist * 25));
            this.collectionTicks = 0;
            this.unreachableRetries = 0;
        }

        // 3. Timeout handling: limited retries, then blacklist this drop permanently for the task
        collectionTicks++;
        if (collectionTicks > maxAllowedTicks) {
            unreachableRetries++;
            if (unreachableRetries >= 3) {
                abandonedDrops.add(activeTarget.getUUID());
                currentDropUUID = null;
                if (breakingPos != null) {
                    BlockBreakHelper.cancelBreaking(companion, level, breakingPos);
                    breakingPos = null;
                }
                if (!trackedDropUUIDs.isEmpty()) {
                    return DropCollectionResult.TIMEOUT_RETRY;
                }
                reset();
                return DropCollectionResult.DROP_UNREACHABLE;
            } else {
                collectionTicks = 0;
                companion.getNavManager().stop();
                companion.getNavManager().navigateTo(new GoalNear(activeTarget.blockPosition(), 1), MovementPolicy.sprint());
                return DropCollectionResult.TIMEOUT_RETRY;
            }
        }

        // 4. Physical pickup only when really within reach (walk there first)
        if (isWithinPickupReach(companion, activeTarget)) {
            if (activeTarget.hasPickUpDelay() && collectionTicks < 40) {
                return DropCollectionResult.MOVING_TO_DROP; // vanilla pickup delay
            }
            CompanionInventoryHandler inv = companion.getInventoryHandler();
            ItemStack stack = activeTarget.getItem();
            int originalCount = stack.getCount();
            ItemStack remaining = inv.addItem(stack);
            if (!remaining.isEmpty()) {
                int freed = inv.freeSpaceForMission(companion, filter);
                if (freed > 0) {
                    remaining = inv.addItem(remaining);
                }
            }
            int accepted = originalCount - remaining.getCount();

            if (accepted > 0) {
                companion.take(activeTarget, accepted);
                companion.playSound(SoundEvents.ITEM_PICKUP, 0.45f, 1.2f);
            }

            if (remaining.isEmpty()) {
                activeTarget.discard();
                currentDropUUID = null;
                collectionTicks = 0;
                unreachableRetries = 0;
                if (!trackedDropUUIDs.isEmpty()) {
                    return DropCollectionResult.PICKED_UP;
                }
                reset();
                return DropCollectionResult.COMPLETE;
            } else {
                activeTarget.setItem(remaining);
                currentDropUUID = null;
                return DropCollectionResult.INVENTORY_FULL;
            }
        }

        // 5. Walk to the item; dig natural obstacles with timed breaking if needed
        companion.getLookControl().setLookAt(activeTarget, 30.0f, 30.0f);
        boolean blocked = companion.getNavManager().isStuck() || collectionTicks > 60;
        if (blocked && clearObstaclesTowardsDrop(companion, level, activeTarget)) {
            return DropCollectionResult.MOVING_TO_DROP;
        }
        if (companion.getNavManager().isDone() || collectionTicks % 10 == 0) {
            companion.getNavManager().navigateTo(new GoalNear(activeTarget.blockPosition(), 1), MovementPolicy.sprint());
        }
        return DropCollectionResult.MOVING_TO_DROP;
    }

    public String getStatusDescription() {
        if (currentDropUUID != null) {
            return "Recogiendo drop del suelo";
        }
        return "Verificando drops";
    }
}
