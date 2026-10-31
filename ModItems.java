package com.teleportdevice.registry;

import com.teleportdevice.TeleportDeviceMod;
import com.teleportdevice.item.TeleportDeviceItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TeleportDeviceMod.MODID);

    public static final RegistryObject<Item> TELEPORT_DEVICE = ITEMS.register("teleport_device",
            () -> new TeleportDeviceItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private ModItems() {}
}
