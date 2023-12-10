package com.theplug.AgilityCourser;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.Utility;
import com.theplug.PaistiUtils.API.Walking;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@DontObfuscate
public enum Course {
    GNOME("Gnome", 1, List.of(
            new Obstacle("Log balance", "Walk-across", new WorldPoint(2474, 3436, 0)),
            new Obstacle("Obstacle net", "Climb-over", new WorldPoint(2474, 3426, 0)),
            new Obstacle("Tree branch", "Climb", new WorldPoint(2473, 3423, 1)),
            new Obstacle("Balancing rope", "Walk-on", new WorldPoint(2477, 3420, 2)),
            new Obstacle("Tree branch", "Climb-down", new WorldPoint(2485, 3419, 2)),
            new Obstacle("Obstacle net", "Climb-over", new WorldPoint(2486, 3425, 0)),
            new Obstacle("Obstacle pipe", "Squeeze-through", new WorldPoint(2484, 3430, 0))
    )),
    DRAYNOR("Draynor Village", 10, List.of(
            new Obstacle("Rough wall", "Climb", new WorldPoint(3103, 3279, 0)),
            new Obstacle("Tightrope", "Cross", new WorldPoint(3099, 3277, 3)),
            new Obstacle("Tightrope", "Cross", new WorldPoint(3091, 3276, 3)),
            new Obstacle("Narrow wall", "Balance", new WorldPoint(3089, 3265, 3)),
            new Obstacle("Wall", "Jump-up", new WorldPoint(3088, 3257, 3)),
            new Obstacle("Gap", "Jump", new WorldPoint(3094, 3255, 3)),
            new Obstacle("Crate", "Climb-down", new WorldPoint(3100, 3261, 3))
    )),
    VARROCK("Varrock", 30, List.of(
            new Obstacle("Rough wall", "Climb", new WorldPoint(3221, 3414, 0)),
            new Obstacle("Clothes line", "Cross", new WorldPoint(3214, 3414, 3)),
            new Obstacle("Gap", "Leap", new WorldPoint(3201, 3416, 3)),
            new Obstacle("Wall", "Balance", new WorldPoint(3194, 3416, 1)),
            new Obstacle("Gap", "Leap", new WorldPoint(3193, 3402, 3)),
            new Obstacle("Gap", "Leap", new WorldPoint(3208, 3399, 3)),
            new Obstacle("Gap", "Leap", new WorldPoint(3231, 3402, 3)),
            new Obstacle("Ledge", "Hurdle", new WorldPoint(3237, 3408, 3)),
            new Obstacle("Edge", "Jump-off", new WorldPoint(3236, 3415, 3))
    )),
    CANIFIS("Canifis", 40, List.of(
            new Obstacle("Tall tree", "Climb", new WorldPoint(3508, 3488, 0)),
            new Obstacle("Gap", "Jump", new WorldPoint(3505, 3497, 2)),
            new Obstacle("Gap", "Jump", new WorldPoint(3498, 3504, 2)),
            new Obstacle("Gap", "Jump", new WorldPoint(3487, 3499, 2)),
            new Obstacle("Gap", "Jump", new WorldPoint(3478, 3493, 3)),
            new Obstacle("Pole-vault", "Vault", new WorldPoint(3479, 3483, 2)),
            new Obstacle("Gap", "Jump", new WorldPoint(3502, 3476, 3)),
            new Obstacle("Gap", "Jump", new WorldPoint(3510, 3482, 2))
    )),
    FALADOR("Falador", 50, List.of(
            new Obstacle("Rough wall", "Climb", new WorldPoint(3036, 3341, 0)),
            new Obstacle("Tightrope", "Cross", new WorldPoint(3039, 3343, 3)),
            new Obstacle("Hand holds", "Cross", new WorldPoint(3050, 3349, 3)),
            new Obstacle("Gap", "Jump", new WorldPoint(3048, 3358, 3)),
            new Obstacle("Gap", "Jump", new WorldPoint(3045, 3361, 3)),
            new Obstacle("Tightrope", "Cross", new WorldPoint(3035, 3361, 3)),
            new Obstacle("Tightrope", "Cross", new WorldPoint(3027, 3353, 3)),
            new Obstacle("Gap", "Jump", new WorldPoint(3018, 3353, 3)),
            new Obstacle("Ledge", "Jump", new WorldPoint(3017, 3346, 3)),
            new Obstacle("Ledge", "Jump", new WorldPoint(3013, 3344, 3)),
            new Obstacle("Ledge", "Jump", new WorldPoint(3013, 3335, 3)),
            new Obstacle("Ledge", "Jump", new WorldPoint(3016, 3333, 3)),
            new Obstacle("Edge", "Jump", new WorldPoint(3022, 3333, 3))
    )),
    SEERS("Seers", 60, List.of(
            new Obstacle("Wall", "Climb-up", new WorldPoint(2729, 3489, 0)),
            new Obstacle("Gap", "Jump", new WorldPoint(2721, 3494, 3)),
            new Obstacle("Tightrope", "Cross", new WorldPoint(2710, 3490, 2)),
            new Obstacle("Gap", "Jump", new WorldPoint(2710, 3477, 2)),
            new Obstacle("Gap", "Jump", new WorldPoint(2702, 3470, 3)),
            new Obstacle("Edge", "Jump", new WorldPoint(2702, 3465, 2))
    )),
    POLLNIVNEACH("Pollnivneach", 70, List.of(
            new Obstacle("Basket", "Climb-on", new WorldPoint(3351, 2961, 0)),
            new Obstacle("Market stall", "Jump-on", new WorldPoint(3350, 2968, 1)),
            new Obstacle("Banner", "Grab", new WorldPoint(3354, 2976, 1)),
            new Obstacle("Gap", "Leap", new WorldPoint(3362, 2977, 1)),
            new Obstacle("Tree", "Jump-to", new WorldPoint(3366, 2976, 1), () -> {
                Utility.sleep(1200);
                return null;
            }),
            new Obstacle("Rough wall", "Climb", new WorldPoint(3366, 2982, 1)),
            new Obstacle("Monkeybars", "Cross", new WorldPoint(3360, 2984, 2)),
            new Obstacle("Tree", "Jump-on", new WorldPoint(3359, 2995, 2)),
            new Obstacle("Drying line", "Jump-to", new WorldPoint(3362, 3002, 2))
    )),
    RELLEKKA("Rellekka", 80, List.of(
            new Obstacle("Rough wall", "Climb", new WorldPoint(2625, 3678, 0)),
            new Obstacle("Gap", "Leap", new WorldPoint(2623, 3672, 3)),
            new Obstacle("Tightrope", "Cross", new WorldPoint(2622, 3658, 3)),
            new Obstacle("Gap", "Leap", new WorldPoint(2630, 3655, 3)),
            new Obstacle("Gap", "Hurdle", new WorldPoint(2643, 3653, 3)),
            new Obstacle("Tightrope", "Cross", new WorldPoint(2647, 3662, 3)),
            new Obstacle("Gap", "Leap", new WorldPoint(2649, 3675, 3)),
            new Obstacle("Pile of fish", "Jump-in", new WorldPoint(2655, 3674, 3))
    )),
    ARDOUGNE("Ardougne", 90, List.of(
            new Obstacle("Wooden Beams", "Climb-up", new WorldPoint(2673, 3298, 0)),
            new Obstacle("Gap", "Jump", new WorldPoint(2671, 3309, 3)),
            new Obstacle("Plank", "Walk-on", new WorldPoint(2662, 3318, 3)),
            new Obstacle("Gap", "Jump", new WorldPoint(2654, 3318, 3)),
            new Obstacle("Gap", "Jump", new WorldPoint(2653, 3310, 3)),
            new Obstacle("Steep roof", "Balance-across", new WorldPoint(2653, 3300, 3)),
            new Obstacle("Gap", "Jump", new WorldPoint(2656, 3297, 3))
    ));

    final String courseName;
    final int minimumLevel;
    final List<Obstacle> obstacles;

    Course(String courseName, int minimumLevel, List<Obstacle> obstacles) {
        this.courseName = courseName;
        this.minimumLevel = minimumLevel;
        this.obstacles = obstacles;
    }

    public Obstacle getNextObstacle(Obstacle lastObstacle) {
        // Try to get the next obstacle from the last one if possible
        if (lastObstacle != null) {
            int lastIndex = obstacles.indexOf(lastObstacle);
            Obstacle possibleNextObstacle = lastIndex + 1 < obstacles.size() ? obstacles.get(lastIndex + 1) : obstacles.get(0);
            if (possibleNextObstacle.isReachable()) {
                return possibleNextObstacle;
            }
        }

        // Otherwise get the first reachable obstacle
        var possibleObstacles = obstacles
                .stream()
                .filter(Obstacle::isReachable)
                .sorted(Comparator.comparingInt(obstacle -> obstacle.worldPoint.distanceTo(Walking.getPlayerLocation()))).collect(Collectors.toList());
        if (possibleObstacles.isEmpty()) {
            return null;
        }
        return possibleObstacles.get(0);
    }

    @Override
    public String toString() {
        return courseName;
    }
}
