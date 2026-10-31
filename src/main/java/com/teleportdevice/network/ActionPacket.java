package com.teleportdevice.network;

import com.teleportdevice.server.ServerActions;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Cliente -> Servidor. Sólo lleva una intención (acción + id de ubicación + texto).
 * NUNCA lleva coordenadas: el servidor usa su propia posición del jugador y sus propios datos guardados.
 */
public record ActionPacket(ActionType type, UUID target, String name) {
    private static final UUID NIL = new UUID(0L, 0L);

    public static ActionPacket of(ActionType type) {
        return new ActionPacket(type, NIL, "");
    }

    public static ActionPacket of(ActionType type, UUID target) {
        return new ActionPacket(type, target, "");
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(type.ordinal());
        buf.writeUUID(target);
        buf.writeUtf(name, 64);
    }

    public static ActionPacket decode(FriendlyByteBuf buf) {
        int o = buf.readUnsignedByte();
        ActionType[] values = ActionType.values();
        ActionType t = o < values.length ? values[o] : null; // índice inválido => se ignora en el servidor
        return new ActionPacket(t, buf.readUUID(), buf.readUtf(64));
    }

    public static void handle(ActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender(); // el remitente lo determina Forge, no el paquete
        if (sender != null) {
            ServerActions.handle(sender, msg);
        }
    }
}
