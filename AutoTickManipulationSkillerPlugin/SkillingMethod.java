package com.theplug.AutoTickManipulationSkillerPlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@DontObfuscate
@AllArgsConstructor
public enum SkillingMethod {
    SMALL_NET_FISHING("Net fishing", (pluginConfig) -> SkillingMethod.hasSmallFishingNet(), (pluginConfig) -> SkillingMethod.handleFishing("Net", Walking.getPlayerLocation())),
    BAIT_FISHING("3T Bait fishing", (pluginConfig) -> SkillingMethod.hasFishingRodAndBaits(), (pluginConfig) -> SkillingMethod.handleFishing("Bait", Walking.getPlayerLocation())),
    LURE_FISHING("3T Lure fishing", (pluginConfig) -> SkillingMethod.hasFlyFishingRodAndFeathers(), (pluginConfig) -> SkillingMethod.handleFishing("Lure", Walking.getPlayerLocation())),
    BARBARIAN_FISHING("3T Use-rod fishing", (pluginConfig) -> SkillingMethod.hasBarbarianRodAndFeathers(), (pluginConfig) -> SkillingMethod.handleFishing("Use-rod", Walking.getPlayerLocation())),
    REGULAR_CHOPPING("3T Regular tree chopping", (pluginConfig) -> SkillingMethod.hasAxe(), (pluginConfig -> SkillingMethod.handleChopping("Tree", Walking.getPlayerLocation(), 10))),
    OAK_CHOPPING("3T Oak chopping", (pluginConfig) -> SkillingMethod.hasAxe(), (pluginConfig) -> SkillingMethod.handleChopping("Oak tree", Walking.getPlayerLocation(), 10)),
    WILLOW_CHOPPING("3T Willow chopping", (pluginConfig) -> SkillingMethod.hasAxe(), (pluginConfig) -> SkillingMethod.handleChopping("Willow tree", Walking.getPlayerLocation(), 10)),
    TEAK_CHOPPING("3T Teak chopping", (pluginConfig) -> SkillingMethod.hasAxe(), (pluginConfig) -> SkillingMethod.handleChopping("Teak tree", Walking.getPlayerLocation(), 1)),
    COOKING("2T Cooking", SkillingMethod::hasRawFood, SkillingMethod::handleCooking);

    private final String name;
    private final Function<AutoTickManipulationSkillerPluginConfig, Boolean> _hasRequiredItems;
    private final Function<AutoTickManipulationSkillerPluginConfig, Boolean> _handleSkilling;

    public boolean hasRequiredItems(AutoTickManipulationSkillerPluginConfig config) {
        return _hasRequiredItems.apply(config);
    }

    public boolean handleSkilling(AutoTickManipulationSkillerPluginConfig config) {
        return _handleSkilling.apply(config);
    }

    private static boolean hasRawFood(AutoTickManipulationSkillerPluginConfig config) {
        var itemsToCook = getRawFoodItems(config.rawFood());
        var rawFishInInventory = Inventory.search().nameContains("raw").result()
                .stream()
                .filter(item -> itemsToCook.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());
        if (rawFishInInventory.size() > 0) return true;
        if (!Bank.isBankUpdated()) return true;
        var rawFishInBank = Bank.search().nameContains("raw").result()
                .stream()
                .filter(item -> itemsToCook.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());
        if (rawFishInBank.size() == 0) {
            Utility.sendGameMessage("Out of fish to cook", "AutoTickSkiller");
            return false;
        }
        return true;
    }

    private static boolean handleBanking(List<String> itemsToCook) {
        if (!Bank.isOpen()) {
            Bank.openBank();
            Utility.sleepUntilCondition(Bank::isOpen);
        }
        Bank.depositInventory();
        Utility.sleepUntilCondition(() -> Inventory.getEmptySlots() > 0);
        var rawFishInBank = Bank.search().nameContains("raw").result()
                .stream()
                .filter(item -> itemsToCook.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());
        if (rawFishInBank.size() == 0) {
            return false;
        }
        var itemQuantity = Bank.getQuantityInBank(rawFishInBank.get(0).getItemId());
        Bank.withdraw(rawFishInBank.get(0).getItemId(), Math.min(itemQuantity, Inventory.getEmptySlots()));
        if (Utility.sleepUntilCondition(() -> Inventory.search().nameContains("raw").result().size() > 0)) {
            return true;
        }
        return false;
    }

