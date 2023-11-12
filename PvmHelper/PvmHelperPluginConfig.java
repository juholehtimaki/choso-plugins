package com.theplug.PvmHelper;

import net.runelite.client.config.*;

@ConfigGroup("PvmHelperPluginConfig")
public interface PvmHelperPluginConfig extends Config {
    @ConfigSection(
            name = "Vardorvis settings",
            description = "Vardorvis settings",
            position = 1,
            closedByDefault = false
    )
    String vardorvisSettings = "vardorvisSettings";

    @ConfigItem(
            keyName = "autoSpores",
            name = "Auto spores",
            description = "Automatically do the spores during the stangle",
            position = 1,
            section = vardorvisSettings
    )
    default boolean autoSpores() {
        return true;
    }

    @ConfigSection(
            name = "Leviathan settings",
            description = "Leviathan settings",
            position = 2,
            closedByDefault = false
    )
    String leviathanSettings = "leviathanSettings";
    @ConfigItem(
            keyName = "shadowBarrageHotkey",
            name = "Shadow barrage hotkey",
            description = "Hotkey to cast shadow barrage on Leviathan",
            position = 2,
            section = leviathanSettings
    )
    default Keybind shadowBarrageHotkey() {
        return null;
    }

    @ConfigSection(
            name = "Whisperer settings",
            description = "Whisperer settings",
            position = 3,
            closedByDefault = false
    )
    String whispererSettings = "whispererSettings";
    @ConfigItem(
            keyName = "iceBarrageHotkey",
            name = "Ice barrage hotkey",
            description = "Hotkey to cast ice barrage on Whisperer",
            position = 2,
            section = whispererSettings
    )
    default Keybind iceBarrageHotkey() {
        return null;
    }

    @ConfigSection(
            name = "Muspah settings",
            description = "Muspah settings",
            position = 4,
            closedByDefault = false
    )
    String muspahSettings = "muspahSettings";
    @ConfigItem(
            keyName = "usingMage",
            name = "Using mage",
            description = "Using mage determines whether plugin uses mage pray in melee phase",
            position = 1,
            section = muspahSettings
    )
    default boolean usingMage() {
        return false;
    }
}
