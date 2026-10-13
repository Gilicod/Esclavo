package com.aicompanion.util;

import com.aicompanion.entity.CompanionEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intelligent notification manager that eliminates chat spam by debouncing,
 * deduplicating, and rate-limiting repeated messages from Esclavo.
 */
public class CompanionNotificationManager {

    private static final Map<String, Long> lastNotificationTimes = new ConcurrentHashMap<>();

    /**
     * Sends a system notification to the companion's owner if the cooldown has elapsed.
     *
     * @param companion     The companion sending the message
     * @param categoryKey   Unique key for the type of notification (e.g. "auto_equip", "inv_full")
     * @param message       The component message to deliver
     * @param cooldownTicks Minimum ticks between messages of this category (20 ticks = 1 second)
     */
    public static void notifyOwner(CompanionEntity companion, String categoryKey, Component message, int cooldownTicks) {
        if (companion == null) return;
        Player owner = companion.getOwnerPlayer();
        if (owner == null) return;

        String key = owner.getUUID().toString() + ":" + categoryKey;
        long currentTime = companion.level().getGameTime();
        Long lastTime = lastNotificationTimes.get(key);

        if (lastTime == null || (currentTime - lastTime) >= cooldownTicks) {
            owner.sendSystemMessage(message);
            lastNotificationTimes.put(key, currentTime);
        }
    }

    /**
     * Sends an immediate one-shot notification (e.g. from manual user action in GUI)
     */
    public static void notifyImmediate(CompanionEntity companion, Component message) {
        if (companion == null) return;
        Player owner = companion.getOwnerPlayer();
        if (owner != null) {
            owner.sendSystemMessage(message);
        }
    }

    public static void clearHistory(CompanionEntity companion) {
        if (companion == null || companion.getOwnerUUID() == null) return;
        String prefix = companion.getOwnerUUID().toString() + ":";
        lastNotificationTimes.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
