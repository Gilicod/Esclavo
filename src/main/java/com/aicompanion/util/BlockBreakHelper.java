package com.aicompanion.util;

import com.aicompanion.debug.EsclavoTrace;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.inventory.EquipmentManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams.Builder;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;

public class BlockBreakHelper {
   public static int calculateBreakTicks(CompanionEntity var0, BlockPos var1, BlockState var2) {
      if (var2.isAir()) {
         return 1;
      }

      float var3 = var2.getDestroySpeed(var0.level(), var1);
      if (var3 < 0.0F) {
         return -1;
      }

      if (var3 == 0.0F) {
         return 1;
      }

      ItemStack var4 = var0.getMainHandItem();
      boolean var5 = var4.isCorrectToolForDrops(var2);
      boolean var6 = var5 || !var2.requiresCorrectToolForDrops();
      float var7 = var4.getDestroySpeed(var2);
      if (var5) {
         int var8 = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, var4);
         if (var8 > 0 && var7 > 1.0F) {
            var7 += var8 * var8 + 1;
         }
      }

      if (var0.hasEffect(MobEffects.DIG_SPEED)) {
         var7 *= 1.0F + (var0.getEffect(MobEffects.DIG_SPEED).getAmplifier() + 1) * 0.2F;
      }

      if (var0.hasEffect(MobEffects.DIG_SLOWDOWN)) {
         float var10 = switch (var0.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
         var7 *= var10;
      }

      float var11 = var7 / var3;
      if (var6) {
         var11 /= 30.0F;
      } else {
         var11 /= 100.0F;
      }

      if (var11 <= 0.0F) {
         return 200;
      }

      int var9 = (int)Math.ceil(1.0F / var11);
      return Math.max(1, Math.min(var9, 500));
   }

   public static boolean tickBreaking(CompanionEntity var0, ServerLevel var1, BlockPos var2, int var3, int var4) {
      if (var2 != null && var1 != null && var0 != null) {
         double dx = var2.getX() + 0.5 - var0.getX();
         double dy = var2.getY() + 0.5 - var0.getEyeY();
         double dz = var2.getZ() + 0.5 - var0.getZ();
         double distXZ = Math.sqrt(dx * dx + dz * dz);
         float yaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
         float pitch = (float)(-(Mth.atan2(dy, distXZ) * (180.0 / Math.PI)));
         var0.setYRot(yaw);
         var0.setXRot(pitch);
         var0.yBodyRot = yaw;
         var0.yHeadRot = yaw;
         var0.getLookControl().setLookAt(var2.getX() + 0.5, var2.getY() + 0.5, var2.getZ() + 0.5);

         BlockState state = var1.getBlockState(var2);
         if (var3 % 4 == 0) {
            var0.swing(InteractionHand.MAIN_HAND, true);
            if (!state.isAir()) {
               SoundType soundType = state.getSoundType();
               var1.playSound(null, var2, soundType.getHitSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
               var1.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                  var2.getX() + 0.5, var2.getY() + 0.5, var2.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.05);
            }
         }

         int var5 = (int)Math.min(9.0F, Math.max(0.0F, (float)var3 / Math.max(1, var4) * 10.0F));
         var1.destroyBlockProgress(var0.getId(), var2, var5);
         return var3 >= var4;
      } else {
         return false;
      }
   }

   public static void cancelBreaking(CompanionEntity var0, ServerLevel var1, BlockPos var2) {
      if (var0 != null && var1 != null && var2 != null) {
         var1.destroyBlockProgress(var0.getId(), var2, -1);
      }
   }

   public static boolean finishBreaking(CompanionEntity var0, ServerLevel var1, BlockPos var2) {
      if (var0 != null && var1 != null && var2 != null) {
         var1.destroyBlockProgress(var0.getId(), var2, -1);
         BlockState var3 = var1.getBlockState(var2);
         if (var3.isAir()) {
            return false;
         }

         BreakEvent var4 = new BreakEvent(var1, var2, var3, var0.getOwnerPlayer());
         MinecraftForge.EVENT_BUS.post(var4);
         if (var4.isCanceled()) {
            EsclavoTrace.log("PICA", var0, "CANCELADO por otro mod/proteccion: " + var3.getBlock() + " en " + EsclavoTrace.fmt(var2));
            return false;
         }

         ItemStack var5 = var0.getMainHandItem();
         if (var5.isDamageableItem()) {
            var5.hurtAndBreak(1, var0, var0x -> var0x.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            EquipmentManager.checkAndPreserveEndangeredTools(var0);
         }

         BlockEntity var6 = var3.hasBlockEntity() ? var1.getBlockEntity(var2) : null;
         Builder var7 = new Builder(var1)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(var2))
            .withParameter(LootContextParams.BLOCK_STATE, var3)
            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, var6)
            .withOptionalParameter(LootContextParams.THIS_ENTITY, var0)
            .withParameter(LootContextParams.TOOL, var5);

         for (ItemStack var9 : var3.getDrops(var7)) {
            Block.popResource(var1, var2, var9);
         }

         boolean var10 = var1.destroyBlock(var2, false, var0);
         EsclavoTrace.log(
            "PICA",
            var0,
            var3.getBlock() + " en " + EsclavoTrace.fmt(var2) + " roto=" + var10 + " herramienta=" + (var5.isEmpty() ? "mano" : var5.getItem().toString())
         );
         if (var10) {
            var1.levelEvent(2001, var2, Block.getId(var3));
            collectDropsPhysically(var0, var2, 0.0);
         }

         return var10;
      } else {
         return false;
      }
   }

   public static int collectDropsPhysically(CompanionEntity var0, BlockPos var1, double var2) {
      if (var0 != null && !var0.level().isClientSide() && var0.level() instanceof ServerLevel var4) {
         AABB companionBox = var0.getBoundingBox().inflate(1.5, 1.0, 1.5);
         AABB searchBox = companionBox;
         if (var1 != null) {
            double distSq = var0.distanceToSqr(Vec3.atCenterOf(var1));
            if (distSq <= 36.0) {
               AABB blockBox = new AABB(var1).inflate(2.0, 2.0, 2.0);
               searchBox = companionBox.minmax(blockBox);
            }
         }
         List<ItemEntity> var6 = var4.getEntitiesOfClass(ItemEntity.class, searchBox);
         int var7 = 0;
         CompanionInventoryHandler var8 = var0.getInventoryHandler();

         for (ItemEntity var10 : var6) {
            if (var10.isAlive() && !var10.getItem().isEmpty() && !var10.hasPickUpDelay()) {
               if (var0.getCompanionBrain() != null && var0.getCompanionBrain().isDiscardedItem(var10)) {
                  continue;
               }
               ItemStack var11 = var10.getItem();
               int var12 = var11.getCount();
               ItemStack var13 = var8.addItem(var11);
               if (!var13.isEmpty() && com.aicompanion.AICompanionConfig.COMMON.allowAutoDiscard.get()) {
                  int freed = var8.freeSpaceForMission(var0, null);
                  if (freed > 0) {
                     var13 = var8.addItem(var13);
                  }
               }
               int var14 = var12 - var13.getCount();
               if (var14 > 0) {
                  var7 += var14;
                  var0.take(var10, var14);
                  var0.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.2F);
               }

               if (var13.isEmpty()) {
                  var10.discard();
               } else {
                  var10.setItem(var13);
               }
            }
         }

         return var7;
      } else {
         return 0;
      }
   }
}
