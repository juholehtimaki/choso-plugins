package com.theplug.AutoItemCombinerPlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoItemCombinerConfig")
public interface AutoItemCombinerConfig extends Config {

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
            keyName = "firstItemNameOrId",
            name = "First item",
            description = "First item's name or ID",
            position = 1
    )
    default String firstItemNameOrId() {
        return "Needle";
    }

    @ConfigItem(
            keyName = "firstItemWithdrawCount",
            name = "First amount",
            description = "First item's withdraw amount",
            position = 2
    )
    @Range(
            min = 1,
            max = 10000000
    )
    default int firstItemWithdrawCount() {
        return 1;
    }

    @ConfigItem(
            keyName = "secondItemNameOrId",
            name = "Second item",
            description = "Second item's name or ID",
            position = 3
    )
    default String secondItemNameOrId() {
        return "Green dragon leather";
    }

    @ConfigItem(
            keyName = "secondItemWithdrawCount",
            name = "Second amount",
            description = "Second item's withdraw amount",
            position = 4
    )
    @Range(
            min = 1,
            max = 10000000
    )
    default int secondItemWithdrawCount() {
        return 24;
    }

    @ConfigItem(
            keyName = "extraItemNameOrId",
            name = "Extra item",
            description = "Extra item's name or ID. Extra item is not combined with clicks (e.g. thread for d'hide bodies)",
            position = 5
    )
    default String extraItemNameOrId() {
        return "Thread";
    }

    @ConfigItem(
            keyName = "extraItemWithdrawCount",
            name = "Extra amount",
            description = "Extra item's withdraw amount",
            position = 6
    )
    @Range(
            min = 0,
            max = 10000000
    )
    default int extraItemWithdrawCount() {
        return 0;
    }

    @ConfigItem(
            keyName = "makeInterfaceOptionName",
            name = "Make interface option name",
            description = "The option name to select from the make interface. Leave empty and it'll use spacebar to select. Defaults to spacebar if given option is not found.",
            position = 7
    )
    default String makeInterfaceOptionName() {
        return "";
    }

    @ConfigSection(
            name = "Spam combine settings",
            description = "Spam combine settings",
            position = 11,
            closedByDefault = true
    )
    String spamCombineSettings = "spamCombineSettings";

    @ConfigItem(
            keyName = "spamCombine",
            name = "Spam combine",
            description = "Toggle on when combining items with spam clicking",
            position = 15,
            section = spamCombineSettings
    )
    default boolean spamCombine() {
        return false;
    }

    @ConfigItem(
            keyName = "spamMin",
            name = "Minimum",
            description = "Minimum time between clicks (in milliseconds)",
            position = 16,
            section = spamCombineSettings
    )
    @Range(
            min = 50,
            max = 120000
    )
    default int spamMin() {
        return 100;
    }

    @ConfigItem(
            keyName = "spamMax",
            name = "Maximum",
            description = "Maximum time between clicks (in milliseconds)",
            position = 17,
            section = spamCombineSettings
    )
    @Range(
            min = 100,
            max = 120000
    )
    default int spamMax() {
        return 200;
    }

}
