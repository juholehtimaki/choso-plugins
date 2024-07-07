package com.theplug.AutoBankSkillerPlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Spells.Lunar;
import com.theplug.PaistiUtils.Collections.Pair;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.widgets.Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@DontObfuscate
@AllArgsConstructor
public enum BankSkillerMethod {
    HERB_CLEANING("Herb cleaning", BankSkillerMethod::hasGrimyHerbs, BankSkillerMethod::handleHerbCleaning, (config) -> false),
    GEM_CUTTING("Gem cutting", BankSkillerMethod::hasChiselAndGems, BankSkillerMethod::handleGemCutting, BankSkillerMethod::handleGemCuttingInventorySetup),
    POTION_MAKING("Potion making", BankSkillerMethod::hasMaterialsToMakeDesiredPotion, BankSkillerMethod::handlePotions, (config) -> false),
    LUNAR_SPELLS("Lunar spells", BankSkillerMethod::hasRunesAndItemsForLunarSpells, BankSkillerMethod::handleLunarSpells, (config) -> false),
    JUG_OF_WINES("Jug of wines", BankSkillerMethod::hasMaterialsToMakeJugOfWines, BankSkillerMethod::handleJugOfWinesMaking, (config) -> false),
    CRAFTING("Crafting", BankSkillerMethod::hasMaterialsToMakeDesiredCraftingProduct, BankSkillerMethod::handleCrafting, BankSkillerMethod::handleCraftingInventorySetup),
    FLETCHING("Fletching", BankSkillerMethod::hasMaterialsToFletch, BankSkillerMethod::handleFletching, BankSkillerMethod::handleFletchingInventorySetup),
    SPAM_COMBINE("Spam combine", BankSkillerMethod::hasItemsToSpamCombine, BankSkillerMethod::handleSpamCombine, (config) -> false),
    CUSTOM_ITEM_ON_ITEM("Custom item on item", BankSkillerMethod::hasItemsForCustomCombine, BankSkillerMethod::handleCustomItemOnItemCombine, (config) -> false);

    private final String name;
    private final Function<AutoBankSkillerPluginConfig, Boolean> _hasRequiredItems;
    private final Function<AutoBankSkillerPluginConfig, Boolean> _handleSkilling;
    private final Function<AutoBankSkillerPluginConfig, Boolean> _handleInventorySetup;

    public boolean hasRequiredItems(AutoBankSkillerPluginConfig config) {
        return _hasRequiredItems.apply(config);
    }

    public boolean handleSkilling(AutoBankSkillerPluginConfig config) {
        return _handleSkilling.apply(config);
    }

    public boolean handleInventorySetup(AutoBankSkillerPluginConfig config) {
        return _handleInventorySetup.apply(config);
    }

    private static List<String> getItemListFromString(String input) {
        return Arrays.asList(input.split("\n"));
    }

    private static boolean hasItemInBankOrInv(List<String> items) {
        var itemsInInventory = Inventory.search().result()
                .stream()
                .filter(item -> items.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());
        if (itemsInInventory.size() > 0) return true;
        if (!Bank.isBankUpdated()) return true;
        var itemsInBank = Bank.search().result()
                .stream()
                .filter(item -> items.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());
        if (itemsInBank.size() == 0) {
            return false;
        }
        return true;
    }

    private static boolean hasItemInBankOrInvWithCount(int itemId, int requiredAmount) {
        var itemsInInventory = Inventory.getItemAmount(itemId);
        if (itemsInInventory > requiredAmount) return true;
        if (!Bank.isBankUpdated()) return true;
        var itemsInBank = Bank.getQuantityInBank(itemId);
        if (itemsInBank < requiredAmount) {
            return false;
        }
        return true;
    }

    private static boolean hasItemInBankOrInvWithCount(String itemName, int requiredAmount) {
        var itemsInInventory = Inventory.getItemAmount(itemName);
        if (itemsInInventory >= requiredAmount) return true;
        if (!Bank.isBankUpdated()) return true;
        var itemsInBank = Bank.getQuantityInBank(itemName);
        if (itemsInBank < requiredAmount) {
            return false;
        }
        return true;
    }

