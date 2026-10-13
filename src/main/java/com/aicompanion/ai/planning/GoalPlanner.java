package com.aicompanion.ai.planning;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.tasks.*;
import com.aicompanion.entity.CompanionEntity;

import java.util.ArrayList;
import java.util.List;

public class GoalPlanner {
    public static List<CompanionTask> plan(CompanionEntity companion, Goal goal) {
        List<CompanionTask> plan = new ArrayList<>();
        switch (goal.getType()) {
            case OBTAIN -> {
                net.minecraft.resources.ResourceLocation res = goal.getTargetResource();
                com.aicompanion.ai.gathering.GatherStrategyRegistry.StrategyType strat =
                        com.aicompanion.ai.gathering.GatherStrategyRegistry.determineStrategy(res);
                // 1.13.7: "minar <bloque>" elegido del desplegable -> minar ese bloque aunque no se llame *_ore/*_stone
                if ("mine".equals(goal.getStringParam())) {
                    net.minecraft.world.level.block.Block blk = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(res);
                    if (strat == com.aicompanion.ai.gathering.GatherStrategyRegistry.StrategyType.TREE) {
                        plan.add(new ChopTask(companion, res.getPath().replace("_log", "").replace("_wood", "").replace("_stem", ""), goal.getAmount()));
                        plan.add(new ReturnTask(companion, false));
                        break;
                    }
                    if (blk != net.minecraft.world.level.block.Blocks.AIR) {
                        strat = com.aicompanion.ai.gathering.GatherStrategyRegistry.StrategyType.BLOCK_MINE;
                    }
                }
                switch (strat) {
                    case TREE -> {
                        String wood = (goal.getStringParam() != null && !goal.getStringParam().isEmpty())
                                ? goal.getStringParam()
                                : res.getPath().replace("_log", "").replace("_wood", "").replace("minecraft:", "");
                        plan.add(new ChopTask(companion, wood, goal.getAmount()));
                    }
                    case BLOCK_MINE -> {
                        plan.add(new MineTask(companion, res, goal.getAmount()));
                    }
                    case MOB_DROP -> {
                        net.minecraft.resources.ResourceLocation mob = com.aicompanion.ai.gathering.GatherStrategyRegistry.getMobForDrop(res);
                        if (mob != null) {
                            plan.add(new KillTask(companion, mob, goal.getAmount(), res));
                        } else {
                            plan.add(new GatherTask(companion, res, goal.getAmount()));
                        }
                    }
                    case SMELTING -> {
                        net.minecraft.resources.ResourceLocation rawSource = com.aicompanion.ai.gathering.GatherStrategyRegistry.getRawSmeltingSource(res);
                        plan.add(new SmeltTask(companion, rawSource, goal.getAmount()));
                    }
                    case CRAFTING -> {
                        plan.add(new CraftTask(companion, res, goal.getAmount()));
                    }
                    default -> {
                        plan.add(new GatherTask(companion, res, goal.getAmount()));
                    }
                }
                plan.add(new ReturnTask(companion, false));
            }
            case KILL -> {
                plan.add(new KillTask(companion, goal.getTargetResource(), goal.getAmount()));
                plan.add(new ReturnTask(companion, false));
            }
            case GO_TO -> plan.add(new GotoTask(companion, goal.getTargetPos()));
            case BUILD -> plan.add(new BuildTask(companion, goal.getStringParam() != null ? goal.getStringParam() : "small_house", companion.blockPosition()));
            case STAY -> plan.add(new StayTask(companion));
            case FOLLOW -> plan.add(new FollowTask(companion));
            case FARM -> plan.add(new FarmTask(companion, goal.getAmount() > 0 ? goal.getAmount() : 16));
            case GUARD -> plan.add(new GuardTask(companion, goal.getAmount() > 0 ? goal.getAmount() : 16));
            case MAINTAIN_CHEST -> plan.add(new MaintainChestTask(companion, goal.getTargetPos(), goal.getTargetResource(), goal.getAmount()));
            case DEPOSIT -> {
                plan.add(new DepositTask(companion, companion.getHomePos()));
                plan.add(new ReturnTask(companion, false));
            }
            case RETURN_HOME -> plan.add(new ReturnTask(companion, true));
            case RETURN_OWNER -> plan.add(new ReturnTask(companion, false));
        }
        return plan;
    }
}
