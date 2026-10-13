package com.aicompanion.internal.baritone.path;

import com.aicompanion.ai.nav.NavigationAction;
import com.aicompanion.internal.baritone.movements.MovementType;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class BaritonePathAdapter {
   public static Queue<NavigationAction> adaptToActions(Path var0, double var1) {
      ArrayDeque var3 = new ArrayDeque();
      if (var0 != null && !var0.isEmpty()) {
         int var4 = var0.length();
         int var5 = var4 > 1 ? 1 : 0;

         for (int var6 = var5; var6 < var4; var6++) {
            PathNode var7 = var0.getNode(var6);
            BlockPos var8 = var7.getPos();
            Vec3 var9 = Vec3.atBottomCenterOf(var8);
            if (var7.movementType == MovementType.BRIDGE) {
               BlockPos var10 = var7.actionPos != null ? var7.actionPos : var8.below();
               var3.add(NavigationAction.placeBlock(var10));
               var3.add(NavigationAction.moveTo(var8, var1));
            } else if (var7.movementType == MovementType.TUNNEL) {
               BlockPos var12 = var7.actionPos != null ? var7.actionPos : var8;
               BlockPos var11 = var12.above();
               var3.add(NavigationAction.breakBlock(var12));
               var3.add(NavigationAction.breakBlock(var11));
               var3.add(NavigationAction.moveTo(var8, var1));
            } else if (var7.movementType == MovementType.TUNNEL_DOWN) {
               BlockPos var12 = var7.actionPos != null ? var7.actionPos : var8;
               var3.add(NavigationAction.breakBlock(var12.above(2)));
               var3.add(NavigationAction.breakBlock(var12.above(1)));
               var3.add(NavigationAction.breakBlock(var12));
               var3.add(NavigationAction.moveTo(var8, var1));
            } else if (var7.movementType == MovementType.TUNNEL_UP) {
               if (var6 > 0) {
                  BlockPos prevPos = var0.getNode(var6 - 1).getPos();
                  var3.add(NavigationAction.breakBlock(prevPos.above(2)));
               }
               BlockPos var12 = var7.actionPos != null ? var7.actionPos : var8;
               var3.add(NavigationAction.breakBlock(var12.above(1)));
               var3.add(NavigationAction.breakBlock(var12));
               var3.add(NavigationAction.jump(var9, var1));
               var3.add(NavigationAction.moveTo(var8, var1));
            } else if (var7.movementType == MovementType.ASCEND) {
               if (var7.actionPos != null && var6 > 0) {
                  BlockPos var13 = var0.getNode(var6 - 1).getPos();
                  var3.add(NavigationAction.breakBlock(var13.above(2)));
                  var3.add(NavigationAction.breakBlock(var8));
                  var3.add(NavigationAction.breakBlock(var8.above()));
               }

               var3.add(NavigationAction.jump(var9, var1));
               var3.add(NavigationAction.moveTo(var8, var1));
            } else if (var7.movementType == MovementType.DIG_DOWN) {
               BlockPos var14 = var7.actionPos != null ? var7.actionPos : var8;
               var3.add(NavigationAction.breakBlock(var14));
               var3.add(NavigationAction.moveTo(var8, var1));
            } else if (var7.movementType == MovementType.SWIM) {
               var3.add(NavigationAction.swim(var9, var1));
            } else {
               var3.add(NavigationAction.moveTo(var8, var1));
            }
         }

         return var3;
      } else {
         return var3;
      }
   }
}