    private static boolean handleCustomItemOnItemCombine(AutoBankSkillerPluginConfig config) {
        if (Utility.getIdleTicks() < 4) return false;

        boolean hasFirstItemInInventory = Inventory.getItemAmount(config.customFirstItem()) > 0;
        boolean hasSecondItemInInventory = Inventory.getItemAmount(config.customSecondItemCount()) > 0;


        if (!hasFirstItemInInventory || !hasSecondItemInInventory) {
            List<Pair<String, Integer>> itemsToWithdraw = new ArrayList<>();
            List<String> itemsToNotBank = config.customDontDepositFirstItem() ? List.of(config.customFirstItem()) : new ArrayList<>();
            if (hasFirstItemInInventory && config.customDontDepositFirstItem()) {
                itemsToWithdraw.add(new Pair<>(config.customSecondItem(), config.customSecondItemCount()));
            } else {
                itemsToWithdraw.add(new Pair<>(config.customFirstItem(), config.customFirstItemCount()));
                itemsToWithdraw.add(new Pair<>(config.customSecondItem(), config.customSecondItemCount()));
            }

            if (!handleBankingWithStringPairs(itemsToWithdraw, itemsToNotBank)) {
                return false;
            }
        }

        var firstItemInInventory = Inventory.search().withName(config.customFirstItem()).first();
        var secondItemInInventory = Inventory.search().withName(config.customSecondItem()).first();

        if (firstItemInInventory.isEmpty() || secondItemInInventory.isEmpty()) {
            return false;
        }

        Interaction.useItemOnItem(firstItemInInventory.get(), secondItemInInventory.get());
        handleMakeInterface(null);

        return Utility.sleepUntilCondition(() -> !Utility.isIdle());
    }

    private static boolean hasItemsForCustomCombine(AutoBankSkillerPluginConfig config) {
        var hasFirstItem = hasItemInBankOrInvWithCount(config.customFirstItem(), config.customFirstItemCount());
        if (!hasFirstItem) {
            Utility.sendGameMessage("Out of " + config.customFirstItem(), "AutoBankSkiller");
            return false;
        }
        var hasSecondItem = hasItemInBankOrInvWithCount(config.customSecondItem(), config.customSecondItemCount());
        if (!hasSecondItem) {
            Utility.sendGameMessage("Out of " + config.customSecondItem(), "AutoBankSkiller");
            return false;
        }
        return true;
    }

    private static boolean hasItemsToSpamCombine(AutoBankSkillerPluginConfig config) {
        var firstItem = Inventory.search().withName(config.spamCombineFirstItem()).first();
        if (firstItem.isEmpty()) {
            Utility.sendGameMessage("Out of " + config.spamCombineFirstItem(), "AutoBankSkiller");
            return false;
        }
        var secondItem = Inventory.search().withName(config.spamCombineSecondItem()).first();
        if (secondItem.isEmpty()) {
            Utility.sendGameMessage("Out of " + config.spamCombineSecondItem(), "AutoBankSkiller");
            return false;
        }
        return true;
    }

    private static boolean handleSpamCombine(AutoBankSkillerPluginConfig config) {
        if (config.waitForAnimation() && Utility.getIdleTicks() < 3) return false;
        var firstItem = Inventory.search().withName(config.spamCombineFirstItem()).first();
        var secondItem = Inventory.search().withName(config.spamCombineSecondItem()).first();
        if (firstItem.isEmpty() || secondItem.isEmpty()) {
            return false;
        }
        Interaction.useItemOnItem(firstItem.get(), secondItem.get());
        if (config.waitForAnimation()) {
            handleMakeInterface(null);
        } else {
            Utility.sleepGaussian(config.spamCombineSleepMin(), config.spamCombineSleepMax());
        }
        return true;
    }

    private static boolean handleFletching(AutoBankSkillerPluginConfig config) {
        if (config.desiredFletching().isRequiresKnife()) {
            return handleGenericCombineFromMaterialsWithMatcher(ItemID.KNIFE, List.of(ItemID.KNIFE), config.desiredFletching().getRequiredMaterials(), config.desiredFletching().getMatcher(), false, false);
        }
        return handleGenericCombineFromMaterials(config.desiredFletching().getRequiredMaterials(), false);
    }

