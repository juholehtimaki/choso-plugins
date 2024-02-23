package com.PaistiPlugins.AutoChaosAltarPlugin;

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
}
