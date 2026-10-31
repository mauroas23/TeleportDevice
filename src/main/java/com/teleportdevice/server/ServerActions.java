package com.teleportdevice.server;

import com.teleportdevice.TeleportDeviceMod;
import com.teleportdevice.config.TeleportConfig;
import com.teleportdevice.data.LocationStorage;
import com.teleportdevice.data.SavedLocation;
import com.teleportdevice.network.ActionPacket;
import com.teleportdevice.network.LocationDto;
import com.teleportdevice.network.NetworkHandler;
import com.teleportdevice.network.SyncPacket;
import com.teleportdevice.registry.ModItems;
import com.teleportdevice.util.NameRules;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Punto de entrada de TODAS las peticiones del cliente. Aquí se valida:
 *  - que el remitente sea un jugador vivo y sostenga el dispositivo,
 *  - límite de frecuencia,
 *  - que cualquier ubicación referenciada pertenezca a ese jugador (se busca en SU lista por UUID),
 *  - nombres saneados, límites de configuración, cooldown, coste, dimensiones y seguridad del destino.
 */
public final class ServerActions {
    private static final int MIN_TICKS_BETWEEN_ACTIONS = 4;
    private static final Map<UUID, Integer> LAST_ACTION_TICK = new HashMap<>();

    private ServerActions() {}

    public static void forget(UUID player) {
        LAST_ACTION_TICK.remove(player);
    }

    public static void clearAll() {
        LAST_ACTION_TICK.clear();
    }

    public static boolean holdsDevice(ServerPlayer player) {
        Item device = ModItems.TELEPORT_DEVICE.get();
        return player.getMainHandItem().is(device) || player.getOffhandItem().is(device);
    }

    /** Llamado desde el ítem (clic derecho en el servidor). */
    public static void open(ServerPlayer player) {
        sendSync(player, SyncPacket.MODE_OPEN, "", "", true);
    }

    public static void handle(ServerPlayer player, ActionPacket msg) {
        if (msg.type() == null || !player.isAlive()) return;
        try {
            dispatch(player, msg);
        } catch (RuntimeException ex) {
            // Un fallo inesperado no debe dejar la GUI colgada ni tumbar el servidor: se registra y se avisa al jugador.
            TeleportDeviceMod.LOGGER.error("Error handling {} from {}", msg.type(), player.getName().getString(), ex);
            fail(player, "internal_error", "");
        }
    }

    private static void dispatch(ServerPlayer player, ActionPacket msg) {
        if (!holdsDevice(player)) {
            sendSync(player, SyncPacket.MODE_CLOSE, "teleportdevice.msg.not_holding", "", false);
            return;
        }

        int now = player.server.getTickCount();
        Integer last = LAST_ACTION_TICK.get(player.getUUID());
        if (last != null && now >= last && now - last < MIN_TICKS_BETWEEN_ACTIONS) return; // anti-spam (todas las acciones)
        LAST_ACTION_TICK.put(player.getUUID(), now);

        LocationStorage storage = LocationStorage.get(player.server);
        switch (msg.type()) {
            case REQUEST_SYNC -> sendSync(player, SyncPacket.MODE_UPDATE, "", "", true);
            case SAVE -> save(player, storage, msg);
            case DELETE -> delete(player, storage, msg);
            case RENAME -> rename(player, storage, msg);
            case TELEPORT -> teleport(player, storage, msg);
        }
    }

    // ------------------------------------------------------------------ Acciones

    private static void save(ServerPlayer player, LocationStorage storage, ActionPacket msg) {
        String name = NameRules.sanitize(msg.name());
        if (name == null) { fail(player, "name_invalid", ""); return; }

        String dim = player.level().dimension().location().toString();
        if (TeleportService.isDimensionBlacklisted(dim)) { fail(player, "dim_blacklisted", ""); return; }

        List<SavedLocation> list = storage.getAll(player.getUUID());
        if (list.size() >= TeleportConfig.MAX_LOCATIONS.get()) { fail(player, "limit_reached", ""); return; }
        if (storage.nameTaken(player.getUUID(), name, null)) { fail(player, "name_duplicate", ""); return; }

        // Coordenadas y dimensión salen del SERVIDOR (posición real del jugador), nunca del paquete.
        storage.add(player.getUUID(), new SavedLocation(UUID.randomUUID(), name,
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), dim));
        ok(player, "saved", name);
    }

    private static void delete(ServerPlayer player, LocationStorage storage, ActionPacket msg) {
        Optional<SavedLocation> loc = storage.find(player.getUUID(), msg.target());
        if (loc.isEmpty()) { fail(player, "not_found", ""); return; }
        storage.remove(player.getUUID(), msg.target());
        ok(player, "deleted", loc.get().name());
    }

    private static void rename(ServerPlayer player, LocationStorage storage, ActionPacket msg) {
        String name = NameRules.sanitize(msg.name());
        if (name == null) { fail(player, "name_invalid", ""); return; }
        Optional<SavedLocation> loc = storage.find(player.getUUID(), msg.target());
        if (loc.isEmpty()) { fail(player, "not_found", ""); return; }
        if (storage.nameTaken(player.getUUID(), name, msg.target())) {
            fail(player, "name_duplicate", ""); return;
        }
        storage.rename(player.getUUID(), msg.target(), name);
        ok(player, "renamed", name);
    }

    private static void teleport(ServerPlayer player, LocationStorage storage, ActionPacket msg) {
        Optional<SavedLocation> loc = storage.find(player.getUUID(), msg.target());
        if (loc.isEmpty()) { fail(player, "not_found", ""); return; }

        TeleportService.Failure failure = TeleportService.tryTeleport(player, loc.get(), storage);
        if (failure != null) {
            fail(player, failure.key(), failure.arg());
        } else {
            sendSync(player, SyncPacket.MODE_CLOSE, "teleportdevice.msg.teleported", loc.get().name(), true);
        }
    }

    // ------------------------------------------------------------------ Utilidades

    private static void ok(ServerPlayer player, String key, String arg) {
        sendSync(player, SyncPacket.MODE_UPDATE, "teleportdevice.msg." + key, arg, true);
    }

    private static void fail(ServerPlayer player, String key, String arg) {
        sendSync(player, SyncPacket.MODE_UPDATE, "teleportdevice.msg." + key, arg, false);
    }

    private static void sendSync(ServerPlayer player, int mode, String statusKey, String statusArg, boolean ok) {
        // Si la clave ya viene completa (teleportdevice.msg.*) se respeta; si no, se completa.
        String key = (statusKey.isEmpty() || statusKey.startsWith("teleportdevice.")) ? statusKey
                : "teleportdevice.msg." + statusKey;

        LocationStorage storage = LocationStorage.get(player.server);
        List<LocationDto> dtos = storage.getAll(player.getUUID()).stream().map(LocationDto::from).toList();
        Item cost = TeleportService.costItem();
        String costId = (cost == null || TeleportService.costCount() <= 0) ? ""
                : String.valueOf(ForgeRegistries.ITEMS.getKey(cost));

        SyncPacket packet = new SyncPacket(mode, key, statusArg, ok,
                TeleportService.cooldownRemaining(player, storage),
                TeleportConfig.MAX_LOCATIONS.get(),
                TeleportConfig.ALLOW_CROSS_DIMENSION.get(),
                costId, TeleportService.costCount(), dtos);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
