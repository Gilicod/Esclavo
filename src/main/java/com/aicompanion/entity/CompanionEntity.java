package com.aicompanion.entity;

import com.aicompanion.AICompanionConfig;
import com.aicompanion.AICompanionMod;
import com.aicompanion.ai.CompanionBrain;
import com.aicompanion.ai.nav.NavigationManager;
import com.aicompanion.ai.tasks.FollowTask;
import com.aicompanion.ai.tasks.StayTask;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.inventory.CompanionMenu;
import com.aicompanion.util.SafeSpawnFinder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

public class CompanionEntity extends PathfinderMob implements MenuProvider {
    private UUID ownerUUID;
    private String customCompanionName = "Esclavo";
    private final CompanionInventoryHandler inventoryHandler = new CompanionInventoryHandler();
    private final CompanionSettings settings = new CompanionSettings();
    private CompanionState currentState = CompanionState.IDLE;
    private DefaultBehavior preferredIdleBehavior = DefaultBehavior.FOLLOW;
    private final CompanionBrain brain;
    private final NavigationManager navManager;

    // Home base
    private BlockPos homePos = null;
    private ResourceKey<Level> homeDim = Level.OVERWORLD;

    // Progression / Stats
    private int companionLevel = 1;
    private int miningXP = 0;
    private int combatXP = 0;
    private int gatheringXP = 0;
    private int buildingXP = 0;
    private int explorationXP = 0;
    private int blocksMined = 0;
    private int mobsKilled = 0;
    private int tasksCompleted = 0;

    // Downed timer
    private int downedTicks = 0;
    // 1.13.9: tick del último daño recibido (pausa la regeneración natural)
    private int lastDamageTick = -1000;
    // 1.13.11: ticks restantes de "buceo" (suprime FloatGoal para poder bajar por agua)
    private int diveTicks = 0;

    public void markDiving() {
        this.diveTicks = 5;
    }

    public boolean isDiving() {
        return this.diveTicks > 0;
    }

    public CompanionEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.brain = new CompanionBrain(this);
        this.navManager = new NavigationManager(this);
        this.setCustomName(Component.literal(customCompanionName));
        this.setCustomNameVisible(true);
        this.setCanPickUpLoot(true);

        if (this.getNavigation() instanceof GroundPathNavigation gNav) {
            gNav.setCanOpenDoors(true);
            gNav.setCanPassDoors(true);
        }

