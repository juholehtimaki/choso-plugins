package com.theplug.AutoPestControlPlugin.States;

import com.theplug.AutoPestControlPlugin.AutoPestControlPlugin;
import com.theplug.AutoPestControlPlugin.AutoPestControlPluginConfig;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import net.runelite.api.widgets.WidgetInfo;

public class PrepareState implements State {

    static AutoPestControlPlugin plugin;
    AutoPestControlPluginConfig config;

    public PrepareState(AutoPestControlPlugin plugin, AutoPestControlPluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public String name() {
        return "Preparing";
    }

    @Override
    public boolean shouldExecuteState() {
        return !Utility.isInInstancedRegion();
    }

    @Override
    public void threadedOnGameTick() {
        Utility.sleepGaussian(100, 200);
    }

    private boolean isInBoat() {
        var pcInfoWidget = Widgets.getWidget(WidgetInfo.PEST_CONTROL_BOAT_INFO);
        return Widgets.isValidAndVisible(pcInfoWidget);
    }

    private boolean handleEnteringBoat() {
        if (isInBoat()) {
            return false;
        }
        if (Walking.getPlayerLocation().distanceTo(plugin.config.boat().getWp()) > 1) {
            WebWalker.walkTo(plugin.config.boat().getWp());
            Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(plugin.config.boat().getWp()) == 0, 1000, 100);
        }
        var gangplank = TileObjects.search().withId(plugin.config.boat().getId()).withAction("Cross").withinDistanceToPoint(1, plugin.config.boat().getWp()).first();

        if (gangplank.isEmpty()) {
            return false;
        }

        var rMap = LocalPathfinder.getReachabilityMap();
        if (rMap.isReachable(gangplank.get())) {
            Interaction.clickTileObject(gangplank.get(), "Cross");
            return Utility.sleepUntilCondition(this::isInBoat, 3000, 100);
        }

        return false;
    }

    @Override
    public void threadedLoop() {
        if (plugin.paistiBreakHandler.shouldBreak(plugin)) {
            Utility.sleepGaussian(6000, 8000);
            Utility.sendGameMessage("Taking a break", "AutoPestControl");
            plugin.paistiBreakHandler.startBreak(plugin);

            Utility.sleepGaussian(1000, 2000);
            Utility.sleepUntilCondition(() -> !plugin.paistiBreakHandler.isBreakActive(plugin), 99999999, 5000);
        }
        Utility.sleepGaussian(1200, 1800);
        if (!shouldExecuteState()) {
            return;
        }
        if (handleEnteringBoat()) {
            Utility.sleepGaussian(600, 1200);
            return;
        }
        Utility.sleepGaussian(100, 200);
    }
}
