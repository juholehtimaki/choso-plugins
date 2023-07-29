package com.example.NmzAfkPlugin;

import net.runelite.client.config.*;

@ConfigGroup("NmzAfkPluginConfig")
public interface NmzAfkPluginConfig extends Config {
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
            keyName = "drinkPotionsBelowBoost",
            name = "Drink potions below boost",
            description = "Drink skill boosting potions if the current boost amount is below this value",
            position = 10
    )
    @Range(
            min = 0,
            max = 15
    )
    default int drinkPotionsBelowBoost() {
        return 9;
    }
}
