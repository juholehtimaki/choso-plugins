package com.theplug.AutoPrayFlickerPlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoPrayFlickerPluginConfig")
public interface AutoPrayFlickerPluginConfig extends Config {

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
            keyName = "autoFlickOffensive",
            name = "Auto flick offensive prayers",
            description = "Automatically flick offensive prayers",
            position = 2
    )
    default boolean autoFlickOffensive() {
        return true;
    }

    @ConfigSection(
            name = "Defensive prayer settings",
            description = "Defensive prayer settings",
            position = 3,
            closedByDefault = true
    )
    String defensiveSettings = "defensiveSettings";

    @ConfigItem(
            keyName = "autoFlickDefensive",
            name = "Auto flick defensive prayers",
            description = "Automatically flick defensive prayers",
            position = 1,
            section = defensiveSettings,
            hidden = true
    )
    default boolean autoFlickDefensive() {
        return false;
    }

    @ConfigItem(
            keyName = "predictDefensive",
            name = "Predict defensive",
            description = "Predict toggling defensive prayer when NPC is nearby and may attack. Otherwise only pray when interacting with NPC.",
            position = 2,
            section = defensiveSettings,
            hidden = true
    )
    default boolean predictDefensive() {
        return true;
    }

    @ConfigItem(
            keyName = "npcData",
            name = "NPC data",
            description = "NPC data to flick defensive against.\n"
                    + "Format should be npcId:attackIntervalInTicks:attackRange:combatStyle:prayPriority:attackAnimationsSeperatedByComma\n"
                    + "Example: '11917:4:1:MELEE:1:386'",
            position = 3,
            section = defensiveSettings,
            hidden = true
    )
    default String npcData() {
        return "11917:4:1:MELEE:1:386";
    }

    @ConfigSection(
            name = "Debug settings",
            description = "Debug settings for developing",
            position = 100,
            closedByDefault = true
    )
    String debugSettings = "debugSettings";

    @ConfigItem(
            keyName = "drawNpcs",
            name = "Draw matching NPCs",
            description = "Draw matching NPCs according to the given NPC data",
            position = 1,
            section = debugSettings,
            hidden = true
    )
    default boolean drawNpcs() {
        return false;
    }

    @ConfigItem(
            keyName = "drawOverlay",
            name = "Draw overlay",
            description = "Draw overlay",
            position = 2,
            section = debugSettings
    )
    default boolean drawOverlay() {
        return true;
    }
}
