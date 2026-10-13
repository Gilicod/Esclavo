package com.aicompanion.client.gui.building;

import com.aicompanion.ai.building.*;
import com.aicompanion.client.gui.CompanionScreen;
import com.aicompanion.client.gui.EsclabUiColors;
import com.aicompanion.client.gui.widgets.EsclabButton;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.network.packets.C2S_ActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Enterprise Building Studio View (Tab 5) for Esclavo.
 * Completely isolates the new 3-column Building Studio from other tabs:
 * - Left: Schematics & Procedural Templates library with search, refresh, folder opener.
 * - Center: Real-time 3D isometric viewer with mouse drag rotation, zoom, layer inspection.
 * - Right: Building modes (Original, Material Único, Vacío, Solo Exterior, Sin Decoración, Personalizado),
 *          material selector with family substitution, 360° rotation, and placement options.
 * - Bottom: Accurate material breakdown & inventory stock calculation, plus execution buttons.
 */
public class BuildingStudioView {

    private final CompanionScreen parent;
    private final BiConsumer<C2S_ActionPacket.Action, String> actionSender;

    // Core Data
    private final BuildingConfig config = new BuildingConfig("small_house");
    private Blueprint sourceBlueprint = null;
    private Blueprint transformedBlueprint = null;
    private BuildingMaterialsCalculator.MaterialSummary materialSummary = null;

    // 3D Renderer
    private final SchematicPreviewRenderer previewRenderer = new SchematicPreviewRenderer();

    // UI State & Navigation
    private EditBox searchBox = null;
    private String currentSearch = "";
    private int selectedLibraryIdx = 0;
    private int libraryScrollOffset = 0;
    private static final int VISIBLE_LIBRARY_ROWS = 7;

    // Available Materials Palette (Expanded across stones, woods, bricks, nether and copper)
    private static final ResourceLocation[] PALETTE_MATERIALS = {
            new ResourceLocation("minecraft", "cobblestone"),
            new ResourceLocation("minecraft", "mossy_cobblestone"),
            new ResourceLocation("minecraft", "stone_bricks"),
            new ResourceLocation("minecraft", "mossy_stone_bricks"),
            new ResourceLocation("minecraft", "deepslate_bricks"),
            new ResourceLocation("minecraft", "deepslate_tiles"),
            new ResourceLocation("minecraft", "polished_deepslate"),
            new ResourceLocation("minecraft", "smooth_stone"),
            new ResourceLocation("minecraft", "bricks"),
            new ResourceLocation("minecraft", "mud_bricks"),
            new ResourceLocation("minecraft", "sandstone"),
            new ResourceLocation("minecraft", "red_sandstone"),
            new ResourceLocation("minecraft", "quartz_block"),
            new ResourceLocation("minecraft", "prismarine"),
            new ResourceLocation("minecraft", "dark_prismarine"),
            new ResourceLocation("minecraft", "nether_bricks"),
            new ResourceLocation("minecraft", "red_nether_bricks"),
            new ResourceLocation("minecraft", "purpur_block"),
            new ResourceLocation("minecraft", "oak_planks"),
            new ResourceLocation("minecraft", "spruce_planks"),
            new ResourceLocation("minecraft", "birch_planks"),
            new ResourceLocation("minecraft", "jungle_planks"),
            new ResourceLocation("minecraft", "acacia_planks"),
            new ResourceLocation("minecraft", "dark_oak_planks"),
            new ResourceLocation("minecraft", "mangrove_planks"),
            new ResourceLocation("minecraft", "cherry_planks"),
            new ResourceLocation("minecraft", "bamboo_planks"),
            new ResourceLocation("minecraft", "crimson_planks"),
            new ResourceLocation("minecraft", "warped_planks"),
            new ResourceLocation("minecraft", "cut_copper")
    };
    private int selectedMaterialIdx = 0;

    // Sub-view overlays (custom toggles and manual coordinates entry)
    private boolean showCustomOverlay = false;
    private boolean showCoordsOverlay = false;
    private EditBox coordXBox = null;
    private EditBox coordYBox = null;
    private EditBox coordZBox = null;

    // Static and file persistence across menu openings and game restarts
    private static BuildingConfig SAVED_CONFIG = null;

