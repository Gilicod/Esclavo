package com.aicompanion.ai.building;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;

/**
 * Enterprise Permanent Build Project Anchor for Esclavo.
 * Encapsulates full identity, world coordinates, dimension, configuration,
 * and block progress for an in-progress or completed building project.
 * Guarantees that neither companion death, owner death, nor restarts lose
 * the project origin or cause duplicate builds in wrong places.
 */
public class BuildProject {

    public enum Status {
        NOT_STARTED("No iniciado"),
        IN_PROGRESS("En construcción"),
        PAUSED("Pausado"),
        COMPLETED("Completado"),
        CANCELLED("Cancelado");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private String projectId;
    private String blueprintId;
    private String blueprintName;
    private BlockPos originPos;
    private ResourceLocation dimension;
    private BuildingConfig buildingConfig;
    private int currentBlockIndex;
    private int totalBlocks;
    private Status status;
    private long createdTime;
    private long lastActiveTime;

    public BuildProject(String blueprintId, String blueprintName, BlockPos originPos, ResourceLocation dimension, BuildingConfig config, int totalBlocks) {
        this.projectId = "PROJ-" + Long.toHexString(System.currentTimeMillis() & 0xFFFFFFF);
        this.blueprintId = blueprintId != null ? blueprintId : "default";
        this.blueprintName = blueprintName != null ? blueprintName : this.blueprintId;
        this.originPos = originPos != null ? originPos.immutable() : BlockPos.ZERO;
        this.dimension = dimension != null ? dimension : new ResourceLocation("minecraft", "overworld");
        this.buildingConfig = config != null ? config : new BuildingConfig(this.blueprintId);
        this.currentBlockIndex = 0;
        this.totalBlocks = totalBlocks;
        this.status = Status.IN_PROGRESS;
        this.createdTime = System.currentTimeMillis();
        this.lastActiveTime = this.createdTime;
    }

    public BuildProject() {
        this.status = Status.NOT_STARTED;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getBlueprintId() {
        return blueprintId;
    }

    public void setBlueprintId(String blueprintId) {
        this.blueprintId = blueprintId;
    }

    public String getBlueprintName() {
        return blueprintName != null ? blueprintName : blueprintId;
    }

    public void setBlueprintName(String blueprintName) {
        this.blueprintName = blueprintName;
    }

    public BlockPos getOriginPos() {
        return originPos;
    }

    public void setOriginPos(BlockPos originPos) {
        this.originPos = originPos != null ? originPos.immutable() : BlockPos.ZERO;
    }

    public ResourceLocation getDimension() {
        return dimension;
    }

    public void setDimension(ResourceLocation dimension) {
        this.dimension = dimension;
    }

    public BuildingConfig getBuildingConfig() {
        return buildingConfig;
    }

    public void setBuildingConfig(BuildingConfig buildingConfig) {
        this.buildingConfig = buildingConfig;
    }

    public int getCurrentBlockIndex() {
        return currentBlockIndex;
    }

    public void setCurrentBlockIndex(int currentBlockIndex) {
        this.currentBlockIndex = currentBlockIndex;
        this.lastActiveTime = System.currentTimeMillis();
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public void setTotalBlocks(int totalBlocks) {
        this.totalBlocks = totalBlocks;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        this.lastActiveTime = System.currentTimeMillis();
    }

    public boolean isActive() {
        return status == Status.IN_PROGRESS || status == Status.PAUSED;
    }

    public boolean isSameProject(String bpId, ResourceLocation dim) {
        if (blueprintId == null || bpId == null) return false;
        if (!blueprintId.equalsIgnoreCase(bpId)) return false;
        if (dimension != null && dim != null && !dimension.equals(dim)) return false;
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ProjectId", projectId != null ? projectId : "");
        tag.putString("BlueprintId", blueprintId != null ? blueprintId : "");
        tag.putString("BlueprintName", blueprintName != null ? blueprintName : "");
        if (originPos != null) {
            tag.put("OriginPos", NbtUtils.writeBlockPos(originPos));
        }
        if (dimension != null) {
            tag.putString("Dimension", dimension.toString());
        }
        if (buildingConfig != null) {
            tag.putString("BuildingConfig", buildingConfig.serialize());
        }
        tag.putInt("CurrentBlockIndex", currentBlockIndex);
        tag.putInt("TotalBlocks", totalBlocks);
        tag.putString("Status", status != null ? status.name() : Status.IN_PROGRESS.name());
        tag.putLong("CreatedTime", createdTime);
        tag.putLong("LastActiveTime", lastActiveTime);
        return tag;
    }

    public static BuildProject load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return null;
        BuildProject project = new BuildProject();
        project.projectId = tag.getString("ProjectId");
        project.blueprintId = tag.getString("BlueprintId");
        project.blueprintName = tag.getString("BlueprintName");
        if (tag.contains("OriginPos")) {
            project.originPos = NbtUtils.readBlockPos(tag.getCompound("OriginPos"));
        }
        if (tag.contains("Dimension")) {
            project.dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        }
        if (tag.contains("BuildingConfig")) {
            project.buildingConfig = BuildingConfig.deserialize(tag.getString("BuildingConfig"));
        }
        project.currentBlockIndex = tag.getInt("CurrentBlockIndex");
        project.totalBlocks = tag.getInt("TotalBlocks");
        if (tag.contains("Status")) {
            try {
                project.status = Status.valueOf(tag.getString("Status"));
            } catch (Exception e) {
                project.status = Status.IN_PROGRESS;
            }
        }
        project.createdTime = tag.getLong("CreatedTime");
        project.lastActiveTime = tag.getLong("LastActiveTime");
        return project;
    }

    public CompoundTag serialize() {
        return save();
    }

    public static BuildProject deserialize(CompoundTag tag) {
        return load(tag);
    }
}
