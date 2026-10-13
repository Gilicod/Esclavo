package com.aicompanion.inventory;

import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionSettings;
import com.aicompanion.init.ModMenus;
import com.aicompanion.network.CompanionNetworking;
import com.aicompanion.network.packets.S2C_SyncCompanionDataPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CompanionMenu extends AbstractContainerMenu {
    public static final int SLOT_COMPANION_START = 0;
    public static final int SLOT_ARMOR_START = CompanionInventoryHandler.SLOT_ARMOR_START; // 36
    public static final int SLOT_MAINHAND = CompanionInventoryHandler.SLOT_MAINHAND;       // 40
    public static final int SLOT_OFFHAND = CompanionInventoryHandler.SLOT_OFFHAND;         // 41

    private final CompanionEntity companion;
    private final UUID companionUUID;
    private final CompanionInventoryHandler inventoryHandler;
    private int currentTab = 0;
    private final List<Integer> originalSlotX = new ArrayList<>();
    private final List<Integer> originalSlotY = new ArrayList<>();

    private boolean initialSyncDone = false;
    private int tickCounter = 0;

    @Override
    protected Slot addSlot(Slot slot) {
        originalSlotX.add(slot.x);
        originalSlotY.add(slot.y);
        return super.addSlot(slot);
    }

    public static CompanionMenu createClientMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        int entityId = extraData.readInt();
        UUID compUUID = (extraData.readableBytes() >= 16) ? extraData.readUUID() : null;

        Entity entity = playerInv.player.level().getEntity(entityId);
        CompanionEntity comp = null;
        if (entity instanceof CompanionEntity c) {
            comp = c;
        }

        return new CompanionMenu(containerId, playerInv, comp, compUUID);
    }

    public CompanionMenu(int containerId, Inventory playerInv, CompanionEntity companion) {
        this(containerId, playerInv, companion, companion != null ? companion.getUUID() : null);
    }

    public CompanionMenu(int containerId, Inventory playerInv, CompanionEntity companion, UUID compUUID) {
        super(ModMenus.COMPANION_MENU.get(), containerId);
        this.companion = companion;
        this.companionUUID = compUUID != null ? compUUID : (companion != null ? companion.getUUID() : null);

        this.inventoryHandler = companion != null ? companion.getInventoryHandler() : new CompanionInventoryHandler();
        // Ensure inventory handler always has at least TOTAL_SLOTS (42) to prevent any out-of-range slot indices
        if (this.inventoryHandler.getSlots() < CompanionInventoryHandler.TOTAL_SLOTS) {
            this.inventoryHandler.setSize(CompanionInventoryHandler.TOTAL_SLOTS);
        }

        // 1. Companion Main Inventory (36 slots: 4 rows x 9 columns) at top-right
        int compInvX = 194;
        int compInvY = 68;
        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 9; ++col) {
                int index = col + row * 9;
                int x = compInvX + col * 18;
                int y = compInvY + row * 18;
                this.addSlot(new SafeSlotItemHandler(this.inventoryHandler, index, x, y) {
                    @Override
                    public boolean isActive() {
                        return currentTab == 0;
                    }
                    @Override
                    public boolean mayPickup(Player player) {
                        return canModifyCompanionInventory(player);
                    }
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return canModifyCompanionInventory(playerInv.player) && super.mayPlace(stack);
                    }
                });
            }
        }

        // 2. Companion Armor Slots (indices 36..39) in column
        int armorX = 122;
        int armorY = 64;
        for (int i = 0; i < 4; ++i) {
            final EquipmentSlot slotType = EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, 3 - i);
            int armorIndex = CompanionInventoryHandler.SLOT_ARMOR_START + i;
            int x = armorX;
            int y = armorY + i * 22;
            this.addSlot(new SafeSlotItemHandler(this.inventoryHandler, armorIndex, x, y) {
                @Override
                public int getMaxStackSize() { return 1; }
                @Override
                public boolean mayPickup(Player player) {
                    return canModifyCompanionInventory(player);
                }
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return canModifyCompanionInventory(playerInv.player) && (companion == null || stack.canEquip(slotType, companion));
                }
                @Override
                public boolean isActive() {
                    return currentTab == 0;
                }
            });
        }

        // 3. Companion Hands: Main hand (slot 40) & Off hand (slot 41)
        int handsX = 162;
        int mainhandY = 74;
        int offhandY = 106;

        // Main hand slot (index 40)
        this.addSlot(new SafeSlotItemHandler(this.inventoryHandler, CompanionInventoryHandler.SLOT_MAINHAND, handsX, mainhandY) {
            @Override
            public int getMaxStackSize() { return 64; }
            @Override
            public boolean mayPickup(Player player) {
                return canModifyCompanionInventory(player);
            }
            @Override
            public boolean mayPlace(ItemStack stack) {
                return canModifyCompanionInventory(playerInv.player) && super.mayPlace(stack);
            }
            @Override
            public boolean isActive() {
                return currentTab == 0;
            }
        });

        // Off hand slot (index 41)
        this.addSlot(new SafeSlotItemHandler(this.inventoryHandler, CompanionInventoryHandler.SLOT_OFFHAND, handsX, offhandY) {
            @Override
            public int getMaxStackSize() { return 64; }
            @Override
            public boolean mayPickup(Player player) {
                return canModifyCompanionInventory(player);
            }
            @Override
            public boolean mayPlace(ItemStack stack) {
                return canModifyCompanionInventory(playerInv.player) && super.mayPlace(stack);
            }
            @Override
            public boolean isActive() {
                return currentTab == 0;
            }
        });

        // 4. Player Inventory (3 rows x 9 cols) centered below
        int playerInvX = 134;
        int playerInvY = 222;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int x = playerInvX + col * 18;
                int y = playerInvY + row * 18;
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, x, y) {
                    @Override
                    public boolean isActive() {
                        return currentTab == 0;
                    }
                });
            }
        }

        // 5. Player Hotbar (9 slots)
        int hotbarY = 282;
        for (int col = 0; col < 9; ++col) {
            int x = playerInvX + col * 18;
            int y = hotbarY;
            this.addSlot(new Slot(playerInv, col, x, y) {
                @Override
                public boolean isActive() {
                    return currentTab == 0;
                }
            });
        }
        // NO dangerous task syncing in constructor! Handled in broadcastChanges.
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        // Server-side safe data synchronization after menu is fully constructed
        if (companion != null && companion.level() != null && !companion.level().isClientSide) {
            Player owner = companion.getOwnerPlayer();
            if (owner instanceof ServerPlayer serverPlayer) {
                if (!initialSyncDone) {
                    initialSyncDone = true;
                    syncDataToClient(serverPlayer);
                } else {
                    tickCounter++;
                    if (tickCounter % 20 == 0) { // Sync every 1 second while GUI is open
                        syncDataToClient(serverPlayer);
                    }
                }
            }
        }
    }

    private static sun.misc.Unsafe UNSAFE = null;
    private static long slotXOffset = -1;
    private static long slotYOffset = -1;

    static {
        try {
            java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) field.get(null);

            for (java.lang.reflect.Field f : Slot.class.getDeclaredFields()) {
                if (f.getType() == int.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    if ("x".equals(f.getName()) || "field_75223_e".equals(f.getName()) || "f_40220_".equals(f.getName())) {
                        slotXOffset = UNSAFE.objectFieldOffset(f);
                    } else if ("y".equals(f.getName()) || "field_75221_f".equals(f.getName()) || "f_40221_".equals(f.getName())) {
                        slotYOffset = UNSAFE.objectFieldOffset(f);
                    }
                }
            }
            if (slotXOffset == -1 || slotYOffset == -1) {
                java.util.List<java.lang.reflect.Field> intFields = new java.util.ArrayList<>();
                for (java.lang.reflect.Field f : Slot.class.getDeclaredFields()) {
                    if (f.getType() == int.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        intFields.add(f);
                    }
                }
                if (intFields.size() >= 3) {
                    slotXOffset = UNSAFE.objectFieldOffset(intFields.get(1));
                    slotYOffset = UNSAFE.objectFieldOffset(intFields.get(2));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void setTabActive(int tab) {
        this.currentTab = tab;
        boolean isInv = (tab == 0);
        if (UNSAFE != null && slotXOffset != -1 && slotYOffset != -1) {
            for (int i = 0; i < this.slots.size() && i < originalSlotX.size(); i++) {
                Slot slot = this.slots.get(i);
                int targetX = isInv ? originalSlotX.get(i) : -2000;
                int targetY = isInv ? originalSlotY.get(i) : -2000;
                UNSAFE.putInt(slot, slotXOffset, targetX);
                UNSAFE.putInt(slot, slotYOffset, targetY);
            }
        }
    }

    public void syncDataToClient(ServerPlayer player) {
        if (companion == null || player == null) return;
        try {
            List<String> queueDesc = new ArrayList<>();
            if (companion.getCompanionBrain() != null && companion.getCompanionBrain().getTaskManager() != null) {
                var queue = companion.getCompanionBrain().getTaskManager().getTaskQueue();
                if (queue != null && queue.getTasks() != null) {
                    for (var t : queue.getTasks()) {
                        if (t != null) {
                            try {
                                String d = t.getDescription();
                                queueDesc.add(d != null ? d : "Misión");
                            } catch (Exception e) {
                                queueDesc.add("Datos de misión no disponibles");
                            }
                        }
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
            try {
                if (companion.getCompanionBrain() != null && companion.getCompanionBrain().getTaskManager() != null) {
                    var current = companion.getCompanionBrain().getTaskManager().getCurrentTask();
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
            } catch (Exception e) {
                taskDesc = "Datos de misión no disponibles";
            }

            String home = companion.getHomePos() != null ? companion.getHomePos().toShortString() : "No establecida";
            String dim = companion.level() != null ? companion.level().dimension().location().toString() : "minecraft:overworld";
            int px = companion.getBlockX();
            int py = companion.getBlockY();
            int pz = companion.getBlockZ();

            CompanionSettings settings = companion.getSettings();

            S2C_SyncCompanionDataPacket packet = new S2C_SyncCompanionDataPacket(
                    companion.getUUID(),
                    companion.getId(),
                    companion.getCustomCompanionName(),
                    companion.getCurrentState() != null ? companion.getCurrentState().name() : "IDLE",
                    companion.getPreferredIdleBehavior() != null ? companion.getPreferredIdleBehavior().name() : "FOLLOW",
                    companion.getHealth(),
                    companion.getMaxHealth(),
                    companion.getCompanionLevel(),
                    companion.getMiningXP(),
                    companion.getCombatXP(),
                    companion.getGatheringXP(),
                    companion.getBuildingXP(),
                    companion.getExplorationXP(),
                    companion.getBlocksMined(),
                    companion.getMobsKilled(),
                    companion.getTasksCompleted(),
                    taskDesc,
                    taskProg,
                    taskType,
                    taskStage,
                    taskSubstate,
                    taskFailure,
                    lastProg,
                    queueDesc,
                    home,
                    dim,
                    px, py, pz,
                    settings.isAutoFollow(),
                    settings.isAutoEquip(),
                    settings.isAutoEat(),
                    settings.isAutoDeliver(),
                    settings.isAutoReturn(),
                    settings.isAllowBlockBreaking(),
                    settings.isAllowBlockPlacement(),
                    settings.isAllowCombat(),
                    settings.isEnableChunkLoading()
            );

            companion.populateExtendedSyncData(packet);

            CompanionNetworking.CHANNEL.sendTo(packet, player.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        } catch (Exception e) {
            // Never crash menu on data sync error
        }
    }

    public CompanionEntity getCompanion() {
        return companion;
    }

    public UUID getCompanionUUID() {
        return companionUUID;
    }

    public int getCurrentTab() {
        return currentTab;
    }

    public boolean canModifyCompanionInventory(Player player) {
        if (player == null) return false;
        if (companion == null || !companion.isAlive() || companion.isRemoved()) return false;
        if (player.level() != companion.level()) return false;
        return player.distanceToSqr(companion) <= 16.0; // 4 blocks max
    }

    @Override
    public boolean stillValid(Player player) {
        if (player == null || !player.isAlive()) return false;
        // The GUI belongs to the owner, regardless of distance or dimension
        if (companion != null) {
            return companion.isAlive() && !companion.isRemoved() && player.getUUID().equals(companion.getOwnerUUID());
        }
        return true;
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        // Enforce remote read-only access: reject any modifications to companion slots if remote
        if (!canModifyCompanionInventory(player)) {
            if (slotId >= 0 && slotId < CompanionInventoryHandler.TOTAL_SLOTS) {
                return; // Disallow any click/drag/pickup on companion slots
            }
            if (clickType == net.minecraft.world.inventory.ClickType.QUICK_MOVE) {
                return; // Disallow shift-click into companion slots
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!canModifyCompanionInventory(player)) {
            return ItemStack.EMPTY;
        }

        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            int companionSlotsCount = CompanionInventoryHandler.TOTAL_SLOTS; // 42
            int totalSlotsCount = this.slots.size();                         // 42 + 36 = 78

            if (index < companionSlotsCount) {
                // Move from companion to player
                if (!this.moveItemStackTo(slotStack, companionSlotsCount, totalSlotsCount, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Move from player to companion main slots
                if (!this.moveItemStackTo(slotStack, 0, CompanionInventoryHandler.MAIN_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }
}
