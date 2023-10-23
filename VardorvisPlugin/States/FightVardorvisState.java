package com.theplug.VardorvisPlugin.States;

import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.API.NPCTickSimulation.NPCTickSimulation;
import com.theplug.PaistiUtils.API.Potions.BoostPotion;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.VardorvisPlugin.VardorvisPlugin;
import com.theplug.VardorvisPlugin.VardorvisPluginConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class FightVardorvisState implements State {
    VardorvisPlugin plugin;
    VardorvisPluginConfig config;
    private static final int RANGE_PROJECTILE_ID = 2521;
    private static final int VARDORVIS_NPC_ID = 12223;
    private static final int DANGEROUS_GROUND_GRAPHIC_ID = 2510;
    private static final int AXE_NPC_ID = 12227;
    private static final int TENDRIL_NPC_ID = 12225;
    private final Object dangerousTilesLock = new Object();
    public final Set<WorldPoint> dangerousTiles = new HashSet<>();
    private final Object predictedDangerousTilesLock = new Object();
    public final Set<WorldPoint> predictedDangerousTiles = new HashSet<>();
    public final AtomicReference<WorldPoint> optimalTile = new AtomicReference<>(null);
    public final AtomicReference<Boolean> canSafelyAttackThisTick = new AtomicReference<>(false);
    public final AtomicReference<WorldPoint> simulatedPlayerLocationAfterAttack = new AtomicReference<>(null);
    private static final AtomicReference<Integer> lastAteOnTick = new AtomicReference<>(-1);
    private static final AtomicReference<Integer> axesSpawnedOnTick = new AtomicReference<>(-1);
    private int nextPrayerPotionAt = -1;
    private int nextEatAtHp = -1;
    private final AtomicReference<PPrayer> defensivePrayer = new AtomicReference<>(null);
    private final AtomicReference<List<Widget>> bloodBlobs = new AtomicReference<>();
    private InventoryLoadout.InventoryLoadoutSetup specWeaponLoadout = null;
    private ArrayList<Integer> equipmentIdsBeforeSwitch = null;
    private static long switchedToSpecGearAt = 0;

    public enum NpcAngle {
        SOUTH,
        SOUTHWEST,
        WEST,
        NORTHWEST,
        NORTH,
        NORTHEAST,
        EAST,
        SOUTHEAST
    }


    public FightVardorvisState(VardorvisPlugin plugin, VardorvisPluginConfig config) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.nextPrayerPotionAt = generateNextPrayerPotAt();
        this.nextEatAtHp = generateNextEatAtHp();
        specWeaponLoadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.specEquipmentString());
    }

    private void updateDefensivePrayer() {
        var vardorvisHead = NPCs.search().withId(12226).first();
        if (vardorvisHead.isPresent()) {
            defensivePrayer.set(PPrayer.PROTECT_FROM_MISSILES);
        } else {
            defensivePrayer.set(PPrayer.PROTECT_FROM_MELEE);
        }
    }

    private void updateBloodBlobs() {
        Utility.runOnClientThread(() -> {
            var blobs = Widgets.search().withAction("Destroy").withParentId(54591493).result();
            bloodBlobs.set(blobs);
            return null;
        });
    }

    private boolean canPathSafelyToTile(WorldPoint destinationTile) {
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        var tiles = reachabilityMap.getPathTo(destinationTile);
        for (var tile : tiles) {
            if (dangerousTiles.contains(tile)) return false;
        }
        return true;
    }

    private WorldPoint getOptimalTileWithOnlyDangerousTiles(List<WorldPoint> tiles, WorldArea vardorvisWorldArea){
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        tiles.sort(Comparator.comparingInt(reachabilityMap::getCostTo));
        tiles.sort(Comparator.comparingInt(t -> {
            if (predictedDangerousTiles.contains(t)) return 1;
            return 0;
        }));
        var tilesWithSafePathing = tiles.stream().filter(this::canPathSafelyToTile).collect(Collectors.toList());
        if (!tilesWithSafePathing.isEmpty()) return tilesWithSafePathing.get(0);
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

    private void updateOptimalTile() {
        var vardorvis = getVardorvis();
        if (vardorvis == null) {
            optimalTile.set(null);
            return;
        }
        var vardorvisWorldArea = vardorvis.getWorldArea();
        var playerLoc = Walking.getPlayerLocation();
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        List<WorldPoint> tiles = new ArrayList<>();
        for (var dx = -20; dx <= 20; dx++) {
            for (var dy = -20; dy <= 20; dy++) {
                var tile = playerLoc.dx(dx).dy(dy);
                if (dangerousTiles.contains(tile)) continue;
                if (!reachabilityMap.isReachable(tile)) continue;
                if (vardorvisWorldArea.contains(tile)) continue;
                tiles.add(tile);
            }
        }

        if (tiles.isEmpty()) {
            optimalTile.set(null);
            return;
        }

        var tileWithDangerous = getOptimalTileWithOnlyDangerousTiles(tiles, vardorvisWorldArea);

        optimalTile.set(tileWithDangerous);
    }

    private NpcAngle getNpcAngle(int value) {
        int[] definedValues = {0, 256, 512, 768, 1024, 1280, 1536, 1792};
        int closestValue = definedValues[0];

        for (int definedValue : definedValues) {
            if (Math.abs(value - definedValue) < Math.abs(value - closestValue)) {
                closestValue = definedValue;
            }
        }

        if (closestValue == 0) return NpcAngle.SOUTH;
        if (closestValue == 256) return NpcAngle.SOUTHWEST;
        if (closestValue == 512) return NpcAngle.WEST;
        if (closestValue == 768) return NpcAngle.NORTHWEST;
        if (closestValue == 1024) return NpcAngle.NORTH;
        if (closestValue == 1280) return NpcAngle.NORTHEAST;
        if (closestValue == 1536) return NpcAngle.EAST;
        return NpcAngle.SOUTHEAST;
    }

    private NpcAngle getAxeAngle(int value) {
        if (value == 0) return NpcAngle.SOUTH;
        if (value == 255) return NpcAngle.SOUTHWEST;
        if (value == 512) return NpcAngle.WEST;
        if (value == 767) return NpcAngle.NORTHWEST;
        if (value == 1024) return NpcAngle.NORTH;
        if (value == 1023) return NpcAngle.NORTHEAST;
        if (value == 1536) return NpcAngle.EAST;
        if (value == 1791) return NpcAngle.SOUTHEAST;
        Utility.sendGameMessage("Unknown angle: " + value, "AutoVardorvis");
        return null;
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
            var TENDRIL_START_ITERATOR = 0;
            var TENDRIL_END_ITERATOR = 2;
            var tendrils = NPCs.search().withId(TENDRIL_NPC_ID).result();
            for (var tendril : tendrils) {
                var orientation = tendril.getOrientation();
                var angle = getNpcAngle(orientation);
                var axeWorldArea = tendril.getWorldArea();
                if (angle == NpcAngle.SOUTH) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = TENDRIL_START_ITERATOR; xy <= TENDRIL_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dy(-xy));
                        }
                    }
                }  else if (angle == NpcAngle.SOUTHWEST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = TENDRIL_START_ITERATOR; xy <= TENDRIL_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dx(-xy).dy(-xy));
                        }
                    }
                } else if (angle == NpcAngle.WEST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = TENDRIL_START_ITERATOR; xy <= TENDRIL_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dx(-xy));
                        }
                    }
                } else if (angle == NpcAngle.NORTHWEST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = TENDRIL_START_ITERATOR; xy <= TENDRIL_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dy(xy).dx(-xy));
                        }
                    }
                } else if (angle == NpcAngle.NORTH) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = TENDRIL_START_ITERATOR; xy <= TENDRIL_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dy(xy));
                        }
                    }
                } else if (angle == NpcAngle.NORTHEAST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = TENDRIL_START_ITERATOR; xy <= TENDRIL_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dx(xy).dy(xy));
                        }
                    }
                } else if (angle == NpcAngle.EAST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = TENDRIL_START_ITERATOR; xy <= TENDRIL_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dx(xy));
                        }
                    }
                } else if (angle == NpcAngle.SOUTHEAST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = TENDRIL_START_ITERATOR; xy <= TENDRIL_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dx(xy).dy(-xy));
                        }
                    }
                }
            }
            var tick = Utility.getTickCount() - axesSpawnedOnTick.get();
            var lastHurl = tick == 4;
            var AXE_START_ITERATOR = lastHurl ? 1 : 2;
            var AXE_END_ITERATOR = lastHurl ? 1 : 2;
            var axes = NPCs.search().withId(AXE_NPC_ID).result();
            for (var axe : axes) {
                var orientation = axe.getOrientation();
                var angle = getAxeAngle(orientation);
                var axeWorldArea = axe.getWorldArea();
                if (angle == NpcAngle.SOUTH) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dy(-xy));
                        }
                    }
                }  else if (angle == NpcAngle.SOUTHWEST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dx(-xy).dy(-xy));
                        }
                    }
                } else if (angle == NpcAngle.WEST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dx(-xy));
                        }
                    }
                } else if (angle == NpcAngle.NORTHWEST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dy(xy).dx(-xy));
                        }
                    }
                } else if (angle == NpcAngle.NORTH) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dy(xy));
                        }
                    }
                } else if (angle == NpcAngle.NORTHEAST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dx(xy).dy(xy));
                        }
                    }
                } else if (angle == NpcAngle.EAST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dx(xy));
                        }
                    }
                } else if (angle == NpcAngle.SOUTHEAST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dx(xy).dy(-xy));
                        }
                    }
                }
            }
        }
    }

    private void updatePredictedDangerousTiles() {
        synchronized (predictedDangerousTilesLock) {
            predictedDangerousTiles.clear();
            var AXE_START_ITERATOR = 3;
            var AXE_END_ITERATOR = 10;
            var axes = NPCs.search().withId(AXE_NPC_ID).result();
            for (var axe : axes) {
                var orientation = axe.getOrientation();
                var angle = getAxeAngle(orientation);
                var axeWorldArea = axe.getWorldArea();
                if (angle == NpcAngle.SOUTH) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            predictedDangerousTiles.add(tile.dy(-xy));
                        }
                    }
                }  else if (angle == NpcAngle.SOUTHWEST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            predictedDangerousTiles.add(tile.dx(-xy).dy(-xy));
                        }
                    }
                } else if (angle == NpcAngle.WEST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            predictedDangerousTiles.add(tile.dx(-xy));
                        }
                    }
                } else if (angle == NpcAngle.NORTHWEST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            predictedDangerousTiles.add(tile.dy(xy).dx(-xy));
                        }
                    }
                } else if (angle == NpcAngle.NORTH) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            predictedDangerousTiles.add(tile.dy(xy));
                        }
                    }
                } else if (angle == NpcAngle.NORTHEAST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            predictedDangerousTiles.add(tile.dx(xy).dy(xy));
                        }
                    }
                } else if (angle == NpcAngle.EAST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            predictedDangerousTiles.add(tile.dx(xy));
                        }
                    }
                } else if (angle == NpcAngle.SOUTHEAST) {
                    for (var tile : axeWorldArea.toWorldPointList()) {
                        for (var xy = AXE_START_ITERATOR; xy <= AXE_END_ITERATOR; xy++) {
                            dangerousTiles.add(tile.dx(xy).dy(-xy));
                        }
                    }
                }
            }
        }
    }

    public boolean handleStatBoostPotions() {
        if (Utility.getTickCount() - lastAteOnTick.get() < 3) return false;
        var potionsToDrink = Utility.runOnClientThread(() -> Arrays.stream(BoostPotion.values()).filter(potion -> {
            if (potion.findBoost(Skill.PRAYER) != null) return false;
            if (potion.findInInventory().isEmpty()) return false;
            return potion.isAnyCurrentBoostBelow(config.drinkPotionsBelowBoost());
        }).collect(Collectors.toList()));

        if (potionsToDrink == null || potionsToDrink.isEmpty()) return false;

        potionsToDrink.sort(Comparator.comparingInt(t -> {
            if (t.getNameMatcher().toLowerCase().contains("divine")) return 0;
            return 1;
        }));

        var drankPotion = false;
        for (var boostPotion : potionsToDrink) {
            if (boostPotion.drink()) {
                Utility.sendGameMessage("Drank " + boostPotion.name(), "AutoVardorvis");
                lastAteOnTick.set(Utility.getTickCount());
            }
            return true;
        }
        return drankPotion;
    }

    private boolean shouldSpec() {
        if (!config.useSpecialAttack()) return false;
        if (!(Varbits.BOSS_HEALTH_CURRENT >= config.specHpMinimum() || Varbits.BOSS_HEALTH_CURRENT <= config.specHpMaximum())) return false;
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

    public boolean handleSpecialAttacking() {
        if (!config.useSpecialAttack()) return false;
        var vardorvis = getVardorvis();
        if (vardorvis == null || vardorvis.isDead()) return false;
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
            Utility.sendGameMessage("Switched to spec gear", "AutoVardorvis");
            equipmentIdsBeforeSwitch = tempEqBeforeSwitch;
        }
        if (Utility.sleepUntilCondition(() -> specWeaponLoadout.getEquipmentInstructions().stream().allMatch(r -> r.isSatisfied(true)), 800, 200) && !Utility.isSpecialAttackEnabled()) {
            Utility.specialAttack();
            Utility.sendGameMessage("Special attacking", "AutoVardorvis");
            return true;
        }

        return didSwitches;
    }

    public boolean shouldRestock(boolean printMessages) {
        var vardorvis = getVardorvis();
        if (vardorvis != null) return false;

        var currentHp = Utility.getBoostedSkillLevel(Skill.HITPOINTS);
        var totalFoodHealing = getFoodItems().stream().mapToInt(item -> plugin.foodStats.getHealAmount(item.getItemId())).sum();
        var totalHitpoints = currentHp + totalFoodHealing;

        if (totalHitpoints < config.bankUnderHpAmount()) {
            if (printMessages)
                Utility.sendGameMessage(String.format("Banking because total hp (%d) is less than the configured %d", totalHitpoints, config.bankUnderHpAmount()), "AutoVardorvis");
            return true;
        }

        var prayerDoses = BoostPotion.PRAYER_POTION.getTotalDosesInInventory();
        var restoDoses = BoostPotion.SUPER_RESTORE.getTotalDosesInInventory();
        var totalPrayerPointsCount = Utility.getBoostedSkillLevel(Skill.PRAYER) +
                prayerDoses * BoostPotion.PRAYER_POTION.findBoost(Skill.PRAYER).getBoostAmount() +
                restoDoses * BoostPotion.SUPER_RESTORE.findBoost(Skill.PRAYER).getBoostAmount();
        if (totalPrayerPointsCount < config.bankUnderPrayerAmount()) {
            if (printMessages)
                Utility.sendGameMessage(String.format("Banking because prayer points amount (%d) is less than the configured %d", totalPrayerPointsCount, config.bankUnderPrayerAmount()), "AutoVardorvis");
            return true;
        }

        var boostPotionDoses = Arrays.stream(BoostPotion.values()).mapToInt(BoostPotion::getTotalDosesInInventory).sum();

        if (boostPotionDoses < config.bankUnderBoostDoseAmount()) {
            if (printMessages)
                Utility.sendGameMessage(String.format("Banking because boost potion doses left (%d) is less than the configured %d", boostPotionDoses, config.bankUnderBoostDoseAmount()), "AutoVardorvis");
            return true;
        }

        return false;
    }

    public boolean handleRegularLooting() {
        var vardorvis = getVardorvis();
        if (vardorvis != null && !vardorvis.isDead()) return false;
        var groundItems = TileItems.search().result();
        groundItems.sort(Comparator.comparingInt(itm -> -Math.max(itm.getStackGePrice(), itm.getStackHaPrice())));
        Utility.sleepUntilCondition(() -> Utility.getTickCount() - lastAteOnTick.get() >= 1, 1200, 150);
        boolean pickedUpLoot = false;
        for (var groundItem : groundItems) {
            if (Inventory.isFull()) {
                if (!groundItem.isStackable() || Inventory.search().withId(groundItem.getId()).first().isEmpty()) {
                    continue;
                }
            }
            Interaction.clickGroundItem(groundItem, "Take");
            var quantityBeforeClick = Inventory.getItemAmount(groundItem.getId());
            if (Utility.sleepUntilCondition(() -> Inventory.getItemAmount(groundItem.getId()) > quantityBeforeClick, 6000, 600)) {
                pickedUpLoot = true;
            }
        }
        return pickedUpLoot;
    }

    public NPC getVardorvis() {
        var vardorvis = NPCs.search().withId(VARDORVIS_NPC_ID).first();
        return vardorvis.orElse(null);
    }

    private boolean handleMoving() {
        var vardorvis = getVardorvis();
        if (vardorvis == null || vardorvis.isDead()) return false;
        var opTile = optimalTile.get();
        if (opTile == null) return false;
        var playerLoc = Walking.getPlayerLocation();
        if (playerLoc.equals(opTile)) return false;
        Walking.sceneWalk(opTile);
        return Utility.sleepUntilCondition(() -> !Walking.getPlayerLocation().equals(playerLoc), 600, 50);
    }

    private boolean handleBloodBlobs() {
        var blobs = bloodBlobs.get();
        if (blobs == null || blobs.isEmpty()) return false;
        for (var blob : blobs) {
            Interaction.clickWidget(blob, "Destroy");
            Utility.sleepGaussian(50, 100);
        }
        return true;
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

    public List<Widget> getFoodItems() {
        return Inventory.search().onlyUnnoted().filter((item) -> plugin.foodStats.getHealAmount(item.getItemId()) >= 8).result();
    }

    public boolean eatFood() {
        var foodItem = getFoodItems().stream().findFirst();
        return foodItem.filter(widget -> Interaction.clickWidget(widget, "Eat", "Drink")).isPresent();
    }

    private boolean handleEating() {
        log.debug("handleEating");
        if (Utility.getTickCount() - lastAteOnTick.get() < 3) return false;
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

    public boolean teleportToRestock(boolean emergencyTp) {
        if (emergencyTp) {
            for (int attempt = 1; attempt <= 3; attempt++) {
                var locationBeforeTp = Walking.getPlayerLocation();
                var teleport = Inventory.search().withName("Teleport to house").first();
                if (teleport.isEmpty()) {
                    Utility.sendGameMessage("Stopping because no teleport found", "AutoVardorvis");
                    plugin.stop();
                    return false;
                }
                if (Interaction.clickWidget(teleport.get(), "Break") &&
                        Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(locationBeforeTp) > 15, 3600, 600)) {
                    Utility.sleepGaussian(600, 1200);
                    Utility.sleepUntilCondition(() -> Utility.getGameState() != GameState.LOADING);
                    break;
                }
                Utility.sleepGaussian(500, 1000);
            }
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            var locationBeforeTp = Walking.getPlayerLocation();
            var teleport = Inventory.search().withName("Teleport to house").first();
            if (teleport.isEmpty()) {
                Utility.sendGameMessage("Stopping because no teleport found", "AutoVardorvis");
                plugin.stop();
                return false;
            }
            if (Interaction.clickWidget(teleport.get(), "Break") &&
                    Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(locationBeforeTp) > 15, 3600, 600)) {
                Utility.sleepGaussian(600, 1200);
                Utility.sleepUntilCondition(() -> Utility.getGameState() != GameState.LOADING);
                break;
            }
            Utility.sleepGaussian(500, 1000);
        }

        return true;
    }

    public boolean handleAttacking() {
        var vardorvis = getVardorvis();
        if (vardorvis == null) return false;
        if (vardorvis.isDead()) return false;
        if (Utility.getInteractionTarget() == vardorvis) return false;
        if (getDistanceToVardorvis() < 1) return false;
        boolean attackReady = plugin.attackTickTracker.getTicksUntilNextAttack() <= 1;
        if (!attackReady) return false;
        var playerLoc = Walking.getPlayerLocation();
        if (dangerousTiles.contains(playerLoc)) return false;
        if (!canSafelyAttackThisTick.get()) return false;
        if (Interaction.clickNpc(vardorvis, "Attack")) {
            return Utility.sleepUntilCondition(() -> Utility.getInteractionTarget() == vardorvis, 1200, 50);
        }
        return true;
    }

    private boolean handlePrayers(){
        var vardorvis = getVardorvis();
        if (vardorvis == null) {
            if (PPrayer.PROTECT_FROM_MELEE.isActive() || PPrayer.PROTECT_FROM_MISSILES.isActive() || PPrayer.PIETY.isActive()) {
                PPrayer.PROTECT_FROM_MELEE.setEnabled(false);
                PPrayer.PROTECT_FROM_MISSILES.setEnabled(false);
                PPrayer.PIETY.setEnabled(false);
                return true;
            }
            return false;
        }
        var defPray = defensivePrayer.get();
        if (defPray == null) return false;
        if (!defPray.isActive() || !PPrayer.PIETY.isActive()) {
            defPray.setEnabled(true);
            PPrayer.PIETY.setEnabled(true);
            return true;
        }
        return false;
    }

    private int getDistanceToVardorvis() {
        var vardorvis = getVardorvis();
        if (vardorvis == null) return -1;
        return Walking.getPlayerLocation().distanceTo(vardorvis.getWorldArea());
    }

    public boolean handleAbortFight() {
        if (Utility.getVarbitValue(Varbits.BOSS_HEALTH_CURRENT) <= 0) return false;
        var currentHp = Utility.getBoostedSkillLevel(Skill.HITPOINTS);
        var totalFoodHealing = getFoodItems().stream().mapToInt(item -> plugin.foodStats.getHealAmount(item.getItemId())).sum();
        var totalHitpoints = currentHp + totalFoodHealing;
        if (totalHitpoints <= 40) {
            Utility.sendGameMessage("Trying to emergency teleport, not enough food to continue", "AutoVardorvis");
            teleportToRestock(true);
            return true;
        }

        return false;
    }

    @Override
    public String name() {
        return "Fighting Vardorvis";
    }

    @Override
    public boolean shouldExecuteState() {
        return plugin.isInsideVardorvisArea();
    }

    @Override
    public void threadedOnGameTick() {
    }

    @Override
    public void threadedLoop() {
        if (handleAbortFight()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleMoving()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handlePrayers()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleBloodBlobs()) {
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
        if (handleStatBoostPotions()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleSpecialAttacking()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleAttacking()){
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleRegularLooting()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (shouldRestock(true)) {
            handleRegularLooting();
            if (!teleportToRestock(false)) {
                Utility.sleepGaussian(2000, 3000);
                if (!teleportToRestock(false)) {
                    Utility.sendGameMessage("Failed to teleport to restock", "AutoVardorvis");
                    plugin.stop();
                }
            }
            return;
        }
        Utility.sleepGaussian(50, 100);
    }

    @Subscribe
    private void onGameTick(GameTick e) {
        updateDefensivePrayer();
        updateDangerousTiles();
        updatePredictedDangerousTiles();
        updateBloodBlobs();
        updateOptimalTile();
        updateCanSafelyAttackThisTick();
    }

    public int generateNextPrayerPotAt() {
        return Utility.random(30, 40);
    }

    public int generateNextEatAtHp() {
        return Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.random(25, 35);
    }

    @Subscribe(priority = 5000)
    public void onActorDeath(ActorDeath actorDeath) {
        if (!plugin.runner.isRunning()) return;
        Actor actor = actorDeath.getActor();
        if (actor instanceof NPC) {
            if (actor.getName() != null) {
                if (actor.getName().toLowerCase().contains("vardorvis")) {
                    Utility.sendGameMessage("Vardorvis has died!", "AutoVardorvis");
                    plugin.setTotalKillCount(plugin.getTotalKillCount() + 1);
                }
            }
        }
    }
    @Subscribe
    public void onNpcSpawned(NpcSpawned spawnedNpc) {
        if(spawnedNpc.getNpc().getId() == AXE_NPC_ID) {
            axesSpawnedOnTick.set(Utility.getTickCount());
        }
    }
}
