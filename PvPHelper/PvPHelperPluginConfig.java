package com.theplug.PvPHelper;

import net.runelite.client.config.*;

@ConfigGroup("PvPHelperPluginConfig")
public interface PvPHelperPluginConfig extends Config {

    @ConfigSection(
            name = "Item protection settings",
            description = "Item protection settings",
            position = 1,
            closedByDefault = true
    )
    String itemProtectionSettings = "itemProtectionSettings";

    @ConfigItem(
            keyName = "turnOnItemProtectionInWilderness",
            name = "Auto turn on protect item in wilderness",
            description = "Automatically turn on item protection when entering wilderness",
            position = 2,
            section = itemProtectionSettings
    )
    default boolean turnOnItemProtectionInWilderness() {
        return false;
    }

    @ConfigItem(
            keyName = "turnOffItemProtectionInSafeArea",
            name = "Auto turn off protect item in safe zone",
            description = "Automatically turn off item protection when entering safe zone",
            position = 3,
            section = itemProtectionSettings
    )
    default boolean turnOffItemProtectionInSafeArea() {
        return false;
    }

    @ConfigSection(
            name = "Eat settings",
            description = "Eat settings",
            position = 4,
            closedByDefault = true
    )

    String eatSettings = "eatSettings";

    @ConfigItem(
            keyName = "shouldAutoEat",
            name = "Should automatically eat",
            description = "Should automatically eat",
            position = 5,
            section = eatSettings
    )
    default boolean shouldAutoEat() {
        return false;
    }

    @ConfigItem(
            keyName = "disableWhenSpecialAttackEnabled",
            name = "Disable when special attack is enabled",
            description = "Disable when special attack is enabled",
            position = 6,
            section = eatSettings
    )
    default boolean disableWhenSpecialAttackEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "eatingThreshold",
            name = "Eating threshold",
            description = "Desired HP when should the auto eat activate",
            position = 7,
            section = eatSettings
    )
    @Range(
            min = 1,
            max = 99
    )
    default int eatingThreshold() {
        return 50;
    }

    @ConfigItem(
            keyName = "regularFoods",
            name = "Allowed regular foods",
            description = "Regular foods to be eaten. E.g. Anglerfish",
            position = 8,
            section = eatSettings
    )

    default String regularFoods() {
        return "Anglerfish\nBlighted anglerfish\nManta ray\nBlighted manta ray\nShark";
    }

    @ConfigItem(
            keyName = "comboFoods",
            name = "Combo food",
            description = "Combo foods to be eaten. E.g. Cooked karambwan",
            position = 9,
            section = eatSettings
    )

    default String comboFoods() {
        return "Cooked karambwan\nBlighted karambwan";
    }

    @ConfigItem(
            keyName = "shouldUseSaradominBrew",
            name = "Drink saradomin brew",
            description = "Drink brew on top of eating regular food and combo food",
            position = 10,
            section = eatSettings
    )
    default boolean shouldUseSaradominBrew() {
        return false;
    }
}
