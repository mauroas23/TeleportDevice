package com.teleportdevice.network;

import com.teleportdevice.data.SavedLocation;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Copia de sólo lectura de una ubicación, para mostrar en la GUI. */
public record LocationDto(UUID id, String name, double x, double y, double z, String dimension) {

    public static LocationDto from(SavedLocation l) {
        // Recorte defensivo: un dato corrupto o editado a mano nunca debe romper el paquete (writeUtf lanza si excede).
        String name = l.name().length() > 64 ? l.name().substring(0, 64) : l.name();
        String dim = l.dimension().length() > 128 ? l.dimension().substring(0, 128) : l.dimension();
        return new LocationDto(l.id(), name, l.x(), l.y(), l.z(), dim);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, 64);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeUtf(dimension, 128);
    }

    public static LocationDto read(FriendlyByteBuf buf) {
        return new LocationDto(buf.readUUID(), buf.readUtf(64), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readUtf(128));
    }
}
