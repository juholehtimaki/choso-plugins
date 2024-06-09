package com.theplug.AutoBlastFurnacePlugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("AutoBlastFurnacePluginConfig")
public interface AutoBlastFurnacePluginConfig extends Config {

    @ConfigItem(
            keyName = "method",
            name = "Method",
            description = "Select training method",
            position = 20
    )
    default BlastFurnaceMethod method() {
        return BlastFurnaceMethod.GOLD_BARS;
    }

    @ConfigItem(
            keyName = "autoGear",
            name = "Auto gear",
            description = "Withdraw optimal gear automatically for the method",
            position = 21
    )
    default boolean autoGear() {
        return true;
    }


    @ConfigItem(
            keyName = "startHotkey",
            name = "Start hotkey",
            description = "Hotkey to start the plugin",
            position = 25
    )
    default Keybind startHotkey() {
        return null;
    }

}
