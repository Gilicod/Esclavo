package com.aicompanion.ai.dimension;

import com.aicompanion.AICompanionMod;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.util.SafeSpawnFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

import java.util.Optional;
import java.util.function.Function;

/**
 * Enterprise Autonomous Dimension Teleporter for Minecraft Forge 1.20.1.
 * Safely computes interdimensional portal coordinates (1:8 scale for Nether/Overworld),
 * locates existing portals or synthesizes new portal structures via PortalForcer,
 * ensures target chunk residency, and guarantees valid PortalInfo and entity registration.
 */
public class CompanionNetherTeleporter implements ITeleporter {
    private final BlockPos departurePortalPos;
    private final ResourceKey<Level> targetDim;

    public CompanionNetherTeleporter(BlockPos departurePortalPos, ResourceKey<Level> targetDim) {
        this.departurePortalPos = departurePortalPos;
        this.targetDim = targetDim;
    }

    @Override
    public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld, Function<ServerLevel, PortalInfo> defaultPortalInfo) {
        AICompanionMod.LOGGER.info("[ESCLAVO-DIM] Calculando punto de entrada para entidad {} hacia dimensión {}", entity.getId(), destWorld.dimension().location());

        // Handle The End dimension
        if (destWorld.dimension().equals(Level.END)) {
            ServerLevel.makeObsidianPlatform(destWorld);
            BlockPos spawnPos = ServerLevel.END_SPAWN_POINT;
            Vec3 pos = new Vec3(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            return new PortalInfo(pos, Vec3.ZERO, 90.0F, 0.0F);
        }

        // Handle Nether / Overworld portal logic
        double scale = DimensionType.getTeleportationScale(entity.level().dimensionType(), destWorld.dimensionType());
        BlockPos originPos = departurePortalPos != null ? departurePortalPos : entity.blockPosition();

        double targetX = originPos.getX() * scale;
        double targetZ = originPos.getZ() * scale;
        double targetY = originPos.getY();

        if (destWorld.dimension().equals(Level.NETHER)) {
            // Keep within Nether bounds (avoid bedrock floor and ceiling)
            targetY = Math.max(32, Math.min(targetY, 115));
        } else {
            targetY = Math.max(destWorld.getMinBuildHeight() + 5, Math.min(targetY, destWorld.getMaxBuildHeight() - 15));
        }

        WorldBorder border = destWorld.getWorldBorder();
        BlockPos targetPos = border.clampToBounds(targetX, targetY, targetZ);

        // Preload destination chunk to allow portal discovery and creation
        destWorld.getChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4);

        boolean toNether = destWorld.dimension().equals(Level.NETHER);

        // 1. Search for existing portal in destination world
        Optional<net.minecraft.BlockUtil.FoundRectangle> existing = destWorld.getPortalForcer().findPortalAround(targetPos, toNether, border);

        net.minecraft.BlockUtil.FoundRectangle portalRect = null;
        if (existing.isPresent()) {
            portalRect = existing.get();
            AICompanionMod.LOGGER.info("[ESCLAVO-DIM] Portal existente encontrado en destino en {}", portalRect.minCorner);
        } else {
            // 2. No existing portal found; physically generate one using PortalForcer
            AICompanionMod.LOGGER.info("[ESCLAVO-DIM] No se encontró portal en destino. Creando nuevo portal cerca de {}", targetPos);
            Optional<net.minecraft.BlockUtil.FoundRectangle> created = destWorld.getPortalForcer().createPortal(targetPos, Direction.Axis.X);
            if (created.isPresent()) {
                portalRect = created.get();
                AICompanionMod.LOGGER.info("[ESCLAVO-DIM] Portal creado con éxito en destino en {}", portalRect.minCorner);
            } else {
                AICompanionMod.LOGGER.warn("[ESCLAVO-DIM] PortalForcer.createPortal no pudo generar portal en {}", targetPos);
            }
        }

        if (portalRect != null) {
            BlockPos min = portalRect.minCorner;
            Direction.Axis axis = Direction.Axis.X;
            BlockState state = destWorld.getBlockState(min);
            if (state.is(Blocks.NETHER_PORTAL) && state.hasProperty(NetherPortalBlock.AXIS)) {
                axis = state.getValue(NetherPortalBlock.AXIS);
            } else {
                BlockState aboveState = destWorld.getBlockState(min.above());
                if (aboveState.is(Blocks.NETHER_PORTAL) && aboveState.hasProperty(NetherPortalBlock.AXIS)) {
                    axis = aboveState.getValue(NetherPortalBlock.AXIS);
                }
            }
            double spawnX = min.getX() + (axis == Direction.Axis.X ? (portalRect.axis1Size / 2.0) : 0.5);
            double spawnY = min.getY();
            double spawnZ = min.getZ() + (axis == Direction.Axis.Z ? (portalRect.axis1Size / 2.0) : 0.5);
            Vec3 spawnVec = new Vec3(spawnX, spawnY, spawnZ);
            return new PortalInfo(spawnVec, Vec3.ZERO, entity.getYRot(), entity.getXRot());
        }

        // 3. Fallback safe position if portal generation failed
        SafeSpawnFinder.SafeSpawnResult safeRes = SafeSpawnFinder.findSafeSpawnResult(destWorld, targetPos, entity);
        Vec3 fallbackVec = safeRes.isSuccess() ? safeRes.getPosition() : new Vec3(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        AICompanionMod.LOGGER.warn("[ESCLAVO-DIM] Usando posición de emergencia segura en destino: {}", fallbackVec);
        return new PortalInfo(fallbackVec, Vec3.ZERO, entity.getYRot(), entity.getXRot());
    }

    @Override
    public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
        Entity newEntity = repositionEntity.apply(false);
        if (newEntity instanceof CompanionEntity newCompanion) {
            AICompanionMod.LOGGER.info("[ESCLAVO-DIM] Entidad CompanionEntity colocada y registrada en destino {}: id={}, pos={}",
                    destWorld.dimension().location(), newCompanion.getId(), newCompanion.blockPosition());

            // Halt any residual movement or pathfinding from departure dimension
            newCompanion.setDeltaMovement(Vec3.ZERO);
            newCompanion.getNavManager().stop();
            newCompanion.getNavigation().stop();
            newCompanion.setSprinting(false);

            // Ensure safe solid floor apron around arrival portal (prevents stepping into lava lakes)
            ensureSafePortalApron(destWorld, newCompanion.blockPosition());

            com.aicompanion.data.CompanionManager.registerCompanion(newCompanion);
            com.aicompanion.ai.chunk.CompanionChunkManager.updateChunkTickets(newCompanion);
            BlockPos arrivalPos = newCompanion.blockPosition();
            newCompanion.getMemory().getWorldModel().rememberLocation("return_portal", arrivalPos);
            newCompanion.getMemory().getWorldModel().rememberPOI(arrivalPos, Blocks.NETHER_PORTAL, destWorld.getGameTime());
        }
        return newEntity;
    }

    /**
     * Inspects a 5x5 horizontal footprint around the portal and ensures solid, non-lava floor
     * exists beneath the companion's feet, and extinguishes fires/lava hazards at foot and eye levels.
     * Prevents the companion from stepping out of the portal frame into open lava or falling to death.
     */
    private void ensureSafePortalApron(ServerLevel world, BlockPos center) {
        if (world == null || center == null) return;
        int floorY = center.getY() - 1;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos floorPos = new BlockPos(center.getX() + dx, floorY, center.getZ() + dz);
                BlockState floorState = world.getBlockState(floorPos);

                // If floor is air, lava, fire, or liquid: solidify with blast-resistant cobblestone
                if (floorState.isAir() || !floorState.getFluidState().isEmpty() || floorState.is(Blocks.LAVA) || floorState.is(Blocks.FIRE)) {
                    world.setBlockAndUpdate(floorPos, Blocks.COBBLESTONE.defaultBlockState());
                }

                // Clear any dangerous fire or lava occupying the standing space
                BlockPos footPos = floorPos.above();
                BlockState footState = world.getBlockState(footPos);
                if (footState.is(Blocks.FIRE) || footState.is(Blocks.SOUL_FIRE) || !footState.getFluidState().isEmpty()) {
                    if (!footState.is(Blocks.NETHER_PORTAL)) {
                        world.setBlockAndUpdate(footPos, Blocks.AIR.defaultBlockState());
                    }
                }

                BlockPos headPos = footPos.above();
                BlockState headState = world.getBlockState(headPos);
                if (headState.is(Blocks.FIRE) || headState.is(Blocks.SOUL_FIRE) || !headState.getFluidState().isEmpty()) {
                    if (!headState.is(Blocks.NETHER_PORTAL)) {
                        world.setBlockAndUpdate(headPos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }
}
