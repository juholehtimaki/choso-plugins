package com.theplug.AutoMotherlodeMinePlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.Geometry;
import com.theplug.PaistiUtils.API.Walking;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@Getter
@DontObfuscate
@AllArgsConstructor
public enum Area {
    NW_AREA(
            "NW area",
            new WorldPoint(3722, 5685, 0),
            new Geometry.CuboidArea(
                    new Geometry.Cuboid(new WorldPoint(3716, 5687, 0), new WorldPoint(3724, 5659, 0))
            )),
    UPPER_AREA(
            "Upper area",
            new WorldPoint(3755, 5675, 0),
            new Geometry.CuboidArea(
                    new Geometry.Cuboid(new WorldPoint(3745, 5685, 0), new WorldPoint(3764, 5681, 0)),
                    new Geometry.Cuboid(new WorldPoint(3751, 5680, 0), new WorldPoint(3765, 5676, 0)),
                    new Geometry.Cuboid(new WorldPoint(3755, 5675, 0), new WorldPoint(3763, 5674, 0)),
                    new Geometry.Cuboid(new WorldPoint(3758, 5673, 0), new WorldPoint(3762, 5670, 0))
            ));

    private final String name;
    private final WorldPoint wp;
    private final Geometry.CuboidArea cuboidArea;
}
