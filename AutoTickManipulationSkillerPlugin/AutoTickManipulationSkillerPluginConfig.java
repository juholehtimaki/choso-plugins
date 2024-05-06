package com.theplug.AutoTickManipulationSkillerPlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoTickManipulationSkillerPluginConfig")
public interface AutoTickManipulationSkillerPluginConfig extends Config {

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
            keyName = "skillingMethod",
            name = "Method",
            description = "Selected skilling method",
            position = 1
    )
    default SkillingMethod skillingMethod() {
        return SkillingMethod.TEAK_CHOPPING;
    }

    @ConfigItem(
            keyName = "hopOnNearbyPlayers",
            name = "Hop on nearby players",
            description = "Hop on nearby players to avoid competition",
            position = 2
    )
    default boolean hopOnNearbyPlayers() {
        return false;
    }

    @ConfigSection(
            name = "Cooking settings",
            description = "Cooking settings",
            position = 3,
            closedByDefault = true
    )
    String cookingSettings = "Debug settings";

    @ConfigItem(
            keyName = "rawFood",
            name = "Raw food",
            description = "List of raw food, separated by new line",
            position = 1,
            section = cookingSettings
    )
    default String rawFood() {
        return "Raw salmon\nRaw trout";
    }
}
