package com.theplug.MuspahKiller.States;

import com.theplug.MuspahKiller.MuspahKillerPlugin;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.NPCTickSimulation.NPCTickSimulation;
import com.theplug.PaistiUtils.API.Potions.BoostPotion;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.Collections.query.NPCQuery;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class FightMuspahState implements State {
    MuspahKillerPlugin plugin;
    private static final int MUSPAH_RANGE_FORM_ID = 12077;
    private static final int MUSPAH_MELEE_FORM_ID = 12078;
    private static final int MUSPAH_DARKNESS_FORM_ID = 12082;
    private static final int MUSPAH_SHIELD_FORM_ID = 12079;
    private static final int MUSPAH_POST_SHIELD_FORM_ID = 12080;
    private static final int MUSPAH_MAGE_ATTACK_ANIMATION_ID = 9918;
    private static final int MUSPAH_MELEE_ATTACK_ANIMATION_ID = 9920;
    private static final int MUSPAH_RANGE_ATTACK_ANIMATION_ID = 9922;
    private static final int MUSPAH_AOE_ATTACK_ANIMATION_ID = 9925;
    private static final int MUSPAH_MAGE_ATTACK_PROJECTILE_ID = 2327;
    private static final int DANGEROUS_ICY_GRAPHIC_ID = 2335;
    private static final int DANGEROUS_SPIKE_GAME_OBJECT_ID = 46695;
    private static final int DANGEROUS_MOVING_SPIKE_GAME_OBJECT_ID = 46697;
    private static final int CLOUD_NPC_ID = 12083;
    public Set<WorldPoint> spikeTiles = new HashSet<>();
    private final Object dangerousSpikeTilesLock = new Object();
    public Set<WorldPoint> dangerousMovingSpikeTiles = new HashSet<>();
    private final Object dangerousMovingSpikeTilesLock = new Object();
    public Set<WorldPoint> allDangerousTiles = new HashSet<>();
    private final Object allDangerousTilesLock = new Object();
    public final Set<WorldPoint> dangerousTilesDuringAoe = new HashSet<>();
    private final Object dangerousTilesDuringAoeLock = new Object();
    public final Set<WorldPoint> safeTilesDuringAoe = new HashSet<>();
    private final Object safeTilesDuringAoeLock = new Object();
    public final AtomicReference<MuspahPhase> currMuspahPhase = new AtomicReference<>(null);
    public final AtomicReference<WorldPoint> darknessSafeTile = new AtomicReference<>(null);
    public final AtomicReference<WorldPoint> roomCenterTile = new AtomicReference<>(null);
    public final AtomicReference<Integer> magicAttackTick = new AtomicReference<>(-1);
    private static AtomicReference<Integer> lastAteOnTick = new AtomicReference<>(-1);
    private static AtomicReference<Integer> lastMeleeAttackTick = new AtomicReference<>(-1);
    private static AtomicReference<Integer> muspahLastAttackTick = new AtomicReference<>(-1);
    public final AtomicReference<WorldPoint> optimalTile = new AtomicReference<>(null);
    public final AtomicReference<Boolean> canSafelyAttackThisTick = new AtomicReference<>(false);
    public final AtomicReference<WorldPoint> simulatedPlayerLocationAfterAttack = new AtomicReference<>(null);
    private int nextEatAtHp;
    private int nextPrayerPotionAt;


    public FightMuspahState(MuspahKillerPlugin plugin) {
        super();
        this.plugin = plugin;
        this.nextPrayerPotionAt = generateNextPrayerPotAt();
        this.nextEatAtHp = generateNextEatAtHp();
    }

    public enum MuspahPhase {
        MELEE,
        RANGED,
        DARKNESS,
        SHIELD,
        POST_SHIELD
    }

    public enum CloudAngle {
        SOUTH,
        SOUTHWEST,
        WEST,
        NORTHWEST,
        NORTH,
        NORTHEAST,
        EAST,
        SOUTHEAST
    }

    @Override
    public String name() {
        return "Fighting Muspah";
    }

    @Override
    public boolean shouldExecuteState() {
        return plugin.isInsideMuspahArea();
    }

    @Override
    public void threadedOnGameTick() {
        simulateAndSetPrayers();
        Utility.sleepGaussian(50, 150);
        //setOffensivePrayers();
        //Utility.sleepGaussian(100, 160);
        //simulateAndSetPrayers();
    }

    @Override
    public void threadedLoop() {
        if (handleEquipmentSwitch()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        /*
        if (handleDarknessSafeTile()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
         */
        /*
        if (handlePrayers()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleEquipmentSwitch()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        /*
        if (handlePrayerRestore()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleAttacking()) {
            Utility.sleepGaussian(50, 100);
            return;
        } else {
            handleImprovePosition();
        }
         */
        Utility.sleepGaussian(50, 100);
    }

    @Subscribe
    public void onGameTick(GameTick e) {
        updateMuspahPhase();
        updateDarknessSafeTile();
        updateRoomCenterTile();
        updateDangerousTiles();
        updateCanSafelyAttackThisTick();
        updateOptimalTile();
        calculateSafeTilesDuringAoe();
    }

    private void calculateSafeTilesDuringAoe() {
        synchronized (safeTilesDuringAoeLock) {
            safeTilesDuringAoe.clear();
            var muspah = getMuspah();
            if (muspah == null) return;
            if (muspah.getAnimation() != MUSPAH_AOE_ATTACK_ANIMATION_ID) return;
            var muspahTiles = muspah.getWorldArea().toWorldPointList();
            Set<WorldPoint> aoeTiles = new HashSet<>();
            for (var mTile : muspahTiles) {
                //if (spikeTiles.contains(mTile)) continue;
                //WEST
                for (int x = 0; x < 20; x++) {
                    var newTile = mTile.dx(-x);
                    if (spikeTiles.contains(newTile)) break;
                    aoeTiles.add(newTile);
                }
                //EAST
                for (int x = 0; x < 20; x++) {
                    var newTile = mTile.dx(x);
                    if (spikeTiles.contains(newTile)) break;
                    aoeTiles.add(newTile);
                }
                //NORTH
                for (int y = 0; y < 20; y++) {
                    var newTile = mTile.dy(y);
                    if (spikeTiles.contains(newTile)) break;
                    aoeTiles.add(newTile);
                }
                //SOUTH
                for (int y = 0; y < 20; y++) {
                    var newTile = mTile.dy(-y);
                    if (spikeTiles.contains(newTile)) break;
                    aoeTiles.add(newTile);
                }
                //SOUTHWEST
                for (int x = 0; x < 20; x++) {
                    var newTile = mTile.dy(-x).dx(-x);
                    if (spikeTiles.contains(newTile)) break;
                    aoeTiles.add(newTile);
                }
                //NORTHWEST
                for (int x = 0; x < 20; x++) {
                    var newTile = mTile.dy(x).dx(-x);
                    if (spikeTiles.contains(newTile)) break;
                    aoeTiles.add(newTile);
                }
                //SOUTHEAST
                for (int x = 0; x < 20; x++) {
                    var newTile = mTile.dy(-x).dx(x);
                    if (spikeTiles.contains(newTile)) break;
                    aoeTiles.add(newTile);
                }
                //NORTHEAST
                for (int x = 0; x < 20; x++) {
                    var newTile = mTile.dy(x).dx(x);
                    if (spikeTiles.contains(newTile)) break;
                }
            }

            Set<WorldPoint> safeTiles = new HashSet<>();
            var playerLoc = Walking.getPlayerLocation();
            LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();

            for (int x = -30; x <= 30; x++) {
                for (int y = -30; y <= 30; y++) {
                    var tile = playerLoc.dx(x).dy(y);
                    if (!reachabilityMap.isReachable(tile)) continue;
                    if (aoeTiles.contains(tile)) continue;
                    if (spikeTiles.contains(tile)) continue;
                    safeTiles.add(tile);
                }
            }

            safeTilesDuringAoe.addAll(safeTiles);
        }
    }

    private void setOffensivePrayers() {
        if (Utility.getInteractionTarget() instanceof NPC) {
            var npc = (NPC) Utility.getInteractionTarget();
            if (!npc.isDead() && plugin.attackTickTracker.getTicksUntilNextAttack() <= Utility.random(1, 2)) {
                if (currMuspahPhase.get() == MuspahPhase.SHIELD) {
                    PPrayer.SMITE.setEnabled(true);
                    return;
                }
            }
        }
    }

    public boolean enableOffensiveRangePray() {
        if (PPrayer.RIGOUR.canUse()) {
            if (!PPrayer.RIGOUR.isActive()) {
                PPrayer.RIGOUR.setEnabled(true);
                return true;
            }
        } else {
            if (!PPrayer.EAGLE_EYE.isActive()) {
                PPrayer.EAGLE_EYE.setEnabled(true);
                return true;
            }
        }
        return false;
    }

    public boolean enableOffensiveMagePray() {
        if (PPrayer.AUGURY.canUse()) {
            if (!PPrayer.AUGURY.isActive()) {
                PPrayer.AUGURY.setEnabled(true);
                return true;
            }
        } else {
            if (!PPrayer.MYSTIC_MIGHT.isActive()) {
                PPrayer.MYSTIC_MIGHT.setEnabled(true);
                return true;
            }
        }
        return false;
    }

    private static final List<PPrayer> offensivePrayers = List.of(
            PPrayer.RIGOUR,
            PPrayer.EAGLE_EYE,
            PPrayer.AUGURY,
            PPrayer.MYSTIC_MIGHT,
            PPrayer.PIETY,
            PPrayer.CHIVALRY,
            PPrayer.ULTIMATE_STRENGTH,
            PPrayer.INCREDIBLE_REFLEXES
    );

    public boolean disableAllOffensivePrayers() {
        var didDisable = false;
        for (var prayer : offensivePrayers) {
            if (prayer.isActive()) {
                prayer.setEnabled(false);
                didDisable = true;
                Utility.sleepGaussian(20, 80);
            }
        }

        return didDisable;
    }

    private void disableAllPrayers() {
        EnumSet<PPrayer> prayerEnumSet = EnumSet.allOf(PPrayer.class);
        List<PPrayer> prayerList = List.copyOf(prayerEnumSet);
        for (var prayer : prayerList) {
            if (prayer.isActive()) {
                 prayer.setEnabled(false);
            }
        }
    }

    private static int ticksWithNoDefPrayerNeeded = 0;

    private void simulateAndSetPrayers() {
        Utility.runOnClientThread(() -> {
            var client = PaistiUtils.getClient();
            var relevantNpcs = NPCs.search().withName("Phantom Muspah").result();

            if (relevantNpcs.isEmpty()) {
                disableAllPrayers();
                return null;
            }

            var currPhase = currMuspahPhase.get();
            var shouldCareAboutMagicAttack = Utility.getTickCount() - magicAttackTick.get() == 3;

            var defensivePrayer = shouldCareAboutMagicAttack ? PPrayer.PROTECT_FROM_MAGIC : currPhase == MuspahPhase.MELEE ? PPrayer.PROTECT_FROM_MELEE : PPrayer.PROTECT_FROM_MISSILES;
            var offensivePrayer = currPhase == MuspahPhase.MELEE ? PPrayer.AUGURY : PPrayer.RIGOUR;

            if (currPhase != MuspahPhase.SHIELD) {
                if (!defensivePrayer.isActive() || !offensivePrayer.isActive()) {
                    defensivePrayer.setEnabled(true);
                    offensivePrayer.setEnabled(true);
                }
                return null;
            }

            var _tickSimulation = new NPCTickSimulation(client, plugin.attackTickTracker, relevantNpcs);
            _tickSimulation.getPlayerState().setInteracting(client.getLocalPlayer().getInteracting());

            _tickSimulation.simulateNpcsTick(client);
            var prayAgainst = _tickSimulation.shouldPrayAgainst(client);

            var attackingSoon = false;
            var noAttacksToCareAbout = prayAgainst == null;

            if (Utility.getInteractionTarget() instanceof NPC) {
                var npc = (NPC) Utility.getInteractionTarget();
                if (!npc.isDead() && plugin.attackTickTracker.getTicksUntilNextAttack() <= 1) {
                    attackingSoon = true;
                }
            }

            var overheadPrayer = shouldCareAboutMagicAttack ? PPrayer.PROTECT_FROM_MAGIC : attackingSoon && noAttacksToCareAbout ? PPrayer.SMITE : PPrayer.PROTECT_FROM_MISSILES;

            if (!overheadPrayer.isActive() || !offensivePrayer.isActive()) {
                overheadPrayer.setEnabled(true);
                offensivePrayer.setEnabled(true);
            }

            return null;
        });
    }

    private void updateCanSafelyAttackThisTick() {
        var relevantNpcs = NPCs.search().withinDistance(24).result();
        var _tickSimulation = new NPCTickSimulation(PaistiUtils.getClient(), plugin.attackTickTracker, relevantNpcs);
        var newPlayerLocation = _tickSimulation.getPlayerState().getArea().toWorldPoint();
        simulatedPlayerLocationAfterAttack.set(newPlayerLocation);

        canSafelyAttackThisTick.set(true);
        int distanceThreshold = 0;
        if (allDangerousTiles.stream().anyMatch(t -> t.distanceTo(newPlayerLocation) <= distanceThreshold)) {
            canSafelyAttackThisTick.set(false);
        }
    }

    private void updateMuspahPhase() {
        var muspah = NPCs.search().withName("Phantom Muspah").alive().first();
        if (muspah.isPresent()) {
            if (muspah.get().getId() == MUSPAH_RANGE_FORM_ID) currMuspahPhase.set(MuspahPhase.RANGED);
            else if (muspah.get().getId() == MUSPAH_MELEE_FORM_ID) currMuspahPhase.set(MuspahPhase.MELEE);
            else if (muspah.get().getId() == MUSPAH_DARKNESS_FORM_ID) currMuspahPhase.set(MuspahPhase.DARKNESS);
            else if (muspah.get().getId() == MUSPAH_POST_SHIELD_FORM_ID) currMuspahPhase.set(MuspahPhase.POST_SHIELD);
            else if (muspah.get().getId() == MUSPAH_SHIELD_FORM_ID) currMuspahPhase.set(MuspahPhase.SHIELD);
            else currMuspahPhase.set(null);
        }
    }

    private void updateDangerousTiles(){
        synchronized (allDangerousTilesLock) {
            allDangerousTiles.clear();
            allDangerousTiles.addAll(spikeTiles);
            allDangerousTiles.addAll(dangerousMovingSpikeTiles);
            var icyTiles = PaistiUtils.getClient().getGraphicsObjects();
            for (var icyTile : icyTiles) {
                if (icyTile.getId() == DANGEROUS_ICY_GRAPHIC_ID) {
                    var icyTileLocation = WorldPoint.fromLocal(PaistiUtils.getClient(), icyTile.getLocation());
                    allDangerousTiles.add(icyTileLocation);
                }
            }
            var clouds = NPCs.search().withId(CLOUD_NPC_ID).result();
            for (var cloud : clouds) {
                var angle = cloud.getOrientation();
                var cloudAngle = getCloudAnge(angle);
                var cloudWorldPoint = cloud.getWorldLocation();
                if (cloudAngle == CloudAngle.SOUTH) {
                    //allDangerousTiles.add(cloudWorldPoint);
                    allDangerousTiles.add(cloudWorldPoint.dy(-1));
                }  else if (cloudAngle == CloudAngle.SOUTHWEST) {
                    //allDangerousTiles.add(cloudWorldPoint);
                    allDangerousTiles.add(cloudWorldPoint.dx(-1).dy(-1));
                } else if (cloudAngle == CloudAngle.WEST) {
                    //allDangerousTiles.add(cloudWorldPoint);
                    allDangerousTiles.add(cloudWorldPoint.dx(-1));
                } else if (cloudAngle == CloudAngle.NORTHWEST) {
                    //allDangerousTiles.add(cloudWorldPoint);
                    allDangerousTiles.add(cloudWorldPoint.dy(1).dx(-1));
                } else if (cloudAngle == CloudAngle.NORTH) {
                    //allDangerousTiles.add(cloudWorldPoint);
                    allDangerousTiles.add(cloudWorldPoint.dy(1));
                } else if (cloudAngle == CloudAngle.NORTHEAST) {
                    //allDangerousTiles.add(cloudWorldPoint);
                    allDangerousTiles.add(cloudWorldPoint.dx(1).dy(1));
                } else if (cloudAngle == CloudAngle.EAST) {
                    //allDangerousTiles.add(cloudWorldPoint);
                    allDangerousTiles.add(cloudWorldPoint.dx(1));
                } else if (cloudAngle == CloudAngle.SOUTHEAST) {
                    //allDangerousTiles.add(cloudWorldPoint);
                    allDangerousTiles.add(cloudWorldPoint.dx(1).dy(-1));
                }
            }
        }
    }

    private CloudAngle getCloudAnge(int value) {
        int[] definedValues = {0, 256, 512, 768, 1024, 1280, 1536, 1792};
        int closestValue = definedValues[0];

        for (int definedValue : definedValues) {
            if (Math.abs(value - definedValue) < Math.abs(value - closestValue)) {
                closestValue = definedValue;
            }
        }

        if (closestValue == 0) return CloudAngle.SOUTH;
        if (closestValue == 256) return CloudAngle.SOUTHWEST;
        if (closestValue == 512) return CloudAngle.WEST;
        if (closestValue == 768) return CloudAngle.NORTHWEST;
        if (closestValue == 1024) return CloudAngle.NORTH;
        if (closestValue == 1280) return CloudAngle.NORTHEAST;
        if (closestValue == 1536) return CloudAngle.EAST;
        return CloudAngle.SOUTHEAST;
    }

    private void updateDarknessSafeTile() {
        var cave = TileObjects.search().withId(MuspahKillerPlugin.EXIT_CAVE_ID).nearestToPlayer();
        if (cave.isEmpty()) return;
        var caveLoc = cave.get().getWorldLocation();
        var safeSpotLoc = caveLoc.dx(-19).dy(-6);
        darknessSafeTile.set(safeSpotLoc);
    }

    private void updateRoomCenterTile() {
        var cave = TileObjects.search().withId(MuspahKillerPlugin.EXIT_CAVE_ID).nearestToPlayer();
        if (cave.isEmpty()) return;
        var caveLoc = cave.get().getWorldLocation();
        var safeSpotLoc = caveLoc.dx(-16).dy(-2);
        roomCenterTile.set(safeSpotLoc);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned e) {
        if (e.getGameObject().getId() == DANGEROUS_SPIKE_GAME_OBJECT_ID) {
            synchronized (dangerousSpikeTilesLock) {
                spikeTiles.add(e.getGameObject().getWorldLocation());
            }
        }
        if (e.getGameObject().getId() == DANGEROUS_MOVING_SPIKE_GAME_OBJECT_ID) {
            synchronized (dangerousMovingSpikeTilesLock) {
                dangerousMovingSpikeTiles.add(e.getGameObject().getWorldLocation());
            }
        }
    }
    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned e) {
        if (e.getGameObject().getId() == DANGEROUS_SPIKE_GAME_OBJECT_ID) {
            synchronized (dangerousSpikeTilesLock) {
                spikeTiles.remove(e.getGameObject().getWorldLocation());
            }
        }
        if (e.getGameObject().getId() == DANGEROUS_MOVING_SPIKE_GAME_OBJECT_ID) {
            synchronized (dangerousMovingSpikeTilesLock) {
                dangerousMovingSpikeTiles.remove(e.getGameObject().getWorldLocation());
            }
        }
    }

     @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        int animation = event.getActor().getAnimation();
        if (animation == MUSPAH_MAGE_ATTACK_ANIMATION_ID) {
            magicAttackTick.set(Utility.getTickCount());
            Utility.sendGameMessage("Magic attack incoming", "AutoMuspah");
        }
        if (animation == MUSPAH_MELEE_ATTACK_ANIMATION_ID) lastMeleeAttackTick.set(Utility.getTickCount());
        if (animation == MUSPAH_RANGE_ATTACK_ANIMATION_ID || animation == MUSPAH_MAGE_ATTACK_ANIMATION_ID || animation == MUSPAH_MELEE_ATTACK_ANIMATION_ID) {
            muspahLastAttackTick.set(Utility.getTickCount());
        }
    }

    public boolean handleDarknessSafeTile() {
        if (!isMuspahPresentAndAlive()) return false;
        if (currMuspahPhase.get() == MuspahPhase.DARKNESS) {
            if (Walking.getPlayerLocation().equals(darknessSafeTile.get())) return false;
            Walking.sceneWalk(darknessSafeTile.get());
            Utility.sendGameMessage("Walking to safe tile", "AutoMuspah");
            return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation() == darknessSafeTile.get(), 5000, 100);
        }
        return false;
    }

    public boolean handleImprovePosition() {
        if (currMuspahPhase.get() == MuspahPhase.MELEE || currMuspahPhase.get() == MuspahPhase.RANGED || currMuspahPhase.get() == MuspahPhase.DARKNESS || currMuspahPhase.get() == MuspahPhase.POST_SHIELD) {
            var opTile = optimalTile.get();
            if (opTile == null) return false;
            if (opTile.equals(Walking.getPlayerLocation())) return false;
            boolean shouldWaitForMeleeAttack = currMuspahPhase.get() == MuspahPhase.MELEE && Utility.getTickCount() - lastMeleeAttackTick.get() < 4 && !allDangerousTiles.contains(Walking.getPlayerLocation());
            if (shouldWaitForMeleeAttack) return false;
            var playerLocBefore = Walking.getPlayerLocation();
            Walking.sceneWalk(opTile);
            Utility.sleepUntilCondition(() -> !Walking.getPlayerLocation().equals(playerLocBefore), 1200, 50);
        }
        return false;
    }

    private NPC _cachedMuspah = null;
    private int _cachedMuspahTick = -1;
    public NPC getMuspah() {
        if (_cachedMuspah != null && _cachedMuspahTick == Utility.getTickCount()) {
            return _cachedMuspah;
        }
        var muspah = NPCs.search().withName("Phantom Muspah").first();
        if (muspah.isEmpty()) {
            _cachedMuspah = null;
            _cachedMuspahTick = Utility.getTickCount();
            return _cachedMuspah;
        }
        _cachedMuspahTick = Utility.getTickCount();
        _cachedMuspah = muspah.get();
        return _cachedMuspah;
    }

    public boolean isMuspahPresentAndAttackable() {
        var nex = getMuspah();
        if (nex == null || nex.isDead()) {
            return false;
        }
        var nexComposition = NPCQuery.getNPCComposition(nex);
        if (nexComposition == null) return false;
        var nexActions = nexComposition.getActions();
        return nexActions != null && Arrays.stream(nexActions).anyMatch(a -> a != null && a.equalsIgnoreCase("attack"));
    }

    public int getMuspahDistance() {
        var muspah = getMuspah();
        if (muspah == null) {
            return -1;
        }
        return Walking.getPlayerLocation().distanceTo(muspah.getWorldArea());
    }

    private void updateOptimalTile() {
        var muspah = getMuspah();
        if (muspah == null) return;
        var muspahWorldArea = muspah.getWorldArea();
        var playerLoc = Walking.getPlayerLocation();
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        List<WorldPoint> tiles = new ArrayList<>();
        var minimumDistance = currMuspahPhase.get() == MuspahPhase.MELEE ? 2 : 1;
        for (var dx = -35; dx <= 35; dx++) {
            for (var dy = -35; dy <= 35; dy++) {
                var tile = playerLoc.dx(dx).dy(dy);
                if (allDangerousTiles.contains(tile)) continue;
                if (!reachabilityMap.isReachable(tile)) continue;
                if (muspahWorldArea.distanceTo(tile) < minimumDistance) continue;
                if (currMuspahPhase.get() == MuspahPhase.MELEE && getMuspahDistance() < 2) {
                    //if (areTilesInLine(tile, playerLoc)) continue;
                    if (playerLoc.distanceTo(tile) < 2) continue;
                }
                if (!dangerousMovingSpikeTiles.isEmpty()) {
                    boolean tileCloseToMovingSpikes = false;
                    for (var spike : dangerousMovingSpikeTiles) {
                        if (tile.distanceTo(spike) < 3) {
                            tileCloseToMovingSpikes = true;
                            break;
                        }
                    }
                    if (tileCloseToMovingSpikes) continue;
                }
                tiles.add(tile);
            }
        }
        var centerTile = roomCenterTile.get();
        if (centerTile != null) {
            tiles.sort(Comparator.comparingInt(centerTile::distanceTo));
        }
        if (currMuspahPhase.get() == MuspahPhase.MELEE) tiles.sort(Comparator.comparingInt(reachabilityMap::getCostTo));
        var tilesWithSafePathing = tiles.stream()
                .filter(t -> {
                    var pathTiles = reachabilityMap.getPathTo(t);
                    var currPlayLoc = Walking.getPlayerLocation();
                    for (var pathTile : pathTiles) {
                        if (allDangerousTiles.contains(pathTile) && !currPlayLoc.equals(pathTile)) {
                            return false;
                        }
                    }
                    return true;
                }).collect(Collectors.toList());
        if (tilesWithSafePathing.isEmpty()) {
            Utility.sendGameMessage("No safe tile available", "AutoMuspah");
            optimalTile.set(null);
            return;
        }
        optimalTile.set(tilesWithSafePathing.get(0));
    }

    private boolean areTilesInLine(WorldPoint tile1, WorldPoint tile2) {
        int dx = Math.abs(tile1.getX() - tile2.getX());
        int dy = Math.abs(tile1.getY() - tile2.getY());
        return dx == 0 || dy == 0 || dx == dy;
    }


    public boolean handleAttacking() {
        if (!isMuspahPresentAndAttackable()) return false;
        var muspah = getMuspah();
        if (muspah == null) return false;
        if (Utility.getInteractionTarget() == muspah) return false;
        boolean attackReady = plugin.attackTickTracker.getTicksUntilNextAttack() <= 1;
        if (!attackReady) return false;
        /*
        if (muspah.getWorldArea().distanceTo(Walking.getPlayerLocation()) > plugin.attackTickTracker.getPlayerAttackRange()) return false;
        var minimumDistance = currMuspahPhase.get() == MuspahPhase.MELEE ? 3 : 1;
        if (getMuspahDistance() < minimumDistance) return false
         */
        var playerLoc = Walking.getPlayerLocation();
        if (allDangerousTiles.contains(playerLoc)) return false;
        if (!canSafelyAttackThisTick.get()) return false;
        if (!playerLoc.equals(optimalTile.get())) return false;
        if (Interaction.clickNpc(muspah, "Attack")) {
            return Utility.sleepUntilCondition(() -> Utility.getInteractionTarget() == muspah, 1800, 50);
        }
        return true;
    }

    public boolean handleEquipmentSwitch() {
        if (!plugin.config.shouldSwitchGear()) return false;
        if (currMuspahPhase.get() == (MuspahPhase.MELEE)) {
            if (plugin.magicLoadout.isSatisfied(true)) return false;
            plugin.magicLoadout.handleSwitchTurbo();
            return true;
        }
        else {
            if (plugin.rangedLoadout.isSatisfied(true)) return false;
            plugin.rangedLoadout.handleSwitchTurbo();
            return true;
        }
    }

    public boolean isMuspahPresentAndAlive() {
        var muspah = NPCs.search().withName("Phantom Muspah").alive().first();
        if (muspah.isPresent()) return true;
        return false;
    }

    public boolean handlePrayers() {
        var shouldCareAboutMagicAttack = Utility.getTickCount() - magicAttackTick.get() <= 3;

        var usingOnlyRanged = !plugin.config.shouldSwitchGear();

        if (!isMuspahPresentAndAlive()) {
            var prayerActive = PPrayer.PROTECT_FROM_MELEE.isActive() || PPrayer.AUGURY.isActive() || PPrayer.PROTECT_FROM_MISSILES.isActive() || PPrayer.RIGOUR.isActive();
            if (prayerActive) {
                PPrayer.PROTECT_FROM_MELEE.setEnabled(false);
                PPrayer.AUGURY.setEnabled(false);
                PPrayer.PROTECT_FROM_MISSILES.setEnabled(false);
                PPrayer.RIGOUR.setEnabled(false);
                return true;
            }
            return false;
        }

        var offensivePrayer = usingOnlyRanged ? PPrayer.RIGOUR : currMuspahPhase.get() == MuspahPhase.MELEE ? PPrayer.AUGURY : PPrayer.RIGOUR;
        var defensivePrayer = currMuspahPhase.get() == MuspahPhase.MELEE ? PPrayer.PROTECT_FROM_MELEE : shouldCareAboutMagicAttack ? PPrayer.PROTECT_FROM_MAGIC : PPrayer.PROTECT_FROM_MISSILES;

        if (currMuspahPhase.get() != MuspahPhase.SHIELD) {
            if (!offensivePrayer.isActive() || !defensivePrayer.isActive()) {
                offensivePrayer.setEnabled(true);
                defensivePrayer.setEnabled(true);
                return true;
            }
            return false;
        }

        boolean attackReady = plugin.attackTickTracker.getTicksUntilNextAttack() <= 1;
        var relevantNpcs = NPCs.search().withId(MUSPAH_SHIELD_FORM_ID).result();
        var _tickSimulation = new NPCTickSimulation(PaistiUtils.getClient(), plugin.attackTickTracker, relevantNpcs);
        _tickSimulation.simulateNpcsTick(PaistiUtils.getClient());
        var client = PaistiUtils.getClient();
        var prayAgainst = _tickSimulation.shouldPrayAgainst(client);
        var overheadPray = isPlayerInteractingWithMuspah() && attackReady && prayAgainst == null ? PPrayer.SMITE : defensivePrayer;
        if (!overheadPray.isActive() || !offensivePrayer.isActive()) {
            overheadPray.setEnabled(true);
            offensivePrayer.setEnabled(true);
            return true;
        }

        return false;
    }

    public int generateNextPrayerPotAt() {
        return Utility.random(30, 40);
    }

    public int generateNextEatAtHp() {
        return Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.random(25, 35);
    }

    private boolean handlePrayerRestore() {
        if (Utility.getTickCount() - lastAteOnTick.get() < 3) return false;
        if (Utility.getBoostedSkillLevel(Skill.PRAYER) <= nextPrayerPotionAt) {
            BoostPotion prayerBoostPot = BoostPotion.PRAYER_POTION.findInInventoryWithLowestDose().isEmpty() ? BoostPotion.SUPER_RESTORE : BoostPotion.PRAYER_POTION;
            var potionInInventory = prayerBoostPot.findInInventoryWithLowestDose();
            if (potionInInventory.isPresent()) {
                var clicked = Interaction.clickWidget(potionInInventory.get(), "Drink");
                if (clicked) {
                    nextPrayerPotionAt = generateNextPrayerPotAt();
                    lastAteOnTick.set(Utility.getTickCount());
                }
                return clicked;
            }
        }
        return false;
    }

    private boolean isPlayerInteractingWithMuspah() {
        var muspah = getMuspah();
        if (muspah == null) return false;
        var interactionTarget = Utility.getInteractionTarget();
        if (interactionTarget == null) return false;
        return interactionTarget.equals(muspah);
    }

    @Subscribe(priority = 5000)
    public void onActorDeath(ActorDeath actorDeath) {
        if (!plugin.runner.isRunning()) return;
        Actor actor = actorDeath.getActor();
        if (actor instanceof NPC) {
            if (actor.getName() != null) {
                if (actor.getName().toLowerCase().contains("muspah")) {
                    Utility.sendGameMessage("Muspah has died", "AutoMuspah");
                    allDangerousTiles.clear();
                    spikeTiles.clear();
                    dangerousMovingSpikeTiles.clear();
                }
            }
        }
    }
}
