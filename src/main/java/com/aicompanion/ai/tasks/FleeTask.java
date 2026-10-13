package com.aicompanion.ai.tasks;

import com.aicompanion.AICompanionMod;
import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.nav.InteractionPositionPlanner;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.internal.baritone.goals.GoalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

/**
 * Autonomous Emergency Tactical Escape Task for Esclavo (Pilar 4).
 * Injected when risk is critical and Esclavo cannot fight (low HP, unarmed, or overwhelming swarm).
 * Computes a safe escape vector pointing away from the threat center, routing with Baritone at 1.35x speed.
 */
public class FleeTask extends CompanionTask {

    private Vec3 threatCenter;
    private double fleeDistance;
    private BlockPos escapeTarget = null;
    private int fleeTicks = 0;
    private int repathCooldown = 0;

    public FleeTask(CompanionEntity companion, Vec3 threatCenter, double fleeDistance) {
        super(companion);
        this.threatCenter = threatCenter != null ? threatCenter : companion.position();
        this.fleeDistance = Math.max(12.0, fleeDistance);
        this.stage = "FLEEING";
        this.substate = "Huyendo de la zona de peligro...";
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.NAVIGATING);
        calculateAndNavigate();
        AICompanionMod.LOGGER.info("[ESCLAVO-SURVIVAL] FleeTask initiated: Evacuating away from {}", threatCenter);
    }

    private void calculateAndNavigate() {
        Vec3 companionPos = companion.position();
        Vec3 away = companionPos.subtract(threatCenter);
        if (away.lengthSqr() < 0.05) {
            away = new Vec3(companion.getRandom().nextDouble() - 0.5, 0, companion.getRandom().nextDouble() - 0.5);
        }
        Vec3 dir = away.normalize();

        BlockPos targetPos = companion.blockPosition().offset(
                (int) (dir.x * fleeDistance),
                0,
                (int) (dir.z * fleeDistance)
        );

        if (companion.level() instanceof ServerLevel sl) {
            if (sl.dimensionType().hasCeiling()) {
                // In Nether/roofed dimensions, find solid floor near companion's current Y level instead of bedrock roof
                int startY = companion.blockPosition().getY();
                int bestY = startY;
                for (int dy = 0; dy <= 8; dy++) {
                    BlockPos pUp = new BlockPos(targetPos.getX(), startY + dy, targetPos.getZ());
                    if (isSafeStandableFloor(sl, pUp)) { bestY = startY + dy; break; }
                    BlockPos pDown = new BlockPos(targetPos.getX(), startY - dy, targetPos.getZ());
                    if (isSafeStandableFloor(sl, pDown)) { bestY = startY - dy; break; }
                }
                targetPos = new BlockPos(targetPos.getX(), bestY, targetPos.getZ());
            } else {
                int surfaceY = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, targetPos.getX(), targetPos.getZ());
                targetPos = new BlockPos(targetPos.getX(), surfaceY, targetPos.getZ());
            }
        }

        this.escapeTarget = targetPos;
        companion.getNavManager().navigateTo(new GoalBlock(escapeTarget), 1.35);
        this.repathCooldown = 20;
    }

    @Override
    public void tick() {
        if (paused) return;
        fleeTicks++;

        double distToThreatSq = companion.position().distanceToSqr(threatCenter);

        // Check if safely beyond threat radius
        if (distToThreatSq >= (fleeDistance * fleeDistance)) {
            // Confirm no hostiles within immediate 8-block proximity
            int nearbyMonsters = companion.level().getEntitiesOfClass(
                    Monster.class,
                    companion.getBoundingBox().inflate(8.0),
                    m -> m.isAlive()
            ).size();

            if (nearbyMonsters == 0) {
                complete = true;
                stage = "SAFE";
                substate = "Zona segura alcanzada";
                companion.getNavManager().stop();
                AICompanionMod.LOGGER.info("[ESCLAVO-SURVIVAL] FleeTask completed: Esclavo has escaped to safety!");
                return;
            }
        }

        // Timeout fallback
        if (fleeTicks >= 160) { // 8 seconds of fleeing
            complete = true;
            companion.getNavManager().stop();
            return;
        }

        // Periodic repath if navigation finished or blocked
        if (--repathCooldown <= 0 || companion.getNavManager().isDone()) {
            calculateAndNavigate();
        }
    }

    @Override
    public void stop() {
        companion.getNavManager().stop();
    }

    @Override
    public String getDescription() {
        return "Retirada táctica a zona segura";
    }

    @Override
    public String getProgress() {
        double dist = Math.sqrt(companion.position().distanceToSqr(threatCenter));
        return String.format("%.0f/%.0fm (%s)", dist, fleeDistance, stage);
    }

    @Override
    public String getTaskType() {
        return "FLEE";
    }

    public Vec3 getThreatCenter() {
        return threatCenter;
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("FleeTicks", fleeTicks);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        this.fleeTicks = tag.getInt("FleeTicks");
    }

    private static boolean isSafeStandableFloor(ServerLevel sl, BlockPos pos) {
        if (!sl.isLoaded(pos) || !sl.isLoaded(pos.below())) return false;
        var state = sl.getBlockState(pos);
        var stateAbove = sl.getBlockState(pos.above());
        var floorState = sl.getBlockState(pos.below());

        boolean footPassable = state.isAir() || !state.blocksMotion();
        boolean headPassable = stateAbove.isAir() || !stateAbove.blocksMotion();
        boolean floorSolid = !floorState.isAir() && floorState.isSolid() && !floorState.is(net.minecraft.world.level.block.Blocks.LAVA) && !floorState.is(net.minecraft.world.level.block.Blocks.FIRE);

        return footPassable && headPassable && floorSolid;
    }
}
