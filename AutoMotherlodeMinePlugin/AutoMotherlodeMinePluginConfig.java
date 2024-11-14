package com.theplug.AutoMotherlodeMinePlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoMotherlodeMinePluginConfig")
public interface AutoMotherlodeMinePluginConfig extends Config {

    @ConfigItem(
            keyName = "startHotkey",
            name = "Start hotkey",
            description = "Hotkey to start the plugin",
            position = 1
    )
    default Keybind startHotkey() {
        return null;
    }

    @ConfigItem(
            keyName = "area",
            name = "Area",
            description = "Desired area to mine",
            position = 2
    )
    default Area area() {
        return Area.NW_AREA;
    }

    @ConfigItem(
            keyName = "buyCoalBag",
            name = "Buy coal bag",
            description = "Automatically buys a coal bag if user doesn't have it already",
            position = 3
    )
    default boolean buyCoalBag() {
        return false;
    }

    @ConfigItem(
            keyName = "upgradePickaxe",
            name = "Automatically upgrade pickaxe",
            description = "Automatically upgrades pickaxe if there are better pickaxes available in the bank",
            position = 4
    )
    default boolean upgradePickaxe() {
        return true;
    }

    @ConfigItem(
            keyName = "hideScreenOverlay",
            name = "Hide overlay",
            description = "Hide overlay",
            position = 5
    )
    default boolean hideScreenOverlay() {
        return false;
    }
}
