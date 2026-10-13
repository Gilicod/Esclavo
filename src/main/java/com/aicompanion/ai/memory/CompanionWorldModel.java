package com.aicompanion.ai.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/**
 * Enterprise World Model & Spatial POI Episodic Memory for Esclavo (Pilar 5).
 * Caches environmental Points of Interest (Crafting Tables, Furnaces, Chests, Barrels)
 * with strict TTL (Time-To-Live) eviction to prevent memory leaks, enabling Esclavo
 * to reuse nearby village workstations rather than redundantly crafting new ones.
 */
public class CompanionWorldModel {

    public record POIRecord(BlockPos pos, ResourceLocation blockId, long discoveredTick, long ttlTicks) {
        public boolean isExpired(long currentTick) {
            return ttlTicks > 0 && (currentTick - discoveredTick > ttlTicks);
        }
    }

    public record ResourceDepositRecord(BlockPos pos, ResourceLocation blockId, int count, long discoveredTick, long ttlTicks) {
        public boolean isExpired(long currentTick) {
            return ttlTicks > 0 && (currentTick - discoveredTick > ttlTicks);
        }
    }

    public static final long DEFAULT_POI_TTL = 24000L; // 20 minutes (1 full Minecraft day)
    private static final int MAX_DEPOSITS_PER_RESOURCE = 64;

    private final Map<String, BlockPos> knownLocations = new HashMap<>();
    private final Map<String, BlockPos> dimensionalPortals = new HashMap<>();
    private final Map<ResourceLocation, Map<BlockPos, POIRecord>> poisByType = new HashMap<>();
    private final Map<ResourceLocation, Map<BlockPos, ResourceDepositRecord>> resourceDeposits = new HashMap<>();

    // ==========================================
    // DIMENSIONAL PORTAL MEMORY
    // ==========================================

    public void rememberDimensionalPortal(String dimension, BlockPos pos) {
        if (dimension != null && pos != null) {
            dimensionalPortals.put(dimension.toLowerCase(), pos.immutable());
        }
    }

    public BlockPos getDimensionalPortal(String dimension) {
        if (dimension == null) return null;
        return dimensionalPortals.get(dimension.toLowerCase());
    }

    public Map<String, BlockPos> getAllDimensionalPortals() {
        return Collections.unmodifiableMap(dimensionalPortals);
    }

    // ==========================================
    // CUSTOM NAMED LOCATIONS (Home, Base, Mine)
    // ==========================================

    public void rememberLocation(String label, BlockPos pos) {
        knownLocations.put(label.toLowerCase(), pos);
    }

    public BlockPos getLocation(String label) {
        return knownLocations.get(label.toLowerCase());
    }

    public Map<String, BlockPos> getAllLocations() {
        return Collections.unmodifiableMap(knownLocations);
    }

    // ==========================================
    // EPISODIC POI SPATIAL CACHING (Pilar 5)
    // ==========================================

    public void rememberPOI(BlockPos pos, Block block, long currentTick) {
        rememberPOI(pos, block, currentTick, DEFAULT_POI_TTL);
    }

    public void rememberPOI(BlockPos pos, Block block, long currentTick, long ttlTicks) {
        if (pos == null || block == null) return;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        poisByType.computeIfAbsent(id, k -> new HashMap<>())
                .put(pos.immutable(), new POIRecord(pos.immutable(), id, currentTick, ttlTicks));
    }

    public void removePOI(BlockPos pos) {
        if (pos == null) return;
        for (Map<BlockPos, POIRecord> map : poisByType.values()) {
            map.remove(pos);
        }
    }

