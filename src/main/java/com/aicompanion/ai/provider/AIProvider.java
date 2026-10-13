package com.aicompanion.ai.provider;

import com.aicompanion.ai.planning.Goal;
import com.aicompanion.entity.CompanionEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AIProvider {
    CompletableFuture<List<Goal>> plan(CompanionEntity companion, String prompt);
    CompletableFuture<String> ask(CompanionEntity companion, String question);
}
