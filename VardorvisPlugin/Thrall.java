package com.theplug.VardorvisPlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.Spells.Necromancy;

@DontObfuscate
public enum Thrall {
    SKELETON("Greater skeleton", Necromancy.RESURRECT_GREATER_SKELETON),
    ZOMBIE("Greater zombie", Necromancy.RESURRECT_GREATER_ZOMBIE),
    GHOST("Greater ghost", Necromancy.RESURRECT_GREATER_GHOST),
    NONE("None", null);
    private final Necromancy spell;
    private final String name;

    Thrall(String name, Necromancy spell) {
        this.spell = spell;
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public Necromancy getThrall(){
        return spell;
    }

}
