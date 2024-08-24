package com.theplug.AutoStunAlcherPlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.Spells.Standard;

@DontObfuscate
public enum StunAlcherAlchSpell {
    PROGRESSIVE("Progressive", null),
    HIGH_ALCH("High alch", Standard.HIGH_LEVEL_ALCHEMY),
    LOW_ALCH("Low alch", Standard.LOW_LEVEL_ALCHEMY),
    NONE("None", null);
    private final Standard spell;
    private final String name;

    StunAlcherAlchSpell(String name, Standard spell) {
        this.spell = spell;
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public Standard getSpell() {
        return spell;
    }

    public Standard getBestAlchemySpell() {
        if (Standard.HIGH_LEVEL_ALCHEMY.canCast()) {
            return Standard.HIGH_LEVEL_ALCHEMY;
        } else if (Standard.LOW_LEVEL_ALCHEMY.canCast()) {
            return Standard.LOW_LEVEL_ALCHEMY;
        }
        return null;
    }

    public Standard getBestAlchemySpell(int magicLevel) {
        if (Standard.HIGH_LEVEL_ALCHEMY.haveRunesForSpell() && Standard.HIGH_LEVEL_ALCHEMY.canCastOnMagicLevel(magicLevel)) {
            return Standard.HIGH_LEVEL_ALCHEMY;
        } else if (Standard.HIGH_LEVEL_ALCHEMY.haveRunesForSpell() && Standard.LOW_LEVEL_ALCHEMY.canCastOnMagicLevel(magicLevel)) {
            return Standard.LOW_LEVEL_ALCHEMY;
        }
        return null;
    }
}