    private static boolean handleGenericCombineFromMaterialsWithMatcher(int itemToUse, List<Integer> itemsToNotBank, List<Pair<Integer, Integer>> materials, String matcher, boolean secondaryMaterialStackable, boolean threeTickDelay) {
        if (Utility.getIdleTicks() < (threeTickDelay ? 3 : 2)) return false;

        boolean hasMaterialsInInventory = materials.stream()
                .allMatch(material -> Inventory.getItemAmount(material.getLeft()) >= material.getRight());

        boolean hasItemToUseInInv = Inventory.getItemAmount(itemToUse) > 0;

        if (!hasMaterialsInInventory || !hasItemToUseInInv) {
            List<Pair<Integer, Integer>> itemsToWithdraw = new ArrayList<>();
            if (!hasItemToUseInInv) {
                itemsToWithdraw.add(new Pair<>(itemToUse, 1));
            }
            var countToWithdraw = secondaryMaterialStackable ? 28 - itemsToNotBank.size() : (28 - itemsToNotBank.size()) / materials.size();
            if (secondaryMaterialStackable) {
                itemsToWithdraw.add(new Pair<>(materials.get(0).getLeft(), 28 - itemsToNotBank.size()));
                itemsToWithdraw.add(new Pair<>(materials.get(1).getLeft(), Bank.getQuantityInBank(materials.get(1).getLeft())));
            } else {
                for (var material : materials) {
                    itemsToWithdraw.add(new Pair<>(material.getLeft(), countToWithdraw));
                }
            }

            if (!handleBankingWithPairs(itemsToWithdraw, itemsToNotBank)) {
                return false;
            }
        }

        for (var material : materials) {
            if (Inventory.getItemAmount(material.getLeft()) < material.getRight()) {
                return false;
            }
        }

        if (Inventory.getItemAmount(itemToUse) < 1) {
            return false;
        }

        var firstMaterialInInventory = Inventory.search().withId(itemToUse).first();
        var secondaryMaterialInInventory = Inventory.search().withId(materials.get(0).getLeft()).first();

        if (firstMaterialInInventory.isEmpty() || secondaryMaterialInInventory.isEmpty()) {
            return false;
        }

        Interaction.useItemOnItem(firstMaterialInInventory.get(), secondaryMaterialInInventory.get());
        handleMakeInterface(matcher);
        return Utility.sleepUntilCondition(() -> !Utility.isIdle());
    }

    private static boolean hasMaterialsToFletch(AutoBankSkillerPluginConfig config) {
        if (config.desiredFletching().isRequiresKnife()) {
            var hasKnife = hasItemInBankOrInv(List.of("Knife"));
            if (!hasKnife) {
                Utility.sendGameMessage("No knife found", "AutoBankSkiller");
                return false;
            }
        }
        for (var material : config.desiredFletching().getRequiredMaterials()) {
            var hasMaterials = hasItemInBankOrInvWithCount(material.getLeft(), material.getRight());
            if (!hasMaterials) {
                Utility.sendGameMessage("Not enough materials for " + config.desiredFletching().name(), "AutoBankSkiller");
                return false;
            }
        }
        return true;
    }

    private static boolean hasMaterialsToMakeJugOfWines(AutoBankSkillerPluginConfig config) {
        var hasJugOfWaters = hasItemInBankOrInvWithCount(ItemID.JUG_OF_WATER, 1);
        var hasGrapes = hasItemInBankOrInvWithCount(ItemID.GRAPES, 1);
        var hasJugOfWatersAndGrapes = hasJugOfWaters && hasGrapes;
        if (!hasJugOfWatersAndGrapes) {
            Utility.sendGameMessage("Out of grapes or jug of waters", "AutoBankSkiller");
            return false;
        }
        return true;
    }

    private static boolean handleJugOfWinesMaking(AutoBankSkillerPluginConfig config) {
        return handleGenericCombineFromMaterials(List.of(new Pair<>(ItemID.JUG_OF_WATER, 1), new Pair<>(ItemID.GRAPES, 1)), false);
    }

