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
            keyName = "buyCoalBag",
            name = "Buy coal bag",
            description = "Automatically buys a coal bag if user doesn't have it already",
            position = 2
    )
    default boolean buyCoalBag() {
        return false;
    }
}
