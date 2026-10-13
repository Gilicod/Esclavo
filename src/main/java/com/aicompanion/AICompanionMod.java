package com.aicompanion;

import com.aicompanion.command.CompanionCommand;
import com.aicompanion.debug.EsclavoClientTrace;
import com.aicompanion.debug.EsclavoTrace;
import com.aicompanion.debug.TracingLogger;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.init.ModEntities;
import com.aicompanion.init.ModItems;
import com.aicompanion.init.ModMenus;
import com.aicompanion.network.CompanionNetworking;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.Logger;

@Mod("aicompanion")
public class AICompanionMod {
   public static final String MOD_ID = "aicompanion";
   public static final Logger LOGGER = TracingLogger.create("aicompanion");

   public AICompanionMod() {
      IEventBus var1 = FMLJavaModLoadingContext.get().getModEventBus();
      ModEntities.ENTITIES.register(var1);
      ModItems.ITEMS.register(var1);
      ModMenus.MENUS.register(var1);
      ModLoadingContext.get().registerConfig(Type.COMMON, AICompanionConfig.COMMON_SPEC);
      ModLoadingContext.get().registerConfig(Type.CLIENT, AICompanionConfig.CLIENT_SPEC);
      var1.addListener(this::commonSetup);
      var1.addListener(this::createEntityAttributes);
      var1.addListener(this::addCreative);
      MinecraftForge.EVENT_BUS.register(this);

      try {
         MinecraftForge.EVENT_BUS.addListener(EsclavoTrace::onCommand);

         boolean var2;
         try {
            Class.forName("net.minecraft.client.Minecraft", false, AICompanionMod.class.getClassLoader());
            var2 = true;
         } catch (Throwable var4) {
            var2 = false;
         }

         if (var2) {
            EsclavoClientTrace.register();
         }

         EsclavoTrace.log("INICIO", "Mod Esclavo " + net.minecraftforge.fml.ModList.get().getModContainerById("aicompanion")
            .map(c -> c.getModInfo().getVersion().toString()).orElse("?") + " (registro de diagnostico activo). cliente=" + var2);
      } catch (Throwable var5) {
         LOGGER.warn("No se pudo activar el registro de diagnostico del Esclavo: {}", var5.toString());
      }

      LOGGER.info("AI Companion mod initialized successfully.");
   }

   private void commonSetup(FMLCommonSetupEvent var1) {
      var1.enqueueWork(() -> {
         CompanionNetworking.register();

         try {
            ForgeChunkManager.setForcedChunkLoadingCallback("aicompanion", (var0, var1xx) -> {});
         } catch (Exception var1x) {
         }

         try {
            java.io.File schematicsDir = new java.io.File("config/esclavo/schematics");
            com.aicompanion.ai.building.SchematicImporter.loadSchematicsDirectory(schematicsDir);
         } catch (Throwable t) {
            LOGGER.warn("Could not load schematics directory: {}", t.getMessage());
         }

         LOGGER.info("AI Companion networking and chunk loading callback registered.");
      });
   }

   private void createEntityAttributes(EntityAttributeCreationEvent var1) {
      var1.put((EntityType)ModEntities.COMPANION.get(), CompanionEntity.createAttributes().build());
   }

   private void addCreative(BuildCreativeModeTabContentsEvent var1) {
      if (var1.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
         var1.accept(ModItems.COMPANION_WHISTLE);
      }
   }

   @SubscribeEvent
   public void onRegisterCommands(RegisterCommandsEvent var1) {
      CompanionCommand.register(var1.getDispatcher());
   }

   @SubscribeEvent
   public void onLevelTick(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
      if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && !event.level.isClientSide()) {
         if (event.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // Tick cooperative missions every 20 ticks (1 second)
            if (serverLevel.getGameTime() % 20L == 0L) {
               com.aicompanion.ai.coordination.CompanionCoordinator.tickCooperativeMissions(serverLevel);
            }
         }
      }
   }
}
