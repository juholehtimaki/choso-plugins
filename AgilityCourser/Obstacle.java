package com.theplug.AgilityCourser;

import com.theplug.PaistiUtils.API.Interaction;
import com.theplug.PaistiUtils.API.TileObjects;
import com.theplug.PaistiUtils.API.Utility;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import net.runelite.api.coords.WorldPoint;

import java.util.concurrent.Callable;

public class Obstacle {
    String action;
    String name;

    WorldPoint worldPoint;

    Callable<Void> extraActionAfterObstacle;

    Obstacle(String name, String action, WorldPoint worldPoint) {
        super();
        this.action = action;
        this.name = name;
        this.worldPoint = worldPoint;
        this.extraActionAfterObstacle = null;
    }

    Obstacle(String name, String action, WorldPoint worldPoint, Callable<Void> extraActionAfterObstacle) {
        super();
        this.action = action;
        this.name = name;
        this.worldPoint = worldPoint;
        this.extraActionAfterObstacle = extraActionAfterObstacle;
    }

    public void cross() {
        var obstacle = TileObjects.search().withName(name).withAction(action).nearestToPoint(worldPoint);
        if (obstacle.isEmpty()) {
            Utility.sendGameMessage("Could not interact with obstacle: " + name, "PAgilityCourser");
            return;
        }
        Interaction.clickTileObject(obstacle.get(), action);
    }

    public boolean isReachable() {
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        return reachabilityMap.isReachable(worldPoint);
    }

    @Override
    public String toString() {
        return "Obstacle{" +
                "action='" + action + '\'' +
                ", name='" + name + '\'' +
                ", worldPoint=" + worldPoint +
                '}';
    }
}
