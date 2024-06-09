package com.theplug.AutoRoguesDenPlugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("AutoRoguesDenPluginConfig")
public interface AutoRoguesDenPluginConfig extends Config {

    @ConfigItem(
            keyName = "startHotkey",
            name = "Start hotkey",
            description = "Hotkey to start the plugin",
            position = 25
    )
    default Keybind startHotkey() {
        return null;
    }

    @ConfigItem(
            keyName = "useRunRestores",
            name = "Use run restore",
            description = "Use super energies and staminas. Priorities super energies for energy. Must have super energy(4)s and stamina potions in the bank.",
            position = 1
    )
    default boolean useRunRestores() {
        return true;
    }

    @ConfigItem(
            keyName = "stopAfterFiveCrates",
            name = "Stop after five crates",
            description = "Stop after enough (5) crates have been acquired for the rogues outfit.",
            position = 2
    )
    default boolean stopAfterFiveCrates() {
        return true;
    }
}