        // Strict pathfinding avoidance of fire, lava, and lethal hazards
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.LAVA, -1.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_OTHER, 16.0F);

        // Vanilla step height. 1.25 let the companion glide up full blocks without jumping, which
        // looked like micro-teleports; full blocks are now climbed with a real jump (executor/FloatGoal).
        this.setMaxUpStep(0.6F);
    }

    private final com.aicompanion.util.TemporaryBlockTracker temporaryBlocks = new com.aicompanion.util.TemporaryBlockTracker();

    public enum ChestCategory {
        ALL("General", 0xFF55FF55),
        FOOD("Comida", 0xFFFFAA00),
        ORES("Minerales", 0xFF55FFFF),
        WOOD_BUILD("Madera/Bloques", 0xFFAAAAFF),
        LOOT_EQUIP("Botín/Equipo", 0xFFFF55FF);

        private final String displayName;
        private final int color;

        ChestCategory(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public int getColor() { return color; }

        public ChestCategory next() {
            ChestCategory[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }

        public static ChestCategory fromName(String name) {
            if (name == null) return ALL;
            for (ChestCategory c : values()) {
                if (c.name().equalsIgnoreCase(name)) return c;
            }
            return ALL;
        }

        public static boolean isHealthyFood(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;
            var item = stack.getItem();
            if (item == net.minecraft.world.item.Items.ROTTEN_FLESH
                    || item == net.minecraft.world.item.Items.SPIDER_EYE
                    || item == net.minecraft.world.item.Items.POISONOUS_POTATO
                    || item == net.minecraft.world.item.Items.PUFFERFISH
                    || item == net.minecraft.world.item.Items.CHORUS_FRUIT) {
                return false;
            }
            return stack.isEdible();
        }

        public boolean matchesItem(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;
            if (this == ALL) return true;
            var item = stack.getItem();
            boolean isFood = isHealthyFood(stack)
                    || item == net.minecraft.world.item.Items.WHEAT
                    || item == net.minecraft.world.item.Items.HAY_BLOCK
                    || item == net.minecraft.world.item.Items.SUGAR_CANE
                    || item == net.minecraft.world.item.Items.BOWL;
            if (this == FOOD) return isFood;

            ResourceLocation rl = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            String path = rl != null ? rl.getPath().toLowerCase() : "";
            boolean isOreOrIngot = stack.is(net.minecraft.tags.ItemTags.COALS)
                    || path.contains("ingot") || path.contains("raw_") || path.contains("_ore")
                    || path.contains("diamond") || path.contains("emerald") || path.contains("redstone")
                    || path.contains("lapis") || path.contains("quartz") || path.contains("amethyst")
                    || path.contains("nugget") || path.contains("netherite") || path.contains("coal");
            if (this == ORES) return isOreOrIngot;

            boolean isWoodOrBuild = stack.is(net.minecraft.tags.ItemTags.LOGS)
                    || stack.is(net.minecraft.tags.ItemTags.PLANKS)
                    || (item instanceof net.minecraft.world.item.BlockItem && !isFood && !isOreOrIngot);
            if (this == WOOD_BUILD) return isWoodOrBuild;

            if (this == LOOT_EQUIP) {
                return !isFood && !isOreOrIngot && !isWoodOrBuild;
            }
            return true;
        }
    }

    public static class AuthorizedChestEntry {
        private final int chestId;
        private final BlockPos pos;
        private final ResourceLocation dimension;
        private final UUID authorizedBy;
        private String customName;
        private ChestCategory category;

        public AuthorizedChestEntry(int chestId, BlockPos pos, ResourceLocation dimension, UUID authorizedBy, String customName, ChestCategory category) {
            this.chestId = Math.max(1, chestId);
            this.pos = pos != null ? pos.immutable() : BlockPos.ZERO;
            this.dimension = dimension != null ? dimension : Level.OVERWORLD.location();
            this.authorizedBy = authorizedBy;
            this.customName = (customName == null || customName.isBlank()) ? ("Baúl #" + this.chestId) : customName.trim();
            this.category = category != null ? category : ChestCategory.ALL;
        }

        public AuthorizedChestEntry(BlockPos pos, ResourceLocation dimension, UUID authorizedBy) {
            this(1, pos, dimension, authorizedBy, "Baúl #1", ChestCategory.ALL);
        }

        public int getChestId() { return chestId; }
        public BlockPos getPos() { return pos; }
        public ResourceLocation getDimension() { return dimension; }
        public UUID getAuthorizedBy() { return authorizedBy; }
        public String getCustomName() { return customName; }
        public void setCustomName(String customName) {
            if (customName != null && !customName.isBlank()) {
                this.customName = customName.trim();
            }
        }
        public ChestCategory getCategory() { return category; }
        public void setCategory(ChestCategory category) {
            this.category = category != null ? category : ChestCategory.ALL;
        }

        public String getFullLabel() {
            return "[#" + chestId + "] " + customName + " (" + category.getDisplayName() + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AuthorizedChestEntry that)) return false;
            return pos.equals(that.pos) && dimension.equals(that.dimension);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(pos, dimension);
        }
    }

    private final java.util.List<AuthorizedChestEntry> authorizedChests = new java.util.ArrayList<>();
    private int nextAuthorizedChestId = 1;

    private int allocateNextChestId() {
        int max = 0;
        for (AuthorizedChestEntry e : authorizedChests) {
            if (e.getChestId() > max) max = e.getChestId();
        }
        nextAuthorizedChestId = Math.max(nextAuthorizedChestId, max + 1);
        return nextAuthorizedChestId++;
    }

    private BlockPos getConnectedChestNeighbor(BlockPos pos, Level level) {
        if (pos == null || level == null) return null;
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
            try {
                net.minecraft.world.level.block.state.properties.ChestType type = state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE);
                if (type != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                    net.minecraft.core.Direction connectedDir = net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state);
                    return pos.relative(connectedDir);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public AuthorizedChestEntry getAuthorizedChestAt(BlockPos pos, Level level) {
        if (pos == null) return null;
        ResourceLocation currentDim = level != null ? level.dimension().location() : Level.OVERWORLD.location();
        BlockPos neighbor = getConnectedChestNeighbor(pos, level);
        for (AuthorizedChestEntry entry : authorizedChests) {
            if (entry.getDimension().equals(currentDim)) {
                if (entry.getPos().equals(pos) || (neighbor != null && entry.getPos().equals(neighbor))) {
                    return entry;
                }
            }
        }
        return null;
    }

    public AuthorizedChestEntry getAuthorizedChestById(int chestId) {
        for (AuthorizedChestEntry entry : authorizedChests) {
            if (entry.getChestId() == chestId) return entry;
        }
        return null;
    }

    public AuthorizedChestEntry authorizeChestWithEntry(BlockPos pos, ResourceLocation dimension, UUID playerUUID) {
        if (pos == null) return null;
        ResourceLocation dim = dimension != null ? dimension : (this.level() != null ? this.level().dimension().location() : Level.OVERWORLD.location());
        AuthorizedChestEntry existing = getAuthorizedChestAt(pos, this.level());
        if (existing != null) {
            applyWorldChestLabel(existing);
            return existing;
        }
        int id = allocateNextChestId();
        AuthorizedChestEntry created = new AuthorizedChestEntry(id, pos, dim, playerUUID, "Baúl #" + id, ChestCategory.ALL);
        authorizedChests.add(created);
        applyWorldChestLabel(created);
        return created;
    }

    public void authorizeChest(BlockPos pos, ResourceLocation dimension, UUID playerUUID) {
        authorizeChestWithEntry(pos, dimension, playerUUID);
    }

    public void authorizeChest(BlockPos pos) {
        authorizeChest(pos, this.level().dimension().location(), this.getOwnerUUID());
    }

    public void applyWorldChestLabel(AuthorizedChestEntry entry) {
        if (entry == null || this.level() == null || this.level().isClientSide) return;
        if (!this.level().dimension().location().equals(entry.getDimension())) return;
        try {
            net.minecraft.world.level.block.entity.BlockEntity be = this.level().getBlockEntity(entry.getPos());
            if (be instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity container) {
                container.setCustomName(Component.literal(entry.getFullLabel()));
                be.setChanged();
            }
        } catch (Exception ignored) {}
    }

    private void clearWorldChestLabel(AuthorizedChestEntry entry) {
        if (entry == null || this.level() == null || this.level().isClientSide) return;
        if (!this.level().dimension().location().equals(entry.getDimension())) return;
        try {
            net.minecraft.world.level.block.entity.BlockEntity be = this.level().getBlockEntity(entry.getPos());
            if (be instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity container) {
                container.setCustomName(null);
                be.setChanged();
            }
        } catch (Exception ignored) {}
    }

    public void unauthorizeChest(BlockPos pos, ResourceLocation dimension) {
        if (pos != null) {
            BlockPos neighbor = getConnectedChestNeighbor(pos, this.level());
            java.util.Iterator<AuthorizedChestEntry> it = authorizedChests.iterator();
            while (it.hasNext()) {
                AuthorizedChestEntry e = it.next();
                boolean sameDim = (dimension == null || e.getDimension().equals(dimension));
                if (sameDim && (e.getPos().equals(pos) || (neighbor != null && e.getPos().equals(neighbor)))) {
                    clearWorldChestLabel(e);
                    it.remove();
                }
            }
        }
    }

    public void unauthorizeChest(BlockPos pos) {
        unauthorizeChest(pos, this.level().dimension().location());
    }

    public boolean unauthorizeChestById(int chestId) {
        java.util.Iterator<AuthorizedChestEntry> it = authorizedChests.iterator();
        while (it.hasNext()) {
            AuthorizedChestEntry e = it.next();
            if (e.getChestId() == chestId) {
                clearWorldChestLabel(e);
                it.remove();
                return true;
            }
        }
        return false;
    }

    public boolean renameAuthorizedChest(int chestId, String newName) {
        AuthorizedChestEntry entry = getAuthorizedChestById(chestId);
        if (entry == null || newName == null || newName.isBlank()) return false;
        String clean = newName.trim();
        if (clean.length() > 24) clean = clean.substring(0, 24);
        entry.setCustomName(clean);
        applyWorldChestLabel(entry);
        return true;
    }

    public ChestCategory cycleAuthorizedChestCategory(int chestId) {
        AuthorizedChestEntry entry = getAuthorizedChestById(chestId);
        if (entry == null) return null;
        entry.setCategory(entry.getCategory().next());
        applyWorldChestLabel(entry);
        return entry.getCategory();
    }

    public boolean setPrimaryChestById(int chestId) {
        AuthorizedChestEntry entry = getAuthorizedChestById(chestId);
        if (entry == null) return false;
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, entry.getDimension());
        this.setHomePos(entry.getPos(), dimKey);
        return true;
    }

    public AuthorizedChestEntry authorizeNearestContainerAround(BlockPos center, Level level, UUID playerUUID, double radius) {
        if (center == null || level == null) return null;
        int r = (int) Math.ceil(radius);
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (isChestAuthorized(p, level)) continue;
                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(p);
                    boolean isContainer = (be != null && (be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).isPresent() || be instanceof net.minecraft.world.Container))
                            || level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.ChestBlock;
                    if (isContainer) {
                        double d = center.distSqr(p);
                        if (d < bestDist) {
                            bestDist = d;
                            bestPos = p.immutable();
                        }
                    }
                }
            }
        }
        if (bestPos != null) {
            return authorizeChestWithEntry(bestPos, level.dimension().location(), playerUUID);
        }
        return null;
    }

    public boolean isChestAuthorized(BlockPos pos, Level level) {
        if (pos == null || level == null) return false;
        // CRITICAL SAFETY MANDATE: Empty list NEVER authorizes chests!
        if (authorizedChests.isEmpty()) return false;

        AuthorizedChestEntry entry = getAuthorizedChestAt(pos, level);
        if (entry != null) {
            if (level instanceof ServerLevel sl && getOwnerUUID() != null) {
                ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(getOwnerUUID());
                if (owner != null && !sl.mayInteract(owner, entry.getPos())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public boolean isChestAuthorized(BlockPos pos) {
        return isChestAuthorized(pos, this.level());
    }

    public java.util.Optional<BlockPos> getNearestAuthorizedChest(BlockPos origin, Level level, double maxRadius) {
        if (origin == null || level == null || authorizedChests.isEmpty()) return java.util.Optional.empty();
        ResourceLocation currentDim = level.dimension().location();
        double maxDistSq = maxRadius * maxRadius;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (AuthorizedChestEntry entry : authorizedChests) {
            if (entry.getDimension().equals(currentDim)) {
                net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(entry.getPos());
                if (be != null && be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).isPresent()) {
                    double d = origin.distSqr(entry.getPos());
                    if (d <= maxDistSq && d < bestDist) {
                        bestDist = d;
                        best = entry.getPos();
                    }
                }
            }
        }
        return java.util.Optional.ofNullable(best);
    }

    /**
     * Smart multi-chest routing: picks the best authorized chest for a given ItemStack
     * considering existing stacks of the same item, matching ChestCategory, free space,
     * primary chest bonus, and distance.
     */
    public java.util.Optional<AuthorizedChestEntry> getBestAuthorizedChestForItem(BlockPos origin, Level level, ItemStack stack, double maxRadius) {
        if (origin == null || level == null || authorizedChests.isEmpty()) return java.util.Optional.empty();
        ResourceLocation currentDim = level.dimension().location();
        double maxDistSq = maxRadius * maxRadius;
        AuthorizedChestEntry bestEntry = null;
        double bestScore = -1e9;

        for (AuthorizedChestEntry entry : authorizedChests) {
            if (!entry.getDimension().equals(currentDim)) continue;
            double distSq = origin.distSqr(entry.getPos());
            if (distSq > maxDistSq) continue;

            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(entry.getPos());
            if (be == null) continue;
            var cap = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER);
            if (!cap.isPresent()) continue;
            net.minecraftforge.items.IItemHandler handler = cap.orElse(null);
            if (handler == null) continue;

            // Check if chest can accept at least 1 item of stack (when stack is provided)
            boolean hasSameItem = false;
            boolean canInsert = (stack == null || stack.isEmpty());
            if (stack != null && !stack.isEmpty()) {
                ItemStack sim = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(handler, stack.copyWithCount(1), true);
                canInsert = sim.isEmpty();
                if (!canInsert) continue;
                for (int s = 0; s < handler.getSlots(); s++) {
                    ItemStack inChest = handler.getStackInSlot(s);
                    if (!inChest.isEmpty() && inChest.is(stack.getItem())) {
                        hasSameItem = true;
                        break;
                    }
                }
            } else {
                for (int s = 0; s < handler.getSlots(); s++) {
                    if (handler.getStackInSlot(s).isEmpty()) {
                        canInsert = true;
                        break;
                    }
                }
                if (!canInsert) continue;
            }

            double score = 0.0;
            if (stack != null && !stack.isEmpty()) {
                if (entry.getCategory() != ChestCategory.ALL && entry.getCategory().matchesItem(stack)) {
                    score += 300.0; // Dedicated category chest match!
                } else if (entry.getCategory() == ChestCategory.ALL) {
                    score += 50.0;
                } else {
                    score -= 150.0; // Avoid putting non-matching items into specialized chests unless no other option
                }
                if (hasSameItem) {
                    score += 220.0; // Stack consolidation bonus
                }
            }
            if (this.homePos != null && this.homePos.equals(entry.getPos())) {
                score += 65.0; // Primary chest bonus
            }
            score -= Math.sqrt(distSq) * 1.5;

            if (score > bestScore) {
                bestScore = score;
                bestEntry = entry;
            }
        }
        return java.util.Optional.ofNullable(bestEntry);
    }

    public java.util.List<AuthorizedChestEntry> getAuthorizedChestEntries() {
        return java.util.Collections.unmodifiableList(authorizedChests);
    }

    public java.util.Set<BlockPos> getAuthorizedChests() {
        java.util.Set<BlockPos> positions = new java.util.HashSet<>();
        for (AuthorizedChestEntry entry : authorizedChests) {
            positions.add(entry.getPos());
        }
        return java.util.Collections.unmodifiableSet(positions);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void registerGoals() {
        // Priority 0: Float in water (prevents drowning)
        // 1.13.11: se desactiva mientras el ejecutor bucea hacia un objetivo bajo el agua (antes lo empujaba
        // siempre a la superficie y quedaba "atascado" flotando encima del camino).
        this.goalSelector.addGoal(0, new FloatGoal(this) {
            @Override
            public boolean canUse() {
                return CompanionEntity.this.diveTicks <= 0 && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return CompanionEntity.this.diveTicks <= 0 && super.canContinueToUse();
            }
        });
        // Priority 1: Open and pass doors
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        // Secondary ambient goals
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public CompanionBrain getCompanionBrain() {
        return brain;
    }

    public com.aicompanion.ai.stuck.StuckDetector getStuckDetector() {
        return brain != null ? brain.getStuckDetector() : null;
    }

    public NavigationManager getNavManager() {
        return navManager;
    }

    public CompanionInventoryHandler getInventoryHandler() {
        return inventoryHandler;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public String getCustomCompanionName() {
        return customCompanionName;
    }

    public void setCustomCompanionName(String name) {
        this.customCompanionName = (name != null && !name.trim().isEmpty()) ? name.trim() : "Esclavo";
        this.setCustomName(Component.literal(this.customCompanionName));
        this.setCustomNameVisible(true);
    }

    public CompanionState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(CompanionState state) {
        this.currentState = state;
    }

    public DefaultBehavior getPreferredIdleBehavior() {
        return preferredIdleBehavior;
    }

    public void setPreferredIdleBehavior(DefaultBehavior behavior) {
        this.preferredIdleBehavior = behavior;
    }

    public BlockPos getHomePos() {
        return homePos;
    }

    public void setHomePos(BlockPos pos, ResourceKey<Level> dim) {
        this.homePos = pos;
        this.homeDim = dim;
    }

    public ResourceKey<Level> getHomeDim() {
        return homeDim;
    }

    public int getCompanionLevel() { return companionLevel; }
    public int getMiningXP() { return miningXP; }
    public int getCombatXP() { return combatXP; }
    public int getGatheringXP() { return gatheringXP; }
    public int getBuildingXP() { return buildingXP; }
    public int getExplorationXP() { return explorationXP; }
    public int getBlocksMined() { return blocksMined; }
    public int getMobsKilled() { return mobsKilled; }
    public int getTasksCompleted() { return tasksCompleted; }

    public void incrementBlocksMined() { this.blocksMined++; }
    public void incrementMobsKilled() { this.mobsKilled++; }
    public void incrementMobsKilled(int amount) { this.mobsKilled += amount; }
    public void incrementTasksCompleted() { this.tasksCompleted++; }

    public void addMiningXP(int amount) {
        this.miningXP += amount;
        checkLevelUp();
    }

    public void addCombatXP(int amount) {
        this.combatXP += amount;
        checkLevelUp();
    }

    public void addGatheringXP(int amount) {
        this.gatheringXP += amount;
        checkLevelUp();
    }

    public void addBuildingXP(int amount) {
        this.buildingXP += amount;
        checkLevelUp();
    }

    public void addExplorationXP(int amount) {
        this.explorationXP += amount;
        checkLevelUp();
    }

    private void checkLevelUp() {
        int totalXP = miningXP + combatXP + gatheringXP + buildingXP + explorationXP;
        int nextLevelXP = companionLevel * 100;
        if (totalXP >= nextLevelXP) {
            companionLevel++;
            if (this.level() instanceof ServerLevel) {
                Player owner = getOwnerPlayer();
                if (owner != null) {
                    owner.sendSystemMessage(Component.literal("[Esclavo] ").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(customCompanionName + " ha subido al Nivel " + companionLevel + "!").withStyle(ChatFormatting.WHITE)));
                }
            }
        }
    }

    public Player getOwnerPlayer() {
        if (ownerUUID != null && this.level() instanceof ServerLevel sl) {
            return sl.getServer().getPlayerList().getPlayer(ownerUUID);
        }
        return null;
    }

    public void tellOwner(String message) {
        Player owner = getOwnerPlayer();
        if (owner != null && owner.isAlive() && !owner.isDeadOrDying()) {
            String formatted = formatWithPersonality(message);
            owner.sendSystemMessage(Component.literal("[" + customCompanionName + "] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(formatted).withStyle(ChatFormatting.WHITE)));
        }
    }

    private String formatWithPersonality(String message) {
        if (message == null || message.isEmpty()) return message;
        try {
            String personality = com.aicompanion.AICompanionConfig.COMMON.aiPersonality.get();
            if (personality == null) return message;
            personality = personality.trim().toUpperCase();
            if (message.startsWith("¡") || message.startsWith("¿") || message.startsWith("[") || message.contains(":")) {
                return message;
            }
            return switch (personality) {
                case "HUMOROUS" -> switch ((int) (Math.abs(message.hashCode()) % 4)) {
                    case 0 -> "Jeje, " + Character.toLowerCase(message.charAt(0)) + message.substring(1);
                    case 1 -> message + " (¡Espero que haya pastel después!)";
                    case 2 -> message + " (A sus órdenes, jefazo).";
                    default -> "Bueno... " + message;
                };
                case "STOIC" -> switch ((int) (Math.abs(message.hashCode()) % 3)) {
                    case 0 -> "Afirmativo. " + message;
                    case 1 -> message;
                    default -> "Entendido. " + message;
                };
                default -> message; // LOYAL
            };
        } catch (Exception e) {
            return message;
        }
    }

    public void returnToDefaultBehavior() {
        if (this.brain != null) {
            if ((this.brain.getActiveBuildProject() != null && this.brain.getActiveBuildProject().isActive())
                    || (this.brain.getTaskManager().getCurrentTask() != null && this.brain.getTaskManager().getCurrentTask().isPaused())) {
                this.brain.getTaskManager().resume();
                return;
            }
        }
        if (this.preferredIdleBehavior == DefaultBehavior.FOLLOW) {
            this.currentState = CompanionState.FOLLOWING;
            this.brain.getTaskManager().setTask(new FollowTask(this));
        } else {
            this.currentState = CompanionState.STAYING;
            this.brain.getTaskManager().setTask(new StayTask(this));
        }
    }

    @Override
    public void aiStep() {
        if (this.isRemoved() || this.getRemovalReason() != null) {
            return;
        }
        super.aiStep();
        if (!this.level().isClientSide) {
            if (this.isRemoved() || this.getRemovalReason() != null) {
                return;
            }
            if (this.diveTicks > 0) {
                this.diveTicks--;
            }
            // Auto-organize debounced tick
            if (this.inventoryHandler != null) {
                this.inventoryHandler.tickAutoOrganize(this);
            }

            // Handle Downed state
            if (currentState == CompanionState.DOWNED) {
                downedTicks++;
                this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
                if (this.tickCount % 40 == 0) {
                    syncToOwner();
                }
                int downedTimeoutTicks = AICompanionConfig.COMMON.downedTimeoutSeconds.get() * 20;
                if (downedTicks > downedTimeoutTicks) {
                    Player owner = getOwnerPlayer();
                    if (owner != null && Boolean.TRUE.equals(AICompanionConfig.COMMON.showDeathNotification.get())) {
                        owner.sendSystemMessage(Component.literal("[" + customCompanionName + "] ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("Ha sucumbido a sus heridas tras " + (downedTimeoutTicks / 1200) + " minutos sin auxilio.").withStyle(ChatFormatting.RED)));
                    }
                    this.die(this.damageSources().generic());
                    return;
                }
                return;
            }

            if (currentState == CompanionState.DEAD_WAITING_RESPAWN || currentState == CompanionState.RESPAWNING) {
                return;
            }

            // Sync armor from inventory to visual equipment slots
            syncVisualEquipment();

            // Auto-eat logic
            if (Boolean.TRUE.equals(AICompanionConfig.COMMON.autoEat.get()) && this.getHealth() < this.getMaxHealth() && this.tickCount % 40 == 0) {
                tryEatFood();
            }

            // 1.13.9: regeneración natural (como un jugador saciado). Antes se quedaba a 1 HP para siempre sin comida.
            // 1 HP cada 4 s; descansando (HealTask) 1 HP por segundo. Solo si lleva 5 s sin recibir daño.
            if (this.getHealth() < this.getMaxHealth() && this.tickCount - this.lastDamageTick > 100) {
                var curTask = brain.getTaskManager().getCurrentTask();
                boolean resting = curTask != null && "HEAL".equals(curTask.getTaskType());
                if (this.tickCount % (resting ? 20 : 80) == 0) {
                    this.heal(1.0F);
                }
            }

            // Brain & Task tick
            brain.tick();

            if (this.isRemoved() || this.getRemovalReason() != null) {
                return;
            }

            // Navigation manager tick
            navManager.tick();

            // Left on top of its own temporary pillar (task interrupted / finished): descend safely
            if (temporaryBlocks.isStandingOnTracked(this)) {
                var cur = brain.getTaskManager().getCurrentTask();
                if (!(cur instanceof com.aicompanion.ai.tasks.ChopTask) && !(cur instanceof com.aicompanion.ai.tasks.BuildTask)
                        && (navManager.isDone() || navManager.isStuck())) {
                    temporaryBlocks.tickCleanup(this, true, true);
                }
            }

            // Periodic sync to owner (every 40 ticks = 2s) for live HUD locator
            if (this.tickCount % 40 == 0) {
                syncToOwner();
            }

            // Periodic save to CompanionSavedData every 100 ticks (5s)
            if (this.tickCount % 100 == 0 && this.level() instanceof ServerLevel sl) {
                com.aicompanion.data.CompanionSavedData.get(sl).saveCompanion(this);
            }

            // Low health tactical retreat when fighting
            if (this.getHealth() <= this.getMaxHealth() * 0.25f && this.tickCount % 60 == 0) {
                if (this.currentState == CompanionState.FIGHTING || this.currentState == CompanionState.DANGER) {
                    Player owner = getOwnerPlayer();
                    if (owner != null && owner.isAlive() && !owner.isDeadOrDying() && this.distanceToSqr(owner) > 16.0) {
                        this.tellOwner("¡Salud crítica! Retirándome hacia tu posición.");
                        this.navManager.navigateTo(new com.aicompanion.internal.baritone.goals.GoalEntity(owner, 2.0, 3.5), com.aicompanion.ai.nav.MovementPolicy.sprint());
                    }
                }
            }

            // Default behavior when no active task is running
            if (!brain.getTaskManager().isBusy()) {
                handleDefaultIdleBehavior();
            }
        }
    }

    public void syncToOwner() {
        Player owner = getOwnerPlayer();
        if (owner instanceof ServerPlayer sp && sp.isAlive() && !sp.isDeadOrDying()) {
            try {
                java.util.List<String> queueDesc = new java.util.ArrayList<>();
                if (this.brain != null && this.brain.getTaskManager() != null) {
                    var queue = this.brain.getTaskManager().getTaskQueue();
                    if (queue != null && queue.getTasks() != null) {
                        for (var t : queue.getTasks()) {
                            if (t != null) queueDesc.add(t.getDescription());
                        }
                    }
                }

                String taskDesc = "Ninguna";
                String taskProg = "";
                String taskType = "";
                String taskStage = "";
                String taskSubstate = "";
                String taskFailure = "";
                long lastProg = 0L;
                if (this.brain != null && this.brain.getTaskManager() != null) {
                    var current = this.brain.getTaskManager().getCurrentTask();
                    if (current != null) {
                        taskDesc = current.getDescription() != null ? current.getDescription() : "En progreso";
                        taskProg = current.getProgress() != null ? current.getProgress() : "";
                        taskType = current.getTaskType() != null ? current.getTaskType() : "";
                        taskStage = current.getStage() != null ? current.getStage() : "";
                        taskSubstate = current.getSubstate() != null ? current.getSubstate() : "";
                        taskFailure = current.getFailureReason() != null ? current.getFailureReason() : "";
                        lastProg = current.getLastProgressTick();
                    }
                }

                String home = this.homePos != null ? this.homePos.toShortString() : "No establecida";
                String dim = this.level() != null ? this.level().dimension().location().toString() : "minecraft:overworld";

                com.aicompanion.network.packets.S2C_SyncCompanionDataPacket packet = new com.aicompanion.network.packets.S2C_SyncCompanionDataPacket(
                        this.getUUID(), this.getId(), this.getCustomCompanionName(),
                        this.getCurrentState() != null ? this.getCurrentState().name() : "IDLE",
                        this.getPreferredIdleBehavior() != null ? this.getPreferredIdleBehavior().name() : "FOLLOW",
                        this.getHealth(), this.getMaxHealth(), this.getCompanionLevel(),
                        this.getMiningXP(), this.getCombatXP(), this.getGatheringXP(),
                        this.getBuildingXP(), this.getExplorationXP(),
                        this.getBlocksMined(), this.getMobsKilled(), this.getTasksCompleted(),
                        taskDesc, taskProg, taskType, taskStage, taskSubstate, taskFailure,
                        lastProg, queueDesc, home, dim,
                        this.getBlockX(), this.getBlockY(), this.getBlockZ(),
                        this.settings.isAutoFollow(), this.settings.isAutoEquip(), this.settings.isAutoEat(),
                        this.settings.isAutoDeliver(), this.settings.isAutoReturn(),
                        this.settings.isAllowBlockBreaking(), this.settings.isAllowBlockPlacement(),
                        this.settings.isAllowCombat(), this.settings.isEnableChunkLoading()
                );

                this.populateExtendedSyncData(packet);

                com.aicompanion.network.CompanionNetworking.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                        packet
                );
            } catch (Exception e) {
                com.aicompanion.AICompanionMod.LOGGER.debug("syncToOwner packet send failed: {}", e.getMessage());
            }
        }
    }

    public void populateExtendedSyncData(com.aicompanion.network.packets.S2C_SyncCompanionDataPacket packet) {
        if (packet == null) return;
        java.util.List<com.aicompanion.network.packets.S2C_SyncCompanionDataPacket.AuthorizedChestDTO> dtos = new java.util.ArrayList<>();
        ResourceLocation curDim = this.level() != null ? this.level().dimension().location() : Level.OVERWORLD.location();

        for (AuthorizedChestEntry e : this.authorizedChests) {
            int usedSlots = 0;
            int totalSlots = 27;
            java.util.List<String> previewParts = new java.util.ArrayList<>();
            boolean existsInWorld = false;

            if (this.level() != null && curDim.equals(e.getDimension()) && this.level().isLoaded(e.getPos())) {
                net.minecraft.world.level.block.entity.BlockEntity be = this.level().getBlockEntity(e.getPos());
                if (be != null) {
                    var cap = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER);
                    if (cap.isPresent()) {
                        existsInWorld = true;
                        net.minecraftforge.items.IItemHandler handler = cap.orElse(null);
                        if (handler != null) {
                            totalSlots = handler.getSlots();
                            for (int i = 0; i < totalSlots; i++) {
                                ItemStack s = handler.getStackInSlot(i);
                                if (!s.isEmpty()) {
                                    usedSlots++;
                                    if (previewParts.size() < 3) {
                                        previewParts.add(s.getCount() + "x " + s.getHoverName().getString());
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                existsInWorld = true; // In another dimension or unloaded chunk
            }

            if (existsInWorld) {
                boolean isPrimary = (this.homePos != null && this.homePos.equals(e.getPos()));
                String preview = previewParts.isEmpty() ? "Vacío" : String.join(", ", previewParts);
                dtos.add(new com.aicompanion.network.packets.S2C_SyncCompanionDataPacket.AuthorizedChestDTO(
                        e.getChestId(),
                        e.getCustomName(),
                        e.getCategory().name(),
                        e.getCategory().getDisplayName(),
                        e.getPos().getX(),
                        e.getPos().getY(),
                        e.getPos().getZ(),
                        e.getDimension().toString(),
                        usedSlots,
                        totalSlots,
                        isPrimary,
                        preview
                ));
            }
        }

        String statusBanner = "EN ESPERA: Sin operación de baúl activa";
        String itemsBanner = "Mochila al día (sin excedentes pendientes)";

        if (this.brain != null && this.brain.getTaskManager() != null) {
            var curTask = this.brain.getTaskManager().getCurrentTask();
            if (curTask instanceof com.aicompanion.ai.tasks.DepositTask depTask) {
                statusBanner = "GUARDANDO AHORA → " + depTask.getTargetChestLabel();
                itemsBanner = "A guardar: " + depTask.getItemsToDepositSummary();
            } else if (curTask instanceof com.aicompanion.ai.tasks.WithdrawTask withTask) {
                statusBanner = "RETIRANDO AHORA → Baúl autorizado";
                itemsBanner = withTask.getDescription();
            } else {
                // Compute preview of what would be stored next and which chest it would go to
                int foodReserve = this.settings.getFoodStoragePolicy().getReserveLimit();
                java.util.List<String> surplusList = new java.util.ArrayList<>();
                AuthorizedChestEntry targetPreview = null;
                for (int i = 0; i < 36; i++) {
                    ItemStack s = this.inventoryHandler.getStackInSlot(i);
                    if (s.isEmpty()) continue;
                    if (ChestCategory.isHealthyFood(s)) {
                        if (foodReserve >= s.getCount()) {
                            foodReserve -= s.getCount();
                            continue;
                        }
                        int ex = s.getCount() - Math.max(0, foodReserve);
                        foodReserve = 0;
                        if (ex > 0) {
                            if (surplusList.size() < 3) surplusList.add(ex + "x " + s.getHoverName().getString());
                            if (targetPreview == null && this.level() != null) {
                                targetPreview = getBestAuthorizedChestForItem(this.blockPosition(), this.level(), s, 64.0).orElse(null);
                            }
                        }
                    } else if (com.aicompanion.ai.tasks.DepositTask.isDepositableSlot(this, i, 0)) {
                        if (surplusList.size() < 3) surplusList.add(s.getCount() + "x " + s.getHoverName().getString());
                        if (targetPreview == null && this.level() != null) {
                            targetPreview = getBestAuthorizedChestForItem(this.blockPosition(), this.level(), s, 64.0).orElse(null);
                        }
                    }
                }
                if (!surplusList.isEmpty()) {
                    String dest = targetPreview != null
                            ? ("[#" + targetPreview.getChestId() + "] " + targetPreview.getCustomName())
                            : (dtos.isEmpty() ? "Sin baúl autorizado" : "Baúl más cercano");
                    statusBanner = "EN COLA → Próximo destino sugerido: " + dest;
                    itemsBanner = "Excedentes en mochila: " + String.join(", ", surplusList);
                }
            }
        }

        String buildSummary = "Ninguna construcción activa";
        if (this.brain != null && this.brain.getActiveBuildProject() != null) {
            var proj = this.brain.getActiveBuildProject();
            if (proj.isActive()) {
                buildSummary = proj.getBlueprintId() + " en (" + proj.getOriginPos().toShortString() + ") — "
                        + proj.getCurrentBlockIndex() + "/" + proj.getTotalBlocks() + " bloques";
            }
        }

        int rememberedCount = 0;
        if (this.brain != null && this.brain.getMemory() != null && this.brain.getMemory().getWorldModel() != null) {
            rememberedCount = this.brain.getMemory().getWorldModel().getTotalRememberedCount();
        }
        int dangerCount = this.brain != null ? this.brain.getDangerZoneCount() : 0;

        String autonomyMode = "PROACTIVE";
        String personalityMode = "SERIOUS";
        try {
            autonomyMode = com.aicompanion.AICompanionConfig.COMMON.aiAutonomyLevel.get();
            personalityMode = com.aicompanion.AICompanionConfig.COMMON.aiPersonality.get();
        } catch (Exception ignored) {}

        packet.authorizedChests = dtos;
        packet.foodPolicyName = this.settings.getFoodStoragePolicy().name();
        packet.foodPolicyDisplay = this.settings.getFoodStoragePolicy().getDisplayName();
        packet.autoDepositPolicyName = this.settings.getAutoDepositPolicy().name();
        packet.autoDepositPolicyDisplay = this.settings.getAutoDepositPolicy().getDisplayName();
        packet.logisticsStatusBanner = statusBanner;
        packet.logisticsItemsBanner = itemsBanner;
        packet.activeBuildSummary = buildSummary;
        packet.rememberedDepositsCount = rememberedCount;
        packet.dangerZonesCount = dangerCount;
        packet.aiAutonomyMode = autonomyMode;
        packet.aiPersonalityMode = personalityMode;
    }

    private int idleFollowCooldown = 0;
    private int autoLogisticsCooldown = 0;

    private void handleDefaultIdleBehavior() {
        if (preferredIdleBehavior == DefaultBehavior.FOLLOW) {
            Player owner = getOwnerPlayer();
            if (owner != null && owner.isAlive() && !owner.isDeadOrDying()) {
                if (!this.level().dimension().equals(owner.level().dimension())) {
                    this.currentState = CompanionState.IDLE;
                    this.navManager.stop();
                    return;
                }

                this.getLookControl().setLookAt(owner, 10.0F, (float) this.getMaxHeadXRot());
                double distSq = this.distanceToSqr(owner);
                if (distSq > 9.0) {
                    this.currentState = CompanionState.FOLLOWING;
                    double speed = com.aicompanion.ai.nav.MovementPolicy.sprint();
                    this.setSprinting(com.aicompanion.ai.nav.MovementPolicy.sprintingAllowed());
                    if (--idleFollowCooldown <= 0) {
                        this.navManager.navigateTo(new com.aicompanion.internal.baritone.goals.GoalEntity(owner, 2.5, 4.5), speed);
                        idleFollowCooldown = 15;
                    }
                } else if (distSq <= 6.25) {
                    this.currentState = CompanionState.FOLLOWING;
                    this.setSprinting(false);
                    this.navManager.stop();
                    idleFollowCooldown = 0;
                    temporaryBlocks.tickCleanup(this, true);
                }
            } else {
                this.currentState = CompanionState.IDLE;
                this.navManager.stop();
                temporaryBlocks.tickCleanup(this, true);
            }
        } else {
            // STAY mode
            this.navManager.stop();
            this.currentState = CompanionState.STAYING;
        }

        if (autoLogisticsCooldown > 0) autoLogisticsCooldown--;
        if (this.tickCount % 60 == 0) {
            handleProactiveAutonomy();
        }
    }

    private void handleProactiveAutonomy() {
        try {
            // 1. Autonomous Food & Surplus Storage Policy Execution when idle/following
            if (autoLogisticsCooldown <= 0 && this.brain != null && this.brain.getTaskManager() != null && !this.brain.getTaskManager().isBusy()) {
                var foodPolicy = this.settings.getFoodStoragePolicy();
                var depositPolicy = this.settings.getAutoDepositPolicy();

                // 1a. If policy is DELIVER_TO_OWNER and companion has > 16 food, hand surplus directly to owner if nearby
                if (foodPolicy == CompanionSettings.FoodStoragePolicy.DELIVER_TO_OWNER) {
                    Player owner = getOwnerPlayer();
                    if (owner != null && owner.isAlive() && this.distanceToSqr(owner) <= 36.0) {
                        int foodLeftToKeep = 16;
                        int givenCount = 0;
                        for (int i = 0; i < 36; i++) {
                            ItemStack s = this.inventoryHandler.getStackInSlot(i);
                            if (s.isEmpty() || !ChestCategory.isHealthyFood(s)) continue;
                            if (foodLeftToKeep >= s.getCount()) {
                                foodLeftToKeep -= s.getCount();
                                continue;
                            }
                            int excess = s.getCount() - Math.max(0, foodLeftToKeep);
                            foodLeftToKeep = 0;
                            if (excess > 0) {
                                ItemStack extracted = this.inventoryHandler.extractItem(i, excess, false);
                                if (!extracted.isEmpty()) {
                                    if (!owner.getInventory().add(extracted)) {
                                        this.inventoryHandler.insertItem(i, extracted, false);
                                    } else {
                                        givenCount += excess;
                                    }
                                }
                            }
                        }
                        if (givenCount > 0) {
                            tellOwner("Te he entregado " + givenCount + " de comida recolectada (política: entregar excedente).");
                            autoLogisticsCooldown = 400;
                        }
                    }
                }

                // 1b. Autonomous chest storage when near an authorized chest and policy permits
                if (autoLogisticsCooldown <= 0 && depositPolicy != CompanionSettings.AutoDepositPolicy.MANUAL_ONLY && !this.authorizedChests.isEmpty()) {
                    int totalFood = 0;
                    int depositableSlots = 0;
                    int foodReserve = foodPolicy.getReserveLimit();
                    int foodSim = foodReserve;
                    for (int i = 0; i < 36; i++) {
                        ItemStack s = this.inventoryHandler.getStackInSlot(i);
                        if (s.isEmpty()) continue;
                        if (s.isEdible()) {
                            totalFood += s.getCount();
                            if (foodSim < s.getCount()) {
                                depositableSlots++;
                                foodSim = 0;
                            } else {
                                foodSim -= s.getCount();
                            }
                        } else if (com.aicompanion.ai.tasks.DepositTask.isDepositableSlot(this, i, 0)) {
                            depositableSlots++;
                        }
                    }

                    boolean foodNeedsStore = (foodPolicy == CompanionSettings.FoodStoragePolicy.STORE_ALL_FOOD && totalFood > 8)
                            || (foodPolicy == CompanionSettings.FoodStoragePolicy.RESERVE_16_STORE_REST && totalFood > 24);
                    boolean idleSurplusNeedsStore = (depositPolicy == CompanionSettings.AutoDepositPolicy.ON_FULL_OR_IDLE && depositableSlots >= 6);
                    boolean fullNeedsStore = this.inventoryHandler.isCapacityCritical() && depositableSlots > 0;

                    if (foodNeedsStore || idleSurplusNeedsStore || fullNeedsStore) {
                        var nearChest = getNearestAuthorizedChest(this.blockPosition(), this.level(), 28.0);
                        if (nearChest.isPresent()) {
                            autoLogisticsCooldown = 600; // 30s cooldown between autonomous deposits
                            this.brain.getTaskManager().setTask(new com.aicompanion.ai.tasks.DepositTask(this));
                            return;
                        }
                    }
                }
            }

            String autonomy = com.aicompanion.AICompanionConfig.COMMON.aiAutonomyLevel.get();
            if (!"PROACTIVE".equalsIgnoreCase(autonomy) && !"SEMI_AUTONOMOUS".equalsIgnoreCase(autonomy)) {
                return;
            }
            // 2. Proactive auto-heal if hurt
            if (this.getHealth() < this.getMaxHealth() - 4.0F) {
                tryEatFood();
            }
            // 3. Proactive illumination: if standing in darkness and block placement allowed, place torch if available
            if (this.settings.isAllowBlockPlacement() && this.level() instanceof ServerLevel sl) {
                BlockPos pos = this.blockPosition();
                if (sl.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) < 4) {
                    int torchSlot = this.inventoryHandler.findItemSlot(net.minecraft.world.item.Items.TORCH);
                    if (torchSlot != -1) {
                        BlockPos placePos = pos;
                        if (!sl.getBlockState(placePos).canBeReplaced()) {
                            placePos = pos.above();
                        }
                        if (sl.getBlockState(placePos).canBeReplaced() && net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState().canSurvive(sl, placePos)) {
                            sl.setBlock(placePos, net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState(), 3);
                            this.inventoryHandler.extractItem(torchSlot, 1, false);
                            sl.playSound(null, placePos, net.minecraft.sounds.SoundEvents.WOOD_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public CompanionSettings getSettings() {
        return settings;
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        if (currentState == CompanionState.DOWNED || inventoryHandler == null) return false;
        if (com.aicompanion.inventory.ItemHeuristicValue.isTrash(stack, this)) return false;
        return inventoryHandler.hasSpaceFor(stack);
    }

    @Override
    protected void pickUpItem(net.minecraft.world.entity.item.ItemEntity itemEntity) {
        // Player-like pickup: goes into the backpack inventory, never swaps the equipped tool.
        if (itemEntity == null || !itemEntity.isAlive() || itemEntity.hasPickUpDelay()) return;
        if (getCompanionBrain() != null && getCompanionBrain().isDiscardedItem(itemEntity)) return;
        ItemStack stack = itemEntity.getItem();
        int before = stack.getCount();
        ItemStack rest = inventoryHandler.addItem(stack);
        int taken = before - rest.getCount();
        if (taken > 0) {
            this.onItemPickup(itemEntity);
            this.take(itemEntity, taken);
            if (rest.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(rest);
            }
        }
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (this.inventoryHandler == null) return ItemStack.EMPTY;
        return switch (slot) {
            case HEAD -> this.inventoryHandler.getStackInSlot(CompanionInventoryHandler.SLOT_HELMET);
            case CHEST -> this.inventoryHandler.getStackInSlot(CompanionInventoryHandler.SLOT_CHEST);
            case LEGS -> this.inventoryHandler.getStackInSlot(CompanionInventoryHandler.SLOT_LEGS);
            case FEET -> this.inventoryHandler.getStackInSlot(CompanionInventoryHandler.SLOT_BOOTS);
            case MAINHAND -> this.inventoryHandler.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
            case OFFHAND -> this.inventoryHandler.getStackInSlot(CompanionInventoryHandler.SLOT_OFFHAND);
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        this.verifyEquippedItem(stack);
        if (this.inventoryHandler == null) return;
        int index = switch (slot) {
            case HEAD -> CompanionInventoryHandler.SLOT_HELMET;
            case CHEST -> CompanionInventoryHandler.SLOT_CHEST;
            case LEGS -> CompanionInventoryHandler.SLOT_LEGS;
            case FEET -> CompanionInventoryHandler.SLOT_BOOTS;
            case MAINHAND -> CompanionInventoryHandler.SLOT_MAINHAND;
            case OFFHAND -> CompanionInventoryHandler.SLOT_OFFHAND;
        };
        this.inventoryHandler.setStackInSlot(index, stack);
        this.onEquipItem(slot, stack, stack);
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return java.util.List.of(
                getItemBySlot(EquipmentSlot.FEET),
                getItemBySlot(EquipmentSlot.LEGS),
                getItemBySlot(EquipmentSlot.CHEST),
                getItemBySlot(EquipmentSlot.HEAD)
        );
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return java.util.List.of(
                getItemBySlot(EquipmentSlot.MAINHAND),
                getItemBySlot(EquipmentSlot.OFFHAND)
        );
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ItemStack main = this.getMainHandItem();
        if (!main.isEmpty()) {
            var modifiers = main.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE);
            for (var mod : modifiers) {
                var attr = this.getAttribute(Attributes.ATTACK_DAMAGE);
                if (attr != null && !attr.hasModifier(mod)) {
                    damage += (float) mod.getAmount();
                }
            }
        }

        float knockback = (float) this.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        if (target instanceof LivingEntity livingTarget) {
            damage += net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageBonus(main, livingTarget.getMobType());
            knockback += (float) net.minecraft.world.item.enchantment.EnchantmentHelper.getKnockbackBonus(this);
        }

        int fireAspect = net.minecraft.world.item.enchantment.EnchantmentHelper.getFireAspect(this);
        if (fireAspect > 0) {
            target.setSecondsOnFire(fireAspect * 4);
        }

        boolean hit = target.hurt(this.damageSources().mobAttack(this), damage);
        if (hit) {
            if (knockback > 0.0F && target instanceof LivingEntity livingTarget) {
                livingTarget.knockback((double) (knockback * 0.5F),
                        (double) net.minecraft.util.Mth.sin(this.getYRot() * ((float) Math.PI / 180F)),
                        (double) (-net.minecraft.util.Mth.cos(this.getYRot() * ((float) Math.PI / 180F))));
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
            }
            this.doEnchantDamageEffects(this, target);
            this.setLastHurtMob(target);
        }
        return hit;
    }

    private void syncVisualEquipment() {
        if (this.tickCount % 20 == 0) {
            com.aicompanion.inventory.EquipmentManager.autoEquipBestArmorAndWeapons(this);
        }
    }

    public void tryEatFood() {
        if (!settings.isAutoEat()) return;
        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack stack = inventoryHandler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem().isEdible()) {
                float heal = stack.getItem().getFoodProperties(stack, this) != null ?
                        stack.getItem().getFoodProperties(stack, this).getNutrition() * 1.5f : 4.0f;
                this.heal(heal);
                stack.shrink(1);
                this.playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, 1.0f, 1.0f);
                break;
            }
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float damageMultiplier, DamageSource source) {
        // Capped fall damage prevents instant fatal drops during autonomous operations on cliffs
        float cappedDistance = Math.min(distance, 12.0F);
        return super.causeFallDamage(cappedDistance, damageMultiplier, source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isRemoved() || this.getRemovalReason() != null) {
            return false;
        }

        if (source.getEntity() instanceof Player p && p.getUUID().equals(ownerUUID)) {
            // Friendly fire protection against owner
            return false;
        }

        if (this.currentState == CompanionState.DEAD_WAITING_RESPAWN || this.currentState == CompanionState.RESPAWNING) {
            return false;
        }

        if (Boolean.TRUE.equals(AICompanionConfig.COMMON.allowDownedStateBeforeDeath.get())) {
            // If already DOWNED, further mob hits cannot kill him while waiting for rescue
            if (this.currentState == CompanionState.DOWNED) {
                return false;
            }
            if (this.getHealth() - amount <= 0.0F) {
                this.setHealth(1.0F);
                this.currentState = CompanionState.DOWNED;
                this.downedTicks = 0;
                this.navManager.stop();
                if (this.brain != null && this.brain.getTaskManager() != null) {
                    this.brain.getTaskManager().pause();
                }
                this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
                this.syncToOwner();
                Player owner = getOwnerPlayer();
                if (owner != null && Boolean.TRUE.equals(AICompanionConfig.COMMON.showDeathNotification.get())) {
                    owner.sendSystemMessage(Component.literal("[Alerta] " + customCompanionName + " ha caído herido en (" 
                            + this.getBlockX() + ", " + this.getBlockY() + ", " + this.getBlockZ() 
                            + ") y necesita ayuda urgente.")
                            .withStyle(ChatFormatting.RED));
                }
                if (source.getEntity() instanceof net.minecraft.world.entity.Mob attackingMob) {
                    if (attackingMob.getTarget() == this) {
                        attackingMob.setTarget(null);
                    }
                }
                return false;
            }
        } else {
            // Direct death mode (default): fatal damage triggers immediate auto-respawn countdown
            if (this.getHealth() - amount <= 0.0F) {
                this.die(source);
                return false;
            }
        }

        boolean damaged = super.hurt(source, amount);
        if (damaged) {
            this.lastDamageTick = this.tickCount;
            if (!this.level().isClientSide) {
                net.minecraft.world.entity.Entity trueSource = source.getEntity();
                if (trueSource instanceof LivingEntity attacker) {
                    if (this.brain != null) {
                        this.brain.onAttacked(attacker, source);
                    }
                }
            }
        }
        return damaged;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.getUUID().equals(ownerUUID) || player.hasPermissions(2)) {
                if (currentState == CompanionState.DOWNED) {
                    this.heal(10.0F);
                    this.currentState = CompanionState.IDLE;
                    this.returnToDefaultBehavior();
                    serverPlayer.sendSystemMessage(Component.translatable("message.aicompanion.revived"));
                    this.syncToOwner();
                    return InteractionResult.sidedSuccess(false);
                }

                openCompanionMenu(serverPlayer);
                return InteractionResult.sidedSuccess(false);
            } else {
                player.sendSystemMessage(Component.translatable("message.aicompanion.not_owner"));
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    public void openCompanionMenu(ServerPlayer player) {
        syncToOwner();
        NetworkHooks.openScreen(player, this, buffer -> {
            buffer.writeInt(this.getId());
            buffer.writeUUID(this.getUUID());
        });
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(customCompanionName);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CompanionMenu(containerId, playerInventory, this);
    }

    public void recallToOwnerWalking() {
        Player owner = getOwnerPlayer();
        if (owner == null) return;

        double distSq = this.distanceToSqr(owner);
        if (this.level().dimension() == owner.level().dimension() && distSq <= 9.0) {
            tellOwner("Ya estoy a tu lado.");
            return;
        }

        // Stash any active mission so it can be seamlessly resumed later via REANUDAR
        if (this.brain != null && this.brain.getTaskManager() != null) {
            this.brain.getTaskManager().stashActiveMission();
            this.brain.getTaskManager().setTask(new com.aicompanion.ai.tasks.ReturnTask(this, false));
        }

        com.aicompanion.ai.chunk.CompanionChunkManager.updateChunkTickets(this);

        if (this.level().dimension() != owner.level().dimension()) {
            tellOwner("Regresando a tu dimensión a través del portal...");
        } else {
            tellOwner("Regresando hacia tu posición a pie...");
        }
    }

    public void recallTo(BlockPos pos, ResourceKey<Level> targetDim) {
        if (this.brain != null && this.brain.getTaskManager() != null && this.brain.getTaskManager().getCurrentTask() != null) {
            this.brain.getTaskManager().stashActiveMission();
        }
        if (this.level().dimension() != targetDim && this.getServer() != null) {
            ServerLevel targetLevel = this.getServer().getLevel(targetDim);
            if (targetLevel != null) {
                Entity transferred = this.changeDimension(targetLevel, new com.aicompanion.ai.dimension.CompanionNetherTeleporter(null, targetDim));
                if (transferred instanceof CompanionEntity newComp) {
                    newComp.recallTo(pos, targetDim);
                    return;
                }
            }
        }
        if (this.level() instanceof ServerLevel sl) {
            Player owner = getOwnerPlayer();
            SafeSpawnFinder.SafeSpawnResult res;
            if (owner != null && owner.level() == this.level()) {
                res = SafeSpawnFinder.findSafeSpawnNearEntity(sl, owner, this);
            } else {
                res = SafeSpawnFinder.findSafeSpawnResult(sl, pos, this);
            }
            if (res.isSuccess()) {
                Vec3 safe = res.getPosition();
                this.teleportTo(safe.x, safe.y, safe.z);
                if (owner != null && owner.level() == this.level()) {
                    SafeSpawnFinder.faceEntity(this, owner);
                }
            }
        }
        this.getNavigation().stop();
        this.currentState = CompanionState.IDLE;
        if (this.preferredIdleBehavior == DefaultBehavior.FOLLOW) {
            this.currentState = CompanionState.FOLLOWING;
            this.brain.getTaskManager().setTask(new FollowTask(this));
        } else {
            this.currentState = CompanionState.STAYING;
            this.brain.getTaskManager().setTask(new StayTask(this));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }
        tag.putString("CustomCompanionName", customCompanionName);
        tag.putString("CurrentState", currentState.name());
        tag.putString("PreferredIdleBehavior", preferredIdleBehavior.name());
        tag.put("Inventory", inventoryHandler.serializeNBT());
        tag.put("Settings", settings.serializeNBT());

        if (homePos != null) {
            tag.put("HomePos", NbtUtils.writeBlockPos(homePos));
            tag.putString("HomeDim", homeDim.location().toString());
        }

        tag.putInt("CompanionLevel", companionLevel);
        tag.putInt("MiningXP", miningXP);
        tag.putInt("CombatXP", combatXP);
        tag.putInt("GatheringXP", gatheringXP);
        tag.putInt("BuildingXP", buildingXP);
        tag.putInt("ExplorationXP", explorationXP);
        tag.putInt("BlocksMined", blocksMined);
        tag.putInt("MobsKilled", mobsKilled);
        tag.putInt("TasksCompleted", tasksCompleted);

        CompoundTag brainTag = new CompoundTag();
        brain.save(brainTag);
        tag.put("BrainData", brainTag);
        tag.put("TemporaryBlocks", temporaryBlocks.save());

        tag.putInt("NextAuthorizedChestId", nextAuthorizedChestId);
        if (!authorizedChests.isEmpty()) {
            net.minecraft.nbt.ListTag chestList = new net.minecraft.nbt.ListTag();
            for (AuthorizedChestEntry e : authorizedChests) {
                CompoundTag chestTag = NbtUtils.writeBlockPos(e.getPos());
                chestTag.putInt("ChestId", e.getChestId());
                chestTag.putString("CustomName", e.getCustomName());
                chestTag.putString("Category", e.getCategory().name());
                chestTag.putString("Dimension", e.getDimension().toString());
                if (e.getAuthorizedBy() != null) {
                    chestTag.putUUID("AuthorizedBy", e.getAuthorizedBy());
                }
                chestList.add(chestTag);
            }
            tag.put("AuthorizedChests", chestList);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) {
            this.ownerUUID = tag.getUUID("OwnerUUID");
        }
        if (tag.contains("CustomCompanionName")) {
            String savedName = tag.getString("CustomCompanionName");
            // Old default name of the mod -> new default; custom names chosen by the player are kept
            if (savedName.isBlank() || savedName.equals("Esc" + "lab")) savedName = "Esclavo";
            this.customCompanionName = savedName;
            this.setCustomName(Component.literal(this.customCompanionName));
        }
        if (tag.contains("CurrentState")) {
            try {
                this.currentState = CompanionState.valueOf(tag.getString("CurrentState"));
            } catch (Exception ignored) {}
        }
        if (tag.contains("PreferredIdleBehavior")) {
            try {
                this.preferredIdleBehavior = DefaultBehavior.valueOf(tag.getString("PreferredIdleBehavior"));
            } catch (Exception ignored) {}
        }
        if (tag.contains("Inventory")) {
            inventoryHandler.deserializeNBT(tag.getCompound("Inventory"));
        }
        if (tag.contains("Settings")) {
            settings.deserializeNBT(tag.getCompound("Settings"));
        }
        if (tag.contains("HomePos")) {
            this.homePos = NbtUtils.readBlockPos(tag.getCompound("HomePos"));
            if (tag.contains("HomeDim")) {
                this.homeDim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("HomeDim")));
            }
        }
        this.companionLevel = tag.getInt("CompanionLevel");
        this.miningXP = tag.getInt("MiningXP");
        this.combatXP = tag.getInt("CombatXP");
        this.gatheringXP = tag.getInt("GatheringXP");
        this.buildingXP = tag.getInt("BuildingXP");
        this.explorationXP = tag.getInt("ExplorationXP");
        this.blocksMined = tag.getInt("BlocksMined");
        this.mobsKilled = tag.getInt("MobsKilled");
        this.tasksCompleted = tag.getInt("TasksCompleted");

        if (tag.contains("BrainData")) {
            brain.load(tag.getCompound("BrainData"));
        }
        if (tag.contains("TemporaryBlocks")) {
            temporaryBlocks.load(tag.getCompound("TemporaryBlocks"));
        }

        this.authorizedChests.clear();
        this.nextAuthorizedChestId = tag.contains("NextAuthorizedChestId") ? Math.max(1, tag.getInt("NextAuthorizedChestId")) : 1;
        if (tag.contains("AuthorizedChests", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = tag.getList("AuthorizedChests", net.minecraft.nbt.Tag.TAG_COMPOUND);
            int maxId = 0;
            for (int i = 0; i < list.size(); i++) {
                CompoundTag cTag = list.getCompound(i);
                BlockPos p = NbtUtils.readBlockPos(cTag);
                ResourceLocation dim = cTag.contains("Dimension") ? new ResourceLocation(cTag.getString("Dimension")) : Level.OVERWORLD.location();
                UUID authBy = cTag.hasUUID("AuthorizedBy") ? cTag.getUUID("AuthorizedBy") : this.ownerUUID;
                int cId = cTag.contains("ChestId") ? cTag.getInt("ChestId") : (i + 1);
                if (cId > maxId) maxId = cId;
                String cName = cTag.contains("CustomName") ? cTag.getString("CustomName") : ("Baúl #" + cId);
                ChestCategory cat = cTag.contains("Category") ? ChestCategory.fromName(cTag.getString("Category")) : ChestCategory.ALL;
                this.authorizedChests.add(new AuthorizedChestEntry(cId, p, dim, authBy, cName, cat));
            }
            this.nextAuthorizedChestId = Math.max(this.nextAuthorizedChestId, maxId + 1);
        }
    }

    public com.aicompanion.util.TemporaryBlockTracker getTemporaryBlocks() {
        return temporaryBlocks;
    }

    public com.aicompanion.ai.memory.CompanionMemory getMemory() {
        return this.brain != null ? this.brain.getMemory() : null;
    }

    @Override
    public void restoreFrom(Entity oldEntity) {
        super.restoreFrom(oldEntity);
        if (oldEntity instanceof CompanionEntity oldCompanion) {
            this.setUUID(oldCompanion.getUUID());
            this.setHealth(oldCompanion.getHealth());
            this.downedTicks = oldCompanion.downedTicks;
            this.ownerUUID = oldCompanion.ownerUUID;
            this.customCompanionName = oldCompanion.customCompanionName;
            this.setCustomName(Component.literal(this.customCompanionName));
            this.currentState = oldCompanion.currentState;
            this.preferredIdleBehavior = oldCompanion.preferredIdleBehavior;
            this.homePos = oldCompanion.homePos;
            this.homeDim = oldCompanion.homeDim;
            this.companionLevel = oldCompanion.companionLevel;
            this.miningXP = oldCompanion.miningXP;
            this.combatXP = oldCompanion.combatXP;
            this.gatheringXP = oldCompanion.gatheringXP;
            this.buildingXP = oldCompanion.buildingXP;
            this.explorationXP = oldCompanion.explorationXP;
            this.blocksMined = oldCompanion.blocksMined;
            this.mobsKilled = oldCompanion.mobsKilled;
            this.tasksCompleted = oldCompanion.tasksCompleted;

            // Transfer full inventory
            CompoundTag invTag = oldCompanion.inventoryHandler.serializeNBT();
            this.inventoryHandler.deserializeNBT(invTag);

            // Transfer settings
            CompoundTag setTag = oldCompanion.settings.serializeNBT();
            this.settings.deserializeNBT(setTag);

            // Transfer brain state (memory, goals, task manager)
            CompoundTag brainTag = new CompoundTag();
            oldCompanion.brain.save(brainTag);
            this.brain.load(brainTag);

            // Transfer authorized chests & temporary blocks
            this.authorizedChests.clear();
            this.authorizedChests.addAll(oldCompanion.authorizedChests);
            this.nextAuthorizedChestId = oldCompanion.nextAuthorizedChestId;
            CompoundTag tbTag = oldCompanion.temporaryBlocks.save();
            this.temporaryBlocks.load(tbTag);

            // Register newly spawned entity in CompanionManager
            com.aicompanion.data.CompanionManager.registerCompanion(this);
            com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DIM] CompanionEntity '{}' successfully restored internal state across dimension transfer.", this.customCompanionName);
        }
    }

    @Override
    public Entity changeDimension(ServerLevel destination) {
        return this.changeDimension(destination, new com.aicompanion.ai.dimension.CompanionNetherTeleporter(null, destination.dimension()));
    }

    @Override
    public Entity changeDimension(ServerLevel destination, net.minecraftforge.common.util.ITeleporter teleporter) {
        Entity newEntity = super.changeDimension(destination, teleporter);
        if (newEntity instanceof CompanionEntity newCompanion) {
            com.aicompanion.data.CompanionManager.registerCompanion(newCompanion);
            com.aicompanion.ai.chunk.CompanionChunkManager.updateChunkTickets(newCompanion);
        }
        return newEntity;
    }

    public void resetPortalCooldown() {
        setPortalCooldownTicks(0);
    }

    public void setPortalCooldownTicks(int ticks) {
        try {
            net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(net.minecraft.world.entity.Entity.class, this, Math.max(0, ticks), "f_19816_");
        } catch (Throwable t) {
            try {
                java.lang.reflect.Field f = net.minecraft.world.entity.Entity.class.getDeclaredField("portalCooldown");
                f.setAccessible(true);
                f.setInt(this, Math.max(0, ticks));
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        // 1.13.9: registrar SIEMPRE por qué desaparece (antes desaparecía sin dejar rastro en el registro)
        if (!this.level().isClientSide) {
            try {
                if (reason == RemovalReason.UNLOADED_TO_CHUNK && this.level() instanceof ServerLevel sl) {
                    com.aicompanion.data.CompanionSavedData.get(sl).saveCompanion(this);
                }
                StringBuilder where = new StringBuilder();
                if (reason != RemovalReason.UNLOADED_TO_CHUNK && reason != RemovalReason.CHANGED_DIMENSION) {
                    StackTraceElement[] st = Thread.currentThread().getStackTrace();
                    for (int i = 2; i < Math.min(st.length, 14); i++) {
                        where.append(" <- ").append(st[i].getClassName()).append('.').append(st[i].getMethodName());
                    }
                }
                com.aicompanion.debug.EsclavoTrace.log("ELIMINADO", this, "motivo=" + reason + " vida=" + this.getHealth() + where);
            } catch (Throwable ignored) {}
        }
        if (reason != RemovalReason.UNLOADED_TO_CHUNK) {
            com.aicompanion.ai.chunk.CompanionChunkManager.releaseTickets(this);
        }
        super.remove(reason);
    }

    @Override
    public void die(DamageSource damageSource) {
        if (this.isRemoved() || this.getRemovalReason() != null) {
            return; // Already removed or transferred across dimensions!
        }

        if (this.currentState == CompanionState.DEAD_WAITING_RESPAWN || this.currentState == CompanionState.RESPAWNING) {
            return; // Idempotent: already handled once
        }

        // Verify if another active instance exists in CompanionManager for this owner (e.g. cross-dimension transfer)
        if (this.getServer() != null && this.getOwnerUUID() != null) {
            CompanionEntity currentActive = com.aicompanion.data.CompanionManager.getCompanionForOwner(this.getServer(), this.getOwnerUUID());
            if (currentActive != null && currentActive != this && currentActive.isAlive() && !currentActive.isRemoved()) {
                com.aicompanion.AICompanionMod.LOGGER.info("[CompanionEntity] Ignorando die() espurio en instancia obsoleta {} (la entidad activa es {} en {})",
                        this.getId(), currentActive.getId(), currentActive.level().dimension().location());
                return;
            }
        }

        this.currentState = CompanionState.DEAD_WAITING_RESPAWN;

        if (!this.level().isClientSide && this.level() instanceof ServerLevel sl) {
            int respawnSecs = AICompanionConfig.COMMON.respawnSeconds.get();
            ServerLevel overworld = sl.getServer().overworld();
            long now = overworld != null ? overworld.getGameTime() : sl.getGameTime();
            long respawnGameTime = now + respawnSecs * 20L;
            String locationMode = AICompanionConfig.COMMON.respawnLocationMode.get();

            // Record high danger zone at death location
            if (this.brain != null) {
                this.brain.recordDangerZone(this.position());
            }

            // Save complete snapshot with inventory, equipment, level, XP (100% loss-free)
            com.aicompanion.data.CompanionSavedData.get(sl).saveDeadWaitingRespawn(this, respawnGameTime, respawnSecs, locationMode);

            AICompanionMod.LOGGER.warn("[ESCLAVO] NPC {} ({}) sufrió daño fatal. Transición: ALIVE -> DEAD_WAITING_RESPAWN. Inventario guardado (100% persistente). Reaparición programada en {} segundos.",
                    this.getId(), this.getCustomCompanionName(), respawnSecs);

            Player owner = getOwnerPlayer();
            if (owner != null && Boolean.TRUE.equals(AICompanionConfig.COMMON.showDeathNotification.get())) {
                owner.sendSystemMessage(Component.literal("[" + customCompanionName + "] ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Ha caído en combate (" + this.getBlockX() + ", " + this.getBlockY() + ", " + this.getBlockZ() 
                                + "). Reaparecerá automáticamente en " + respawnSecs + " segundos.")
                                .withStyle(ChatFormatting.RED)));
            }

            if (owner instanceof ServerPlayer sp) {
                com.aicompanion.network.packets.S2C_SyncCompanionDataPacket deathPacket = new com.aicompanion.network.packets.S2C_SyncCompanionDataPacket(
                        this.getUUID(), this.getId(), this.getCustomCompanionName(),
                        CompanionState.DEAD_WAITING_RESPAWN.name(), this.getPreferredIdleBehavior().name(),
                        0.0F, this.getMaxHealth(), this.getCompanionLevel(),
                        this.miningXP, this.combatXP, this.gatheringXP, this.buildingXP, this.explorationXP,
                        this.blocksMined, this.mobsKilled, this.tasksCompleted,
                        "Reaparición en: " + String.format("%02d:%02d", respawnSecs / 60, respawnSecs % 60), "", "", "", "", "", 0L,
                        java.util.Collections.emptyList(),
                        this.homePos != null ? this.homePos.toShortString() : "",
                        this.level().dimension().location().toString(),
                        this.getBlockX(), this.getBlockY(), this.getBlockZ(),
                        this.settings.isAutoFollow(), this.settings.isAutoEquip(), this.settings.isAutoEat(),
                        this.settings.isAutoDeliver(), this.settings.isAutoReturn(), this.settings.isAllowBlockBreaking(),
                        this.settings.isAllowBlockPlacement(), this.settings.isAllowCombat(), this.settings.isEnableChunkLoading()
                ).withRespawnCountdown(respawnSecs);
                com.aicompanion.network.CompanionNetworking.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                        deathPacket
                );
            }

            // Release chunk tickets and unregister active tracking
            com.aicompanion.ai.chunk.CompanionChunkManager.releaseTickets(this);
            com.aicompanion.data.CompanionManager.unregisterCompanion(this);

            // Safely discard the dead entity so it stops ticking immediately!
            this.discard();
            return;
        }
        super.die(damageSource);
    }

    // ===== 1.13.9: Esclavo NUNCA debe desaparecer como un mob salvaje =====
    // Vanilla elimina mobs no persistentes a >32 bloques del jugador (al azar tras 30 s) o a >128 bloques;
    // el mod "letmedespawn" además quita la persistencia a los mobs que recogen objetos.
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void checkDespawn() {
        // no-op: un compañero con dueño no se despawnea
    }
}