    private static boolean handleCrafting(AutoBankSkillerPluginConfig config) {
        if (config.desiredCrafting().isRequiresNeedleAndThread()) {
            return handleGenericCombineFromMaterialsWithMatcher(ItemID.NEEDLE, List.of(ItemID.NEEDLE, ItemID.THREAD), config.desiredCrafting().getRequiredMaterials(), config.desiredCrafting().getMatcher(), true, true);
        }
        return handleGenericCombineFromMaterials(config.desiredCrafting().getRequiredMaterials(), false);
    }

    private static boolean handlePotions(AutoBankSkillerPluginConfig config) {
        return handleGenericCombineFromMaterials(config.desiredPotion().getRequiredMaterials(), config.desiredPotion().isOneTickable());
    }

    private static boolean hasMaterialsToMakeDesiredCraftingProduct(AutoBankSkillerPluginConfig config) {
        if (config.desiredCrafting().isRequiresNeedleAndThread()) {
            var hasNeedle = hasItemInBankOrInv(List.of("Needle"));
            if (!hasNeedle) {
                Utility.sendGameMessage("No needle found", "AutoBankSkiller");
                return false;
            }
        }
        for (var material : config.desiredCrafting().getRequiredMaterials()) {
            var hasMaterial = hasItemInBankOrInvWithCount(material.getLeft(), material.getRight());
            if (!hasMaterial) {
                Utility.sendGameMessage("Not enough materials for " + config.desiredCrafting().name(), "AutoBankSkiller");
                return false;
            }
        }
        return true;
    }

    private static boolean hasMaterialsToMakeDesiredPotion(AutoBankSkillerPluginConfig config) {
        for (var material : config.desiredPotion().getRequiredMaterials()) {
            var hasMaterial = hasItemInBankOrInvWithCount(material.getLeft(), material.getRight());
            if (!hasMaterial) {
                Utility.sendGameMessage("Not enough materials for " + config.desiredPotion().name(), "AutoBankSkiller");
                return false;
            }
        }
        return true;
    }

    private static boolean hasRunesAndItemsForLunarSpells(AutoBankSkillerPluginConfig config) {
        if (!config.desiredLunarSpell().getSpell().canCast()) {
            Utility.sendGameMessage("Not sufficient runes in inventory to cast:" + config.desiredLunarSpell().getSpell().name(), "AutoBankSkiller");
            return false;
        }
        for (var material : config.desiredLunarSpell().getRequiredMaterials()) {
            var hasMaterial = hasItemInBankOrInvWithCount(material.getLeft(), material.getRight());
            if (!hasMaterial) {
                Utility.sendGameMessage("Not enough materials for " + config.desiredLunarSpell().name(), "AutoBankSkiller");
                return false;
            }
        }
        return true;
    }

