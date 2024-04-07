package com.theplug.ScurriusPlugin;

import com.theplug.DontObfuscate;

@DontObfuscate
public enum CombatStyle {
    MELEE("Melee"),
    RANGE("Range"),
    MAGE("Mage");

    private final String methodName;

    @Override
    public String toString() {
        return this.methodName;
    }

    CombatStyle(String methodName) {
        this.methodName = methodName;
    }
}
