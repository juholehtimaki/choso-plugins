package com.theplug.VardorvisPlugin;

import net.runelite.client.config.*;

@ConfigGroup("VardorvisPluginConfig")
public interface VardorvisPluginConfig extends Config {
    @ConfigItem(
            keyName = "gearLoadout",
            name = "Gear loadout",
            description = "Setup your inventory for the plugin to use, and copy loadout string here by right clicking your inventory icon",
            position = 4
    )
    default String gearLoadout() {
        return "";
    }

    @ConfigSection(
            name = "Banking settings",
            description = "Banking settings",
            position = 10,
            closedByDefault = false
    )
    String bankingSettings = "bankingSettings";

    @ConfigItem(
            keyName = "bankingMethod",
            name = "Banking method",
            description = "Select the preferred banking method to use",
            position = 1,
            section = bankingSettings
    )
    default BankingMethod bankingMethod() {
        return BankingMethod.HOUSE;
    }


    @ConfigItem(
            keyName = "bankUnderHpAmount",
            name = "Bank if total hp <",
            description = "After kills, bank if hp + potential hp from food in inventory is less than this amount." +
                    " Set this to a hitpoint amount you feel you need to kill Vardorvis",
            position = 5,
            section = bankingSettings
    )
    @Range(
            min = 0,
            max = 400
    )
    default int bankUnderHpAmount() {
        return 180;
    }

    @ConfigItem(
            keyName = "bankUnderPrayerAmount",
            name = "Bank if prayer <",
            description = "After kills, bank if total prayer points including inventory potions is less than this amount." +
                    " Set this to a prayer point amount you feel you need to kill Vardorvis",
            position = 10,
            section = bankingSettings
    )
    @Range(
            min = 0,
            max = 200
    )
    default int bankUnderPrayerAmount() {
        return 90;
    }

    @ConfigItem(
            keyName = "bankUnderBoostDoseAmount",
            name = "Bank if boost doses <",
            description = "After kills, bank if boosts (Super combat potion, Bastion potion etc.) doses is below this amount." +
                    " Set this to a boost potion dose amount you feel you need to kill Vardorvis",
            position = 25,
            section = bankingSettings
    )
    @Range(
            min = 0,
            max = 4
    )
    default int bankUnderBoostDoseAmount() {
        return 0;
    }


    @ConfigSection(
            name = "Fight settings",
            description = "Fight settings",
            position = 20,
            closedByDefault = false
    )
    String fightSettings = "fightSettings";

    @ConfigItem(
            keyName = "drinkPotionsBelowBoost",
            name = "Drink potions below boost",
            description = "Drink skill boosting potions if the current boost amount is below this value",
            position = 10,
            section = fightSettings
    )
    @Range(
            min = 1,
            max = 15
    )
    default int drinkPotionsBelowBoost() {
        return 9;
    }
    @ConfigItem(
            keyName = "selectedThrall",
            name = "Selected thrall",
            description = "Thrall you wish to summon when fighting Vardorvis",
            position = 10,
            section = fightSettings
    )
    default Thrall selectedThrall() {
        return Thrall.NONE;
    }

    @ConfigItem(
            keyName = "useDeathCharge",
            name = "Use Death Charge",
            description = "Use death charge when Vardorvis is low HP",
            position = 10,
            section = fightSettings
    )
    default boolean useDeathCharge() {
        return false;
    }

    @ConfigItem(
            keyName = "soulreaperAxeThreshold",
            name = "Soulreaper axe HP",
            description = "Soulreaper axe special will be used when Vardorvis is below this HP",
            position = 10,
            section = fightSettings
    )
    @Range(
            min = 0,
            max = 1400
    )
    default int soulreaperAxeThreshold() {
        return 50;
    }


    @ConfigSection(
            name = "Special attack settings",
            description = "Special attack settings",
            position = 40,
            closedByDefault = false
    )
    String specSettings = "specSettings";

    @ConfigItem(
            keyName = "specEquipmentString",
            name = "Spec equipment",
            description = "Wear your special attack switch equipment and right click your equipment icon to copy-paste the equipment string and put it here",
            position = 1,
            section = specSettings
    )
    default String specEquipmentString() {
        return "";
    }

    @ConfigItem(
            keyName = "useSpecialAttack",
            name = "Use special attack",
            description = "Use special attack during fight",
            position = 2,
            section = specSettings
    )
    default boolean useSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "twoHandedSpecWeapon",
            name = "Two-handed spec weapon",
            description = "Modifies the behavior of looting & speccing to account for the fact that you need an inventory slot to unequip your shield slot item",
            position = 3,
            section = specSettings
    )
    default boolean twoHandedSpecWeapon() {
        return true;
    }

    @ConfigItem(
            keyName = "specEnergyMinimum",
            name = "Minimum spec %",
            description = "Minimum spec energy % to use special attack of the spec weapon",
            position = 5,
            section = specSettings
    )
    @Range(
            min = 25,
            max = 100
    )
    default int specEnergyMinimum() {
        return 100;
    }

    @ConfigItem(
            keyName = "specHpMinimum",
            name = "Minimum HP",
            description = "Minimum Vardorvis HP to use special attack of the spec weapon",
            position = 6,
            section = specSettings
    )
    @Range(
            min = 0,
            max = 1400
    )
    default int specHpMinimum() {
        return 100;
    }

    @ConfigItem(
            keyName = "specHpMaximum",
            name = "Maximum HP",
            description = "Maximum Vardorvis HP to use special attack of the spec weapon",
            position = 7,
            section = specSettings
    )
    @Range(
            min = 0,
            max = 1400
    )
    default int specHpMaximum() {
        return 700;
    }

    @ConfigItem(
            keyName = "startHotkey",
            name = "Start hotkey",
            description = "Hotkey to start the plugin",
            position = 55
    )
    default Keybind startHotkey() {
        return null;
    }


    @ConfigSection(
            name = "Debug settings",
            description = "Debug settings",
            position = 100,
            closedByDefault = true
    )
    String debugSettings = "debugSettings";

    @ConfigItem(
            keyName = "drawDangerousTiles",
            name = "Draw dangerous tiles",
            description = "Toggles on drawing dangerous tiles",
            position = 3,
            section = debugSettings
    )
    default boolean drawDangerousTiles() {
        return false;
    }
    @ConfigItem(
            keyName = "drawVardorvis",
            name = "Draw Vardorvis",
            description = "Toggles on drawing Vardorvis true tile",
            position = 4,
            section = debugSettings
    )
    default boolean drawVardorvis() {
        return false;
    }
    @ConfigItem(
            keyName = "drawAxes",
            name = "Draw axes",
            description = "Toggles on drawing axes' true tiles",
            position = 4,
            section = debugSettings
    )
    default boolean drawAxes() {
        return false;
    }

    @ConfigItem(
            keyName = "drawTendrils",
            name = "Draw tendrils",
            description = "Toggles on drawing tendrils' true tiles",
            position = 4,
            section = debugSettings
    )
    default boolean drawTendrils() {
        return false;
    }

    @ConfigItem(
            keyName = "awakenedMode",
            name = "Awakened mode",
            description = "Toggles on awakened mode",
            position = 5,
            section = debugSettings
    )
    default boolean awakenedMode() {
        return false;
    }
}
