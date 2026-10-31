package com.teleportdevice.data;

import com.teleportdevice.TeleportDeviceMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Almacenamiento PERMANENTE del lado del servidor (world/data/teleportdevice_locations.dat).
 * Las ubicaciones se indexan por UUID del jugador: un jugador sólo puede acceder a su propia lista.
 */
public class LocationStorage extends SavedData {
    public static final String NAME = TeleportDeviceMod.MODID + "_locations";

    private final Map<UUID, List<SavedLocation>> locations = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    public static LocationStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LocationStorage::load, LocationStorage::new, NAME);
    }

    public List<SavedLocation> getAll(UUID owner) {
        return List.copyOf(locations.getOrDefault(owner, List.of()));
    }

    public Optional<SavedLocation> find(UUID owner, UUID id) {
        return locations.getOrDefault(owner, List.of()).stream().filter(l -> l.id().equals(id)).findFirst();
    }

    /** ¿Tiene ese jugador otra ubicación con el mismo nombre (sin distinguir mayúsculas)? Sólo mira SU lista. */
    public boolean nameTaken(UUID owner, String name, @Nullable UUID ignoreId) {
        for (SavedLocation l : locations.getOrDefault(owner, List.of())) {
            if (!l.id().equals(ignoreId) && l.name().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public void add(UUID owner, SavedLocation loc) {
        locations.computeIfAbsent(owner, k -> new ArrayList<>()).add(loc);
        setDirty();
    }

    public boolean remove(UUID owner, UUID id) {
        List<SavedLocation> list = locations.get(owner);
        if (list == null) return false;
        boolean removed = list.removeIf(l -> l.id().equals(id));
        if (removed) setDirty();
        return removed;
    }

    public boolean rename(UUID owner, UUID id, String newName) {
        List<SavedLocation> list = locations.get(owner);
        if (list == null) return false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                list.set(i, list.get(i).withName(newName));
                setDirty();
                return true;
            }
        }
        return false;
    }

    /** Game time (overworld) del último teletransporte, o -1 si nunca. Persistente: no se evade reconectando. */
    public long getLastTeleport(UUID owner) {
        return lastTeleport.getOrDefault(owner, -1L);
    }

    public void setLastTeleport(UUID owner, long gameTime) {
        lastTeleport.put(owner, gameTime);
        setDirty();
    }

    // ---------- NBT ----------

    public static LocationStorage load(CompoundTag tag) {
        LocationStorage s = new LocationStorage();
        ListTag players = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag p = players.getCompound(i);
            if (!p.hasUUID("Owner")) continue;
            UUID owner = p.getUUID("Owner");
            ListTag list = p.getList("Locations", Tag.TAG_COMPOUND);
            List<SavedLocation> out = new ArrayList<>();
            for (int j = 0; j < list.size(); j++) {
                CompoundTag l = list.getCompound(j);
                if (l.hasUUID("Id")) out.add(SavedLocation.load(l));
            }
            s.locations.put(owner, out);
            if (p.contains("LastTeleport")) s.lastTeleport.put(owner, p.getLong("LastTeleport"));
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        Set<UUID> all = new HashSet<>(locations.keySet());
        all.addAll(lastTeleport.keySet());
        for (UUID owner : all) {
            CompoundTag p = new CompoundTag();
            p.putUUID("Owner", owner);
            ListTag list = new ListTag();
            for (SavedLocation l : locations.getOrDefault(owner, List.of())) list.add(l.save());
            p.put("Locations", list);
            if (lastTeleport.containsKey(owner)) p.putLong("LastTeleport", lastTeleport.get(owner));
            players.add(p);
        }
        tag.put("Players", players);
        return tag;
    }
}
