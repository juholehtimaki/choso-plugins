package com.theplug.GearSwitcherPlugin;

import net.runelite.client.config.*;

@ConfigGroup("GearSwitcherPluginConfig")
public interface GearSwitcherPluginConfig extends Config {
    @ConfigItem(
            keyName = "sleepBetweenActions",
            name = "Sleep between actions",
            description = "How long to sleep between actions. This is randomized by +- 40ms",
            position = 0
    )
    @Range(
            min = 0,
            max = 400
    )
    default int sleepBetweenActions() {
        return 100;
    }

    @ConfigSection(
            name = "First loadout settings",
            description = "First loadout settings",
            position = 1,
            closedByDefault = true
    )
    String firstLoadoutSettings = "firstLoadoutSettings";

    @ConfigItem(
            keyName = "firstLoadoutHotkey",
            name = "First loadout hotkey",
            description = "Hotkey to switch to first loadout",
            position = 2,
            section = firstLoadoutSettings
    )
    default Keybind firstLoadoutHotkey() {
        return null;
    }

    @ConfigItem(
            keyName = "firstLoadoutTrigger",
            name = "First loadout trigger",
            description = "First loadout trigger",
            position = 3,
            section = firstLoadoutSettings
    )
    default Trigger firstLoadoutTrigger() {
        return Trigger.NONE;
    }

    @ConfigItem(
            keyName = "firstLoadout",
            name = "First loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 4,
            section = firstLoadoutSettings
    )

    default String firstLoadout() {
        return "";
    }

    @ConfigSection(
            name = "Second loadout settings",
            description = "Second loadout settings",
            position = 4,
            closedByDefault = true
    )
    String secondLoadoutSettings = "secondLoadoutSettings";

    @ConfigItem(
            keyName = "secondLoadoutHotkey",
            name = "Secord loadout hotkey",
            description = "Hotkey to switch to second loadout",
            position = 5,
            section = secondLoadoutSettings
    )
    default Keybind secondLoadoutHotkey() {
        return null;
    }

    @ConfigItem(
            keyName = "secondLoadoutTrigger",
            name = "Second loadout trigger",
            description = "Second loadout trigger",
            position = 6,
            section = secondLoadoutSettings
    )
    default Trigger secondLoadoutTrigger() {
        return Trigger.NONE;
    }

    @ConfigItem(
            keyName = "secondLoadout",
            name = "Second loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 7,
            section = secondLoadoutSettings
    )
    default String secondLoadout() {
        return "";
    }

    @ConfigSection(
            name = "Third loadout settings",
            description = "Third loadout settings",
            position = 8,
            closedByDefault = true
    )
    String thirdLoadoutSettings = "thirdLoadoutSettings";

    @ConfigItem(
            keyName = "thirdLoadoutHotkey",
            name = "Third loadout hotkey",
            description = "Hotkey to switch to third loadout",
            position = 9,
            section = thirdLoadoutSettings
    )
    default Keybind thirdLoadoutHotkey() {
        return null;
    }

    @ConfigItem(
            keyName = "thirdLoadoutTrigger",
            name = "Third loadout trigger",
            description = "Third loadout trigger",
            position = 10,
            section = thirdLoadoutSettings
    )
    default Trigger thirdLoadoutTrigger() {
        return Trigger.NONE;
    }

    @ConfigItem(
            keyName = "thirdLoadout",
            name = "Third loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 11,
            section = thirdLoadoutSettings
    )
    default String thirdLoadout() {
        return "";
    }

    @ConfigSection(
            name = "Fourth loadout settings",
            description = "Fourth loadout settings",
            position = 12,
            closedByDefault = true
    )
    String fourthLoadoutSettings = "fourthLoadoutSettings";

    @ConfigItem(
            keyName = "fourthLoadoutHotkey",
            name = "Fourth loadout hotkey",
            description = "Hotkey to switch to fourth loadout",
            position = 13,
            section = fourthLoadoutSettings
    )
    default Keybind fourthLoadoutHotkey() {
        return null;
    }

    @ConfigItem(
            keyName = "fourthLoadoutTrigger",
            name = "Fourth loadout trigger",
            description = "Fourth loadout trigger",
            position = 14,
            section = fourthLoadoutSettings
    )
    default Trigger fourthLoadoutTrigger() {
        return Trigger.NONE;
    }

    @ConfigItem(
            keyName = "fourthLoadout",
            name = "Fourth loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 15,
            section = fourthLoadoutSettings
    )
    default String fourthLoadout() {
        return "";
    }

    @ConfigSection(
            name = "Fifth loadout settings",
            description = "Fifth loadout settings",
            position = 16,
            closedByDefault = true
    )
    String fifthLoadoutSettings = "fifthLoadoutSettings";

    @ConfigItem(
            keyName = "fifthLoadoutHotkey",
            name = "Fifth loadout hotkey",
            description = "Hotkey to switch to fifth loadout",
            position = 17,
            section = fifthLoadoutSettings
    )
    default Keybind fifthLoadoutHotkey() {
        return null;
    }

    @ConfigItem(
            keyName = "fifthLoadoutTrigger",
            name = "Fifth loadout trigger",
            description = "Fifth loadout trigger",
            position = 18,
            section = fifthLoadoutSettings
    )
    default Trigger fifthLoadoutTrigger() {
        return Trigger.NONE;
    }

    @ConfigItem(
            keyName = "fifthLoadout",
            name = "Fifth loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 19,
            section = fifthLoadoutSettings
    )
    default String fifthLoadout() {
        return "";
    }
}
