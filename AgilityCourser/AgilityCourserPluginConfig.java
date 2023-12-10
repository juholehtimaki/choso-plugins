package com.theplug.AgilityCourser;

import net.runelite.client.config.*;

@ConfigGroup("AgilityCourserPluginConfig")
public interface AgilityCourserPluginConfig extends Config {

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
            keyName = "selectedCourse",
            name = "Selected course to do",
            description = "Select the agility course you wish to do",
            position = 2
    )
    default Course selectedCourse() {
        return Course.VARROCK;
    }

    @ConfigItem(
            keyName = "progressiveMode",
            name = "Progressive mode",
            description = "Automatically proceed on courses as agility level increases",
            position = 3
    )
    default boolean progressiveMode() {
        return false;
    }

    @ConfigItem(
            keyName = "skipCanifis",
            name = "Skip Canifis",
            description = "Skip Canifis course in progressive mode",
            position = 4
    )
    default boolean skipCanifis() {
        return false;
    }

    @ConfigItem(
            keyName = "randomMiniAfkChance",
            name = "Random mini afk chance",
            description = "% Chance between obstacles to afk for a short duration",
            position = 13
    )
    @Range(
            min = 0,
            max = 80
    )
    default int randomMiniAfkChance() {
        return 3;
    }

    @ConfigItem(
            keyName = "randomExtraClickChance",
            name = "Random extra click chance",
            description = "% Chance to click obstacles a few extra times",
            position = 14
    )
    @Range(
            min = 0,
            max = 100
    )
    default int randomExtraClickChance() {
        return 10;
    }

    @ConfigItem(
            keyName = "targetLevel",
            name = "Target agility level",
            description = "Stop upon reaching this level",
            position = 15
    )

    default int targetLevel() {
        return 99;
    }

    @ConfigItem(
            keyName = "hopOnPlayerNearby",
            name = "Hop on nearby player",
            description = "Hop if players are nearby",
            position = 16
    )
    default boolean hopOnPlayerNearby() {
        return false;
    }

    @ConfigItem(
            keyName = "alchDuringCourses",
            name = "Enable alching",
            description = "Alch items during courses",
            position = 17
    )
    default boolean alchDuringCourses() {
        return false;
    }

    @ConfigItem(
            keyName = "alchNamesOrIds",
            name = "Alch items",
            description = "Item names or ids that you want to alch during courses",
            position = 18
    )
    default String alchNamesOrIds() {
        return "Yew longbow,123456";
    }
}
