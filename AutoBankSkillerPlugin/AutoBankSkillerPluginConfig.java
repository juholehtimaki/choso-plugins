package com.theplug.AutoBankSkillerPlugin;

import net.runelite.client.config.*;

@ConfigGroup("AutoBankSkillerPluginConfig")
public interface AutoBankSkillerPluginConfig extends Config {

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
            keyName = "bankSkillerMetod",
            name = "Bank skiller method",
            description = "Choose bank skiller method",
            position = 2
    )
    default BankSkillerMethod bankSkillerMetod() {
        return BankSkillerMethod.HERB_CLEANING;
    }

    @ConfigSection(
            name = "Herb cleaning settings",
            description = "Herb cleaning settings",
            position = 3,
            closedByDefault = true
    )
    String herbCleaningSettings = "Herb cleaning settings";

    @ConfigItem(
            keyName = "grimyHerbs",
            name = "Grimy herbs",
            description = "Grimy herbs' names to clean, separated by new line",
            position = 1,
            section = herbCleaningSettings
    )
    default String grimyHerbs() {
        return "Grimy guam leaf\nGrimy ranarr weed";
    }

    @ConfigItem(
            keyName = "cleanHerbSleepMin",
            name = "Min sleep time",
            description = "Min sleep time between cleaning herbs",
            position = 1,
            section = herbCleaningSettings
    )
    default int cleanHerbSleepMin() {
        return 50;
    }

    @ConfigItem(
            keyName = "cleanHerbSleepMax",
            name = "Max sleep time",
            description = "Max sleep time between cleaning herbs",
            position = 2,
            section = herbCleaningSettings
    )
    default int cleanHerbSleepMax() {
        return 100;
    }

    @ConfigSection(
            name = "Gem cutting settings",
            description = "Gem cutting settings",
            position = 4,
            closedByDefault = true
    )
    String gemCuttingSettings = "Gem cutting settings";

    @ConfigItem(
            keyName = "uncutGems",
            name = "Uncut gems",
            description = "Uncut gems' names to cut, separated by new line",
            position = 1,
            section = gemCuttingSettings
    )
    default String uncutGems() {
        return "Uncut sapphire\nUncut diamond";
    }

    @ConfigSection(
            name = "Potion making settings",
            description = "Potions settings",
            position = 5,
            closedByDefault = true
    )
    String potionSettings = "potionSettings";

    @ConfigItem(
            keyName = "desiredPotion",
            name = "Potion",
            description = "Potion to make",
            position = 1,
            section = potionSettings
    )
    default BankSkillerPotion desiredPotion() {
        return BankSkillerPotion.GUAM_POTION;
    }

    @ConfigSection(
            name = "Lunar spell settings",
            description = "Lunar spell settings",
            position = 6,
            closedByDefault = true
    )
    String lunarSpellSettings = "lunarSpellSettings";

    @ConfigItem(
            keyName = "desiredLunarSpell",
            name = "Spell",
            description = "Lunar spell to cast",
            position = 1,
            section = lunarSpellSettings
    )
    default BankSkillerLunarSpell desiredLunarSpell() {
        return BankSkillerLunarSpell.REGULAR_PLANK;
    }

    @ConfigSection(
            name = "Crafting settings",
            description = "Crafting settings",
            position = 7,
            closedByDefault = true
    )
    String craftingSettings = "craftingSettings";

    @ConfigItem(
            keyName = "desiredCrafting",
            name = "Crafting",
            description = "Crafting to do",
            position = 1,
            section = craftingSettings
    )
    default BankSkillerCrafting desiredCrafting() {
        return BankSkillerCrafting.WATER_BATTLESTAFF;
    }

    @ConfigSection(
            name = "Fletching settings",
            description = "Fletching settings",
            position = 8,
            closedByDefault = true
    )
    String fletchingSettings = "fletchingSettings";

    @ConfigItem(
            keyName = "desiredFletching",
            name = "Fletching",
            description = "Fletching to do",
            position = 1,
            section = fletchingSettings
    )
    default BankSkillerFletching desiredFletching() {
        return BankSkillerFletching.ARROW_SHAFTS;
    }

    @ConfigSection(
            name = "Spam combine settings",
            description = "Spam combine settings (e.g. darts, bolts)",
            position = 9,
            closedByDefault = true
    )
    String spamCombineSettings = "spamCombineSettings";

    @ConfigItem(
            keyName = "spamCombineFirstItem",
            name = "First item",
            description = "First item's name or ID",
            position = 1,
            section = spamCombineSettings
    )
    default String spamCombineFirstItem() {
        return "Adamant dart tip";
    }

    @ConfigItem(
            keyName = "spamCombineSecondItem",
            name = "Second item",
            description = "First item's name or ID",
            position = 2,
            section = spamCombineSettings
    )
    default String spamCombineSecondItem() {
        return "Feather";
    }

    @ConfigItem(
            keyName = "waitForAnimation",
            name = "Wait for animation",
            description = "Wait for animation to finish before combining items again (e.g. arrows)",
            position = 3,
            section = spamCombineSettings
    )
    default boolean waitForAnimation() {
        return true;
    }

    @ConfigItem(
            keyName = "spamCombineSleepMin",
            name = "Min sleep time",
            description = "Min sleep time between combine clicks",
            position = 4,
            section = spamCombineSettings
    )
    default int spamCombineSleepMin() {
        return 50;
    }

    @ConfigItem(
            keyName = "spamCombineSleepMax",
            name = "Max sleep time",
            description = "Max sleep time between combine clicks",
            position = 5,
            section = spamCombineSettings
    )
    default int spamCombineSleepMax() {
        return 100;
    }

    @ConfigSection(
            name = "Custom item on item settings",
            description = "Custom item on item settings",
            position = 10,
            closedByDefault = true
    )
    String customItemOnItemSettings = "customItemOnItemSettings";

    @ConfigItem(
            keyName = "customFirstItem",
            name = "First item",
            description = "First item's name",
            position = 1,
            section = customItemOnItemSettings
    )
    default String customFirstItem() {
        return "Pestle and mortar";
    }

    @ConfigItem(
            keyName = "customFirstItemCount",
            name = "First item count",
            description = "First item's withdraw count",
            position = 2,
            section = customItemOnItemSettings
    )

    default int customFirstItemCount() {
        return 1;
    }

    @ConfigItem(
            keyName = "customSecondItem",
            name = "Second item",
            description = "Second item's name",
            position = 3,
            section = customItemOnItemSettings
    )
    default String customSecondItem() {
        return "Unicorn horn";
    }

    @ConfigItem(
            keyName = "customSecondItemCount",
            name = "Second item count",
            description = "Second item's withdraw count",
            position = 4,
            section = customItemOnItemSettings
    )

    default int customSecondItemCount() {
        return 27;
    }

    @ConfigItem(
            keyName = "customDontDepositFirstItem",
            name = "Don't deposit first item",
            description = "Don't deposit first item. E.g. always keep pestle and mortar in inventory",
            position = 5,
            section = customItemOnItemSettings
    )
    default boolean customDontDepositFirstItem() {
        return false;
    }

}
