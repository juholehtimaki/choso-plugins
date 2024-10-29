package com.theplug.AutoHueycoatlPlugin.States;

import com.theplug.AutoHueycoatlPlugin.AutoHueycoatlPlugin;
import com.theplug.AutoHueycoatlPlugin.AutoHueycoatlPluginConfig;
import com.theplug.PaistiUtils.API.Interaction;
import com.theplug.PaistiUtils.API.NPCs;
import com.theplug.PaistiUtils.API.Potions.BoostPotion;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.API.Utility;
import com.theplug.PaistiUtils.API.Walking;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class PreBossState implements State {
    AutoHueycoatlPlugin plugin;
    AutoHueycoatlPluginConfig config;

    private static final AtomicReference<Integer> lastAteOnTick = new AtomicReference<>(-1);
    private static final AtomicReference<Integer> lastDrankOnTick = new AtomicReference<>(-1);
    private final AtomicReference<PPrayer> defensivePrayer = new AtomicReference<>(null);
    private final AtomicReference<WorldPoint> optimalTile = new AtomicReference<>(null);
    private final Object dangerousTilesLock = new Object();
    public final Set<WorldPoint> dangerousTiles = new HashSet<>();
    private int nextPrayerPotionAt = -1;
    private int nextEatAtHp = -1;

    public PreBossState(AutoHueycoatlPlugin plugin, AutoHueycoatlPluginConfig config) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.nextPrayerPotionAt = generateNextPrayerPotAt();
        this.nextEatAtHp = generateNextEatAtHp();
    }

    private final String HUEYCOATL_BODY_NAME = "Hueycoatl body";

    private int generateNextPrayerPotAt() {
        return Utility.random(10, 20);
    }

    private int generateNextEatAtHp() {
        return Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.random(30, 55);
    }

    private boolean canDrinkThisTick() {
        int currTick = Utility.getTickCount();
        if (currTick - lastDrankOnTick.get() < 3) return false;
        if (currTick - lastAteOnTick.get() < 3 && lastAteOnTick.get() != 0) return false;
        return true;
    }

    private boolean canEatThisTick() {
        int currTick = Utility.getTickCount();
        if (currTick - lastAteOnTick.get() < 3) return false;
        if (currTick - lastDrankOnTick.get() < 3) return false;
        return true;
    }

    private boolean eatFood() {
        var foodItem = plugin.getFoodItems().stream().findFirst();
        return foodItem.filter(widget -> Interaction.clickWidget(widget, "Eat", "Drink")).isPresent();
    }

    private Optional<NPC> getTarget() {
        return NPCs.search().withName(HUEYCOATL_BODY_NAME).withAction("Attack").alive().nearestToPlayer();
    }

    private boolean handleAttacking() {
        var target = getTarget();
        if (target.isEmpty()) {
            Utility.sendGameMessage("No suitable target", "AutoHueycoatl");
            return false;
        }
        var ourTarget = target.get();
        if (Utility.getInteractionTarget() == ourTarget) return false;
        if (dangerousTiles.contains(Walking.getPlayerLocation())) return false;
        if (Interaction.clickNpc(ourTarget, "Attack")) {
            return Utility.sleepUntilCondition(() -> Utility.getInteractionTarget() == ourTarget, 1200, 50);
        }
        return false;
    }

    private boolean handleMoving() {
        var target = getTarget();
        if (target.isEmpty() || target.get().isDead()) return false;
        var preferredTile = optimalTile.get();
        if (preferredTile == null) return false;
        if (Walking.getPlayerLocation().equals(preferredTile)) return false;
        Walking.sceneWalk(preferredTile);
        return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().equals(preferredTile), 600, 50);
    }

    private boolean handleEating() {
        if (!canEatThisTick()) return false;
        var isBelowHpTreshold = Utility.getBoostedSkillLevel(Skill.HITPOINTS) <= nextEatAtHp;
        if (isBelowHpTreshold) {
            if (eatFood()) {
                nextEatAtHp = generateNextEatAtHp();
                lastAteOnTick.set(Utility.getTickCount());
                return true;
            }
        }
        return false;
    }

    private boolean handlePrayerRestore() {
        if (!canDrinkThisTick()) return false;
        if (Utility.getBoostedSkillLevel(Skill.PRAYER) <= nextPrayerPotionAt) {
            BoostPotion prayerBoostPot = BoostPotion.PRAYER_POTION.findInInventoryWithLowestDose().isEmpty() ? BoostPotion.SUPER_RESTORE : BoostPotion.PRAYER_POTION;
            var potionInInventory = prayerBoostPot.findInInventoryWithLowestDose();
            if (potionInInventory.isPresent()) {
                var clicked = Interaction.clickWidget(potionInInventory.get(), "Drink");
                if (clicked) {
                    nextPrayerPotionAt = generateNextPrayerPotAt();
                    lastDrankOnTick.set(Utility.getTickCount());
                }
                return clicked;
            }
        }
        return false;
    }

    private void updateMissiles() {
        List<Projectile> projectilesList = new ArrayList<>();
        var projectiles = PaistiUtils.getClient().getProjectiles();
        var rangeProjectile = plugin.getRANGED_MISSILE_ID();
        var meleeProjectile = plugin.getMELEE_MISSILE_ID();
        var magicProjectile = plugin.getMAGIC_MISSILE_ID();
        for (var projectile : projectiles) {
            Utility.sendGameMessage("found projectile");
            if (projectile.getRemainingCycles() <= 60 && projectile.getRemainingCycles() > 0) {
                if (projectile.getId() == rangeProjectile || projectile.getId() == meleeProjectile || projectile.getId() == magicProjectile) {
                    projectilesList.add(projectile);
                }
            }
        }
        projectilesList.sort(Comparator.comparingInt(Projectile::getRemainingCycles));
        if (projectilesList.isEmpty()) {
            defensivePrayer.set(null);
            return;
        }
        Utility.sendGameMessage("found projectile to pray against");
        var firstProjectile = projectilesList.get(0);
        if (firstProjectile.getId() == rangeProjectile) {
            defensivePrayer.set(PPrayer.PROTECT_FROM_MISSILES);
        } else if (firstProjectile.getId() == magicProjectile) {
            defensivePrayer.set(PPrayer.PROTECT_FROM_MAGIC);
        } else if (firstProjectile.getId() == meleeProjectile) {
            defensivePrayer.set(PPrayer.PROTECT_FROM_MELEE);
        }
    }

    private void updateDangerousTiles() {
        synchronized (dangerousTilesLock) {
            dangerousTiles.clear();
            var graphicsObjects = PaistiUtils.getClient().getGraphicsObjects();
            for (var graphicObj : graphicsObjects) {
                if (graphicObj.getId() == plugin.getPUDDLE_GRAPHIC_ID()) {
                    var tileLocation = WorldPoint.fromLocal(PaistiUtils.getClient(), graphicObj.getLocation());
                    dangerousTiles.add(tileLocation);
                }
            }
        }
    }

    private void updateOptimalTile() {
        var target = getTarget();
        if (target.isEmpty()) {
            optimalTile.set(null);
            return;
        }
        var presentTarget = target.get();
        var playerLoc = Walking.getPlayerLocation();
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        List<WorldPoint> tiles = new ArrayList<>();
        for (var dx = -20; dx <= 20; dx++) {
            for (var dy = -20; dy <= 20; dy++) {
                var tile = playerLoc.dx(dx).dy(dy);
                if (dangerousTiles.contains(tile)) continue;
                if (!reachabilityMap.isReachable(tile)) continue;
                tiles.add(tile);
            }
        }

        if (tiles.isEmpty()) {
            Utility.sendGameMessage("No tiles available", "AutoHueycoatl");
            optimalTile.set(null);
            return;
        }

        tiles.sort(Comparator.comparingInt(reachabilityMap::getCostTo));
        tiles.sort(Comparator.comparingInt(t -> t.distanceTo(presentTarget.getWorldArea())));

        optimalTile.set(tiles.get(0));
    }

    private void handlePrayers() {
        var target = getTarget();

        // Offensive prayer
        if (target.isEmpty()) {
            PPrayer.disableOffensivePrayers();
        } else {
            var bestPrayerList = PPrayer.getBestOffensivePrayers();
            if (!bestPrayerList.isEmpty()) {
                var bestPrayer = bestPrayerList.get(0);
                if (!bestPrayer.isActive()) {
                    bestPrayer.setEnabledWithoutClicks(true);
                }
            }
        }

        // Defensive prayer
        var defPrayer = defensivePrayer.get();
        if (defPrayer == null) {
            PPrayer.disableDefensivePrayers();
            return;
        }
        if (defPrayer.isActive()) return;
        defPrayer.setEnabledWithoutClicks(true);
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public boolean shouldExecuteState() {
        return true;
    }

    @Override
    public void threadedOnGameTick() {
        Utility.sendGameMessage("test");
        handlePrayers();
    }

    @Override
    public void threadedLoop() {
        handlePrayers();
        if (handleMoving()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (handleEating()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (handlePrayerRestore()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (handleAttacking()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        Utility.sleepGaussian(100, 200);
    }

    @Subscribe
    private void onGameTick(GameTick e) {
        if (!plugin.isRunning()) return;
        updateDangerousTiles();
        updateOptimalTile();
        updateMissiles();
    }
}