    private static boolean handleLunarSpells(AutoBankSkillerPluginConfig config) {
        BankSkillerLunarSpell bankSkillerLunarSpell = config.desiredLunarSpell();
        if (bankSkillerLunarSpell.getSpell() == Lunar.PLANK_MAKE && Utility.getIdleTicks() < 2) return false;
        List<Pair<Integer, Integer>> materials = bankSkillerLunarSpell.getRequiredMaterials();

        boolean hasMaterialsInInventory = materials.stream()
                .allMatch(material -> Inventory.getItemAmount(material.getLeft()) >= material.getRight());

        if (!hasMaterialsInInventory) {
            List<Pair<Integer, Integer>> itemsToWithdraw = new ArrayList<>();
            List<Integer> itemsToNotDeposit = Inventory.search().nameContains("rune")
                    .result()
                    .stream()
                    .map(Widget::getItemId)
                    .collect(Collectors.toList());

            if (config.desiredLunarSpell().getSpell() == Lunar.PLANK_MAKE) {
                itemsToNotDeposit.add(ItemID.COINS_995);
                if (Bank.getQuantityInBank(ItemID.COINS_995) > 0) {
                    itemsToWithdraw.add(new Pair<>(ItemID.COINS_995, Bank.getQuantityInBank(ItemID.COINS_995)));
                }
                itemsToWithdraw.add(new Pair<>(materials.get(0).getLeft(), 25));
            } else if (config.desiredLunarSpell().getSpell() == Lunar.HUMIDIFY) {
                itemsToWithdraw.add(new Pair<>(materials.get(0).getLeft(), 26));
            } else if (config.desiredLunarSpell().getSpell() == Lunar.TAN_LEATHER || config.desiredLunarSpell().getSpell() == Lunar.SPIN_FLAX) {
                itemsToWithdraw.add(new Pair<>(materials.get(0).getLeft(), 25));
            } else {
                for (var material : materials) {
                    itemsToWithdraw.add(new Pair<>(material.getLeft(), 13));
                }
            }
            if (!handleBankingWithPairs(itemsToWithdraw, itemsToNotDeposit)) {
                return false;
            }
        }

        for (var material : materials) {
            if (Inventory.getItemAmount(material.getLeft()) < material.getRight()) {
                return false;
            }
        }

        var firstMaterialInInventory = Inventory.search().withId(materials.get(0).getLeft()).first();
        if (firstMaterialInInventory.isEmpty() || !config.desiredLunarSpell().getSpell().canCast()) {
            return false;
        }

        if (config.desiredLunarSpell().getSpell() == Lunar.PLANK_MAKE) {
            Interaction.useSpellOnItem(config.desiredLunarSpell().getSpell(), firstMaterialInInventory.get());
            return Utility.sleepUntilCondition(() -> !Utility.isIdle(), 1800, 200);
        } else {
            config.desiredLunarSpell().getSpell().cast();
        }
        var tickCount = Utility.getTickCount();
        return Utility.sleepUntilCondition(() -> Utility.getTickCount() >= tickCount + 4);
    }

    private static boolean handleGenericCombineFromMaterials(List<Pair<Integer, Integer>> materials, boolean secondaryMaterialStackable) {
        if (Utility.getIdleTicks() < 2) return false;

        boolean hasMaterialsInInventory = materials.stream()
                .allMatch(material -> Inventory.getItemAmount(material.getLeft()) >= material.getRight());

        if (!hasMaterialsInInventory) {
            List<Integer> itemsToNotBank = secondaryMaterialStackable ? List.of(materials.get(1).getLeft()) : List.of();
            List<Pair<Integer, Integer>> itemsToWithdraw = new ArrayList<>();

            if (secondaryMaterialStackable) {
                itemsToWithdraw.add(new Pair<>(materials.get(0).getLeft(), 27));
                itemsToWithdraw.add(new Pair<>(materials.get(1).getLeft(), Bank.getQuantityInBank(materials.get(1).getLeft())));
            } else {
                var countToWithdraw = 28 / materials.size();
                for (var material : materials) {
                    itemsToWithdraw.add(new Pair<>(material.getLeft(), countToWithdraw));
                }
            }

            if (!handleBankingWithPairs(itemsToWithdraw, itemsToNotBank)) {
                return false;
            }
        }

        for (var material : materials) {
            if (Inventory.getItemAmount(material.getLeft()) < material.getRight()) {
                return false;
            }
        }

        var firstMaterialInInventory = Inventory.search().withId(materials.get(0).getLeft()).first();
        var secondMaterialInInventory = Inventory.search().withId(materials.get(1).getLeft()).first();

        if (firstMaterialInInventory.isEmpty() || secondMaterialInInventory.isEmpty()) {
            return false;
        }

        Interaction.useItemOnItem(firstMaterialInInventory.get(), secondMaterialInInventory.get());
        handleMakeInterface(null);
        return Utility.sleepUntilCondition(() -> !Utility.isIdle());
    }

    private static boolean handleGemCuttingInventorySetup(AutoBankSkillerPluginConfig config) {
        var chisel = Inventory.search().withName("Chisel").first();
        if (chisel.isPresent()) return false;
        if (!handleBankingWithPairs(List.of(new Pair<>(ItemID.CHISEL, 1)), List.of())) {
            return false;
        }
        return true;
    }

