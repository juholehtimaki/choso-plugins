package com.theplug.AutoPestControlPlugin;

import com.theplug.DontObfuscate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@AllArgsConstructor
@Getter
@DontObfuscate
public enum Boat {
    VETERAN(25632, new WorldPoint(2638, 2653, 0)),
    INTERMEDIATE(25631, new WorldPoint(2644, 2644, 0)),
    NOVICE(14315, new WorldPoint(2657, 2639, 0));

    private final int id;
    private final WorldPoint wp;
}
