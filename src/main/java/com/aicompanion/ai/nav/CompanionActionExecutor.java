package com.aicompanion.ai.nav;

import com.aicompanion.AICompanionConfig;
import com.aicompanion.debug.EsclavoTrace;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.internal.baritone.planner.InternalBaritonePlanner;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.inventory.EquipmentManager;
import com.aicompanion.util.BlockBreakHelper;
import com.aicompanion.util.TerrainPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CompanionActionExecutor {
   private static final int MAX_CHAINED_ACTIONS = 4;
   private static final int STUCK_JUMP_TICKS = 10;
   private static final int STUCK_LIMIT_TICKS = 40;
   private static final int MOVE_TIMEOUT_TICKS = 50;
   private final CompanionEntity companion;
   private final ArrayDeque<NavigationAction> actionQueue = new ArrayDeque<>();
   private NavigationAction currentAction = null;
   private int actionTicks = 0;
   private int stuckTicks = 0;
   private Vec3 lastPos = Vec3.ZERO;
   private boolean isStuck = false;
   private BlockPos lastStuckTarget = null;
   private String lastStuckReason = "";
   private final List<CompanionActionExecutor.OpenedDoor> openedDoors = new ArrayList<>();

   public CompanionActionExecutor(CompanionEntity var1) {
      this.companion = var1;
   }

   public void setActions(Queue<NavigationAction> var1) {
      this.actionQueue.clear();
      if (var1 != null) {
         this.actionQueue.addAll(var1);
      }

      this.currentAction = null;
      this.actionTicks = 0;
      this.stuckTicks = 0;
      this.isStuck = false;
      if (this.companion != null) {
         this.lastPos = this.companion.position();
      }
   }

   public void appendActions(Queue<NavigationAction> var1) {
      if (var1 != null) {
         this.actionQueue.addAll(var1);
      }
   }

   public void clear() {
      if (this.companion != null && this.companion.isSprinting()) {
         this.companion.setSprinting(false);
      }
      if (this.currentAction != null
         && this.currentAction.getType() == NavigationAction.Type.BREAK_BLOCK
         && this.currentAction.getTargetBlock() != null
         && this.companion != null
         && this.companion.level() instanceof ServerLevel var1) {
         BlockBreakHelper.cancelBreaking(this.companion, var1, this.currentAction.getTargetBlock());
      }

      this.actionQueue.clear();
      this.currentAction = null;
      this.actionTicks = 0;
      this.stuckTicks = 0;
      this.isStuck = false;
   }

   public boolean hasActions() {
      return this.currentAction != null || !this.actionQueue.isEmpty();
   }

   public boolean isStuck() {
      return this.isStuck;
   }

   public BlockPos consumeStuckTarget() {
      BlockPos var1 = this.lastStuckTarget;
      this.lastStuckTarget = null;
      return var1;
   }

   public String consumeStuckReason() {
      String var1 = this.lastStuckReason;
      this.lastStuckReason = "";
      return var1;
   }

   public boolean isMakingNoProgress() {
      if (this.currentAction == null) {
         return false;
      }

      NavigationAction.Type var1 = this.currentAction.getType();
      boolean var2 = var1 == NavigationAction.Type.MOVE_TO || var1 == NavigationAction.Type.JUMP || var1 == NavigationAction.Type.SWIM;
      return var2 && (this.stuckTicks >= 20 || this.actionTicks >= 40);
   }

   public boolean isMakingProgress() {
      if (this.isStuck) {
         return false;
      }
      if (this.currentAction == null) {
         return false;
      }
      NavigationAction.Type var1 = this.currentAction.getType();
      if (var1 == NavigationAction.Type.BREAK_BLOCK || var1 == NavigationAction.Type.PLACE_BLOCK || var1 == NavigationAction.Type.INTERACT) {
         return true;
      }
      return this.stuckTicks < 15;
   }

   public NavigationAction getCurrentAction() {
      return this.currentAction;
   }

   public int getRemainingActionsCount() {
      return this.actionQueue.size() + (this.currentAction != null ? 1 : 0);
   }

   private void markStuck(String var1) {
      if (this.companion != null && this.companion.isSprinting()) {
         this.companion.setSprinting(false);
      }
      this.isStuck = true;
      this.lastStuckReason = var1
         + (this.currentAction != null ? " | accion=" + this.currentAction + " ticksEnAccion=" + this.actionTicks : "")
         + " | colisionHorizontal="
         + this.companion.horizontalCollision
         + " suelo="
         + this.companion.onGround()
         + " agua="
         + this.companion.isInWater();
      if (this.currentAction != null) {
         BlockPos var2 = this.currentAction.getTargetBlock();
         if (var2 == null && this.currentAction.getTargetVec() != null) {
            var2 = BlockPos.containing(this.currentAction.getTargetVec());
         }

         this.lastStuckTarget = var2;
      }
   }

   public boolean tick() {
      if (this.companion != null && this.companion.isAlive()) {
         this.tickDoors();
         if (this.currentAction == null && this.actionQueue.isEmpty()) {
            if (this.companion.isSprinting()) {
               this.companion.setSprinting(false);
            }
            return false;
         }

         if (this.isStuck) {
            if (this.companion.isSprinting()) {
               this.companion.setSprinting(false);
            }
            return false;
         }

         for (int var1 = 0; var1 < 4; var1++) {
            if (this.currentAction == null) {
               if (this.actionQueue.isEmpty()) {
                  return true;
               }

               this.currentAction = this.actionQueue.poll();
               this.actionTicks = 0;
               this.stuckTicks = 0;
               this.lastPos = this.companion.position();
            }

            if (var1 == 0) {
               this.actionTicks++;
               if (!this.checkProgress()) {
                  return false;
               }
            }

            boolean var2 = this.executeAction(this.currentAction);
            if (!var2) {
               return true;
            }

            this.currentAction = null;
            this.actionTicks = 0;
            this.stuckTicks = 0;
            if (this.isStuck) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private boolean checkProgress() {
      NavigationAction.Type var1 = this.currentAction.getType();
      if (var1 != NavigationAction.Type.MOVE_TO && var1 != NavigationAction.Type.JUMP && var1 != NavigationAction.Type.SWIM) {
         return true;
      }

      int var2 = var1 == NavigationAction.Type.SWIM ? 100 : 50;
      if (this.actionTicks > var2) {
         this.markStuck("timeout: no llego al nodo en " + var2 + " ticks (posible vuelta en circulos)");
         return false;
      }

      if (this.companion.position().distanceToSqr(this.lastPos) < 0.0025) {
         this.stuckTicks++;
         Vec3 tgtW = this.currentAction.getTargetVec();
         boolean targetBelow = tgtW != null && tgtW.y < this.companion.getY() - 0.25;
         if (this.companion.isInWater() && this.stuckTicks > 12 && !targetBelow) {
            Vec3 var3 = this.currentAction.getTargetVec();
            double var4 = var3 != null ? var3.x - this.companion.getX() : this.companion.getLookAngle().x;
            double var6 = var3 != null ? var3.z - this.companion.getZ() : this.companion.getLookAngle().z;
            double var8 = Math.sqrt(var4 * var4 + var6 * var6);
            if (var8 > 0.01) {
               var4 /= var8;
               var6 /= var8;
            }

            this.companion.setDeltaMovement(var4 * 0.28, 0.46, var6 * 0.28);
            this.companion.hasImpulse = true;
            this.companion.getJumpControl().jump();
            this.stuckTicks = 0;
         } else if (this.stuckTicks >= 10 && this.stuckTicks % 10 == 0) {
            this.companion.getJumpControl().jump();
         }

         if (this.stuckTicks > 40) {
            this.markStuck("sin moverse 40 ticks (bloqueado)");
            return false;
         }
      } else {
         this.stuckTicks = 0;
         this.lastPos = this.companion.position();
      }

      return true;
   }

   private boolean executeAction(NavigationAction var1) {
      return switch (var1.getType()) {
         case MOVE_TO -> this.executeMoveTo(var1);
         case JUMP -> this.executeJump(var1);
         case BREAK_BLOCK -> {
            if (this.companion.isSprinting()) this.companion.setSprinting(false);
            yield this.executeBreakBlock(var1);
         }
         case PLACE_BLOCK -> {
            if (this.companion.isSprinting()) this.companion.setSprinting(false);
            yield this.executePlaceBlock(var1);
         }
         case SWIM -> this.executeSwim(var1);
         case WAIT -> {
            if (this.companion.isSprinting()) this.companion.setSprinting(false);
            yield this.executeWait(var1);
         }
         case INTERACT -> {
            if (this.companion.isSprinting()) this.companion.setSprinting(false);
            yield this.executeInteract(var1);
         }
      };
   }

   private boolean canLookAhead(Vec3 var1) {
      NavigationAction var2 = this.actionQueue.peek();
      if (var2 != null && var2.getType() == NavigationAction.Type.MOVE_TO && var2.getTargetVec() != null) {
         Vec3 var3 = var2.getTargetVec();
         if (Math.abs(var3.y - var1.y) > 0.01) {
            return false;
         }

         if (this.companion.onGround() && !(Math.abs(this.companion.getY() - var1.y) > 0.6)) {
            double var4 = var1.x - this.companion.getX();
            double var6 = var1.z - this.companion.getZ();
            double var8 = var3.x - var1.x;
            double var10 = var3.z - var1.z;
            double var12 = Math.sqrt(var4 * var4 + var6 * var6);
            double var14 = Math.sqrt(var8 * var8 + var10 * var10);
            if (!(var12 < 1.0E-4) && !(var14 < 1.0E-4)) {
               double var16 = (var4 * var8 + var6 * var10) / (var12 * var14);
               return var16 > 0.92;
            } else {
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean alreadyCloserToNext(Vec3 var1, double var2) {
      NavigationAction var4 = this.actionQueue.peek();
      if (var4 != null && var4.getType() == NavigationAction.Type.MOVE_TO && var4.getTargetVec() != null) {
         Vec3 var5 = var4.getTargetVec();
         if (!(Math.abs(var5.y - var1.y) > 0.01) && !(Math.abs(this.companion.getY() - var5.y) > 0.6)) {
            double var6 = var5.x - this.companion.getX();
            double var8 = var5.z - this.companion.getZ();
            double var10 = var6 * var6 + var8 * var8;
            return var10 < 1.0 && var10 < var2;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean executeMoveTo(NavigationAction var1) {
      Vec3 var2 = var1.getTargetVec();
      if (var2 == null) {
         return true;
      }

      double var3 = var2.x - this.companion.getX();
      double var5 = var2.z - this.companion.getZ();
      double var7 = var3 * var3 + var5 * var5;
      double var9 = Math.abs(var2.y - this.companion.getY());
      if (var7 < 0.35 && var9 < 1.25) {
         return true;
      }

      if (var7 < 0.75 && var9 < 0.6 && this.canLookAhead(var2)) {
         return true;
      }

      if (this.alreadyCloserToNext(var2, var7)) {
         return true;
      }

      this.openDoorsToward(var2);
      this.companion.getLookControl().setLookAt(var2.x, var2.y + 1.5, var2.z, 30.0F, 30.0F);
      this.companion.getMoveControl().setWantedPosition(var2.x, var2.y, var2.z, MovementPolicy.clamp(var1.getSpeed()));
      if (MovementPolicy.sprintingAllowed()) {
         this.companion.setSprinting(true);
      }
      if (this.companion.isInWater()) {
         boolean var11 = var2.y >= this.companion.getY() - 0.25;
         boolean lowAirMt = this.companion.getAirSupply() < this.companion.getMaxAirSupply() * 0.35;
         if (!var11 && !lowAirMt) {
            // 1.13.12: el nodo está más abajo dentro del agua -> bucear (antes el FloatGoal lo subía siempre y
            // el planificador lo daba por atascado en la superficie de la poza).
            this.companion.markDiving();
            Vec3 cur = this.companion.getDeltaMovement();
            double dist = Math.sqrt(var7);
            double hx = dist > 0.01 ? var3 / dist * 0.06 : 0.0;
            double hz = dist > 0.01 ? var5 / dist * 0.06 : 0.0;
            this.companion.setDeltaMovement(cur.x * 0.7 + hx, Math.min(cur.y, -0.14), cur.z * 0.7 + hz);
            this.companion.hasImpulse = true;
         } else if (var11 || this.companion.horizontalCollision) {
            Vec3 var12 = this.companion.getDeltaMovement();
            double var13 = Math.sqrt(var7);
            double var15 = var13 > 0.01 ? var3 / var13 * 0.1 : 0.0;
            double var17 = var13 > 0.01 ? var5 / var13 * 0.1 : 0.0;
            double var19 = this.companion.horizontalCollision ? 0.36 : 0.12;
            this.companion.setDeltaMovement(var12.x * 0.6 + var15, Math.max(var12.y, var19), var12.z * 0.6 + var17);
            this.companion.getJumpControl().jump();
            this.companion.hasImpulse = true;
         }
      } else if (this.companion.horizontalCollision && var2.y > this.companion.getY() + 0.5 && var9 <= 1.2) {
         this.companion.getJumpControl().jump();
      }

      return false;
   }

   private boolean executeJump(NavigationAction var1) {
      Vec3 var2 = var1.getTargetVec();
      if (var2 != null) {
         this.openDoorsToward(var2);
         this.companion.getLookControl().setLookAt(var2.x, var2.y + 1.5, var2.z, 30.0F, 30.0F);
         this.companion.getMoveControl().setWantedPosition(var2.x, var2.y, var2.z, MovementPolicy.clamp(var1.getSpeed()));
         if (MovementPolicy.sprintingAllowed()) {
            this.companion.setSprinting(true);
         }
      }

      if (this.companion.isInWater()) {
         Vec3 var3 = this.companion.getDeltaMovement();
         double var4 = var2 != null ? var2.x - this.companion.getX() : this.companion.getLookAngle().x;
         double var6 = var2 != null ? var2.z - this.companion.getZ() : this.companion.getLookAngle().z;
         double var8 = Math.sqrt(var4 * var4 + var6 * var6);
         if (var8 > 0.01) {
            var4 /= var8;
            var6 /= var8;
         }

         double var10 = this.companion.horizontalCollision ? 0.38 : Math.max(var3.y, 0.12);
         this.companion.setDeltaMovement(var3.x * 0.6 + var4 * 0.12, var10, var3.z * 0.6 + var6 * 0.12);
         this.companion.hasImpulse = true;
      } else if (this.companion.onGround()) {
         this.companion.getJumpControl().jump();
      }

      return var2 != null && this.companion.getY() >= var2.y - 0.05 && this.actionTicks >= 2
         ? true
         : this.actionTicks >= 4 && !this.companion.onGround() || this.actionTicks >= 12;
   }

   private boolean executeBreakBlock(NavigationAction var1) {
      BlockPos var2 = var1.getTargetBlock();
      if (var2 == null) {
         return true;
      } else if (!(this.companion.level() instanceof ServerLevel var3)) {
         return true;
      } else {
         BlockState var10 = var3.getBlockState(var2);
         if (var10.isAir() || !var10.blocksMotion()) {
            return true;
         }

         if (InternalBaritonePlanner.isOpenableDoorway(var10)) {
            this.openDoorway(var3, var2, var10);
            return true;
         }

         boolean var5 = AICompanionConfig.COMMON != null
            && (Boolean)AICompanionConfig.COMMON.allowBlockBreaking.get()
            && this.companion.getSettings().isAllowBlockBreaking();
         if (var5 && TerrainPolicy.mayBreakImplicitly(var3, var2, var10, this.companion.getTemporaryBlocks())) {
            com.aicompanion.ai.equipment.ToolSelectionPolicy.ensureToolForBlock(this.companion, var10);

            // Never break stone or ores with an axe, sword, or fist: abort and mark stuck if no pickaxe
            if (com.aicompanion.ai.equipment.ToolSelectionPolicy.isPickaxePreferred(var10) && !EquipmentManager.isPickaxe(this.companion.getMainHandItem())) {
               this.actionQueue.clear();
               this.markStuck("necesita un pico para romper " + var10.getBlock() + " y no tiene ni puede fabricar uno");
               return true;
            }

            int var7 = BlockBreakHelper.calculateBreakTicks(this.companion, var2, var10);
            if (var7 <= 0) {
               this.actionQueue.clear();
               this.markStuck("no puede romper " + var10.getBlock() + " (irrompible o sin herramienta)");
               return true;
            } else {
               this.companion.getLookControl().setLookAt(var2.getX() + 0.5, var2.getY() + 0.5, var2.getZ() + 0.5);
               boolean var8 = BlockBreakHelper.tickBreaking(this.companion, var3, var2, this.actionTicks, var7);
               if (var8) {
                  boolean var9 = BlockBreakHelper.finishBreaking(this.companion, var3, var2);
                  EsclavoTrace.log(
                     "NAV-ROMPE",
                     this.companion,
                     "abre paso rompiendo " + var10.getBlock() + " en " + EsclavoTrace.fmt(var2) + " ok=" + var9 + " ticks=" + var7
                  );
                  return true;
               } else {
                  return false;
               }
            }
         } else {
            this.actionQueue.clear();
            this.markStuck("no se le permite romper " + var10.getBlock() + " en " + EsclavoTrace.fmt(var2) + " (config o bloque protegido)");
            return true;
         }
      }
   }

   private boolean executePlaceBlock(NavigationAction var1) {
      BlockPos var2 = var1.getTargetBlock();
      if (var2 == null) {
         return true;
      }

      if (this.companion.level() instanceof ServerLevel var3) {
         BlockState var14 = var3.getBlockState(var2);
         if (!var14.isAir() && !var14.canBeReplaced()) {
            return true;
         }

         boolean var5 = AICompanionConfig.COMMON != null
            && (Boolean)AICompanionConfig.COMMON.allowBlockPlacement.get()
            && this.companion.getSettings().isAllowBlockPlacement();
         if (!var5) {
            this.actionQueue.clear();
            this.markStuck("colocar bloques desactivado (no puede hacer puente)");
            return true;
         }

         int var6 = ScaffoldBlockHelper.findBestScaffoldSlot(this.companion);
         if (var6 == -1) {
            boolean var7 = ScaffoldBlockHelper.acquireScaffoldBlockAround(this.companion, var3);
            if (var7) {
               var6 = ScaffoldBlockHelper.findBestScaffoldSlot(this.companion);
            }
         }

         if (var6 == -1) {
            this.actionQueue.clear();
            this.markStuck("sin bloques en el inventario para hacer puente");
            return true;
         }

         AABB var15 = new AABB(var2);
         if (this.companion.getBoundingBox().deflate(0.05).intersects(var15)) {
            if (this.companion.onGround()) {
               this.companion.getJumpControl().jump();
            }

            return this.actionTicks > 20;
         } else {
            CompanionInventoryHandler var8 = this.companion.getInventoryHandler();
            ItemStack var9 = var8.getStackInSlot(var6);
            if (var9.getItem() instanceof BlockItem var10) {
               Block var16 = var10.getBlock();
               BlockState var12 = var16.defaultBlockState();
               var3.setBlock(var2, var12, 3);
               SoundType var13 = var12.getSoundType();
               var3.playSound(null, var2, var13.getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
               this.companion.swing(InteractionHand.MAIN_HAND);
               this.companion.getLookControl().setLookAt(var2.getX() + 0.5, var2.getY() + 0.5, var2.getZ() + 0.5);
               this.companion.getTemporaryBlocks().track(var2, var16);
               EsclavoTrace.log("NAV-PUENTE", this.companion, "coloca " + var16 + " en " + EsclavoTrace.fmt(var2));
               var9.shrink(1);
               if (var9.isEmpty()) {
                  var8.setStackInSlot(var6, ItemStack.EMPTY);
               }
            }

            return true;
         }
      } else {
         return true;
      }
   }

   private boolean executeSwim(NavigationAction var1) {
      Vec3 var2 = var1.getTargetVec();
      if (var2 == null) {
         return true;
      }

      this.companion.getLookControl().setLookAt(var2.x, var2.y + 0.5, var2.z, 30.0F, 30.0F);
      this.companion.getMoveControl().setWantedPosition(var2.x, var2.y, var2.z, MovementPolicy.clamp(var1.getSpeed()));
      if (this.companion.isInWater()) {
         double var3 = var2.y - this.companion.getY();
         Vec3 var7 = this.companion.getDeltaMovement();
         // 1.13.11: antes usaba max(velY, -0.08): el FloatGoal empujaba hacia arriba y NUNCA podía bajar nadando
         // (se quedaba flotando en la superficie del agua subterránea y se declaraba atascado).
         boolean lowAir = this.companion.getAirSupply() < this.companion.getMaxAirSupply() * 0.35;
         if (var3 < -0.1 && !lowAir) {
            this.companion.markDiving();
            this.companion.setDeltaMovement(var7.x, Math.min(var7.y, -0.12), var7.z);
         } else {
            double var5 = (var3 > 0.1 || lowAir) ? 0.18 : 0.04;
            this.companion.setDeltaMovement(var7.x, Math.max(var7.y, var5), var7.z);
         }
         if (this.companion.horizontalCollision && var3 >= -0.2) {
            this.companion.getJumpControl().jump();
            this.companion.hasImpulse = true;
         }
      }

      double var8 = var2.x - this.companion.getX();
      double var9 = var2.z - this.companion.getZ();
      return var8 * var8 + var9 * var9 < 0.45 && Math.abs(var2.y - this.companion.getY()) < 1.5;
   }

   private boolean executeWait(NavigationAction var1) {
      return this.actionTicks >= var1.getWaitTicks();
   }

   private boolean executeInteract(NavigationAction var1) {
      BlockPos var2 = var1.getTargetBlock();
      if (var2 != null) {
         this.companion.getLookControl().setLookAt(var2.getX() + 0.5, var2.getY() + 0.5, var2.getZ() + 0.5);
         this.companion.swing(InteractionHand.MAIN_HAND);
      }

      return true;
   }

   private void openDoorsToward(Vec3 var1) {
      if (this.companion.level() instanceof ServerLevel var2) {
         BlockPos var11 = BlockPos.containing(var1);
         this.tryOpenAt(var2, var11);
         this.tryOpenAt(var2, var11.above());
         double var4 = var1.x - this.companion.getX();
         double var6 = var1.z - this.companion.getZ();
         double var8 = Math.sqrt(var4 * var4 + var6 * var6);
         if (var8 > 0.01) {
            BlockPos var10 = BlockPos.containing(
               this.companion.getX() + var4 / var8 * 0.9, this.companion.getY() + 0.2, this.companion.getZ() + var6 / var8 * 0.9
            );
            this.tryOpenAt(var2, var10);
            this.tryOpenAt(var2, var10.above());
         }
      }
   }

   private void tryOpenAt(ServerLevel var1, BlockPos var2) {
      if (var1.isLoaded(var2)) {
         BlockState var3 = var1.getBlockState(var2);
         if (InternalBaritonePlanner.isOpenableDoorway(var3) && !var3.getCollisionShape(var1, var2).isEmpty()) {
            this.openDoorway(var1, var2, var3);
         }
      }
   }

   private void openDoorway(ServerLevel var1, BlockPos var2, BlockState var3) {
      try {
         if (var3.getBlock() instanceof DoorBlock var4) {
            var4.setOpen(this.companion, var1, var3, var2, true);
            this.rememberDoor(var2);
            EsclavoTrace.log("PUERTA", this.companion, "abre " + var3.getBlock() + " en " + EsclavoTrace.fmt(var2));
         } else if (var3.getBlock() instanceof FenceGateBlock) {
            BooleanProperty var7 = findOpenProperty(var3);
            if (var7 != null && !(Boolean)var3.getValue(var7)) {
               var1.setBlock(var2, (BlockState)var3.setValue(var7, Boolean.TRUE), 10);
               this.rememberDoor(var2);
               EsclavoTrace.log("PUERTA", this.companion, "abre " + var3.getBlock() + " en " + EsclavoTrace.fmt(var2));
            }
         }

         this.companion.swing(InteractionHand.MAIN_HAND);
      } catch (Exception var6) {
      }
   }

   private void rememberDoor(BlockPos var1) {
      BlockPos var2 = var1.immutable();

      for (CompanionActionExecutor.OpenedDoor var4 : this.openedDoors) {
         if (var4.pos.equals(var2)) {
            var4.openedAt = this.companion.tickCount;
            return;
         }
      }

      this.openedDoors.add(new CompanionActionExecutor.OpenedDoor(var2, this.companion.tickCount));
   }

   private void tickDoors() {
      if (!this.openedDoors.isEmpty() && this.companion.level() instanceof ServerLevel var1) {
         Iterator var18 = this.openedDoors.iterator();

         while (var18.hasNext()) {
            CompanionActionExecutor.OpenedDoor var3 = (CompanionActionExecutor.OpenedDoor)var18.next();
            int var4 = this.companion.tickCount - var3.openedAt;
            double var5 = var3.pos.getX() + 0.5;
            double var7 = var3.pos.getZ() + 0.5;
            double var9 = this.companion.getX() - var5;
            double var11 = this.companion.getZ() - var7;
            boolean var13 = var9 * var9 + var11 * var11 > 6.25 || Math.abs(this.companion.getY() - var3.pos.getY()) > 3.0;
            if (var4 > 600 || var4 < 0) {
               var18.remove();
            } else if (var13 && var4 > 15) {
               try {
                  if (var1.isLoaded(var3.pos)) {
                     BlockState var14 = var1.getBlockState(var3.pos);
                     if (var14.getBlock() instanceof DoorBlock var15) {
                        var15.setOpen(this.companion, var1, var14, var3.pos, false);
                     } else if (var14.getBlock() instanceof FenceGateBlock) {
                        BooleanProperty var19 = findOpenProperty(var14);
                        if (var19 != null && (Boolean)var14.getValue(var19)) {
                           var1.setBlock(var3.pos, (BlockState)var14.setValue(var19, Boolean.FALSE), 10);
                        }
                     }
                  }
               } catch (Exception var17) {
               }

               var18.remove();
            }
         }
      }
   }

   private static BooleanProperty findOpenProperty(BlockState var0) {
      for (Property var2 : var0.getProperties()) {
         if (var2 instanceof BooleanProperty var3 && "open".equals(var2.getName())) {
            return var3;
         }
      }

      return null;
   }

   private static final class OpenedDoor {
      final BlockPos pos;
      int openedAt;

      OpenedDoor(BlockPos var1, int var2) {
         this.pos = var1;
         this.openedAt = var2;
      }
   }
}