    public static List<String> getRawFoodItems(String input) {
        return Arrays.asList(input.split("\n"));
    }

    public static WorldPoint getCookingLocation() {
        var distToDen = Walking.getPlayerLocation().distanceTo(new WorldPoint(3043, 4972, 1));
        var distToHosidius = Walking.getPlayerLocation().distanceTo(new WorldPoint(1677, 3621, 0));
        return distToDen < distToHosidius ? new WorldPoint(3043, 4972, 1) : new WorldPoint(1677, 3621, 0);
    }

    private static boolean handleCooking(AutoTickManipulationSkillerPluginConfig config) {
        var itemsToCook = getRawFoodItems(config.rawFood());
        var fishToCook = Inventory.search().nameContains("raw").result()
                .stream()
                .filter(item -> itemsToCook.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());
        if (fishToCook.isEmpty()) {
            if (!handleBanking(itemsToCook)) {
                return false;
            }
        }

        var rawFood = Inventory.search().nameContains("raw").result()
                .stream()
                .filter(item -> itemsToCook.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());

        if (rawFood.size() == 0) return false;

        var rawFoodId = rawFood.get(0).getItemId();

        var cookingLocation = getCookingLocation();

        if (!Walking.getPlayerLocation().equals(cookingLocation)) {
            Walking.sceneWalk(cookingLocation);
            if (!Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().equals(cookingLocation))) {
                return false;
            }
        }
        for (var raw : rawFood) {
            if (raw == rawFood.get(rawFood.size() - 1)) continue;
            Interaction.clickWidget(raw, "Drop");
            Utility.sleepGaussian(110, 240);
        }

