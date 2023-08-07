package com.example.PvPHelperPlugin;

import com.example.PaistiUtils.API.Prayer.PPrayer;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("PvPHelperPluginConfig")
public interface PvPHelperPluginConfig extends Config {
    @ConfigItem(
            keyName = "firstLoadoutHotkey",
            name = "First loadout hotkey",
            description = "Hotkey to switch to first loadout",
            position = 1
    )
    default Keybind firstLoadoutHotkey() {
        return null;
    }

    @ConfigItem(
            keyName = "firstLoadoutTrigger",
            name = "First loadout trigger",
            description = "First loadout trigger",
            position = 2
    )
    default Trigger firstLoadoutTrigger() {
        return Trigger.NONE;
    }

    @ConfigItem(
            keyName = "firstLoadout",
            name = "First loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 3
    )

    default String firstLoadout() {
        return "";
    }
    @ConfigItem(
            keyName = "secondLoadoutHotkey",
            name = "Secord loadout hotkey",
            description = "Hotkey to switch to second loadout",
            position = 4
    )
    default Keybind secondLoadoutHotkey() {
        return null;
    }

    @ConfigItem(
            keyName = "secondLoadoutTrigger",
            name = "Second loadout trigger",
            description = "Second loadout trigger",
            position = 5
    )
    default Trigger secondLoadoutTrigger() {
        return Trigger.NONE;
    }

    @ConfigItem(
            keyName = "secondLoadout",
            name = "Second loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 6
    )
    default String secondLoadout() {
        return "";
    }

    @ConfigItem(
            keyName = "thirdLoadoutHotkey",
            name = "Third loadout hotkey",
            description = "Hotkey to switch to third loadout",
            position = 7
    )
    default Keybind thirdLoadoutHotkey() {
        return null;
    }

    @ConfigItem(
            keyName = "thirdLoadoutTrigger",
            name = "Third loadout trigger",
            description = "Third loadout trigger",
            position = 8
    )
    default Trigger thirdLoadoutTrigger() {
        return Trigger.NONE;
    }

    @ConfigItem(
            keyName = "thirdLoadout",
            name = "This loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 9
    )
    default String thirdLoadout() {
        return "";
    }
}
