package com.aicompanion.ai.building;

import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.inventory.CompanionInventoryHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Calculates exact material breakdowns and inventory availability
 * based on the TRANSFORMED Blueprint, guaranteeing 100% accuracy before building.
 */
public class BuildingMaterialsCalculator {

    public record MaterialSummary(
            int totalBlocksToPlace,
            int totalAvailableBlocks,
            int totalMissingBlocks,
            Map<Item, Integer> required,
            Map<Item, Integer> available,
            Map<Item, Integer> missing,
            String summaryLine,
            String statusLine
    ) {}

    public static MaterialSummary calculate(Blueprint blueprint, CompanionEntity companion) {
        if (blueprint == null || blueprint.getBlocks().isEmpty()) {
            return new MaterialSummary(0, 0, 0, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), "Sin bloques seleccionados", "SELECCIONA UNA CONSTRUCCIÓN");
        }

        Map<Item, Integer> required = new LinkedHashMap<>();
        int totalBlocks = 0;

        for (Blueprint.BlockEntry entry : blueprint.getBlocks()) {
            BlockState state = entry.state();
            if (state == null || state.isAir()
                    || SchematicImporter.isTechnicalOrUnobtainable(state)
                    || SchematicImporter.isSecondaryMultiBlockPart(state)) continue;

            Block block = state.getBlock();
            Item item = block.asItem();
            if (item != Items.AIR) {
                required.put(item, required.getOrDefault(item, 0) + 1);
                totalBlocks++;
            }
        }

        Map<Item, Integer> available = new LinkedHashMap<>();
        Map<Item, Integer> missing = new LinkedHashMap<>();
        int totalAvailable = 0;
        int totalMissing = 0;

        CompanionInventoryHandler inv = companion != null ? companion.getInventoryHandler() : null;

        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            Item item = entry.getKey();
            int needed = entry.getValue();

            int have = 0;
            if (inv != null) {
                have = inv.countItem(item);
            }

            available.put(item, have);

            int diff = Math.max(0, needed - have);
            if (diff > 0) {
                missing.put(item, diff);
                totalMissing += diff;
            }
            totalAvailable += Math.min(have, needed);
        }

        // Build summary line (e.g. "120 ADOQUÍN | 80 ROBLE | 14 CRISTAL | 1 PUERTA")
        List<String> itemsList = new ArrayList<>();
        int shownCount = 0;
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            if (shownCount++ >= 4) {
                itemsList.add("+" + (required.size() - 4) + " más...");
                break;
            }
            String name = new ItemStack(entry.getKey()).getHoverName().getString().toUpperCase();
            itemsList.add(entry.getValue() + " " + name);
        }
        String summaryLine = itemsList.isEmpty() ? "0 BLOQUES" : String.join(" | ", itemsList);

        String statusLine;
        if (totalMissing == 0 && totalBlocks > 0) {
            statusLine = "100% DISPONIBLE: Listo para edificar de inmediato";
        } else if (totalMissing > 0) {
            statusLine = "FALTAN " + totalMissing + " BLOQUES: Usa 'CONSEGUIR Y CONSTRUIR' para recolectar";
        } else {
            statusLine = "LISTO PARA PLANIFICAR";
        }

        return new MaterialSummary(
                totalBlocks,
                totalAvailable,
                totalMissing,
                required,
                available,
                missing,
                summaryLine,
                statusLine
        );
    }
}
