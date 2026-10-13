package com.aicompanion.ai.nav;

import com.aicompanion.AICompanionConfig;
import com.aicompanion.AICompanionMod;
import com.aicompanion.debug.EsclavoTrace;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.internal.baritone.goals.Goal;
import com.aicompanion.internal.baritone.goals.GoalBlock;
import com.aicompanion.internal.baritone.goals.GoalEntity;
import com.aicompanion.internal.baritone.path.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public class NavigationManager {
   private final CompanionEntity companion;
   private final InternalBaritoneNavigationProvider baritoneProvider;
   private final EsclabNavigationProvider esclabProvider;
   private final VanillaNavigationProvider vanillaProvider;
   private NavigationProvider fallbackOverride = null;
   private int fallbackTicks = 0;
   private Goal currentGoal = null;
   private double currentSpeed = 1.0;

   public NavigationManager(CompanionEntity var1) {
      this.companion = var1;
      this.baritoneProvider = new InternalBaritoneNavigationProvider(var1);
      this.esclabProvider = new EsclabNavigationProvider(var1);
      this.vanillaProvider = new VanillaNavigationProvider(var1);
   }

   public NavigationProvider getActiveProvider() {
      if (this.fallbackOverride != null) {
         return this.fallbackOverride;
      }

      String var1 = "AUTO";

      try {
         if (AICompanionConfig.COMMON != null && AICompanionConfig.COMMON.navigationMode != null) {
            var1 = (String)AICompanionConfig.COMMON.navigationMode.get();
         }
      } catch (Exception var3) {
      }

      if ("INTERNAL_BARITONE".equalsIgnoreCase(var1)) {
         return this.baritoneProvider;
      } else if ("ESCLAB".equalsIgnoreCase(var1)) {
         return this.esclabProvider;
      } else {
         return "VANILLA_SAFE".equalsIgnoreCase(var1) ? this.vanillaProvider : this.baritoneProvider;
      }
   }

   public boolean navigateTo(Goal var1, double var2) {
      double var4 = MovementPolicy.clamp(var2);
      if (var1 != null && isSameGoal(this.currentGoal, var1)) {
         NavigationProvider var6 = this.getActiveProvider();
         NavigationRequestResult var7 = var6.getStatus();
         if (!var6.isStuck() && !var6.isDone() && var7 == NavigationRequestResult.STARTED) {
            this.currentSpeed = var4;
            return true;
         }
      } else if (var1 != null && this.fallbackOverride != null) {
         this.stopProvidersExcept(null);
         this.fallbackOverride = null;
         this.fallbackTicks = 0;
      }

      if (!isSameGoal(this.currentGoal, var1)) {
         EsclavoTrace.log(
            "NAV-META",
            this.companion,
            "nueva meta='"
               + (var1 != null ? var1.getDescription() : "null")
               + "' velocidad="
               + String.format("%.2f", var4)
               + " navegador="
               + this.getActiveProvider().getName()
         );
      }

      this.currentGoal = var1;
      this.currentSpeed = var4;
      NavigationProvider var8 = this.getActiveProvider();
      boolean var9 = var8.navigateTo(var1, var4);
      if (!var9 && this.fallbackOverride == null) {
         this.triggerCircuitBreaker("No path in primary provider");
         return this.getActiveProvider().navigateTo(var1, var4);
      } else {
         return var9;
      }
   }

   static boolean isSameGoal(Goal var0, Goal var1) {
      if (var0 == null || var1 == null) {
         return false;
      } else if (var0 == var1) {
         return true;
      } else if (var0.getClass() != var1.getClass()) {
         return false;
      } else if (var0 instanceof GoalEntity var2 && var1 instanceof GoalEntity var5) {
         return var2.getTarget() == var5.getTarget();
      } else {
         String var4 = var0.getDescription();
         String var3 = var1.getDescription();
         return var4 != null && var4.equals(var3);
      }
   }

   public boolean moveTo(BlockPos var1, double var2) {
      return this.navigateTo(new GoalBlock(var1), var2);
   }

   public boolean moveTo(Entity var1, double var2) {
      return this.navigateTo(new GoalEntity(var1, 2.5, 4.0), var2);
   }

   public void tick() {
      EsclavoTrace.tickCompanion(this.companion, this);
      NavigationProvider var1 = this.getActiveProvider();
      var1.tick();
      if (var1.isStuck() && this.fallbackOverride == null) {
         this.triggerCircuitBreaker("Stuck in " + var1.getName());
         if (this.currentGoal != null) {
            this.getActiveProvider().navigateTo(this.currentGoal, this.currentSpeed);
         }
      } else if (var1.isStuck() && this.fallbackOverride == this.esclabProvider) {
         this.triggerCircuitBreaker("Stuck in " + var1.getName());
         if (this.currentGoal != null) {
            this.getActiveProvider().navigateTo(this.currentGoal, this.currentSpeed);
         }
      }

      if (this.fallbackOverride != null) {
         this.fallbackTicks++;
         if (this.fallbackOverride.isDone() || this.fallbackTicks > 160) {
            EsclavoTrace.log(
               "FALLBACK",
               this.companion,
               "vuelve al navegador principal tras "
                  + this.fallbackTicks
                  + " ticks en "
                  + this.fallbackOverride.getName()
                  + " (termino="
                  + this.fallbackOverride.isDone()
                  + ")"
            );
            this.fallbackOverride.stop();
            this.fallbackOverride = null;
            this.fallbackTicks = 0;
            if (this.currentGoal != null && !this.getActiveProvider().isDone()) {
               this.getActiveProvider().navigateTo(this.currentGoal, this.currentSpeed);
            }
         }
      }
   }

   private void triggerCircuitBreaker(String var1) {
      NavigationProvider var2 = this.getActiveProvider();
      if (this.fallbackOverride == null) {
         this.fallbackOverride = this.esclabProvider;
         this.fallbackTicks = 0;
      } else if (this.fallbackOverride == this.esclabProvider) {
         this.fallbackOverride = this.vanillaProvider;
         this.fallbackTicks = 0;
      }

      if (var2 != this.getActiveProvider()) {
         var2.stop();
      }

      EsclavoTrace.log(
         "FALLBACK",
         this.companion,
         var2.getName()
            + " -> "
            + this.getActiveProvider().getName()
            + " ("
            + var1
            + ") meta='"
            + (this.currentGoal != null ? this.currentGoal.getDescription() : "null")
            + "'"
      );
      if (AICompanionConfig.COMMON != null) {
         try {
            if ((Boolean)AICompanionConfig.COMMON.debugLogging.get()) {
               AICompanionMod.LOGGER.debug("[Nav] Fallback -> {} ({})", this.getActiveProvider().getName(), var1);
            }
         } catch (Exception var4) {
         }
      }
   }

   private void stopProvidersExcept(NavigationProvider var1) {
      if (var1 != this.baritoneProvider) {
         this.baritoneProvider.stop();
      }

      if (var1 != this.esclabProvider) {
         this.esclabProvider.stop();
      }

      if (var1 != this.vanillaProvider) {
         this.vanillaProvider.stop();
      }
   }

   public void stop() {
      this.currentGoal = null;
      this.baritoneProvider.stop();
      this.esclabProvider.stop();
      this.vanillaProvider.stop();
      this.fallbackOverride = null;
      this.fallbackTicks = 0;
      if (this.companion != null && this.companion.isSprinting()) {
         this.companion.setSprinting(false);
      }
   }

   public double getCurrentSpeed() {
      return this.currentSpeed;
   }

   public void recomputePath() {
      if (this.currentGoal != null) {
         this.getActiveProvider().navigateTo(this.currentGoal, this.currentSpeed);
      } else {
         this.companion.getNavigation().recomputePath();
      }
   }

   public boolean isDone() {
      return this.getActiveProvider().isDone();
   }

   public boolean isStuck() {
      return this.getActiveProvider().isStuck();
   }

   public boolean isMakingProgress() {
      return this.getActiveProvider().isMakingProgress();
   }

   public NavigationRequestResult getStatus() {
      return this.getActiveProvider().getStatus();
   }

   public Path getCurrentPath() {
      return this.getActiveProvider().getCurrentPath();
   }

   public Goal getCurrentGoal() {
      return this.currentGoal;
   }

   public InternalBaritoneNavigationProvider getBaritoneProvider() {
      return this.baritoneProvider;
   }

   public String getDebugStats() {
      NavigationProvider var1 = this.getActiveProvider();
      Path var2 = var1.getCurrentPath();
      int var3 = var2 != null ? var2.length() : 0;
      int var4 = var1 instanceof InternalBaritoneNavigationProvider var5 ? var5.getExecutor().getRemainingActionsCount() : 0;
      return String.format(
         "Provider: %s | Status: %s | Goal: %s | PathLen: %d | ActionsLeft: %d",
         var1.getName(),
         var1.getStatus().name(),
         this.currentGoal != null ? this.currentGoal.getDescription() : "None",
         var3,
         var4
      );
   }
}
