package com.aicompanion.client.gui.building;

import com.aicompanion.ai.building.Blueprint;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.opengl.GL11;

import java.util.*;

/**
 * Enterprise 3D Isometric Real-time Preview Renderer for Esclavo.
 * Renders transformed blueprint blocks with mouse rotation (pitch/yaw), zoom,
 * layer-by-layer interior inspection, scissor clipping, and fast occlusion culling.
 */
public class SchematicPreviewRenderer {

    private float pitch = 25.0f;
    private float yaw = -45.0f;
    private float zoom = 1.0f;

    // Layer inspection (-1 means show all layers)
    private int maxVisibleLayer = -1;
    private int totalLayers = 1;

    // Drag tracking
    private boolean isDragging = false;
    private double lastMouseX = 0;
    private double lastMouseY = 0;

    // Geometry Cache
    private Blueprint lastRenderedBlueprint = null;
    private int lastRenderedLayer = -2;
    private final List<Blueprint.BlockEntry> culledBlocks = new ArrayList<>();

    public void resetCamera() {
        this.pitch = 25.0f;
        this.yaw = -45.0f;
        this.zoom = 1.0f;
        this.maxVisibleLayer = -1;
    }

    public void rotateLeft() { this.yaw -= 45.0f; }
    public void rotateRight() { this.yaw += 45.0f; }
    public void zoomIn() { this.zoom = Math.min(4.0f, this.zoom * 1.25f); }
    public void zoomOut() { this.zoom = Math.max(0.4f, this.zoom * 0.8f); }

    public void setMaxVisibleLayer(int layer) {
        this.maxVisibleLayer = layer;
        this.lastRenderedLayer = -2; // Force re-cull
    }

    public int getMaxVisibleLayer() { return maxVisibleLayer; }
    public int getTotalLayers() { return totalLayers; }

    public void nextLayer() {
        if (maxVisibleLayer < 0) {
            maxVisibleLayer = 0;
        } else if (maxVisibleLayer < totalLayers - 1) {
            maxVisibleLayer++;
        }
        this.lastRenderedLayer = -2;
    }

    public void prevLayer() {
        if (maxVisibleLayer > 0) {
            maxVisibleLayer--;
        } else if (maxVisibleLayer == 0) {
            maxVisibleLayer = -1; // Show all
        }
        this.lastRenderedLayer = -2;
    }

    public void showAllLayers() {
        this.maxVisibleLayer = -1;
        this.lastRenderedLayer = -2;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int boxX, int boxY, int boxW, int boxH) {
        if (mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH) {
            if (button == 0 || button == 1) {
                this.isDragging = true;
                this.lastMouseX = mouseX;
                this.lastMouseY = mouseY;
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isDragging) {
            this.isDragging = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, int boxX, int boxY, int boxW, int boxH) {
        if (this.isDragging) {
            float dx = (float) (mouseX - this.lastMouseX);
            float dy = (float) (mouseY - this.lastMouseY);
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;

            this.yaw += dx * 1.2f;
            this.pitch = Math.max(-85.0f, Math.min(85.0f, this.pitch + dy * 1.2f));
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta, int boxX, int boxY, int boxW, int boxH) {
        if (mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH) {
            if (delta > 0) {
                zoomIn();
            } else if (delta < 0) {
                zoomOut();
            }
            return true;
        }
        return false;
    }

    public void render(GuiGraphics guiGraphics, Blueprint blueprint, int boxX, int boxY, int boxW, int boxH) {
        if (blueprint == null || blueprint.getBlocks().isEmpty()) {
            return;
        }

        Vec3i dims = blueprint.getDimensions();
        this.totalLayers = Math.max(1, dims.getY());

        // Recompute culling if blueprint or layer filter changed
        if (blueprint != this.lastRenderedBlueprint || this.maxVisibleLayer != this.lastRenderedLayer) {
            recomputeCulling(blueprint);
            this.lastRenderedBlueprint = blueprint;
            this.lastRenderedLayer = this.maxVisibleLayer;
        }

        if (culledBlocks.isEmpty()) {
            return;
        }

        // 1. Scissor test to strictly bound 3D preview within panel box
        guiGraphics.enableScissor(boxX, boxY, boxX + boxW, boxY + boxH);

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();

        RenderSystem.enableDepthTest();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, false);
        Lighting.setupFor3DItems();

        int centerX = boxX + boxW / 2;
        int centerY = boxY + boxH / 2;

        // Auto-scale to fit comfortably inside the box
        float maxDim = Math.max(dims.getX(), Math.max(dims.getY(), dims.getZ()));
        float baseScale = Math.max(2.5f, Math.min(22.0f, (boxW * 0.75f) / Math.max(1.0f, maxDim)));
        float finalScale = baseScale * this.zoom;

        pose.translate(centerX, centerY, 150.0);
        pose.scale(finalScale, -finalScale, finalScale); // Invert Y for GUI coordinate system
        pose.mulPose(Axis.XP.rotationDegrees(this.pitch));
        pose.mulPose(Axis.YP.rotationDegrees(this.yaw));
        pose.translate(-dims.getX() / 2.0, -dims.getY() / 2.0, -dims.getZ() / 2.0);

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        // Render visible blocks
        int renderedCount = 0;
        int maxToRender = 4000; // Safeguard against freezing on colossal structures
        int stride = culledBlocks.size() > maxToRender ? (culledBlocks.size() / maxToRender) + 1 : 1;

        for (int i = 0; i < culledBlocks.size(); i += stride) {
            Blueprint.BlockEntry entry = culledBlocks.get(i);
            pose.pushPose();
            Vec3i offset = entry.offset();
            pose.translate(offset.getX(), offset.getY(), offset.getZ());
            dispatcher.renderSingleBlock(entry.state(), pose, guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            renderedCount++;
        }

        guiGraphics.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, false);
        Lighting.setupForFlatItems();
        RenderSystem.disableDepthTest();

        pose.popPose();
        guiGraphics.disableScissor();
    }

    private void recomputeCulling(Blueprint blueprint) {
        culledBlocks.clear();
        List<Blueprint.BlockEntry> all = blueprint.getBlocks();
        if (all.isEmpty()) return;

        Vec3i dims = blueprint.getDimensions();
        int sx = dims.getX() + 1;
        int sy = dims.getY() + 1;
        int sz = dims.getZ() + 1;

        // Build 3D spatial index for fast neighbor checking
        boolean[][][] grid = new boolean[sx][sy][sz];
        for (Blueprint.BlockEntry entry : all) {
            Vec3i pos = entry.offset();
            if (maxVisibleLayer >= 0 && pos.getY() > maxVisibleLayer) continue;
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if (x >= 0 && x < sx && y >= 0 && y < sy && z >= 0 && z < sz) {
                grid[x][y][z] = true;
            }
        }

        for (Blueprint.BlockEntry entry : all) {
            Vec3i pos = entry.offset();
            if (maxVisibleLayer >= 0 && pos.getY() > maxVisibleLayer) continue;

            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            // Check if hidden by 6 solid neighbors
            boolean fullyHidden = x > 0 && x < sx - 1 && y > 0 && y < sy - 1 && z > 0 && z < sz - 1
                    && grid[x + 1][y][z] && grid[x - 1][y][z]
                    && grid[x][y + 1][z] && grid[x][y - 1][z]
                    && grid[x][y][z + 1] && grid[x][y][z - 1];

            if (!fullyHidden) {
                culledBlocks.add(entry);
            }
        }
    }
}
