package com.aicompanion.ai.provider;

import com.aicompanion.ai.parser.TaskParser;
import com.aicompanion.ai.planning.Goal;
import com.aicompanion.entity.CompanionEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LocalRuleProvider implements AIProvider {
    @Override
    public CompletableFuture<List<Goal>> plan(CompanionEntity companion, String prompt) {
        return CompletableFuture.completedFuture(TaskParser.parse(prompt));
    }

    @Override
    public CompletableFuture<String> ask(CompanionEntity companion, String question) {
        String q = question.toLowerCase();
        if (q.contains("haciendo") || q.contains("doing")) {
            return CompletableFuture.completedFuture("Actualmente estoy en estado: " + companion.getCurrentState().getDisplayName() + ".");
        }
        if (q.contains("vida") || q.contains("salud") || q.contains("health")) {
            return CompletableFuture.completedFuture("Tengo " + (int)companion.getHealth() + " de 20 puntos de vida.");
        }
        if (q.contains("nivel") || q.contains("level")) {
            return CompletableFuture.completedFuture("Soy nivel " + companion.getCompanionLevel() + ".");
        }
        return CompletableFuture.completedFuture("¡Listo para trabajar! Puedes darme órdenes como 'mina 20 diamantes', 'sígueme' o 'tala 64 de roble'.");
    }
}
