package com.aicompanion.client.gui;

import com.aicompanion.AICompanionMod;
import com.aicompanion.client.gui.widgets.EsclabButton;
import com.aicompanion.client.gui.widgets.EsclabKeybindButton;
import com.aicompanion.client.gui.widgets.EsclabTabButton;
import com.aicompanion.client.gui.widgets.EsclabToggle;
import com.aicompanion.client.key.ModKeyBindings;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.inventory.CompanionMenu;
import com.aicompanion.network.CompanionNetworking;
import com.aicompanion.network.packets.C2S_ActionPacket;
import com.aicompanion.network.packets.C2S_NaturalOrderPacket;
import com.aicompanion.network.packets.S2C_SyncCompanionDataPacket;
import com.aicompanion.init.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.aicompanion.ai.order.OrderResultStatus;
import com.aicompanion.ai.order.OrderSubmitMode;
import com.aicompanion.ai.order.PlanPreviewRow;
import com.aicompanion.ai.planning.Goal;
import com.aicompanion.ai.parser.TaskParser;
import com.aicompanion.ai.parser.NormalizedOrderText;
import com.aicompanion.network.packets.C2S_SubmitOrderPacket;
import com.aicompanion.network.packets.C2S_RequestOrderPreviewPacket;
import com.aicompanion.network.packets.S2C_OrderResultPacket;
import com.aicompanion.network.packets.S2C_OrderPreviewPacket;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.aicompanion.ai.parser.ItemSearchRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CompanionScreen extends AbstractContainerScreen<CompanionMenu> {
    private static final ResourceLocation GUI_MAIN = new ResourceLocation(AICompanionMod.MOD_ID, "textures/gui/esclab_main.png");
    private static final ResourceLocation GUI_ICONS = new ResourceLocation(AICompanionMod.MOD_ID, "textures/gui/esclab_icons.png");

    private static final String[] TAB_KEYS = {
            "gui.aicompanion.tab.inventory", "gui.aicompanion.tab.name", "gui.aicompanion.tab.actual",
            "gui.aicompanion.tab.orders", "gui.aicompanion.tab.queue", "gui.aicompanion.tab.build",
            "gui.aicompanion.tab.ai", "gui.aicompanion.tab.memory", "gui.aicompanion.tab.stats", "gui.aicompanion.tab.config"
    };

    private static final String[] TAB_NAMES = {
            "INVENTARIO", "NOMBRE", "ACTUAL", "ÓRDENES", "COLA",
            "CONSTRUCCIÓN", "IA", "MEMORIA", "ESTADÍSTICAS", "CONFIG"
    };

    private int selectedTab = 0;
    private S2C_SyncCompanionDataPacket syncedData = null;

    // Name Tab state
    private EditBox nameInput;
    private String nameFeedbackText = "";
    private int nameFeedbackColor = 0xFF3CD058;

    // Dynamic suggestions & test status
    private com.aicompanion.client.gui.widgets.EsclabSuggestionDropdown suggestionDropdown = null;
    private String connectionTestStatus = null;

    // Order submission protocol & state
    private static int nextRequestId = 1;
    private int pendingSubmitRequestId = -1;
    private long submitRequestTimestamp = 0;
    private boolean isSubmitting = false;
    private String submitStatusText = "";
    private int submitStatusColor = 0xFFFFFFFF;
    private EsclabButton executeBtn = null;

    // Server-Authoritative Plan Preview
    private int lastPreviewRequestId = 0;
    private long lastPreviewTypeTime = 0;
    private String lastRequestedPreviewText = "";
    private boolean showFullPlan = false;
    private final List<PlanPreviewRow> serverPreviewRows = new ArrayList<>();
    private String serverGoalSummary = "";
    private String serverIntentSummary = "";
    private boolean serverPlanValid = false;

    // Order & plan interpretation
    private EditBox orderInput;
    private String interpretedPlanTarget = "Conseguir 40 de Oro";
    private final List<String> interpretedPlanSteps = new ArrayList<>();
    public record OrderBadge(String label, int bgColor, int borderColor, int textColor) {}
    private final List<OrderBadge> currentOrderBadges = new ArrayList<>();

    // Modals
    private enum ModalType { NONE, MINE, CHOP, KILL, GOTO, DISMISS_CONFIRM, ITEM_CATALOG }
    private ModalType activeModal = ModalType.NONE;

    // Dynamic Catalog
    private EditBox catalogSearchInput = null;
    private List<ItemSearchRegistry.CatalogEntry> catalogResults = new ArrayList<>();
    private int catalogPage = 0;
    private static final int CATALOG_PAGE_SIZE = 8;

    // Destinations
    private static final String[] DEST_NAMES = {"Traérmelo", "Cofre Base", "Esclavo"};

    // Mining modal state
    private static final String[] ORE_NAMES = {"Hierro", "Oro", "Diamante", "Carbón", "Cobre", "Redstone", "Lapislázuli", "Netherita"};
    private static final String[] ORE_IDS = {"iron", "gold", "diamond", "coal", "copper", "redstone", "lapis", "netherite"};
    private int selectedOreIdx = 1; // Default: Oro
    private int mineAmount = 40;
    private int mineDestination = 0;

    // Chopping modal state
    private static final String[] WOOD_NAMES = {"Roble", "Abedul", "Abeto", "Jungla", "Acacia", "Roble Oscuro", "Manglar", "Cerezo"};
    private static final String[] WOOD_IDS = {"oak", "birch", "spruce", "jungle", "acacia", "dark_oak", "mangrove", "cherry"};
    private int selectedWoodIdx = 0;
    private int chopAmount = 32;
    private int chopDestination = 0;

    // Hunting modal state
    private static final String[] MOB_NAMES = {"Zombie", "Esqueleto", "Araña", "Creeper", "Enderman", "Bruja"};
    private static final String[] MOB_IDS = {"zombie", "skeleton", "spider", "creeper", "enderman", "witch"};
    private int selectedMobIdx = 0;
    private int killAmount = 10;

    // Goto modal inputs
    private EditBox gotoXInput;
    private EditBox gotoYInput;
    private EditBox gotoZInput;

    // Construction Studio (Tab 5)
    private com.aicompanion.client.gui.building.BuildingStudioView buildingStudioView;

    public <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> T addStudioWidget(T widget) {
        return this.addRenderableWidget(widget);
    }

    // Legacy Construction state
    private final List<String> availableBlueprintNames = new ArrayList<>();
    private final List<String> availableBlueprintIds = new ArrayList<>();
    private int selectedBlueprintIdx = 0;
    private int buildRotation = 0; // 0, 90, 180, 270

    private void refreshAvailableBlueprints() {
        availableBlueprintNames.clear();
        availableBlueprintIds.clear();

        try {
            List<String> customIds = com.aicompanion.ai.building.BlueprintRegistry.getCustomSchematicIds();
            for (String id : customIds) {
                String disp = com.aicompanion.ai.building.BlueprintRegistry.getFormatDisplayName(id);
                availableBlueprintNames.add("📁 " + disp);
                availableBlueprintIds.add(id);
            }
        } catch (Throwable ignored) {}

        availableBlueprintNames.add("Casa de Supervivencia (5x5)");
        availableBlueprintIds.add("small_house");

        availableBlueprintNames.add("Casa Mediana (7x7)");
        availableBlueprintIds.add("medium_house");

        availableBlueprintNames.add("Casa 2 Pisos (7x7)");
        availableBlueprintIds.add("two_story_house");

        availableBlueprintNames.add("Refugio Rápido (4x4)");
        availableBlueprintIds.add("shelter");

        availableBlueprintNames.add("Torre de Vigilancia");
        availableBlueprintIds.add("watchtower");

        availableBlueprintNames.add("Almacén / Bodega");
        availableBlueprintIds.add("warehouse");

        availableBlueprintNames.add("Muralla Defensiva");
        availableBlueprintIds.add("defensive_wall");

        availableBlueprintNames.add("Puente Sturdy");
        availableBlueprintIds.add("bridge");

        availableBlueprintNames.add("Granja Vallada");
        availableBlueprintIds.add("farm");

        if (selectedBlueprintIdx < 0 || selectedBlueprintIdx >= availableBlueprintIds.size()) {
            selectedBlueprintIdx = 0;
        }
    }

    // Construction material text input & dropdown (single unified box)
    private EditBox materialInput;
    private String customMaterialText = "Roble y Adoquín";
    private com.aicompanion.client.gui.widgets.EsclabSuggestionDropdown constructionDropdown = null;
    private String currentOrderText = "";

    // Config states
    private boolean cfgAutoFollow = true;
    private boolean cfgAutoEat = true;
    private boolean cfgAutoEquip = true;
    private boolean cfgAutoDeliver = false;
    private boolean cfgAutoReturn = true;
    private boolean cfgBlockBreak = true;
    private boolean cfgBlockPlace = true;
    private boolean cfgCombat = true;
    private boolean cfgChunkLoad = true;
    private boolean cfgHud = true;

    private UUID companionUUID = null;
    private CompanionEntity dummyCompanion = null;

    public CompanionScreen(CompanionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, Component.literal("ESCLAB"));
        this.imageWidth = 430;
        this.imageHeight = 314;
        this.companionUUID = menu.getCompanionUUID();

        interpretedPlanTarget = "Esperando orden...";
        interpretedPlanSteps.clear();
        interpretedPlanSteps.add("• Escribe una orden o selecciona una sugerencia con las flechas o ratón.");
        interpretedPlanSteps.add("• Soporta misiones encadenadas con 'y', 'despues', o comas.");
    }

    private CompanionEntity getDummyCompanion() {
        if (dummyCompanion == null && Minecraft.getInstance().level != null) {
            dummyCompanion = new CompanionEntity(ModEntities.COMPANION.get(), Minecraft.getInstance().level);
        }
        return dummyCompanion;
    }

    private void sendAction(C2S_ActionPacket.Action action) {
        sendAction(action, "");
    }

    private void sendAction(C2S_ActionPacket.Action action, String payload) {
        CompanionEntity companion = menu.getCompanion();
        int entityId = companion != null ? companion.getId() : (syncedData != null ? syncedData.entityId : 0);
        UUID compUUID = companionUUID != null ? companionUUID : (menu.getCompanionUUID() != null ? menu.getCompanionUUID() : (syncedData != null ? syncedData.companionUUID : null));
        CompanionNetworking.CHANNEL.sendToServer(new C2S_ActionPacket(action, entityId, compUUID, payload));
    }

    private void sendNaturalOrder(String text) {
        int reqId = ++nextRequestId;
        CompanionEntity companion = menu.getCompanion();
        int entityId = companion != null ? companion.getId() : (syncedData != null ? syncedData.entityId : 0);
        UUID compUUID = companionUUID != null ? companionUUID : (menu.getCompanionUUID() != null ? menu.getCompanionUUID() : (syncedData != null ? syncedData.companionUUID : null));
        CompanionNetworking.CHANNEL.sendToServer(new C2S_SubmitOrderPacket(reqId, entityId, compUUID, text, OrderSubmitMode.REPLACE_CURRENT));
    }

    private void applySmartSuggestion(com.aicompanion.ai.parser.ItemSearchRegistry.SmartSuggestion sugg) {
        if (this.orderInput == null || sugg == null) return;
        String cur = this.orderInput.getValue();
        String next;
        String insert = sugg.insertText();

        if ("ACTION".equals(sugg.category())) {
            String trimmed = cur.trim();
            if (trimmed.isEmpty() || !trimmed.contains(" ")) {
                next = insert + " ";
            } else if (trimmed.endsWith(" y") || trimmed.endsWith(" luego") || trimmed.endsWith(",")) {
                next = trimmed + " " + insert + " ";
            } else {
                int lastSpace = cur.lastIndexOf(' ');
                String lastWord = cur.substring(lastSpace + 1).trim();
                if (!lastWord.isEmpty() && insert.toLowerCase().startsWith(lastWord.toLowerCase())) {
                    next = cur.substring(0, lastSpace + 1) + insert + " ";
                } else {
                    next = trimmed + " y " + insert + " ";
                }
            }
        } else if ("SUBTYPE".equals(sugg.category()) || "ITEM".equals(sugg.category())) {
            String trimmed = cur.trim();
            String[] parts = trimmed.split("\\s+");

            // Reemplazo inteligente del objetivo preservando la acción (verbo) y cantidad previa
            if (parts.length >= 1 && (parts[0].equalsIgnoreCase("minar") || parts[0].equalsIgnoreCase("mina")
                    || parts[0].equalsIgnoreCase("talar") || parts[0].equalsIgnoreCase("tala")
                    || parts[0].equalsIgnoreCase("cazar") || parts[0].equalsIgnoreCase("caza")
                    || parts[0].equalsIgnoreCase("recoger") || parts[0].equalsIgnoreCase("recoge")
                    || parts[0].equalsIgnoreCase("conseguir") || parts[0].equalsIgnoreCase("consigue")
                    || parts[0].equalsIgnoreCase("buscar") || parts[0].equalsIgnoreCase("busca")
                    || parts[0].equalsIgnoreCase("picar") || parts[0].equalsIgnoreCase("pica")
                    || parts[0].equalsIgnoreCase("corta") || parts[0].equalsIgnoreCase("cortar")
                    || parts[0].equalsIgnoreCase("matar") || parts[0].equalsIgnoreCase("mata"))) {

                String verb = parts[0];
                String qty = (parts.length >= 2 && parts[1].matches("\\d+")) ? parts[1] : null;

                // Extraer destino posterior si ya lo había escrito ("y traer a mi", "y guardar en baul", etc.)
                String trailingDest = "";
                int yIdx = trimmed.lastIndexOf(" y ");
                if (yIdx > 0) {
                    String afterY = trimmed.substring(yIdx + 3).trim();
                    if (afterY.startsWith("traer") || afterY.startsWith("guardar") || afterY.startsWith("volver") || afterY.startsWith("sigueme") || afterY.startsWith("en inventario")) {
                        trailingDest = " " + trimmed.substring(yIdx).trim();
                    }
                }

                if (qty != null) {
                    next = verb + " " + qty + " " + insert + " " + (trailingDest.isEmpty() ? "" : trailingDest.trim() + " ");
                } else {
                    next = verb + " " + insert + " " + (trailingDest.isEmpty() ? "" : trailingDest.trim() + " ");
                }
            } else {
                int lastSpace = cur.lastIndexOf(' ');
                if (cur.endsWith(" ") || lastSpace < 0) {
                    next = cur.trim().isEmpty() ? insert + " " : cur.trim() + " " + insert + " ";
                } else {
                    next = cur.substring(0, lastSpace + 1) + insert + " ";
                }
            }
        } else if ("QUANTITY".equals(sugg.category())) {
            int lastSpace = cur.lastIndexOf(' ');
            if (cur.endsWith(" ") || lastSpace < 0) {
                next = cur.trim() + " " + insert + " ";
            } else {
                next = cur.substring(0, lastSpace + 1) + insert + " ";
            }
        } else if ("DESTINATION".equals(sugg.category())) {
            String cleanVal = insert.trim();
            if (cleanVal.startsWith("y ") || cur.trim().endsWith("y") || cur.trim().isEmpty()) {
                next = cur.trim() + " " + cleanVal;
            } else {
                next = cur.trim() + " " + cleanVal;
            }
        } else {
            int lastSpace = cur.lastIndexOf(' ');
            if (cur.endsWith(" ") || lastSpace < 0) {
                next = cur.trim().isEmpty() ? insert + " " : cur.trim() + " " + insert + " ";
            } else {
                next = cur.substring(0, lastSpace + 1) + insert + " ";
            }
        }

        this.orderInput.setValue(next);
        this.orderInput.moveCursorToEnd();
        this.currentOrderText = next;
        updatePlanPreview(next);

        if ("DESTINATION".equals(sugg.category())) {
            if (this.suggestionDropdown != null) {
                this.suggestionDropdown.close();
            }
        } else {
            updateSuggestions(next);
        }
    }

    private void updateSuggestions(String text) {
        if (selectedTab != 3 || suggestionDropdown == null) return;
        if (orderInput == null || !orderInput.isFocused() || text == null || text.trim().isEmpty()) {
            suggestionDropdown.close();
            return;
        }
        List<com.aicompanion.ai.parser.ItemSearchRegistry.SmartSuggestion> suggestions =
                com.aicompanion.ai.parser.ItemSearchRegistry.getSmartSuggestions(text, 15);
        if (suggestions.isEmpty()) {
            suggestionDropdown.close();
        } else {
            suggestionDropdown.setSuggestions(suggestions);
        }
    }

    private void applyConstructionSuggestion(com.aicompanion.ai.parser.ItemSearchRegistry.SmartSuggestion sugg) {
        if (this.materialInput == null || sugg == null) return;
        String val = sugg.insertText();
        this.materialInput.setValue(val);
        this.materialInput.moveCursorToEnd();
        this.customMaterialText = val;
        if (this.constructionDropdown != null) {
            this.constructionDropdown.close();
        }
    }

    private void updateConstructionSuggestions(String text) {
        if (selectedTab != 5 || constructionDropdown == null) return;
        if (materialInput == null || !materialInput.isFocused()) {
            constructionDropdown.close();
            return;
        }
        List<com.aicompanion.ai.parser.ItemSearchRegistry.SmartSuggestion> suggestions =
                com.aicompanion.ai.parser.ItemSearchRegistry.getMaterialSuggestions(text != null ? text : "");
        if (suggestions.isEmpty()) {
            constructionDropdown.close();
        } else {
            constructionDropdown.setSuggestions(suggestions);
        }
    }

    private String getCombinedOrderText() {
        if (this.orderInput == null) return "";
        return this.orderInput.getValue().trim();
    }

    private void updatePlanPreview(String text) {
        updateSuggestions(text);
        if (text == null || text.trim().isEmpty()) {
            interpretedPlanTarget = "Esperando orden...";
            interpretedPlanSteps.clear();
            interpretedPlanSteps.add("• 1° Acción (minar, talar, cazar...) 2° Qué 3° Cantidad 4° Destino.");
            interpretedPlanSteps.add("• Escribe directamente o busca en el catálogo [ 🔍 ]");
            currentOrderBadges.clear();
            return;
        }
        String combined = getCombinedOrderText();
        interpretedPlanTarget = combined;
        interpretedPlanSteps.clear();
        updateOrderBadges(combined);
        try {
            var goals = com.aicompanion.ai.parser.TaskParser.parse(combined);
            if (goals.isEmpty()) {
                interpretedPlanSteps.add("• Analizando orden...");
                interpretedPlanSteps.add("• Se enviará al planificador de tareas.");
            } else {
                for (int i = 0; i < goals.size(); i++) {
                    var g = goals.get(i);
                    interpretedPlanSteps.add((i + 1) + ". " + com.aicompanion.ai.parser.TaskParser.formatGoalDescription(g));
                }
            }
        } catch (Exception e) {
            interpretedPlanSteps.add("• Plan: " + combined);
        }
    }

    private void updateOrderBadges(String text) {
        currentOrderBadges.clear();
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        List<Goal> goals = new ArrayList<>();
        try {
            goals = TaskParser.parse(text);
        } catch (Exception ignored) {}

        String norm = NormalizedOrderText.foldAccents(text).toLowerCase();

        // 1. ACTION BADGE
        if (!goals.isEmpty()) {
            Goal firstGoal = goals.get(0);
            switch (firstGoal.getType()) {
                case OBTAIN -> {
                    if (norm.matches(".*\\b(?:mina|minar|pica|picar|oro|diamante|hierro|carbon|cobre|redstone|esmeralda|lapislazuli|netherita)\\b.*")) {
                        currentOrderBadges.add(new OrderBadge("⛏ MINAR", 0xDD0D47A1, 0xFF2196F3, 0xFF90CAF9));
                    } else if (norm.matches(".*\\b(?:tala|talar|corta|cortar|madera|roble|abedul|abeto|arbol|arboles)\\b.*")) {
                        currentOrderBadges.add(new OrderBadge("🌲 TALAR", 0xDD1B5E20, 0xFF4CAF50, 0xFFA5D6A7));
                    } else {
                        currentOrderBadges.add(new OrderBadge("📦 CONSEGUIR", 0xDD4E342E, 0xFFFFB300, 0xFFFFE082));
                    }
                }
                case KILL -> currentOrderBadges.add(new OrderBadge("⚔ CAZAR", 0xDDB71C1C, 0xFFE53935, 0xFFFFCDD2));
                case FOLLOW -> currentOrderBadges.add(new OrderBadge("🏃 SEGUIR", 0xDD004D40, 0xFF00E676, 0xFFB9F6CA));
                case STAY -> currentOrderBadges.add(new OrderBadge("🛑 ESPERAR", 0xDD37474F, 0xFF90A4AE, 0xFFECEFF1));
                case GO_TO -> currentOrderBadges.add(new OrderBadge("🧭 VIAJAR", 0xDD006064, 0xFF00ACC1, 0xFFB2EBF2));
                case BUILD -> currentOrderBadges.add(new OrderBadge("🏗 CONSTRUIR", 0xDD4A148C, 0xFFAB47BC, 0xFFE1BEE7));
                case DEPOSIT -> currentOrderBadges.add(new OrderBadge("📦 DEPOSITAR", 0xDD4E342E, 0xFFFFB300, 0xFFFFE082));
                case RETURN_HOME -> currentOrderBadges.add(new OrderBadge("🏠 BASE", 0xDD004D40, 0xFF00E676, 0xFFB9F6CA));
                case RETURN_OWNER -> currentOrderBadges.add(new OrderBadge("🎁 TRAER A MÍ", 0xDD004D40, 0xFF00E676, 0xFFB9F6CA));
                default -> currentOrderBadges.add(new OrderBadge("⚙ ORDEN", 0xDD263238, 0xFF78909C, 0xFFCFD8DC));
            }
        } else {
            if (norm.matches(".*\\b(?:mina|minar|pica|picar)\\b.*")) {
                currentOrderBadges.add(new OrderBadge("⛏ MINAR", 0xDD0D47A1, 0xFF2196F3, 0xFF90CAF9));
            } else if (norm.matches(".*\\b(?:tala|talar|corta|cortar)\\b.*")) {
                currentOrderBadges.add(new OrderBadge("🌲 TALAR", 0xDD1B5E20, 0xFF4CAF50, 0xFFA5D6A7));
            } else if (norm.matches(".*\\b(?:caza|cazar|mata|matar)\\b.*")) {
                currentOrderBadges.add(new OrderBadge("⚔ CAZAR", 0xDDB71C1C, 0xFFE53935, 0xFFFFCDD2));
            } else if (norm.matches(".*\\b(?:ve\\s+a|viaja|ir)\\b.*")) {
                currentOrderBadges.add(new OrderBadge("🧭 VIAJAR", 0xDD006064, 0xFF00ACC1, 0xFFB2EBF2));
            } else if (norm.matches(".*\\b(?:consigue|conseguir|busca|buscar)\\b.*")) {
                currentOrderBadges.add(new OrderBadge("📦 CONSEGUIR", 0xDD4E342E, 0xFFFFB300, 0xFFFFE082));
            }
        }

        // 2. TARGET / ITEM BADGE
        String targetName = null;
        for (Goal g : goals) {
            if (g.getTargetResource() != null) {
                targetName = formatResourceBadge(g.getTargetResource().toString());
                break;
            }
        }
        if (targetName == null) {
            targetName = findTargetNameFromText(norm);
        }
        if (targetName != null && !targetName.isEmpty()) {
            currentOrderBadges.add(new OrderBadge(" " + targetName.toUpperCase() + " ", 0xDD5D4037, 0xFFFFB300, 0xFFFFD54F));
        }

        // 3. QUANTITY BADGE
        int qty = 0;
        for (Goal g : goals) {
            if (g.getAmount() > 0) {
                qty = g.getAmount();
                break;
            }
        }
        if (qty <= 0) {
            qty = findQuantityFromText(norm);
        }
        if (qty > 0) {
            currentOrderBadges.add(new OrderBadge("CANT: " + qty, 0xDD3E2723, 0xFFFFCA28, 0xFFFFF59D));
        }

        // 4. DESTINATION / FOLLOW-UP ACTION BADGE
        boolean hasDelivery = false;
        for (Goal g : goals) {
            if (g.getType() == Goal.Type.RETURN_OWNER) {
                currentOrderBadges.add(new OrderBadge("🎁 TRAER A MÍ", 0xDD004D40, 0xFF00E676, 0xFFB9F6CA));
                hasDelivery = true;
                break;
            } else if (g.getType() == Goal.Type.DEPOSIT) {
                currentOrderBadges.add(new OrderBadge("📦 EN COFRE", 0xDD004D40, 0xFF00E676, 0xFFB9F6CA));
                hasDelivery = true;
                break;
            } else if (g.getType() == Goal.Type.RETURN_HOME) {
                currentOrderBadges.add(new OrderBadge("🏠 A BASE", 0xDD004D40, 0xFF00E676, 0xFFB9F6CA));
                hasDelivery = true;
                break;
            }
        }
        if (!hasDelivery) {
            if (norm.matches(".*\\b(?:traer|trae|traemelo|traemelos|traelos|a\\s+mi|conmigo)\\b.*")) {
                currentOrderBadges.add(new OrderBadge("🎁 TRAER A MÍ", 0xDD004D40, 0xFF00E676, 0xFFB9F6CA));
            } else if (norm.matches(".*\\b(?:baul|vaul|cofre|guardar|guardes|depositar|deposita)\\b.*")) {
                currentOrderBadges.add(new OrderBadge("📦 EN COFRE", 0xDD004D40, 0xFF00E676, 0xFFB9F6CA));
            } else if (norm.matches(".*\\b(?:base|casa)\\b.*")) {
                currentOrderBadges.add(new OrderBadge("🏠 A BASE", 0xDD004D40, 0xFF00E676, 0xFFB9F6CA));
            } else if (norm.matches(".*\\b(?:sigueme|seguir)\\b.*")) {
                currentOrderBadges.add(new OrderBadge("🏃 SEGUIRME", 0xDD004D40, 0xFF00E676, 0xFFB9F6CA));
            } else if (norm.matches(".*\\b(?:inventario|conservar)\\b.*")) {
                currentOrderBadges.add(new OrderBadge("🎒 EN INVENTARIO", 0xDD263238, 0xFF78909C, 0xFFCFD8DC));
            }
        }

        // 5. READINESS BADGE
        if (!goals.isEmpty()) {
            currentOrderBadges.add(new OrderBadge("✓ LISTO", 0xDD1B5E20, 0xFF00E676, 0xFFFFFFFF));
        } else {
            currentOrderBadges.add(new OrderBadge("◆ FALTA INFORMACIÓN", 0xDD4E342E, 0xFFFF9800, 0xFFFFCC80));
        }
    }

    private String formatResourceBadge(String resourceId) {
        if (resourceId == null) return "";
        return switch (resourceId) {
            case "minecraft:diamond_ore", "minecraft:diamond" -> "DIAMANTE";
            case "minecraft:gold_ore", "minecraft:raw_gold", "minecraft:gold_ingot" -> "ORO";
            case "minecraft:iron_ore", "minecraft:raw_iron", "minecraft:iron_ingot" -> "HIERRO";
            case "minecraft:coal_ore", "minecraft:coal" -> "CARBÓN";
            case "minecraft:copper_ore", "minecraft:raw_copper" -> "COBRE";
            case "minecraft:redstone_ore", "minecraft:redstone" -> "REDSTONE";
            case "minecraft:emerald_ore", "minecraft:emerald" -> "ESMERALDA";
            case "minecraft:lapis_ore", "minecraft:lapis_lazuli" -> "LAPISLÁZULI";
            case "minecraft:ancient_debris", "minecraft:netherite_ingot" -> "NETHERITA";
            case "minecraft:glowstone" -> "PIEDRA LUMINOSA";
            case "minecraft:glowstone_dust" -> "POLVO LUMINOSO";
            case "minecraft:nether_quartz_ore", "minecraft:quartz" -> "CUARZO";
            case "minecraft:oak_log" -> "MADERA";
            case "minecraft:birch_log" -> "ABEDUL";
            case "minecraft:spruce_log" -> "ABETO";
            case "minecraft:dark_oak_log" -> "ROBLE OSCURO";
            case "minecraft:jungle_log" -> "JUNGLA";
            case "minecraft:acacia_log" -> "ACACIA";
            case "minecraft:cherry_log" -> "CEREZO";
            case "minecraft:stone" -> "PIEDRA";
            case "minecraft:cobblestone" -> "ADOQUÍN";
            case "minecraft:deepslate" -> "PIZARRA";
            case "minecraft:obsidian" -> "OBSIDIANA";
            case "minecraft:creeper" -> "CREEPER";
            case "minecraft:zombie" -> "ZOMBIE";
            case "minecraft:skeleton" -> "ESQUELETO";
            case "minecraft:spider" -> "ARAÑA";
            case "minecraft:cow" -> "VACA";
            case "minecraft:sheep" -> "OVEJA";
            case "minecraft:pig" -> "CERDO";
            case "minecraft:chicken" -> "GALLINA";
            case "minecraft:ink_sac" -> "TINTA";
            default -> {
                ResourceLocation rl = ResourceLocation.tryParse(resourceId);
                if (rl != null) {
                    String disp = com.aicompanion.ai.parser.ItemSearchRegistry.getDisplayName(rl);
                    if (disp != null && !disp.trim().isEmpty()) {
                        yield disp.toUpperCase();
                    }
                }
                String path = resourceId.contains(":") ? resourceId.substring(resourceId.indexOf(':') + 1) : resourceId;
                yield path.replace('_', ' ').toUpperCase();
            }
        };
    }

    private String findTargetNameFromText(String norm) {
        String[] ores = {"diamante", "oro", "hierro", "carbon", "cobre", "redstone", "esmeralda", "lapislazuli", "netherita", "madera", "roble", "abedul", "abeto", "piedra", "adoquin", "pizarra", "obsidiana", "creeper", "zombie", "esqueleto", "tinta"};
        for (String o : ores) {
            if (norm.matches(".*\\b" + o + "\\b.*")) {
                return o.toUpperCase();
            }
        }
        return null;
    }

    private int findQuantityFromText(String norm) {
        var m = java.util.regex.Pattern.compile("\\b(\\d+)\\b").matcher(norm);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return 0;
    }

    private void drawBadge(GuiGraphics guiGraphics, int x, int y, String text, int bgColor, int borderColor, int textColor) {
        int textW = this.font.width(text);
        int badgeW = textW + 8;
        int badgeH = 11;
        guiGraphics.fill(x, y, x + badgeW, y + badgeH, bgColor);
        guiGraphics.renderOutline(x, y, badgeW, badgeH, borderColor);
        guiGraphics.drawString(this.font, text, x + 4, y + 2, textColor, false);
    }

    private void executeOrderInput() {
        submitOrder(OrderSubmitMode.REPLACE_CURRENT);
    }

    private void submitOrder(OrderSubmitMode mode) {
        if (this.orderInput == null || this.isSubmitting) return;
        String raw = this.orderInput.getValue().trim();
        if (raw.isEmpty()) return;

        String lower = raw.toLowerCase();
        if (lower.equals("madera") || lower.equals("talar") || lower.equals("talar madera") || lower.equals("quiero madera")) {
            openModal(ModalType.CHOP);
            return;
        }
        if (lower.equals("minar") || lower.equals("mineral") || lower.equals("oro") || lower.equals("hierro") || lower.equals("diamante")) {
            if (lower.equals("oro")) selectedOreIdx = 1;
            else if (lower.equals("hierro")) selectedOreIdx = 0;
            else if (lower.equals("diamante")) selectedOreIdx = 2;
            openModal(ModalType.MINE);
            return;
        }
        if (lower.equals("construir") || lower.equals("casa") || lower.equals("refugio")) {
            switchTab(5); // Switch to CONSTRUCCIÓN
            return;
        }

        String text = getCombinedOrderText();
        if (text.isEmpty()) return;

        if (this.suggestionDropdown != null) {
            this.suggestionDropdown.close();
        }

        int reqId = ++nextRequestId;
        this.pendingSubmitRequestId = reqId;
        this.submitRequestTimestamp = System.currentTimeMillis();
        this.isSubmitting = true;
        this.submitStatusText = "Enviando orden al servidor...";
        this.submitStatusColor = com.aicompanion.client.gui.EsclabUiColors.BADGE_BUSY;

        if (this.executeBtn != null) {
            this.executeBtn.setMessage(Component.translatable("gui.aicompanion.order.sending"));
            this.executeBtn.active = false;
        }

        CompanionEntity companion = menu.getCompanion();
        int entityId = companion != null ? companion.getId() : (syncedData != null ? syncedData.entityId : 0);
        UUID compUUID = companionUUID != null ? companionUUID : (menu.getCompanionUUID() != null ? menu.getCompanionUUID() : (syncedData != null ? syncedData.companionUUID : null));
        CompanionNetworking.CHANNEL.sendToServer(new C2S_SubmitOrderPacket(reqId, entityId, compUUID, text, mode));
    }

    public void handleOrderResult(S2C_OrderResultPacket packet) {
        if (packet == null) return;
        this.isSubmitting = false;

        if (this.executeBtn != null) {
            this.executeBtn.setMessage(Component.translatable("gui.aicompanion.order.execute"));
            this.executeBtn.active = true;
        }

        OrderResultStatus status = packet.getStatus();
        if (status == OrderResultStatus.ACCEPTED || status == OrderResultStatus.STARTED || status == OrderResultStatus.QUEUED) {
            this.submitStatusText = "✓ " + (packet.getMessage().isEmpty() ? "Orden aceptada" : packet.getMessage());
            this.submitStatusColor = com.aicompanion.client.gui.EsclabUiColors.SUCCESS_GREEN;
            if (this.orderInput != null) {
                this.orderInput.setValue("");
            }
            this.currentOrderText = "";
            this.serverPreviewRows.clear();
            this.serverGoalSummary = "";
            this.serverIntentSummary = "";
            this.pendingSubmitRequestId = -1;
            switchTab(2); // Switch to ACTUAL tab so player immediately watches execution
        } else {
            // Rejected or Error: keep input intact so user does not lose text!
            this.submitStatusText = "✕ " + (packet.getMessage().isEmpty() ? "Orden no comprendida o rechazada" : packet.getMessage());
            this.submitStatusColor = com.aicompanion.client.gui.EsclabUiColors.BADGE_ERROR;
            if (this.orderInput != null) {
                this.orderInput.setFocused(true);
                this.setFocused(this.orderInput);
            }
        }
    }

    public void handleOrderPreview(S2C_OrderPreviewPacket packet) {
        if (packet == null) return;
        this.serverPlanValid = packet.isValid();
        this.serverGoalSummary = packet.getGoalSummary();
        this.serverIntentSummary = packet.getIntentSummary();
        this.serverPreviewRows.clear();
        if (packet.getRows() != null) {
            this.serverPreviewRows.addAll(packet.getRows());
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.orderInput != null) {
            this.orderInput.tick();
        }
        if (this.materialInput != null) {
            this.materialInput.tick();
        }
        if (this.selectedTab == 5 && this.buildingStudioView != null) {
            this.buildingStudioView.tick();
        }
        if (this.catalogSearchInput != null && this.activeModal == ModalType.ITEM_CATALOG) {
            this.catalogSearchInput.tick();
        }

        // Submission timeout fallback (5 seconds)
        if (isSubmitting && System.currentTimeMillis() - submitRequestTimestamp > 5000) {
            isSubmitting = false;
            submitStatusText = "✕ Sin respuesta del servidor (tiempo agotado)";
            submitStatusColor = com.aicompanion.client.gui.EsclabUiColors.BADGE_ERROR;
            if (this.executeBtn != null) {
                this.executeBtn.setMessage(Component.translatable("gui.aicompanion.order.execute"));
                this.executeBtn.active = true;
            }
        }

        // Debounced preview request to server (300ms)
        if (!isSubmitting && lastPreviewTypeTime > 0 && System.currentTimeMillis() - lastPreviewTypeTime >= 300) {
            lastPreviewTypeTime = 0;
            String text = getCombinedOrderText();
            if (!text.equals(lastRequestedPreviewText)) {
                lastRequestedPreviewText = text;
                if (!text.isEmpty()) {
                    int reqId = ++nextRequestId;
                    lastPreviewRequestId = reqId;
                    CompanionEntity companion = menu.getCompanion();
                    int entityId = companion != null ? companion.getId() : (syncedData != null ? syncedData.entityId : 0);
                    UUID compUUID = companionUUID != null ? companionUUID : (menu.getCompanionUUID() != null ? menu.getCompanionUUID() : (syncedData != null ? syncedData.companionUUID : null));
                    CompanionNetworking.CHANNEL.sendToServer(new C2S_RequestOrderPreviewPacket(reqId, entityId, compUUID, text));
                } else {
                    serverPreviewRows.clear();
                    serverGoalSummary = "";
                    serverIntentSummary = "";
                }
            }
        }
    }

    public boolean isAnyTextInputFocused() {
        if (this.activeModal != ModalType.NONE) {
            if (this.activeModal == ModalType.GOTO) {
                if (this.gotoXInput != null && this.gotoXInput.isFocused()) return true;
                if (this.gotoYInput != null && this.gotoYInput.isFocused()) return true;
                if (this.gotoZInput != null && this.gotoZInput.isFocused()) return true;
            }
            if (this.activeModal == ModalType.ITEM_CATALOG) {
                if (this.catalogSearchInput != null && this.catalogSearchInput.isFocused()) return true;
            }
            return false;
        }
        if (this.selectedTab == 1 && this.nameInput != null && this.nameInput.isFocused()) {
            return true;
        }
        if (this.selectedTab == 3 && this.orderInput != null && this.orderInput.isFocused()) {
            return true;
        }
        if (this.selectedTab == 5) {
            if (this.buildingStudioView != null && this.buildingStudioView.isSearchFocused()) return true;
            if (this.materialInput != null && this.materialInput.isFocused()) return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.activeModal != ModalType.NONE) {
            if (this.activeModal == ModalType.GOTO) {
                if (this.gotoXInput != null && this.gotoXInput.isFocused()) {
                    this.gotoXInput.charTyped(codePoint, modifiers);
                    return true;
                }
                if (this.gotoYInput != null && this.gotoYInput.isFocused()) {
                    this.gotoYInput.charTyped(codePoint, modifiers);
                    return true;
                }
                if (this.gotoZInput != null && this.gotoZInput.isFocused()) {
                    this.gotoZInput.charTyped(codePoint, modifiers);
                    return true;
                }
            }
            if (this.activeModal == ModalType.ITEM_CATALOG) {
                if (this.catalogSearchInput != null && this.catalogSearchInput.isFocused()) {
                    this.catalogSearchInput.charTyped(codePoint, modifiers);
                    return true;
                }
            }
            return true; // Modal is open: block input from reaching background tabs
        }

        if (this.selectedTab == 1 && this.nameInput != null && this.nameInput.isFocused()) {
            this.nameInput.charTyped(codePoint, modifiers);
            return true;
        }
        if (this.selectedTab == 3 && this.orderInput != null && this.orderInput.isFocused()) {
            this.orderInput.charTyped(codePoint, modifiers);
            return true;
        }
        if (this.selectedTab == 5) {
            if (this.buildingStudioView != null && this.buildingStudioView.charTyped(codePoint, modifiers)) {
                return true;
            }
            if (this.materialInput != null && this.materialInput.isFocused()) {
                this.materialInput.charTyped(codePoint, modifiers);
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Priority 1: Handle active modals first and isolate completely from background
        if (this.activeModal != ModalType.NONE) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                closeModal();
                return true;
            }
            if (this.activeModal == ModalType.ITEM_CATALOG) {
                if (this.catalogSearchInput != null && this.catalogSearchInput.isFocused()) {
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                        return true;
                    }
                    this.catalogSearchInput.keyPressed(keyCode, scanCode, modifiers);
                    return true;
                }
            }
            if (this.activeModal == ModalType.GOTO) {
                if (this.gotoXInput != null && this.gotoXInput.isFocused()) {
                    this.gotoXInput.keyPressed(keyCode, scanCode, modifiers);
                    return true;
                }
                if (this.gotoYInput != null && this.gotoYInput.isFocused()) {
                    this.gotoYInput.keyPressed(keyCode, scanCode, modifiers);
                    return true;
                }
                if (this.gotoZInput != null && this.gotoZInput.isFocused()) {
                    this.gotoZInput.keyPressed(keyCode, scanCode, modifiers);
                    return true;
                }
            }
            return true; // Block hotbar or other key shortcuts while modal is open
        }

        // Tab 1 Name Input
        if (this.selectedTab == 1 && this.nameInput != null && this.nameInput.isFocused()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                String newName = nameInput.getValue().trim();
                if (!newName.isEmpty()) {
                    sendAction(C2S_ActionPacket.Action.SET_NAME, newName);
                    nameFeedbackText = "¡Nombre cambiado a: " + newName + "!";
                    nameFeedbackColor = 0xFF3CD058;
                }
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                this.nameInput.setFocused(false);
                this.setFocused(null);
                return true;
            }
            this.nameInput.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }

        // Dropdown navigation in Tab 3
        if (this.selectedTab == 3 && this.suggestionDropdown != null && this.suggestionDropdown.isOpen()) {
            if (this.suggestionDropdown.handleKeyPressed(keyCode)) {
                return true;
            }
        }

        // Tab 3 Order Input
        if (this.selectedTab == 3 && this.orderInput != null && this.orderInput.isFocused()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                executeOrderInput();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                this.orderInput.setFocused(false);
                if (this.suggestionDropdown != null) this.suggestionDropdown.close();
                this.setFocused(null);
                return true;
            }
            this.orderInput.keyPressed(keyCode, scanCode, modifiers);
            // CRITICAL: Consume key while typing so typing 'e', hotbar numbers, or inventory shortcuts NEVER close GUI!
            return true;
        }

        // Tab 5 Construction Material Input & Dropdown
        if (this.selectedTab == 5) {
            if (this.buildingStudioView != null && this.buildingStudioView.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (this.constructionDropdown != null && this.constructionDropdown.isOpen()) {
                if (this.constructionDropdown.handleKeyPressed(keyCode)) {
                    return true;
                }
            }
            if (this.materialInput != null && this.materialInput.isFocused()) {
                if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                    this.materialInput.setFocused(false);
                    if (this.constructionDropdown != null) this.constructionDropdown.close();
                    this.setFocused(null);
                    return true;
                }
                this.materialInput.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
        }

        // When NO text input is focused, allow standard container screen close (E key, ESC, etc.)
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.activeModal == ModalType.ITEM_CATALOG) {
            int totalPages = Math.max(1, (int) Math.ceil((double) catalogResults.size() / CATALOG_PAGE_SIZE));
            if (delta < 0) {
                if (catalogPage < totalPages - 1) {
                    catalogPage++;
                    return true;
                }
            } else if (delta > 0) {
                if (catalogPage > 0) {
                    catalogPage--;
                    return true;
                }
            }
            return true;
        }
        if (this.selectedTab == 5 && this.buildingStudioView != null) {
            if (this.buildingStudioView.mouseScrolled(mouseX, mouseY, delta, this.leftPos, this.topPos + 48)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    public com.aicompanion.network.packets.S2C_SyncCompanionDataPacket getSyncedData() {
        return syncedData;
    }

    public void updateSyncedData(S2C_SyncCompanionDataPacket data) {
        this.syncedData = data;
        if (data != null) {
            if (data.companionUUID != null) {
                this.companionUUID = data.companionUUID;
            }
            this.cfgAutoFollow = data.autoFollow;
            this.cfgAutoEquip = data.autoEquip;
            this.cfgAutoEat = data.autoEat;
            this.cfgAutoDeliver = data.autoDeliver;
            this.cfgAutoReturn = data.autoReturn;
            this.cfgBlockBreak = data.allowBlockBreaking;
            this.cfgBlockPlace = data.allowBlockPlacement;
            this.cfgCombat = data.allowCombat;
            this.cfgChunkLoad = data.enableChunkLoading;
            if (this.selectedTab == 1 && this.nameInput != null && !this.nameInput.isFocused() && data.customName != null && !data.customName.isEmpty()) {
                this.nameInput.setValue(data.customName);
            }
            if (this.selectedTab == 9) {
                this.init();
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.menu.setTabActive(this.selectedTab);

        int left = this.leftPos;
        int top = this.topPos;

        // 1. Two rows of 5 Tabs across the top (width 82 each, total 5 * 84 = 420 px)
        int tabW = 82;
        int tabH = 14;
        for (int i = 0; i < 5; i++) {
            final int tabIndex = i;
            this.addRenderableWidget(new EsclabTabButton(
                    left + 5 + i * (tabW + 2), top + 16, tabW, tabH,
                    Component.translatable(TAB_KEYS[i]), tabIndex, selectedTab == tabIndex,
                    b -> switchTab(tabIndex)
            ));
        }
        for (int i = 5; i < 10; i++) {
            final int tabIndex = i;
            this.addRenderableWidget(new EsclabTabButton(
                    left + 5 + (i - 5) * (tabW + 2), top + 32, tabW, tabH,
                    Component.translatable(TAB_KEYS[i]), tabIndex, selectedTab == tabIndex,
                    b -> switchTab(tabIndex)
            ));
        }

        CompanionEntity companion = menu.getCompanion();
        int panelTop = top + 48;

        if (this.buildingStudioView == null) {
            this.buildingStudioView = new com.aicompanion.client.gui.building.BuildingStudioView(this, this::sendAction);
        }

        // 2. Tab Specific Widgets
        if (selectedTab == 0) {
            // INVENTARIO: Quick buttons under the entity box
            this.addRenderableWidget(new EsclabButton(left + 12, panelTop + 130, 50, 16, Component.literal("Seguir"), b -> sendAction(C2S_ActionPacket.Action.FOLLOW)));
            this.addRenderableWidget(new EsclabButton(left + 66, panelTop + 130, 50, 16, Component.literal("Esperar"), b -> sendAction(C2S_ActionPacket.Action.STAY)));

            boolean canModify = menu.canModifyCompanionInventory(Minecraft.getInstance().player);
            EsclabButton btnOrganize = new EsclabButton(left + 194, top + 146, 162, 18, Component.literal("ORGANIZAR MOCHILA"), b -> sendAction(C2S_ActionPacket.Action.ORGANIZE_INVENTORY));
            btnOrganize.active = canModify;
            this.addRenderableWidget(btnOrganize);
        } else if (selectedTab == 1) {
            // NOMBRE: Rename companion
            String currentName = syncedData != null && !syncedData.customName.isEmpty() ? syncedData.customName : (companion != null ? companion.getCustomCompanionName() : "Esclavo");
            this.nameInput = new EditBox(this.font, left + 25, panelTop + 45, 230, 20, Component.literal("Nuevo nombre"));
            this.nameInput.setMaxLength(32);
            this.nameInput.setValue(currentName);
            this.nameInput.setResponder(s -> nameFeedbackText = "");
            this.addRenderableWidget(this.nameInput);

            this.addRenderableWidget(new EsclabButton(left + 265, panelTop + 45, 140, 20, Component.literal("GUARDAR NOMBRE"), b -> {
                String newName = nameInput.getValue().trim();
                if (!newName.isEmpty()) {
                    sendAction(C2S_ActionPacket.Action.SET_NAME, newName);
                    nameFeedbackText = "¡Nombre cambiado a: " + newName + "!";
                    nameFeedbackColor = 0xFF3CD058;
                }
            }));

            this.addRenderableWidget(new EsclabButton(left + 20, panelTop + 115, 135, 20, Component.literal("Restablecer (Esclavo)"), b -> {
                sendAction(C2S_ActionPacket.Action.SET_NAME, "Esclavo");
                this.nameInput.setValue("Esclavo");
                nameFeedbackText = "¡Nombre restablecido a Esclavo!";
                nameFeedbackColor = 0xFFFFD700;
            }));

            // Quick preset suggestions
            this.addRenderableWidget(new EsclabButton(left + 165, panelTop + 115, 75, 20, Component.literal("Centinela"), b -> {
                this.nameInput.setValue("Centinela");
                sendAction(C2S_ActionPacket.Action.SET_NAME, "Centinela");
                nameFeedbackText = "¡Nombre cambiado a: Centinela!";
                nameFeedbackColor = 0xFF3CD058;
            }));

            this.addRenderableWidget(new EsclabButton(left + 245, panelTop + 115, 75, 20, Component.literal("Minero"), b -> {
                this.nameInput.setValue("Minero");
                sendAction(C2S_ActionPacket.Action.SET_NAME, "Minero");
                nameFeedbackText = "¡Nombre cambiado a: Minero!";
                nameFeedbackColor = 0xFF3CD058;
            }));

            this.addRenderableWidget(new EsclabButton(left + 325, panelTop + 115, 80, 20, Component.literal("Compañero"), b -> {
                this.nameInput.setValue("Compañero");
                sendAction(C2S_ActionPacket.Action.SET_NAME, "Compañero");
                nameFeedbackText = "¡Nombre cambiado a: Compañero!";
                nameFeedbackColor = 0xFF3CD058;
            }));
        } else if (selectedTab == 2) {
            // MISIONES: Pausar, Reanudar, Cancelar, Regresar
            int btnY = panelTop + 115;
            this.addRenderableWidget(new EsclabButton(left + 20, btnY, 90, 20, Component.literal("PAUSAR"), b -> sendAction(C2S_ActionPacket.Action.PAUSE)));
            this.addRenderableWidget(new EsclabButton(left + 115, btnY, 90, 20, Component.literal("REANUDAR"), b -> sendAction(C2S_ActionPacket.Action.RESUME)));
            this.addRenderableWidget(new EsclabButton(left + 210, btnY, 90, 20, Component.literal("CANCELAR"), b -> sendAction(C2S_ActionPacket.Action.STOP)));
            this.addRenderableWidget(new EsclabButton(left + 305, btnY, 100, 20, Component.literal("REGRESAR"), b -> sendAction(C2S_ActionPacket.Action.RECALL)));
        } else if (selectedTab == 3) {
            // ÓRDENES: Natural Language Order Input + Catalog Button + Execute Button
            int inputY = panelTop + 14;

            // Natural Language Input Box (Full width)
            this.orderInput = new EditBox(this.font, left + 15, inputY, 282, 18, Component.literal("Escribe una orden..."));
            this.orderInput.setMaxLength(128);
            this.orderInput.setValue(currentOrderText);
            this.orderInput.setHint(Component.literal("Escribe una orden (ej: minar carbón 10 y traer a mí)..."));
            this.orderInput.setResponder(s -> {
                currentOrderText = s;
                submitStatusText = "";
                lastPreviewTypeTime = System.currentTimeMillis();
                updatePlanPreview(s);
            });
            this.addRenderableWidget(this.orderInput);
            this.orderInput.setFocused(true);
            this.setFocused(this.orderInput);

            // Catalog Button [ 🔍 ]
            this.addRenderableWidget(new EsclabButton(left + 301, inputY, 26, 18, Component.literal("🔍"), b -> openModal(ModalType.ITEM_CATALOG)));

            // Execute button with dynamic status
            Component executeLabel = isSubmitting
                    ? Component.translatable("gui.aicompanion.order.sending")
                    : Component.translatable("gui.aicompanion.order.execute");
            this.executeBtn = new EsclabButton(left + 331, inputY, 74, 18, executeLabel, b -> {
                if (!isSubmitting) {
                    submitOrder(OrderSubmitMode.REPLACE_CURRENT);
                }
            });
            this.executeBtn.active = !isSubmitting;
            this.addRenderableWidget(this.executeBtn);

            // Smart Full-Width Suggestion Dropdown (closed by default until typing)
            this.suggestionDropdown = new com.aicompanion.client.gui.widgets.EsclabSuggestionDropdown(
                    left + 15, inputY + 19, 282, this::applySmartSuggestion
            );
            this.addWidget(this.suggestionDropdown);
            this.suggestionDropdown.close();

            if (currentOrderText != null && !currentOrderText.trim().isEmpty()) {
                updatePlanPreview(currentOrderText);
            } else {
                interpretedPlanTarget = "Esperando orden...";
                interpretedPlanSteps.clear();
                interpretedPlanSteps.add("• 1° Acción (minar, talar, cazar...) 2° Qué 3° Cantidad 4° Destino.");
                interpretedPlanSteps.add("• Escribe para ver sugerencias guiadas.");
            }

            // Quick Action Buttons (Opening Modals!)
            int qy1 = panelTop + 104;
            int qy2 = panelTop + 126;
            this.addRenderableWidget(new EsclabButton(left + 15, qy1, 75, 18, Component.literal("MINAR..."), b -> openModal(ModalType.MINE)));
            this.addRenderableWidget(new EsclabButton(left + 95, qy1, 75, 18, Component.literal("TALAR..."), b -> openModal(ModalType.CHOP)));
            this.addRenderableWidget(new EsclabButton(left + 175, qy1, 75, 18, Component.literal("CAZAR..."), b -> openModal(ModalType.KILL)));
            this.addRenderableWidget(new EsclabButton(left + 255, qy1, 75, 18, Component.literal("IR A..."), b -> openModal(ModalType.GOTO)));
            this.addRenderableWidget(new EsclabButton(left + 335, qy1, 70, 18, Component.literal("A BASE"), b -> sendAction(C2S_ActionPacket.Action.HOME)));

            this.addRenderableWidget(new EsclabButton(left + 15, qy2, 75, 18, Component.literal("SEGUIR"), b -> sendAction(C2S_ActionPacket.Action.FOLLOW)));
            this.addRenderableWidget(new EsclabButton(left + 95, qy2, 75, 18, Component.literal("ESPERAR"), b -> sendAction(C2S_ActionPacket.Action.STAY)));
            this.addRenderableWidget(new EsclabButton(left + 175, qy2, 75, 18, Component.literal("VEN AQUÍ"), b -> sendAction(C2S_ActionPacket.Action.RECALL)));
            this.addRenderableWidget(new EsclabButton(left + 255, qy2, 75, 18, Component.literal("DEPOSITAR"), b -> sendAction(C2S_ActionPacket.Action.DEPOSIT)));
            this.addRenderableWidget(new EsclabButton(left + 335, qy2, 70, 18, Component.literal("BASE AQUÍ"), b -> sendAction(C2S_ActionPacket.Action.SET_HOME)));
        } else if (selectedTab == 4) {
            // COLA: Limpiar cola
            this.addRenderableWidget(new EsclabButton(left + 20, panelTop + 120, 130, 20, Component.literal("LIMPIAR COLA"), b -> sendAction(C2S_ActionPacket.Action.CLEAR_QUEUE)));
        } else if (selectedTab == 5) {
            // CONSTRUCCIÓN: Estudio de Construcción 3D & Schematics
            this.buildingStudioView.init(left, panelTop);
        } else if (selectedTab == 6) {
            // IA: Conexión y planificador
            String mode = com.aicompanion.AICompanionConfig.COMMON.aiMode.get();
            String btnText = "LOCAL_ONLY".equalsIgnoreCase(mode) ? "DIAGNÓSTICO MOTOR" : "PROBAR CONEXIÓN";
            this.addRenderableWidget(new EsclabButton(left + 20, panelTop + 115, 140, 20, Component.literal(btnText), b -> {
                connectionTestStatus = "LOCAL_ONLY".equalsIgnoreCase(mode)
                        ? "Motor Baritone 3D y Reglas: 100% OPERATIVO"
                        : "Comprobando endpoint...";
                sendAction(C2S_ActionPacket.Action.TEST_LLM_CONNECTION);
            }));
            this.addRenderableWidget(new EsclabButton(left + 170, panelTop + 115, 120, 20, Component.literal("REPLANIFICAR"), b -> sendAction(C2S_ActionPacket.Action.REPLAN)));
        } else if (selectedTab == 7) {
            // MEMORIA: Guardar base
            this.addRenderableWidget(new EsclabButton(left + 20, panelTop + 120, 180, 20, Component.literal("GUARDAR BASE AQUÍ"), b -> sendAction(C2S_ActionPacket.Action.SET_HOME)));
        } else if (selectedTab == 9) {
            // CONFIG: Toggles and Keybinds directly in tab
            int tx = left + 15;
            int ty = panelTop + 16;
            this.addRenderableWidget(new EsclabToggle(tx, ty, 190, 15, Component.literal("Auto-Seguir (Follow)"), cfgAutoFollow, v -> {
                cfgAutoFollow = v;
                sendAction(C2S_ActionPacket.Action.TOGGLE_CONFIG, "auto_follow");
            }));
            this.addRenderableWidget(new EsclabToggle(tx, ty + 16, 190, 15, Component.literal("Auto-Alimentación"), cfgAutoEat, v -> {
                cfgAutoEat = v;
                sendAction(C2S_ActionPacket.Action.TOGGLE_CONFIG, "auto_eat");
            }));
            this.addRenderableWidget(new EsclabToggle(tx, ty + 32, 190, 15, Component.literal("Auto-Equipar"), cfgAutoEquip, v -> {
                cfgAutoEquip = v;
                sendAction(C2S_ActionPacket.Action.TOGGLE_CONFIG, "auto_equip");
            }));
            this.addRenderableWidget(new EsclabToggle(tx, ty + 48, 190, 15, Component.literal("Romper Bloques"), cfgBlockBreak, v -> {
                cfgBlockBreak = v;
                sendAction(C2S_ActionPacket.Action.TOGGLE_CONFIG, "allow_block_breaking");
            }));

            int tx2 = left + 215;
            this.addRenderableWidget(new EsclabToggle(tx2, ty, 190, 15, Component.literal("Colocar Bloques"), cfgBlockPlace, v -> {
                cfgBlockPlace = v;
                sendAction(C2S_ActionPacket.Action.TOGGLE_CONFIG, "allow_block_placement");
            }));
            this.addRenderableWidget(new EsclabToggle(tx2, ty + 16, 190, 15, Component.literal("Combate Hostil"), cfgCombat, v -> {
                cfgCombat = v;
                sendAction(C2S_ActionPacket.Action.TOGGLE_CONFIG, "allow_combat");
            }));
            this.addRenderableWidget(new EsclabToggle(tx2, ty + 32, 190, 15, Component.literal("Carga de Chunks"), cfgChunkLoad, v -> {
                cfgChunkLoad = v;
                sendAction(C2S_ActionPacket.Action.TOGGLE_CONFIG, "chunk_loading");
            }));
            this.addRenderableWidget(new EsclabButton(tx2, ty + 48, 190, 15, Component.literal("⚠ DESINVOCAR ESCLAB"), b -> openModal(ModalType.DISMISS_CONFIRM)));

            // Keybindings section directly in CONFIG tab
            int kbY = panelTop + 94;
            EsclabKeybindButton btnMenu = new EsclabKeybindButton(left + 15, kbY, 165, 18, Component.literal("Abrir Menú"), ModKeyBindings.KEY_OPEN_GUI);
            this.addRenderableWidget(btnMenu);
            this.addRenderableWidget(new EsclabButton(left + 182, kbY, 18, 18, Component.literal("R"), b -> btnMenu.resetToDefault()));

            EsclabKeybindButton btnRecall = new EsclabKeybindButton(left + 215, kbY, 165, 18, Component.literal("Llamar"), ModKeyBindings.KEY_RECALL);
            this.addRenderableWidget(btnRecall);
            this.addRenderableWidget(new EsclabButton(left + 382, kbY, 18, 18, Component.literal("R"), b -> btnRecall.resetToDefault()));

            int kbY2 = panelTop + 116;
            EsclabKeybindButton btnHud = new EsclabKeybindButton(left + 15, kbY2, 165, 18, Component.literal("Alternar HUD"), ModKeyBindings.KEY_TOGGLE_HUD);
            this.addRenderableWidget(btnHud);
            this.addRenderableWidget(new EsclabButton(left + 182, kbY2, 18, 18, Component.literal("R"), b -> btnHud.resetToDefault()));

            // Button to advanced config screen
            this.addRenderableWidget(new EsclabButton(left + 215, kbY2, 185, 18, Component.literal("MÁS OPCIONES..."), b -> {
                Minecraft.getInstance().setScreen(new EsclabConfigScreen(this));
            }));

            // Button to HUD and Compass visual customization editor
            int kbY3 = panelTop + 138;
            EsclabButton btnCustomizeHud = new EsclabButton(left + 15, kbY3, 385, 20, Component.literal("🎨 PERSONALIZAR INTERFAZ (HUD Y BRÚJULA)..."), b -> {
                Minecraft.getInstance().setScreen(new com.aicompanion.client.gui.hud.HudCustomizeScreen(this));
            });
            btnCustomizeHud.setGoldSelected(true);
            this.addRenderableWidget(btnCustomizeHud);
        }
    }

    private void switchTab(int tabIndex) {
        this.selectedTab = tabIndex;
        this.menu.setTabActive(tabIndex);
        this.activeModal = ModalType.NONE;
        this.init();
    }

    private void closeModal() {
        this.activeModal = ModalType.NONE;
        if (this.selectedTab == 3 && this.orderInput != null) {
            this.orderInput.visible = true;
            this.orderInput.setFocused(true);
            this.setFocused(this.orderInput);
        }
        if (this.selectedTab == 3 && this.executeBtn != null) {
            this.executeBtn.visible = true;
        }
    }

    private void openModal(ModalType type) {
        this.activeModal = type;
        if (this.orderInput != null) {
            this.orderInput.setFocused(false);
            this.orderInput.visible = false;
        }
        if (this.executeBtn != null) {
            this.executeBtn.visible = false;
        }
        if (this.suggestionDropdown != null) {
            this.suggestionDropdown.close();
        }
        if (this.nameInput != null) {
            this.nameInput.setFocused(false);
            this.nameInput.visible = false;
        }
        if (this.materialInput != null) {
            this.materialInput.setFocused(false);
            this.materialInput.visible = false;
        }
        if (this.constructionDropdown != null) {
            this.constructionDropdown.close();
        }
        this.setFocused(null);

        if (type == ModalType.GOTO) {
            CompanionEntity companion = menu.getCompanion();
            int curX = companion != null ? companion.getBlockX() : 0;
            int curY = companion != null ? companion.getBlockY() : 64;
            int curZ = companion != null ? companion.getBlockZ() : 0;

            int modalLeft = this.leftPos + (this.imageWidth - 250) / 2;
            int modalTop = this.topPos + (this.imageHeight - 160) / 2;

            this.gotoXInput = new EditBox(this.font, modalLeft + 50, modalTop + 34, 150, 16, Component.literal("X"));
            this.gotoXInput.setValue(String.valueOf(curX));

            this.gotoYInput = new EditBox(this.font, modalLeft + 50, modalTop + 54, 150, 16, Component.literal("Y"));
            this.gotoYInput.setValue(String.valueOf(curY));

            this.gotoZInput = new EditBox(this.font, modalLeft + 50, modalTop + 74, 150, 16, Component.literal("Z"));
            this.gotoZInput.setValue(String.valueOf(curZ));
        } else if (type == ModalType.ITEM_CATALOG) {
            int mw = 340;
            int mh = 230;
            int modalLeft = this.leftPos + (this.imageWidth - mw) / 2;
            int modalTop = this.topPos + (this.imageHeight - mh) / 2;

            this.catalogPage = 0;
            this.catalogSearchInput = new EditBox(this.font, modalLeft + 16, modalTop + 24, mw - 32, 16, Component.literal("Buscar..."));
            this.catalogSearchInput.setMaxLength(64);
            this.catalogSearchInput.setHint(Component.literal("Buscar por nombre, mod o ID..."));
            this.catalogSearchInput.setValue("");
            this.catalogSearchInput.setResponder(s -> {
                this.catalogPage = 0;
                this.catalogResults = com.aicompanion.ai.parser.ItemSearchRegistry.searchCatalog(s);
            });
            this.catalogResults = com.aicompanion.ai.parser.ItemSearchRegistry.searchCatalog("");
            this.catalogSearchInput.setFocused(true);
            this.setFocused(this.catalogSearchInput);
        }
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> list = super.getTooltipFromContainerItem(stack);
        if (this.selectedTab == 0 && this.menu != null && !this.menu.canModifyCompanionInventory(Minecraft.getInstance().player)) {
            if (this.hoveredSlot != null && this.hoveredSlot.index < com.aicompanion.inventory.CompanionInventoryHandler.TOTAL_SLOTS) {
                List<Component> combined = new ArrayList<>(list);
                combined.add(Component.literal("§c[Acceso remoto: sólo lectura]"));
                return combined;
            }
        }
        return list;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Dynamically enforce widget visibility when modal is open to avoid background leaks
        boolean noModal = (activeModal == ModalType.NONE);
        if (this.orderInput != null) {
            this.orderInput.visible = (selectedTab == 3 && noModal);
        }
        if (this.executeBtn != null) {
            this.executeBtn.visible = (selectedTab == 3 && noModal);
        }
        if (this.nameInput != null) {
            this.nameInput.visible = (selectedTab == 1 && noModal);
        }
        if (this.materialInput != null) {
            this.materialInput.visible = (selectedTab == 5 && noModal);
        }

        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        // Tooltips for specific slots & badges
        if (selectedTab == 0 && activeModal == ModalType.NONE) {
            int left = this.leftPos;
            int top = this.topPos;
            int armorX = left + 124;
            int armorY = top + 64;
            int handsX = left + 158;
            int mainhandY = top + 74;
            int offhandY = top + 106;

            boolean remoteReadOnly = !menu.canModifyCompanionInventory(Minecraft.getInstance().player);
            boolean hoveringOccupiedSlot = (this.hoveredSlot != null && this.hoveredSlot.hasItem());

            // 1.13.59: Evitar solapamiento de tooltips. Si el cursor está sobre un slot ocupado,
            // getTooltipFromContainerItem ya renderiza la información del ítem limpiamente.
            if (!hoveringOccupiedSlot) {
                if (remoteReadOnly && (isInside(mouseX, mouseY, left + 120, top + 60, 245, 90) || isInside(mouseX, mouseY, left + 310, top + 52, 90, 16))) {
                    guiGraphics.renderTooltip(this.font, Component.literal("Acceso remoto: sólo lectura. Acércate a menos de 4 bloques para transferir objetos."), mouseX, mouseY);
                } else if (isInside(mouseX, mouseY, armorX, armorY, 18, 18)) {
                    guiGraphics.renderTooltip(this.font, Component.literal("Casco"), mouseX, mouseY);
                } else if (isInside(mouseX, mouseY, armorX, armorY + 24, 18, 18)) {
                    guiGraphics.renderTooltip(this.font, Component.literal("Pechera"), mouseX, mouseY);
                } else if (isInside(mouseX, mouseY, armorX, armorY + 48, 18, 18)) {
                    guiGraphics.renderTooltip(this.font, Component.literal("Pantalones"), mouseX, mouseY);
                } else if (isInside(mouseX, mouseY, armorX, armorY + 72, 18, 18)) {
                    guiGraphics.renderTooltip(this.font, Component.literal("Botas"), mouseX, mouseY);
                } else if (isInside(mouseX, mouseY, handsX, mainhandY, 18, 18)) {
                    guiGraphics.renderTooltip(this.font, Component.literal("Mano Principal (Arma / Herramienta)"), mouseX, mouseY);
                } else if (isInside(mouseX, mouseY, handsX, offhandY, 18, 18)) {
                    guiGraphics.renderTooltip(this.font, Component.literal("Mano Secundaria (Escudo / Antorcha)"), mouseX, mouseY);
                }
            }

            if (isInside(mouseX, mouseY, left + 14, top + 60, 80, 12)) {
                CompanionEntity comp = menu.getCompanion();
                float hp = comp != null ? comp.getHealth() : 20.0f;
                float maxHp = comp != null ? comp.getMaxHealth() : 20.0f;
                guiGraphics.renderTooltip(this.font, Component.literal("Vida: " + (int)hp + " / " + (int)maxHp), mouseX, mouseY);
            }
        }

        // Render suggestion dropdown on top of screen if open in Tab 3 or Tab 5
        if (noModal && selectedTab == 3 && suggestionDropdown != null && suggestionDropdown.isOpen()) {
            suggestionDropdown.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (noModal && selectedTab == 5 && constructionDropdown != null && constructionDropdown.isOpen()) {
            constructionDropdown.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // Render Active Modal Dialog if open
        if (activeModal != ModalType.NONE) {
            renderModal(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Suppress default AbstractContainerScreen title and inventory labels to prevent ghost text "Inventario"
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        // 1. Draw Gold Main Title
        String mainTitle = syncedData != null && !syncedData.customName.isEmpty() ? syncedData.customName.toUpperCase() : "ESCLAB";
        guiGraphics.drawString(this.font, mainTitle, left + 8, top + 4, 0xFFFFD700, true);

        // 2. Main Top Panel Background (430 x 152 or 256 for studio) at top + 48
        int panelTop = top + 48;
        int panelH = (selectedTab == 5) ? 256 : 152;
        drawCustomPanel(guiGraphics, left, panelTop, this.imageWidth, panelH);

        // 3. Tab-Specific Content Rendering (only when no modal is overlaying)
        if (activeModal == ModalType.NONE) {
            if (selectedTab == 0) {
                renderInventoryTab(guiGraphics, left, top, mouseX, mouseY);
            } else if (selectedTab == 1) {
                renderNameTab(guiGraphics, left, panelTop);
            } else if (selectedTab == 2) {
                renderActualTab(guiGraphics, left, panelTop);
            } else if (selectedTab == 3) {
                renderOrdersTab(guiGraphics, left, panelTop);
            } else if (selectedTab == 4) {
                renderQueueTab(guiGraphics, left, panelTop);
            } else if (selectedTab == 5) {
                renderConstructionTab(guiGraphics, left, panelTop, mouseX, mouseY);
            } else if (selectedTab == 6) {
                renderAITab(guiGraphics, left, panelTop);
            } else if (selectedTab == 7) {
                renderMemoryTab(guiGraphics, left, panelTop);
            } else if (selectedTab == 8) {
                renderStatsTab(guiGraphics, left, panelTop);
            } else if (selectedTab == 9) {
                renderConfigTab(guiGraphics, left, panelTop);
            }
        }
    }

    private void renderInventoryTab(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        CompanionEntity companion = menu.getCompanion();
        int panelTop = top + 48;

        // 1. Entity Preview Box (Left)
        int boxX = left + 10;
        int boxY = panelTop + 8;
        int boxW = 110;
        int boxH = 136;
        drawInsetBox(guiGraphics, boxX, boxY, boxW, boxH);

        // Hearts & Level
        float hp = companion != null ? companion.getHealth() : (syncedData != null ? syncedData.health : 20.0f);
        int lvl = companion != null ? companion.getCompanionLevel() : (syncedData != null ? syncedData.companionLevel : 1);

        int heartsToDraw = Math.min(10, (int) Math.ceil(hp / 2.0f));
        for (int h = 0; h < 10; h++) {
            int hx = boxX + 4 + h * 8;
            int hy = boxY + 5;
            if (h < heartsToDraw) {
                guiGraphics.blit(GUI_ICONS, hx, hy, 0, 32, 9, 9);
            } else {
                guiGraphics.blit(GUI_ICONS, hx, hy, 10, 32, 9, 9);
            }
        }
        guiGraphics.drawString(this.font, "Lvl " + lvl, boxX + 84, boxY + 6, 0xFFFFD700, false);

        // Status Badge
        String stateStr = companion != null ? companion.getCurrentState().name() : (syncedData != null ? syncedData.currentState : "IDLE");
        int statusColor = switch (stateStr) {
            case "FOLLOWING" -> 0xFF3CD058; // Green
            case "STAYING" -> 0xFFFFD700;   // Yellow
            case "MINING" -> 0xFFFFAA00;    // Gold
            case "ATTACKING", "COMBAT" -> 0xFFFF4040; // Red
            case "BUILDING" -> 0xFF00E5FF;  // Cyan
            case "STUCK" -> 0xFFFF1111;     // Intense Red
            default -> 0xFFA0A0A0;          // Gray
        };
        String statusDisplay = "[STATUS: " + stateStr + "]";
        int statusX = boxX + (boxW - this.font.width(statusDisplay)) / 2;
        guiGraphics.drawString(this.font, statusDisplay, statusX, boxY + 18, statusColor, false);

        // 3D Entity Model (Big and Centered)
        CompanionEntity compToRender = companion != null ? companion : getDummyCompanion();
        if (compToRender != null) {
            // Synchronize visual preview model equipment directly from menu slots (36..41)
            for (int i = 0; i < 6; i++) {
                Slot s = menu.slots.get(CompanionMenu.SLOT_ARMOR_START + i);
                if (s != null) {
                    EquipmentSlot eqSlot = switch (i) {
                        case 0 -> EquipmentSlot.HEAD;
                        case 1 -> EquipmentSlot.CHEST;
                        case 2 -> EquipmentSlot.LEGS;
                        case 3 -> EquipmentSlot.FEET;
                        case 4 -> EquipmentSlot.MAINHAND;
                        default -> EquipmentSlot.OFFHAND;
                    };
                    compToRender.setItemSlot(eqSlot, s.getItem());
                }
            }
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    guiGraphics, boxX + 55, boxY + 118, 44,
                    (boxX + 55) - mouseX, (boxY + 65) - mouseY, compToRender
            );
        }

        // 2. Armor and Hand Slots Wells with Ghost Icons & Headers (Image 1)
        int armorX = left + 122;
        int armorY = top + 64;
        guiGraphics.drawString(this.font, "EQUIPO", left + 113, top + 52, 0xFFFFD700, false);
        for (int i = 0; i < 4; i++) {
            int sy = armorY + i * 22;
            drawSlotWell(guiGraphics, armorX, sy);
            Slot slot = menu.slots.get(CompanionMenu.SLOT_ARMOR_START + i);
            if (slot != null && !slot.hasItem()) {
                guiGraphics.blit(GUI_ICONS, armorX + 1, sy + 1, i * 16, 0, 16, 16);
            }
        }

        int handsX = left + 162;
        int mainhandY = top + 74;
        int offhandY = top + 106;
        guiGraphics.drawString(this.font, "MANOS", left + 157, top + 52, 0xFFFFD700, false);
        drawSlotWell(guiGraphics, handsX, mainhandY);
        drawSlotWell(guiGraphics, handsX, offhandY);

        Slot mainSlot = menu.slots.get(CompanionMenu.SLOT_MAINHAND);
        if (mainSlot != null && !mainSlot.hasItem()) {
            guiGraphics.blit(GUI_ICONS, handsX + 1, mainhandY + 1, 64, 0, 16, 16);
        }
        Slot offSlot = menu.slots.get(CompanionMenu.SLOT_OFFHAND);
        if (offSlot != null && !offSlot.hasItem()) {
            guiGraphics.blit(GUI_ICONS, handsX + 1, offhandY + 1, 80, 0, 16, 16);
        }

        // 3. Companion Inventory Grid (Right)
        boolean remoteReadOnly = !menu.canModifyCompanionInventory(Minecraft.getInstance().player);
        int occupiedSlots = 0;
        for (int i = 0; i < 36 && i < menu.slots.size(); i++) {
            if (menu.slots.get(i).hasItem()) {
                occupiedSlots++;
            }
        }
        int capacityPct = (int) Math.round((occupiedSlots / 36.0) * 100.0);
        int capColor = 0xFF55FF55; // Green
        String capStatus = "Normal";
        if (occupiedSlots >= 36) {
            capColor = 0xFFFF5555; // Red
            capStatus = "Lleno";
        } else if (occupiedSlots >= 31) { // >= 85%
            capColor = 0xFFFFAA00; // Gold/Orange
            capStatus = "Crítico";
        } else if (occupiedSlots >= 25) { // >= 70%
            capColor = 0xFFFFFF55; // Yellow
            capStatus = "Alto";
        }

        if (remoteReadOnly) {
            guiGraphics.drawString(this.font, "Inventario (" + occupiedSlots + "/36)", left + 194, top + 56, 0xFFFFAA00, false);
            guiGraphics.drawString(this.font, "[SOLO LECTURA]", left + 315, top + 56, 0xFFFF5555, false);
        } else {
            guiGraphics.drawString(this.font, "Inventario (" + occupiedSlots + "/36)", left + 194, top + 56, 0xFFE0E8F2, false);
            guiGraphics.drawString(this.font, "[" + capStatus + " - " + capacityPct + "%]", left + 285, top + 56, capColor, false);
        }
        int compInvX = left + 194;
        int compInvY = top + 68;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 9; c++) {
                int sx = compInvX + c * 18;
                int sy = compInvY + r * 18;
                drawSlotWell(guiGraphics, sx, sy);
                if (remoteReadOnly) {
                    guiGraphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x22FF5555);
                }
            }
        }

        // 4. Player Inventory Panel (Bottom)
        int playerPanelX = left + 116;
        int playerPanelY = top + 204;
        drawCustomPanel(guiGraphics, playerPanelX, playerPanelY, 198, 106);
        guiGraphics.drawString(this.font, "Inventario", playerPanelX + 8, playerPanelY + 6, 0xFFE0E8F2, false);

        int playerInvX = left + 134;
        int playerInvY = top + 222;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                drawSlotWell(guiGraphics, playerInvX + c * 18, playerInvY + r * 18);
            }
        }
        int hotbarY = top + 282;
        for (int c = 0; c < 9; c++) {
            drawSlotWell(guiGraphics, playerInvX + c * 18, hotbarY);
        }
    }

    private void renderNameTab(GuiGraphics guiGraphics, int left, int panelTop) {
        String currentName = syncedData != null && !syncedData.customName.isEmpty() ? syncedData.customName : (menu.getCompanion() != null ? menu.getCompanion().getCustomCompanionName() : "Esclavo");
        guiGraphics.drawString(this.font, "PERSONALIZAR NOMBRE DE ESCLAVO", left + 20, panelTop + 14, 0xFFFFD700, false);
        guiGraphics.drawString(this.font, "Elige o escribe un nuevo nombre para tu compañero cuando quieras.", left + 20, panelTop + 28, 0xFFA0B0C4, false);

        drawInsetBox(guiGraphics, left + 20, panelTop + 40, 390, 68);
        guiGraphics.drawString(this.font, "Nombre actual: " + currentName, left + 30, panelTop + 72, 0xFFFFFFFF, false);

        if (!nameFeedbackText.isEmpty()) {
            guiGraphics.drawString(this.font, nameFeedbackText, left + 30, panelTop + 88, nameFeedbackColor, false);
        } else {
            guiGraphics.drawString(this.font, "El nuevo nombre se verá en su placa 3D, chat y menús del mod.", left + 30, panelTop + 88, 0xFF8AA8D4, false);
        }
    }

    private void renderActualTab(GuiGraphics guiGraphics, int left, int panelTop) {
        guiGraphics.drawString(this.font, "ESTADO ACTUAL Y PROGRESO DE MISIÓN", left + 20, panelTop + 10, 0xFFFFD700, false);

        drawInsetBox(guiGraphics, left + 20, panelTop + 22, 390, 88);

        String taskDesc = syncedData != null && !syncedData.currentTaskDesc.isEmpty() ? syncedData.currentTaskDesc : "En espera de órdenes";
        String taskProg = syncedData != null ? syncedData.currentTaskProgress : "0 / 0";
        String taskType = syncedData != null ? syncedData.currentTaskType : "IDLE";
        String taskStage = syncedData != null ? syncedData.currentTaskStage : "REPOSO";
        String taskSubstate = syncedData != null ? syncedData.currentTaskSubstate : "Esperando instrucciones";
        String taskFailure = syncedData != null ? syncedData.currentTaskFailureReason : "";
        String stateStr = syncedData != null ? syncedData.currentState : "IDLE";
        long lastProg = syncedData != null ? syncedData.lastProgressTick : 0L;

        String badge = "[EN PROGRESO]";
        int badgeColor = 0xFF3CD058;
        if ("PAUSED".equalsIgnoreCase(stateStr)) {
            badge = "[PAUSADO]";
            badgeColor = 0xFFFFD700;
        } else if ("STUCK".equalsIgnoreCase(stateStr) || !taskFailure.isEmpty()) {
            badge = "[ATASCADO / OBSTÁCULO]";
            badgeColor = 0xFFFF5555;
        } else if ("INVENTORY_FULL".equalsIgnoreCase(stateStr)) {
            badge = "[INVENTARIO LLENO]";
            badgeColor = 0xFFFFAA00;
        } else if ("IDLE".equalsIgnoreCase(stateStr)) {
            badge = "[EN REPOSO]";
            badgeColor = 0xFFA0B0C4;
        }

        guiGraphics.drawString(this.font, "Objetivo: " + taskDesc, left + 30, panelTop + 28, 0xFFFFFFFF, false);
        guiGraphics.drawString(this.font, badge, left + 265, panelTop + 28, badgeColor, false);

        guiGraphics.drawString(this.font, "Etapa: " + taskStage + " (" + taskType + ")", left + 30, panelTop + 40, 0xFFFFAA00, false);
        guiGraphics.drawString(this.font, "Actividad: " + taskSubstate, left + 30, panelTop + 51, 0xFF8AA8D4, false);

        // Progress bar
        int pbX = left + 30;
        int pbY = panelTop + 63;
        int pbW = 350;
        guiGraphics.fill(pbX, pbY, pbX + pbW, pbY + 7, 0xFF141820);
        guiGraphics.renderOutline(pbX, pbY, pbW, 7, 0xFF2A3444);

        float pct = 0.0f;
        int missingAmount = 0;
        if (taskProg.contains("/")) {
            try {
                String[] p1 = taskProg.split("/")[0].trim().split(" ");
                int cur = Integer.parseInt(p1[p1.length - 1]);
                String[] p2 = taskProg.split("/")[1].trim().split(" ");
                int total = Integer.parseInt(p2[0]);
                if (total > 0) {
                    pct = Math.min(1.0f, (float) cur / total);
                    missingAmount = Math.max(0, total - cur);
                }
            } catch (Exception ignored) {}
        }
        int fillW = (int) (pbW * pct);
        if (fillW > 0) {
            guiGraphics.fill(pbX + 1, pbY + 1, pbX + fillW, pbY + 6, 0xFF3CD058);
        }

        String progLabel = "Progreso Real: " + taskProg + (pct > 0 ? " (" + (int)(pct * 100) + "%)" : "");
        guiGraphics.drawString(this.font, progLabel, left + 30, panelTop + 73, 0xFF3CD058, false);

        // Contextual "Qué Falta" System
        if (!taskFailure.isEmpty()) {
            guiGraphics.drawString(this.font, "⚠ Bloqueado por: " + taskFailure, left + 30, panelTop + 85, 0xFFFF5555, false);
        } else if (missingAmount > 0) {
            guiGraphics.drawString(this.font, "Falta: " + missingAmount + " unidades físicas en inventario para terminar.", left + 30, panelTop + 85, 0xFFFFD700, false);
        } else if (lastProg > 0 && Minecraft.getInstance().level != null) {
            long ticksAgo = Math.max(0, Minecraft.getInstance().level.getGameTime() - lastProg);
            guiGraphics.drawString(this.font, "Última actividad: hace " + (ticksAgo / 20) + "s  |  Navegación Baritone 3D Activa", left + 30, panelTop + 85, 0xFFA0B0C4, false);
        } else {
            guiGraphics.drawString(this.font, "Esclavo está listo para recibir órdenes desde la pestaña ÓRDENES.", left + 30, panelTop + 85, 0xFFA0B0C4, false);
        }
    }

    private void renderOrdersTab(GuiGraphics guiGraphics, int left, int panelTop) {
        guiGraphics.drawString(this.font, "ÓRDENES POR LENGUAJE NATURAL Y CADENAS", left + 15, panelTop + 2, com.aicompanion.client.gui.EsclabUiColors.GOLD, false);

        int statusY = panelTop + 34;
        if (isSubmitting) {
            guiGraphics.drawString(this.font, "→ " + submitStatusText, left + 15, statusY, submitStatusColor, false);
        } else if (!submitStatusText.isEmpty()) {
            guiGraphics.drawString(this.font, submitStatusText, left + 15, statusY, submitStatusColor, false);
        } else if (this.orderInput != null && this.orderInput.isFocused()) {
            guiGraphics.renderOutline(this.orderInput.getX() - 1, this.orderInput.getY() - 1, this.orderInput.getWidth() + 2, this.orderInput.getHeight() + 2, com.aicompanion.client.gui.EsclabUiColors.BORDER_FOCUSED);
            guiGraphics.drawString(this.font, "● Escribiendo orden (Enter: ejecutar, Esc: deseleccionar)", left + 15, statusY, com.aicompanion.client.gui.EsclabUiColors.SUCCESS_GREEN, false);
        } else {
            guiGraphics.drawString(this.font, "(Haz clic aquí para escribir orden)", left + 15, statusY, com.aicompanion.client.gui.EsclabUiColors.TEXT_MUTED, false);
        }

        int boxY = panelTop + 46;
        int boxH = showFullPlan ? 56 : 38;
        drawInsetBox(guiGraphics, left + 15, boxY, 395, boxH);

        // Render Colored Badges Row at boxY + 3
        int bx = left + 20;
        int by = boxY + 3;
        if (!currentOrderBadges.isEmpty()) {
            for (OrderBadge badge : currentOrderBadges) {
                int bw = this.font.width(badge.label()) + 8;
                if (bx + bw > left + 330) break; // Room for toggle button
                drawBadge(guiGraphics, bx, by, badge.label(), badge.bgColor(), badge.borderColor(), badge.textColor());
                bx += bw + 3;
            }
        } else {
            drawBadge(guiGraphics, bx, by, "1° ACCIÓN", 0x44263238, 0xFF455A64, 0xFF90A4AE);
            bx += this.font.width("1° ACCIÓN") + 11;
            drawBadge(guiGraphics, bx, by, "2° OBJETO", 0x44263238, 0xFF455A64, 0xFF90A4AE);
            bx += this.font.width("2° OBJETO") + 11;
            drawBadge(guiGraphics, bx, by, "3° CANTIDAD", 0x44263238, 0xFF455A64, 0xFF90A4AE);
            bx += this.font.width("3° CANTIDAD") + 11;
            drawBadge(guiGraphics, bx, by, "4° DESTINO", 0x44263238, 0xFF455A64, 0xFF90A4AE);
        }

        // Toggle button: [▼ Ver Plan] / [▲ Ocultar]
        String toggleLabel = showFullPlan ? "▲ Ocultar" : "▼ Ver Plan";
        drawSmallButton(guiGraphics, left + 335, boxY + 3, 70, 11, toggleLabel, com.aicompanion.client.gui.EsclabUiColors.GOLD);

        // Render steps starting at boxY + 17
        if (!serverPreviewRows.isEmpty()) {
            int maxRows = showFullPlan ? Math.min(4, serverPreviewRows.size()) : 1;
            for (int i = 0; i < maxRows; i++) {
                PlanPreviewRow row = serverPreviewRows.get(i);
                int rowColor = switch (row.type()) {
                    case GOAL -> com.aicompanion.client.gui.EsclabUiColors.GOLD;
                    case ACTION -> com.aicompanion.client.gui.EsclabUiColors.TEXT_PRIMARY;
                    case REQUIREMENT -> com.aicompanion.client.gui.EsclabUiColors.HEADER_ORANGE;
                    case RETURN -> com.aicompanion.client.gui.EsclabUiColors.TEXT_MUTED;
                    case MISSING -> com.aicompanion.client.gui.EsclabUiColors.WARNING_YELLOW;
                    case WARNING -> com.aicompanion.client.gui.EsclabUiColors.BADGE_ERROR;
                };
                String stepText = this.font.plainSubstrByWidth("• " + row.text(), 380);
                guiGraphics.drawString(this.font, stepText, left + 20, boxY + 17 + i * 9, rowColor, false);
            }
        } else {
            int maxLines = showFullPlan ? Math.min(4, interpretedPlanSteps.size()) : 1;
            for (int i = 0; i < maxLines; i++) {
                String stepStr = this.font.plainSubstrByWidth(interpretedPlanSteps.get(i), 380);
                guiGraphics.drawString(this.font, stepStr, left + 20, boxY + 17 + i * 9, com.aicompanion.client.gui.EsclabUiColors.TEXT_PRIMARY, false);
            }
        }
    }

    private void renderQueueTab(GuiGraphics guiGraphics, int left, int panelTop) {
        guiGraphics.drawString(this.font, "COLA DE TAREAS Y OBJETIVOS ENCADENADOS", left + 20, panelTop + 14, 0xFFFFD700, false);
        drawInsetBox(guiGraphics, left + 20, panelTop + 28, 390, 88);

        String activeTask = syncedData != null && !syncedData.currentTaskDesc.isEmpty() ? syncedData.currentTaskDesc : "En reposo";
        guiGraphics.drawString(this.font, "▶ En ejecución activa:", left + 30, panelTop + 34, 0xFF3CD058, false);
        guiGraphics.drawString(this.font, activeTask, left + 160, panelTop + 34, 0xFFFFD700, false);

        guiGraphics.drawString(this.font, "Siguientes etapas programadas:", left + 30, panelTop + 48, 0xFFA0B0C4, false);

        List<String> queue = syncedData != null ? syncedData.queueDescriptions : new ArrayList<>();
        if (queue.isEmpty()) {
            guiGraphics.drawString(this.font, "(No hay más tareas pendientes en cola)", left + 40, panelTop + 62, 0xFFA0A0A0, false);
            guiGraphics.drawString(this.font, "Puedes encadenar misiones: 'corta 10 madera, luego trae a mí'", left + 40, panelTop + 76, 0xFF8AA8D4, false);
        } else {
            for (int i = 0; i < Math.min(3, queue.size()); i++) {
                int qy = panelTop + 62 + i * 18;
                guiGraphics.drawString(this.font, (i + 1) + ". " + queue.get(i), left + 40, qy + 2, 0xFFFFFFFF, false);
                drawSmallButton(guiGraphics, left + 380, qy, 18, 14, "X", 0xFFFF5555);
            }
        }
    }

    private void renderConstructionTab(GuiGraphics guiGraphics, int left, int panelTop, int mouseX, int mouseY) {
        if (this.buildingStudioView != null) {
            this.buildingStudioView.render(guiGraphics, left, panelTop, mouseX, mouseY);
        }
    }

    private void renderAITab(GuiGraphics guiGraphics, int left, int panelTop) {
        guiGraphics.drawString(this.font, "CONFIGURACIÓN DE IA Y PLANIFICADOR", left + 20, panelTop + 12, 0xFFFFD700, false);
        drawInsetBox(guiGraphics, left + 20, panelTop + 26, 390, 84);

        String mode = com.aicompanion.AICompanionConfig.COMMON.aiMode.get();

        if ("LOCAL_ONLY".equalsIgnoreCase(mode)) {
            guiGraphics.drawString(this.font, "● MODO: LOCAL AUTÓNOMO (Sin servidores ni LLMs externos)", left + 28, panelTop + 33, 0xFF3CD058, false);
            guiGraphics.drawString(this.font, "● NAVEGACIÓN: Baritone 3D Voxel A* (Privado y Nativo)", left + 28, panelTop + 45, 0xFFFFD700, false);
            guiGraphics.drawString(this.font, "● PLANIFICADOR: Goal Search & Reglas Locales Deterministas", left + 28, panelTop + 57, 0xFFD8E2ED, false);
            String connStatus = connectionTestStatus != null ? connectionTestStatus : "ACTIVO: 100% Autónomo y listo para recibir órdenes";
            guiGraphics.drawString(this.font, "● ESTADO: " + connStatus, left + 28, panelTop + 69, 0xFF3CD058, false);
            guiGraphics.drawString(this.font, "Capacidades: Minería, Tala, Combate, Puenteo y Túneles 3D", left + 28, panelTop + 81, 0xFF8AA8D4, false);
        } else {
            String endpoint = com.aicompanion.AICompanionConfig.COMMON.aiEndpoint.get();
            String model = com.aicompanion.AICompanionConfig.COMMON.aiModel.get();

            guiGraphics.drawString(this.font, "● MODO: " + mode.toUpperCase() + "  |  REGLAS DETERMINISTAS: ACTIVAS", left + 28, panelTop + 33, 0xFF3CD058, false);
            String epStr = this.font.plainSubstrByWidth("● ENDPOINT: " + endpoint, 370);
            guiGraphics.drawString(this.font, epStr, left + 28, panelTop + 45, 0xFFFFAA00, false);
            guiGraphics.drawString(this.font, "● MODELO: " + model, left + 28, panelTop + 57, 0xFFD8E2ED, false);

            String connStatus = connectionTestStatus != null ? connectionTestStatus : "STANDBY (Haz clic en PROBAR CONEXIÓN abajo)";
            int connColor = connectionTestStatus != null ? 0xFF3CD058 : 0xFFA0B0C4;
            guiGraphics.drawString(this.font, "● ESTADO: " + connStatus, left + 28, panelTop + 69, connColor, false);
            guiGraphics.drawString(this.font, "Planificador: Baritone 3D A* con Fallback Determinista Seguro", left + 28, panelTop + 81, 0xFF8AA8D4, false);
        }
    }

    private void renderMemoryTab(GuiGraphics guiGraphics, int left, int panelTop) {
        guiGraphics.drawString(this.font, "MEMORIA Y PUNTOS DE INTERÉS", left + 20, panelTop + 14, 0xFFFFD700, false);
        drawInsetBox(guiGraphics, left + 20, panelTop + 28, 390, 86);

        String home = syncedData != null ? syncedData.homePos : "No establecida";
        guiGraphics.drawString(this.font, "1. Base Principal: " + home, left + 30, panelTop + 36, 0xFF3CD058, false);
        guiGraphics.drawString(this.font, "2. Cofre de Minerales: [Guardado]", left + 30, panelTop + 50, 0xFFD8E2ED, false);
        guiGraphics.drawString(this.font, "3. Portal al Nether: [Guardado]", left + 30, panelTop + 64, 0xFFD8E2ED, false);
        guiGraphics.drawString(this.font, "4. Mina Subterránea: [Guardada]", left + 30, panelTop + 78, 0xFFD8E2ED, false);
    }

    private void renderStatsTab(GuiGraphics guiGraphics, int left, int panelTop) {
        guiGraphics.drawString(this.font, "ESTADÍSTICAS Y EXPERIENCIA", left + 20, panelTop + 14, 0xFFFFD700, false);
        drawInsetBox(guiGraphics, left + 20, panelTop + 26, 390, 115);

        int mxp = syncedData != null ? syncedData.miningXP : 0;
        int cxp = syncedData != null ? syncedData.combatXP : 0;
        int bxp = syncedData != null ? syncedData.buildingXP : 0;
        int gxp = syncedData != null ? syncedData.gatheringXP : 0;

        renderXPBar(guiGraphics, "Minería", mxp, left + 30, panelTop + 32);
        renderXPBar(guiGraphics, "Combate", cxp, left + 30, panelTop + 50);
        renderXPBar(guiGraphics, "Construcción", bxp, left + 30, panelTop + 68);
        renderXPBar(guiGraphics, "Tala/Recolección", gxp, left + 30, panelTop + 86);

        int mined = syncedData != null ? syncedData.blocksMined : 0;
        int killed = syncedData != null ? syncedData.mobsKilled : 0;
        guiGraphics.drawString(this.font, "Bloques Minados: " + mined + " | Mobs Eliminados: " + killed, left + 30, panelTop + 106, 0xFFA0B0C4, false);
    }

    private void renderXPBar(GuiGraphics guiGraphics, String name, int xp, int x, int y) {
        guiGraphics.drawString(this.font, name + ": " + xp + " XP", x, y, 0xFFFFFFFF, false);
        int barX = x + 120;
        int barW = 200;
        guiGraphics.fill(barX, y + 1, barX + barW, y + 8, 0xFF141820);
        int fill = Math.min(barW, (xp % 1000) * barW / 1000);
        guiGraphics.fill(barX + 1, y + 2, barX + fill, y + 7, 0xFFFFAA00);
    }

    private void renderConfigTab(GuiGraphics guiGraphics, int left, int panelTop) {
        guiGraphics.drawString(this.font, "COMPORTAMIENTO", left + 15, panelTop + 4, 0xFFFFD700, false);
        guiGraphics.drawString(this.font, "OPCIONES GENERALES", left + 215, panelTop + 4, 0xFFFFD700, false);
        guiGraphics.drawString(this.font, "ATAJOS DE TECLADO Y ACCIONES RÁPIDAS", left + 15, panelTop + 82, 0xFFFFD700, false);
    }

    // Modal Rendering
    private void renderModal(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400.0f);
        guiGraphics.fill(0, 0, this.width, this.height, 0xCC000000);

        try {
            if (activeModal == ModalType.ITEM_CATALOG) {
                int mw = 340;
                int mh = 230;
                int mx = this.leftPos + (this.imageWidth - mw) / 2;
                int my = this.topPos + (this.imageHeight - mh) / 2;
                drawCustomPanel(guiGraphics, mx, my, mw, mh);

                guiGraphics.drawString(this.font, "CATÁLOGO DE ÍTEMS Y RECURSOS", mx + 16, my + 10, com.aicompanion.client.gui.EsclabUiColors.GOLD, false);
                String countStr = "(" + catalogResults.size() + " disponibles)";
                guiGraphics.drawString(this.font, countStr, mx + mw - this.font.width(countStr) - 16, my + 10, 0xFFA0B0C4, false);

                if (this.catalogSearchInput != null) {
                    this.catalogSearchInput.render(guiGraphics, mouseX, mouseY, 0);
                }

                int totalPages = Math.max(1, (int) Math.ceil((double) catalogResults.size() / CATALOG_PAGE_SIZE));
                if (catalogPage >= totalPages) catalogPage = totalPages - 1;
                if (catalogPage < 0) catalogPage = 0;

                int startIndex = catalogPage * CATALOG_PAGE_SIZE;
                int endIndex = Math.min(startIndex + CATALOG_PAGE_SIZE, catalogResults.size());

                ItemStack hoveredStack = ItemStack.EMPTY;

                int listY = my + 44;
                int cardW = 148;
                int cardH = 34;

                for (int i = 0; i < CATALOG_PAGE_SIZE; i++) {
                    int itemIdx = startIndex + i;
                    int col = i % 2;
                    int row = i / 2;
                    int cardX = mx + 16 + col * (cardW + 12);
                    int cardY = listY + row * (cardH + 4);

                    if (itemIdx < endIndex) {
                        ItemSearchRegistry.CatalogEntry entry = catalogResults.get(itemIdx);
                        boolean hovered = mouseX >= cardX && mouseX < cardX + cardW && mouseY >= cardY && mouseY < cardY + cardH;

                        int bg = hovered ? 0xFF243042 : 0xFF161D27;
                        int border = hovered ? 0xFF00E676 : 0xFF2C394B;

                        guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, bg);
                        guiGraphics.renderOutline(cardX, cardY, cardW, cardH, border);

                        ItemStack stack = entry.item().getDefaultInstance();
                        guiGraphics.renderItem(stack, cardX + 4, cardY + 9);

                        String name = this.font.plainSubstrByWidth(entry.displayName(), cardW - 28);
                        guiGraphics.drawString(this.font, name, cardX + 24, cardY + 6, hovered ? 0xFFFFFFFF : 0xFFE0E8F2, false);

                        String idStr = entry.id().getPath().replace('_', ' ');
                        idStr = this.font.plainSubstrByWidth(idStr, cardW - 28);
                        guiGraphics.drawString(this.font, idStr, cardX + 24, cardY + 18, 0xFF7A8D9F, false);

                        if (hovered) {
                            hoveredStack = stack;
                        }
                    } else {
                        // Empty slot placeholder
                        guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xFF121720);
                        guiGraphics.renderOutline(cardX, cardY, cardW, cardH, 0xFF1C2430);
                    }
                }

                // Pagination Controls at bottom
                int pageBtnY = my + 200;
                drawModalButton(guiGraphics, mx + 16, pageBtnY, 65, 18, "< Anterior");
                String pageStr = (catalogPage + 1) + " / " + totalPages;
                guiGraphics.drawCenteredString(this.font, pageStr, mx + mw / 2 - 10, pageBtnY + 5, 0xFFFFFFFF);
                drawModalButton(guiGraphics, mx + 195, pageBtnY, 65, 18, "Siguiente >");
                drawModalButton(guiGraphics, mx + 268, pageBtnY, 56, 18, "CERRAR");

                if (!hoveredStack.isEmpty()) {
                    guiGraphics.renderTooltip(this.font, hoveredStack, mouseX, mouseY);
                }
                return;
            }

        int mw = 250;
        int mh = 160;
        int mx = this.leftPos + (this.imageWidth - mw) / 2;
        int my = this.topPos + (this.imageHeight - mh) / 2;

        drawCustomPanel(guiGraphics, mx, my, mw, mh);

        if (activeModal == ModalType.MINE) {
            guiGraphics.drawString(this.font, "MINAR RECURSO", mx + 16, my + 14, 0xFFFFD700, false);
            guiGraphics.drawString(this.font, "Mineral: " + ORE_NAMES[selectedOreIdx], mx + 16, my + 34, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, "Cantidad: " + mineAmount, mx + 16, my + 56, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, "Destino: " + DEST_NAMES[mineDestination], mx + 16, my + 78, 0xFFFFFFFF, false);

            drawModalButton(guiGraphics, mx + 160, my + 30, 70, 16, "Cambiar ▼");
            drawModalButton(guiGraphics, mx + 160, my + 52, 70, 16, "+10 Cant");
            drawModalButton(guiGraphics, mx + 160, my + 74, 70, 16, "Cambiar ▼");

            guiGraphics.drawString(this.font, "[✓] Navegación 3D autónoma", mx + 16, my + 98, 0xFF3CD058, false);

            drawModalButton(guiGraphics, mx + 20, my + 125, 95, 20, "EMPEZAR");
            drawModalButton(guiGraphics, mx + 135, my + 125, 95, 20, "CANCELAR");
        } else if (activeModal == ModalType.CHOP) {
            guiGraphics.drawString(this.font, "TALAR MADERA", mx + 16, my + 14, 0xFFFFD700, false);
            guiGraphics.drawString(this.font, "Madera: " + WOOD_NAMES[selectedWoodIdx], mx + 16, my + 34, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, "Cantidad: " + chopAmount, mx + 16, my + 56, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, "Destino: " + DEST_NAMES[chopDestination], mx + 16, my + 78, 0xFFFFFFFF, false);

            drawModalButton(guiGraphics, mx + 160, my + 30, 70, 16, "Cambiar ▼");
            drawModalButton(guiGraphics, mx + 160, my + 52, 70, 16, "+16 Cant");
            drawModalButton(guiGraphics, mx + 160, my + 74, 70, 16, "Cambiar ▼");

            guiGraphics.drawString(this.font, "[✓] Tala desde la base hacia arriba", mx + 16, my + 98, 0xFF3CD058, false);

            drawModalButton(guiGraphics, mx + 20, my + 125, 95, 20, "EMPEZAR");
            drawModalButton(guiGraphics, mx + 135, my + 125, 95, 20, "CANCELAR");
        } else if (activeModal == ModalType.KILL) {
            guiGraphics.drawString(this.font, "CAZAR ENTIDAD", mx + 16, my + 14, 0xFFFFD700, false);
            guiGraphics.drawString(this.font, "Objetivo: " + MOB_NAMES[selectedMobIdx], mx + 16, my + 34, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, "Cantidad: " + killAmount, mx + 16, my + 60, 0xFFFFFFFF, false);

            drawModalButton(guiGraphics, mx + 160, my + 30, 70, 16, "Cambiar ▼");
            drawModalButton(guiGraphics, mx + 160, my + 56, 70, 16, "+5 Cant");

            drawModalButton(guiGraphics, mx + 20, my + 125, 95, 20, "EMPEZAR");
            drawModalButton(guiGraphics, mx + 135, my + 125, 95, 20, "CANCELAR");
        } else if (activeModal == ModalType.GOTO) {
            guiGraphics.drawString(this.font, "VIAJAR A COORDENADAS", mx + 16, my + 14, 0xFFFFD700, false);
            if (gotoXInput != null) gotoXInput.render(guiGraphics, mouseX, mouseY, 0);
            if (gotoYInput != null) gotoYInput.render(guiGraphics, mouseX, mouseY, 0);
            if (gotoZInput != null) gotoZInput.render(guiGraphics, mouseX, mouseY, 0);

            guiGraphics.drawString(this.font, "X:", mx + 30, my + 38, 0xFFD8E2ED, false);
            guiGraphics.drawString(this.font, "Y:", mx + 30, my + 58, 0xFFD8E2ED, false);
            guiGraphics.drawString(this.font, "Z:", mx + 30, my + 78, 0xFFD8E2ED, false);

            drawModalButton(guiGraphics, mx + 20, my + 125, 95, 20, "VIAJAR");
            drawModalButton(guiGraphics, mx + 135, my + 125, 95, 20, "CANCELAR");
        } else if (activeModal == ModalType.DISMISS_CONFIRM) {
            guiGraphics.drawString(this.font, "§c¿DESINVOCAR A ESCLAB?", mx + 16, my + 14, 0xFFFF5555, false);
            guiGraphics.drawString(this.font, "Esclavo será guardado de forma segura.", mx + 16, my + 38, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, "Su inventario y equipo se conservarán.", mx + 16, my + 54, 0xFF8AA8D4, false);
            guiGraphics.drawString(this.font, "Podrás volver a invocarlo en cualquier", mx + 16, my + 70, 0xFFE0E8F2, false);
            guiGraphics.drawString(this.font, "momento con el atajo o menú.", mx + 16, my + 84, 0xFFE0E8F2, false);

            drawModalButton(guiGraphics, mx + 20, my + 125, 95, 20, "CONFIRMAR");
            drawModalButton(guiGraphics, mx + 135, my + 125, 95, 20, "CANCELAR");
        }
        } finally {
            guiGraphics.flush();
            guiGraphics.pose().popPose();
        }
    }

    private void drawSmallButton(GuiGraphics guiGraphics, int x, int y, int w, int h, String text, int color) {
        guiGraphics.fill(x, y, x + w, y + h, 0xFF2B3647);
        guiGraphics.renderOutline(x, y, w, h, 0xFF4C596E);
        int tx = x + (w - this.font.width(text)) / 2;
        int ty = y + (h - this.font.lineHeight) / 2 + 1;
        guiGraphics.drawString(this.font, text, tx, ty, color, false);
    }

    private void drawModalButton(GuiGraphics guiGraphics, int x, int y, int w, int h, String text) {
        guiGraphics.fill(x, y, x + w, y + h, 0xFF2B3647);
        guiGraphics.renderOutline(x, y, w, h, 0xFFE5B838);
        int tx = x + (w - this.font.width(text)) / 2;
        int ty = y + (h - this.font.lineHeight) / 2 + 1;
        guiGraphics.drawString(this.font, text, tx, ty, 0xFFFFD700, false);
    }

    private void selectCatalogItem(ItemSearchRegistry.CatalogEntry entry) {
        if (entry == null) return;
        String name = entry.displayName();
        String path = entry.id().getPath().toLowerCase();

        String action = "conseguir";
        int amount = 64;
        if (path.endsWith("_ore") || path.equals("ancient_debris") || path.equals("diamond") || path.equals("raw_iron") || path.equals("raw_gold")) {
            action = "minar";
        } else if (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem")) {
            action = "talar";
        } else if (path.equals("bone") || path.equals("gunpowder") || path.equals("string") || path.equals("spider_eye") || path.equals("ender_pearl") || path.equals("leather") || path.equals("beef") || path.equals("porkchop") || path.equals("mutton") || path.equals("chicken") || path.equals("feather") || path.equals("slime_ball")) {
            action = "cazar";
            amount = 10;
        }

        if (this.orderInput != null) {
            this.orderInput.setValue(action + " " + name + " " + amount);
            this.orderInput.moveCursorToEnd();
            this.currentOrderText = this.orderInput.getValue();
            updatePlanPreview(this.currentOrderText);
            this.orderInput.setFocused(true);
            this.setFocused(this.orderInput);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeModal == ModalType.ITEM_CATALOG) {
            int mw = 340;
            int mh = 230;
            int mx = this.leftPos + (this.imageWidth - mw) / 2;
            int my = this.topPos + (this.imageHeight - mh) / 2;

            if (catalogSearchInput != null && catalogSearchInput.mouseClicked(mouseX, mouseY, button)) {
                catalogSearchInput.setFocused(true);
                this.setFocused(catalogSearchInput);
                return true;
            }

            int pageBtnY = my + 200;
            int totalPages = Math.max(1, (int) Math.ceil((double) catalogResults.size() / CATALOG_PAGE_SIZE));

            // Close button [ CERRAR ] (mx + 268, pageBtnY, 56, 18)
            if (isInside(mouseX, mouseY, mx + 268, pageBtnY, 56, 18)) {
                closeModal();
                return true;
            }

            // Previous page [ < Anterior ] (mx + 16, pageBtnY, 65, 18)
            if (isInside(mouseX, mouseY, mx + 16, pageBtnY, 65, 18)) {
                if (catalogPage > 0) catalogPage--;
                return true;
            }

            // Next page [ Siguiente > ] (mx + 195, pageBtnY, 65, 18)
            if (isInside(mouseX, mouseY, mx + 195, pageBtnY, 65, 18)) {
                if (catalogPage < totalPages - 1) catalogPage++;
                return true;
            }

            // Check card clicks
            int listY = my + 44;
            int cardW = 148;
            int cardH = 34;
            int startIndex = catalogPage * CATALOG_PAGE_SIZE;
            int endIndex = Math.min(startIndex + CATALOG_PAGE_SIZE, catalogResults.size());

            for (int i = 0; i < CATALOG_PAGE_SIZE; i++) {
                int itemIdx = startIndex + i;
                if (itemIdx >= endIndex) break;
                int col = i % 2;
                int row = i / 2;
                int cardX = mx + 16 + col * (cardW + 12);
                int cardY = listY + row * (cardH + 4);

                if (isInside(mouseX, mouseY, cardX, cardY, cardW, cardH)) {
                    selectCatalogItem(catalogResults.get(itemIdx));
                    closeModal();
                    return true;
                }
            }

            // Absorb any click inside or outside modal
            return true;
        }

        if (activeModal != ModalType.NONE) {
            int mw = 250;
            int mh = 160;
            int mx = this.leftPos + (this.imageWidth - mw) / 2;
            int my = this.topPos + (this.imageHeight - mh) / 2;

            CompanionEntity companion = menu.getCompanion();
            int entityId = companion != null ? companion.getId() : (syncedData != null ? syncedData.entityId : 0);

            if (isInside(mouseX, mouseY, mx + 135, my + 125, 95, 20)) {
                closeModal();
                return true;
            }

            if (activeModal == ModalType.MINE) {
                if (isInside(mouseX, mouseY, mx + 160, my + 30, 70, 16)) {
                    selectedOreIdx = (selectedOreIdx + 1) % ORE_NAMES.length;
                    return true;
                }
                if (isInside(mouseX, mouseY, mx + 160, my + 52, 70, 16)) {
                    mineAmount = mineAmount >= 64 ? 10 : mineAmount + 10;
                    return true;
                }
                if (isInside(mouseX, mouseY, mx + 160, my + 74, 70, 16)) {
                    mineDestination = (mineDestination + 1) % DEST_NAMES.length;
                    return true;
                }
                if (isInside(mouseX, mouseY, mx + 20, my + 125, 95, 20)) {
                    if (mineDestination == 1) {
                        sendNaturalOrder("minar " + mineAmount + " " + ORE_IDS[selectedOreIdx] + " y depositar en cofre");
                    } else {
                        sendAction(C2S_ActionPacket.Action.MINE, ORE_IDS[selectedOreIdx] + ":" + mineAmount);
                    }
                    closeModal();
                    return true;
                }
            } else if (activeModal == ModalType.CHOP) {
                if (isInside(mouseX, mouseY, mx + 160, my + 30, 70, 16)) {
                    selectedWoodIdx = (selectedWoodIdx + 1) % WOOD_NAMES.length;
                    return true;
                }
                if (isInside(mouseX, mouseY, mx + 160, my + 52, 70, 16)) {
                    chopAmount = chopAmount >= 64 ? 16 : chopAmount + 16;
                    return true;
                }
                if (isInside(mouseX, mouseY, mx + 160, my + 74, 70, 16)) {
                    chopDestination = (chopDestination + 1) % DEST_NAMES.length;
                    return true;
                }
                if (isInside(mouseX, mouseY, mx + 20, my + 125, 95, 20)) {
                    if (chopDestination == 1) {
                        sendNaturalOrder("talar " + chopAmount + " " + WOOD_IDS[selectedWoodIdx] + " y depositar en cofre");
                    } else {
                        sendAction(C2S_ActionPacket.Action.CHOP, WOOD_IDS[selectedWoodIdx] + ":" + chopAmount);
                    }
                    closeModal();
                    return true;
                }
            } else if (activeModal == ModalType.KILL) {
                if (isInside(mouseX, mouseY, mx + 160, my + 30, 70, 16)) {
                    selectedMobIdx = (selectedMobIdx + 1) % MOB_NAMES.length;
                    return true;
                }
                if (isInside(mouseX, mouseY, mx + 160, my + 56, 70, 16)) {
                    killAmount = killAmount >= 30 ? 5 : killAmount + 5;
                    return true;
                }
                if (isInside(mouseX, mouseY, mx + 20, my + 125, 95, 20)) {
                    sendAction(C2S_ActionPacket.Action.KILL, MOB_IDS[selectedMobIdx] + ":" + killAmount);
                    closeModal();
                    return true;
                }
            } else if (activeModal == ModalType.GOTO) {
                if (gotoXInput != null && gotoXInput.mouseClicked(mouseX, mouseY, button)) return true;
                if (gotoYInput != null && gotoYInput.mouseClicked(mouseX, mouseY, button)) return true;
                if (gotoZInput != null && gotoZInput.mouseClicked(mouseX, mouseY, button)) return true;

                if (isInside(mouseX, mouseY, mx + 20, my + 125, 95, 20)) {
                    String coords = gotoXInput.getValue() + ":" + gotoYInput.getValue() + ":" + gotoZInput.getValue();
                    sendAction(C2S_ActionPacket.Action.GOTO, coords);
                    closeModal();
                    return true;
                }
            } else if (activeModal == ModalType.DISMISS_CONFIRM) {
                if (isInside(mouseX, mouseY, mx + 20, my + 125, 95, 20)) {
                    sendAction(C2S_ActionPacket.Action.DISMISS);
                    closeModal();
                    this.onClose();
                    return true;
                }
            }
            return true;
        }

        // Delete item from queue when clicking [X] in Tab 4
        if (selectedTab == 4 && syncedData != null && syncedData.queueDescriptions != null) {
            int panelTop = this.topPos + 48;
            for (int i = 0; i < Math.min(3, syncedData.queueDescriptions.size()); i++) {
                int qy = panelTop + 62 + i * 18;
                if (isInside(mouseX, mouseY, this.leftPos + 380, qy, 18, 14)) {
                    sendAction(C2S_ActionPacket.Action.REMOVE_QUEUE_INDEX, String.valueOf(i));
                    return true;
                }
            }
        }

        // Toggle [VER PLAN] / [OCULTAR] in Tab 3
        if (selectedTab == 3) {
            int panelTop = this.topPos + 48;
            int boxY = panelTop + 46;
            if (isInside(mouseX, mouseY, this.leftPos + 335, boxY + 2, 70, 11)) {
                this.showFullPlan = !this.showFullPlan;
                return true;
            }
        }

        // Handle Tab 1 Name Input focus
        if (selectedTab == 1 && nameInput != null) {
            if (isInside(mouseX, mouseY, nameInput.getX() - 4, nameInput.getY() - 4, nameInput.getWidth() + 8, nameInput.getHeight() + 8)) {
                nameInput.setFocused(true);
                this.setFocused(nameInput);
                nameInput.mouseClicked(mouseX, mouseY, button);
                return true;
            } else {
                nameInput.setFocused(false);
                if (this.getFocused() == nameInput) this.setFocused(null);
            }
        }

        // Direct click on Execute button in Tab 3
        if (selectedTab == 3 && executeBtn != null && executeBtn.visible && executeBtn.active && button == 0) {
            if (isInside(mouseX, mouseY, executeBtn.getX(), executeBtn.getY(), executeBtn.getWidth(), executeBtn.getHeight())) {
                executeBtn.playDownSound(Minecraft.getInstance().getSoundManager());
                executeBtn.onPress();
                return true;
            }
        }

        // Handle suggestion dropdown & order input focus in Tab 3
        if (selectedTab == 3 && suggestionDropdown != null && suggestionDropdown.isOpen()) {
            if (isInside(mouseX, mouseY, suggestionDropdown.getX(), suggestionDropdown.getY(), suggestionDropdown.getWidth(), suggestionDropdown.getHeight())) {
                if (suggestionDropdown.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            } else if (orderInput != null && isInside(mouseX, mouseY, orderInput.getX(), orderInput.getY(), orderInput.getWidth(), orderInput.getHeight())) {
                orderInput.setFocused(true);
                this.setFocused(orderInput);
                orderInput.mouseClicked(mouseX, mouseY, button);
                updateSuggestions(orderInput.getValue());
                return true;
            } else {
                suggestionDropdown.close();
            }
        } else if (selectedTab == 3 && orderInput != null) {
            if (isInside(mouseX, mouseY, orderInput.getX() - 4, orderInput.getY() - 4, orderInput.getWidth() + 8, orderInput.getHeight() + 8)) {
                orderInput.setFocused(true);
                this.setFocused(orderInput);
                orderInput.mouseClicked(mouseX, mouseY, button);
                updateSuggestions(orderInput.getValue());
                return true;
            } else {
                orderInput.setFocused(false);
                if (suggestionDropdown != null) suggestionDropdown.close();
                if (this.getFocused() == orderInput) this.setFocused(null);
            }
        }

        // Handle Tab 5 (Construcción) BuildingStudioView
        if (selectedTab == 5 && this.buildingStudioView != null) {
            if (this.buildingStudioView.mouseClicked(mouseX, mouseY, button, this.leftPos, this.topPos + 48)) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.selectedTab == 5 && this.buildingStudioView != null) {
            if (this.buildingStudioView.mouseDragged(mouseX, mouseY, button, dragX, dragY, this.leftPos, this.topPos + 48)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.selectedTab == 5 && this.buildingStudioView != null) {
            if (this.buildingStudioView.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawCustomPanel(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        int panelBg = 0xFF2B3543;
        int topHighlight = 0xFF4C596E;
        int bottomShadow = 0xFF161A22;
        int borderBlack = 0xFF0E1015;

        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, panelBg);
        guiGraphics.renderOutline(x, y, w, h, borderBlack);

        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + 2, topHighlight);
        guiGraphics.fill(x + 1, y + 1, x + 2, y + h - 1, topHighlight);

        guiGraphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, bottomShadow);
        guiGraphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, bottomShadow);

        drawRivet(guiGraphics, x + 3, y + 3);
        drawRivet(guiGraphics, x + w - 5, y + 3);
        drawRivet(guiGraphics, x + 3, y + h - 5);
        drawRivet(guiGraphics, x + w - 5, y + h - 5);
    }

    private void drawInsetBox(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        int insetBg = 0xFF12161F;
        int shadowDark = 0xFF0A0C12;
        int highlightLight = 0xFF262E3C;

        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, insetBg);
        guiGraphics.renderOutline(x, y, w, h, 0xFF0E1015);

        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + 2, shadowDark);
        guiGraphics.fill(x + 1, y + 1, x + 2, y + h - 1, shadowDark);

        guiGraphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, highlightLight);
        guiGraphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, highlightLight);
    }

    private void drawSlotWell(GuiGraphics guiGraphics, int x, int y) {
        int slotBg = 0xFF1A202C;
        guiGraphics.fill(x, y, x + 18, y + 18, slotBg);
        guiGraphics.fill(x, y, x + 18, y + 1, 0xFF10141C);
        guiGraphics.fill(x, y, x + 1, y + 18, 0xFF10141C);
        guiGraphics.fill(x + 1, y + 17, x + 18, y + 18, 0xFF3D4B60);
        guiGraphics.fill(x + 17, y + 1, x + 18, y + 18, 0xFF3D4B60);
    }

    private void drawRivet(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x, y, x + 2, y + 2, 0xFF8C9BB4);
        guiGraphics.fill(x + 1, y + 1, x + 2, y + 2, 0xFF141820);
    }

    private String getEquipName(int armorIdx) {
        Slot s = menu.slots.get(CompanionMenu.SLOT_ARMOR_START + armorIdx);
        return (s != null && s.hasItem()) ? s.getItem().getHoverName().getString() : "Ninguno";
    }

    private String getMainHandName() {
        Slot s = menu.slots.get(CompanionMenu.SLOT_MAINHAND);
        return (s != null && s.hasItem()) ? s.getItem().getHoverName().getString() : "Vacía";
    }

    private String getOffHandName() {
        Slot s = menu.slots.get(CompanionMenu.SLOT_OFFHAND);
        return (s != null && s.hasItem()) ? s.getItem().getHoverName().getString() : "Vacía";
    }
}
