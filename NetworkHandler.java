package com.teleportdevice.network;

import com.teleportdevice.TeleportDeviceMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TeleportDeviceMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(ActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ActionPacket::encode)
                .decoder(ActionPacket::decode)
                .consumerMainThread(ActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPacket::encode)
                .decoder(SyncPacket::decode)
                .consumerMainThread(SyncPacket::handle)
                .add();
    }

    private NetworkHandler() {}
}
