package com.aicompanion.client.gui.widgets;

import com.aicompanion.ai.parser.ItemSearchRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dropdown autocomplete widget for Esclavo's Order input.
 * Replaces narrow fixed chips with a full-width, scrollable suggestions list
 * with icon preview, category badges, keyboard navigation (Up/Down/Tab/Enter/Esc),
 * and tooltips for long text.
 */
public class EsclabSuggestionDropdown extends AbstractWidget {
    private final List<ItemSearchRegistry.SmartSuggestion> suggestions = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private final int maxVisibleRows = 5;
    private final int rowHeight = 18;
    private final Consumer<ItemSearchRegistry.SmartSuggestion> onSelect;
    private String hoveredTooltip = null;

    public EsclabSuggestionDropdown(int x, int y, int width, Consumer<ItemSearchRegistry.SmartSuggestion> onSelect) {
        super(x, y, width, 5 * 18 + 4, Component.literal("Sugerencias"));
        this.onSelect = onSelect;
        this.visible = false;
        this.active = false;
    }

    public void setSuggestions(List<ItemSearchRegistry.SmartSuggestion> newSuggestions) {
        this.suggestions.clear();
        if (newSuggestions != null) {
            // Deduplicate suggestions by label & value (1.13.8: O(n) con sets; la lista puede tener miles de objetos de mods)
            java.util.Set<String> seenLabels = new java.util.HashSet<>();
            java.util.Set<String> seenVals = new java.util.HashSet<>();
            for (var s : newSuggestions) {
                String l = s.label() == null ? "" : s.label().toLowerCase(java.util.Locale.ROOT);
                String v = s.value() == null ? "" : s.value().toLowerCase(java.util.Locale.ROOT);
                if (seenLabels.contains(l) || seenVals.contains(v)) continue;
                seenLabels.add(l);
                seenVals.add(v);
                suggestions.add(s);
            }
        }

        this.scrollOffset = 0;
        this.selectedIndex = suggestions.isEmpty() ? -1 : 0;
        this.setHeight(Math.min(maxVisibleRows, Math.max(1, suggestions.size())) * rowHeight + 4);
        this.visible = !this.suggestions.isEmpty();
        this.active = !this.suggestions.isEmpty();
    }

    public boolean isOpen() {
        return this.visible && !this.suggestions.isEmpty();
    }

    public void close() {
        this.visible = false;
        this.active = false;
        this.selectedIndex = -1;
    }

