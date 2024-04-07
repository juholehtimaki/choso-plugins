package com.theplug.AutoChaosAltarPlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoChaosAltarPluginConfig")
public interface AutoChaosAltarPluginConfig extends Config {

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
            keyName = "inventoryLoadout",
            name = "Inventory loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 2
    )

    default String inventoryLoadout() {
        return "";
    }
}
