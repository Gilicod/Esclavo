package com.aicompanion.ai.perception;

import com.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise Sensory Perception Daemon for Esclavo (Pilar 4).
 * Runs on a 10-tick interleaved schedule, projecting a spherical AABB (radius 15m)
 * to detect hostile entities, swelling creepers, and imminent projectiles.
 */
public class CompanionPerception {

    private final CompanionEntity companion;
    private final List<LivingEntity> detectedThreats = new ArrayList<>();
    private LivingEntity primaryThreat = null;
    private Vec3 threatCenter = Vec3.ZERO;
    private boolean imminentProjectile = false;
    private long lastScanTick = -1;

    public CompanionPerception(CompanionEntity companion) {
        this.companion = companion;
    }

    /**
     * Executes the 15-meter spherical environment scan.
     */
    public void scan(ServerLevel level) {
        if (level == null || companion == null || !companion.isAlive()) return;
        this.lastScanTick = level.getGameTime();

        detectedThreats.clear();
        primaryThreat = null;
        imminentProjectile = false;

        Vec3 companionPos = companion.position();
        double radius = 15.0;
        AABB scanBox = companion.getBoundingBox().inflate(radius);

        // 1. Scan for hostile living entities in radius 15
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(
                LivingEntity.class,
                scanBox,
                e -> RiskEvaluator.isHostileThreat(e, companion)
        );

        double totalX = 0, totalY = 0, totalZ = 0;
        LivingEntity highestPriorityThreat = null;
        double highestPriorityScore = -1.0;

        for (LivingEntity threat : nearbyEntities) {
            double distSq = companionPos.distanceToSqr(threat.position());
            if (distSq > radius * radius) continue; // Enforce strict sphere

            detectedThreats.add(threat);
            totalX += threat.getX();
            totalY += threat.getY();
            totalZ += threat.getZ();

            // Threat scoring:
            // Swelling Creeper = Emergency priority 1000
            // Close distance = Higher score
            // Targeting companion or owner = Priority boost
            double score = (radius * radius) - distSq;
            if (threat instanceof Creeper creeper && creeper.getSwellDir() > 0) {
                score += 1000.0;
            }
            if (threat instanceof net.minecraft.world.entity.Mob mob) {
                if (mob.getTarget() == companion || (companion.getOwnerPlayer() != null && mob.getTarget() == companion.getOwnerPlayer())) {
                    score += 50.0;
                }
            }

            if (score > highestPriorityScore) {
                highestPriorityScore = score;
                highestPriorityThreat = threat;
            }
        }

        this.primaryThreat = highestPriorityThreat;
        if (!detectedThreats.isEmpty()) {
            this.threatCenter = new Vec3(totalX / detectedThreats.size(), totalY / detectedThreats.size(), totalZ / detectedThreats.size());
        } else {
            this.threatCenter = companionPos;
        }

        // 2. Scan for incoming projectiles in radius 12
        List<Projectile> projectiles = level.getEntitiesOfClass(
                Projectile.class,
                companion.getBoundingBox().inflate(12.0),
                p -> p.isAlive() && p.getOwner() != companion
        );

        for (Projectile proj : projectiles) {
            Vec3 projMotion = proj.getDeltaMovement();
            if (projMotion.lengthSqr() > 0.05) {
                Vec3 toCompanion = companionPos.subtract(proj.position());
                // If projectile is moving towards companion
                if (projMotion.normalize().dot(toCompanion.normalize()) > 0.65 && toCompanion.lengthSqr() < 100.0) {
                    this.imminentProjectile = true;
                    if (this.primaryThreat == null) {
                        if (proj.getOwner() instanceof LivingEntity livingOwner && livingOwner.isAlive() && RiskEvaluator.isHostileThreat(livingOwner, companion)) {
                            this.primaryThreat = livingOwner;
                            this.threatCenter = livingOwner.position();
                        } else {
                            this.threatCenter = proj.position();
                        }
                    }
                    break;
                }
            }
        }

        // 3. Passive POI Environmental Scanner (Pilar 5)
        if (companion.getMemory() != null) {
            var worldModel = companion.getMemory().getWorldModel();
            worldModel.cleanExpiredPOIs(level.getGameTime());

            BlockPos origin = companion.blockPosition();
            int radiusBlocks = 8;
            for (int dx = -radiusBlocks; dx <= radiusBlocks; dx += 2) {
                for (int dz = -radiusBlocks; dz <= radiusBlocks; dz += 2) {
                    for (int dy = -2; dy <= 4; dy++) {
                        BlockPos checkPos = origin.offset(dx, dy, dz);
                        if (!level.isLoaded(checkPos)) continue;
                        var blockState = level.getBlockState(checkPos);
                        if (blockState.isAir()) continue;

                        var block = blockState.getBlock();
                        if (block == net.minecraft.world.level.block.Blocks.CRAFTING_TABLE ||
                            block == net.minecraft.world.level.block.Blocks.FURNACE ||
                            block == net.minecraft.world.level.block.Blocks.BLAST_FURNACE ||
                            block == net.minecraft.world.level.block.Blocks.SMOKER ||
                            block == net.minecraft.world.level.block.Blocks.CHEST ||
                            block == net.minecraft.world.level.block.Blocks.BARREL ||
                            block == net.minecraft.world.level.block.Blocks.TRAPPED_CHEST) {
                            worldModel.rememberPOI(checkPos, block, level.getGameTime());
                        }
                    }
                }
            }
        }
    }

    public List<LivingEntity> getDetectedThreats() {
        return detectedThreats;
    }

    public void recordDirectAttacker(LivingEntity attacker) {
        if (attacker != null && attacker.isAlive()) {
            this.primaryThreat = attacker;
            if (!this.detectedThreats.contains(attacker)) {
                this.detectedThreats.add(attacker);
            }
            this.threatCenter = attacker.position();
        }
    }

    public LivingEntity getPrimaryThreat() {
        return primaryThreat;
    }

    public Vec3 getThreatCenter() {
        return threatCenter;
    }

    public boolean hasImminentProjectile() {
        return imminentProjectile;
    }

    public int getThreatCount() {
        return detectedThreats.size();
    }

    public long getLastScanTick() {
        return lastScanTick;
    }

    public String toCompactSummary() {
        BlockPos pos = companion.blockPosition();
        return String.format(
                "{\"pos\":[%d,%d,%d],\"dim\":\"%s\",\"hp\":%.1f,\"state\":\"%s\",\"lvl\":%d,\"threats\":%d}",
                pos.getX(), pos.getY(), pos.getZ(),
                companion.level().dimension().location().toString(),
                companion.getHealth(),
                companion.getCurrentState().name(),
                companion.getCompanionLevel(),
                detectedThreats.size()
        );
    }
}
