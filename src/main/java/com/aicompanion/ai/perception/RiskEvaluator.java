package com.aicompanion.ai.perception;

import com.aicompanion.entity.CompanionEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Event-Driven Risk Evaluator for Esclavo (Pilar 4).
 * Determines survival risk level based on live perception data:
 * imminent projectiles, swelling creepers, flanking enemies, and HP status.
 */
public class RiskEvaluator {

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public static RiskLevel evaluate(CompanionEntity companion, CompanionPerception perception) {
        if (companion == null || !companion.isAlive()) return RiskLevel.LOW;

        float health = companion.getHealth();
        float maxHealth = companion.getMaxHealth();

        // 1. Environmental immediate lethality
        if (companion.isInLava() || (companion.isOnFire() && !companion.fireImmune() && health <= 8.0F)) {
            return RiskLevel.CRITICAL;
        }

        // 2. Imminent projectile danger (only critical if launched by an active hostile threat or at low health)
        if (perception != null && perception.hasImminentProjectile() && (perception.getPrimaryThreat() != null || health <= 8.0F)) {
            return RiskLevel.CRITICAL;
        }

        // 3. Swelling Creeper danger (zero tolerance)
        if (perception != null) {
            LivingEntity primary = perception.getPrimaryThreat();
            if (primary instanceof Creeper creeper && creeper.getSwellDir() > 0) {
                return RiskLevel.CRITICAL;
            }

            // Close proximity to Creeper (under 5.0m)
            if (primary instanceof Creeper && companion.distanceToSqr(primary) <= 25.0) {
                return RiskLevel.CRITICAL;
            }

            int threatCount = perception.getThreatCount();

            // 4. Critical Health thresholds
            if (health <= 6.0F && threatCount > 0) {
                return RiskLevel.CRITICAL;
            }

            // 5. Direct close-quarters attack (enemy within 4 blocks)
            if (primary != null && companion.distanceToSqr(primary) <= 16.0) {
                return RiskLevel.CRITICAL;
            }

            // 6. Overwhelming enemy swarm
            if (threatCount >= 3) {
                return RiskLevel.CRITICAL;
            }

            // High risk conditions
            if (threatCount >= 2 || (threatCount >= 1 && health <= (maxHealth * 0.5f))) {
                return RiskLevel.HIGH;
            }

            // Medium risk
            if (threatCount >= 1) {
                return RiskLevel.MEDIUM;
            }
        }

        return RiskLevel.LOW;
    }

    public static RiskLevel evaluate(CompanionEntity companion) {
        if (companion != null && companion.getCompanionBrain() != null) {
            return evaluate(companion, companion.getCompanionBrain().getPerception());
        }
        return companion != null && companion.getHealth() <= 6.0F ? RiskLevel.CRITICAL : RiskLevel.LOW;
    }

    /**
     * Determines whether an entity represents a hostile threat to the companion.
     * Enforces strict friendly protection: never attacks owner, teammate companions,
     * player pets, peaceful villagers or passive animals.
     */
    public static boolean isHostileThreat(net.minecraft.world.entity.LivingEntity entity, CompanionEntity companion) {
        if (entity == null || !entity.isAlive() || entity.isRemoved() || entity == companion) {
            return false;
        }
        // Protect owner player
        if (companion != null && companion.getOwnerUUID() != null && entity.getUUID().equals(companion.getOwnerUUID())) {
            return false;
        }
        // Protect other players (neutral server policy)
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            return false;
        }
        // Protect other companions
        if (entity instanceof CompanionEntity) {
            return false;
        }
        // Protect tamed pets belonging to the owner
        if (entity instanceof net.minecraft.world.entity.TamableAnimal tamed && tamed.getOwnerUUID() != null) {
            if (companion != null && companion.getOwnerUUID() != null && tamed.getOwnerUUID().equals(companion.getOwnerUUID())) {
                return false;
            }
        }
        // Protect villagers
        if (entity instanceof net.minecraft.world.entity.npc.Villager) {
            return false;
        }
        // Protect iron golems unless they are attacking the companion
        if (entity instanceof net.minecraft.world.entity.animal.IronGolem golem) {
            return companion != null && golem.getTarget() == companion;
        }
        // Protect passive animals (cows, pigs, sheep, chickens, horses, etc.)
        if (entity instanceof net.minecraft.world.entity.animal.Animal && !(entity instanceof net.minecraft.world.entity.monster.Enemy)) {
            if (companion != null && entity instanceof net.minecraft.world.entity.NeutralMob neutralMob) {
                return neutralMob.isAngryAt(companion);
            }
            return false;
        }

