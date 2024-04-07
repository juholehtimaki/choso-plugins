package com.theplug.ColosseumFarmerPlugin;

import net.runelite.client.config.*;

@ConfigGroup("ColosseumFarmerPluginConfig")
public interface ColosseumFarmerPluginConfig extends Config {
    @ConfigItem(
            keyName = "loadout",
            name = "General loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 0
    )
    default String loadout() {
        return "";
    }

    @ConfigSection(
            name = "Banking settings",
            description = "Banking settings",
            position = 1,
            closedByDefault = false
    )
    String bankingSettings = "bankingSettings";

    @ConfigItem(
            keyName = "bankUnderHpAmount",
            name = "Bank if total hp <",
            description = "After kills, bank if hp from food in inventory is less than this amount.",
            position = 5,
            section = bankingSettings
    )
    @Range(
            min = 0,
            max = 400
    )
    default int bankUnderHpAmount() {
        return 0;
    }

    @ConfigItem(
            keyName = "bankUnderPrayerAmount",
            name = "Bank if prayer <",
            description = "After kills, bank if prayer points from potions is less than this amount.",
            position = 10,
            section = bankingSettings
    )
    @Range(
            min = 0,
            max = 200
    )
    default int bankUnderPrayerAmount() {
        return 0;
    }

    @ConfigItem(
            keyName = "bankUnderBoostDoseAmount",
            name = "Bank if boost doses <",
            description = "After kills, bank if boosts (Ranging potion etc.) doses is below this amount.",
            position = 25,
            section = bankingSettings
    )
    @Range(
            min = 0,
            max = 4
    )
    default int bankUnderBoostDoseAmount() {
        return 1;
    }

    @ConfigSection(
            name = "Mage settings",
            description = "Mage settings",
            position = 2,
            closedByDefault = true
    )
    String mageSettings = "mageSettings";
    @ConfigItem(
            keyName = "mageGear",
            name = "Mage gear",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 1,
            section = mageSettings
    )
    default String mageGear() {
        return "";
    }

    @ConfigSection(
            name = "Range settings",
            description = "Range settings",
            position = 3,
            closedByDefault = true
    )
    String rangeSettings = "rangeSettings";
    @ConfigItem(
            keyName = "rangeGear",
            name = "Range gear",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 1,
            section = rangeSettings
    )
    default String rangeGear() {
        return "";
    }
    @ConfigItem(
            keyName = "useSpecialAttack",
            name = "Use special attack",
            description = "Use special attack during (only used on serpent shaman)",
            position = 2,
            section = rangeSettings
    )
    default boolean useSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "specEnergyMinimum",
            name = "Minimum spec %",
            description = "Minimum spec energy % to use special attack of the spec weapon",
            position = 3,
            section = rangeSettings
    )
    @Range(
            min = 25,
            max = 100
    )
    default int specEnergyMinimum() {
        return 100;
    }
    @ConfigItem(
            keyName = "startHotkey",
            name = "Start hotkey",
            description = "Hotkey to start the plugin",
            position = 55
    )
    default Keybind startHotkey() {
        return null;
    }
}
