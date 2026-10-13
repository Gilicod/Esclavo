package com.aicompanion.ai.nav;

import com.aicompanion.AICompanionMod;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.util.BlockBreakHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

public class ScaffoldBlockHelper {
   public static boolean isSafeScaffoldBlock(Block block) {
      if (block == null) return false;
      if (block == Blocks.SCAFFOLDING) return true;
      if (block.defaultBlockState().is(BlockTags.DIRT)
            || block == Blocks.DIRT
            || block == Blocks.COARSE_DIRT
            || block == Blocks.ROOTED_DIRT
            || block == Blocks.GRASS_BLOCK
            || block == Blocks.MUD
            || block == Blocks.MUDDY_MANGROVE_ROOTS
            || block == Blocks.NETHERRACK) {
         return true;
      }
      if (block == Blocks.COBBLESTONE
            || block == Blocks.COBBLED_DEEPSLATE
            || block == Blocks.MOSSY_COBBLESTONE
            || block == Blocks.BLACKSTONE) {
         return true;
      }
      return block == Blocks.STONE
            || block == Blocks.DEEPSLATE
            || block == Blocks.ANDESITE
            || block == Blocks.DIORITE
            || block == Blocks.GRANITE
            || block == Blocks.TUFF
            || block == Blocks.CALCITE
            || block == Blocks.DRIPSTONE_BLOCK
            || block == Blocks.BASALT;
   }