    private static BuildingConfig loadSavedConfig() {
        if (SAVED_CONFIG != null) {
            return SAVED_CONFIG;
        }
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, "config");
            File file = new File(configDir, "esclavo_building_studio.txt");
            if (file.exists()) {
                String content = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    SAVED_CONFIG = BuildingConfig.deserialize(content);
                    return SAVED_CONFIG;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void saveConfigState() {
        if (this.config == null) return;
        SAVED_CONFIG = new BuildingConfig();
        SAVED_CONFIG.copyFrom(this.config);
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, "config");
            if (!configDir.exists()) configDir.mkdirs();
            File file = new File(configDir, "esclavo_building_studio.txt");
            java.nio.file.Files.writeString(file.toPath(), this.config.serialize(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public BuildingStudioView(CompanionScreen parent, BiConsumer<C2S_ActionPacket.Action, String> actionSender) {
        this.parent = parent;
        this.actionSender = actionSender;
    }

    public void init(int left, int panelTop) {
        // 0. Restore last user configuration if present
        BuildingConfig saved = loadSavedConfig();
        if (saved != null) {
            this.config.copyFrom(saved);
            for (int i = 0; i < PALETTE_MATERIALS.length; i++) {
                if (PALETTE_MATERIALS[i].equals(this.config.getPrimaryMaterial())) {
                    this.selectedMaterialIdx = i;
                    break;
                }
            }
        }

        // 1. Initialize Library and default blueprint
        SchematicLibraryManager.refreshLibrary();
        List<SchematicLibraryManager.SchematicEntry> entries = SchematicLibraryManager.getEntries(currentSearch);
        if (!entries.isEmpty()) {
            int matchIdx = 0;
            if (saved != null && saved.getBlueprintId() != null) {
                for (int i = 0; i < entries.size(); i++) {
                    if (entries.get(i).id().equals(saved.getBlueprintId())) {
                        matchIdx = i;
                        break;
                    }
                }
            }
            this.selectedLibraryIdx = matchIdx;
            selectSchematic(entries.get(selectedLibraryIdx));
        }

        // 2. Search Box in Left Column
        int searchX = left + 12;
        int searchY = panelTop + 18;
        int searchW = 108;
        int searchH = 14;

        this.searchBox = new EditBox(Minecraft.getInstance().font, searchX, searchY, searchW, searchH, Component.literal("Buscar"));
        this.searchBox.setMaxLength(32);
        this.searchBox.setHint(Component.literal("Buscar..."));
        this.searchBox.setValue(this.currentSearch);
        this.searchBox.setResponder(text -> {
            this.currentSearch = text;
            this.selectedLibraryIdx = 0;
            this.libraryScrollOffset = 0;
        });

        // 3. Coordinate Inputs for MANUAL_COORDS Overlay
        int ox = left + 85;
        int oy = panelTop + 50;
        this.coordXBox = new EditBox(Minecraft.getInstance().font, ox + 32, oy + 38, 55, 14, Component.literal("X"));
        this.coordXBox.setMaxLength(10);
        this.coordYBox = new EditBox(Minecraft.getInstance().font, ox + 112, oy + 38, 45, 14, Component.literal("Y"));
        this.coordYBox.setMaxLength(10);
        this.coordZBox = new EditBox(Minecraft.getInstance().font, ox + 182, oy + 38, 55, 14, Component.literal("Z"));
        this.coordZBox.setMaxLength(10);

        // 4. Execution Buttons
        int btnY = panelTop + 226;
        var sync = parent.getSyncedData();
        boolean hasActiveProject = sync != null && ("BUILD".equalsIgnoreCase(sync.currentTaskType)
                || (sync.currentTaskDesc != null && sync.currentTaskDesc.toLowerCase().contains("construir")));

        if (hasActiveProject) {
            parent.addStudioWidget(new EsclabButton(left + 10, btnY, 195, 20, Component.literal("🔄 CONTINUAR PROYECTO"), b -> {
                actionSender.accept(C2S_ActionPacket.Action.RESUME, "");
                parent.onClose();
            }));

            parent.addStudioWidget(new EsclabButton(left + 215, btnY, 205, 20, Component.literal("➕ CREAR NUEVA COPIA"), b -> {
                config.setForceNew(true);
                saveConfigState();
                actionSender.accept(C2S_ActionPacket.Action.BUILD, config.serialize());
                parent.onClose();
            }));
        } else {
            parent.addStudioWidget(new EsclabButton(left + 10, btnY, 195, 20, Component.literal("▶ CONSTRUIR AHORA"), b -> {
                config.setAutoGather(false);
                config.setForceNew(false);
                saveConfigState();
                actionSender.accept(C2S_ActionPacket.Action.BUILD, config.serialize());
                parent.onClose();
            }));

            parent.addStudioWidget(new EsclabButton(left + 215, btnY, 205, 20, Component.literal("⛏ CONSEGUIR Y CONSTRUIR"), b -> {
                config.setAutoGather(true);
                config.setForceNew(false);
                saveConfigState();
                actionSender.accept(C2S_ActionPacket.Action.BUILD, config.serialize());
                parent.onClose();
            }));
        }
    }

    public void selectSchematic(SchematicLibraryManager.SchematicEntry entry) {
        if (entry == null) return;
        config.setBlueprintId(entry.id());
        this.sourceBlueprint = BlueprintRegistry.get(entry.id());
        recomputeTransformations();
    }

    public void recomputeTransformations() {
        if (this.sourceBlueprint == null) {
            this.sourceBlueprint = BlueprintRegistry.get(config.getBlueprintId());
        }
        this.transformedBlueprint = BlueprintTransformer.transform(this.sourceBlueprint, config);
        CompanionEntity companion = parent.getMenu().getCompanion();
        this.materialSummary = BuildingMaterialsCalculator.calculate(this.transformedBlueprint, companion);
        saveConfigState();
    }

    public void openCoordsOverlay() {
        this.showCoordsOverlay = true;
        this.showCustomOverlay = false;
        BlockPos current = config.getTargetPos();
        if (current == null) {
            net.minecraft.world.entity.player.Player p = Minecraft.getInstance().player;
            current = p != null ? p.blockPosition() : parent.getMenu().getCompanion().blockPosition();
            config.setTargetPos(current);
        }
        if (coordXBox != null) coordXBox.setValue(String.valueOf(current.getX()));
        if (coordYBox != null) coordYBox.setValue(String.valueOf(current.getY()));
        if (coordZBox != null) coordZBox.setValue(String.valueOf(current.getZ()));
    }

    public void tick() {
        if (this.searchBox != null) this.searchBox.tick();
        if (this.coordXBox != null) this.coordXBox.tick();
        if (this.coordYBox != null) this.coordYBox.tick();
        if (this.coordZBox != null) this.coordZBox.tick();
    }

    public void render(GuiGraphics guiGraphics, int left, int panelTop, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();

        // Title Header
        guiGraphics.drawString(mc.font, "ESTUDIO DE CONSTRUCCIÓN Y BIBLIOTECA DE SCHEMATICS", left + 12, panelTop + 5, 0xFFFFD700, false);

        // -------------------------------------------------------------
        // COLUMN 1: BIBLIOTECA (left + 10, panelTop + 16, W = 114, H = 165)
        // -------------------------------------------------------------
        int col1X = left + 10;
        int col1Y = panelTop + 16;
        int col1W = 114;
        int col1H = 165;
        drawInsetBox(guiGraphics, col1X, col1Y, col1W, col1H);

        if (this.searchBox != null) {
            this.searchBox.render(guiGraphics, mouseX, mouseY, 0f);
        }

        // List Entries
        List<SchematicLibraryManager.SchematicEntry> entries = SchematicLibraryManager.getEntries(currentSearch);
        int listY = col1Y + 18;
        int rowH = 18;

        int maxScroll = Math.max(0, entries.size() - VISIBLE_LIBRARY_ROWS);
        libraryScrollOffset = Math.max(0, Math.min(maxScroll, libraryScrollOffset));

        for (int i = 0; i < VISIBLE_LIBRARY_ROWS; i++) {
            int entryIdx = libraryScrollOffset + i;
            if (entryIdx >= entries.size()) break;

            SchematicEntryRow(guiGraphics, mc, entries.get(entryIdx), col1X + 2, listY + i * rowH, col1W - 4, rowH, entryIdx == selectedLibraryIdx, mouseX, mouseY);
        }

        // Bottom Library Buttons: [ACTUALIZAR] [CARPETA]
        int libBtnY = col1Y + col1H - 16;
        drawSmallButton(guiGraphics, col1X + 2, libBtnY, 52, 14, "Actualizar", 0xFFD8E2ED, mouseX, mouseY);
        drawSmallButton(guiGraphics, col1X + 58, libBtnY, 54, 14, "Carpeta 📁", 0xFF8AA8D4, mouseX, mouseY);

        // -------------------------------------------------------------
        // COLUMN 2: VISTA PREVIA 3D (left + 128, panelTop + 16, W = 152, H = 165)
        // -------------------------------------------------------------
        int col2X = left + 128;
        int col2Y = panelTop + 16;
        int col2W = 152;
        int col2H = 165;
        drawInsetBox(guiGraphics, col2X, col2Y, col2W, col2H);

        // Dimensions and Name Header
        if (transformedBlueprint != null) {
            Vec3i dims = transformedBlueprint.getDimensions();
            String nameHeader = mc.font.plainSubstrByWidth(BlueprintRegistry.getFormatDisplayName(config.getBlueprintId()), col2W - 8);
            guiGraphics.drawCenteredString(mc.font, "§6§l" + nameHeader, col2X + col2W / 2, col2Y + 4, 0xFFFFD700);
            guiGraphics.drawCenteredString(mc.font, "§7" + dims.getX() + "×" + dims.getY() + "×" + dims.getZ() + " (" + transformedBlueprint.getBlocks().size() + " blq)", col2X + col2W / 2, col2Y + 14, 0xFFA0B0C4);
        } else {
            guiGraphics.drawCenteredString(mc.font, "§7Sin estructura", col2X + col2W / 2, col2Y + 6, 0xFFA0A0A0);
        }

        // Render 3D Model
        int previewBoxY = col2Y + 24;
        int previewBoxH = col2H - 42;
        if (transformedBlueprint != null) {
            previewRenderer.render(guiGraphics, transformedBlueprint, col2X + 2, previewBoxY, col2W - 4, previewBoxH);
        }

        // Bottom 3D Mini-Controls: [⟲] [⟳] [+] [-] [R] | [<] Y=all [>]
        int ctrlY = col2Y + col2H - 16;
        drawTinyButton(guiGraphics, col2X + 3, ctrlY, 14, 13, "⟲", mouseX, mouseY);
        drawTinyButton(guiGraphics, col2X + 19, ctrlY, 14, 13, "⟳", mouseX, mouseY);
        drawTinyButton(guiGraphics, col2X + 35, ctrlY, 14, 13, "+", mouseX, mouseY);
        drawTinyButton(guiGraphics, col2X + 51, ctrlY, 14, 13, "-", mouseX, mouseY);
        drawTinyButton(guiGraphics, col2X + 67, ctrlY, 14, 13, "R", mouseX, mouseY);

        // Layer Filter Indicator
        int curLayer = previewRenderer.getMaxVisibleLayer();
        String layerTxt = curLayer < 0 ? "Todo" : "Y=" + curLayer;
        drawTinyButton(guiGraphics, col2X + 85, ctrlY, 12, 13, "<", mouseX, mouseY);
        guiGraphics.drawCenteredString(mc.font, "§e" + layerTxt, col2X + 115, ctrlY + 3, 0xFFFFD700);
        drawTinyButton(guiGraphics, col2X + 135, ctrlY, 12, 13, ">", mouseX, mouseY);

        // -------------------------------------------------------------
        // COLUMN 3: PERSONALIZACIÓN & MODOS (left + 284, panelTop + 16, W = 136, H = 165)
        // -------------------------------------------------------------
        int col3X = left + 284;
        int col3Y = panelTop + 16;
        int col3W = 136;
        int col3H = 165;
        drawInsetBox(guiGraphics, col3X, col3Y, col3W, col3H);

        guiGraphics.drawString(mc.font, "PERSONALIZACIÓN", col3X + 6, col3Y + 4, 0xFFFFD700, false);

        // Row 1: Modo de Construcción
        guiGraphics.drawString(mc.font, "Modo:", col3X + 6, col3Y + 16, 0xFFA0B0C4, false);
        drawSelectorButton(guiGraphics, mc, col3X + 6, col3Y + 26, col3W - 12, 14, config.getBuildMode().getDisplayName(), mouseX, mouseY);

        // Row 2: Material Principal (if mode replaces material)
        boolean usesMaterial = config.getBuildMode() != BuildingConfig.BuildMode.ORIGINAL;
        int matY = col3Y + 43;
        guiGraphics.drawString(mc.font, "Material:", col3X + 6, matY, usesMaterial ? 0xFFA0B0C4 : 0xFF606060, false);
        String matName = PALETTE_MATERIALS[selectedMaterialIdx].getPath().replace("_", " ");
        drawTinyButton(guiGraphics, col3X + 6, matY + 10, 14, 14, "<", mouseX, mouseY);
        drawSelectorButton(guiGraphics, mc, col3X + 22, matY + 10, col3W - 44, 14, usesMaterial ? capitalize(matName) : "(Original)", mouseX, mouseY);
        drawTinyButton(guiGraphics, col3X + col3W - 20, matY + 10, 14, 14, ">", mouseX, mouseY);

        // Row 3: Sustitución de familia
        int famY = col3Y + 70;
        guiGraphics.drawString(mc.font, "Sustitución:", col3X + 6, famY, usesMaterial ? 0xFFA0B0C4 : 0xFF606060, false);
        String famTxt = config.getMaterialMode() == BuildingConfig.MaterialMode.FAMILY_SUBSTITUTION ? "Por Familia" : "Literal";
        drawSelectorButton(guiGraphics, mc, col3X + 6, famY + 10, col3W - 12, 14, famTxt, mouseX, mouseY);

        // Row 4: Rotación 360° (0°, 90°, 180°, 270°)
        int rotY = col3Y + 97;
        guiGraphics.drawString(mc.font, "Rotación:", col3X + 6, rotY, 0xFFA0B0C4, false);
        int rbW = 28;
        drawToggleChip(guiGraphics, mc, col3X + 6, rotY + 10, rbW, 13, "0°", config.getRotation() == 0, mouseX, mouseY);
        drawToggleChip(guiGraphics, mc, col3X + 37, rotY + 10, rbW, 13, "90°", config.getRotation() == 90, mouseX, mouseY);
        drawToggleChip(guiGraphics, mc, col3X + 68, rotY + 10, rbW, 13, "180°", config.getRotation() == 180, mouseX, mouseY);
        drawToggleChip(guiGraphics, mc, col3X + 99, rotY + 10, rbW, 13, "270°", config.getRotation() == 270, mouseX, mouseY);

        // Row 5: Ubicación
        int plcY = col3Y + 125;
        guiGraphics.drawString(mc.font, "Ubicación:", col3X + 6, plcY, 0xFFA0B0C4, false);
        String plcLabel;
        if (config.getPlacementMode() == BuildingConfig.PlacementMode.MANUAL_COORDS) {
            BlockPos tp = config.getTargetPos();
            plcLabel = tp != null ? tp.getX() + "," + tp.getY() + "," + tp.getZ() + " ✎" : "Manual ✎";
        } else {
            plcLabel = config.getPlacementMode().getDisplayName();
        }
        drawTinyButton(guiGraphics, col3X + 6, plcY + 10, 14, 14, "<", mouseX, mouseY);
        drawSelectorButton(guiGraphics, mc, col3X + 22, plcY + 10, col3W - 44, 14, plcLabel, mouseX, mouseY);
        drawTinyButton(guiGraphics, col3X + col3W - 20, plcY + 10, 14, 14, ">", mouseX, mouseY);

        // Row 6: Botón Opciones Avanzadas / Custom
        int optY = col3Y + col3H - 16;
        drawSmallButton(guiGraphics, col3X + 6, optY, col3W - 12, 14, config.getBuildMode() == BuildingConfig.BuildMode.CUSTOM ? "★ Filtros Custom..." : "Restaurar Original", 0xFFD8E2ED, mouseX, mouseY);

        // -------------------------------------------------------------
        // BOTTOM: RESUMEN DE MATERIALES (left + 10, panelTop + 184, W = 410, H = 38)
        // -------------------------------------------------------------
        int matBoxX = left + 10;
        int matBoxY = panelTop + 184;
        int matBoxW = 410;
        int matBoxH = 38;
        drawInsetBox(guiGraphics, matBoxX, matBoxY, matBoxW, matBoxH);

        if (materialSummary != null) {
            String line1 = mc.font.plainSubstrByWidth("MATERIALES: " + materialSummary.summaryLine(), matBoxW - 12);
            guiGraphics.drawString(mc.font, line1, matBoxX + 6, matBoxY + 5, 0xFFFFD700, false);

            int dispColor = materialSummary.totalMissingBlocks() == 0 ? 0xFF3CD058 : 0xFFFFAA00;
            String line2 = "DISPONIBLES: " + materialSummary.totalAvailableBlocks()
                    + "   FALTANTES: " + materialSummary.totalMissingBlocks()
                    + "   TOTAL: " + materialSummary.totalBlocksToPlace() + " bloques";
            guiGraphics.drawString(mc.font, line2, matBoxX + 6, matBoxY + 17, 0xFF8AA8D4, false);

            var sync = parent.getSyncedData();
            boolean hasActiveProject = sync != null && ("BUILD".equalsIgnoreCase(sync.currentTaskType)
                    || (sync.currentTaskDesc != null && sync.currentTaskDesc.toLowerCase().contains("construir")));
            if (hasActiveProject && sync != null) {
                String prog = (sync.currentTaskProgress != null && !sync.currentTaskProgress.isEmpty()) ? (" (" + sync.currentTaskProgress + ")") : "";
                guiGraphics.drawString(mc.font, "§6PROYECTO EN CURSO: §a" + sync.currentTaskDesc + prog, matBoxX + 6, matBoxY + 27, 0xFF3CD058, false);
            } else {
                guiGraphics.drawString(mc.font, "ESTADO: " + materialSummary.statusLine(), matBoxX + 6, matBoxY + 27, dispColor, false);
            }
        }

        // Custom & Coords Overlays (if active)
        if (showCustomOverlay) {
            renderCustomOverlay(guiGraphics, mc, left + 40, panelTop + 30, 350, 160, mouseX, mouseY);
        } else if (showCoordsOverlay) {
            renderCoordsOverlay(guiGraphics, mc, left + 85, panelTop + 50, 250, 120, mouseX, mouseY);
        }
    }

    private void renderCustomOverlay(GuiGraphics guiGraphics, Minecraft mc, int ox, int oy, int ow, int oh, int mouseX, int mouseY) {
        guiGraphics.fill(ox, oy, ox + ow, oy + oh, 0xFA121820);
        guiGraphics.renderOutline(ox, oy, ow, oh, 0xFF3CD058);

        guiGraphics.drawString(mc.font, "FILTROS PERSONALIZADOS (MODO CUSTOM)", ox + 10, oy + 8, 0xFFFFD700, false);
        guiGraphics.drawString(mc.font, "Activa o desactiva qué elementos colocar en la construcción:", ox + 10, oy + 20, 0xFFA0B0C4, false);

        int cy1 = oy + 36;
        drawCheckbox(guiGraphics, mc, ox + 15, cy1, "Puertas", config.isKeepDoors(), mouseX, mouseY);
        drawCheckbox(guiGraphics, mc, ox + 120, cy1, "Ventanas", config.isKeepWindows(), mouseX, mouseY);
        drawCheckbox(guiGraphics, mc, ox + 225, cy1, "Techo", config.isKeepRoof(), mouseX, mouseY);

        int cy2 = oy + 56;
        drawCheckbox(guiGraphics, mc, ox + 15, cy2, "Pisos", config.isKeepFloors(), mouseX, mouseY);
        drawCheckbox(guiGraphics, mc, ox + 120, cy2, "Escaleras", config.isKeepStairs(), mouseX, mouseY);
        drawCheckbox(guiGraphics, mc, ox + 225, cy2, "Muros Int.", config.isKeepInteriorWalls(), mouseX, mouseY);

        int cy3 = oy + 76;
        drawCheckbox(guiGraphics, mc, ox + 15, cy3, "Muebles", config.isKeepFurniture(), mouseX, mouseY);
        drawCheckbox(guiGraphics, mc, ox + 120, cy3, "Decoración", config.isKeepDecor(), mouseX, mouseY);
        drawCheckbox(guiGraphics, mc, ox + 225, cy3, "Iluminación", config.isKeepLighting(), mouseX, mouseY);

        int cy4 = oy + 96;
        drawCheckbox(guiGraphics, mc, ox + 15, cy4, "Cofres", config.isKeepChests(), mouseX, mouseY);
        drawCheckbox(guiGraphics, mc, ox + 120, cy4, "Cimientos", config.isKeepFoundations(), mouseX, mouseY);

        drawSmallButton(guiGraphics, ox + ow - 70, oy + oh - 22, 60, 16, "CERRAR", 0xFF3CD058, mouseX, mouseY);
    }

    private void renderCoordsOverlay(GuiGraphics guiGraphics, Minecraft mc, int ox, int oy, int ow, int oh, int mouseX, int mouseY) {
        guiGraphics.fill(ox, oy, ox + ow, oy + oh, 0xFA121820);
        guiGraphics.renderOutline(ox, oy, ow, oh, 0xFF3CD058);

        guiGraphics.drawString(mc.font, "COORDENADAS EXACTAS (X, Y, Z)", ox + 10, oy + 8, 0xFFFFD700, false);
        guiGraphics.drawString(mc.font, "Origen de construcción en el mundo:", ox + 10, oy + 22, 0xFFA0B0C4, false);

        int inputY = oy + 38;
        guiGraphics.drawString(mc.font, "X:", ox + 18, inputY + 3, 0xFFFFFFFF, false);
        if (coordXBox != null) coordXBox.render(guiGraphics, mouseX, mouseY, 0f);

        guiGraphics.drawString(mc.font, "Y:", ox + 98, inputY + 3, 0xFFFFFFFF, false);
        if (coordYBox != null) coordYBox.render(guiGraphics, mouseX, mouseY, 0f);

        guiGraphics.drawString(mc.font, "Z:", ox + 168, inputY + 3, 0xFFFFFFFF, false);
        if (coordZBox != null) coordZBox.render(guiGraphics, mouseX, mouseY, 0f);

        int btnY = oy + 64;
        drawSmallButton(guiGraphics, ox + 15, btnY, 105, 16, "📍 Mi Posición", 0xFF8AA8D4, mouseX, mouseY);
        drawSmallButton(guiGraphics, ox + 130, btnY, 105, 16, "🤖 Pos. Esclavo", 0xFF8AA8D4, mouseX, mouseY);

        int actionY = oy + oh - 24;
        drawSmallButton(guiGraphics, ox + 15, actionY, 105, 16, "CANCELAR", 0xFFFFAA00, mouseX, mouseY);
        drawSmallButton(guiGraphics, ox + 130, actionY, 105, 16, "GUARDAR", 0xFF3CD058, mouseX, mouseY);
    }

    private void drawCheckbox(GuiGraphics guiGraphics, Minecraft mc, int x, int y, String label, boolean checked, int mouseX, int mouseY) {
        guiGraphics.fill(x, y, x + 10, y + 10, 0xFF0D1217);
        guiGraphics.renderOutline(x, y, 10, 10, checked ? 0xFF3CD058 : 0xFF505A65);
        if (checked) {
            guiGraphics.fill(x + 2, y + 2, x + 8, y + 8, 0xFF3CD058);
        }
        guiGraphics.drawString(mc.font, label, x + 14, y + 1, 0xFFD8E2ED, false);
    }

    private void SchematicEntryRow(GuiGraphics guiGraphics, Minecraft mc, SchematicLibraryManager.SchematicEntry entry, int x, int y, int w, int h, boolean selected, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = selected ? 0xFF1E354D : (hover ? 0xFF14202D : 0x00000000);
        if (bg != 0) {
            guiGraphics.fill(x, y, x + w, y + h, bg);
        }

        String icon = entry.isCustom() ? "📁" : "🏛";
        String name = mc.font.plainSubstrByWidth(entry.displayName(), w - 24);
        int textColor = selected ? 0xFFFFD700 : (hover ? 0xFFFFFFFF : 0xFFC0CAD5);

        guiGraphics.drawString(mc.font, icon + " " + name, x + 3, y + 2, textColor, false);
        guiGraphics.drawString(mc.font, "§8" + entry.dimensions().getX() + "×" + entry.dimensions().getY() + "×" + entry.dimensions().getZ() + " " + entry.format(), x + 14, y + 10, 0xFF657585, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int left, int panelTop) {
        if (showCustomOverlay) {
            int ox = left + 40;
            int oy = panelTop + 30;
            int ow = 350;
            int oh = 160;

            if (isInside(mouseX, mouseY, ox + ow - 70, oy + oh - 22, 60, 16)) {
                showCustomOverlay = false;
                recomputeTransformations();
                return true;
            }

            int cy1 = oy + 36;
            if (isInside(mouseX, mouseY, ox + 15, cy1, 70, 12)) { config.setKeepDoors(!config.isKeepDoors()); recomputeTransformations(); return true; }
            if (isInside(mouseX, mouseY, ox + 120, cy1, 70, 12)) { config.setKeepWindows(!config.isKeepWindows()); recomputeTransformations(); return true; }
            if (isInside(mouseX, mouseY, ox + 225, cy1, 70, 12)) { config.setKeepRoof(!config.isKeepRoof()); recomputeTransformations(); return true; }

            int cy2 = oy + 56;
            if (isInside(mouseX, mouseY, ox + 15, cy2, 70, 12)) { config.setKeepFloors(!config.isKeepFloors()); recomputeTransformations(); return true; }
            if (isInside(mouseX, mouseY, ox + 120, cy2, 70, 12)) { config.setKeepStairs(!config.isKeepStairs()); recomputeTransformations(); return true; }
            if (isInside(mouseX, mouseY, ox + 225, cy2, 70, 12)) { config.setKeepInteriorWalls(!config.isKeepInteriorWalls()); recomputeTransformations(); return true; }

            int cy3 = oy + 76;
            if (isInside(mouseX, mouseY, ox + 15, cy3, 70, 12)) { config.setKeepFurniture(!config.isKeepFurniture()); recomputeTransformations(); return true; }
            if (isInside(mouseX, mouseY, ox + 120, cy3, 70, 12)) { config.setKeepDecor(!config.isKeepDecor()); recomputeTransformations(); return true; }
            if (isInside(mouseX, mouseY, ox + 225, cy3, 70, 12)) { config.setKeepLighting(!config.isKeepLighting()); recomputeTransformations(); return true; }

            int cy4 = oy + 96;
            if (isInside(mouseX, mouseY, ox + 15, cy4, 70, 12)) { config.setKeepChests(!config.isKeepChests()); recomputeTransformations(); return true; }
            if (isInside(mouseX, mouseY, ox + 120, cy4, 70, 12)) { config.setKeepFoundations(!config.isKeepFoundations()); recomputeTransformations(); return true; }

            return true; // Modal blocks other clicks
        }

        if (showCoordsOverlay) {
            int ox = left + 85;
            int oy = panelTop + 50;
            int ow = 250;
            int oh = 120;

            if (coordXBox != null && coordXBox.mouseClicked(mouseX, mouseY, button)) {
                coordXBox.setFocused(true);
                if (coordYBox != null) coordYBox.setFocused(false);
                if (coordZBox != null) coordZBox.setFocused(false);
                return true;
            }
            if (coordYBox != null && coordYBox.mouseClicked(mouseX, mouseY, button)) {
                coordYBox.setFocused(true);
                if (coordXBox != null) coordXBox.setFocused(false);
                if (coordZBox != null) coordZBox.setFocused(false);
                return true;
            }
            if (coordZBox != null && coordZBox.mouseClicked(mouseX, mouseY, button)) {
                coordZBox.setFocused(true);
                if (coordXBox != null) coordXBox.setFocused(false);
                if (coordYBox != null) coordYBox.setFocused(false);
                return true;
            }

            int btnY = oy + 64;
            // Mi Posición
            if (isInside(mouseX, mouseY, ox + 15, btnY, 105, 16)) {
                net.minecraft.world.entity.player.Player p = Minecraft.getInstance().player;
                if (p != null) {
                    if (coordXBox != null) coordXBox.setValue(String.valueOf(p.getBlockX()));
                    if (coordYBox != null) coordYBox.setValue(String.valueOf(p.getBlockY()));
                    if (coordZBox != null) coordZBox.setValue(String.valueOf(p.getBlockZ()));
                }
                return true;
            }
            // Pos. Esclavo
            if (isInside(mouseX, mouseY, ox + 130, btnY, 105, 16)) {
                CompanionEntity c = parent.getMenu().getCompanion();
                if (c != null) {
                    if (coordXBox != null) coordXBox.setValue(String.valueOf(c.getBlockX()));
                    if (coordYBox != null) coordYBox.setValue(String.valueOf(c.getBlockY()));
                    if (coordZBox != null) coordZBox.setValue(String.valueOf(c.getBlockZ()));
                }
                return true;
            }

            int actionY = oy + oh - 24;
            // CANCELAR
            if (isInside(mouseX, mouseY, ox + 15, actionY, 105, 16)) {
                showCoordsOverlay = false;
                return true;
            }
            // GUARDAR
            if (isInside(mouseX, mouseY, ox + 130, actionY, 105, 16)) {
                applyCoordsInput();
                return true;
            }

            return true; // Modal blocks other clicks
        }

        int col1X = left + 10;
        int col1Y = panelTop + 16;
        int col1W = 114;
        int col1H = 165;

        // Search Box focus
        if (searchBox != null && searchBox.mouseClicked(mouseX, mouseY, button)) {
            searchBox.setFocused(true);
            return true;
        }

        // Library Item Click
        List<SchematicLibraryManager.SchematicEntry> entries = SchematicLibraryManager.getEntries(currentSearch);
        int listY = col1Y + 18;
        int rowH = 18;

        for (int i = 0; i < VISIBLE_LIBRARY_ROWS; i++) {
            int entryIdx = libraryScrollOffset + i;
            if (entryIdx >= entries.size()) break;
            int ry = listY + i * rowH;
            if (isInside(mouseX, mouseY, col1X + 2, ry, col1W - 4, rowH)) {
                this.selectedLibraryIdx = entryIdx;
                selectSchematic(entries.get(entryIdx));
                return true;
            }
        }

        // Library Buttons: Actualizar & Carpeta
        int libBtnY = col1Y + col1H - 16;
        if (isInside(mouseX, mouseY, col1X + 2, libBtnY, 52, 14)) {
            SchematicLibraryManager.refreshLibrary();
            recomputeTransformations();
            return true;
        }
        if (isInside(mouseX, mouseY, col1X + 58, libBtnY, 54, 14)) {
            SchematicLibraryManager.openSchematicsFolder();
            return true;
        }

        // Column 2 (Center Preview Controls)
        int col2X = left + 128;
        int col2Y = panelTop + 16;
        int col2W = 152;
        int col2H = 165;
        int ctrlY = col2Y + col2H - 16;

        if (isInside(mouseX, mouseY, col2X + 3, ctrlY, 14, 13)) { previewRenderer.rotateLeft(); return true; }
        if (isInside(mouseX, mouseY, col2X + 19, ctrlY, 14, 13)) { previewRenderer.rotateRight(); return true; }
        if (isInside(mouseX, mouseY, col2X + 35, ctrlY, 14, 13)) { previewRenderer.zoomIn(); return true; }
        if (isInside(mouseX, mouseY, col2X + 51, ctrlY, 14, 13)) { previewRenderer.zoomOut(); return true; }
        if (isInside(mouseX, mouseY, col2X + 67, ctrlY, 14, 13)) { previewRenderer.resetCamera(); return true; }

        if (isInside(mouseX, mouseY, col2X + 85, ctrlY, 12, 13)) { previewRenderer.prevLayer(); return true; }
        if (isInside(mouseX, mouseY, col2X + 135, ctrlY, 12, 13)) { previewRenderer.nextLayer(); return true; }

        // Mouse Drag initiation on 3D Box
        int previewBoxY = col2Y + 24;
        int previewBoxH = col2H - 42;
        if (previewRenderer.mouseClicked(mouseX, mouseY, button, col2X + 2, previewBoxY, col2W - 4, previewBoxH)) {
            return true;
        }

        // Column 3 (Right Options)
        int col3X = left + 284;
        int col3Y = panelTop + 16;
        int col3W = 136;
        int col3H = 165;

        // Build Mode cycle
        if (isInside(mouseX, mouseY, col3X + 6, col3Y + 26, col3W - 12, 14)) {
            BuildingConfig.BuildMode[] modes = BuildingConfig.BuildMode.values();
            int nextOrdinal = (config.getBuildMode().ordinal() + 1) % modes.length;
            config.setBuildMode(modes[nextOrdinal]);
            recomputeTransformations();
            return true;
        }

        // Material cycle & arrows
        int matY = col3Y + 43;
        if (isInside(mouseX, mouseY, col3X + 6, matY + 10, 14, 14)) {
            selectedMaterialIdx = (selectedMaterialIdx - 1 + PALETTE_MATERIALS.length) % PALETTE_MATERIALS.length;
            config.setPrimaryMaterial(PALETTE_MATERIALS[selectedMaterialIdx]);
            recomputeTransformations();
            return true;
        }
        if (isInside(mouseX, mouseY, col3X + col3W - 20, matY + 10, 14, 14)) {
            selectedMaterialIdx = (selectedMaterialIdx + 1) % PALETTE_MATERIALS.length;
            config.setPrimaryMaterial(PALETTE_MATERIALS[selectedMaterialIdx]);
            recomputeTransformations();
            return true;
        }
        if (isInside(mouseX, mouseY, col3X + 22, matY + 10, col3W - 44, 14)) {
            selectedMaterialIdx = (selectedMaterialIdx + 1) % PALETTE_MATERIALS.length;
            config.setPrimaryMaterial(PALETTE_MATERIALS[selectedMaterialIdx]);
            recomputeTransformations();
            return true;
        }

        // Substitution mode toggle
        int famY = col3Y + 70;
        if (isInside(mouseX, mouseY, col3X + 6, famY + 10, col3W - 12, 14)) {
            BuildingConfig.MaterialMode next = config.getMaterialMode() == BuildingConfig.MaterialMode.FAMILY_SUBSTITUTION
                    ? BuildingConfig.MaterialMode.LITERAL : BuildingConfig.MaterialMode.FAMILY_SUBSTITUTION;
            config.setMaterialMode(next);
            recomputeTransformations();
            return true;
        }

        // Rotation chips
        int rotY = col3Y + 97;
        int rbW = 28;
        if (isInside(mouseX, mouseY, col3X + 6, rotY + 10, rbW, 13)) { config.setRotation(0); recomputeTransformations(); return true; }
        if (isInside(mouseX, mouseY, col3X + 37, rotY + 10, rbW, 13)) { config.setRotation(90); recomputeTransformations(); return true; }
        if (isInside(mouseX, mouseY, col3X + 68, rotY + 10, rbW, 13)) { config.setRotation(180); recomputeTransformations(); return true; }
        if (isInside(mouseX, mouseY, col3X + 99, rotY + 10, rbW, 13)) { config.setRotation(270); recomputeTransformations(); return true; }

        // Placement mode cycle & arrows
        int plcY = col3Y + 125;
        if (isInside(mouseX, mouseY, col3X + 6, plcY + 10, 14, 14)) {
            BuildingConfig.PlacementMode[] places = BuildingConfig.PlacementMode.values();
            int prevOrdinal = (config.getPlacementMode().ordinal() - 1 + places.length) % places.length;
            config.setPlacementMode(places[prevOrdinal]);
            if (config.getPlacementMode() == BuildingConfig.PlacementMode.MANUAL_COORDS) {
                openCoordsOverlay();
            }
            saveConfigState();
            return true;
        }
        if (isInside(mouseX, mouseY, col3X + col3W - 20, plcY + 10, 14, 14)) {
            BuildingConfig.PlacementMode[] places = BuildingConfig.PlacementMode.values();
            int nextOrdinal = (config.getPlacementMode().ordinal() + 1) % places.length;
            config.setPlacementMode(places[nextOrdinal]);
            if (config.getPlacementMode() == BuildingConfig.PlacementMode.MANUAL_COORDS) {
                openCoordsOverlay();
            }
            saveConfigState();
            return true;
        }
        if (isInside(mouseX, mouseY, col3X + 22, plcY + 10, col3W - 44, 14)) {
            if (config.getPlacementMode() == BuildingConfig.PlacementMode.MANUAL_COORDS) {
                openCoordsOverlay();
            } else {
                BuildingConfig.PlacementMode[] places = BuildingConfig.PlacementMode.values();
                int nextOrdinal = (config.getPlacementMode().ordinal() + 1) % places.length;
                config.setPlacementMode(places[nextOrdinal]);
                if (config.getPlacementMode() == BuildingConfig.PlacementMode.MANUAL_COORDS) {
                    openCoordsOverlay();
                }
                saveConfigState();
            }
            return true;
        }

        // Custom Filters / Restore Button
        int optY = col3Y + col3H - 16;
        if (isInside(mouseX, mouseY, col3X + 6, optY, col3W - 12, 14)) {
            if (config.getBuildMode() == BuildingConfig.BuildMode.CUSTOM) {
                showCustomOverlay = true;
            } else {
                config.setBuildMode(BuildingConfig.BuildMode.ORIGINAL);
                config.setRotation(0);
                config.setMaterialMode(BuildingConfig.MaterialMode.FAMILY_SUBSTITUTION);
                recomputeTransformations();
            }
            return true;
        }

        return false;
    }

    private void applyCoordsInput() {
        try {
            int x = coordXBox != null && !coordXBox.getValue().trim().isEmpty() ? Integer.parseInt(coordXBox.getValue().trim()) : 0;
            int y = coordYBox != null && !coordYBox.getValue().trim().isEmpty() ? Integer.parseInt(coordYBox.getValue().trim()) : 64;
            int z = coordZBox != null && !coordZBox.getValue().trim().isEmpty() ? Integer.parseInt(coordZBox.getValue().trim()) : 0;
            config.setTargetPos(new BlockPos(x, y, z));
        } catch (Exception ignored) {}
        showCoordsOverlay = false;
        saveConfigState();
        recomputeTransformations();
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return previewRenderer.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, int left, int panelTop) {
        int col2X = left + 128;
        int col2Y = panelTop + 16;
        int col2W = 152;
        int col2H = 165;
        int previewBoxY = col2Y + 24;
        int previewBoxH = col2H - 42;
        return previewRenderer.mouseDragged(mouseX, mouseY, button, dragX, dragY, col2X + 2, previewBoxY, col2W - 4, previewBoxH);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta, int left, int panelTop) {
        int col1X = left + 10;
        int col1Y = panelTop + 16;
        int col1W = 114;
        int col1H = 165;

        // Scroll Library List
        if (isInside(mouseX, mouseY, col1X, col1Y, col1W, col1H)) {
            List<SchematicLibraryManager.SchematicEntry> entries = SchematicLibraryManager.getEntries(currentSearch);
            int maxScroll = Math.max(0, entries.size() - VISIBLE_LIBRARY_ROWS);
            if (delta > 0 && libraryScrollOffset > 0) {
                libraryScrollOffset--;
                return true;
            } else if (delta < 0 && libraryScrollOffset < maxScroll) {
                libraryScrollOffset++;
                return true;
            }
        }

        // Scroll Zoom on 3D Box
        int col2X = left + 128;
        int col2Y = panelTop + 16;
        int col2W = 152;
        int col2H = 165;
        int previewBoxY = col2Y + 24;
        int previewBoxH = col2H - 42;
        return previewRenderer.mouseScrolled(mouseX, mouseY, delta, col2X + 2, previewBoxY, col2W - 4, previewBoxH);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (showCoordsOverlay) {
            if (coordXBox != null && coordXBox.isFocused()) return coordXBox.charTyped(codePoint, modifiers);
            if (coordYBox != null && coordYBox.isFocused()) return coordYBox.charTyped(codePoint, modifiers);
            if (coordZBox != null && coordZBox.isFocused()) return coordZBox.charTyped(codePoint, modifiers);
            return false;
        }
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showCustomOverlay) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                showCustomOverlay = false;
                return true;
            }
        }
        if (showCoordsOverlay) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                showCoordsOverlay = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyCoordsInput();
                return true;
            }
            if (coordXBox != null && coordXBox.isFocused()) return coordXBox.keyPressed(keyCode, scanCode, modifiers);
            if (coordYBox != null && coordYBox.isFocused()) return coordYBox.keyPressed(keyCode, scanCode, modifiers);
            if (coordZBox != null && coordZBox.isFocused()) return coordZBox.keyPressed(keyCode, scanCode, modifiers);
            return false;
        }
        if (searchBox != null && searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchBox.setFocused(false);
                return true;
            }
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean isSearchFocused() {
        return (searchBox != null && searchBox.isFocused())
                || (coordXBox != null && coordXBox.isFocused())
                || (coordYBox != null && coordYBox.isFocused())
                || (coordZBox != null && coordZBox.isFocused());
    }

