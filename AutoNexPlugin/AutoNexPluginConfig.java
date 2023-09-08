package com.PaistiPlugins.AutoNexPlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoNexPluginConfig")
public interface AutoNexPluginConfig extends Config {
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
            keyName = "shouldAttackNex",
            name = "Should attack Nex",
            description = "Determines if plugin should automatically attack Nex",
            position = 2
    )
    default boolean shouldAttackNex() {
        return false;
    }
}
