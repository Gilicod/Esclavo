package com.aicompanion.util;

import com.aicompanion.AICompanionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class TerrainPolicy {
   private TerrainPolicy() {
   }

   public static boolean protectPlayerBuilds() {
      try {
         if (AICompanionConfig.COMMON != null && AICompanionConfig.COMMON.protectPlayerBuilds != null) {
            return (Boolean)AICompanionConfig.COMMON.protectPlayerBuilds.get();
         }
      } catch (Exception var1) {
      }

      return true;
   }

   public static boolean isNaturalTerrain(BlockState var0) {
      if (var0 != null && !var0.isAir()) {
         return !var0.is(Blocks.BEDROCK) && !var0.is(Blocks.BARRIER) && !var0.hasBlockEntity()
            ? var0.is(BlockTags.BASE_STONE_OVERWORLD)
               || var0.is(BlockTags.BASE_STONE_NETHER)
               || var0.is(BlockTags.DIRT)
               || var0.is(BlockTags.SAND)
               || var0.is(BlockTags.LEAVES)
               || var0.is(BlockTags.SNOW)
               || var0.is(Blocks.TERRACOTTA)
               || var0.is(net.minecraftforge.common.Tags.Blocks.ORES)
               || var0.is(Blocks.GRAVEL)
               || var0.is(Blocks.CLAY)
               || var0.is(Blocks.END_STONE)
               || var0.is(Blocks.SOUL_SAND)
               || var0.is(Blocks.SOUL_SOIL)
               || var0.is(Blocks.MAGMA_BLOCK)
               || var0.is(Blocks.CALCITE)
               || var0.is(Blocks.DRIPSTONE_BLOCK)
               || var0.is(Blocks.POINTED_DRIPSTONE)
               || var0.is(Blocks.SMOOTH_BASALT)
               || var0.is(Blocks.AMETHYST_BLOCK)
               || var0.is(Blocks.MUD)
               || var0.is(Blocks.COBWEB)
               || var0.is(Blocks.VINE)
               || var0.is(Blocks.SUGAR_CANE)
               || !var0.is(Blocks.CACTUS) && var0.canBeReplaced()
            : false;
      } else {
         return true;
      }
   }

   public static boolean mayBreakImplicitly(BlockGetter var0, BlockPos var1, BlockState var2, TemporaryBlockTracker var3) {
      if (var2 == null || var2.isAir()) {
         return true;
      } else if (var2.is(Blocks.BEDROCK) || var2.is(Blocks.BARRIER)) {
         return false;
      } else if (!var2.getFluidState().isEmpty()) {
         return false;
      } else if (var2.getDestroySpeed(var0, var1) < 0.0F) {
         return false;
      } else if (var3 != null && var3.isTracked(var1)) {
         return true;
      } else {
         return !protectPlayerBuilds() ? !var2.hasBlockEntity() : isNaturalTerrain(var2) || isNaturalTreeLog(var0, var1, var2);
      }
   }

   public static boolean isNaturalTreeLog(BlockGetter var0, BlockPos var1, BlockState var2) {
      if (var2 != null && var2.is(BlockTags.LOGS)) {
         MutableBlockPos var3 = new MutableBlockPos();

         for (int var4 = -4; var4 <= 4; var4++) {
            for (int var5 = -1; var5 <= 6; var5++) {
               for (int var6 = -4; var6 <= 4; var6++) {
                  var3.set(var1.getX() + var4, var1.getY() + var5, var1.getZ() + var6);
                  BlockState var7 = var0.getBlockState(var3);
                  if (var7.is(BlockTags.LEAVES) && var7.hasProperty(LeavesBlock.PERSISTENT) && !(Boolean)var7.getValue(LeavesBlock.PERSISTENT)) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean mayClearForBuilding(BlockGetter var0, BlockPos var1, BlockState var2, TemporaryBlockTracker var3) {
      if (var2 == null || var2.isAir() || var2.canBeReplaced()) {
         return true;
      } else if (mayBreakImplicitly(var0, var1, var2, var3)) {
         return true;
      } else {
         return !var2.hasBlockEntity()
               && (
                  var2.is(BlockTags.FLOWERS)
                     || var2.is(BlockTags.SAPLINGS)
                     || var2.is(Blocks.BROWN_MUSHROOM)
                     || var2.is(Blocks.RED_MUSHROOM)
                     || var2.is(Blocks.BROWN_MUSHROOM_BLOCK)
                     || var2.is(Blocks.RED_MUSHROOM_BLOCK)
                     || var2.is(Blocks.MUSHROOM_STEM)
                     || var2.is(Blocks.PUMPKIN)
                     || var2.is(Blocks.MELON)
                     || var2.is(Blocks.BAMBOO)
                     || var2.is(Blocks.CACTUS)
                     || var2.is(Blocks.SWEET_BERRY_BUSH)
                     || var2.is(Blocks.MOSS_BLOCK)
                     || var2.is(Blocks.MOSS_CARPET)
                     || var2.is(Blocks.AZALEA)
                     || var2.is(Blocks.FLOWERING_AZALEA)
                     || var2.is(Blocks.ICE)
                     || var2.is(Blocks.BIG_DRIPLEAF)
                     || var2.is(Blocks.BIG_DRIPLEAF_STEM)
               )
            ? true
            : isNaturalTreeLog(var0, var1, var2);
      }
   }
}
