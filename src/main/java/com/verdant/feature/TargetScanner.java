package com.verdant.feature;

import java.util.ArrayList;
import java.util.List;

import com.verdant.config.VerdantConfig;
import com.verdant.config.VerdantConfig.Target;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Looks through the block entities of loaded chunks (chests, hoppers, shulkers,
 * spawners are all block entities), so it is fast and does not check every block.
 */
public final class TargetScanner {
    private TargetScanner() {}

    /** One highlighted block. Coordinates are world coordinates. */
    public record Hit(double minX, double minY, double minZ,
                      double maxX, double maxY, double maxZ,
                      Target target, double distance) {}

    private static volatile List<Hit> hits = List.of();
    private static int counter = 0;

    public static List<Hit> hits() {
        return hits;
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            hits = List.of();
            return;
        }
        counter++;
        if (counter % 5 != 0) return; // rescan 4 times per second

        boolean storageOn = VerdantConfig.STORAGE.enabled;
        boolean spawnerOn = VerdantConfig.SPAWNER.enabled;
        if (!storageOn && !spawnerOn) {
            hits = List.of();
            return;
        }

        double maxRange = Math.max(storageOn ? VerdantConfig.STORAGE.range : 0,
                spawnerOn ? VerdantConfig.SPAWNER.range : 0);
        int chunkRadius = (int) Math.ceil(maxRange / 16.0) + 1;
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        int playerChunkX = Mth.floor(px) >> 4;
        int playerChunkZ = Mth.floor(pz) >> 4;

        List<Hit> found = new ArrayList<>();
        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    BlockState state = level.getBlockState(pos);
                    Target target = classify(state.getBlock());
                    if (target == null || !target.enabled || !target.module.enabled) continue;

                    double dx = pos.getX() + 0.5 - px;
                    double dy = pos.getY() + 0.5 - py;
                    double dz = pos.getZ() + 0.5 - pz;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > target.module.range) continue;

                    VoxelShape shape = state.getShape(level, pos);
                    AABB box = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
                    box = box.move(pos.getX(), pos.getY(), pos.getZ());
                    found.add(new Hit(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, target, dist));
                }
            }
        }
        hits = found;
    }

    private static Target classify(Block block) {
        if (block == Blocks.TRAPPED_CHEST) return VerdantConfig.TRAPPED_CHEST;
        if (block instanceof ChestBlock) return VerdantConfig.CHEST;
        if (block instanceof EnderChestBlock) return VerdantConfig.ENDER_CHEST;
        if (block instanceof HopperBlock) return VerdantConfig.HOPPER;
        if (block instanceof ShulkerBoxBlock) return VerdantConfig.SHULKER;
        if (block instanceof BarrelBlock) return VerdantConfig.BARREL;
        if (block instanceof SpawnerBlock) return VerdantConfig.SPAWNER_BLOCK;
        return null;
    }
}
