package com.aicompanion.network.packets;

import com.aicompanion.client.gui.CompanionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class S2C_SyncCompanionDataPacket {
    public UUID companionUUID;
    public int entityId;
    public String customName;
    public String currentState;
    public String preferredBehavior;
    public float health;
    public float maxHealth;
    public int companionLevel;
    public int miningXP;
    public int combatXP;
    public int gatheringXP;
    public int buildingXP;
    public int explorationXP;
    public int blocksMined;
    public int mobsKilled;
    public int tasksCompleted;
    public String currentTaskDesc;
    public String currentTaskProgress;
    public String currentTaskType;
    public String currentTaskStage;
    public String currentTaskSubstate;
    public String currentTaskFailureReason;
    public long lastProgressTick;
    public List<String> queueDescriptions;
    public String homePos;
    public String dimension;
    public int posX;
    public int posY;
    public int posZ;

    // Settings
    public boolean autoFollow;
    public boolean autoEquip;
    public boolean autoEat;
    public boolean autoDeliver;
    public boolean autoReturn;
    public boolean allowBlockBreaking;
    public boolean allowBlockPlacement;
    public boolean allowCombat;
    public boolean enableChunkLoading;
    public int respawnSecondsRemaining = 0;

    // 1.13.66: Authorized Chests, Live Logistics, Food Policies & Memory Telemetry
    public static class AuthorizedChestDTO {
        public int chestId;
        public String name;
        public String category;
        public String categoryDisplay;
        public int x;
        public int y;
        public int z;
        public String dimension;
        public int occupiedSlots;
        public int totalSlots;
        public boolean isPrimary;
        public String topItemsSummary;

        public AuthorizedChestDTO() {}

        public AuthorizedChestDTO(int chestId, String name, String category, String categoryDisplay,
                                  int x, int y, int z, String dimension,
                                  int occupiedSlots, int totalSlots, boolean isPrimary, String topItemsSummary) {
            this.chestId = chestId;
            this.name = name != null ? name : ("Baúl #" + chestId);
            this.category = category != null ? category : "ALL";
            this.categoryDisplay = categoryDisplay != null ? categoryDisplay : "General";
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension != null ? dimension : "minecraft:overworld";
            this.occupiedSlots = occupiedSlots;
            this.totalSlots = totalSlots;
            this.isPrimary = isPrimary;
            this.topItemsSummary = topItemsSummary != null ? topItemsSummary : "Vacío";
        }
    }

    public List<AuthorizedChestDTO> authorizedChests = new ArrayList<>();
    public String foodPolicyName = "RESERVE_16_STORE_REST";
    public String foodPolicyDisplay = "Reservar 16 y guardar resto en Baúl";
    public String autoDepositPolicyName = "ON_FULL_OR_IDLE";
    public String autoDepositPolicyDisplay = "Al llenarse (≤3) o volver a base";
    public String logisticsStatusBanner = "Sin operación de baúl activa";
    public String logisticsItemsBanner = "Sin excedentes pendientes";
    public String activeBuildSummary = "Ningún proyecto de construcción activo";
    public int rememberedDepositsCount = 0;
    public int dangerZonesCount = 0;
    public String aiAutonomyMode = "PROACTIVE";
    public String aiPersonalityMode = "LOYAL";

    public S2C_SyncCompanionDataPacket withRespawnCountdown(int seconds) {
        this.respawnSecondsRemaining = seconds;
        return this;
    }

    public S2C_SyncCompanionDataPacket() {}

    public S2C_SyncCompanionDataPacket(UUID companionUUID, int entityId, String customName, String currentState, String preferredBehavior,
                                      float health, float maxHealth, int companionLevel,
                                      int miningXP, int combatXP, int gatheringXP, int buildingXP, int explorationXP,
                                      int blocksMined, int mobsKilled, int tasksCompleted,
                                      String currentTaskDesc, String currentTaskProgress, String currentTaskType,
                                      String currentTaskStage, String currentTaskSubstate, String currentTaskFailureReason,
                                      long lastProgressTick,
                                      List<String> queueDescriptions, String homePos,
                                      String dimension, int posX, int posY, int posZ,
                                      boolean autoFollow, boolean autoEquip, boolean autoEat,
                                      boolean autoDeliver, boolean autoReturn,
                                      boolean allowBlockBreaking, boolean allowBlockPlacement,
                                      boolean allowCombat, boolean enableChunkLoading) {
        this.companionUUID = companionUUID;
        this.entityId = entityId;
        this.customName = customName != null ? customName : "Esclavo";
        this.currentState = currentState != null ? currentState : "IDLE";
        this.preferredBehavior = preferredBehavior != null ? preferredBehavior : "FOLLOW";
        this.health = health;
        this.maxHealth = maxHealth;
        this.companionLevel = companionLevel;
        this.miningXP = miningXP;
        this.combatXP = combatXP;
        this.gatheringXP = gatheringXP;
        this.buildingXP = buildingXP;
        this.explorationXP = explorationXP;
        this.blocksMined = blocksMined;
        this.mobsKilled = mobsKilled;
        this.tasksCompleted = tasksCompleted;
        this.currentTaskDesc = currentTaskDesc != null ? currentTaskDesc : "Ninguna";
        this.currentTaskProgress = currentTaskProgress != null ? currentTaskProgress : "";
        this.currentTaskType = currentTaskType != null ? currentTaskType : "";
        this.currentTaskStage = currentTaskStage != null ? currentTaskStage : "";
        this.currentTaskSubstate = currentTaskSubstate != null ? currentTaskSubstate : "";
        this.currentTaskFailureReason = currentTaskFailureReason != null ? currentTaskFailureReason : "";
        this.lastProgressTick = lastProgressTick;
        this.queueDescriptions = queueDescriptions != null ? queueDescriptions : new ArrayList<>();
        this.homePos = homePos != null ? homePos : "No establecida";
        this.dimension = dimension != null ? dimension : "minecraft:overworld";
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.autoFollow = autoFollow;
        this.autoEquip = autoEquip;
        this.autoEat = autoEat;
        this.autoDeliver = autoDeliver;
        this.autoReturn = autoReturn;
        this.allowBlockBreaking = allowBlockBreaking;
        this.allowBlockPlacement = allowBlockPlacement;
        this.allowCombat = allowCombat;
        this.enableChunkLoading = enableChunkLoading;
    }

    public static void encode(S2C_SyncCompanionDataPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.companionUUID != null);
        if (msg.companionUUID != null) {
            buf.writeUUID(msg.companionUUID);
        }
        buf.writeInt(msg.entityId);
        buf.writeUtf(msg.customName != null ? msg.customName : "Esclavo", 64);
        buf.writeUtf(msg.currentState != null ? msg.currentState : "IDLE", 32);
        buf.writeUtf(msg.preferredBehavior != null ? msg.preferredBehavior : "FOLLOW", 32);
        buf.writeFloat(msg.health);
        buf.writeFloat(msg.maxHealth);
        buf.writeInt(msg.companionLevel);
        buf.writeInt(msg.miningXP);
        buf.writeInt(msg.combatXP);
        buf.writeInt(msg.gatheringXP);
        buf.writeInt(msg.buildingXP);
        buf.writeInt(msg.explorationXP);
        buf.writeInt(msg.blocksMined);
        buf.writeInt(msg.mobsKilled);
        buf.writeInt(msg.tasksCompleted);
        buf.writeUtf(msg.currentTaskDesc != null ? msg.currentTaskDesc : "Ninguna", 256);
        buf.writeUtf(msg.currentTaskProgress != null ? msg.currentTaskProgress : "", 64);
        buf.writeUtf(msg.currentTaskType != null ? msg.currentTaskType : "", 64);
        buf.writeUtf(msg.currentTaskStage != null ? msg.currentTaskStage : "", 64);
        buf.writeUtf(msg.currentTaskSubstate != null ? msg.currentTaskSubstate : "", 128);
        buf.writeUtf(msg.currentTaskFailureReason != null ? msg.currentTaskFailureReason : "", 256);
        buf.writeLong(msg.lastProgressTick);

        List<String> qList = msg.queueDescriptions != null ? msg.queueDescriptions : List.of();
        int qSize = Math.min(qList.size(), 20);
        buf.writeInt(qSize);
        for (int i = 0; i < qSize; i++) {
            buf.writeUtf(qList.get(i), 256);
        }

        buf.writeUtf(msg.homePos != null ? msg.homePos : "No establecida", 64);
        buf.writeUtf(msg.dimension != null ? msg.dimension : "minecraft:overworld", 64);
        buf.writeInt(msg.posX);
        buf.writeInt(msg.posY);
        buf.writeInt(msg.posZ);

        buf.writeBoolean(msg.autoFollow);
        buf.writeBoolean(msg.autoEquip);
        buf.writeBoolean(msg.autoEat);
        buf.writeBoolean(msg.autoDeliver);
        buf.writeBoolean(msg.autoReturn);
        buf.writeBoolean(msg.allowBlockBreaking);
        buf.writeBoolean(msg.allowBlockPlacement);
        buf.writeBoolean(msg.allowCombat);
        buf.writeBoolean(msg.enableChunkLoading);
        buf.writeInt(msg.respawnSecondsRemaining);

        // Extended 1.13.66 data
        List<AuthorizedChestDTO> chests = msg.authorizedChests != null ? msg.authorizedChests : List.of();
        int cSize = Math.min(chests.size(), 32);
        buf.writeInt(cSize);
        for (int i = 0; i < cSize; i++) {
            AuthorizedChestDTO c = chests.get(i);
            buf.writeInt(c.chestId);
            buf.writeUtf(c.name != null ? c.name : ("Baúl #" + c.chestId), 64);
            buf.writeUtf(c.category != null ? c.category : "ALL", 32);
            buf.writeUtf(c.categoryDisplay != null ? c.categoryDisplay : "General", 48);
            buf.writeInt(c.x);
            buf.writeInt(c.y);
            buf.writeInt(c.z);
            buf.writeUtf(c.dimension != null ? c.dimension : "minecraft:overworld", 64);
            buf.writeInt(c.occupiedSlots);
            buf.writeInt(c.totalSlots);
            buf.writeBoolean(c.isPrimary);
            buf.writeUtf(c.topItemsSummary != null ? c.topItemsSummary : "Vacío", 160);
        }

        buf.writeUtf(msg.foodPolicyName != null ? msg.foodPolicyName : "RESERVE_16_STORE_REST", 48);
        buf.writeUtf(msg.foodPolicyDisplay != null ? msg.foodPolicyDisplay : "Reservar 16 y guardar resto en Baúl", 96);
        buf.writeUtf(msg.autoDepositPolicyName != null ? msg.autoDepositPolicyName : "ON_FULL_OR_IDLE", 48);
        buf.writeUtf(msg.autoDepositPolicyDisplay != null ? msg.autoDepositPolicyDisplay : "Al llenarse (≤3) o volver a base", 96);
        buf.writeUtf(msg.logisticsStatusBanner != null ? msg.logisticsStatusBanner : "", 192);
        buf.writeUtf(msg.logisticsItemsBanner != null ? msg.logisticsItemsBanner : "", 192);
        buf.writeUtf(msg.activeBuildSummary != null ? msg.activeBuildSummary : "Ningún proyecto de construcción activo", 192);
        buf.writeInt(msg.rememberedDepositsCount);
        buf.writeInt(msg.dangerZonesCount);
        buf.writeUtf(msg.aiAutonomyMode != null ? msg.aiAutonomyMode : "PROACTIVE", 32);
        buf.writeUtf(msg.aiPersonalityMode != null ? msg.aiPersonalityMode : "LOYAL", 32);
    }

    public static S2C_SyncCompanionDataPacket decode(FriendlyByteBuf buf) {
        S2C_SyncCompanionDataPacket msg = new S2C_SyncCompanionDataPacket();
        if (buf.readBoolean()) {
            msg.companionUUID = buf.readUUID();
        }
        msg.entityId = buf.readInt();
        msg.customName = buf.readUtf(64);
        msg.currentState = buf.readUtf(32);
        msg.preferredBehavior = buf.readUtf(32);
        msg.health = buf.readFloat();
        msg.maxHealth = buf.readFloat();
        msg.companionLevel = buf.readInt();
        msg.miningXP = buf.readInt();
        msg.combatXP = buf.readInt();
        msg.gatheringXP = buf.readInt();
        msg.buildingXP = buf.readInt();
        msg.explorationXP = buf.readInt();
        msg.blocksMined = buf.readInt();
        msg.mobsKilled = buf.readInt();
        msg.tasksCompleted = buf.readInt();
        msg.currentTaskDesc = buf.readUtf(256);
        msg.currentTaskProgress = buf.readUtf(64);
        msg.currentTaskType = buf.readUtf(64);
        msg.currentTaskStage = buf.readUtf(64);
        msg.currentTaskSubstate = buf.readUtf(128);
        msg.currentTaskFailureReason = buf.readUtf(256);
        msg.lastProgressTick = buf.readLong();

        int queueSize = buf.readInt();
        msg.queueDescriptions = new ArrayList<>(queueSize);
        for (int i = 0; i < queueSize; i++) {
            msg.queueDescriptions.add(buf.readUtf(256));
        }

        msg.homePos = buf.readUtf(64);
        msg.dimension = buf.readUtf(64);
        msg.posX = buf.readInt();
        msg.posY = buf.readInt();
        msg.posZ = buf.readInt();

        msg.autoFollow = buf.readBoolean();
        msg.autoEquip = buf.readBoolean();
        msg.autoEat = buf.readBoolean();
        msg.autoDeliver = buf.readBoolean();
        msg.autoReturn = buf.readBoolean();
        msg.allowBlockBreaking = buf.readBoolean();
        msg.allowBlockPlacement = buf.readBoolean();
        msg.allowCombat = buf.readBoolean();
        msg.enableChunkLoading = buf.readBoolean();
        msg.respawnSecondsRemaining = buf.readInt();

        if (buf.readableBytes() > 0) {
            int cSize = buf.readInt();
            msg.authorizedChests = new ArrayList<>(cSize);
            for (int i = 0; i < cSize; i++) {
                int id = buf.readInt();
                String name = buf.readUtf(64);
                String cat = buf.readUtf(32);
                String catDisp = buf.readUtf(48);
                int cx = buf.readInt();
                int cy = buf.readInt();
                int cz = buf.readInt();
                String cdim = buf.readUtf(64);
                int occ = buf.readInt();
                int tot = buf.readInt();
                boolean prim = buf.readBoolean();
                String topItems = buf.readUtf(160);
                msg.authorizedChests.add(new AuthorizedChestDTO(id, name, cat, catDisp, cx, cy, cz, cdim, occ, tot, prim, topItems));
            }
            msg.foodPolicyName = buf.readUtf(48);
            msg.foodPolicyDisplay = buf.readUtf(96);
            msg.autoDepositPolicyName = buf.readUtf(48);
            msg.autoDepositPolicyDisplay = buf.readUtf(96);
            msg.logisticsStatusBanner = buf.readUtf(192);
            msg.logisticsItemsBanner = buf.readUtf(192);
            msg.activeBuildSummary = buf.readUtf(192);
            msg.rememberedDepositsCount = buf.readInt();
            msg.dangerZonesCount = buf.readInt();
            msg.aiAutonomyMode = buf.readUtf(32);
            msg.aiPersonalityMode = buf.readUtf(32);
        }
        return msg;
    }

    public static void handle(S2C_SyncCompanionDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (Minecraft.getInstance().screen instanceof CompanionScreen screen) {
                    screen.updateSyncedData(msg);
                }
                com.aicompanion.client.gui.CompanionHudOverlay.updateCompanionData(msg);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
