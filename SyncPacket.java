package com.teleportdevice.network;

import com.teleportdevice.client.ClientHooks;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Servidor -> Cliente: estado completo de la GUI de UN jugador (sólo sus datos). */
public record SyncPacket(int mode, String statusKey, String statusArg, boolean statusOk,
                         int cooldownTicks, int maxLocations, boolean allowCrossDimension,
                         String costItem, int costCount, List<LocationDto> locations) {

    public static final int MODE_UPDATE = 0;
    public static final int MODE_OPEN = 1;
    public static final int MODE_CLOSE = 2;
    public static final int MAX_LOCATIONS_ON_WIRE = 1024;

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(mode);
        buf.writeUtf(statusKey, 128);
        buf.writeUtf(statusArg, 128);
        buf.writeBoolean(statusOk);
        buf.writeVarInt(cooldownTicks);
        buf.writeVarInt(maxLocations);
        buf.writeBoolean(allowCrossDimension);
        buf.writeUtf(costItem, 128);
        buf.writeVarInt(costCount);
        buf.writeVarInt(locations.size());
        for (LocationDto l : locations) l.write(buf);
    }

    public static SyncPacket decode(FriendlyByteBuf buf) {
        int mode = buf.readUnsignedByte();
        String key = buf.readUtf(128);
        String arg = buf.readUtf(128);
        boolean ok = buf.readBoolean();
        int cd = buf.readVarInt();
        int max = buf.readVarInt();
        boolean cross = buf.readBoolean();
        String cost = buf.readUtf(128);
        int costCount = buf.readVarInt();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_LOCATIONS_ON_WIRE) throw new DecoderException("Invalid location count: " + n);
        List<LocationDto> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(LocationDto.read(buf));
        return new SyncPacket(mode, key, arg, ok, cd, max, cross, cost, costCount, list);
    }

    public static void handle(SyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // La lambda interna sólo se resuelve en el cliente; el servidor dedicado nunca carga ClientHooks.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleSync(msg));
    }
}
