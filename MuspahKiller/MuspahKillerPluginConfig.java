package com.theplug.MuspahKiller;

import net.runelite.client.config.*;

@ConfigGroup("MuspahKillerPluginConfig")
public interface MuspahKillerPluginConfig extends Config {
    @ConfigItem(
            keyName = "startHotkey",
            name = "Start hotkey",
            description = "Hotkey to start the plugin",
            position = 1
    )
    default Keybind startHotkey() {
        return null;
    }

    @ConfigSection(
            name = "Loadouts",
            description = "Range and mage loadouts",
            position = 2,
            closedByDefault = false
    )
    String loadOutSettings = "loadouts";

    @ConfigItem(
            keyName = "shouldSwitchGear",
            name = "Switch gears",
            description = "Should auto switch gears based on Muspah's phase",
            position = 3,
            section = loadOutSettings
    )
    default boolean shouldSwitchGear() {
        return false;
    }

    @ConfigItem(
            keyName = "mageEquipmentString",
            name = "Mage equipment",
            description = "Wear your magic equipment and right click your equipment icon to copy-paste the equipment string and put it here",
            position = 4,
            section = loadOutSettings
    )
    default String mageEquipmentString() {
        return "";
    }

    @ConfigItem(
            keyName = "rangeEquipmentString",
            name = "Range equipment",
            description = "Wear your range equipment and right click your equipment icon to copy-paste the equipment string and put it here",
            position = 5,
            section = loadOutSettings
    )
    default String rangeEquipmentString() {
        return "";
    }
}
