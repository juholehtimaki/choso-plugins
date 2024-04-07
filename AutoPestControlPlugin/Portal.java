package com.theplug.AutoPestControlPlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.Utility;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import net.runelite.api.annotations.Component;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;

@AllArgsConstructor
@Getter
@ToString
@DontObfuscate
public enum Portal {
    PURPLE(ComponentID.PEST_CONTROL_PURPLE_SHIELD, ComponentID.PEST_CONTROL_PURPLE_HEALTH, ComponentID.PEST_CONTROL_PURPLE_ICON, new WorldPoint(2634, 2592, 0)),
    BLUE(ComponentID.PEST_CONTROL_BLUE_SHIELD, ComponentID.PEST_CONTROL_BLUE_HEALTH, ComponentID.PEST_CONTROL_BLUE_ICON, new WorldPoint(2677, 2589, 0)),
    YELLOW(ComponentID.PEST_CONTROL_YELLOW_SHIELD, ComponentID.PEST_CONTROL_YELLOW_HEALTH, ComponentID.PEST_CONTROL_YELLOW_ICON, new WorldPoint(2670, 2575, 0)),
    RED(ComponentID.PEST_CONTROL_RED_SHIELD, ComponentID.PEST_CONTROL_RED_HEALTH, ComponentID.PEST_CONTROL_RED_ICON, new WorldPoint(2646, 2574, 0));

    @Component
    public final int shield;
    @Component
    private final int hitpoints;
    @Component
    private final int icon;
    private final WorldPoint worldPoint;

    public WorldPoint getInstancedLoc() {
        return Utility.threadSafeGetInstanceWorldPoint(worldPoint);
    }
}
