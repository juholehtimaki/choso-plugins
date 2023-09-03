package com.PaistiPlugins.GearSwitcherPlugin.Commands;

import com.PaistiPlugins.PaistiUtils.API.Equipment;
import com.PaistiPlugins.PaistiUtils.API.Interaction;
import com.PaistiPlugins.PaistiUtils.API.Inventory;
import com.PaistiPlugins.PaistiUtils.API.Utility;
import lombok.extern.slf4j.Slf4j;

@Slf4j


public class EquipCommand implements GearSwitcherCommand {
    String itemIdOrName;

    public static final String INSTRUCTION_PREFIX = "E";

    EquipCommand(String itemIdOrName) {
        this.itemIdOrName = itemIdOrName;
    }

    @Override
    public boolean execute() {
        return Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            try {
                var isOnlyDigits = itemIdOrName.matches("\\d+");
                if (isOnlyDigits) {
                    var itemId = Integer.parseInt(itemIdOrName);
                    var itemToEquip = Inventory.search().withId(itemId).first();
                    if (Equipment.search().withId(itemId).first().isPresent()) return false;
                    if (itemToEquip.isPresent()) {
                        return Interaction.clickWidget(itemToEquip.get(), "Wield", "Wear", "Equip");
                    }
                    return false;
                } else {
                    var itemToEquip = Inventory.search().matchesWildCardNoCase(itemIdOrName).first();
                    if (Equipment.search().matchesWildCardNoCase(itemIdOrName).first().isPresent()) return false;
                    if (itemToEquip.isPresent()) {
                        return Interaction.clickWidget(itemToEquip.get(), "Wield", "Wear", "Equip");
                    }
                    return false;
                }
            } catch (Exception e) {
                log.error("Error in equip instruction: " + serializeToString());
                e.printStackTrace();
                return false;
            }
        }));
    }

    @Override
    public String serializeToString() {
        return INSTRUCTION_PREFIX + ":" + itemIdOrName;
    }

    public static EquipCommand deserializeFromString(String serializedString) {
        try {
            String command = serializedString.split(":")[0];
            if (!command.equalsIgnoreCase(INSTRUCTION_PREFIX))
                throw new IllegalArgumentException("Received unknown command");
            String itemIdOrName = serializedString.split(":")[1];
            return new EquipCommand(itemIdOrName);
        } catch (Exception e) {
            log.error("Failed to deserialize");
        }
        return null;
    }
}
