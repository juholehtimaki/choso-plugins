package com.theplug.AutoNexPlugin;

import com.theplug.DontObfuscate;

@DontObfuscate
public enum KillCountNPC {
    SPIRITUAL_MAGE("Spiritual Mage"),
    BLOOD_REAVER("Blood Reaver");

    final String killCountNpc;

    KillCountNPC(String killCountNpc) {
        this.killCountNpc = killCountNpc;
    }

    @Override
    public String toString() {
        return this.killCountNpc;
    }
}