    public boolean handleKeyPressed(int keyCode) {
        if (!isOpen()) return false;

        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (selectedIndex < suggestions.size() - 1) {
                selectedIndex++;
                if (selectedIndex >= scrollOffset + maxVisibleRows) {
                    scrollOffset = selectedIndex - maxVisibleRows + 1;
                }
            } else {
                selectedIndex = 0;
                scrollOffset = 0;
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_UP) {
            if (selectedIndex > 0) {
                selectedIndex--;
                if (selectedIndex < scrollOffset) {
                    scrollOffset = selectedIndex;
                }
            } else {
                selectedIndex = suggestions.size() - 1;
                scrollOffset = Math.max(0, suggestions.size() - maxVisibleRows);
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            int targetIdx = selectedIndex >= 0 ? selectedIndex : (keyCode == GLFW.GLFW_KEY_TAB && !suggestions.isEmpty() ? 0 : -1);
            if (targetIdx >= 0 && targetIdx < suggestions.size()) {
                applySelection(suggestions.get(targetIdx));
                return true;
            }
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return false;
    }

    private void applySelection(ItemSearchRegistry.SmartSuggestion suggestion) {
        if (onSelect != null && suggestion != null) {
            onSelect.accept(suggestion);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen() || !this.isMouseOver(mouseX, mouseY)) return false;

        int relY = (int) mouseY - (this.getY() + 2);
        if (relY >= 0) {
            int clickedRow = scrollOffset + (relY / rowHeight);
            if (clickedRow >= 0 && clickedRow < suggestions.size()) {
                selectedIndex = clickedRow;
                applySelection(suggestions.get(clickedRow));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isOpen() || !this.isMouseOver(mouseX, mouseY)) return false;

        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (delta < 0 && scrollOffset + maxVisibleRows < suggestions.size()) {
            scrollOffset++;
            return true;
        }
        return false;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!isOpen()) return;

        hoveredTooltip = null;
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        Font font = Minecraft.getInstance().font;

        // Panel background with shadow & border
        guiGraphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0E121A); // Dark outer edge
        guiGraphics.fill(x, y, x + w, y + h, 0xF8161D28);                 // Dark inner fill
        guiGraphics.renderOutline(x, y, w, h, 0xFF35445A);                 // Border

        int visibleCount = Math.min(maxVisibleRows, suggestions.size() - scrollOffset);
        for (int i = 0; i < visibleCount; i++) {
            int dataIndex = scrollOffset + i;
            if (dataIndex >= suggestions.size()) break;

            ItemSearchRegistry.SmartSuggestion sugg = suggestions.get(dataIndex);
            int rowY = y + 2 + i * rowHeight;
            boolean isSelected = dataIndex == selectedIndex;
            boolean isMouseOverRow = mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY < rowY + rowHeight;

            // Highlight bar
            if (isSelected) {
                guiGraphics.fill(x + 2, rowY, x + w - 2, rowY + rowHeight, 0x44FFD700); // Gold translucent
                guiGraphics.fill(x + 2, rowY, x + 4, rowY + rowHeight, 0xFFFFD700);     // Solid gold left marker
            } else if (isMouseOverRow) {
                guiGraphics.fill(x + 2, rowY, x + w - 2, rowY + rowHeight, 0x228AA8D4); // Soft blue highlight
            }

            // Draw Item Icon if applicable
            ItemStack iconStack = resolveIcon(sugg);
            int textStartX = x + 6;
            if (!iconStack.isEmpty()) {
                guiGraphics.renderFakeItem(iconStack, x + 6, rowY + 1);
                textStartX = x + 26;
            }

            // Category badge on right side
            String badge = getBadge(sugg.category());
            int badgeColor = getBadgeColor(sugg.category());
            int badgeWidth = font.width(badge);
            int badgeX = x + w - badgeWidth - 8;
            guiGraphics.drawString(font, badge, badgeX, rowY + 5, badgeColor, false);

            // Item Label (with ellipsis truncation if too long for available space)
            int maxTextWidth = badgeX - textStartX - 6;
            String label = sugg.label();
            String displayLabel = font.plainSubstrByWidth(label, maxTextWidth);
            if (displayLabel.length() < label.length()) {
                displayLabel = font.plainSubstrByWidth(label, maxTextWidth - 8) + "...";
                if (isMouseOverRow) {
                    hoveredTooltip = label;
                }
            }

            int textColor = isSelected ? 0xFFFFD700 : (isMouseOverRow ? 0xFFFFFFFF : 0xFFCCD8E6);
            guiGraphics.drawString(font, displayLabel, textStartX, rowY + 5, textColor, false);
        }

        // Scrollbar if more items than maxVisibleRows
        if (suggestions.size() > maxVisibleRows) {
            int sbX = x + w - 5;
            int sbY = y + 2;
            int sbH = h - 4;
            guiGraphics.fill(sbX, sbY, sbX + 3, sbY + sbH, 0xFF1B2330);

            float scrollFraction = (float) scrollOffset / (suggestions.size() - maxVisibleRows);
            int thumbH = Math.max(10, (int) ((float) maxVisibleRows / suggestions.size() * sbH));
            int thumbY = sbY + (int) (scrollFraction * (sbH - thumbH));
            guiGraphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFF587093);
        }

        // Render Tooltip for truncated text
        if (hoveredTooltip != null) {
            guiGraphics.renderTooltip(font, Component.literal(hoveredTooltip), mouseX, mouseY);
        }
    }

