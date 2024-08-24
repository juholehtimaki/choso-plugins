package com.theplug.ScurriusPlugin.States;

import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.API.NPCTickSimulation.NPCTickSimulation;
import com.theplug.PaistiUtils.API.Potions.BoostPotion;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.API.Spells.Necromancy;
import com.theplug.PaistiUtils.API.Spells.Standard;
import com.theplug.PaistiUtils.Hooks.Hooks;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.ScurriusPlugin.BankingMethod;
import com.theplug.ScurriusPlugin.CombatStyle;
import com.theplug.ScurriusPlugin.ScurriusPlugin;
import com.theplug.ScurriusPlugin.ScurriusPluginConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class FightScurriusState implements State {
    ScurriusPlugin plugin;
    ScurriusPluginConfig config;
    private static final int RANGE_PROJECTILE_ID = 2642;
    private static final int MAGE_PROJECTILE_ID = 2640;
    private static final int DANGEROUS_GROUND_GRAPHIC_ID = 2644;
    private static final int SKELETON_THRALL_ID = 10883;
    private static final int ZOMBIE_THRALL_ID = 10886;
    private static final int GHOST_THRALL_ID = 10880;
    private final Object dangerousTilesLock = new Object();
    private final Set<WorldPoint> dangerousTiles = new HashSet<>();
    private final AtomicReference<WorldPoint> optimalTile = new AtomicReference<>(null);
    private final AtomicReference<Boolean> canSafelyAttackThisTick = new AtomicReference<>(false);
    private final AtomicReference<WorldPoint> simulatedPlayerLocationAfterAttack = new AtomicReference<>(null);
    private static final AtomicReference<Integer> lastAteOnTick = new AtomicReference<>(-1);
    private static final AtomicReference<Integer> lastDrankOnTick = new AtomicReference<>(-1);
    private static final AtomicReference<Integer> scurriusDiedOnTick = new AtomicReference<>(-1);
    private int nextPrayerPotionAt = -1;
    private int nextEatAtHp = -1;
    private final AtomicReference<PPrayer> defensivePrayer = new AtomicReference<>(null);
    private InventoryLoadout.InventoryLoadoutSetup specWeaponLoadout = null;
    private ArrayList<Integer> equipmentIdsBeforeSwitch = null;
    private static long switchedToSpecGearAt = 0;
    private final AtomicReference<Boolean> deathChargeCasted = new AtomicReference<>(false);
    private final AtomicReference<Integer> nextDeathChargeHp = new AtomicReference<>(200);
    private final AtomicReference<Integer> castedThrallOnTick = new AtomicReference<>(-1);
    static final String ATE_RATS_FOOD_MESSAGE = "you take food from the food pile";
    static final String ALREADY_ATE_RATS_FOOD_MESSAGE = "you ate from the food pile recently";
    private final AtomicReference<Integer> ateRatsFoodsOnTick = new AtomicReference<>(-1);
    private final AtomicReference<Integer> killedGiantRatsOnTick = new AtomicReference<>(-1);
    private final Set<Integer> lootedItemIdsToAlch;
    private int highAlchCastOnTick = -1;
    private final AtomicReference<Integer> offensivePrayerNotNeededForTicks = new AtomicReference<>(-1);
    private final AtomicReference<Integer> defensivePrayerNotNeededForTicks = new AtomicReference<>(-1);

    private static final List<PPrayer> offensivePrayers = List.of(
            PPrayer.RIGOUR,
            PPrayer.EAGLE_EYE,
            PPrayer.AUGURY,
            PPrayer.MYSTIC_MIGHT,
            PPrayer.PIETY,
            PPrayer.CHIVALRY,
            PPrayer.ULTIMATE_STRENGTH,
            PPrayer.INCREDIBLE_REFLEXES,
            PPrayer.STEEL_SKIN
    );

    private static final List<String> foodItems = List.of(
            "Shark", "Lobster", "Tuna", "Trout"
    );


    public FightScurriusState(ScurriusPlugin plugin, ScurriusPluginConfig config) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.nextPrayerPotionAt = generateNextPrayerPotAt();
        this.nextEatAtHp = generateNextEatAtHp();
        specWeaponLoadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.specEquipmentString());
        this.lootedItemIdsToAlch = new HashSet<>(28);
    }

    private void updateMissiles() {
        List<Projectile> projectilesList = new ArrayList<>();
        var projectiles = PaistiUtils.getClient().getProjectiles();
        for (var projectile : projectiles) {
            if (projectile.getRemainingCycles() <= 60 && projectile.getRemainingCycles() > 0) {
                if (projectile.getId() == RANGE_PROJECTILE_ID || projectile.getId() == MAGE_PROJECTILE_ID) {
                    projectilesList.add(projectile);
                }
            }
        }
        projectilesList.sort(Comparator.comparingInt(Projectile::getRemainingCycles));
        if (projectilesList.isEmpty()) {
            defensivePrayer.set(null);
            return;
        }
        var firstProjectile = projectilesList.get(0);
        if (firstProjectile.getId() == RANGE_PROJECTILE_ID) {
            defensivePrayer.set(PPrayer.PROTECT_FROM_MISSILES);
        } else if (firstProjectile.getId() == MAGE_PROJECTILE_ID) {
            defensivePrayer.set(PPrayer.PROTECT_FROM_MAGIC);
        }
    }

    private boolean canPathSafelyToTile(WorldPoint destinationTile) {
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        var tiles = reachabilityMap.getPathTo(destinationTile);
        for (int i = 2; i < tiles.size() - 1; i += 2) {
            if (dangerousTiles.contains(tiles.get(i))) return false;
        }
        return true;
    }

    private WorldPoint getBestPossibleTileFromSafeTiles(List<WorldPoint> tiles, WorldArea scurriusWorldArea) {
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        tiles.sort(Comparator.comparingInt(t -> t.distanceTo(scurriusWorldArea)));
        tiles.sort(Comparator.comparingInt(reachabilityMap::getCostTo));
        var tilesWithSafePathing = tiles.stream().filter(this::canPathSafelyToTile).collect(Collectors.toList());
        if (!tilesWithSafePathing.isEmpty()) {
            return tilesWithSafePathing.get(0);
        }
        Utility.sendGameMessage("Could not find a tile with safe pathing", "AutoScurrius");
        return tiles.get(0);
    }

    private void updateCanSafelyAttackThisTick() {
        var relevantNpcs = NPCs.search().withinDistance(24).result();
        var _tickSimulation = new NPCTickSimulation(PaistiUtils.getClient(), plugin.attackTickTracker, relevantNpcs);
        var newPlayerLocation = _tickSimulation.getPlayerState().getArea().toWorldPoint();
        simulatedPlayerLocationAfterAttack.set(newPlayerLocation);

        canSafelyAttackThisTick.set(true);
        int distanceThreshold = 0;
        if (dangerousTiles.stream().anyMatch(t -> t.distanceTo(newPlayerLocation) <= distanceThreshold)) {
            canSafelyAttackThisTick.set(false);
        }
    }

    private boolean shouldAlchItem(PGroundItem groundItem) {
        if (!Standard.HIGH_LEVEL_ALCHEMY.canCast()) return false;
        if (groundItem.getName().toLowerCase().contains("spine")) return false;
        if (groundItem.getSingleItemHaPrice() > 1000) {
            return true;
        }
        return false;
    }

    private boolean handleAlching() {
        var target = getTarget();
        if (target != null) return false;
        if (lootedItemIdsToAlch.isEmpty()) return false;
        if (Utility.getTickCount() - highAlchCastOnTick < 5) return false;

        while (true) {
            if (!Standard.HIGH_LEVEL_ALCHEMY.canCast()) {
                log.debug("Can't cast high alch");
                return false;
            }

            if (lootedItemIdsToAlch.isEmpty()) return false;
            Integer itemIdToAlch = lootedItemIdsToAlch.stream().findFirst().get();
            var itemToAlchInInventory = Inventory.search().withId(itemIdToAlch).first();
            if (itemToAlchInInventory.isEmpty()) {
                lootedItemIdsToAlch.remove(itemIdToAlch);
                continue;
            }

            if (Standard.HIGH_LEVEL_ALCHEMY.castOnItem(itemToAlchInInventory.get())) {
                highAlchCastOnTick = Utility.getTickCount();
                Utility.sendGameMessage("High alching " + itemToAlchInInventory.get().getName(), "AutoScurrius");
                PaistiUtils.runOnExecutor(() -> {
                    Utility.sleepGaussian(1800, 2400);
                    InventoryTab.INVENTORY.openTab();
                    return null;
                });
                return true;
            }
        }
    }

    private boolean isTileOnSameAxis(WorldPoint tile, WorldArea area) {
        var worldAreaTiles = area.toWorldPointList();
        for (WorldPoint areaTile : worldAreaTiles) {
            if (tile.getX() == areaTile.getX() || tile.getY() == areaTile.getY()) {
                return true;
            }
        }
        return false;
    }

    private void updateOptimalTile() {
        var scurrius = getScurrius();
        if (scurrius == null) {
            optimalTile.set(null);
            return;
        }
        var scurriusWorldArea = scurrius.getWorldArea();
        var playerLoc = Walking.getPlayerLocation();
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        List<WorldPoint> tiles = new ArrayList<>();
        for (var dx = -20; dx <= 20; dx++) {
            for (var dy = -20; dy <= 20; dy++) {
                var tile = playerLoc.dx(dx).dy(dy);
                if (dangerousTiles.contains(tile)) continue;
                if (!reachabilityMap.isReachable(tile)) continue;
                if (!areGiantRatsPresent() && scurriusWorldArea.contains((tile)))
                    continue;
                if (!areGiantRatsPresent() && config.combatStyle() == CombatStyle.MELEE && !isTileOnSameAxis(tile, scurriusWorldArea))
                    continue;
                if (!areGiantRatsPresent() && scurriusWorldArea.distanceTo(tile) > plugin.attackTickTracker.getPlayerAttackRange())
                    continue;
                tiles.add(tile);
            }
        }

        if (tiles.isEmpty()) {
            optimalTile.set(null);
            return;
        }

        var opTile = getBestPossibleTileFromSafeTiles(tiles, scurriusWorldArea);

        optimalTile.set(opTile);
    }

    private void updateDangerousTiles() {
        synchronized (dangerousTilesLock) {
            dangerousTiles.clear();
            var graphicsObjects = PaistiUtils.getClient().getGraphicsObjects();
            for (var graphicObj : graphicsObjects) {
                if (graphicObj.getId() == DANGEROUS_GROUND_GRAPHIC_ID) {
                    var tileLocation = WorldPoint.fromLocal(PaistiUtils.getClient(), graphicObj.getLocation());
                    dangerousTiles.add(tileLocation);
                }
            }
        }
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

    private boolean handleStatBoostPotions() {
        if (!canDrinkThisTick()) return false;
        var potionsToDrink = Utility.runOnClientThread(() -> Arrays.stream(BoostPotion.values()).filter(potion -> {
            if (potion.findBoost(Skill.PRAYER) != null) return false;
            if (potion.findInInventory().isEmpty()) return false;
            return potion.isAnyStatBoostBelow(config.drinkPotionsBelowBoost());
        }).collect(Collectors.toList()));

        if (potionsToDrink == null || potionsToDrink.isEmpty()) return false;

        potionsToDrink.sort(Comparator.comparingInt(t -> {
            if (t.getNameMatcher().toLowerCase().contains("divine")) return 0;
            return 1;
        }));

        var drankPotion = false;
        for (var boostPotion : potionsToDrink) {
            if (boostPotion.drink()) {
                lastDrankOnTick.set(Utility.getTickCount());
            }
            return true;
        }
        return drankPotion;
    }

    private boolean shouldSpec() {
        if (!config.useSpecialAttack()) return false;
        int currBossHp = Utility.getVarbitValue(Varbits.BOSS_HEALTH_CURRENT);
        boolean isInHpThresholdRange = currBossHp >= config.specHpMinimum() && currBossHp <= config.specHpMaximum();
        if (!isInHpThresholdRange) return false;
        var specEnergy = Utility.getSpecialAttackEnergy();
        if (specEnergy < config.specEnergyMinimum()) {
            return false;
        }
        var haveRequiredGear = specWeaponLoadout.getEquipmentInstructions().stream().allMatch(req -> req.findInEquipment().isPresent() || !req.findInInventory().isEmpty());
        if (!haveRequiredGear) {
            return false;
        }
        return true;
    }

    private boolean handleSpecialAttacking() {
        if (!config.useSpecialAttack()) return false;
        var scurrius = getScurrius();
        if (scurrius == null || scurrius.isDead()) return false;
        if (equipmentIdsBeforeSwitch != null && !shouldSpec() && System.currentTimeMillis() - switchedToSpecGearAt > 700) {
            var previousEquipmentInInventory = Inventory.search().filter(item -> equipmentIdsBeforeSwitch.stream().anyMatch(id -> item.getItemId() == id)).onlyUnnoted().result();
            for (var item : previousEquipmentInInventory) {
                if (Interaction.clickWidget(item, "Wear", "Wield", "Equip")) {
                    Utility.sleepGaussian(125, 200);
                }
            }
            equipmentIdsBeforeSwitch = null;
        }
        if (!shouldSpec()) return false;

        var equipment = Equipment.search().result();
        if (config.twoHandedSpecWeapon() && equipment.stream().anyMatch(i -> i.getEquipmentSlot() == EquipmentInventorySlot.SHIELD)) {
            if (Inventory.getEmptySlots() == 0) {
                return false;
            }
        }

        ArrayList<Integer> tempEqBeforeSwitch = equipment.stream().mapToInt(EquipmentItemWidget::getItemId).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        var didSwitches = false;
        didSwitches = specWeaponLoadout.handleSwitch();
        if (didSwitches && equipmentIdsBeforeSwitch == null) {
            switchedToSpecGearAt = System.currentTimeMillis();
            Utility.sendGameMessage("Switched to spec gear", "AutoScurrius");
            equipmentIdsBeforeSwitch = tempEqBeforeSwitch;
        }
        if (Utility.sleepUntilCondition(() -> specWeaponLoadout.getEquipmentInstructions().stream().allMatch(r -> r.isSatisfied(true)), 800, 200) && !Utility.isSpecialAttackEnabled()) {
            Utility.specialAttack();
            Utility.sendGameMessage("Special attacking", "AutoScurrius");
            return true;
        }

        return didSwitches;
    }

    private boolean shouldRestock(boolean printMessages) {
        if (Utility.getTickCount() - scurriusDiedOnTick.get() < 3) return false;
        var scurrius = getScurrius();
        if (scurrius != null) return false;

        var currentHp = Utility.getBoostedSkillLevel(Skill.HITPOINTS);
        var totalFoodHealing = getFoodItems().stream().mapToInt(item -> plugin.foodStats.getHealAmount(item.getItemId())).sum();
        var totalHitpoints = currentHp + totalFoodHealing;

        if (totalHitpoints < config.bankUnderHpAmount()) {
            if (printMessages)
                Utility.sendGameMessage(String.format("Banking because total hp (%d) is less than the configured %d", totalHitpoints, config.bankUnderHpAmount()), "AutoScurrius");
            return true;
        }

        var prayerDoses = BoostPotion.PRAYER_POTION.getTotalDosesInInventory();
        var restoDoses = BoostPotion.SUPER_RESTORE.getTotalDosesInInventory();
        var totalPrayerPointsCount = Utility.getBoostedSkillLevel(Skill.PRAYER) +
                prayerDoses * BoostPotion.PRAYER_POTION.findBoost(Skill.PRAYER).getBoostAmount() +
                restoDoses * BoostPotion.SUPER_RESTORE.findBoost(Skill.PRAYER).getBoostAmount();
        if (totalPrayerPointsCount < config.bankUnderPrayerAmount()) {
            if (printMessages)
                Utility.sendGameMessage(String.format("Banking because prayer points amount (%d) is less than the configured %d", totalPrayerPointsCount, config.bankUnderPrayerAmount()), "AutoScurrius");
            return true;
        }

        var boostPotionDoses = Arrays.stream(BoostPotion.values()).filter(b -> Arrays.stream(b.getBoosts()).noneMatch(boost -> boost.getSkill() == Skill.PRAYER)).mapToInt(BoostPotion::getTotalDosesInInventory).sum();
        if (boostPotionDoses < config.bankUnderBoostDoseAmount()) {
            if (printMessages)
                Utility.sendGameMessage(String.format("Banking because boost potion doses left (%d) is less than the configured %d", boostPotionDoses, config.bankUnderBoostDoseAmount()), "AutoScurrius");
            return true;
        }

        return false;
    }

    private boolean handleSpeedRats() {
        var giantRats = NPCs.search().withName("Giant rat").alive().result();
        if (giantRats.isEmpty()) return false;
        if (Utility.getTickCount() - killedGiantRatsOnTick.get() < 5) return false;
        Utility.sleepUntilCondition(() -> plugin.attackTickTracker.getTicksUntilNextAttack() <= 1, 3000, 1);
        var attackedRat = false;
        NPC previousRat = null;
        Utility.sendGameMessage("Attempting to slay giant rats", "AutoScurrius");
        while (!giantRats.isEmpty()) {
            // Sort the remaining rats based on their distance to the player or the previous rat
            NPC finalPreviousRat = previousRat;
            giantRats.sort(Comparator.comparingInt(r -> calculateDistanceToTarget(r, finalPreviousRat)));

            NPC closestRat = giantRats.get(0); // Get the closest rat

            // Attack the closest rat
            if (Interaction.clickNpc(closestRat, "Attack")) {
                // Get the current tick count
                var tick = Utility.getTickCount();

                // Define a condition for sleeping until certain conditions are met
                attackedRat = Utility.sleepUntilCondition(() -> {
                    // Check if the tick count has advanced since the attack
                    boolean tickAdvanced = Utility.getTickCount() > tick;

                    // Check if the interaction target is the closest rat
                    boolean isTargetClosestRat = closestRat.equals(Utility.getInteractionTarget());

                    // Check if the player is in melee range or within attack range of the rat
                    boolean isInRange = plugin.attackTickTracker.getPlayerAttackRange() == 1 ? closestRat.getWorldArea().isInMeleeDistance(Walking.getPlayerLocation()) : Walking.getPlayerLocation().distanceTo(closestRat.getWorldArea()) <= plugin.attackTickTracker.getPlayerAttackRange();

                    // Combine conditions
                    return tickAdvanced && isTargetClosestRat && isInRange;
                }, 3000, 100);
            }

            // Update the previous rat
            previousRat = closestRat;

            // Remove the attacked rat from the list
            giantRats.remove(closestRat);
            if (attackedRat) killedGiantRatsOnTick.set(Utility.getTickCount());
        }
        return attackedRat;
    }

    private int calculateDistanceToTarget(NPC rat, NPC target) {
        if (target == null) {
            // If no previous rat, calculate distance to player
            return Walking.getPlayerLocation().distanceTo(rat.getWorldLocation());
        } else {
            // Otherwise, calculate distance to the previous rat
            return target.getWorldLocation().distanceTo(rat.getWorldLocation());
        }
    }

    private boolean handleToggleRun() {
        if (Walking.isRunEnabled() || Walking.getRunEnergy() < 15) return false;
        return Walking.setRun(true);
    }

    private boolean shouldLootItem(PGroundItem item) {
        return shouldEatItem(item) || item.getName().contains("spine") || item.getName().toLowerCase().contains("long bone") || item.getName().toLowerCase().contains("curved bone") || item.getStackGePrice() > config.lootItemsAboveValue();
    }

    private boolean handleRegularLooting() {
        var target = getTarget();
        if (target != null && !target.isDead()) return false;
        handleVialDropping();
        var groundItems = TileItems.search().result().stream().filter(this::shouldLootItem).sorted(Comparator.comparingInt(itm -> -Math.max(itm.getStackGePrice(), itm.getStackHaPrice()))).sorted(Comparator.comparingInt(itm -> {
            if (itm.getName().contains("spine")) {
                return 0;
            }
            if (shouldAlchItem(itm)) {
                return 1;
            }
            if (shouldEatItem(itm)) {
                return 2;
            }
            return 3;
        })).collect(Collectors.toList());
        Utility.sleepUntilCondition(() -> Utility.getTickCount() - lastAteOnTick.get() >= 1, 1200, 150);
        boolean pickedUpLoot = false;
        for (var groundItem : groundItems) {
            if (!shouldLootItem(groundItem)) {
                continue;
            }
            if (Inventory.getEmptySlots() <= 1) {
                if (!groundItem.isStackable() || Inventory.search().withId(groundItem.getId()).first().isEmpty()) {
                    if (Inventory.isFull() || (!shouldAlchItem(groundItem) && !shouldEatItem(groundItem))) {
                        if (groundItem.getId() == ItemID.SCURRIUS_SPINE) {
                            var itemToDrop = Inventory.search().filter(i -> PaistiUtils.getInstance().getFoodStats().getHealAmount(i.getItemId()) >= 4).first();
                            if (itemToDrop.isPresent() && Utility.getItemMaxPrice(itemToDrop.get().getItemId()) < 10000) {
                                Interaction.clickWidget(itemToDrop.get(), "Drop");
                                Utility.sleepUntilCondition(() -> Inventory.getEmptySlots() > 1, 1200, 300);
                            }
                        }

                        continue;
                    }
                }
            }
            Interaction.clickGroundItem(groundItem, "Take");
            var quantityBeforeClick = Inventory.getItemAmount(groundItem.getId());
            if (Utility.sleepUntilCondition(() -> Inventory.getItemAmount(groundItem.getId()) > quantityBeforeClick, 6000, 600)) {
                pickedUpLoot = true;
                if (shouldAlchItem(groundItem)) {
                    lootedItemIdsToAlch.add(groundItem.getId());
                    log.debug("Added item to alch list: " + groundItem.getName());
                } else if (shouldEatItem(groundItem)) {
                    Utility.sleepUntilCondition(this::canEatThisTick);
                    var lootedFoodItem = Inventory.search().withId(groundItem.getId()).first();
                    if (lootedFoodItem.isPresent()) {
                        Interaction.clickWidget(lootedFoodItem.get(), "Eat");
                        lastAteOnTick.set(Utility.getTickCount());
                    }
                }
            }
        }
        return pickedUpLoot;
    }

    private boolean shouldEatItem(PGroundItem item) {
        if (!foodItems.contains(item.getName())) return false;
        return Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.getBoostedSkillLevel(Skill.HITPOINTS) >= 10;
    }

    private boolean handleRatFood() {
        var target = getTarget();
        if (target != null && !target.isDead()) return false;
        if (ateRatsFoodsOnTick.get() != -1 && Utility.getTickCount() - ateRatsFoodsOnTick.get() < 1000) {
            return false;
        }
        if (Utility.getTickCount() - scurriusDiedOnTick.get() < 5) {
            return false;
        }
        if (Utility.getBoostedSkillLevel(Skill.HITPOINTS) >= Utility.getRealSkillLevel(Skill.HITPOINTS) * 0.6) {
            return false;
        }
        var ratFood = TileObjects.search().withName("Food pile").withAction("Eat").reachable().nearestToPlayer();
        if (ratFood.isEmpty()) {
            Utility.sendGameMessage("No rat food found", "AutoScurrius");
            return false;
        }
        var hp = Utility.getBoostedSkillLevel(Skill.HITPOINTS);
        Interaction.clickTileObject(ratFood.get(), "Eat");
        return Utility.sleepUntilCondition(() -> Utility.getBoostedSkillLevel(Skill.HITPOINTS) > hp, 5000, 100);
    }

    private NPC getScurrius() {
        var scurrius = NPCs.search().withName("Scurrius").first();
        return scurrius.orElse(null);
    }

    private boolean handleDodging() {
        if (dangerousTiles.contains(Walking.getPlayerLocation())) {
            return handleMoving();
        }
        return false;
    }

    private boolean handleMoving() {
        var scurrius = getScurrius();
        if (scurrius == null || scurrius.isDead()) return false;
        var opTile = optimalTile.get();
        if (opTile == null) return false;
        var playerLoc = Walking.getPlayerLocation();
        if (playerLoc.equals(opTile)) return false;
        Walking.sceneWalk(opTile);
        return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().equals(opTile), 600, 50);
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

    private void simulateAndSetPrayers() {
        var mostImportantPray = getMostImportantPrayer();
        boolean didPray = false;
        if (mostImportantPray == null) {
            defensivePrayerNotNeededForTicks.accumulateAndGet(1, Integer::sum);
            if (!plugin.config.reducePrayerFlicking()
                    || defensivePrayerNotNeededForTicks.get() >= 4) {
                PPrayer.disableDefensivePrayers();
            }
        } else {
            switch (mostImportantPray.getAttackType()) {
                case MELEE:
                    didPray = !PPrayer.PROTECT_FROM_MELEE.isActive() && PPrayer.PROTECT_FROM_MELEE.setEnabled(true);
                    if (didPray) {
                        defensivePrayerNotNeededForTicks.set(0);
                        log.debug("Enabled melee prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
                    }
                    break;
                case MAGIC:
                    didPray = !PPrayer.PROTECT_FROM_MAGIC.isActive() && PPrayer.PROTECT_FROM_MAGIC.setEnabled(true);
                    if (didPray) {
                        defensivePrayerNotNeededForTicks.set(0);
                        log.debug("Enabled magic prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
                    }
                    break;
                case RANGED:
                    didPray = !PPrayer.PROTECT_FROM_MISSILES.isActive() && PPrayer.PROTECT_FROM_MISSILES.setEnabled(true);
                    if (didPray) {
                        defensivePrayerNotNeededForTicks.set(0);
                        log.debug("Enabled ranged prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
                    }
                    break;
            }
        }
    }

    private NPCTickSimulation.PrayAgainstResult getMostImportantPrayer() {
        return Utility.runOncePerClientTickTask(() -> {
            var client = PaistiUtils.getClient();
            var relevantNpcs = NPCs.search().withinDistance(17).result();

            var _tickSimulation = new NPCTickSimulation(client, plugin.attackTickTracker, relevantNpcs);
            _tickSimulation.getPlayerState().setInteracting(plugin.attackTickTracker.getPredictedInteractionTarget());
            List<NPCTickSimulation.PrayAgainstResult> prayThisTick = new ArrayList<>();

            _tickSimulation.simulateNpcsTick(client);
            var prayAgainst = _tickSimulation.shouldPrayAgainst(client);
            if (prayAgainst != null) {
                prayThisTick.add(prayAgainst);
            }

            _tickSimulation.simulatePlayerTick(client);

            prayAgainst = _tickSimulation.shouldPrayAgainst(client);
            if (prayAgainst != null) {
                prayThisTick.add(prayAgainst);
            }

            return prayThisTick.isEmpty() ? null : prayThisTick.stream().max(Comparator.comparingInt(NPCTickSimulation.PrayAgainstResult::getPriority)).get();
        });
    }

    @Override
    public void threadedOnGameTick() {
        Utility.sleep(200);
        setOffensivePrayers();
        Utility.sleep(60);
        var scurrius = getScurrius();
        var scurriusPose = scurrius != null ? scurrius.getPoseAnimation() : -1;
        if (defensivePrayer.get() == null && scurriusPose != 10689) {
            simulateAndSetPrayers();
        } else if (defensivePrayer.get() != null) {
            defensivePrayer.get().setEnabled(true);
            defensivePrayerNotNeededForTicks.set(0);
        } else {
            defensivePrayerNotNeededForTicks.accumulateAndGet(1, Integer::sum);
            if (!plugin.config.reducePrayerFlicking()
                    || defensivePrayerNotNeededForTicks.get() >= 4) {
                PPrayer.disableDefensivePrayers();
            }
        }
    }

    private void setOffensivePrayers() {
        if (plugin.attackTickTracker.getPredictedInteractionTarget() instanceof NPC
                || Utility.getInteractionTarget() instanceof NPC) {
            var npc = (NPC) Utility.getInteractionTarget();
            if (npc == null) npc = (NPC) plugin.attackTickTracker.getPredictedInteractionTarget();
            if (npc != null && !npc.isDead() && plugin.attackTickTracker.getTicksUntilNextAttack() <= 1) {
                offensivePrayerNotNeededForTicks.set(0);
                if (config.combatStyle() == CombatStyle.RANGE) {
                    enableOffensiveRangePray(false);
                    return;
                } else if (config.combatStyle() == CombatStyle.MAGE) {
                    enableOffensiveMagePray(false);
                    return;
                } else {
                    enableOffensiveMeleePray(false);
                    return;
                }
            }
        }

        offensivePrayerNotNeededForTicks.accumulateAndGet(1, Integer::sum);
        if (!plugin.config.reducePrayerFlicking()
                || offensivePrayerNotNeededForTicks.get() >= 5
        ) {
            disableAllOffensivePrayers();
        }
    }

    private boolean disableAllOffensivePrayers() {
        var didDisable = false;
        for (var prayer : offensivePrayers) {
            if (prayer.isActive()) {
                prayer.setEnabled(false);
                didDisable = true;
                Utility.sleepGaussian(10, 30);
            }
        }

        return didDisable;
    }

    private boolean enableOffensiveRangePray(boolean allowThickSkin) {
        if (PPrayer.RIGOUR.canUse()) {
            if (!PPrayer.RIGOUR.isActive()) {
                PPrayer.RIGOUR.setEnabled(true);
                return true;
            }
        } else {
            var toggled = false;
            if (!PPrayer.EAGLE_EYE.isActive()) {
                toggled = PPrayer.EAGLE_EYE.setEnabled(true);
            }
            if (allowThickSkin) {
                if (!PPrayer.STEEL_SKIN.isActive()) {
                    if (toggled) Utility.sleepGaussian(10, 30);
                    toggled = PPrayer.STEEL_SKIN.setEnabled(true) || toggled;
                }
            }
            return toggled;
        }
        return false;
    }

    private boolean enableOffensiveMagePray(boolean allowThickSkin) {
        if (PPrayer.AUGURY.canUse()) {
            if (!PPrayer.AUGURY.isActive()) {
                PPrayer.AUGURY.setEnabled(true);
                return true;
            }
        } else {
            var toggled = false;
            if (!PPrayer.MYSTIC_MIGHT.isActive()) {
                toggled = PPrayer.MYSTIC_MIGHT.setEnabled(true);
            }
            if (allowThickSkin) {
                if (!PPrayer.STEEL_SKIN.isActive()) {
                    if (toggled) Utility.sleepGaussian(10, 30);
                    toggled = PPrayer.STEEL_SKIN.setEnabled(true) || toggled;
                }
            }
            return toggled;
        }
        return false;
    }

    private boolean enableOffensiveMeleePray(boolean allowThickSkin) {
        if (PPrayer.PIETY.canUse()) {
            if (!PPrayer.PIETY.isActive()) {
                PPrayer.PIETY.setEnabled(true);
                return true;
            }
        } else if (PPrayer.CHIVALRY.canUse()) {
            if (!PPrayer.CHIVALRY.isActive()) {
                PPrayer.CHIVALRY.setEnabled(true);
                return true;
            }
        } else {
            var toggled = false;
            if (!PPrayer.ULTIMATE_STRENGTH.isActive()) {
                toggled = PPrayer.ULTIMATE_STRENGTH.setEnabled(true);
            }
            if (!PPrayer.INCREDIBLE_REFLEXES.isActive()) {
                if (toggled) Utility.sleepGaussian(10, 30);
                toggled = PPrayer.INCREDIBLE_REFLEXES.setEnabled(true) || toggled;
            }
            if (allowThickSkin) {
                if (!PPrayer.STEEL_SKIN.isActive()) {
                    if (toggled) Utility.sleepGaussian(10, 30);
                    toggled = PPrayer.STEEL_SKIN.setEnabled(true) || toggled;
                }
            }
            return toggled;
        }
        return false;
    }

    private boolean handleThralls() {
        var selectedThrall = config.selectedThrall().getThrall();
        if (selectedThrall == null) return false;
        var scurrius = getScurrius();
        if (scurrius == null) return false;
        if (Utility.getTickCount() - castedThrallOnTick.get() <= 10) return false;
        var thrall = NPCs.search().withIdInArr(SKELETON_THRALL_ID, ZOMBIE_THRALL_ID, GHOST_THRALL_ID).first();
        if (thrall.isPresent()) return false;
        if (selectedThrall.tryCast("Cast")) {
            castedThrallOnTick.set(Utility.getTickCount());
            return true;
        }
        return false;
    }

    private boolean handleDeathCharge() {
        if (!plugin.config.useDeathCharge()) return false;
        if (deathChargeCasted.get()) return false;
        var scurrius = getScurrius();
        if (scurrius == null) return false;
        int currBossHp = Utility.getVarbitValue(Varbits.BOSS_HEALTH_CURRENT);
        if (currBossHp > nextDeathChargeHp.get() || currBossHp == 0) return false;
        if (Necromancy.DEATH_CHARGE.tryCast("Cast")) {
            deathChargeCasted.set(true);
            nextDeathChargeHp.set(generateNextDeathChargeAt());
            return true;
        }
        return false;
    }

    private List<Widget> getFoodItems() {
        return Inventory.search().onlyUnnoted().filter((item) -> plugin.foodStats.getHealAmount(item.getItemId()) >= 8).result();
    }

    private boolean eatFood() {
        var foodItem = getFoodItems().stream().findFirst();
        return foodItem.filter(widget -> Interaction.clickWidget(widget, "Eat", "Drink")).isPresent();
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

    private boolean areGiantRatsPresent() {
        if (!config.prioGiantRats()) return false;
        var giantRats = NPCs.search().withName("Giant rat").alive().result();
        return !giantRats.isEmpty();
    }

    private boolean teleportToRestock() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (config.bankingMethod() == BankingMethod.FEROX) {
                if (handleRingOfDuelingRestock()) {
                    Utility.sleepGaussian(600, 1200);
                    break;
                }
            }
            if (config.bankingMethod() == BankingMethod.HOUSE) {
                if (handleHouseTeleRestock()) {
                    break;
                }
            }
            Utility.sleepGaussian(1000, 2000);
        }
        return true;
    }

    private boolean handleHouseTeleRestock() {
        var locationBeforeTp = Walking.getPlayerLocation();
        var teleport = Inventory.search().withName("Teleport to house").first();
        if (teleport.isEmpty()) {
            Utility.sendGameMessage("Stopping because no house teleport tab found", "AutoScurrius");
            plugin.stop();
            return false;
        }
        if (Interaction.clickWidget(teleport.get(), "Break") &&
                Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(locationBeforeTp) > 15, 3600, 600)) {
            Utility.sleepGaussian(600, 1200);
            Utility.sleepUntilCondition(House::isPlayerInsideHouse);
            return true;
        }
        return false;
    }

    private boolean handleRingOfDuelingRestock() {
        var ringOfDueling = Inventory.search().matchesWildcard("ring of dueling*").first();
        if (ringOfDueling.isEmpty()) {
            Utility.sendGameMessage("Stopping because no ring of dueling found", "AutoScurrius");
            plugin.stop();
            return false;
        }
        var locationBeforeTp = Walking.getPlayerLocation();
        Interaction.clickWidget(ringOfDueling.get(), "Rub");
        Utility.runOncePerClientTickTask(() -> {
            MenuAction menuAction = MenuAction.WIDGET_CONTINUE;
            int p0 = 3;
            int p1 = 14352385;
            int itemId = -1;
            int objectId = 0;
            String opt = "Continue";
            Hooks.invokeMenuAction(p0, p1, menuAction.getId(), objectId, itemId, opt, "", 0, 0);
            return null;
        });
        return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(locationBeforeTp) > 15, 3600, 600);
    }

    private Actor getTarget() {
        Actor target = null;
        if (config.prioGiantRats()) {
            var giantRat = NPCs.search().withName("Giant rat").alive().nearestToPlayer();
            if (giantRat.isPresent()) {
                target = giantRat.get();
            }
        }
        if (target == null) {
            var scurrius = getScurrius();
            if (scurrius != null && !scurrius.isDead()) {
                target = scurrius;
            }
        }
        if (target == null) {
            var giantRat = NPCs.search().withName("Giant rat").alive().nearestToPlayer();
            target = giantRat.orElse(null);
        }

        if (target == null) {
            offensivePrayerNotNeededForTicks.set(999);
            defensivePrayerNotNeededForTicks.set(999);
        }

        return target;
    }

    private boolean handleAttacking() {
        var target = getTarget();
        if (target == null) return false;
        if (Utility.getInteractionTarget() == target) return false;
        boolean attackReady = plugin.attackTickTracker.getTicksUntilNextAttack() <= 1;
        if (!Objects.requireNonNull(target.getName()).equalsIgnoreCase("giant rat") && !attackReady) return false;
        var playerLoc = Walking.getPlayerLocation();
        if (dangerousTiles.contains(playerLoc)) return false;
        if (!canSafelyAttackThisTick.get()) return false;
        if (Interaction.clickNpc((NPC) target, "Attack")) {
            return Utility.sleepUntilCondition(() -> Utility.getInteractionTarget() == target, 1200, 50);
        }
        return true;
    }

    private boolean handleAbortFight() {
        if (Utility.getVarbitValue(Varbits.BOSS_HEALTH_CURRENT) <= 0) return false;
        var currentHp = Utility.getBoostedSkillLevel(Skill.HITPOINTS);
        var totalFoodHealing = getFoodItems().stream().mapToInt(item -> plugin.foodStats.getHealAmount(item.getItemId())).sum();
        var totalHitpoints = currentHp + totalFoodHealing;
        if (totalHitpoints <= 20) {
            Utility.sendGameMessage("Trying to emergency teleport, not enough food to continue", "AutoScurrius");
            teleportToRestock();
            return true;
        }

        return false;
    }

    private static int parseTicksLeft(String message) {
        // Define a regular expression pattern to match the time duration in minutes or seconds
        Pattern pattern = Pattern.compile("(\\d+)\\s*(minute|second)s*");
        Matcher matcher = pattern.matcher(message);

        // Find the first occurrence of the time duration in the message
        if (matcher.find()) {
            int amount = Integer.parseInt(matcher.group(1)); // Extract the numerical part
            String unit = matcher.group(2); // Extract the unit part

            // Convert the time duration to ticks
            switch (unit) {
                case "second":
                    return amount * 1000 / 600; // Convert seconds to ticks
                case "minute":
                    return amount * 60 * 1000 / 600; // Convert minutes to ticks
                default:
                    return 0; // Invalid unit
            }
        } else {
            return 0; // No time duration found
        }
    }

    private void handleExit() {
        var exit = TileObjects.search().withId(14204).nearestToPlayer();
        exit.ifPresent(tileObject -> Interaction.clickTileObject(tileObject, "Quick-escape"));
    }

    @Override
    public String name() {
        return "Fighting Scurrius";
    }

    @Override
    public boolean shouldExecuteState() {
        return plugin.isInsideScurriusArea();
    }

    private boolean hasBoneWeapon() {
        return Equipment.search().withId(ItemID.BONE_STAFF, ItemID.BONE_SHORTBOW, ItemID.BONE_MACE).first().isPresent();
    }

    @Override
    public void threadedLoop() {
        if (handleAbortFight()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleDodging()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handlePrayerRestore()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleEating()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleToggleRun()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleStatBoostPotions()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleSpecialAttacking()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleThralls()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleDeathCharge()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (hasBoneWeapon() && handleSpeedRats()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleAttacking()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleRegularLooting()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (shouldRestock(true)) {
            handleRegularLooting();
            if (!teleportToRestock()) {
                Utility.sleepGaussian(2000, 3000);
                if (!teleportToRestock()) {
                    Utility.sendGameMessage("Failed to teleport to restock", "AutoScurrius");
                    handleExit();
                    plugin.stop();
                }
            }
            return;
        }
        if (handleRatFood()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleAlching()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        Utility.sleepGaussian(50, 100);
    }

    @Subscribe
    private void onGameTick(GameTick e) {
        updateMissiles();
        updateDangerousTiles();
        updateOptimalTile();
        updateCanSafelyAttackThisTick();
    }

    private int generateNextPrayerPotAt() {
        int prayerLevel = Utility.getRealSkillLevel(Skill.PRAYER);
        int minThreshold = (int) (prayerLevel * 0.2);
        int maxThreshold = (int) (prayerLevel * 0.4);

        return Utility.random(minThreshold, maxThreshold);
    }

    private int generateNextDeathChargeAt() {
        return Utility.random(76, 204);
    }

    private void handleVialDropping() {
        var vials = Inventory.search().withId(ItemID.VIAL).result();
        if (vials.isEmpty()) return;
        for (var vial : vials) {
            Interaction.clickWidget(vial, "Drop");
            Utility.sleepGaussian(150, 300);
        }
    }

    private int generateNextEatAtHp() {
        int hpLevel = Utility.getRealSkillLevel(Skill.HITPOINTS);
        int minThreshold = (int) (hpLevel * 0.4);
        int maxThreshold = (int) (hpLevel * 0.6);
        int randomThreshold = Utility.random(minThreshold, maxThreshold);

        return Math.max(15, randomThreshold);
    }

    @Subscribe(priority = 5000)
    public void onActorDeath(ActorDeath actorDeath) {
        if (!plugin.runner.isRunning()) return;
        Actor actor = actorDeath.getActor();
        if (actor instanceof NPC) {
            if (actor.getName() != null) {
                if (actor.getName().toLowerCase().contains("scurrius")) {
                    Utility.sendGameMessage("Scurrius has died!", "AutoScurrius");
                    plugin.setTotalKillCount(plugin.getTotalKillCount() + 1);
                    scurriusDiedOnTick.set(Utility.getTickCount());
                    deathChargeCasted.set(false);
                }
            }
        }
    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;
        if (event.getMessage().toLowerCase().contains(ATE_RATS_FOOD_MESSAGE.toLowerCase())) {
            ateRatsFoodsOnTick.set(Utility.getTickCount());
        }
        if (event.getMessage().toLowerCase().contains(ALREADY_ATE_RATS_FOOD_MESSAGE.toLowerCase())) {
            Utility.sendGameMessage("Already ate rat food", "AutoScurrius");
            var calcTickWhenRatFoodWasEaten = parseTicksLeft(event.getMessage().toLowerCase());
            ateRatsFoodsOnTick.set(Utility.getTickCount() + (1000 - calcTickWhenRatFoodWasEaten + 5));
        }
    }
}
