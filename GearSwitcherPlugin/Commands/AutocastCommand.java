package com.theplug.GearSwitcherPlugin.Commands;

import com.theplug.PaistiUtils.API.Spells.Spell;
import com.theplug.PaistiUtils.API.Spells.Standard;
import com.theplug.PaistiUtils.API.Utility;
import com.theplug.PaistiUtils.API.Widgets;
import com.theplug.PaistiUtils.Hooks.Hooks;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;

import java.util.Arrays;

@Slf4j
public class AutocastCommand implements GearSwitcherCommand {

    Spell spell;

    public static final String INSTRUCTION_PREFIX = "AUTOCAST";

    AutocastCommand(String spellName) {
        this.spell = Spell.findSpellByName(spellName);
        if (this.spell == null) throw new IllegalArgumentException("Invalid spell name:" + spellName);
    }


    @Override
    public boolean execute() {
        Utility.setAutocastSpell(spell);
        return true;
    }

    @Override
    public String serializeToString() {
        return INSTRUCTION_PREFIX + ":" + spell.getName();
    }

    static public AutocastCommand deserializeFromString(String serializedString) {
        try {
            String command = serializedString.split(":")[0];
            if (!command.equalsIgnoreCase(INSTRUCTION_PREFIX))
                throw new IllegalArgumentException("Received unknown command");
            String spellName = serializedString.split(":")[1];
            return new AutocastCommand(spellName);
        } catch (Exception e) {
            log.error("Failed to deserialize");
            e.printStackTrace();
        }
        return null;
    }
}
