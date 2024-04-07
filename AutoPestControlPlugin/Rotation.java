package com.theplug.AutoPestControlPlugin;

import com.theplug.DontObfuscate;

import static com.theplug.AutoPestControlPlugin.Portal.*;

@DontObfuscate
enum Rotation {
    PBYR(PURPLE, BLUE, YELLOW, RED),
    PYBR(PURPLE, YELLOW, BLUE, RED),
    BRYP(BLUE, RED, YELLOW, PURPLE),
    BPRY(BLUE, PURPLE, RED, YELLOW),
    YRPB(YELLOW, RED, PURPLE, BLUE),
    YPRB(YELLOW, PURPLE, RED, BLUE);

    private final Portal[] portals;

    Rotation(Portal first, Portal second, Portal third, Portal fourth) {
        portals = new Portal[]
                {
                        first, second, third, fourth
                };
    }

    public Portal getPortal(int index) {
        if (index < 0 || index >= portals.length) {
            return null;
        }

        return portals[index];
    }
}