    public void cleanExpiredPOIs(long currentTick) {
        for (Map<BlockPos, POIRecord> map : poisByType.values()) {
            map.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTick));
        }
    }

    public void rememberResourceDeposit(BlockPos pos, Block block, int count, long currentTick) {
        if (pos == null || block == null) return;
        rememberResourceDeposit(pos, BuiltInRegistries.BLOCK.getKey(block), count, currentTick);
    }

    public void rememberResourceDeposit(BlockPos pos, ResourceLocation id, int count, long currentTick) {
        if (pos == null || id == null) return;
        Map<BlockPos, ResourceDepositRecord> map = resourceDeposits.computeIfAbsent(id, k -> new HashMap<>());
        if (map.size() >= MAX_DEPOSITS_PER_RESOURCE) {
            cleanExpiredDeposits(currentTick);
            if (map.size() >= MAX_DEPOSITS_PER_RESOURCE) {
                BlockPos oldest = map.keySet().iterator().next();
                map.remove(oldest);
            }
        }
        map.put(pos.immutable(), new ResourceDepositRecord(pos.immutable(), id, count, currentTick, DEFAULT_POI_TTL));
    }

    public void cleanExpiredDeposits(long currentTick) {
        for (Map<BlockPos, ResourceDepositRecord> map : resourceDeposits.values()) {
            map.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTick));
        }
    }

    public void removeResourceDeposit(BlockPos pos) {
        if (pos == null) return;
        for (Map<BlockPos, ResourceDepositRecord> map : resourceDeposits.values()) {
            map.remove(pos);
        }
    }

    public void clearResourceDeposits() {
        resourceDeposits.clear();
    }

    public int getTotalRememberedCount() {
        int count = knownLocations.size() + dimensionalPortals.size();
        for (Map<BlockPos, POIRecord> map : poisByType.values()) {
            count += map.size();
        }
        for (Map<BlockPos, ResourceDepositRecord> map : resourceDeposits.values()) {
            count += map.size();
        }
        return count;
    }

    public Optional<BlockPos> findClosestResourceDeposit(Block targetBlock, BlockPos origin, double maxRadius) {
        if (origin == null || targetBlock == null) return Optional.empty();
        return findClosestResourceDeposit(BuiltInRegistries.BLOCK.getKey(targetBlock), origin, maxRadius);
    }

    public Optional<BlockPos> findClosestResourceDeposit(ResourceLocation id, BlockPos origin, double maxRadius) {
        if (origin == null || id == null) return Optional.empty();
        Map<BlockPos, ResourceDepositRecord> map = resourceDeposits.get(id);
        if (map == null || map.isEmpty()) return Optional.empty();

        double maxDistSq = maxRadius * maxRadius;
        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        for (BlockPos p : map.keySet()) {
            double distSq = origin.distSqr(p);
            if (distSq <= maxDistSq && distSq < bestDistSq) {
                bestDistSq = distSq;
                bestPos = p;
            }
        }
        return Optional.ofNullable(bestPos);
    }

    /**
     * Finds the closest verified POI of the requested block type within maxRadius.
     * Automatically invalidates stale POIs if the block was removed in the world.
     */
    public Optional<BlockPos> findClosestPOI(ServerLevel level, BlockPos origin, Block targetBlock, double maxRadius) {
        if (origin == null || targetBlock == null) return Optional.empty();

        List<ResourceLocation> compatibleIds = getCompatibleBlockIds(targetBlock);
        double maxDistSq = maxRadius * maxRadius;
        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        List<BlockPos> stalePoses = new ArrayList<>();

        for (ResourceLocation id : compatibleIds) {
            Map<BlockPos, POIRecord> map = poisByType.get(id);
            if (map == null || map.isEmpty()) continue;

            for (Map.Entry<BlockPos, POIRecord> entry : map.entrySet()) {
                BlockPos p = entry.getKey();
                POIRecord record = entry.getValue();

                if (level != null && record.isExpired(level.getGameTime())) {
                    stalePoses.add(p);
                    continue;
                }

                // If chunk is loaded, verify the block is actually still there
                if (level != null && level.isLoaded(p)) {
                    if (!level.getBlockState(p).is(targetBlock)) {
                        stalePoses.add(p);
                        continue;
                    }
                }

                double dSq = origin.distSqr(p);
                if (dSq <= maxDistSq && dSq < bestDistSq) {
                    bestDistSq = dSq;
                    bestPos = p;
                }
            }
        }

        // Clean stale POIs
        for (BlockPos stale : stalePoses) {
            removePOI(stale);
        }

        return Optional.ofNullable(bestPos);
    }

    private List<ResourceLocation> getCompatibleBlockIds(Block targetBlock) {
        List<ResourceLocation> ids = new ArrayList<>();
        ids.add(BuiltInRegistries.BLOCK.getKey(targetBlock));

        if (targetBlock == Blocks.FURNACE) {
            ids.add(BuiltInRegistries.BLOCK.getKey(Blocks.BLAST_FURNACE));
            ids.add(BuiltInRegistries.BLOCK.getKey(Blocks.SMOKER));
        } else if (targetBlock == Blocks.CHEST) {
            ids.add(BuiltInRegistries.BLOCK.getKey(Blocks.BARREL));
            ids.add(BuiltInRegistries.BLOCK.getKey(Blocks.TRAPPED_CHEST));
        }

        return ids;
    }

    public List<BlockPos> getAllPOIs(Block targetBlock) {
        List<BlockPos> result = new ArrayList<>();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(targetBlock);
        Map<BlockPos, POIRecord> map = poisByType.get(id);
        if (map != null) {
            result.addAll(map.keySet());
        }
        return result;
    }

    // ==========================================
    // PERSISTENCE (NBT)
    // ==========================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        // 1. Save named locations
        ListTag locList = new ListTag();
        for (Map.Entry<String, BlockPos> entry : knownLocations.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("Label", entry.getKey());
            item.put("Pos", NbtUtils.writeBlockPos(entry.getValue()));
            locList.add(item);
        }
        tag.put("Locations", locList);

        // 2. Save cached POIs with TTL
        ListTag poiList = new ListTag();
        for (Map<BlockPos, POIRecord> map : poisByType.values()) {
            for (POIRecord record : map.values()) {
                CompoundTag pTag = new CompoundTag();
                pTag.put("Pos", NbtUtils.writeBlockPos(record.pos()));
                pTag.putString("BlockId", record.blockId().toString());
                pTag.putLong("Discovered", record.discoveredTick());
                pTag.putLong("TTL", record.ttlTicks());
                poiList.add(pTag);
            }
        }
        tag.put("POIs", poiList);

        // 3. Save cached Resource Deposits
        ListTag depList = new ListTag();
        for (Map<BlockPos, ResourceDepositRecord> map : resourceDeposits.values()) {
            for (ResourceDepositRecord record : map.values()) {
                CompoundTag dTag = new CompoundTag();
                dTag.put("Pos", NbtUtils.writeBlockPos(record.pos()));
                dTag.putString("BlockId", record.blockId().toString());
                dTag.putInt("Count", record.count());
                dTag.putLong("Discovered", record.discoveredTick());
                dTag.putLong("TTL", record.ttlTicks());
                depList.add(dTag);
            }
        }
        tag.put("ResourceDeposits", depList);

        // 4. Save dimensional portals
        ListTag portalList = new ListTag();
        for (Map.Entry<String, BlockPos> entry : dimensionalPortals.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("Dimension", entry.getKey());
            item.put("Pos", NbtUtils.writeBlockPos(entry.getValue()));
            portalList.add(item);
        }
        tag.put("DimensionalPortals", portalList);

        return tag;
    }

    public void load(CompoundTag tag) {
        knownLocations.clear();
        dimensionalPortals.clear();
        poisByType.clear();
        resourceDeposits.clear();

        // 1. Load named locations
        if (tag.contains("Locations")) {
            ListTag locList = tag.getList("Locations", Tag.TAG_COMPOUND);
            for (int i = 0; i < locList.size(); i++) {
                CompoundTag item = locList.getCompound(i);
                knownLocations.put(item.getString("Label"), NbtUtils.readBlockPos(item.getCompound("Pos")));
            }
        }

        // 2. Load cached POIs
        if (tag.contains("POIs")) {
            ListTag poiList = tag.getList("POIs", Tag.TAG_COMPOUND);
            for (int i = 0; i < poiList.size(); i++) {
                CompoundTag pTag = poiList.getCompound(i);
                BlockPos pos = NbtUtils.readBlockPos(pTag.getCompound("Pos"));
                ResourceLocation blockId = new ResourceLocation(pTag.getString("BlockId"));
                long discovered = pTag.getLong("Discovered");
                long ttl = pTag.getLong("TTL");
                poisByType.computeIfAbsent(blockId, k -> new HashMap<>())
                        .put(pos, new POIRecord(pos, blockId, discovered, ttl));
            }
        }

        // 3. Load cached Resource Deposits
        if (tag.contains("ResourceDeposits")) {
            ListTag depList = tag.getList("ResourceDeposits", Tag.TAG_COMPOUND);
            for (int i = 0; i < depList.size(); i++) {
                CompoundTag dTag = depList.getCompound(i);
                BlockPos pos = NbtUtils.readBlockPos(dTag.getCompound("Pos"));
                ResourceLocation blockId = new ResourceLocation(dTag.getString("BlockId"));
                int count = dTag.getInt("Count");
                long discovered = dTag.getLong("Discovered");
                long ttl = dTag.getLong("TTL");
                resourceDeposits.computeIfAbsent(blockId, k -> new HashMap<>())
                        .put(pos, new ResourceDepositRecord(pos, blockId, count, discovered, ttl));
            }
        }

        // 4. Load dimensional portals
        if (tag.contains("DimensionalPortals")) {
            ListTag portalList = tag.getList("DimensionalPortals", Tag.TAG_COMPOUND);
            for (int i = 0; i < portalList.size(); i++) {
                CompoundTag item = portalList.getCompound(i);
                dimensionalPortals.put(item.getString("Dimension"), NbtUtils.readBlockPos(item.getCompound("Pos")));
            }
        }
    }
}
