package com.theplug.AutoPestControlPlugin.States;

import com.theplug.AutoPestControlPlugin.*;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import jdk.jshell.execution.Util;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.eventbus.Subscribe;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class MinigameState implements State {

    static AutoPestControlPlugin plugin;
    AutoPestControlPluginConfig config;

    private final Pattern SHIELD_DROP = Pattern.compile("The ([a-z]+), [^ ]+ portal shield has dropped!", Pattern.CASE_INSENSITIVE);

    private Game game;

    public static Set<WorldPoint> brawlerTiles = new HashSet<>();

    private final Object brawlerTilesLock = new Object();

    public static Set<WorldPoint> splatterDeathTiles = new HashSet<>();

    private final Object splatterDeathTilesLock = new Object();
    private final AtomicReference<Integer> splatterDeathsCachedOnTick = new AtomicReference<>(-1);

    public MinigameState(AutoPestControlPlugin plugin, AutoPestControlPluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public String name() {
        return "Minigaming";
    }

    @Override
    public boolean shouldExecuteState() {
        return Utility.isInInstancedRegion();
    }

    public static AtomicReference<List<WorldPoint>> PATH_DEBUG = new AtomicReference<>(null);
    private static AtomicReference<Integer> closedDoorOnTick = new AtomicReference<>(-1);

    public static AtomicReference<WorldPoint> desiredPortalLoc = new AtomicReference<>(null);
    public static AtomicReference<WorldArea> desiredWorldArea = new AtomicReference<>(null);
    public static AtomicReference<WorldArea> desiredWorldAreaRed = new AtomicReference<>(null);
    public static AtomicReference<WorldArea> desiredWorldAreaBlue = new AtomicReference<>(null);
    public static AtomicReference<WorldArea> desiredWorldAreaPurple = new AtomicReference<>(null);
    public static AtomicReference<WorldArea> desiredWorldAreaYellow = new AtomicReference<>(null);

    private void initializeGame() {
        if (Widgets.getWidget(ComponentID.PEST_CONTROL_BLUE_SHIELD) == null) {
            if (game != null) {
                game = null;
            }
        } else if (game == null) {
            game = new Game();
        }
    }

    private void updateGame() {
        game.updateGame();
    }

    private boolean handleTravelling() {
        if (desiredPortalLoc.get() == null) return false;
        if (desiredWorldArea.get() == null) return false;
        if (desiredWorldArea.get().contains(Walking.getPlayerLocation())) return false;
        var rMap = plugin.getPestControlReachabilityMap();
        var pathToPortal = rMap.getPathTo(desiredPortalLoc.get());
        PATH_DEBUG.set(pathToPortal);
        return plugin.progressWalkingOnPestPath(pathToPortal);
    }

    private boolean handleDodging() {
        List<WorldPoint> tilesToAvoid = new ArrayList<>();
        tilesToAvoid.addAll(brawlerTiles);
        tilesToAvoid.addAll(splatterDeathTiles);
        var playerLoc = Walking.getPlayerLocation();

        if (!tilesToAvoid.contains(playerLoc)) return false;

        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        List<WorldPoint> tiles = new ArrayList<>();
        if (!tilesToAvoid.contains(playerLoc)) return false;
        for (var dx = -3; dx <= 3; dx++) {
            for (var dy = -3; dy <= 3; dy++) {
                var tile = playerLoc.dx(dx).dy(dy);
                if (tilesToAvoid.contains(tile)) continue;
                if (!reachabilityMap.isReachable(tile)) continue;
                tiles.add(tile);
            }
        }
        if (tiles.isEmpty()) {
            Utility.sendGameMessage("Could not find tile to avoid damage / brawler", "AutoPestControl");
            return false;
        }

        tiles.sort(Comparator.comparingInt(reachabilityMap::getCostTo));
        var opTile = tiles.get(0);
        if (playerLoc.equals(opTile)) return false;
        Walking.sceneWalk(opTile);
        return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().equals(opTile), 600, 50);
    }

    private boolean handleQuickPrayers() {
        if (!plugin.config.useQuickPrayers()) return false;
        if (desiredWorldArea.get() == null) return false;
        if (!desiredWorldArea.get().contains(Walking.getPlayerLocation())) return false;
        if (!PPrayer.isQuickPrayerActive()) return PPrayer.setQuickPrayerEnabled(true);
        return false;
    }

    private boolean handleAttacking() {
        if (desiredWorldArea.get() == null) return false;
        var rMap = LocalPathfinder.getReachabilityMap();
        var possibleTargets = NPCs.search().alive().withAction("Attack").result().stream().filter(n -> desiredWorldArea.get().contains(n.getWorldLocation()) && rMap.isReachable(n)).collect(Collectors.toList());
        if (possibleTargets.isEmpty()) return false;
        possibleTargets.sort(Comparator.comparingInt(p -> p.getWorldArea().distanceTo(Walking.getPlayerLocation())));
        possibleTargets.sort(Comparator.comparingInt(p -> {
            if (p.getName() == null || p.getName().equalsIgnoreCase("splatter")) return Integer.MAX_VALUE;
            if (p.getName().equalsIgnoreCase("brawler")) return 1;
            if (p.getName().equalsIgnoreCase("spinner")) return 2;
            if (p.getName().equalsIgnoreCase("portal")) return 3;
            return 5;
        }));
        var target = possibleTargets.get(0);
        if (Utility.getInteractionTarget() == target) return false;
        //Utility.sendGameMessage("Attempting to attack: " + target.getName(), "AutoPestControl");
        Interaction.clickNpc(target, "Attack");
        return Utility.sleepUntilCondition(() -> Utility.getInteractionTarget() == target, 1200, 50);
    }

    private void updateBrawlerTiles() {
        synchronized (brawlerTilesLock) {
            brawlerTiles.clear();
            var brawlers = NPCs.search().withName("Brawler").result();
            for (var brawler : brawlers) {
                var bTiles = brawler.getWorldArea().toWorldPointList();
                brawlerTiles.addAll(bTiles);
            }
        }
    }

    private void updateSplatterDeathTiles() {
        synchronized (splatterDeathTilesLock) {
            if (Utility.getTickCount() - splatterDeathsCachedOnTick.get() > 3) splatterDeathTiles.clear();
            var splatters = NPCs.search().withName("Splatter").dead().result();
            for (var splatter : splatters) {
                var loc = splatter.getWorldLocation();
                for (var x = -1; x < 2; x++) {
                    for (var y = -1; y < 2; y++) {
                        splatterDeathTiles.add(loc.dx(x).dy(y));
                    }
                }
            }
        }
    }

    private void updateDesiredPortal() {
        var dPortal = game.getDesiredPortal();
        if (dPortal == null) return;
        desiredPortalLoc.set(dPortal.getPortal().getInstancedLoc());
        if (dPortal.getPortal() == Portal.BLUE) {
            desiredWorldArea.set(new WorldArea(game.getBlue().getPortal().getInstancedLoc().dx(-6).dy(-7), 16, 20));
        } else if (dPortal.getPortal() == Portal.YELLOW) {
            desiredWorldArea.set(new WorldArea(game.getYellow().getPortal().getInstancedLoc().dx(-12).dy(-9), 25, 19));
        } else if (dPortal.getPortal() == Portal.RED) {
            desiredWorldArea.set(new WorldArea(game.getRed().getPortal().getInstancedLoc().dx(-12).dy(-9), 25, 20));
        } else if (dPortal.getPortal() == Portal.PURPLE) {
            desiredWorldArea.set(new WorldArea(game.getPurple().getPortal().getInstancedLoc().dx(-7).dy(-7), 16, 20));
        }

        //desiredWorldAreaRed.set(new WorldArea(game.getRed().getPortal().getInstancedLoc().dx(-12).dy(-9), 25, 20));
        //desiredWorldAreaBlue.set(new WorldArea(game.getBlue().getPortal().getInstancedLoc().dx(-6).dy(-7), 16, 20));
        //desiredWorldAreaYellow.set(new WorldArea(game.getYellow().getPortal().getInstancedLoc().dx(-12).dy(-9), 25, 19));
        //desiredWorldAreaPurple.set(new WorldArea(game.getPurple().getPortal().getInstancedLoc().dx(-7).dy(-7), 16, 20));
    }

    private boolean handleClosingDoors() {
        if (Utility.getTickCount() - closedDoorOnTick.get() < 20) return false;
        WorldPoint corner1 = plugin.getWalledAreaCorner1();
        WorldPoint corner2 = plugin.getWalledAreaCorner2();
        Geometry.Cuboid walledArea = new Geometry.Cuboid(corner1.getX(), corner1.getY(), corner1.getPlane(), corner2.getX(), corner2.getY(), corner2.getPlane());

        if (walledArea.contains(Walking.getPlayerLocation())) return false;

        var closeDoor = TileObjects.search().withinDistance(5).withAction("Close").nearestToPlayer();
        if (closeDoor.isEmpty()) {
            return false;
        }

        Boolean playersNearbyInsideFence = Utility.runOnClientThread(() -> PaistiUtils.getClient().getPlayers().stream().anyMatch(p -> {
            if (p != PaistiUtils.getClient().getLocalPlayer()
                    && p.getWorldLocation().distanceTo(closeDoor.get().getWorldLocation()) <= 3
                    && walledArea.contains(p.getWorldLocation())) return true;
            return false;
        }));

        if (Boolean.FALSE.equals(playersNearbyInsideFence)) {
            Interaction.clickTileObject(closeDoor.get(), "Close");
            if (Utility.sleepUntilCondition(() -> TileObjects.search().withinDistance(3).withAction("Close").empty(), 3000, 600)) {
                closedDoorOnTick.set(Utility.getTickCount());
                return true;
            }
        }
        return false;
    }

    private boolean handleToggleRun() {
        if (Walking.isRunEnabled() || Walking.getRunEnergy() < 15) return false;
        return Walking.setRun(true);
    }

    @Override
    public void threadedOnGameTick() {
        initializeGame();
        updateGame();
        updateBrawlerTiles();
        updateSplatterDeathTiles();
        updateDesiredPortal();
        Utility.sleepGaussian(100, 200);
    }

    private boolean handleSpec() {
        if (desiredWorldArea.get() == null) return false;
        if (!desiredWorldArea.get().contains(Walking.getPlayerLocation())) return false;
        if (!plugin.config.useSpecialAttack()) return false;
        if (Utility.getSpecialAttackEnergy() >= plugin.config.specEnergyMinimum() && !Utility.isSpecialAttackEnabled()) {
            Utility.specialAttack();
            return Utility.sleepUntilCondition(Utility::isSpecialAttackEnabled, 1200, 300);
        }
        return false;
    }

    @Override
    public void threadedLoop() {
        if (handleTravelling()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleDodging()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleQuickPrayers()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleClosingDoors()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleAttacking()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleSpec()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleToggleRun()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        Utility.sleepGaussian(200, 500);
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE) {
            Matcher matcher = SHIELD_DROP.matcher(chatMessage.getMessage());
            if (matcher.lookingAt()) {
                game.fall(matcher.group(1));
            }
        }
    }
}