        // Neutral mobs check: Zombified Piglin, Enderman, Iron Golems, Wolves, Bees, Polar Bears, etc.
        if (entity instanceof net.minecraft.world.entity.NeutralMob neutralMob) {
            if (companion != null && companion.getLastHurtByMob() == entity) {
                return true;
            }
            if (neutralMob.isAngryAt(companion)) {
                return true;
            }
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                LivingEntity target = mob.getTarget();
                if (companion != null && (target == companion || (companion.getOwnerPlayer() != null && target == companion.getOwnerPlayer()))) {
                    return true;
                }
            }
            return false; // Peaceful neutral mob!
        }

        // Piglin check: respect gold armor and non-aggressive states
        if (entity instanceof net.minecraft.world.entity.monster.piglin.Piglin piglin) {
            if (companion != null && companion.getLastHurtByMob() == piglin) {
                return true;
            }
            LivingEntity target = piglin.getTarget();
            if (companion != null && (target == companion || (companion.getOwnerPlayer() != null && target == companion.getOwnerPlayer()))) {
                return true;
            }
            if (piglin.isConverting() || !piglin.isAggressive()) {
                return false;
            }
            if (companion != null && isWearingGoldArmor(companion)) {
                return false;
            }
            if (target == null) {
                return false;
            }
        }

        // Hostile monsters and enemies
        if (entity instanceof net.minecraft.world.entity.monster.Enemy || entity instanceof net.minecraft.world.entity.monster.Monster) {
            return true;
        }
        // Any mob actively targeting companion or owner
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            LivingEntity target = mob.getTarget();
            if (companion != null) {
                if (target == companion || (companion.getOwnerPlayer() != null && target == companion.getOwnerPlayer())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isWearingGoldArmor(CompanionEntity companion) {
        if (companion == null) return false;
        for (net.minecraft.world.item.ItemStack armor : companion.getArmorSlots()) {
            if (!armor.isEmpty() && armor.getItem() instanceof net.minecraft.world.item.ArmorItem ai) {
                if (ai.getMaterial() == net.minecraft.world.item.ArmorMaterials.GOLD) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Quick filter for inherently hostile monsters (excluding passive animals, players, and villagers).
     */
    public static boolean isInherentlyHostile(net.minecraft.world.entity.LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) return false;
        if (entity instanceof net.minecraft.world.entity.player.Player) return false;
        if (entity instanceof CompanionEntity) return false;
        if (entity instanceof net.minecraft.world.entity.npc.Villager) return false;
        if (entity instanceof net.minecraft.world.entity.animal.Animal && !(entity instanceof net.minecraft.world.entity.monster.Enemy)) return false;
        if (entity instanceof net.minecraft.world.entity.NeutralMob) return false;
        if (entity instanceof net.minecraft.world.entity.monster.piglin.Piglin) return false;
        return entity instanceof net.minecraft.world.entity.monster.Enemy || entity instanceof net.minecraft.world.entity.monster.Monster;
    }

    /**
     * Evaluates whether a threat vastly overpowers the companion's current gear and health.
     * Prevents suicidal melee engagements against modded bosses or high-damage mobs
     * when equipped with only wooden/stone tools and low armor.
     */
    public static boolean isOverpoweringThreat(net.minecraft.world.entity.LivingEntity threat, CompanionEntity companion) {
        if (threat == null || companion == null) return false;

        float threatMaxHp = threat.getMaxHealth();
        // 1. High HP threat (bosses / mini-bosses >= 50 max HP like Maze Mother, Wither, etc.)
        if (threatMaxHp >= 50.0F) {
            int armorValue = companion.getArmorValue();
            var mainHand = companion.getMainHandItem();
            boolean lowTierWeapon = mainHand.isEmpty() ||
                    mainHand.is(net.minecraft.world.item.Items.WOODEN_SWORD) ||
                    mainHand.is(net.minecraft.world.item.Items.STONE_SWORD) ||
                    !(mainHand.getItem() instanceof net.minecraft.world.item.TieredItem);

            // If companion has less than half iron armor or only basic wood/stone weapon against a boss:
            if (lowTierWeapon || armorValue < 10) {
                return true;
            }
        }

        // 2. High attack damage attribute
        if (threat instanceof net.minecraft.world.entity.Mob mob) {
            var attr = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (attr != null) {
                double atk = attr.getValue();
                // Lethal boss/mini-boss attacks (Warden, Iron Golem, Ravager >= 14.0 dmg):
                if (atk >= 14.0 && (companion.getArmorValue() < 15 || companion.getHealth() <= 12.0F)) {
                    return true;
                }
                // High damage enemies (7.0 - 13.0 dmg, e.g. Vindicator, Piglin Brute, Piglin):
                // Only overpowering if companion is at critical low HP (< 35% health or <= 7 HP)
                if (atk >= 7.0) {
                    float hpRatio = companion.getHealth() / Math.max(1.0F, companion.getMaxHealth());
                    if (hpRatio <= 0.35F || companion.getHealth() <= 7.0F) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
