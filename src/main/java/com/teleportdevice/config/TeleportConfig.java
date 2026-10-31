package com.teleportdevice.config;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración COMMON: config/teleportdevice.toml. El servidor es quien manda. */
public final class TeleportConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MAX_LOCATIONS;
    public static final ForgeConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSION_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<String> COST_ITEM;
    public static final ForgeConfigSpec.IntValue COST_COUNT;
    public static final ForgeConfigSpec.BooleanValue CREATIVE_BYPASS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("general");
        MAX_LOCATIONS = b
                .comment("Maximum number of saved locations per player.")
                .defineInRange("maxLocations", 10, 1, 100);
        COOLDOWN_SECONDS = b
                .comment("Seconds a player must wait between teleports (0 = no cooldown).")
                .defineInRange("cooldownSeconds", 30, 0, 86400);
        ALLOW_CROSS_DIMENSION = b
                .comment("Allow teleporting to a location saved in a different dimension.")
                .define("allowCrossDimension", true);
        DIMENSION_BLACKLIST = b
                .comment("Dimensions where saving AND teleporting (from/to) is forbidden.",
                         "Example: [\"minecraft:the_end\", \"somemod:dungeon\"]")
                .defineListAllowEmpty("dimensionBlacklist", List.<String>of(), o -> o instanceof String);
        CREATIVE_BYPASS = b
                .comment("Creative-mode players ignore cooldown and cost.")
                .define("creativeBypass", true);
        b.pop();

        b.push("cost");
        COST_ITEM = b
                .comment("Item consumed on every teleport (registry name), e.g. \"minecraft:ender_pearl\".",
                         "Leave empty or set costCount to 0 to make teleporting free.")
                .define("costItem", "minecraft:ender_pearl");
        COST_COUNT = b
                .comment("How many of costItem are consumed per teleport (0 = free).")
                .defineInRange("costCount", 0, 0, 64);
        b.pop();

        SPEC = b.build();
    }

    private TeleportConfig() {}
}
