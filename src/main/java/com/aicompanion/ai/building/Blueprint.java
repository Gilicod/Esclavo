package com.aicompanion.ai.building;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.List;

public class Blueprint {

    public enum BuildPhase {
        CLEARING,
        FOUNDATION,
        WALLS,
        ROOF,
        FURNISHING
    }

    public record BlockEntry(Vec3i offset, BlockState state, BuildPhase phase) {
        public BlockEntry(Vec3i offset, BlockState state) {
            this(offset, state, BuildPhase.WALLS);
        }
    }

    private final String name;
    private final List<BlockEntry> blocks;

    public Blueprint(String name, List<BlockEntry> blocks) {
        this.name = name;
        this.blocks = blocks;
    }

    public String getName() {
        return name;
    }

    public List<BlockEntry> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public Vec3i getDimensions() {
        if (blocks.isEmpty()) return new Vec3i(0, 0, 0);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockEntry entry : blocks) {
            Vec3i pos = entry.offset();
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }
        return new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    public Blueprint rotate(int degrees) {
        int rotDeg = ((degrees % 360) + 360) % 360;
        if (rotDeg == 0) return this;

        net.minecraft.world.level.block.Rotation rot = switch (rotDeg) {
            case 90 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
            case 180 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
            case 270 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
            default -> net.minecraft.world.level.block.Rotation.NONE;
        };

        if (rot == net.minecraft.world.level.block.Rotation.NONE) return this;

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;

        for (BlockEntry entry : blocks) {
            Vec3i pos = entry.offset();
            int x = pos.getX();
            int z = pos.getZ();

            int nx = switch (rot) {
                case CLOCKWISE_90 -> -z;
                case CLOCKWISE_180 -> -x;
                case COUNTERCLOCKWISE_90 -> z;
                default -> x;
            };
            int nz = switch (rot) {
                case CLOCKWISE_90 -> x;
                case CLOCKWISE_180 -> -z;
                case COUNTERCLOCKWISE_90 -> -x;
                default -> z;
            };

            if (nx < minX) minX = nx;
            if (nz < minZ) minZ = nz;
        }

        java.util.List<BlockEntry> rotated = new java.util.ArrayList<>(blocks.size());
        for (BlockEntry entry : blocks) {
            Vec3i pos = entry.offset();
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            int nx = switch (rot) {
                case CLOCKWISE_90 -> -z;
                case CLOCKWISE_180 -> -x;
                case COUNTERCLOCKWISE_90 -> z;
                default -> x;
            };
            int nz = switch (rot) {
                case CLOCKWISE_90 -> x;
                case CLOCKWISE_180 -> -z;
                case COUNTERCLOCKWISE_90 -> -x;
                default -> z;
            };

            BlockState rotatedState = entry.state();
            try {
                rotatedState = rotatedState.rotate(rot);
            } catch (Exception ignored) {
            }

            rotated.add(new BlockEntry(new Vec3i(nx - minX, y, nz - minZ), rotatedState, entry.phase()));
        }

        return new Blueprint(this.name + "_rot" + rotDeg, rotated);
    }
}