    private static boolean handleFletchingInventorySetup(AutoBankSkillerPluginConfig config) {
        if (!config.desiredFletching().isRequiresKnife()) return false;
        var knife = Inventory.search().withName("Knife").first();
        if (knife.isPresent()) return false;
        if (!handleBankingWithPairs(List.of(new Pair<>(ItemID.KNIFE, 1)), List.of())) {
            return false;
        }
        return true;
    }

    private static boolean handleCraftingInventorySetup(AutoBankSkillerPluginConfig config) {
        if (!config.desiredCrafting().isRequiresNeedleAndThread()) return false;
        var needle = Inventory.search().withName("Needle").first();
        if (needle.isPresent()) return false;
        if (!handleBankingWithPairs(List.of(new Pair<>(ItemID.NEEDLE, 1)), List.of())) {
            return false;
        }
        return true;
    }

    private static boolean hasChiselAndGems(AutoBankSkillerPluginConfig config) {
        var hasChisel = hasItemInBankOrInv(List.of("Chisel"));
        if (!hasChisel) {
            Utility.sendGameMessage("No chisel found", "AutoBankSkiller");
            return false;
        }
        var gemsToCut = getItemListFromString(config.uncutGems());
        var hasGems = hasItemInBankOrInv(gemsToCut);
        if (!hasGems) {
            Utility.sendGameMessage("Out of uncut gems", "AutoBankSkiller");
        }
        return hasGems;
    }

    private static boolean hasGrimyHerbs(AutoBankSkillerPluginConfig config) {
        var herbsToClean = getItemListFromString(config.grimyHerbs());
        var grimyHerbsInInventory = Inventory.search().nameContains("grimy").result()
                .stream()
                .filter(item -> herbsToClean.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());
        if (grimyHerbsInInventory.size() > 0) return true;
        if (!Bank.isBankUpdated()) return true;
        var grimyHerbsInBank = Bank.search().nameContains("grimy").result()
                .stream()
                .filter(item -> herbsToClean.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());
        if (grimyHerbsInBank.size() == 0) {
            Utility.sendGameMessage("Out of herbs to clean", "AutoBankSkiller");
            return false;
        }
        return true;
    }

    private static boolean handleBanking(List<String> itemsToWithdraw, List<String> itemsToNotDeposit) {
        if (!Bank.isOpen()) {
            Bank.openBank();
            Utility.sleepUntilCondition(Bank::isOpen);
        }

        if (itemsToNotDeposit.size() == 0) {
            Bank.depositInventory();
        } else {
            Bank.depositInventoryExceptWithNames(itemsToNotDeposit);
        }

        Utility.sleepUntilCondition(() -> Utility.sleepUntilCondition(() -> Inventory.getEmptySlots() > 0, 1000, 200));


        var itemsInBank = Bank.search().result()
                .stream()
                .filter(item -> itemsToWithdraw.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());
        if (itemsInBank.size() == 0) {
            return false;
        }
        var itemQuantity = Bank.getQuantityInBank(itemsInBank.get(0).getItemId());
        Bank.withdraw(itemsInBank.get(0).getItemId(), Math.min(itemQuantity, Inventory.getEmptySlots()));
        if (Utility.sleepUntilCondition(() -> BankInventory.search().result().stream().anyMatch(item -> itemsToWithdraw.stream().anyMatch(name -> item.getName().contains(name))))) {
            return true;
        }
        return false;
    }

    private static boolean handleBankingWithPairs(List<Pair<Integer, Integer>> items, List<Integer> itemsToNotDeposit) {
        if (!Bank.isOpen()) {
            Bank.openBank();
            Utility.sleepUntilCondition(Bank::isOpen);
        }

        if (itemsToNotDeposit.size() == 0) {
            Bank.depositInventory();
        } else {
            Bank.depositInventoryExceptWithIds(itemsToNotDeposit);
        }

        Utility.sleepUntilCondition(() -> Utility.sleepUntilCondition(() -> Inventory.getEmptySlots() > 0, 1000, 200));

        for (var item : items) {
            var itemInBank = Bank.search().withId(item.getLeft()).first();
            if (itemInBank.isEmpty()) {
                return false;
            }
            Bank.withdraw(itemInBank.get().getItemId(), Math.min(item.getRight(), Bank.getQuantityInBank(item.getLeft())));
            if (!Utility.sleepUntilCondition(() -> BankInventory.search().withId(item.getLeft()).first().isPresent())) {
                return false;
            }
        }

        return true;
    }

