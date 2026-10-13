package com.aicompanion.ai.provider;

import com.aicompanion.AICompanionConfig;
import com.aicompanion.ai.planning.Goal;
import com.aicompanion.entity.CompanionEntity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OpenAICompatibleProvider implements AIProvider {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private final LocalRuleProvider fallback = new LocalRuleProvider();

    @Override
    public CompletableFuture<List<Goal>> plan(CompanionEntity companion, String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String endpoint = AICompanionConfig.COMMON.aiEndpoint.get();
                if (!endpoint.endsWith("/chat/completions")) {
                    endpoint = endpoint.replaceAll("/+$", "") + "/chat/completions";
                }

                String model = AICompanionConfig.COMMON.aiModel.get();
                double temperature = AICompanionConfig.COMMON.aiTemperature.get();
                int maxTokens = AICompanionConfig.COMMON.aiMaxPlanningTokens.get();
                int timeoutMs = Math.max(2, AICompanionConfig.COMMON.aiTimeoutSeconds.get()) * 1000;

                URL url = new URL(endpoint);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                con.setConnectTimeout(timeoutMs);
                con.setReadTimeout(timeoutMs);
                con.setDoOutput(true);

                String apiKey = System.getenv("OPENAI_API_KEY");
                if (apiKey == null || apiKey.isBlank()) {
                    apiKey = System.getProperty("openai.api.key");
                }
                if (apiKey != null && !apiKey.isBlank()) {
                    con.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
                }

                String systemPrompt = "You are Esclavo, an intelligent autonomous Minecraft companion. "
                        + "Translate the user's order into a valid JSON array of goal objects. "
                        + "Valid goal types: "
                        + "{\"type\":\"OBTAIN\",\"target\":\"minecraft:item_id\",\"amount\":N}, "
                        + "{\"type\":\"KILL\",\"target\":\"minecraft:entity_id\",\"amount\":N}, "
                        + "{\"type\":\"BUILD\",\"blueprint\":\"small_house|medium_house|shelter|watchtower|bridge|farm|two_story_house|warehouse\"}, "
                        + "{\"type\":\"FARM\",\"amount\":N}, "
                        + "{\"type\":\"GUARD\",\"amount\":N}, "
                        + "{\"type\":\"MAINTAIN_CHEST\",\"target\":\"minecraft:item_id\",\"amount\":N}, "
                        + "{\"type\":\"GO_TO\",\"x\":X,\"y\":Y,\"z\":Z}, "
                        + "{\"type\":\"FOLLOW\"}, "
                        + "{\"type\":\"STAY\"}, "
                        + "{\"type\":\"DEPOSIT\"}, "
                        + "{\"type\":\"RETURN_OWNER\"}. "
                        + "Respond ONLY with a JSON array, no commentary.";

                JsonObject reqJson = new JsonObject();
                reqJson.addProperty("model", model);
                reqJson.addProperty("temperature", temperature);
                reqJson.addProperty("max_tokens", maxTokens);

                JsonArray messages = new JsonArray();
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", systemPrompt);
                messages.add(sysMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", prompt);
                messages.add(userMsg);

                reqJson.add("messages", messages);

                byte[] postBytes = reqJson.toString().getBytes(StandardCharsets.UTF_8);
                con.setFixedLengthStreamingMode(postBytes.length);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(postBytes);
                }

                if (con.getResponseCode() == 200) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);

                        JsonObject resObj = JsonParser.parseString(sb.toString()).getAsJsonObject();
                        if (resObj.has("choices")) {
                            JsonArray choices = resObj.getAsJsonArray("choices");
                            if (!choices.isEmpty()) {
                                String content = choices.get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString().trim();
                                if (content.startsWith("```")) {
                                    content = content.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "").trim();
                                }

                                JsonArray goalsArr = JsonParser.parseString(content).getAsJsonArray();
                                List<Goal> plannedGoals = new ArrayList<>();
                                for (JsonElement el : goalsArr) {
                                    if (!el.isJsonObject()) continue;
                                    JsonObject gObj = el.getAsJsonObject();
                                    if (!gObj.has("type")) continue;
                                    String typeStr = gObj.get("type").getAsString().toUpperCase();
                                    Goal.Type gType = Goal.Type.valueOf(typeStr);

                                    Goal goal;
                                    switch (gType) {
                                        case OBTAIN -> {
                                            String target = gObj.has("target") ? gObj.get("target").getAsString() : "minecraft:oak_log";
                                            int amount = gObj.has("amount") ? gObj.get("amount").getAsInt() : 1;
                                            goal = Goal.obtain(new ResourceLocation(target), amount);
                                        }
                                        case KILL -> {
                                            String target = gObj.has("target") ? gObj.get("target").getAsString() : "minecraft:zombie";
                                            int amount = gObj.has("amount") ? gObj.get("amount").getAsInt() : 1;
                                            goal = Goal.kill(new ResourceLocation(target), amount);
                                        }
                                        case BUILD -> {
                                            String bp = gObj.has("blueprint") ? gObj.get("blueprint").getAsString() : "small_house";
                                            goal = Goal.build(bp);
                                        }
                                        case GO_TO -> {
                                            int x = gObj.has("x") ? gObj.get("x").getAsInt() : companion.getBlockX();
                                            int y = gObj.has("y") ? gObj.get("y").getAsInt() : companion.getBlockY();
                                            int z = gObj.has("z") ? gObj.get("z").getAsInt() : companion.getBlockZ();
                                            goal = Goal.goTo(new BlockPos(x, y, z));
                                        }
                                        case FOLLOW -> goal = new Goal(Goal.Type.FOLLOW);
                                        case STAY -> goal = new Goal(Goal.Type.STAY);
                                        case DEPOSIT -> goal = new Goal(Goal.Type.DEPOSIT);
                                        case RETURN_OWNER -> goal = Goal.returnOwner(false);
                                        case RETURN_HOME -> goal = new Goal(Goal.Type.RETURN_HOME);
                                        case FARM -> {
                                            int amount = gObj.has("amount") ? gObj.get("amount").getAsInt() : (gObj.has("radius") ? gObj.get("radius").getAsInt() : 16);
                                            goal = Goal.farm(amount);
                                        }
                                        case GUARD -> {
                                            int radius = gObj.has("amount") ? gObj.get("amount").getAsInt() : (gObj.has("radius") ? gObj.get("radius").getAsInt() : 16);
                                            goal = Goal.guard(radius);
                                        }
                                        case MAINTAIN_CHEST -> {
                                            BlockPos cPos = null;
                                            if (gObj.has("x") && gObj.has("y") && gObj.has("z")) {
                                                cPos = new BlockPos(gObj.get("x").getAsInt(), gObj.get("y").getAsInt(), gObj.get("z").getAsInt());
                                            }
                                            ResourceLocation supplyItem = gObj.has("target") ? new ResourceLocation(gObj.get("target").getAsString()) : null;
                                            int supplyCount = gObj.has("amount") ? gObj.get("amount").getAsInt() : 0;
                                            goal = Goal.maintainChest(cPos, supplyItem, supplyCount);
                                        }
                                        default -> goal = null;
                                    }

                                    if (goal != null) {
                                        plannedGoals.add(goal);
                                    }
                                }

                                if (!plannedGoals.isEmpty()) {
                                    com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-AI] External LLM successfully planned {} goals for '{}'",
                                            plannedGoals.size(), prompt);
                                    return plannedGoals;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-AI] External LLM planning failed: {}. Falling back to deterministic rule provider.", e.getMessage());
            }

            return fallback.plan(companion, prompt).join();
        }, EXECUTOR);
    }

    @Override
    public CompletableFuture<String> ask(CompanionEntity companion, String question) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String endpoint = AICompanionConfig.COMMON.aiEndpoint.get();
                if (!endpoint.endsWith("/chat/completions")) {
                    endpoint = endpoint.replaceAll("/+$", "") + "/chat/completions";
                }

                String model = AICompanionConfig.COMMON.aiModel.get();
                double temperature = AICompanionConfig.COMMON.aiTemperature.get();
                int timeoutMs = Math.max(2, AICompanionConfig.COMMON.aiTimeoutSeconds.get()) * 1000;

                URL url = new URL(endpoint);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                con.setConnectTimeout(timeoutMs);
                con.setReadTimeout(timeoutMs);
                con.setDoOutput(true);

                String apiKey = System.getenv("OPENAI_API_KEY");
                if (apiKey == null || apiKey.isBlank()) {
                    apiKey = System.getProperty("openai.api.key");
                }
                if (apiKey != null && !apiKey.isBlank()) {
                    con.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
                }

                JsonObject reqJson = new JsonObject();
                reqJson.addProperty("model", model);
                reqJson.addProperty("temperature", temperature);
                reqJson.addProperty("max_tokens", 256);

                JsonArray messages = new JsonArray();
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", "You are Esclavo, a helpful and loyal Minecraft companion. Answer concisely in Spanish.");
                messages.add(sysMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", question);
                messages.add(userMsg);

                reqJson.add("messages", messages);

                byte[] postBytes = reqJson.toString().getBytes(StandardCharsets.UTF_8);
                con.setFixedLengthStreamingMode(postBytes.length);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(postBytes);
                }

                if (con.getResponseCode() == 200) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);

                        JsonObject resObj = JsonParser.parseString(sb.toString()).getAsJsonObject();
                        if (resObj.has("choices")) {
                            JsonArray choices = resObj.getAsJsonArray("choices");
                            if (!choices.isEmpty()) {
                                return choices.get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString().trim();
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            return fallback.ask(companion, question).join();
        }, EXECUTOR);
    }
}
