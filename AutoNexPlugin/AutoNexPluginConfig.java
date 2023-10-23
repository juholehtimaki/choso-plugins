package com.theplug.AutoNexPlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoNexPluginConfig")
public interface AutoNexPluginConfig extends Config {
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
            keyName = "gearLoadout",
            name = "Gear loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 2
    )

    default String gearLoadout() {
        return "";
    }

    @ConfigSection(
            name = "Fight settings",
            description = "Restock settings",
            position = 4,
            closedByDefault = true
    )
    String fightSettings = "Fight settings";

    @ConfigItem(
            keyName = "useSpecialAttack",
            name = "Use special attack",
            description = "Determines whether the plugin should use special attack",
            position = 5,
            section = fightSettings
    )
    default boolean useSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "specEnergyMinimum",
            name = "Minimum spec %",
            description = "Minimum spec energy % to use special attack of the spec weapon (ACB = 50%, ZCB = 75% etc.)",
            position = 6,
            section = fightSettings
    )
    @Range(
            min = 25,
            max = 100
    )
    default int specEnergyMinimum() {
        return 75;
    }

    @ConfigItem(
            keyName = "specHpThreshold",
            name = "Minimum spec HP",
            description = "Nex's minimum health when to spec (e.g. only speccing when HP is above 300)",
            position = 7,
            section = fightSettings
    )
    @Range(
            min = 1,
            max = 3400
    )
    default int specHpThreshold() {
        return 300;
    }

    @ConfigSection(
            name = "Restock settings",
            description = "Restock settings",
            position = 8,
            closedByDefault = true
    )
    String restockSettings = "restockSettings";


    @ConfigItem(
            keyName = "smartRestock",
            name = "Smart restocking on low supplies",
            description = "Determines whether the plugin should restock. If not enabled, restocking when reaching 40 kc",
            position = 9,
            section = restockSettings
    )
    default boolean smartRestock() {
        return false;
    }

    @ConfigItem(
            keyName = "brewMinimum",
            name = "Minimum brew sip count",
            description = "Minimum brew sip count before exiting",
            position = 10,
            section = restockSettings
    )
    @Range(
            min = 0,
            max = 100
    )
    default int brewMinimum() {
        return 12;
    }

    @ConfigItem(
            keyName = "restoreMinimum",
            name = "Minimum restore sip count",
            description = "Minimum super restore sip count before exiting",
            position = 11,
            section = restockSettings
    )
    @Range(
            min = 0,
            max = 100
    )
    default int restoreMinimum() {
        return 12;
    }

    @ConfigSection(
            name = "Kill count settings",
            description = "Kill count settings",
            position = 12,
            closedByDefault = true
    )
    String killCountSettings = "killCountSettings";

    @ConfigItem(
            keyName = "selectedNpc",
            name = "Selected NPC",
            description = "Select the preferred NPC to kill when getting kill count",
            position = 13,
            section = killCountSettings
    )
    default KillCountNPC selectedNpc() {
        return KillCountNPC.BLOOD_REAVER;
    }

    @ConfigSection(
            name = "Other settings",
            description = "Other settings",
            position = 14,
            closedByDefault = true
    )
    String otherSettings = "otherSettings";

    @ConfigItem(
            keyName = "shouldRandomizeOptimalTile",
            name = "Randomize walking",
            description = "Determines whether the plugin should randomize walk",
            position = 15,
            section = otherSettings
    )
    default boolean shouldRandomizeOptimalTile() {
        return false;
    }

    @ConfigItem(
            keyName = "waitSpot",
            name = "Wait spot",
            description = "Determines the nex spawn waiting point",
            position = 16,
            section = otherSettings
    )
    default WaitSpot waitSpot() {
        return WaitSpot.EDGE_OPTIMAL;
    }

    @ConfigItem(
            keyName = "assistMode",
            name = "Assist mode",
            description = "Assist mode only helps with prayers, rest is up to you",
            position = 17,
            section = otherSettings
    )
    default boolean assistMode() {
        return false;
    }

    @ConfigItem(
            keyName = "allowDrag",
            name = "Allow attack to drag you",
            description = "This should only be enabled when using weapon with 10 attack range",
            position = 18,
            section = otherSettings
    )
    default boolean allowDrag() {
        return false;
    }

    @ConfigItem(
            keyName = "drawNpcs",
            name = "Draw NPCs",
            description = "Draw NPCs and indicate whether player is in range with colors",
            position = 19,
            section = otherSettings
    )
    default boolean drawNpcs() {
        return false;
    }

    @ConfigItem(
            keyName = "onlyMassWorlds",
            name = "Only mass worlds",
            description = "Only allow plugin to enter Nex room in mass worlds (505 and 332)",
            position = 20,
            section = otherSettings
    )
    default boolean onlyMassWorlds() {
        return true;
    }

    @ConfigItem(
            keyName = "lootOnlyMine",
            name = "Loot only mine",
            description = "Attempt to only loot own items. This is mainly for Iron mans. RuneLite API sucks so toggling this on may cause some loot to be left on the ground but uniques will be always looted",
            position = 21,
            section = otherSettings
    )
    default boolean lootOnlyMine() {
        return false;
    }

    @ConfigItem(
            keyName = "useRemedy",
            name = "Use Menaphite Remedies",
            description = "Restore reduced stats with Menaphite remedies instead of Super restores",
            position = 22,
            section = otherSettings
    )
    default boolean useRemedy() {
        return false;
    }

    @ConfigItem(
            keyName = "shouldExecute",
            name = "Should execute",
            description = "Determines whether the plugin should run. Start key toggles this.",
            position = 23,
            section = otherSettings,
            hidden = true
    )
    default boolean shouldExecute() {
        return false;
    }
}
