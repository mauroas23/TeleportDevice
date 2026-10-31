package com.teleportdevice.server;

import com.teleportdevice.TeleportDeviceMod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TeleportDeviceMod.MODID)
public final class ServerEvents {
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerActions.forget(event.getEntity().getUUID());
    }

    /** Al cerrar el servidor (o el mundo en un solo jugador) no debe quedar estado estático de la sesión anterior. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ServerActions.clearAll();
    }

    private ServerEvents() {}
}
