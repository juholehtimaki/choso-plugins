package com.theplug.AutoTitheFarmPlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoTitheFarmPluginConfig")
public interface AutoTitheFarmPluginConfig extends Config {

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

    @ConfigItem(
            keyName = "targetLevel",
            name = "Targeted prayer level",
            description = "Level to reach before stopping",
            position = 3
    )

    default int targetLevel() {
        return 99;
    }
}
