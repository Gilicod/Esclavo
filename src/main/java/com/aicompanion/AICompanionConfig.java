package com.aicompanion;

import net.minecraftforge.common.ForgeConfigSpec;

public class AICompanionConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder commonBuilder = new ForgeConfigSpec.Builder();
        COMMON = new Common(commonBuilder);
        COMMON_SPEC = commonBuilder.build();

        ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
    }

    public static class Client {
        public final ForgeConfigSpec.BooleanValue showHudOverlay;
        public final ForgeConfigSpec.BooleanValue showDetailedTooltips;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.push("Interface");
            showHudOverlay = builder.comment("Show Esclavo HUD overlay on screen").define("showHudOverlay", true);
            showDetailedTooltips = builder.comment("Show detailed tooltips in Esclavo GUI").define("showDetailedTooltips", true);
            builder.pop();
        }
    }

    public static class Common {
        public final ForgeConfigSpec.IntValue maxCompanionsPerPlayer;
        public final ForgeConfigSpec.BooleanValue enableChunkLoading;
        public final ForgeConfigSpec.IntValue chunkLoadingRadius;
        public final ForgeConfigSpec.BooleanValue autoEat;
        public final ForgeConfigSpec.BooleanValue autoEquip;
        public final ForgeConfigSpec.BooleanValue notifyAutoEquip;
        public final ForgeConfigSpec.BooleanValue autoDeliver;
        public final ForgeConfigSpec.BooleanValue returnOnMissionComplete;
        public final ForgeConfigSpec.BooleanValue allowBlockBreaking;
        public final ForgeConfigSpec.BooleanValue allowBlockPlacement;
        public final ForgeConfigSpec.BooleanValue allowCombat;
        public final ForgeConfigSpec.BooleanValue allowDimensionTravel;
        public final ForgeConfigSpec.BooleanValue allowPlayerCarrying;
        public final ForgeConfigSpec.BooleanValue protectPlayerBuilds;
        public final ForgeConfigSpec.IntValue maxSearchRadius;
        public final ForgeConfigSpec.IntValue maxTaskQueueSize;
        public final ForgeConfigSpec.IntValue stuckTimeout;
        public final ForgeConfigSpec.BooleanValue debugLogging;
        public final ForgeConfigSpec.BooleanValue allowAutoDiscard;
        public final ForgeConfigSpec.BooleanValue autoOrganizeInventory;
        public final ForgeConfigSpec.ConfigValue<String> companionDeathMode;

        // Death and Respawn configuration
        public final ForgeConfigSpec.BooleanValue autoRespawn;
        public final ForgeConfigSpec.IntValue respawnSeconds;
        public final ForgeConfigSpec.ConfigValue<String> respawnLocationMode;
        public final ForgeConfigSpec.BooleanValue keepInventoryOnDeath;
        public final ForgeConfigSpec.BooleanValue showRespawnCountdown;
        public final ForgeConfigSpec.BooleanValue showDeathNotification;
        public final ForgeConfigSpec.BooleanValue preserveInterruptedMission;
        public final ForgeConfigSpec.BooleanValue autoResumeMissionOnRespawn;
        public final ForgeConfigSpec.BooleanValue allowDownedStateBeforeDeath;
        public final ForgeConfigSpec.IntValue downedTimeoutSeconds;

        // Inventory Management configuration
        public final ForgeConfigSpec.ConfigValue<String> inventoryManagementMode;
        public final ForgeConfigSpec.BooleanValue autoCompactInventory;
        public final ForgeConfigSpec.BooleanValue autoOrganizeOnPickup;
        public final ForgeConfigSpec.IntValue criticalCapacityThreshold;
        public final ForgeConfigSpec.IntValue desiredFreeSlots;
        public final ForgeConfigSpec.BooleanValue allowDepositSurplus;
        public final ForgeConfigSpec.BooleanValue allowCraftEmergencyChest;
        public final ForgeConfigSpec.BooleanValue allowDiscardCommonBlocks;
        public final ForgeConfigSpec.BooleanValue allowDiscardMobLoot;
        public final ForgeConfigSpec.BooleanValue allowDiscardBones;
        public final ForgeConfigSpec.BooleanValue allowDiscardString;
        public final ForgeConfigSpec.BooleanValue allowDiscardRottenFlesh;
        public final ForgeConfigSpec.IntValue reserveCommonBlocksCount;
        public final ForgeConfigSpec.BooleanValue showInventoryManagementAlerts;

        // AI configuration
        public final ForgeConfigSpec.BooleanValue aiEnabled;
        public final ForgeConfigSpec.ConfigValue<String> aiMode;
        public final ForgeConfigSpec.ConfigValue<String> aiProvider;
        public final ForgeConfigSpec.ConfigValue<String> aiEndpoint;
        public final ForgeConfigSpec.ConfigValue<String> aiModel;
        public final ForgeConfigSpec.IntValue aiTimeoutSeconds;
        public final ForgeConfigSpec.DoubleValue aiTemperature;
        public final ForgeConfigSpec.IntValue aiMaxPlanningTokens;
        public final ForgeConfigSpec.BooleanValue aiAllowFallback;
        public final ForgeConfigSpec.ConfigValue<String> aiAutonomyLevel;
        public final ForgeConfigSpec.ConfigValue<String> aiPersonality;
        public final ForgeConfigSpec.IntValue maxReplansPerTask;

        // Navigation configuration
        public final ForgeConfigSpec.ConfigValue<String> navigationMode;
        public final ForgeConfigSpec.IntValue maxPathNodesPerTick;
        public final ForgeConfigSpec.IntValue maxPlanningMillisecondsPerTick;
        public final ForgeConfigSpec.BooleanValue allowBridging;
        public final ForgeConfigSpec.BooleanValue allowTunneling;
        public final ForgeConfigSpec.DoubleValue walkSpeedModifier;
        public final ForgeConfigSpec.DoubleValue maxSpeedModifier;
        public final ForgeConfigSpec.BooleanValue allowSprinting;
        public final ForgeConfigSpec.IntValue maxRescueAttempts;
        public final ForgeConfigSpec.DoubleValue deliveryDistance;
        public final ForgeConfigSpec.DoubleValue pickupWalkDistance;
        public final ForgeConfigSpec.BooleanValue interactiveBuild;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("General");
            maxCompanionsPerPlayer = builder.comment("Max companions per player").defineInRange("maxCompanionsPerPlayer", 1, 1, 10);
            enableChunkLoading = builder.comment("Enable chunk loading for remote missions").define("enableChunkLoading", true);
            chunkLoadingRadius = builder.comment("Chunk loading radius").defineInRange("chunkLoadingRadius", 1, 1, 3);
            autoEat = builder.comment("Automatically eat food from inventory").define("autoEat", true);
            autoEquip = builder.comment("Automatically equip better armor/weapons").define("autoEquip", true);
            notifyAutoEquip = builder.comment("Notify player in chat when Esclavo auto-equips gear").define("notifyAutoEquip", false);
            autoDeliver = builder.comment("Deliver items directly to player on return").define("autoDeliver", false);
            returnOnMissionComplete = builder.comment("Return to owner when mission finishes").define("returnOnMissionComplete", true);
            allowBlockBreaking = builder.comment("Allow block breaking").define("allowBlockBreaking", true);
            allowBlockPlacement = builder.comment("Allow block placement").define("allowBlockPlacement", true);
            allowCombat = builder.comment("Allow combat").define("allowCombat", true);
            allowDimensionTravel = builder.comment("Allow dimension travel").define("allowDimensionTravel", true);
            allowPlayerCarrying = builder.comment("Allow carrying downed players").define("allowPlayerCarrying", true);
            protectPlayerBuilds = builder.comment("Do not break player-placed blocks").define("protectPlayerBuilds", true);
            maxSearchRadius = builder.comment("Max search radius for ores/resources").defineInRange("maxSearchRadius", 64, 16, 256);
            maxTaskQueueSize = builder.comment("Max tasks in companion queue").defineInRange("maxTaskQueueSize", 20, 1, 50);
            stuckTimeout = builder.comment("Seconds before declaring navigation stuck").defineInRange("stuckTimeout", 15, 5, 60);
            debugLogging = builder.comment("Enable debug logging").define("debugLogging", false);
            allowAutoDiscard = builder.comment("Allow Esclavo to automatically discard low value items when inventory is full (default false to prevent unwanted item loss)").define("allowAutoDiscard", false);
            autoOrganizeInventory = builder.comment("Automatically organize Esclavo backpack periodically and upon completing missions").define("autoOrganizeInventory", true);
            companionDeathMode = builder.comment("Death mode: DOWNED or RESPAWN").define("companionDeathMode", "RESPAWN");
            builder.pop();

            builder.push("DeathAndRespawn");
            autoRespawn = builder.comment("Automatically respawn Esclavo when he dies").define("autoRespawn", true);
            respawnSeconds = builder.comment("Seconds before Esclavo automatically respawns (default 60)").defineInRange("respawnSeconds", 60, 5, 600);
            respawnLocationMode = builder.comment("Where to respawn: NEAR_OWNER (default) or DEATH_LOCATION").define("respawnLocationMode", "NEAR_OWNER");
            keepInventoryOnDeath = builder.comment("Keep full inventory, equipment and XP on death (100% loss-free)").define("keepInventoryOnDeath", true);
            showRespawnCountdown = builder.comment("Show respawn countdown on HUD").define("showRespawnCountdown", true);
            showDeathNotification = builder.comment("Show single death notification in chat").define("showDeathNotification", true);
            preserveInterruptedMission = builder.comment("Preserve interrupted mission state upon death").define("preserveInterruptedMission", true);
            autoResumeMissionOnRespawn = builder.comment("Automatically resume interrupted mission after respawning").define("autoResumeMissionOnRespawn", false);
            allowDownedStateBeforeDeath = builder.comment("Allow downed/incapacitated state waiting for rescue before dying (default false for immediate 60s auto-respawn)").define("allowDownedStateBeforeDeath", false);
            downedTimeoutSeconds = builder.comment("Seconds in downed state before dying if rescue mode is enabled (default 300 = 5 min)").defineInRange("downedTimeoutSeconds", 300, 10, 600);
            builder.pop();

            builder.push("InventoryManagement");
            inventoryManagementMode = builder.comment("Autonomous inventory management mode: CONSERVATIVE, BALANCED (default), or AGGRESSIVE")
                    .define("inventoryManagementMode", "BALANCED");
            autoCompactInventory = builder.comment("Automatically compact compatible item stacks")
                    .define("autoCompactInventory", true);
            autoOrganizeOnPickup = builder.comment("Automatically organize backpack items on pickup after debounce")
                    .define("autoOrganizeOnPickup", true);
            criticalCapacityThreshold = builder.comment("Occupancy percentage threshold to trigger preemptive space clearing (default 85%)")
                    .defineInRange("criticalCapacityThreshold", 85, 50, 100);
            desiredFreeSlots = builder.comment("Target number of free slots to maintain for missions (default 2)")
                    .defineInRange("desiredFreeSlots", 2, 1, 10);
            allowDepositSurplus = builder.comment("Allow depositing surplus materials into nearby authorized chests")
                    .define("allowDepositSurplus", true);
            allowCraftEmergencyChest = builder.comment("Allow crafting and placing an emergency chest when storage is needed and viable")
                    .define("allowCraftEmergencyChest", true);
            allowDiscardCommonBlocks = builder.comment("Allow discarding common geology blocks (andesite, granite, dirt, excess cobblestone) in BALANCED/AGGRESSIVE modes")
                    .define("allowDiscardCommonBlocks", true);
            allowDiscardMobLoot = builder.comment("Allow discarding common mob loot (rotten flesh, spider eyes)")
                    .define("allowDiscardMobLoot", true);
            allowDiscardBones = builder.comment("Allow discarding bones when not required for active missions or crafting")
                    .define("allowDiscardBones", false);
            allowDiscardString = builder.comment("Allow discarding string when not required for active missions or crafting")
                    .define("allowDiscardString", false);
            allowDiscardRottenFlesh = builder.comment("Allow discarding rotten flesh")
                    .define("allowDiscardRottenFlesh", true);
            reserveCommonBlocksCount = builder.comment("Minimum count of useful building blocks (cobblestone, deepslate, netherrack) to reserve for bridges/scaffolding")
                    .defineInRange("reserveCommonBlocksCount", 32, 0, 128);
            showInventoryManagementAlerts = builder.comment("Show chat alerts when companion clears space or deposits surplus")
                    .define("showInventoryManagementAlerts", true);
            builder.pop();

            builder.push("AI");
            aiEnabled = builder.comment("Enable intelligent decision making").define("aiEnabled", true);
            aiMode = builder.comment("AI mode: LOCAL_ONLY, HYBRID, or LLM_ONLY").define("aiMode", "LOCAL_ONLY");
            aiProvider = builder.comment("AI provider: LOCAL, OPENAI, or OLLAMA").define("aiProvider", "LOCAL");
            aiEndpoint = builder.comment("LLM API endpoint").define("aiEndpoint", "http://localhost:11434/v1");
            aiModel = builder.comment("LLM model name").define("aiModel", "llama3:latest");
            aiTimeoutSeconds = builder.comment("LLM timeout in seconds").defineInRange("aiTimeoutSeconds", 10, 1, 60);
            aiTemperature = builder.comment("LLM temperature").defineInRange("aiTemperature", 0.3, 0.0, 1.0);
            aiMaxPlanningTokens = builder.comment("Max planning tokens").defineInRange("aiMaxPlanningTokens", 512, 64, 2048);
            aiAllowFallback = builder.comment("Fallback to local rules if LLM fails").define("aiAllowFallback", true);
            aiAutonomyLevel = builder.comment("Autonomy level: ASSISTANT, PROACTIVE, or SEMI_AUTONOMOUS").define("aiAutonomyLevel", "ASSISTANT");
            aiPersonality = builder.comment("Companion personality: LOYAL, HUMOROUS, or STOIC").define("aiPersonality", "LOYAL");
            maxReplansPerTask = builder.comment("Max replans per task before failure").defineInRange("maxReplansPerTask", 3, 1, 10);
            builder.pop();

            builder.push("Navigation");
            navigationMode = builder.comment("Navigation engine mode: AUTO, INTERNAL_BARITONE, ESCLAB, or VANILLA_SAFE")
                    .define("navigationMode", "AUTO");
            maxPathNodesPerTick = builder.comment("Max A* path nodes explored per tick budget")
                    .defineInRange("maxPathNodesPerTick", 1500, 200, 10000);
            maxPlanningMillisecondsPerTick = builder.comment("Max planning execution time in ms per tick budget")
                    .defineInRange("maxPlanningMillisecondsPerTick", 15, 2, 50);
            allowBridging = builder.comment("Allow placing blocks across gaps when bridging")
                    .define("allowBridging", true);
            allowTunneling = builder.comment("Allow digging 2-block high tunnels towards goals")
                    .define("allowTunneling", true);
            walkSpeedModifier = builder.comment("Normal walking speed modifier (1.0 = natural mob walk, close to a player walking)")
                    .defineInRange("walkSpeedModifier", 1.0, 0.5, 1.5);
            maxSpeedModifier = builder.comment("Hard cap for any movement speed modifier requested by tasks (prevents 'light speed' walking)")
                    .defineInRange("maxSpeedModifier", 1.35, 0.6, 2.0);
            allowSprinting = builder.comment("Allow the companion to sprint when moving (sprinting adds +30% speed like player Ctrl sprint)")
                    .define("allowSprinting", true);
            maxRescueAttempts = builder.comment("Progressive stuck-recovery attempts before giving up and informing the owner")
                    .defineInRange("maxRescueAttempts", 6, 1, 20);
            builder.pop();

            builder.push("Delivery");
            deliveryDistance = builder.comment("Max distance (blocks) to the owner to hand over items")
                    .defineInRange("deliveryDistance", 3.0, 1.5, 6.0);
            pickupWalkDistance = builder.comment("Distance at which a dropped item is collected (the companion walks to it first)")
                    .defineInRange("pickupWalkDistance", 1.6, 1.0, 3.0);
            builder.pop();

            builder.push("Building");
            interactiveBuild = builder.comment("Ask the missing details (material, size, floors, windows, door, location, facing) and show a summary before building a house")
                    .define("interactiveBuild", true);
            builder.pop();
        }
    }
}
