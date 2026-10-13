package com.aicompanion.network.packets;

import com.aicompanion.ai.order.PlanPreviewRow;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2C_OrderPreviewPacket {
    private final int requestId;
    private final String normalizedOrder;
    private final String intentSummary;
    private final String goalSummary;
    private final List<PlanPreviewRow> rows;
    private final boolean isValid;
    private final String validationMessage;

    public S2C_OrderPreviewPacket(int requestId, String normalizedOrder, String intentSummary, String goalSummary,
                                  List<PlanPreviewRow> rows, boolean isValid, String validationMessage) {
        this.requestId = requestId;
        this.normalizedOrder = normalizedOrder != null ? normalizedOrder : "";
        this.intentSummary = intentSummary != null ? intentSummary : "";
        this.goalSummary = goalSummary != null ? goalSummary : "";
        this.rows = rows != null ? rows : new ArrayList<>();
        this.isValid = isValid;
        this.validationMessage = validationMessage != null ? validationMessage : "";
    }

    public static void encode(S2C_OrderPreviewPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.requestId);
        buf.writeUtf(msg.normalizedOrder, 512);
        buf.writeUtf(msg.intentSummary, 512);
        buf.writeUtf(msg.goalSummary, 512);
        buf.writeBoolean(msg.isValid);
        buf.writeUtf(msg.validationMessage, 512);

        buf.writeInt(msg.rows.size());
        for (PlanPreviewRow row : msg.rows) {
            buf.writeUtf(row.text(), 256);
            buf.writeEnum(row.type());
            buf.writeUtf(row.iconResource() != null ? row.iconResource() : "", 128);
            buf.writeUtf(row.status() != null ? row.status() : "", 64);
        }
    }

    public static S2C_OrderPreviewPacket decode(FriendlyByteBuf buf) {
        int reqId = buf.readInt();
        String normOrder = buf.readUtf(512);
        String intent = buf.readUtf(512);
        String goal = buf.readUtf(512);
        boolean valid = buf.readBoolean();
        String validMsg = buf.readUtf(512);

        int rowCount = buf.readInt();
        List<PlanPreviewRow> rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            String text = buf.readUtf(256);
            PlanPreviewRow.PlanRowType type = buf.readEnum(PlanPreviewRow.PlanRowType.class);
            String icon = buf.readUtf(128);
            String status = buf.readUtf(64);
            rows.add(new PlanPreviewRow(text, type, icon.isEmpty() ? null : icon, status));
        }

        return new S2C_OrderPreviewPacket(reqId, normOrder, intent, goal, rows, valid, validMsg);
    }

    public static void handle(S2C_OrderPreviewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (Minecraft.getInstance().screen instanceof com.aicompanion.client.gui.CompanionScreen screen) {
                    screen.handleOrderPreview(msg);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }

    public int getRequestId() { return requestId; }
    public String getNormalizedOrder() { return normalizedOrder; }
    public String getIntentSummary() { return intentSummary; }
    public String getGoalSummary() { return goalSummary; }
    public List<PlanPreviewRow> getRows() { return rows; }
    public boolean isValid() { return isValid; }
    public String getValidationMessage() { return validationMessage; }
}
