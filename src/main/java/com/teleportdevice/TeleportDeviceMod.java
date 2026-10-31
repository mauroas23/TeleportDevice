package com.teleportdevice;

import com.mojang.logging.LogUtils;
import com.teleportdevice.config.TeleportConfig;
import com.teleportdevice.network.NetworkHandler;
import com.teleportdevice.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TeleportDeviceMod.MODID)
public class TeleportDeviceMod {
    public static final String MODID = "teleportdevice";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TeleportDeviceMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addCreative);

        // COMMON: se genera en <instancia>/config/teleportdevice.toml (cliente y servidor dedicado)
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TeleportConfig.SPEC, MODID + ".toml");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::register);
    }

    private void addCreative(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.TELEPORT_DEVICE);
        }
    }
}
