package com.theplug.AutoPvpPlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoPvpPluginConfig")
public interface AutoPvpPluginConfig extends Config {

    @ConfigItem(
            keyName = "startHotkey",
            name = "Start hotkey",
            description = "Hotkey to start the plugin",
            position = 0
    )
    default Keybind startHotkey() {
        return null;
    }

    @ConfigSection(
            name = "Mage loadout settings",
            description = "Mage loadout",
            position = 1,
            closedByDefault = true
    )
    String mageLoadoutSettings = "mageLoadoutSettings";

    @ConfigItem(
            keyName = "mageLoadout",
            name = "Mage loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 4,
            section = mageLoadoutSettings
    )

    default String mageLoadout() {
        return "";
    }

    @ConfigSection(
            name = "Range loadout settings",
            description = "Range loadout settings",
            position = 4,
            closedByDefault = true
    )
    String rangeLoadoutSettings = "rangeLoadoutSettings";


    @ConfigItem(
            keyName = "rangeLoadout",
            name = "Range loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 7,
            section = rangeLoadoutSettings
    )
    default String rangeLoadout() {
        return "";
    }

    @ConfigSection(
            name = "Melee loadout settings",
            description = "Third loadout settings",
            position = 8,
            closedByDefault = true
    )
    String meleeLoadoutSettings = "meleeLoadoutSettings";

    @ConfigItem(
            keyName = "meleeLoadout",
            name = "Melee loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 11,
            section = meleeLoadoutSettings
    )
    default String meleeLoadout() {
        return "";
    }

    @ConfigSection(
            name = "Tank loadout settings",
            description = "Tank loadout settings",
            position = 12,
            closedByDefault = true
    )
    String tankLoadoutSettings = "tankLoadoutSettings";

    @ConfigItem(
            keyName = "tankLoadout",
            name = "Tank loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 15,
            section = tankLoadoutSettings
    )
    default String tankLoadout() {
        return "";
    }

    @ConfigSection(
            name = "Spec loadout settings",
            description = "Spec loadout settings",
            position = 16,
            closedByDefault = true
    )
    String specLoadoutSettings = "specLoadoutSettings";

    @ConfigItem(
            keyName = "fifthLoadoutHotkey",
            name = "Fifth loadout hotkey",
            description = "Hotkey to switch to fifth loadout",
            position = 17,
            section = specLoadoutSettings
    )
    default Keybind specLoadoutHotkey() {
        return null;
    }

    @ConfigItem(
            keyName = "Spec loadout",
            name = "Spec loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 19,
            section = specLoadoutSettings
    )
    default String specLoadout() {
        return "";
    }
}
