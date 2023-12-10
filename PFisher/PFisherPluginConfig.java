package com.theplug.PFisher;

import net.runelite.client.config.*;

@ConfigGroup("PFisherPluginConfig")
public interface PFisherPluginConfig extends Config {
    @ConfigItem(
            keyName = "startHotkey",
            name = "Start hotkey",
            description = "Hotkey to start the plugin",
            position = 1
    )
    default Keybind startHotkey() {
        return null;
    }

    @ConfigItem(
            keyName = "fishingPoolRadius",
            name = "Fishing pool radius",
            description = "Fish selected pools within in this radius from starting position",
            position = 2
    )

    default int fishingPoolRadius() {
        return 10;
    }

    @ConfigItem(
            keyName = "fishingMethod",
            name = "Selected fishing method",
            description = "Selected fishing method (e.g. lure)",
            position = 3
    )
    default FishingMethod fishingMethod() {
        return FishingMethod.LURE;
    }

    @ConfigItem(
            keyName = "fishingPool",
            name = "Selected fishing pool",
            description = "Selected fishing pool (e.g. Rod Fishing pool)",
            position = 4
    )
    default FishingPool fishingPool() {
        return FishingPool.ROD_FISHING_SPOT;
    }

    @ConfigItem(
            keyName = "targetLevel",
            name = "Targeted fishing level",
            description = "Level to reach before stopping",
            position = 5
    )

    default int targetLevel() {
        return 99;
    }

    @ConfigItem(
            keyName = "threeTickFish",
            name = "Three tick fish",
            description = "Must have clean guam leaf, grimy guam leaf, pestle and mortar and swamp tar in inventory",
            position = 6
    )
    default boolean threeTickFish() {
        return false;
    }
}
