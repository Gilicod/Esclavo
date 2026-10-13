package com.aicompanion.internal.baritone.v120;

import com.aicompanion.internal.baritone.goals.Goal;

public final class PathNode {
   public final int x;
   public final int y;
   public final int z;
   public final double estimatedCostToGoal;
   public double cost;
   public double combinedCost;
   public PathNode previous = null;
   public int heapPosition;
   public static final double HEURISTIC_WEIGHT = 1.6;

   public PathNode(int var1, int var2, int var3, Goal var4) {
      this.cost = Double.POSITIVE_INFINITY;
      this.estimatedCostToGoal = var4.heuristic(var1, var2, var3) * 1.6;
      if (Double.isNaN(this.estimatedCostToGoal)) {
         throw new IllegalStateException(String.format("%s calculated implausible heuristic NaN at %s %s %s", var4, var1, var2, var3));
      }

      this.heapPosition = -1;
      this.x = var1;
      this.y = var2;
      this.z = var3;
   }

   public boolean isOpen() {
      return this.heapPosition != -1;
   }

   @Override
   public int hashCode() {
      return (int)NpcPathSearch.key(this.x, this.y, this.z);
   }

   @Override
   public boolean equals(Object var1) {
      return var1 instanceof PathNode var2 ? this.x == var2.x && this.y == var2.y && this.z == var2.z : false;
   }
}
