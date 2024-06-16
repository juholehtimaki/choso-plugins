package com.theplug.AutoGorillasPlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoGorillasPluginConfig")
public interface AutoGorillasPluginConfig extends Config {

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
            name = "First loadout",
            description = "First loadout",
            position = 2,
            closedByDefault = true
    )
    String firstLoadoutSettings = "firstLoadoutSettings";

    @ConfigItem(
            keyName = "firstLoadoutCombatStyle",
            name = "Combat style",
            description = "Defines the combat style and offensive prayer",
            position = 1,
            section = firstLoadoutSettings
    )
    default GorillaCombatStyle firstLoadoutCombatStyle() {
        return GorillaCombatStyle.MELEE;
    }

    @ConfigItem(
            keyName = "firstLoadout",
            name = "First loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 2,
            section = firstLoadoutSettings
    )

    default String firstLoadout() {
        return "";
    }

    @ConfigSection(
            name = "Second loadout",
            description = "Second loadout",
            position = 3,
            closedByDefault = true
    )
    String secondLoadoutSettings = "secondLoadoutSettings";

    @ConfigItem(
            keyName = "secondLoadoutCombatStyle",
            name = "Combat style",
            description = "Defines the combat style and offensive prayer",
            position = 1,
            section = secondLoadoutSettings
    )
    default GorillaCombatStyle secondLoadoutCombatStyle() {
        return GorillaCombatStyle.RANGE;
    }

    @ConfigItem(
            keyName = "secondLoadout",
            name = "Second loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 1,
            section = secondLoadoutSettings
    )

    default String secondLoadout() {
        return "";
    }

    @ConfigItem(
            keyName = "drinkPotionsBelowBoost",
            name = "Drink potions below boost",
            description = "Drink skill boosting potions if the current boost amount is below this value",
            position = 10
    )
    @Range(
            min = 1,
            max = 15
    )
    default int drinkPotionsBelowBoost() {
        return 9;
    }
}
