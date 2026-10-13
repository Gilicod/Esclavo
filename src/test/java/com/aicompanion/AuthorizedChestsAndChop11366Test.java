package com.aicompanion;

import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionSettings;
import com.aicompanion.network.packets.S2C_SyncCompanionDataPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AuthorizedChestsAndChop11366Test {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testAuthorizedChestEntryCreationAndLabels() {
        BlockPos pos = new BlockPos(120, 68, -240);
        ResourceLocation dim = new ResourceLocation("minecraft", "overworld");
        UUID ownerId = UUID.randomUUID();
        CompanionEntity.AuthorizedChestEntry entry = new CompanionEntity.AuthorizedChestEntry(
                3, pos, dim, ownerId, "Almacén de Comida", CompanionEntity.ChestCategory.FOOD
        );

        assertEquals(3, entry.getChestId());
        assertEquals(pos, entry.getPos());
        assertEquals(dim, entry.getDimension());
        assertEquals("Almacén de Comida", entry.getCustomName());
        assertEquals(CompanionEntity.ChestCategory.FOOD, entry.getCategory());
        assertEquals("[#3] Almacén de Comida (Comida)", entry.getFullLabel());

        entry.setCustomName("Despensa Central");
        entry.setCategory(entry.getCategory().next());
        assertEquals("Despensa Central", entry.getCustomName());
        assertEquals(CompanionEntity.ChestCategory.ORES, entry.getCategory());
    }

    @Test
    public void testChestCategoryMatchingLogic() {
        ItemStack cookedBeef = new ItemStack(Items.COOKED_BEEF, 8);
        ItemStack wheat = new ItemStack(Items.WHEAT, 16);
        ItemStack rawIron = new ItemStack(Items.RAW_IRON, 12);
        ItemStack diamond = new ItemStack(Items.DIAMOND, 4);
        ItemStack oakLog = new ItemStack(Items.OAK_LOG, 32);
        ItemStack cobblestone = new ItemStack(Items.COBBLESTONE, 64);
        ItemStack rottenFlesh = new ItemStack(Items.ROTTEN_FLESH, 10);
        ItemStack bow = new ItemStack(Items.BOW, 1);

        assertTrue(CompanionEntity.ChestCategory.FOOD.matchesItem(cookedBeef));
        assertTrue(CompanionEntity.ChestCategory.FOOD.matchesItem(wheat));
        assertFalse(CompanionEntity.ChestCategory.FOOD.matchesItem(rawIron));

        assertTrue(CompanionEntity.ChestCategory.ORES.matchesItem(rawIron));
        assertTrue(CompanionEntity.ChestCategory.ORES.matchesItem(diamond));
        assertFalse(CompanionEntity.ChestCategory.ORES.matchesItem(cookedBeef));

        assertTrue(CompanionEntity.ChestCategory.WOOD_BUILD.matchesItem(oakLog));
        assertTrue(CompanionEntity.ChestCategory.WOOD_BUILD.matchesItem(cobblestone));

        assertTrue(CompanionEntity.ChestCategory.LOOT_EQUIP.matchesItem(rottenFlesh));
        assertTrue(CompanionEntity.ChestCategory.LOOT_EQUIP.matchesItem(bow));

        assertTrue(CompanionEntity.ChestCategory.ALL.matchesItem(cookedBeef));
        assertTrue(CompanionEntity.ChestCategory.ALL.matchesItem(rawIron));
        assertTrue(CompanionEntity.ChestCategory.ALL.matchesItem(oakLog));
    }

    @Test
    public void testFoodAndAutoDepositPoliciesPersistenceAndCycling() {
        CompanionSettings settings = new CompanionSettings();
        assertEquals(CompanionSettings.FoodStoragePolicy.RESERVE_16_STORE_REST, settings.getFoodStoragePolicy());
        assertEquals(CompanionSettings.AutoDepositPolicy.ON_FULL_OR_IDLE, settings.getAutoDepositPolicy());

        settings.setFoodStoragePolicy(settings.getFoodStoragePolicy().next());
        assertEquals(CompanionSettings.FoodStoragePolicy.STORE_ALL_FOOD, settings.getFoodStoragePolicy());

        settings.setAutoDepositPolicy(settings.getAutoDepositPolicy().next());
        assertEquals(CompanionSettings.AutoDepositPolicy.ON_FULL_ONLY, settings.getAutoDepositPolicy());

        CompoundTag saved = settings.serializeNBT();
        CompanionSettings loaded = new CompanionSettings();
        loaded.deserializeNBT(saved);

        assertEquals(CompanionSettings.FoodStoragePolicy.STORE_ALL_FOOD, loaded.getFoodStoragePolicy());
        assertEquals(CompanionSettings.AutoDepositPolicy.ON_FULL_ONLY, loaded.getAutoDepositPolicy());
    }

    @Test
    public void testSyncCompanionDataPacketRoundTripWithAuthorizedChests() {
        S2C_SyncCompanionDataPacket packet = new S2C_SyncCompanionDataPacket(
                UUID.randomUUID(), 42, "Esclavo", "BUILDING", "FOLLOW",
                20.0f, 20.0f, 5,
                120, 80, 150, 200, 90,
                340, 25, 12,
                "Guardando en Baúl #1", "5 / 10", "DEPOSIT", "EN CAMINO", "Caminando al cofre", "",
                1234L,
                List.of("Talar 32 de Roble"),
                "100, 64, -200",
                "minecraft:overworld", 102, 64, -198,
                true, true, true, false, true, true, true, true, true
        );

        S2C_SyncCompanionDataPacket.AuthorizedChestDTO dto1 = new S2C_SyncCompanionDataPacket.AuthorizedChestDTO(
                1, "Baúl Principal", "ALL", "General",
                100, 64, -200, "minecraft:overworld",
                14, 27, true, "Roble x64, Hierro x32"
        );
        S2C_SyncCompanionDataPacket.AuthorizedChestDTO dto2 = new S2C_SyncCompanionDataPacket.AuthorizedChestDTO(
                2, "Despensa", "FOOD", "Comida",
                104, 64, -200, "minecraft:overworld",
                8, 27, false, "Filete x24, Pan x16"
        );

        packet.authorizedChests = List.of(dto1, dto2);
        packet.foodPolicyName = "RESERVE_16_STORE_REST";
        packet.foodPolicyDisplay = "Reservar 16 y guardar resto en Baúl";
        packet.autoDepositPolicyName = "ON_FULL_OR_IDLE";
        packet.autoDepositPolicyDisplay = "Al llenarse (≤3) o volver a base";
        packet.logisticsStatusBanner = "GUARDANDO AHORA → [#1] Baúl Principal (General)";
        packet.logisticsItemsBanner = "Llevando al baúl: Roble x64, Piedra x32";
        packet.activeBuildSummary = "casa_llanura_mediana — 45% (180/400) en 110, 64, -190";
        packet.rememberedDepositsCount = 6;
        packet.dangerZonesCount = 2;
        packet.aiAutonomyMode = "PROACTIVE";
        packet.aiPersonalityMode = "LOYAL";

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        S2C_SyncCompanionDataPacket.encode(packet, buf);

        S2C_SyncCompanionDataPacket decoded = S2C_SyncCompanionDataPacket.decode(buf);
        assertEquals(2, decoded.authorizedChests.size());
        assertEquals(1, decoded.authorizedChests.get(0).chestId);
        assertEquals("Baúl Principal", decoded.authorizedChests.get(0).name);
        assertTrue(decoded.authorizedChests.get(0).isPrimary);
        assertEquals(14, decoded.authorizedChests.get(0).occupiedSlots);
        assertEquals(27, decoded.authorizedChests.get(0).totalSlots);
        assertEquals(2, decoded.authorizedChests.get(1).chestId);
        assertEquals("Despensa", decoded.authorizedChests.get(1).name);
        assertEquals("Comida", decoded.authorizedChests.get(1).categoryDisplay);
        assertEquals("GUARDANDO AHORA → [#1] Baúl Principal (General)", decoded.logisticsStatusBanner);
        assertEquals("Llevando al baúl: Roble x64, Piedra x32", decoded.logisticsItemsBanner);
        assertEquals(6, decoded.rememberedDepositsCount);
        assertEquals(2, decoded.dangerZonesCount);
        assertEquals("PROACTIVE", decoded.aiAutonomyMode);
        assertEquals("LOYAL", decoded.aiPersonalityMode);
    }
}
