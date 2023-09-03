package com.PaistiPlugins.GearSwitcherPlugin.Commands;

import com.PaistiPlugins.GearSwitcherPlugin.GearSwitcherPlugin;
import com.PaistiPlugins.PaistiUtils.API.Spells.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class SpellCommand implements GearSwitcherCommand {

    Spell spell;

    public static final String INSTRUCTION_PREFIX = "SPELL";

    private static final List<Spell> targetedSpells = new ArrayList<>() {
        {
            add(Standard.FIRE_SURGE);
            add(Standard.FIRE_WAVE);
            add(Ancient.BLOOD_BARRAGE);
            add(Ancient.ICE_BARRAGE);
            add(Ancient.SHADOW_BARRAGE);
            add(Ancient.SMOKE_BARRAGE);
            add(Ancient.BLOOD_BURST);
            add(Ancient.ICE_BURST);
            add(Ancient.SHADOW_BURST);
            add(Ancient.SMOKE_BURST);
            add(Ancient.BLOOD_BLITZ);
            add(Ancient.ICE_BLITZ);
            add(Ancient.SHADOW_BLITZ);
            add(Ancient.SMOKE_BLITZ);
        }
    };
    private static final List<Spell> allSpells = new ArrayList<>() {
        {
            addAll(Arrays.asList(Lunar.values()));
            addAll(Arrays.asList(Standard.values()));
            addAll(Arrays.asList(Ancient.values()));
            addAll(Arrays.asList(Necromancy.values()));
        }
    };

    SpellCommand(String spellName) {
        this.spell = allSpells.stream().filter(f -> f.getName().equalsIgnoreCase(spellName)).findFirst().orElse(null);
        if (this.spell == null) throw new IllegalArgumentException("Invalid spell name:" + spellName);
    }

    @Override
    public boolean execute() {
        boolean isTargetedSpell = targetedSpells.stream().anyMatch(f -> f == this.spell);
        if (isTargetedSpell) {
            var lastTarget = GearSwitcherPlugin.getLastTargetInteractedWith();
            if (lastTarget != null) {
                return spell.castOnActor(lastTarget);
            }
            return false;
        }
        return spell.cast();
    }

    @Override
    public String serializeToString() {
        return INSTRUCTION_PREFIX + ":" + spell.getName();
    }

    static public SpellCommand deserializeFromString(String serializedString) {
        try {
            String command = serializedString.split(":")[0];
            if (!command.equalsIgnoreCase(INSTRUCTION_PREFIX))
                throw new IllegalArgumentException("Received unknown command");
            String spellName = serializedString.split(":")[1];
            return new SpellCommand(spellName);
        } catch (Exception e) {
            log.error("Failed to deserialize");
        }
        return null;
    }
}