    private static boolean handleBankingWithStringPairs(List<Pair<String, Integer>> items, List<String> itemsToNotDeposit) {
        if (!Bank.isOpen()) {
            Bank.openBank();
            Utility.sleepUntilCondition(Bank::isOpen);
        }

        if (itemsToNotDeposit.size() == 0) {
            Bank.depositInventory();
        } else {
            Bank.depositInventoryExceptWithNames(itemsToNotDeposit);
        }

        Utility.sleepUntilCondition(() -> Utility.sleepUntilCondition(() -> Inventory.getEmptySlots() > 0, 1000, 200));

        for (var item : items) {
            var itemInBank = Bank.search().withName(item.getLeft()).first();
            if (itemInBank.isEmpty()) {
                return false;
            }
            Bank.withdraw(itemInBank.get().getItemId(), Math.min(item.getRight(), Bank.getQuantityInBank(item.getLeft())));
            if (!Utility.sleepUntilCondition(() -> BankInventory.search().withName(item.getLeft()).first().isPresent())) {
                return false;
            }
        }

        return true;
    }

    private static boolean handleHerbCleaning(AutoBankSkillerPluginConfig config) {
        var herbsToClean = getItemListFromString(config.grimyHerbs());
        var herbsToCleanInInventory = Inventory.search().nameContains("grimy").result()
                .stream()
                .filter(item -> herbsToClean.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());
        if (herbsToCleanInInventory.isEmpty()) {
            if (!handleBanking(herbsToClean, List.of())) {
                return false;
            }
        }
        var grimyHerbs = Inventory.search().nameContains("grimy").result()
                .stream()
                .filter(item -> herbsToClean.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());

        if (grimyHerbs.size() == 0) return false;

        for (var grimyHerb : grimyHerbs) {
            Interaction.clickWidget(grimyHerb, "Clean");
            Utility.sleepGaussian(config.cleanHerbSleepMin(), config.cleanHerbSleepMax());
        }
        return true;
    }

    private static void handleMakeInterface(String matcher) {
        Utility.sleepUntilCondition(MakeInterface::isMakeInterfaceOpen, 1800, 200);

        if (MakeInterface.isMakeInterfaceOpen()) {
            if (matcher != null) {
                MakeInterface.selectOptionWildcard(matcher);
            } else {
                Keyboard.pressSpacebar();
            }
        }
        Utility.sleepUntilCondition(() -> !MakeInterface.isMakeInterfaceOpen(), 1800, 600);
    }

    private static boolean handleGemCutting(AutoBankSkillerPluginConfig config) {
        if (Utility.getIdleTicks() < 2) return false;
        var gemsToCut = getItemListFromString(config.uncutGems());
        var chisel = Inventory.search().nameContains("Chisel").result().stream().findFirst();
        var gemsToCutInInventory = Inventory.search().result()
                .stream()
                .filter(item -> gemsToCut.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());
        if (gemsToCutInInventory.isEmpty() || chisel.isEmpty()) {
            if (!handleBanking(gemsToCut, List.of("Chisel"))) {
                return false;
            }
        }
        var uncutGems = Inventory.search().result()
                .stream()
                .filter(item -> gemsToCut.stream().anyMatch(name -> item.getName().contains(name))).collect(Collectors.toList());

        var chiselInInventory = Inventory.search().nameContains("Chisel").result().stream().findFirst();

        if (uncutGems.size() == 0 || chiselInInventory.isEmpty()) return false;

        Interaction.useItemOnItem(chiselInInventory.get(), uncutGems.get(0));
        handleMakeInterface(null);
        return Utility.sleepUntilCondition(() -> !Utility.isIdle());
    }

    @Override
    public String toString() {
        return name;
    }
}
