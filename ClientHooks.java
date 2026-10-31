package com.teleportdevice.client;

import com.teleportdevice.network.SyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/** Clase SÓLO CLIENTE. Nunca se referencia fuera de DistExecutor / código cliente. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void handleSync(SyncPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        switch (packet.mode()) {
            case SyncPacket.MODE_OPEN -> {
                if (mc.screen instanceof TeleportDeviceScreen screen) {
                    screen.applySync(packet);
                } else {
                    mc.setScreen(new TeleportDeviceScreen(packet));
                }
            }
            case SyncPacket.MODE_CLOSE -> {
                if (mc.screen instanceof TeleportDeviceScreen) mc.setScreen(null);
                if (mc.player != null && !packet.statusKey().isEmpty()) {
                    mc.player.displayClientMessage(statusComponent(packet.statusKey(), packet.statusArg()), true);
                }
            }
            default -> {
                if (mc.screen instanceof TeleportDeviceScreen screen) screen.applySync(packet);
            }
        }
    }

    /** Traduce un mensaje del servidor con el idioma del CLIENTE. */
    public static Component statusComponent(String key, String arg) {
        if (key.equals("teleportdevice.msg.cost_missing")) {
            // arg = "<cantidad>;<id del ítem>"
            String[] parts = arg.split(";", 2);
            String count = parts.length > 0 ? parts[0] : "?";
            Component name = parts.length > 1 ? itemName(parts[1]) : Component.literal("?");
            return Component.translatable(key, count, name);
        }
        return arg.isEmpty() ? Component.translatable(key) : Component.translatable(key, arg);
    }

    public static Component itemName(String registryName) {
        ResourceLocation rl = ResourceLocation.tryParse(registryName);
        if (rl != null) {
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null) return item.getDescription();
        }
        return Component.literal(registryName);
    }
}