   public static boolean isPreciousOrFragileBlock(Block block, ItemStack stack) {
      if (block == null) return true;
      if (block instanceof FallingBlock) return true;

      // 1.13.59: Vidrio y variantes (se rompen sin silk touch, son materiales de construcción frágiles)
      if (block == Blocks.GLASS || block == Blocks.TINTED_GLASS
            || block instanceof net.minecraft.world.level.block.AbstractGlassBlock
            || block instanceof net.minecraft.world.level.block.StainedGlassBlock) {
         return true;
      }

      // 1.13.59: Escombros ancestrales (Ancient Debris) y Netherite
      if (block == Blocks.ANCIENT_DEBRIS || block == Blocks.NETHERITE_BLOCK) {
         return true;
      }

      String id = block.getDescriptionId().toLowerCase();
      if (id.contains("ancient_debris") || id.contains("netherite") || id.contains("glass") || id.contains("cristal") || id.contains("vidrio")) {
         return true;
      }

      // Madera / tablones / troncos (son recursos de carpintería, no andamios descartables en navegación normal)
      if (stack != null && !stack.isEmpty()) {
         if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.WARPED_STEMS) || stack.is(ItemTags.CRIMSON_STEMS) || stack.is(ItemTags.PLANKS)) {
            return true;
         }
         if (stack.is(ItemTags.COALS)
               || stack.is(ItemTags.IRON_ORES)
               || stack.is(ItemTags.GOLD_ORES)
               || stack.is(ItemTags.DIAMOND_ORES)
               || stack.is(ItemTags.REDSTONE_ORES)
               || stack.is(ItemTags.LAPIS_ORES)
               || stack.is(ItemTags.COPPER_ORES)
               || stack.is(ItemTags.EMERALD_ORES)) {
            return true;
         }
         Item item = stack.getItem();
         if (item == Items.RAW_IRON || item == Items.RAW_GOLD || item == Items.RAW_COPPER) {
            return true;
         }
      }

      if (id.contains("ore") || id.contains("mineral") || id.contains("raw_") || id.contains("diamond") || id.contains("emerald") || id.contains("debris")) {
         return true;
      }

      // Bloques preciosos o funcionales
      if (block == Blocks.IRON_BLOCK
            || block == Blocks.GOLD_BLOCK
            || block == Blocks.DIAMOND_BLOCK
            || block == Blocks.EMERALD_BLOCK
            || block == Blocks.COPPER_BLOCK
            || block == Blocks.COAL_BLOCK
            || block == Blocks.REDSTONE_BLOCK
            || block == Blocks.LAPIS_BLOCK
            || block == Blocks.AMETHYST_BLOCK) {
         return true;
      }

      // Contenedores y bloques de utilidad
      if (block.defaultBlockState().hasBlockEntity()
            || block instanceof ShulkerBoxBlock
            || block == Blocks.CRAFTING_TABLE
            || block == Blocks.FURNACE
            || block == Blocks.CHEST
            || block == Blocks.BARREL
            || block == Blocks.SMOKER
            || block == Blocks.BLAST_FURNACE
            || block == Blocks.ANVIL
            || block == Blocks.CHIPPED_ANVIL
            || block == Blocks.DAMAGED_ANVIL
            || block == Blocks.HOPPER
            || block == Blocks.DISPENSER
            || block == Blocks.DROPPER
            || block == Blocks.SHULKER_BOX
            || block == Blocks.BEACON
            || block == Blocks.ENCHANTING_TABLE
            || block == Blocks.BREWING_STAND
            || block == Blocks.ENDER_CHEST) {
         return true;
      }

      // Bloques de alta resistencia o decorativos costosos
      if (block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN || block == Blocks.BEDROCK || block == Blocks.BARRIER) {
         return true;
      }

      if (id.contains("terracotta") || id.contains("glazed") || id.contains("concrete") || id.contains("quartz") || id.contains("prismarine")) {
         return true;
      }

      return false;
   }

   public static boolean isForbiddenScaffold(ItemStack var0, CompanionEntity var1) {
      if (var0 == null || var0.isEmpty() || !(var0.getItem() instanceof BlockItem bi)) {
         return true;
      }
      Block block = bi.getBlock();
      if (isPreciousOrFragileBlock(block, var0)) {
         return true;
      }
      return !isSafeScaffoldBlock(block);
   }

   public static int getScaffoldPriority(ItemStack var0) {
      if (var0 == null || var0.isEmpty() || !(var0.getItem() instanceof BlockItem var1)) {
         return 0;
      }
      Block var3 = var1.getBlock();
      if (isPreciousOrFragileBlock(var3, var0)) {
         return 0;
      }
      if (var3 == Blocks.SCAFFOLDING) {
         return 150;
      }
      if (var3 == Blocks.DIRT
         || var3 == Blocks.COARSE_DIRT
         || var3 == Blocks.GRASS_BLOCK
         || var3 == Blocks.ROOTED_DIRT
         || var3 == Blocks.MUD
         || var3 == Blocks.NETHERRACK
         || var3.defaultBlockState().is(BlockTags.DIRT)) {
         return 100;
      }

      if (var3 == Blocks.COBBLESTONE
         || var3 == Blocks.COBBLED_DEEPSLATE
         || var3 == Blocks.MOSSY_COBBLESTONE
         || var3 == Blocks.BLACKSTONE) {
         return 80;
      }

      if (var3 == Blocks.ANDESITE
         || var3 == Blocks.DIORITE
         || var3 == Blocks.GRANITE
         || var3 == Blocks.TUFF
         || var3 == Blocks.CALCITE
         || var3 == Blocks.DRIPSTONE_BLOCK
         || var3 == Blocks.STONE
         || var3 == Blocks.DEEPSLATE
         || var3 == Blocks.BASALT) {
         return 70;
      }

      return 0;
   }

   public static int findBestScaffoldSlot(CompanionEntity var0) {
      if (var0 == null) {
         return -1;
      }

      CompanionInventoryHandler var1 = var0.getInventoryHandler();
      int var2 = -1;
      int var3 = 0;

      for (int var4 = 0; var4 < 36; var4++) {
         ItemStack var5 = var1.getStackInSlot(var4);
         if (!var5.isEmpty() && !isForbiddenScaffold(var5, var0)) {
            int var6 = getScaffoldPriority(var5);
            if (var6 > var3) {
               var3 = var6;
               var2 = var4;
            }
         }
      }

      return var2;
   }

   /**
    * 1.13.12: emergencia (rescate): si no hay bloques "desechables", vale cualquier bloque de cubo completo que no
    * caiga y no sea valioso (minerales, bloques preciosos, contenedores). Tablones y troncos incluidos.
    */
   public static int findEmergencyScaffoldSlot(CompanionEntity var0) {
      if (var0 == null) {
         return -1;
      }
      int normal = findBestScaffoldSlot(var0);
      if (normal != -1) {
         return normal;
      }
      CompanionInventoryHandler inv = var0.getInventoryHandler();
      int best = -1;
      int bestScore = Integer.MIN_VALUE;
      for (int i = 0; i < 36; i++) {
         ItemStack s = inv.getStackInSlot(i);
         if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi)) continue;
         Block b = bi.getBlock();
         if (isPreciousOrFragileBlock(b, s)) continue;
         if (!b.defaultBlockState().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) continue;
         if (b.defaultBlockState().hasBlockEntity()) continue; // cofres, hornos, mesas especiales...
         // prefer planks over logs (a log can become 4 planks), then larger stacks
         int score = s.getCount() + (s.is(ItemTags.PLANKS) ? 100 : 0) - (s.is(ItemTags.LOGS) ? 50 : 0);
         if (score > bestScore) {
            bestScore = score;
            best = i;
         }
      }
      return best;
   }

   public static boolean acquireScaffoldBlockAround(CompanionEntity var0, ServerLevel var1) {
      if (var0 != null && var1 != null) {
         BlockPos var2 = var0.blockPosition();
         BlockPos var3 = var2.below();
         BlockPos var4 = null;
         double var5 = Double.MAX_VALUE;

         // 1.13.11: solo bloques que realmente sueltan algo colocable (tierra siempre; piedra solo con pico;
         // las hojas no sueltan bloque sin tijeras), sin tocar construcciones del jugador. Funciona también
         // rodeado de agua (antes se descartaba cualquier bloque con agua encima).
         for (int var7 = -3; var7 <= 3; var7++) {
            for (int var8 = -3; var8 <= 3; var8++) {
               for (int var9 = -1; var9 <= 2; var9++) {
                  BlockPos var10 = var2.offset(var7, var9, var8);
                  if (!var10.equals(var2) && !var10.equals(var3) && !var10.equals(var2.above())) {
                     BlockState var11 = var1.getBlockState(var10);
                     if (!var11.isAir() && !var11.liquid() && isDisposableTerrain(var11, var1, var10)
                           && !var11.is(BlockTags.LEAVES)
                           && com.aicompanion.util.TerrainPolicy.mayBreakImplicitly(var1, var10, var11, var0.getTemporaryBlocks())) {
                        boolean dirtLike = var11.is(BlockTags.DIRT) || var11.is(Blocks.GRASS_BLOCK) || var11.is(Blocks.NETHERRACK);
                        if (!dirtLike && com.aicompanion.inventory.EquipmentManager.findBestPickaxeSlot(var0, var11) == -1) {
                           continue; // stone without a pickaxe drops nothing
                        }
                        double var13 = var2.distSqr(var10) + (dirtLike ? 0.0 : 4.0) + (var9 < 0 ? 3.0 : 0.0);
                        if (var13 < var5) {
                           var5 = var13;
                           var4 = var10;
                        }
                     }
                  }
               }
            }
         }

         if (var4 != null) {
            AICompanionMod.LOGGER.info("[Esclavo] Harvesting disposable scaffold block {} at {} to build an escape", var1.getBlockState(var4).getBlock().getName().getString(), var4);
            int toolSlot = com.aicompanion.inventory.EquipmentManager.findBestPickaxeSlot(var0, var1.getBlockState(var4));
            if (toolSlot != -1) {
               com.aicompanion.inventory.EquipmentManager.swapMainHandSafely(var0, toolSlot);
            }
            boolean var15 = BlockBreakHelper.finishBreaking(var0, var1, var4);
            if (var15) {
               BlockBreakHelper.collectDropsPhysically(var0, var4, 5.0);
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean isDisposableTerrain(BlockState var0, ServerLevel var1, BlockPos var2) {
      String var3 = var0.getBlock().getDescriptionId().toLowerCase();
      if (var3.contains("ore") || var3.contains("log") || var3.contains("planks")) {
         return false;
      } else if (var0.is(BlockTags.LOGS) || var0.is(BlockTags.PLANKS)) {
         return false;
      } else if (!var0.is(Blocks.BEDROCK) && !var0.is(Blocks.BARRIER)) {
         float var4 = var0.getDestroySpeed(var1, var2);
         return !(var4 < 0.0F) && !(var4 > 3.0F)
            ? var0.is(BlockTags.DIRT)
               || var0.is(Blocks.GRASS_BLOCK)
               || var0.is(Blocks.COBBLESTONE)
               || var0.is(Blocks.STONE)
               || var0.is(Blocks.NETHERRACK)
               || var0.is(Blocks.ANDESITE)
               || var0.is(Blocks.DIORITE)
               || var0.is(Blocks.GRANITE)
               || var0.is(Blocks.TUFF)
               || var0.is(Blocks.DEEPSLATE)
               || var0.is(Blocks.COBBLED_DEEPSLATE)
               || var0.is(BlockTags.LEAVES)
            : false;
      } else {
         return false;
      }
   }
}
