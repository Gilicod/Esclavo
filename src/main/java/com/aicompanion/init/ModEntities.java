package com.aicompanion.init;

import com.aicompanion.AICompanionMod;
import com.aicompanion.entity.CompanionEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AICompanionMod.MOD_ID);

    public static final RegistryObject<EntityType<CompanionEntity>> COMPANION =
            ENTITIES.register("companion", () -> EntityType.Builder.of(CompanionEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(AICompanionMod.MOD_ID + ":companion"));
}
