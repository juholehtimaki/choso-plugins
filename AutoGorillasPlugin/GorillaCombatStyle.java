package com.theplug.AutoGorillasPlugin;

import com.theplug.DontObfuscate;
import lombok.Getter;
import net.runelite.api.HeadIcon;

@DontObfuscate
public enum GorillaCombatStyle {
    MELEE("Melee", HeadIcon.MELEE),
    RANGE("Range", HeadIcon.RANGED),
    MAGE("Mage", HeadIcon.MAGIC);

    private final String methodName;

    @Getter
    public final HeadIcon protectedByHeadIcon;

    @Override
    public String toString() {
        return this.methodName;
    }

    GorillaCombatStyle(String methodName, HeadIcon headIcon) {
        this.methodName = methodName;
        this.protectedByHeadIcon = headIcon;
    }
}
