package com.aicompanion.ai.building;

import com.aicompanion.ai.planning.Goal;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.util.TerrainPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * World side of the interactive house dialogue: resolves "aquí"/"hacia mí", validates the site,
 * computes the real material list against the companion inventory and produces the final BUILD goal.
 * One session per companion (held by CompanionBrain). Expires after 5 minutes without answers.
 */
public class BuildDialogSession {

    public static final long TIMEOUT_TICKS = 6000;

    public record Reply(String message, boolean finished, Goal goal, boolean notUnderstood) {}

    private final CompanionEntity companion;
    private final BuildOrderDialog dialog = new BuildOrderDialog();

    public BuildDialogSession(CompanionEntity companion) {
        this.companion = companion;
    }

    public boolean isExpired(long gameTime) {
        return gameTime - dialog.getLastActivity() > TIMEOUT_TICKS;
    }

    public Reply start(String folded, ServerPlayer player) {
        dialog.touch(companion.level().getGameTime());
        dialog.absorb(folded, null);
        return advance(player, "Vamos a planear la casa. ");
    }

    public Reply handle(String folded, ServerPlayer player) {
        dialog.touch(companion.level().getGameTime());
        if (BuildOrderDialog.isCancel(folded)) {
            return new Reply("De acuerdo, cancelé la construcción de la casa.", true, null, false);
        }
        BuildOrderDialog.Step awaiting = dialog.getAwaiting();

        if (awaiting == BuildOrderDialog.Step.CONFIRM) {
            if (BuildOrderDialog.isModify(folded)) {
                BuildOrderDialog.Step reset = dialog.resetFromText(folded);
                boolean changed = dialog.absorb(folded, null);
                if (!changed && reset == null) {
                    return new Reply("¿Qué quieres modificar? (material, madera, tamaño, pisos, ventanas, puerta, ubicación u orientación)", false, null, false);
                }
                return advance(player, "");
            }
            if (BuildOrderDialog.isConfirm(folded)) {
                return tryConfirm(player);
            }
            if (dialog.absorb(folded, null)) {
                return advance(player, "Cambio aplicado. ");
            }
            return new Reply("No entendí. " + BuildOrderDialog.question(BuildOrderDialog.Step.CONFIRM), false, null, true);
        }

        if (!dialog.absorb(folded, awaiting)) {
            return new Reply("No entendí. " + BuildOrderDialog.question(awaiting) + " (o 'cancelar')", false, null, true);
        }
        return advance(player, "");
    }

    private Reply advance(ServerPlayer player, String prefix) {
        resolveWorldParameters(player);
        BuildOrderDialog.Step next = dialog.nextMissing();
        // Location/facing chosen but not resolvable yet (other dimension...): ask again
        if (next == BuildOrderDialog.Step.CONFIRM && dialog.getSpec().origin == null) next = BuildOrderDialog.Step.LOCATION;
        if (next == BuildOrderDialog.Step.CONFIRM && dialog.getSpec().facing == null) next = BuildOrderDialog.Step.FACING;
        dialog.setAwaiting(next);
        if (next != BuildOrderDialog.Step.CONFIRM) {
            return new Reply(prefix + BuildOrderDialog.question(next), false, null, false);
        }
        return new Reply(prefix + summary(), false, null, false);
    }

    private Reply tryConfirm(ServerPlayer player) {
        resolveWorldParameters(player);
        HouseSpec spec = dialog.getSpec();
        if (spec.origin == null) {
            dialog.setAwaiting(BuildOrderDialog.Step.LOCATION);
            return new Reply(BuildOrderDialog.question(BuildOrderDialog.Step.LOCATION), false, null, false);
        }
        Blueprint bp = ProceduralTemplates.createHouse(spec);
        int blocked = countProtected(bp, spec.origin);
        if (blocked > 0) {
            return new Reply("No puedo empezar: hay " + blocked + " bloques de construcciones en esa zona y no los voy a romper. "
                    + "Dime otra ubicación ('modificar ubicación') o 'cancelar'.", false, null, false);
        }
        Goal goal = Goal.build(spec.encode(), spec.origin);
        return new Reply("¡Entendido! Empiezo la casa en X=" + spec.origin.getX() + " Y=" + spec.origin.getY() + " Z=" + spec.origin.getZ()
                + ". Conseguiré los materiales que falten (talando, minando y fundiendo) antes de cada fase.", true, goal, false);
    }

    /** Converts "aquí", "donde estás", coordinates and "hacia mí" into real positions/directions. */
    private void resolveWorldParameters(ServerPlayer player) {
        HouseSpec spec = dialog.getSpec();
        if (!(companion.level() instanceof ServerLevel level)) return;
        boolean sameDim = player != null && player.level() == companion.level();
        int reachOut = Math.max(spec.widthOrDefault(), spec.depthOrDefault()) / 2 + 3;

        if (spec.origin == null) {
            switch (dialog.getLocationMode()) {
                case HERE_PLAYER -> {
                    if (sameDim) {
                        BlockPos front = player.blockPosition().relative(player.getDirection(), reachOut);
                        spec.origin = BuildSiteValidator.findSolidGround(level, front);
                    }
                }
                case HERE_COMPANION -> spec.origin = BuildSiteValidator.findSolidGround(level, companion.blockPosition());
                case COORDS -> {
                    int[] c = dialog.getCoords();
                    if (c != null) spec.origin = BuildSiteValidator.findSolidGround(level, new BlockPos(c[0], c[1], c[2]));
                }
                default -> { }
            }
        }
        if (spec.facing == null && spec.origin != null) {
            switch (dialog.getFacingMode()) {
                case TOWARDS_PLAYER -> {
                    if (sameDim) {
                        double dx = player.getX() - (spec.origin.getX() + 0.5);
                        double dz = player.getZ() - (spec.origin.getZ() + 0.5);
                        spec.facing = (dx * dx + dz * dz < 1.0) ? player.getDirection().getOpposite()
                                : Direction.getNearest(dx, 0, dz);
                        if (!spec.facing.getAxis().isHorizontal()) spec.facing = Direction.NORTH;
                    }
                }
                case PLAYER_LOOK -> {
                    if (sameDim) spec.facing = player.getDirection();
                }
                default -> { }
            }
        }
    }

