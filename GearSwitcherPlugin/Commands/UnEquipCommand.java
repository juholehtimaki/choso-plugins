package com.PaistiPlugins.GearSwitcherPlugin.Commands;

import com.PaistiPlugins.PaistiUtils.API.Equipment;
import com.PaistiPlugins.PaistiUtils.API.Interaction;
import com.PaistiPlugins.PaistiUtils.API.Utility;
import lombok.extern.slf4j.Slf4j;

@Slf4j


public class UnEquipCommand implements GearSwitcherCommand {
    String itemIdOrName;

    public static final String INSTRUCTION_PREFIX = "UE";

    UnEquipCommand(String itemIdOrName) {
        this.itemIdOrName = itemIdOrName;
    }

    @Override
    public boolean execute() {
        return Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            try {
                var isOnlyDigits = itemIdOrName.matches("\\d+");
                if (isOnlyDigits) {
                    var itemId = Integer.parseInt(itemIdOrName);
                    var itemToUnEquip = Equipment.search().withId(itemId).first();
                    if (itemToUnEquip.isPresent()) {
                        return Interaction.clickWidget(itemToUnEquip.get(), "Remove");
                    }
                    return false;
                } else {
                    var itemToUnEquip = Equipment.search().matchesWildCardNoCase(itemIdOrName).first();
                    if (itemToUnEquip.isPresent()) {
                        return Interaction.clickWidget(itemToUnEquip.get(), "Remove");
                    }
                    return false;
                }
            } catch (Exception e) {
                log.error("Error in unequip instruction: " + serializeToString());
                e.printStackTrace();
                return false;
            }
        }));
    }

    @Override
    public String serializeToString() {
        return INSTRUCTION_PREFIX + ":" + itemIdOrName;
    }

    public static UnEquipCommand deserializeFromString(String serializedString) {
        try {
            String command = serializedString.split(":")[0];
            if (!command.equalsIgnoreCase(INSTRUCTION_PREFIX))
                throw new IllegalArgumentException("Received unknown command");
            String itemIdOrName = serializedString.split(":")[1];
            return new UnEquipCommand(itemIdOrName);
        } catch (Exception e) {
            log.error("Failed to deserialize");
        }
        return null;
    }
}
