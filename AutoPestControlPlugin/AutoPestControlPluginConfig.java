package com.theplug.AutoPestControlPlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoPestControlPluginConfig")
public interface AutoPestControlPluginConfig extends Config {
    @ConfigItem(
            keyName = "boat",
            name = "Boat",
            description = "Choose desired boat",
            position = 1
    )
    default Boat boat() {
        return Boat.INTERMEDIATE;
    }

    @ConfigItem(
            keyName = "closeDoors",
            name = "Close doors",
            description = "Close doors if there other nearby inside the fence. During pathing the doors will be closed regardless of this setting",
            position = 2
    )
    default boolean closeDoors() {
        return true;
    }

    @ConfigSection(
            name = "Fight settings",
            description = "Restock settings",
            position = 3,
            closedByDefault = false
    )
    String fightSettings = "Fight settings";

    @ConfigItem(
            keyName = "useQuickPrayers",
            name = "Use quick prayer",
            description = "Determines whether the plugin should use quick prayers",
            position = 1,
            section = fightSettings
    )
    default boolean useQuickPrayers() {
        return false;
    }

    @ConfigItem(
            keyName = "useSpecialAttack",
            name = "Use special attack",
            description = "Determines whether the plugin should use special attack",
            position = 2,
            section = fightSettings
    )
    default boolean useSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "specEnergyMinimum",
            name = "Minimum spec %",
            description = "Minimum spec energy % to use special attack of the spec weapon (ACB = 50%, ZCB = 75% etc.)",
            position = 3,
            section = fightSettings
    )
    @Range(
            min = 25,
            max = 100
    )
    default int specEnergyMinimum() {
        return 75;
    }

    @ConfigSection(
            name = "Debug settings",
            description = "Debug settings",
            position = 4,
            closedByDefault = true
    )
    String debugSettings = "Debug settings";

    @ConfigItem(
            keyName = "drawDebug",
            name = "Draw debug",
            description = "Determines whether the plugin should draw debug stuff",
            position = 1,
            section = debugSettings
    )
    default boolean drawDebug() {
        return false;
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