    private ItemStack resolveIcon(ItemSearchRegistry.SmartSuggestion sugg) {
        String canon = sugg.canonicalId();
        if (canon != null && canon.contains(":")) {
            try {
                ResourceLocation direct = new ResourceLocation(canon.trim());
                if (BuiltInRegistries.ITEM.containsKey(direct)) {
                    Item it = BuiltInRegistries.ITEM.get(direct);
                    if (it != Items.AIR) {
                        return new ItemStack(it);
                    }
                }
                if (BuiltInRegistries.BLOCK.containsKey(direct)) {
                    var blk = BuiltInRegistries.BLOCK.get(direct);
                    if (blk != net.minecraft.world.level.block.Blocks.AIR && blk.asItem() != Items.AIR) {
                        return new ItemStack(blk);
                    }
                }
            } catch (Exception ignored) {}
        }
        String val = sugg.insertText().toLowerCase();
        // Blocks & Stones
        if (val.contains("ladrillo")) return new ItemStack(Items.STONE_BRICKS);
        if (val.contains("adoquin") || val.contains("cobble")) return new ItemStack(Items.COBBLESTONE);
        if (val.contains("pizarra") || val.contains("deepslate")) return new ItemStack(Items.COBBLED_DEEPSLATE);
        if (val.contains("arenisca") || val.contains("sandstone")) return new ItemStack(Items.SANDSTONE);

        // Woods
        if (val.contains("cerezo")) return new ItemStack(Items.CHERRY_PLANKS);
        if (val.contains("roble_oscuro") || val.contains("oscuro")) return new ItemStack(Items.DARK_OAK_PLANKS);
        if (val.contains("abedul")) return new ItemStack(Items.BIRCH_PLANKS);
        if (val.contains("abeto")) return new ItemStack(Items.SPRUCE_PLANKS);
        if (val.contains("acacia")) return new ItemStack(Items.ACACIA_PLANKS);
        if (val.contains("jungla")) return new ItemStack(Items.JUNGLE_PLANKS);
        if (val.contains("manglar") || val.contains("mangle")) return new ItemStack(Items.MANGROVE_PLANKS);
        if (val.contains("bambu")) return new ItemStack(Items.BAMBOO_PLANKS);
        if (val.contains("roble") || val.contains("madera") || val.contains("arbol")) return new ItemStack(Items.OAK_LOG);

        // Ores & Minerals
        if (val.contains("oro")) return new ItemStack(Items.RAW_GOLD);
        if (val.contains("hierro")) return new ItemStack(Items.RAW_IRON);
        if (val.contains("diamante")) return new ItemStack(Items.DIAMOND);
        if (val.contains("carbon")) return new ItemStack(Items.COAL);
        if (val.contains("cobre")) return new ItemStack(Items.RAW_COPPER);
        if (val.contains("redstone")) return new ItemStack(Items.REDSTONE);
        if (val.contains("esmeralda")) return new ItemStack(Items.EMERALD);
        if (val.contains("lapiz") || val.contains("lapis")) return new ItemStack(Items.LAPIS_LAZULI);
        if (val.contains("netherita") || val.contains("debris")) return new ItemStack(Items.ANCIENT_DEBRIS);
        if (val.contains("obsidiana")) return new ItemStack(Items.OBSIDIAN);
        if (val.contains("piedra")) return new ItemStack(Items.STONE);

        // Crops & Farm
        if (val.contains("trigo")) return new ItemStack(Items.WHEAT);
        if (val.contains("zanahoria")) return new ItemStack(Items.CARROT);
        if (val.contains("patata") || val.contains("papa")) return new ItemStack(Items.POTATO);
        if (val.contains("semilla")) return new ItemStack(Items.WHEAT_SEEDS);
        if (val.contains("cana") || val.contains("azucar")) return new ItemStack(Items.SUGAR_CANE);
        if (val.contains("flor") || val.contains("amapola")) return new ItemStack(Items.POPPY);
        if (val.contains("manzana")) return new ItemStack(Items.APPLE);
        if (val.contains("sandia")) return new ItemStack(Items.MELON_SLICE);

        // Mobs & Drops
        if (val.contains("zombie")) return new ItemStack(Items.ROTTEN_FLESH);
        if (val.contains("esqueleto")) return new ItemStack(Items.BONE);
        if (val.contains("tinta") || val.contains("calamar")) return new ItemStack(Items.INK_SAC);
        if (val.contains("cuero") || val.contains("vaca") || val.contains("bife")) return new ItemStack(Items.LEATHER);
        if (val.contains("cerdo")) return new ItemStack(Items.PORKCHOP);
        if (val.contains("oveja") || val.contains("lana")) return new ItemStack(Items.WHITE_WOOL);
        if (val.contains("pollo") || val.contains("pluma")) return new ItemStack(Items.FEATHER);
        if (val.contains("creeper") || val.contains("polvora")) return new ItemStack(Items.GUNPOWDER);
        if (val.contains("arana")) return new ItemStack(Items.SPIDER_EYE);
        if (val.contains("enderman") || val.contains("perla")) return new ItemStack(Items.ENDER_PEARL);
        if (val.contains("blaze")) return new ItemStack(Items.BLAZE_ROD);

        // Actions & Utilities
        if (val.contains("minar") || val.contains("picar")) return new ItemStack(Items.IRON_PICKAXE);
        if (val.contains("talar")) return new ItemStack(Items.IRON_AXE);
        if (val.contains("cazar") || val.contains("matar")) return new ItemStack(Items.IRON_SWORD);
        if (val.contains("recoger") || val.contains("cosecha")) return new ItemStack(Items.IRON_HOE);
        if (val.contains("ir a") || val.contains("viaja")) return new ItemStack(Items.COMPASS);
        if (val.contains("sigueme") || val.contains("seguir")) return new ItemStack(Items.LEAD);
        if (val.contains("espera")) return new ItemStack(Items.CLOCK);
        if (val.contains("baul") || val.contains("cofre")) return new ItemStack(Items.CHEST);
        if (val.contains("casa") || val.contains("base") || val.contains("construir")) return new ItemStack(Items.CRAFTING_TABLE);

        // Try built-in registry lookup
        ResourceLocation id = ItemSearchRegistry.resolve(val);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(id));
        }

        return ItemStack.EMPTY;
    }

    private String getBadge(String category) {
        if (category == null) return "";
        return switch (category.toUpperCase()) {
            case "ACTION" -> "[ACCIÓN]";
            case "SUBTYPE" -> "[TIPO]";
            case "QUANTITY" -> "[CANTIDAD]";
            case "DESTINATION" -> "[DESTINO]";
            case "ITEM" -> "[OBJETO]";
            default -> "";
        };
    }

    private int getBadgeColor(String category) {
        if (category == null) return 0xFF8AA8D4;
        return switch (category.toUpperCase()) {
            case "ACTION" -> 0xFF3CD058;      // Green
            case "SUBTYPE" -> 0xFFFFD700;     // Gold
            case "QUANTITY" -> 0xFF00E5FF;    // Cyan
            case "DESTINATION" -> 0xFFFFAA00; // Orange
            case "ITEM" -> 0xFFA0B0C4;        // Gray blue
            default -> 0xFF8AA8D4;
        };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
