package com.theplug.AutoHopperPlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoHopperPluginConfig")
public interface AutoHopperPluginConfig extends Config {
    @ConfigItem(
            keyName = "hopOnlyIfPlayerCanAttack",
            name = "Hop only if player can attack",
            description = "Hop only if player can attack. E.g. only active in wilderness or pvp worlds",
            position = 1
    )
    default boolean hopOnlyIfPlayerCanAttack() {
        return true;
    }

    @ConfigItem(
            keyName = "hopOnlyIfPlayerHasWeapon",
            name = "Hop only if player has a weapon",
            description = "Hop only if player has a weapon. This should only be used in wilderness",
            position = 2
    )
    default boolean hopOnlyIfPlayerHasWeapon() {
        return true;
    }

    @ConfigItem(
            keyName = "playerRadius",
            name = "Radius for player detection",
            description = "Hop only if player is within this radius from the local player",
            position = 3
    )
    default int playerRadius() {
        return 14;
    }

    @ConfigSection(
            name = "Debug settings",
            description = "Debug settings for developing",
            position = 100,
            closedByDefault = true
    )
    String debugSettings = "debugSettings";

    @ConfigItem(
            keyName = "drawMatchingPlayers",
            name = "Draw matching players",
            description = "Draw matching players",
            position = 1,
            section = debugSettings
    )
    default boolean drawMatchingPlayers() {
        return false;
    }

    @ConfigItem(
            keyName = "isHoppingDisabled",
            name = "Disable hopping",
            description = "Disable hopping. Should be only used for debugging purposes",
            position = 2,
            section = debugSettings
    )
    default boolean isHoppingDisabled() {
        return false;
    }
}
