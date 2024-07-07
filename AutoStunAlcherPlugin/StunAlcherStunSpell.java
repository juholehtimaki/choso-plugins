package com.theplug.AutoStunAlcherPlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.Spells.Standard;

@DontObfuscate
public enum StunAlcherStunSpell {
    PROGRESSIVE("Progressive", null),
    CONFUSE("Confuse", Standard.CONFUSE),
    WEAKEN("Weaken", Standard.WEAKEN),
    CURSE("Curse", Standard.CURSE),
    VULNERABILITY("Vulnerability", Standard.VULNERABILITY),
    ENFEEBLE("Enfeeble", Standard.ENFEEBLE),
    STUN("Stun", Standard.STUN),
    NONE("None", null);
    private final Standard spell;
    private final String name;

    StunAlcherStunSpell(String name, Standard spell) {
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

    public Standard getBestStunSpell() {
        if (Standard.STUN.canCast()) {
            return Standard.STUN;
        } else if (Standard.ENFEEBLE.canCast()) {
            return Standard.ENFEEBLE;
        } else if (Standard.VULNERABILITY.canCast()) {
            return Standard.VULNERABILITY;
        } else if (Standard.CURSE.canCast()) {
            return Standard.CURSE;
        } else if (Standard.WEAKEN.canCast()) {
            return Standard.WEAKEN;
        } else if (Standard.CONFUSE.canCast()) {
            return Standard.CONFUSE;
        }
        return null;
    }
}