package com.theplug.GearSwitcherPlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;

import java.util.Arrays;

@DontObfuscate
public enum Trigger {
    RANGE_OFFENSIVE_PRAYER("Offensive range pray", PPrayer.RIGOUR, PPrayer.HAWK_EYE, PPrayer.SHARP_EYE, PPrayer.EAGLE_EYE),
    MELEE_OFFENSIVE_PRAYER("Offensive melee pray", PPrayer.PIETY, PPrayer.CHIVALRY, PPrayer.ULTIMATE_STRENGTH, PPrayer.SUPERHUMAN_STRENGTH, PPrayer.BURST_OF_STRENGTH),
    MAGIC_OFFENSIVE_PRAYER("Offensive mage pray", PPrayer.AUGURY, PPrayer.MYSTIC_MIGHT, PPrayer.MYSTIC_WILL, PPrayer.MYSTIC_LORE),
    NONE("None");
    private final PPrayer[] triggerPrayers;
    private final String name;

    Trigger(String name, PPrayer... triggerPrayers) {
        this.triggerPrayers = triggerPrayers;
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public boolean shouldTriggerOnMenuOptionClicked(MenuOptionClicked menuOptionClicked) {
        if (triggerPrayers == null) return false;
        if (menuOptionClicked.getMenuAction() != MenuAction.CC_OP) return false;
        return Arrays.stream(triggerPrayers).anyMatch((prayer) -> menuOptionClicked.getParam1() == prayer.getWidgetPackedId() && !prayer.isActive());
    }
}
