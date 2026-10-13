package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.inventory.CompanionInventoryHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.List;

/**
 * Enterprise smelting engine for Esclavo.
 * Dynamically queries Minecraft RecipeManager for SMELTING recipes,
 * locates or automatically deploys a furnace, calculates fuel requirements
 * based on Forge burn times, and produces authentic smelted outputs.
 */
public class SmeltTask extends CompanionTask {
    private ResourceLocation inputItemId;
    private int amount;
    private int smeltedCount = 0;
    private BlockPos furnacePos = null;
    private int progressTicks = 0;
    private int repathCooldown = 0;
    private static final int TICKS_PER_SMELT = 20; // 1 second per item for companion efficiency

    public SmeltTask(CompanionEntity companion, ResourceLocation inputItemId, int amount) {
        super(companion);
        this.inputItemId = inputItemId;
        this.amount = Math.max(1, amount);
    }

    @Override
    public List<CompanionTask> checkPreconditions() {
        List<CompanionTask> prereqs = new java.util.ArrayList<>();
        CompanionInventoryHandler inv = companion.getInventoryHandler();

        // 1. Ensure furnace availability (nearby, remembered, in inventory, or 8x cobblestone to craft one)
        boolean hasFurnaceAvailable = inv.countItem(Items.FURNACE) > 0 || inv.countItem(Items.COBBLESTONE) >= 8;
        if (!hasFurnaceAvailable && companion.level() != null) {
            BlockPos center = companion.blockPosition();
            if (companion.level() instanceof net.minecraft.server.level.ServerLevel sl && companion.getMemory() != null) {
                if (companion.getMemory().findClosest(sl, center, Blocks.FURNACE, 64.0).isPresent()) {
                    hasFurnaceAvailable = true;
                }
            }
            if (!hasFurnaceAvailable) {
                for (BlockPos p : BlockPos.betweenClosed(center.offset(-8, -2, -8), center.offset(8, 2, 8))) {
                    if (companion.level().getBlockState(p).is(Blocks.FURNACE)) {
                        hasFurnaceAvailable = true;
                        break;
                    }
                }
            }
        }
        if (!hasFurnaceAvailable) {
            int neededCobble = Math.max(1, 8 - inv.countItem(Items.COBBLESTONE));
            prereqs.add(new MineTask(companion, new ResourceLocation("minecraft", "cobblestone"), neededCobble));
        }

        // 2. Ensure fuel availability (coal, charcoal, planks, or logs)
        if (!hasSufficientFuel()) {
            prereqs.add(new ChopTask(companion, "any", 2));
        }

        return prereqs;
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.SMELTING);
        this.smeltedCount = 0;
        this.progressTicks = 0;
        this.repathCooldown = 0;
        logEvent("Iniciando: Fundir " + amount + "x " + inputItemId.getPath());
    }

    @Override
    public void tick() {
        if (paused || complete) return;

        if (smeltedCount >= amount) {
            complete = true;
            companion.setCurrentState(CompanionState.IDLE);
            logEvent("Fundición completada: " + smeltedCount + "/" + amount + " " + inputItemId.getPath());
            return;
        }

        CompanionInventoryHandler inv = companion.getInventoryHandler();
        Item inputItem = BuiltInRegistries.ITEM.get(inputItemId);

        // Check if companion possesses raw iron or iron ore when looking for iron
        if (inv.countItem(inputItem) == 0) {
            if (inputItemId.getPath().contains("iron")) {
                if (inv.countItem(Items.RAW_IRON) > 0) {
                    inputItem = Items.RAW_IRON;
                    inputItemId = BuiltInRegistries.ITEM.getKey(Items.RAW_IRON);
                } else if (inv.countItem(Items.IRON_ORE) > 0) {
                    inputItem = Items.IRON_ORE;
                    inputItemId = BuiltInRegistries.ITEM.getKey(Items.IRON_ORE);
                } else if (inv.countItem(Items.DEEPSLATE_IRON_ORE) > 0) {
                    inputItem = Items.DEEPSLATE_IRON_ORE;
                    inputItemId = BuiltInRegistries.ITEM.getKey(Items.DEEPSLATE_IRON_ORE);
                }
            }
        }

        if (inv.countItem(inputItem) == 0 && smeltedCount == 0) {
            fail("No tengo " + inputItemId.getPath() + " en el inventario para fundir.");
            return;
        }

        // 1. Locate or deploy a furnace
        if (furnacePos == null || !companion.level().getBlockState(furnacePos).is(Blocks.FURNACE)) {
            furnacePos = findOrDeployFurnace();
            if (furnacePos == null) {
                fail("No se encontró ni se pudo colocar un horno para fundir.");
                return;
            }
        }

        // 2. Navigate to furnace
        double distSq = companion.blockPosition().distSqr(furnacePos);
        if (distSq > 9.0) {
            stage = "NAVIGATING";
            substate = "Aproximándose al horno...";
            companion.setCurrentState(CompanionState.NAVIGATING);
            if (--repathCooldown <= 0 || companion.getNavManager().isDone()) {
                repathCooldown = 15;
                companion.getNavManager().moveTo(furnacePos, 1.2);
            }
            return;
        }

        // 3. In front of furnace: Smelting phase
        companion.getNavManager().stop();
        companion.setCurrentState(CompanionState.SMELTING);
        stage = "SMELTING";
        substate = "Fundiendo " + inputItemId.getPath() + " (" + (smeltedCount + 1) + "/" + amount + ")";

        // Verify fuel availability
        if (!hasSufficientFuel()) {
            fail("No tengo combustible (carbón o madera) para alimentar el horno.");
            return;
        }

        progressTicks++;
        if (progressTicks >= TICKS_PER_SMELT) {
            progressTicks = 0;

            // Resolve recipe output
            ItemStack resultStack = resolveSmeltOutput(inputItem);
            if (resultStack.isEmpty()) {
                fail("Receta de fundición no encontrada para " + inputItemId);
                return;
            }

            // Consume 1 input item
            inv.removeItem(inputItem, 1);
            // Consume fractional fuel if needed
            consumeFuelForOneSmelt();

            // Deliver output to inventory
            inv.addItem(resultStack.copy());
            smeltedCount++;
            markProgress();

            if (companion.level() != null) {
                companion.level().playSound(null, furnacePos, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.5f, 1.0f);
            }

            if (smeltedCount >= amount || inv.countItem(inputItem) == 0) {
                complete = true;
                companion.setCurrentState(CompanionState.IDLE);
            }
        }
    }

    private ItemStack resolveSmeltOutput(Item input) {
        RecipeManager rm = companion.level().getRecipeManager();
        ItemStack inStack = new ItemStack(input);

        List<SmeltingRecipe> recipes = rm.getAllRecipesFor(RecipeType.SMELTING);
        for (SmeltingRecipe r : recipes) {
            if (!r.getIngredients().isEmpty() && r.getIngredients().get(0).test(inStack)) {
                return r.getResultItem(companion.level().registryAccess());
            }
        }

        // Fallbacks
        if (input == Items.RAW_IRON || input == Items.IRON_ORE || input == Items.DEEPSLATE_IRON_ORE) {
            return new ItemStack(Items.IRON_INGOT);
        }
        if (input == Items.RAW_COPPER || input == Items.COPPER_ORE) {
            return new ItemStack(Items.COPPER_INGOT);
        }
        if (input == Items.RAW_GOLD || input == Items.GOLD_ORE) {
            return new ItemStack(Items.GOLD_INGOT);
        }
        if (input == Items.COBBLESTONE) {
            return new ItemStack(Items.STONE);
        }
        if (input == Items.SAND) {
            return new ItemStack(Items.GLASS);
        }

        return ItemStack.EMPTY;
    }

    private boolean hasSufficientFuel() {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && ForgeHooks.getBurnTime(s, RecipeType.SMELTING) > 0) {
                return true;
            }
        }
        return false;
    }

    private void consumeFuelForOneSmelt() {
        // Every 8 smelts consumes 1 coal (or equivalent)
        if (smeltedCount % 8 == 0) {
            CompanionInventoryHandler inv = companion.getInventoryHandler();
            // 1. First priority: Real mineral fuel (Coal, Charcoal, Coal Block)
            for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty() && (s.is(Items.COAL) || s.is(Items.CHARCOAL) || s.is(Items.COAL_BLOCK))) {
                    s.shrink(1);
                    return;
                }
            }
            // 2. Second priority: Other dedicated fuels (Blaze Rod, Lava Bucket)
            for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty() && (s.is(Items.BLAZE_ROD) || s.is(Items.LAVA_BUCKET))) {
                    if (s.is(Items.LAVA_BUCKET)) {
                        inv.setStackInSlot(i, new ItemStack(Items.BUCKET));
                    } else {
                        s.shrink(1);
                    }
                    return;
                }
            }
            // 3. Third priority: Wood planks or logs, but NEVER sticks or crafted tools if possible!
            for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty() && ForgeHooks.getBurnTime(s, RecipeType.SMELTING) > 0) {
                    if (!s.is(Items.STICK) && !(s.getItem() instanceof net.minecraft.world.item.TieredItem)) {
                        s.shrink(1);
                        return;
                    }
                }
            }
            // 4. Absolute fallback
            for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty() && ForgeHooks.getBurnTime(s, RecipeType.SMELTING) > 0) {
                    s.shrink(1);
                    return;
                }
            }
        }
    }

    private BlockPos findOrDeployFurnace() {
        BlockPos center = companion.blockPosition();

        // 0. Spatial POI Episodic Memory Query (Pilar 5)
        if (companion.level() instanceof net.minecraft.server.level.ServerLevel sl && companion.getMemory() != null) {
            var closestFurnace = companion.getMemory().findClosest(sl, center, Blocks.FURNACE, 64.0);
            if (closestFurnace.isPresent()) {
                com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-MEMORY] SmeltTask: Reusing remembered furnace at {}!", closestFurnace.get());
                return closestFurnace.get();
            }
        }

        // 1. Search for existing furnace within 10 blocks
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-8, -2, -8), center.offset(8, 2, 8))) {
            if (companion.level().getBlockState(p).is(Blocks.FURNACE)) {
                BlockPos found = p.immutable();
                if (companion.getMemory() != null) {
                    companion.getMemory().rememberPOI(found, Blocks.FURNACE, companion.level().getGameTime());
                }
                return found;
            }
        }

        // 2. Auto-craft furnace from 8 cobblestone if none in inventory
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        if (inv.countItem(Items.FURNACE) == 0 && inv.countItem(Items.COBBLESTONE) >= 8) {
            inv.removeItem(Items.COBBLESTONE, 8);
            inv.addItem(new ItemStack(Items.FURNACE, 1));
            logEvent("Horno fabricado automáticamente con 8x adoquín.");
        }

        // 3. Deploy furnace from inventory if companion carries one
        if (inv.countItem(Items.FURNACE) > 0) {
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
                if (p.equals(center) || p.equals(center.above())) continue;
                var stateAt = companion.level().getBlockState(p);
                if ((stateAt.isAir() || stateAt.canBeReplaced()) && companion.level().getBlockState(p.below()).isSolid()) {
                    companion.level().setBlock(p, Blocks.FURNACE.defaultBlockState(), 3);
                    inv.removeItem(Items.FURNACE, 1);
                    BlockPos deployed = p.immutable();
                    if (companion.getMemory() != null) {
                        companion.getMemory().rememberPOI(deployed, Blocks.FURNACE, companion.level().getGameTime());
                    }
                    return deployed;
                }
            }
        }

        return null;
    }

    @Override
    public void onStuckTargetUnreachable() {
        this.furnacePos = null;
        this.repathCooldown = 0;
    }

    @Override
    public String getDescription() { return "Fundir " + inputItemId.getPath() + " (" + smeltedCount + "/" + amount + ")"; }
    @Override
    public String getProgress() { return complete ? "Terminado" : (smeltedCount + "/" + amount); }
    @Override
    public String getTaskType() { return "SMELT"; }
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("InputItem", inputItemId.toString());
        tag.putInt("Amount", amount);
        tag.putInt("SmeltedCount", smeltedCount);
        if (furnacePos != null) tag.put("FurnacePos", NbtUtils.writeBlockPos(furnacePos));
        return tag;
    }
    @Override
    public void load(CompoundTag tag) {
        this.inputItemId = new ResourceLocation(tag.getString("InputItem"));
        this.amount = tag.getInt("Amount");
        this.smeltedCount = tag.getInt("SmeltedCount");
        if (tag.contains("FurnacePos")) this.furnacePos = NbtUtils.readBlockPos(tag.getCompound("FurnacePos"));
    }
}
