package com.example.PvPHelperPlugin.Commands;

import com.example.PaistiUtils.API.Prayer.PPrayer;
import com.example.PaistiUtils.API.Spells.Lunar;
import com.example.PaistiUtils.API.Spells.Spell;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public class SpellCommand implements PvpHelperCommand {

    Spell spell;

    public static final String INSTRUCTION_PREFIX = "SPELL";

    SpellCommand(String spellName) {
        this.spell = Arrays.stream(Lunar.values()).filter(f -> f.name().equalsIgnoreCase(spellName)).findFirst().orElse(null);
        if(this.spell == null) throw new IllegalArgumentException("Invalid spell name:" + spellName);
    }
    @Override
    public boolean execute() {
        return spell.cast();
    }

    @Override
    public String serializeToString() {
        return INSTRUCTION_PREFIX + ":" + spell.getName();
    }

    static public SpellCommand deserializeFromString(String serializedString) {
        try {
            String command = serializedString.split(":")[0];
            if (!command.equalsIgnoreCase(INSTRUCTION_PREFIX)) throw new IllegalArgumentException("Received unknown command");
            String spellName = serializedString.split(":")[1];
            return new SpellCommand(spellName);
        } catch(Exception e) {
            log.error("Failed to deserialize");
        }
        return null;
    }
}
