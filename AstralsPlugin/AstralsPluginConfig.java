package com.example.AstralsPlugin;

import com.example.BlackjackerPlugin.BlackjackerPlugin;
import net.runelite.client.config.*;

@ConfigGroup("AstralsPluginConfig")
public interface AstralsPluginConfig extends Config {
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
