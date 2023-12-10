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
            keyName = "primaryItemNameOrId",
            name = "Primary item",
            description = "Primary item's name or ID",
            position = 2
    )
    default String primaryItemNameOrId() {
        return "Chisel";
    }

    @ConfigItem(
            keyName = "primaryItemWithdrawCount",
            name = "Primary withdraw",
            description = "Primary item's withdraw count",
            position = 3
    )
    @Range(
            min = 1,
            max = 10000000
    )
    default int primaryItemWithdrawCount() {
        return 1;
    }

    @ConfigItem(
            keyName = "secondaryItemNameOrId",
            name = "Secondary item",
            description = "Secondary item's name or ID",
            position = 4
    )

    default String secondaryItemNameOrId() {
        return "Uncut sapphire";
    }

    @ConfigItem(
            keyName = "secondaryItemWithdrawCount",
            name = "Secondary withdraw",
            description = "Secondary item's withdraw count",
            position = 5
    )
    @Range(
            min = 1,
            max = 10000000
    )
    default int secondaryItemWithdrawCount() {
        return 27;
    }

    @ConfigSection(
            name = "Spam combine settings",
            description = "Spam combine settings",
            position = 6,
            closedByDefault = true
    )
    String spamCombineSettings = "spamCombineSettings";

    @ConfigItem(
            keyName = "spamCombine",
            name = "Spam combine",
            description = "Toggle on when combining items with spam clicking",
            position = 7,
            section = spamCombineSettings
    )
    default boolean spamCombine() {
        return false;
    }

    @ConfigItem(
            keyName = "spamMin",
            name = "Minimum",
            description = "Minimum time between clicks",
            position = 8,
            section = spamCombineSettings
    )
    @Range(
            min = 1,
            max = 3000
    )
    default int spamMin() {
        return 100;
    }

    @ConfigItem(
            keyName = "spamMax",
            name = "Maximum",
            description = "Maximum time between clicks",
            position = 9,
            section = spamCombineSettings
    )
    @Range(
            min = 1,
            max = 3000
    )
    default int spamMax() {
        return 200;
    }

}