    /** Human readable summary with the real material list and what is already in the inventory. */
    private String summary() {
        HouseSpec spec = dialog.getSpec();
        Blueprint bp = ProceduralTemplates.createHouse(spec);
        ProceduralTemplates.WoodType wood = ProceduralTemplates.woodOf(spec);
        StringBuilder sb = new StringBuilder();
        sb.append("Resumen de la casa:\n");
        sb.append("• Tamaño: ").append(spec.widthOrDefault()).append("x").append(spec.depthOrDefault())
                .append(", ").append(spec.floorsOrDefault()).append(spec.floorsOrDefault() == 1 ? " piso" : " pisos").append('\n');
        sb.append("• Paredes: ").append(materialName(spec.walls(), wood))
                .append(" | Techo: ").append(materialName(spec.roof(), wood))
                .append(" | Piso: ").append(materialName(spec.floor(), wood)).append('\n');
        sb.append("• Ventanas: ").append(spec.windowsOrDefault() ? "sí (vidrio)" : "no")
                .append(" | Puerta: ").append(spec.doorOrDefault() ? "sí" : "no")
                .append(spec.floorsOrDefault() > 1 ? " | Escaleras de mano entre pisos" : "").append('\n');
        if (spec.origin != null) {
            sb.append("• Ubicación: X=").append(spec.origin.getX()).append(" Y=").append(spec.origin.getY())
                    .append(" Z=").append(spec.origin.getZ()).append(", puerta mirando al ")
                    .append(HouseSpec.facingSpanish(spec.facingOrDefault())).append('\n');
        }

        // Real material list vs. inventory
        Map<Item, Integer> required = new LinkedHashMap<>();
        for (Blueprint.BlockEntry e : bp.getBlocks()) {
            Item it = e.state().getBlock().asItem();
            if (it != Items.AIR) required.merge(it, 1, Integer::sum);
        }
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        sb.append("• Materiales (necesito / tengo):");
        int missingKinds = 0;
        for (Map.Entry<Item, Integer> e : required.entrySet()) {
            int have = inv.countItem(e.getKey());
            sb.append("\n   - ").append(new ItemStack(e.getKey()).getHoverName().getString()).append(": ")
                    .append(e.getValue()).append(" / ").append(have);
            if (have < e.getValue()) missingKinds++;
        }
        sb.append('\n');
        MaterialAcquisitionPlanner.AcquisitionPlan plan = MaterialAcquisitionPlanner.planMaterials(companion, bp, null);
        if (missingKinds == 0) {
            sb.append("• Tengo todo lo necesario.\n");
        } else {
            sb.append("• Materias primas a conseguir: ").append(plan.getMissingReport().replace("Faltan: ", ""))
                    .append(". Lo conseguiré en modo supervivencia (tablas, palos, puertas y escaleras los fabrico yo).")
                    .append(" Cama, antorchas y vidrio son opcionales: si no los consigo, te aviso al final.\n");
        }
        if (spec.usesWood()) {
            sb.append("• Si no encuentro madera de ").append(wood.getDisplayName()).append(" usaré otra y te avisaré.\n");
        }
        if (spec.origin != null) {
            int blocked = countProtected(bp, spec.origin);
            if (blocked > 0) {
                sb.append("⚠ Hay ").append(blocked).append(" bloques de construcciones en esa zona: no los romperé. Elige otra ubicación.\n");
            }
        }
        sb.append(BuildOrderDialog.question(BuildOrderDialog.Step.CONFIRM));
        return sb.toString();
    }

    private static String materialName(HouseSpec.Material m, ProceduralTemplates.WoodType wood) {
        return m == HouseSpec.Material.WOOD ? "madera de " + wood.getDisplayName() : "piedra (adoquín)";
    }

    /** Number of non-clearable (player) blocks inside the house volume. */
    private int countProtected(Blueprint bp, BlockPos origin) {
        if (!(companion.level() instanceof ServerLevel level) || origin == null) return 0;
        int minX = 0, maxX = 0, minZ = 0, maxZ = 0, maxY = 0;
        for (Blueprint.BlockEntry e : bp.getBlocks()) {
            minX = Math.min(minX, e.offset().getX());
            maxX = Math.max(maxX, e.offset().getX());
            minZ = Math.min(minZ, e.offset().getZ());
            maxZ = Math.max(maxZ, e.offset().getZ());
            maxY = Math.max(maxY, e.offset().getY());
        }
        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = 1; y <= maxY; y++) {
                    BlockPos p = origin.offset(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (!TerrainPolicy.mayClearForBuilding(level, p, s, companion.getTemporaryBlocks())) count++;
                }
            }
        }
        return count;
    }
}