    // Helper Graphics
    private void drawInsetBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF0D1217);
        g.renderOutline(x, y, w, h, 0xFF2A3644);
    }

    private void drawSmallButton(GuiGraphics g, int x, int y, int w, int h, String text, int color, int mouseX, int mouseY) {
        boolean hover = isInside(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? 0xFF2B3A4C : 0xFF16212D);
        g.renderOutline(x, y, w, h, hover ? 0xFF4A6584 : 0xFF2A3644);
        g.drawCenteredString(Minecraft.getInstance().font, text, x + w / 2, y + 3, color);
    }

    private void drawTinyButton(GuiGraphics g, int x, int y, int w, int h, String text, int mouseX, int mouseY) {
        boolean hover = isInside(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? 0xFF354B63 : 0xFF1A2633);
        g.renderOutline(x, y, w, h, hover ? 0xFF54779E : 0xFF2C3E52);
        g.drawCenteredString(Minecraft.getInstance().font, text, x + w / 2, y + 3, hover ? 0xFFFFD700 : 0xFFD8E2ED);
    }

    private void drawSelectorButton(GuiGraphics g, Minecraft mc, int x, int y, int w, int h, String text, int mouseX, int mouseY) {
        boolean hover = isInside(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? 0xFF243242 : 0xFF141D26);
        g.renderOutline(x, y, w, h, hover ? 0xFF4A6584 : 0xFF2A3644);
        String label = mc.font.plainSubstrByWidth(text, w - 8);
        g.drawCenteredString(mc.font, "§f" + label, x + w / 2, y + 3, 0xFFFFFFFF);
    }

    private void drawToggleChip(GuiGraphics g, Minecraft mc, int x, int y, int w, int h, String text, boolean active, int mouseX, int mouseY) {
        boolean hover = isInside(mouseX, mouseY, x, y, w, h);
        int bg = active ? 0xFF1E422B : (hover ? 0xFF253342 : 0xFF121B24);
        int border = active ? 0xFF3CD058 : (hover ? 0xFF4A6584 : 0xFF2A3644);
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, border);
        g.drawCenteredString(mc.font, active ? "§a" + text : "§7" + text, x + w / 2, y + 3, active ? 0xFF3CD058 : 0xFFA0A0A0);
    }

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return Character.toUpperCase(str.charAt(0)) + (str.length() > 1 ? str.substring(1) : "");
    }
}
