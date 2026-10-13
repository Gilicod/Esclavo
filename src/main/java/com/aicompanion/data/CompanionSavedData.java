package com.aicompanion.data;

import com.aicompanion.entity.CompanionEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CompanionSavedData extends SavedData {
    private static final String FILE_NAME = "aicompanion_data";
    private final Map<UUID, CompoundTag> savedCompanions = new HashMap<>();

    public static CompanionSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                CompanionSavedData::load,
                CompanionSavedData::new,
                FILE_NAME
        );
    }

    public CompanionSavedData() {}

    public static CompanionSavedData load(CompoundTag tag) {
        CompanionSavedData data = new CompanionSavedData();
        ListTag list = tag.getList("Companions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag compTag = list.getCompound(i);
            if (compTag.hasUUID("OwnerUUID")) {
                data.savedCompanions.put(compTag.getUUID("OwnerUUID"), compTag);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (CompoundTag compTag : savedCompanions.values()) {
            list.add(compTag);
        }
        tag.put("Companions", list);
        return tag;
    }

    public boolean hasSavedCompanion(UUID ownerUUID) {
        return savedCompanions.containsKey(ownerUUID);
    }

    public CompoundTag getSavedCompanion(UUID ownerUUID) {
        return savedCompanions.get(ownerUUID);
    }

    public void saveCompanion(CompanionEntity companion) {
        if (companion == null || companion.getOwnerUUID() == null) return;
        CompoundTag tag = new CompoundTag();
        companion.saveWithoutId(tag);
        tag.putString("Dimension", companion.level().dimension().location().toString());
        tag.putUUID("CompanionUUID", companion.getUUID());
        tag.putDouble("SavedPosX", companion.getX());
        tag.putDouble("SavedPosY", companion.getY());
        tag.putDouble("SavedPosZ", companion.getZ());
        tag.putFloat("Health", companion.getHealth());
        tag.putBoolean("IsAlive", companion.isAlive() && companion.getHealth() > 0.0F);
        tag.putBoolean("IsDismissed", false);
        savedCompanions.put(companion.getOwnerUUID(), tag);
        setDirty();
    }

    public void markDismissed(UUID ownerUUID) {
        CompoundTag tag = savedCompanions.get(ownerUUID);
        if (tag != null) {
            tag.putBoolean("IsDismissed", true);
            setDirty();
        }
    }

    public Map<UUID, CompoundTag> getAllSavedCompanions() {
        return savedCompanions;
    }

    public void saveDeadWaitingRespawn(CompanionEntity companion, long respawnGameTime, int totalRespawnSeconds, String respawnLocationMode) {
        if (companion == null || companion.getOwnerUUID() == null) return;
        CompoundTag tag = new CompoundTag();
        companion.saveWithoutId(tag);
        tag.putString("Dimension", companion.level().dimension().location().toString());
        tag.putUUID("CompanionUUID", companion.getUUID());
        tag.putDouble("SavedPosX", companion.getX());
        tag.putDouble("SavedPosY", companion.getY());
        tag.putDouble("SavedPosZ", companion.getZ());
        tag.putFloat("Health", 0.0F);
        tag.putBoolean("IsAlive", false);
        tag.putBoolean("IsDismissed", false);
        tag.putString("State", com.aicompanion.entity.CompanionState.DEAD_WAITING_RESPAWN.name());
        tag.putLong("RespawnGameTime", respawnGameTime);
        tag.putInt("TotalRespawnSeconds", totalRespawnSeconds);
        tag.putString("RespawnLocationMode", respawnLocationMode);
        tag.putDouble("DeathPosX", companion.getX());
        tag.putDouble("DeathPosY", companion.getY());
        tag.putDouble("DeathPosZ", companion.getZ());
        tag.putString("DeathDim", companion.level().dimension().location().toString());
        tag.putString("CustomCompanionName", companion.getCustomCompanionName());
        savedCompanions.put(companion.getOwnerUUID(), tag);
        setDirty();
    }

    public void markDead(UUID ownerUUID) {
        CompoundTag tag = savedCompanions.get(ownerUUID);
        if (tag != null) {
            tag.putBoolean("IsAlive", false);
            tag.putFloat("Health", 0.0F);
            tag.putString("State", com.aicompanion.entity.CompanionState.DEAD_WAITING_RESPAWN.name());
            setDirty();
        }
    }

    public void markRespawning(UUID ownerUUID) {
        CompoundTag tag = savedCompanions.get(ownerUUID);
        if (tag != null) {
            tag.putString("State", com.aicompanion.entity.CompanionState.RESPAWNING.name());
            setDirty();
        }
    }

    public void markAlive(UUID ownerUUID) {
        CompoundTag tag = savedCompanions.get(ownerUUID);
        if (tag != null) {
            tag.putBoolean("IsAlive", true);
            tag.remove("RespawnGameTime");
            tag.remove("TotalRespawnSeconds");
            tag.remove("RespawnRemainingTicks");
            tag.putString("State", com.aicompanion.entity.CompanionState.FOLLOWING.name());
            setDirty();
        }
    }

    public boolean deleteCompanion(UUID ownerUUID) {
        boolean removed = savedCompanions.remove(ownerUUID) != null;
        setDirty();
        return removed;
    }
}
