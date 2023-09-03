package com.PaistiPlugins.StonemasonPlugin;

import net.runelite.client.config.*;

@ConfigGroup("StonemasonPluginConfig")
public interface StonemasonPluginConfig extends Config {
    @ConfigItem(
            keyName = "startHotkey",
            name = "Start hotkey",
            description = "Hotkey to start the plugin",
            position = 25
    )
    default Keybind startHotkey() {
        return null;
    }
}
