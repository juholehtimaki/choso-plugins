package com.theplug.AutoStunAlcherPlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoStunAlcherPluginConfig")
public interface AutoStunAlcherPluginConfig extends Config {

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
            name = "Alch settings",
            description = "Alch settings",
            position = 2,
            closedByDefault = false
    )
    String alchSettings = "Alch settings";

    @ConfigItem(
            keyName = "alchSpell",
            name = "Alch spell",
            description = "Alch spell to use. Progressive uses the best possible spell and None disables alching.",
            position = 1,
            section = alchSettings
    )

    default StunAlcherAlchSpell alchSpell() {
        return StunAlcherAlchSpell.PROGRESSIVE;
    }

    @ConfigItem(
            keyName = "alchItems",
            name = "Alch item",
            description = "Item names or IDs to alch",
            position = 2,
            section = alchSettings
    )

    default String alchItems() {
        return "Rune arrow";
    }

    @ConfigSection(
            name = "Stun settings",
            description = "Stun settings",
            position = 3,
            closedByDefault = false
    )
    String stunSettings = "Stun settings";

    @ConfigItem(
            keyName = "stunSpell",
            name = "Stun spell",
            description = "Stun spell to use. Progressive uses the best possible spell and None disables stunning.",
            position = 1,
            section = stunSettings
    )

    default StunAlcherStunSpell stunSpell() {
        return StunAlcherStunSpell.PROGRESSIVE;
    }

    @ConfigItem(
            keyName = "stunTarget",
            name = "Stun target",
            description = "NPC name to stun",
            position = 2,
            section = stunSettings
    )

    default String stunTarget() {
        return "Grizzly bear";
    }

    @ConfigItem(
            keyName = "elementalBalance",
            name = "Elemental balance",
            description = "Enter house and use stun spells on elemental balance. Make sure to have doors closed or removed and house not in building mode.",
            position = 3,
            section = stunSettings
    )

    default boolean elementalBalance() {
        return false;
    }

    @ConfigItem(
            keyName = "randomMiniAfkChance",
            name = "Random mini afk chance",
            description = "% Chance between spells to afk for a short duration",
            position = 4
    )
    @Range(
            min = 0,
            max = 80
    )
    default int randomMiniAfkChance() {
        return 0;
    }

    @ConfigItem(
            keyName = "hopOnPlayerNearby",
            name = "Hop on nearby player",
            description = "Hop if players are nearby",
            position = 5
    )
    default boolean hopOnPlayerNearby() {
        return false;
    }

    @ConfigItem(
            keyName = "targetLevel",
            name = "Targeted magic level",
            description = "Level to reach before stopping",
            position = 6
    )

    default int targetLevel() {
        return 99;
    }
}
