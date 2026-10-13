package com.aicompanion.ai.order;

import com.aicompanion.ai.parser.ItemSearchRegistry;
import net.minecraft.resources.ResourceLocation;

public class OrderDraft {
    public enum ActionType {
        NONE,
        MINE,
        CHOP,
        HUNT,
        GATHER,
        OBTAIN,
        GOTO,
        BUILD,
        FOLLOW,
        STAY,
        DEPOSIT
    }

    public enum DestinationType {
        NONE,
        OWNER,       // "y tráemelos"
        CHEST,       // "y guardar en baúl"
        HOME,        // "y volver a base"
        FOLLOW,      // "y seguirme"
        INVENTORY    // "y en inventario"
    }

    private ActionType action = ActionType.NONE;
    private String target = "";
    private ResourceLocation targetId = null;
    private int amount = 0;
    private DestinationType destination = DestinationType.NONE;
    private OrderSubmitMode submitMode = OrderSubmitMode.REPLACE_CURRENT;

    public OrderDraft() {}

    public void clear() {
        this.action = ActionType.NONE;
        this.target = "";
        this.targetId = null;
        this.amount = 0;
        this.destination = DestinationType.NONE;
        this.submitMode = OrderSubmitMode.REPLACE_CURRENT;
    }

    public boolean isEmpty() {
        return action == ActionType.NONE && target.isEmpty() && amount == 0;
    }

    public ActionType getAction() { return action; }
    public void setAction(ActionType action) { this.action = action; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public ResourceLocation getTargetId() { return targetId; }
    public void setTargetId(ResourceLocation targetId) { this.targetId = targetId; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public DestinationType getDestination() { return destination; }
    public void setDestination(DestinationType destination) { this.destination = destination; }

    public OrderSubmitMode getSubmitMode() { return submitMode; }
    public void setSubmitMode(OrderSubmitMode submitMode) { this.submitMode = submitMode; }

    /**
     * Builds a natural, grammatically clean Spanish command.
     */
    public String toNaturalString() {
        if (action == ActionType.NONE && target.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        switch (action) {
            case MINE -> {
                sb.append("Mina ");
                if (amount > 0) sb.append(amount).append(" ");
                sb.append(resolveReadableTarget());
            }
            case CHOP -> {
                sb.append("Tala ");
                if (amount > 0) sb.append(amount).append(" de ");
                sb.append(resolveReadableTarget());
            }
            case HUNT -> {
                sb.append("Caza ");
                if (amount > 0) sb.append(amount).append(" ");
                sb.append(resolveReadableTarget());
            }
            case GATHER -> {
                sb.append("Recoge ");
                if (amount > 0) sb.append(amount).append(" de ");
                sb.append(resolveReadableTarget());
            }
            case OBTAIN -> {
                sb.append("Consigue ");
                if (amount > 0) sb.append(amount).append(" de ");
                sb.append(resolveReadableTarget());
            }
            case GOTO -> {
                sb.append("Ve a ").append(target.isEmpty() ? "base" : target);
            }
            case BUILD -> {
                sb.append("Construye ").append(target.isEmpty() ? "casa" : target);
            }
            case FOLLOW -> sb.append("Sígueme");
            case STAY -> sb.append("Espera aquí");
            case DEPOSIT -> sb.append("Guarda objetos en baúl");
            default -> {
                if (amount > 0) sb.append(amount).append(" ");
                sb.append(target);
            }
        }

        switch (destination) {
            case OWNER -> sb.append(" y tráemelos");
            case CHEST -> sb.append(" y guardar en baúl");
            case HOME -> sb.append(" y volver a base");
            case FOLLOW -> sb.append(" y seguirme");
            case INVENTORY -> sb.append(" y conservar en inventario");
            case NONE -> {}
        }

        return sb.toString().trim();
    }

    private String resolveReadableTarget() {
        if (targetId != null) {
            return ItemSearchRegistry.getDisplayName(targetId);
        }
        if (!target.isEmpty()) {
            return target;
        }
        return "recursos";
    }
}
