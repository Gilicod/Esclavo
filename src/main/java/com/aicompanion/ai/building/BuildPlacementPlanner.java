package com.aicompanion.ai.building;

import java.util.*;

public class BuildPlacementPlanner {

    /**
     * Orders blueprint entries bottom-to-top (foundation first, then walls, then roof)
     * so structures are built in realistic, physically stable order.
     */
    public static List<Blueprint.BlockEntry> planConstructionOrder(Blueprint blueprint) {
        if (blueprint == null) return new ArrayList<>();

        List<Blueprint.BlockEntry> sorted = new ArrayList<>(blueprint.getBlocks());
        sorted.sort(Comparator.comparingInt((Blueprint.BlockEntry e) -> e.phase() != null ? e.phase().ordinal() : 1)
                .thenComparingInt(e -> e.offset().getY())
                .thenComparingInt(e -> Math.abs(e.offset().getX()) + Math.abs(e.offset().getZ())));

        return sorted;
    }

    /**
     * Groups block entries by their defined BuildPhase.
     */
    public static Map<Blueprint.BuildPhase, List<Blueprint.BlockEntry>> groupBlocksByPhase(Blueprint blueprint) {
        Map<Blueprint.BuildPhase, List<Blueprint.BlockEntry>> grouped = new LinkedHashMap<>();
        if (blueprint == null) return grouped;

        for (Blueprint.BuildPhase phase : Blueprint.BuildPhase.values()) {
            grouped.put(phase, new ArrayList<>());
        }

        for (Blueprint.BlockEntry entry : blueprint.getBlocks()) {
            Blueprint.BuildPhase phase = entry.phase() != null ? entry.phase() : Blueprint.BuildPhase.WALLS;
            grouped.computeIfAbsent(phase, k -> new ArrayList<>()).add(entry);
        }

        return grouped;
    }

    /**
     * Returns all block entries belonging to a specific BuildPhase.
     */
    public static List<Blueprint.BlockEntry> getBlocksForPhase(Blueprint blueprint, Blueprint.BuildPhase phase) {
        if (blueprint == null || phase == null) return Collections.emptyList();
        List<Blueprint.BlockEntry> result = new ArrayList<>();
        for (Blueprint.BlockEntry entry : blueprint.getBlocks()) {
            Blueprint.BuildPhase p = entry.phase() != null ? entry.phase() : Blueprint.BuildPhase.WALLS;
            if (p == phase) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Returns distinct build phases present in the blueprint, sorted by natural progression.
     */
    public static List<Blueprint.BuildPhase> getPhasesInOrder(Blueprint blueprint) {
        if (blueprint == null) return Collections.emptyList();
        Set<Blueprint.BuildPhase> phases = new TreeSet<>();
        for (Blueprint.BlockEntry entry : blueprint.getBlocks()) {
            phases.add(entry.phase() != null ? entry.phase() : Blueprint.BuildPhase.WALLS);
        }
        return new ArrayList<>(phases);
    }
}