        if (!Utility.sleepUntilCondition(() -> Inventory.getItemAmount(rawFoodId) <= 1, 1200)) {
            return false;
        }
        return Utility.sleepUntilCondition(() -> {
            int rawInInventory = Inventory.getItemAmount(rawFoodId);
            if (rawInInventory > 0) {
                var fire = TileObjects.search().withId(43475, 21302).nearestToPlayer();
                if (fire.isEmpty()) {
                    return true;
                }
                Interaction.clickTileObject(fire.get(), "Cook");
                Utility.sleepUntilCondition(() -> Inventory.getItemAmount(rawFoodId) < rawInInventory, 3600, 200);
                return false;
            } else {
                var rawUnderPlayer2 = TileItems.search().withId(rawFoodId).withinDistance(0).result();
                if (rawUnderPlayer2.isEmpty()) {
                    return true;
                }

                Interaction.clickGroundItem(rawUnderPlayer2.get(0), "Take");
                Utility.sleepUntilCondition(() -> Inventory.getItemAmount(rawFoodId) > 0, 3000, 200);
            }

            return false;
        }, 160000, 100);
    }

    private static boolean hasBarbarianRodAndFeathers() {
        var hasRequiredItems = Inventory.getItemAmount(ItemID.FEATHER) > 0 && Inventory.getItemAmount(ItemID.BARBARIAN_ROD) > 0;
        if (!hasRequiredItems) {
            Utility.sendGameMessage("Must have feathers and barbarian rod", "AutoTickSkiller");
            return false;
        }
        return true;
    }

    private static boolean hasSmallFishingNet() {
        var hasRequiredItems = Inventory.getItemAmount(ItemID.SMALL_FISHING_NET) > 0;
        if (!hasRequiredItems) {
            Utility.sendGameMessage("Must have a small fishing net", "AutoTickSkiller");
            return false;
        }
        return true;
    }

    private static boolean hasFlyFishingRodAndFeathers() {
        var hasRequiredItems = Inventory.getItemAmount(ItemID.FEATHER) > 0 && Inventory.getItemAmount(ItemID.FLY_FISHING_ROD) > 0;
        if (!hasRequiredItems) {
            Utility.sendGameMessage("Must have feathers and fly fishing rod", "AutoTickSkiller");
            return false;
        }
        return true;
    }

    private static boolean hasFishingRodAndBaits() {
        var hasRequiredItems = Inventory.getItemAmount(ItemID.FISHING_BAIT) > 0 && Inventory.getItemAmount(ItemID.FISHING_ROD) > 0;
        if (!hasRequiredItems) {
            Utility.sendGameMessage("Must have fishing baits and fishing rod", "AutoTickSkiller");
            return false;
        }
        return true;
    }

    private static void useHarpoonSpecial() {
        if (Utility.getSpecialAttackEnergy() < 100 || Utility.isSpecialAttackEnabled()) return;
        if (Equipment.search().withName("Dragon harpoon").result().isEmpty())
            return;
        Utility.specialAttack();
        Utility.sleepGaussian(50, 100);
    }

    private static void useDragonAxeSpec() {
        if (Utility.getSpecialAttackEnergy() < 100 || Utility.isSpecialAttackEnabled()) return;
        if (Equipment.search().withName("Dragon axe").result().isEmpty() && Equipment.search().withName("Dragon felling axe").result().isEmpty())
            return;
        Utility.specialAttack();
        Utility.sleepGaussian(50, 100);
    }

    private static boolean handleFishing(String fishingAction, WorldPoint loc) {
        if (hasMaterialsTo3Tick()) return handle3TickFishing(fishingAction, loc);
        if (handle3TickItems()) return handle3TickFishing(fishingAction, loc);
        return handleRegularFishing(fishingAction, loc);
    }

    private static boolean handleRegularFishing(String fishingAction, WorldPoint loc) {
        if (Inventory.isFull()) handleFishDropping();
        if (!Utility.isIdle()) return false;
        var fishingPool = NPCs.search().withAction(fishingAction).withinDistanceToPoint(loc, 14).nearestToPlayerTrueDistance();
        if (fishingPool.isEmpty()) {
            return false;
        }
        var interactionTarget = Utility.getInteractionTarget();

        if (interactionTarget != null && interactionTarget.equals(fishingPool.get())) {
            return false;
        }
        useHarpoonSpecial();
        Interaction.clickNpc(fishingPool.get(), fishingAction);
        return Utility.sleepUntilCondition(() -> !Utility.isIdle(), 10000, 200);
    }

    private static boolean handle3TickFishing(String fishingAction, WorldPoint loc) {
        if (Inventory.isFull()) handleFishDropping();
        var fishingSpot = NPCs.search().withAction(fishingAction).withinDistanceToPoint(loc, 14).nearestToPlayerTrueDistance();
        if (fishingSpot.isEmpty()) {
            Utility.sleepGaussian(1200, 1800);
            return false;
        }

        WorldPoint fishingSpotLocation = fishingSpot.get().getWorldLocation();
        useHarpoonSpecial();
        Interaction.clickNpc(fishingSpot.get(), fishingAction);
        Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(fishingSpotLocation) <= 2, 15000, 40);
        var animationStarted = Utility.sleepUntilCondition(() -> Utility.getLocalAnimation() == 622 || Utility.getLocalAnimation() == 621 || Utility.getLocalAnimation() == 9349, 2400, 40);
        if (animationStarted) {
            var animatedTick = Utility.getTickCount();
            Utility.sleepUntilCondition(() -> Utility.getTickCount() >= animatedTick + 1, 2400, 50);
            Utility.sleepGaussian(140, 240);
            var guamLeaf = Inventory.search().withId(ItemID.GUAM_LEAF).onlyUnnoted().first();
            var tar = Inventory.search().withId(ItemID.SWAMP_TAR).onlyUnnoted().first();
            Interaction.useItemOnItem(tar.get(), guamLeaf.get());
            var fish = Inventory.search().nameContains("Raw", "Guam tar", "Leaping").first();
            if (fish.isPresent()) {
                Utility.sleepGaussian(100, 150);
                Interaction.clickWidget(fish.get(), "Drop");
                Utility.sleepGaussian(100, 150);
            } else {
                Utility.sleepGaussian(100, 250);
            }
        }
        Utility.sleepGaussian(100, 200);
        return true;
    }

    private static boolean hasAxe() {
        var requiredItems = Inventory.search().nameContains("axe").first().isPresent() || Equipment.search().nameContains("axe").first().isPresent();
        if (!requiredItems) {
            Utility.sendGameMessage("Must have axe in inventory or equipped", "AutoTickSkiller");
            return false;
        }
        return true;
    }

    private static boolean hasMaterialsTo3Tick() {
        return Inventory.getItemAmount(ItemID.GUAM_LEAF) == 1 && Inventory.getItemAmount(ItemID.SWAMP_TAR) >= 15 && Inventory.getItemAmount(ItemID.PESTLE_AND_MORTAR) > 0;
    }

    private static boolean handleChopping(String treeName, WorldPoint loc, int radius) {
        if (hasMaterialsTo3Tick()) return handle3tickChopping(treeName, loc, radius);
        if (handle3TickItems()) return handle3tickChopping(treeName, loc, radius);
        return handleRegularChopping(treeName, loc, radius);
    }

    private static void handleFishDropping() {
        var logs = Inventory.search().nameContains("raw", "leaping").result();
        for (var log : logs) {
            Interaction.clickWidget(log, "Drop");
            Utility.sleepGaussian(150, 250);
        }
    }

    private static void handleLogDropping() {
        var logs = Inventory.search().nameContains("log").result();
        for (var log : logs) {
            Interaction.clickWidget(log, "Drop");
            Utility.sleepGaussian(150, 250);
        }
    }

    private static boolean handle3TickItems() {
        if (Inventory.getItemAmount(ItemID.GUAM_LEAF) > 1 && Inventory.getItemAmount(ItemID.SWAMP_TAR) >= 15 && Inventory.getItemAmount(ItemID.PESTLE_AND_MORTAR) > 0) {
            var excessGuams = Inventory.search().withId(ItemID.GUAM_LEAF).result();
            for (int i = 0; i < excessGuams.size() - 1; i++) {
                if (excessGuams.get(i) != null) {
                    Interaction.clickWidget(excessGuams.get(i), "Drop");
                    Utility.sleepGaussian(150, 300);
                }
            }
            return Utility.sleepUntilCondition(() -> Inventory.getItemAmount(ItemID.GUAM_LEAF) == 1);
        }
        if (Inventory.getItemAmount(ItemID.GRIMY_GUAM_LEAF) > 0 && Inventory.getItemAmount(ItemID.SWAMP_TAR) >= 15 && Inventory.getItemAmount(ItemID.PESTLE_AND_MORTAR) > 0) {
            var dirtyGuam = Inventory.search().withId(ItemID.GRIMY_GUAM_LEAF).first();
            if (dirtyGuam.isEmpty()) {
                return false;
            }
            Interaction.clickWidget(dirtyGuam.get(), "Clean");
            return Utility.sleepUntilCondition(() -> Inventory.getItemAmount(ItemID.GUAM_LEAF) > 0);
        }
        return false;
    }

    private static boolean handleRegularChopping(String treeName, WorldPoint loc, int radius) {
        if (Inventory.isFull()) handleLogDropping();
        if (!Utility.isIdle()) return false;
        var tree = TileObjects.search().withName(treeName).withinDistanceToPoint(radius, loc).withAction("Chop down").nearestToPlayer();
        if (tree.isEmpty()) return false;
        useDragonAxeSpec();
        Interaction.clickTileObject(tree.get(), "Chop down");
        Utility.sleepGaussian(1200, 1800);
        if (Utility.sleepUntilCondition(() -> !Utility.isIdle(), 5000)) {
            Utility.sleepGaussian(500, 2000);
            return true;
        }
        return false;
    }

    private static boolean handle3tickChopping(String treeName, WorldPoint loc, int radius) {
        if (Inventory.isFull()) handleLogDropping();
        var tree = TileObjects.search().withName(treeName).withinDistanceToPoint(radius, loc).withAction("Chop down").nearestToPlayer();
        if (tree.isEmpty()) return false;
        var guamLeaf = Inventory.search().withId(ItemID.GUAM_LEAF).onlyUnnoted().first();
        var tar = Inventory.search().withId(ItemID.SWAMP_TAR).onlyUnnoted().first();
        if (guamLeaf.isEmpty() || tar.isEmpty()) return false;
        Interaction.useItemOnItem(tar.get(), guamLeaf.get());
        var combineStartedOnTick = Utility.getTickCount();
        Utility.sleepUntilCondition(() -> Utility.getTickCount() >= combineStartedOnTick + 1, 2400, 50);
        useDragonAxeSpec();
        Interaction.clickTileObject(tree.get(), "Chop down");
        var choppingStartedOnTick = Utility.getTickCount();
        Utility.sleepUntilCondition(() -> Utility.getTickCount() >= choppingStartedOnTick + 2, 2400, 50);
        var logs = Inventory.search().nameContains("Logs", "Guam tar").first();
        if (logs.isPresent()) Interaction.clickWidget(logs.get(), "Drop");
        return true;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
