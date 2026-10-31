package com.teleportdevice.server;

import com.teleportdevice.TeleportDeviceMod;
import com.teleportdevice.config.TeleportConfig;
import com.teleportdevice.data.LocationStorage;
import com.teleportdevice.data.SavedLocation;
import com.teleportdevice.util.Cooldown;
import com.teleportdevice.util.SearchOrder;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** Toda la validación y ejecución del teletransporte. Se ejecuta SIEMPRE en el servidor. */
public final class TeleportService {
    private static final int SEARCH_RADIUS = 3;
    private static final int SEARCH_VERTICAL = 6;
    private static final int MAX_DROP = 3;       // caída máxima aceptada bajo el destino (sin daño)
    private static final double PLAYER_WIDTH_HALF = 0.3;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final double MAX_COORD = 2.9E7; // por debajo del límite del mundo (±3.0E7)

    /** Motivo de fallo (clave de mensaje sin prefijo + argumento opcional). */
    public record Failure(String key, String arg) {
        static Failure of(String key) { return new Failure(key, ""); }
    }

    private TeleportService() {}

    // ------------------------------------------------------------------ API

    /** @return null si salió bien; en caso contrario el motivo del fallo (no se cobra nada ni se aplica cooldown). */
    @Nullable
    public static Failure tryTeleport(ServerPlayer player, SavedLocation loc, LocationStorage storage) {
        MinecraftServer server = player.server;
        ServerLevel current = player.serverLevel();

        if (isDimensionBlacklisted(loc.dimension())
                || isDimensionBlacklisted(current.dimension().location().toString())) {
            return Failure.of("dim_blacklisted");
        }

        ResourceKey<Level> key = loc.dimensionKey();
        ServerLevel target = key == null ? null : server.getLevel(key);
        if (target == null) return Failure.of("dim_missing");

        if (target != current && !TeleportConfig.ALLOW_CROSS_DIMENSION.get()) {
            return Failure.of("cross_dim_disabled");
        }

        boolean bypass = bypassesLimits(player);
        Item cost = costItem();
        int costCount = costCount();
        if (!bypass) {
            int remaining = cooldownRemaining(player, storage);
            if (remaining > 0) return new Failure("cooldown", Long.toString(Cooldown.secondsCeil(remaining)));
            if (cost != null && costCount > 0 && countItem(player, cost) < costCount) {
                return new Failure("cost_missing", costCount + ";" + ForgeRegistries.ITEMS.getKey(cost));
            }
        }

        // Sólo ahora (cooldown y coste ya comprobados) se carga el chunk de destino.
        Vec3 dest = findSafeSpot(target, loc);
        if (dest == null) return Failure.of("unsafe");

        float yaw = Float.isFinite(loc.yRot()) ? loc.yRot() : 0.0F;
        float pitch = Float.isFinite(loc.xRot()) ? Mth.clamp(loc.xRot(), -90.0F, 90.0F) : 0.0F;

        Vec3 origin = player.position();
        try {
            player.teleportTo(target, dest.x, dest.y, dest.z, yaw, pitch);
        } catch (RuntimeException ex) {
            TeleportDeviceMod.LOGGER.error("Teleport of {} failed", player.getName().getString(), ex);
            return Failure.of("teleport_failed");
        }

        // Si otro mod/plugin (p. ej. vía eventos de Arclight/Bukkit) canceló o desvió el teletransporte, no cobramos nada.
        if (player.serverLevel() != target || player.position().distanceToSqr(dest) > 4.0) {
            return Failure.of("teleport_failed");
        }
        player.resetFallDistance();

        if (!bypass) {
            if (cost != null && costCount > 0) consumeItem(player, cost, costCount);
            storage.setLastTeleport(player.getUUID(), server.overworld().getGameTime());
        }

        current.sendParticles(ParticleTypes.PORTAL, origin.x, origin.y + 1.0, origin.z, 30, 0.3, 0.5, 0.3, 0.2);
        current.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        target.sendParticles(ParticleTypes.PORTAL, dest.x, dest.y + 1.0, dest.z, 30, 0.3, 0.5, 0.3, 0.2);
        target.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        return null;
    }

    public static boolean bypassesLimits(ServerPlayer player) {
        return TeleportConfig.CREATIVE_BYPASS.get() && player.isCreative();
    }

    public static boolean isDimensionBlacklisted(String dimensionId) {
        return TeleportConfig.DIMENSION_BLACKLIST.get().contains(dimensionId);
    }

