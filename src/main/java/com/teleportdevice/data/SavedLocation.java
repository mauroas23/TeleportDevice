package com.teleportdevice.data;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Una ubicación guardada. El id (UUID) es lo único que viaja del cliente al servidor para teletransportar/borrar/renombrar. */
public record SavedLocation(UUID id, String name, double x, double y, double z,
                            float yRot, float xRot, String dimension) {

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", id);
        t.putString("Name", name);
        t.putDouble("X", x);
        t.putDouble("Y", y);
        t.putDouble("Z", z);
        t.putFloat("YRot", yRot);
        t.putFloat("XRot", xRot);
        t.putString("Dim", dimension);
        return t;
    }

    public static SavedLocation load(CompoundTag t) {
        return new SavedLocation(t.getUUID("Id"), t.getString("Name"),
                t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"),
                t.getFloat("YRot"), t.getFloat("XRot"), t.getString("Dim"));
    }

    public SavedLocation withName(String newName) {
        return new SavedLocation(id, newName, x, y, z, yRot, xRot, dimension);
    }

    @Nullable
    public ResourceKey<Level> dimensionKey() {
        ResourceLocation rl = ResourceLocation.tryParse(dimension);
        return rl == null ? null : ResourceKey.create(Registries.DIMENSION, rl);
    }
}
