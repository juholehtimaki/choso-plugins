package com.theplug.AutoTitheFarmPlugin;

import java.time.Duration;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;

class TitheFarmPlant {
    private static final Duration PLANT_TIME = Duration.ofMinutes(1);

    @Getter
    @Setter
    private Instant planted;

    @Getter
    @Setter
    private Instant lastInteraction;

    @Getter
    private final TitheFarmPlantState state;

    @Getter
    private final TitheFarmPlantType type;

    @Getter
    private final GameObject gameObject;

    @Getter
    private final WorldPoint worldLocation;

    TitheFarmPlant(TitheFarmPlantState state, TitheFarmPlantType type, GameObject gameObject) {
        this.planted = Instant.now();
        this.lastInteraction = Instant.now();
        this.state = state;
        this.type = type;
        this.gameObject = gameObject;
        this.worldLocation = gameObject.getWorldLocation();
    }

    public double getPlantTimeRelative() {
        Duration duration = Duration.between(planted, Instant.now());
        return duration.compareTo(PLANT_TIME) < 0 ? (double) duration.toMillis() / PLANT_TIME.toMillis() : 1;
    }

    public double getPlantTimeDiff() {
        Duration duration = Duration.between(planted, Instant.now());
        return (double) duration.getSeconds(); // Explicitly cast to double for consistency
    }

    public Double getTimeFromLastInteraction() {
        if (lastInteraction == null) {
            return null; // Indicates no interaction has occurred yet
        }
        Duration duration = Duration.between(lastInteraction, Instant.now());
        return (double) duration.getSeconds(); // Return the seconds as a Double
    }
}