    /** Ticks que le faltan al jugador para poder teletransportarse otra vez. */
    public static int cooldownRemaining(ServerPlayer player, LocationStorage storage) {
        if (bypassesLimits(player)) return 0;
        return Cooldown.remainingTicks(storage.getLastTeleport(player.getUUID()),
                TeleportConfig.COOLDOWN_SECONDS.get(), player.server.overworld().getGameTime());
    }

    // ------------------------------------------------------------------ Coste

    @Nullable
    public static Item costItem() {
        String id = TeleportConfig.COST_ITEM.get();
        if (id == null || id.isBlank()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        if (rl == null) return null;
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return (item == null || item == Items.AIR) ? null : item;
    }

    public static int costCount() {
        return costItem() == null ? 0 : TeleportConfig.COST_COUNT.get();
    }

    private static int countItem(ServerPlayer player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) total += s.getCount();
        }
        return total;
    }

    private static void consumeItem(ServerPlayer player, Item item, int amount) {
        int left = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        player.getInventory().setChanged();
    }

    // ------------------------------------------------------------------ Destino seguro

    /**
     * 1) Prueba la posición exacta guardada. 2) Si no es segura, busca alrededor (radio 3, ±6 en vertical, de dentro
     * hacia fuera). 3) En dimensiones sin techo, prueba la superficie. Si nada es seguro devuelve null.
     */
    @Nullable
    static Vec3 findSafeSpot(ServerLevel level, SavedLocation loc) {
        if (!Double.isFinite(loc.x()) || !Double.isFinite(loc.y()) || !Double.isFinite(loc.z())) return null;
        int bx = Mth.floor(loc.x()), by = Mth.floor(loc.y()), bz = Mth.floor(loc.z());

        for (int[] o : SearchOrder.offsets(SEARCH_RADIUS, SEARCH_VERTICAL)) {
            boolean exact = o[0] == 0 && o[1] == 0 && o[2] == 0;
            double cx = exact ? loc.x() : bx + o[0] + 0.5;
            double cy = exact ? loc.y() : by + o[1];
            double cz = exact ? loc.z() : bz + o[2] + 0.5;
            if (isSafe(level, cx, cy, cz)) return new Vec3(cx, cy, cz);
        }
        if (!level.dimensionType().hasCeiling()) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            double cx = bx + 0.5, cz = bz + 0.5;
            if (isSafe(level, cx, y, cz)) return new Vec3(cx, y, cz);
        }
        return null;
    }

    private static boolean isSafe(ServerLevel level, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        if (Math.abs(x) > MAX_COORD || Math.abs(z) > MAX_COORD) return false;
        if (y < level.getMinBuildHeight() + 1 || y + PLAYER_HEIGHT > level.getMaxBuildHeight()) return false;

        AABB box = new AABB(x - PLAYER_WIDTH_HALF, y, z - PLAYER_WIDTH_HALF,
                x + PLAYER_WIDTH_HALF, y + PLAYER_HEIGHT, z + PLAYER_WIDTH_HALF);
        if (!level.getWorldBorder().isWithinBounds(box)) return false;

        // IMPORTANTE: noCollision() ignora los chunks que no están cargados (los trata como aire). Hay que cargarlos
        // ANTES de comprobar colisiones; si no, un destino lejano podría aprobarse estando dentro de bloques.
        preloadChunks(level, box);
        if (!level.noCollision(box)) return false;

        // Ni dentro de lava, fuego, cactus, etc.
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (isHazard(level.getBlockState(p))) return false;
        }

        // Debe haber suelo (o agua) a menos de MAX_DROP bloques; así se evita el vacío y las caídas largas.
        BlockPos feet = BlockPos.containing(x, y, z);
        for (int i = 0; i <= MAX_DROP + 1; i++) {
            BlockPos p = feet.below(i);
            BlockState s = level.getBlockState(p);
            if (isHazard(s)) return false;
            if (!s.getCollisionShape(level, p).isEmpty()) return true;
            if (s.getFluidState().is(FluidTags.WATER)) return true;
        }
        return false;
    }

    /** Carga (o genera, si hiciera falta) los chunks que toca la caja del jugador. */
    private static void preloadChunks(ServerLevel level, AABB box) {
        int minCx = Mth.floor(box.minX) >> 4, maxCx = Mth.floor(box.maxX) >> 4;
        int minCz = Mth.floor(box.minZ) >> 4, maxCz = Mth.floor(box.maxZ) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    private static boolean isHazard(BlockState s) {
        return s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES)
                || s.is(Blocks.CACTUS) || s.is(Blocks.MAGMA_BLOCK)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.WITHER_ROSE)
                || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.NETHER_PORTAL)
                || s.is(Blocks.END_PORTAL) || s.is(Blocks.END_GATEWAY)
                || s.getFluidState().is(FluidTags.LAVA);
    }
}
