package com.theplug.AutoBlastFurnacePlugin;

import com.theplug.AccountBuilderPlugin.TaskSystem.PTaskResult;
import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.API.Loadouts.LoadoutInstructions.EquipmentInstruction;
import com.theplug.PaistiUtils.API.Loadouts.LoadoutInstructions.InstructionResult;
import com.theplug.PaistiUtils.API.Loadouts.LoadoutInstructions.InventoryInstruction;
import lombok.AllArgsConstructor;
import net.runelite.api.ItemID;
import net.runelite.api.widgets.Widget;

import java.util.Optional;
import java.util.function.BooleanSupplier;

@DontObfuscate
@AllArgsConstructor
public enum BlastFurnaceMethod {
    BRONZE_BARS("Bronze bars", 1, BlastFurnaceMethod::handleBronzeWithdraw, BlastFurnaceMethod::enoughTinAndCopperOreToContinue, BlastFurnaceMethod::handleBasicLoadout),
    IRON_BARS("Iron bars", 15, BlastFurnaceMethod::handleIronWithdraw, BlastFurnaceMethod::enoughIronToContinue, BlastFurnaceMethod::handleBasicLoadout),
    SILVER_BARS("Silver bars", 20, BlastFurnaceMethod::handleSilverWithdraw, BlastFurnaceMethod::enoughSilverOresToContinue, BlastFurnaceMethod::handleBasicLoadout),
    STEEL_BARS("Steel bars", 30, BlastFurnaceMethod::handleSteelWithdraw, BlastFurnaceMethod::enoughIronAndCoalOresToContinue, BlastFurnaceMethod::handleCoalBagLoadout),
    GOLD_BARS("1T Gold bars", 40, BlastFurnaceMethod::handleGoldWithdraw, BlastFurnaceMethod::enoughGoldOresToContinue, BlastFurnaceMethod::handleGoldLoadout),
    MITHRIL_BARS("Mithril bars", 50, BlastFurnaceMethod::handleMithrilWithdraw, BlastFurnaceMethod::enoughMithrilAndCoalToContinue, BlastFurnaceMethod::handleCoalBagLoadout),
    ADAMANTITE_BARS("Adamantite bars", 70, BlastFurnaceMethod::handleAdamantiteWithdraw, BlastFurnaceMethod::enoughAdamantiteAndCoalToContinue, BlastFurnaceMethod::handleCoalBagLoadout),
    RUNITE_BARS("Runite bars", 85, BlastFurnaceMethod::handleRuniteWithdraw, BlastFurnaceMethod::enoughRuniteAndCoalToContinue, BlastFurnaceMethod::handleCoalBagLoadout);

    private final String name;
    private final int levelRequired;
    private final BooleanSupplier _handleWithdraw;
    private final BooleanSupplier _enoughMaterials;
    private final BooleanSupplier _handleLoadout;

    public boolean handleWithdraw() {
        return _handleWithdraw.getAsBoolean();
    }

    public boolean enoughMaterialsToContinue() {
        return _enoughMaterials.getAsBoolean();
    }

    public boolean handleLoadout() {
        return _handleLoadout.getAsBoolean();
    }

    private static boolean handleGoldWithdraw() {
        Bank.withdraw(ItemID.GOLD_ORE, 27);
        return BlastFurnaceContents.areBarsAvailable();
    }

    private static boolean handleSilverWithdraw() {
        Bank.withdraw(ItemID.SILVER_ORE, 28);
        return true;
    }

    private static boolean handleIronWithdraw() {
        Bank.withdraw(ItemID.IRON_ORE, 28);
        return true;
    }

    private static boolean hasCoalBag() {
        return Inventory.search().withName("Open coal bag").first().isPresent();
    }

    private static Optional<Widget> getCoalBag() {
        return BankInventory.search().withName("Open coal bag").first();
    }

    private static boolean handleSteelWithdraw() {
        var coalCount = BlastFurnaceContents.COAL.getQuantity();
        var withdrawQuantity = hasCoalBag() ? 27 : 28;
        if (coalCount < 27) {
            Bank.withdraw(ItemID.COAL, withdrawQuantity);
            return false;
        } else {
            if (hasCoalBag()) {
                var bag = getCoalBag();
                if (bag.isEmpty()) {
                    throw new RuntimeException("Failed to get coal bag");
                }
                Interaction.clickWidget(bag.get(), "Fill");
                Utility.sleepGaussian(100, 200);
            }
            Bank.withdraw(ItemID.IRON_ORE, withdrawQuantity);
            return true;
        }
    }

    private static boolean handleBronzeWithdraw() {
        Bank.withdraw(ItemID.COPPER_ORE, 14);
        Utility.sleepGaussian(100, 200);
        Bank.withdraw(ItemID.TIN_ORE, 14);
        return BlastFurnaceContents.areBarsAvailable();
    }

    private static boolean handleMithrilWithdraw() {
        var coalCount = BlastFurnaceContents.COAL.getQuantity();
        var withdrawQuantity = hasCoalBag() ? 27 : 28;
        if (coalCount < 54) {
            Bank.withdraw(ItemID.COAL, withdrawQuantity);
            if (hasCoalBag()) {
                var bag = getCoalBag();
                if (bag.isEmpty()) {
                    throw new RuntimeException("Failed to get coal bag");
                }
                Interaction.clickWidget(bag.get(), "Fill");
                Utility.sleepGaussian(100, 200);
            }
            return false;
        } else {
            if (hasCoalBag()) {
                var bag = getCoalBag();
                if (bag.isEmpty()) {
                    throw new RuntimeException("Failed to get coal bag");
                }
                Interaction.clickWidget(bag.get(), "Fill");
                Utility.sleepGaussian(100, 200);
            }
            Bank.withdraw(ItemID.MITHRIL_ORE, withdrawQuantity);
            return true;
        }
    }

    private static boolean handleAdamantiteWithdraw() {
        var coalCount = BlastFurnaceContents.COAL.getQuantity();
        var withdrawQuantity = hasCoalBag() ? 27 : 28;
        if (coalCount < 54) {
            Bank.withdraw(ItemID.COAL, withdrawQuantity);
            if (hasCoalBag()) {
                var bag = getCoalBag();
                if (bag.isEmpty()) {
                    throw new RuntimeException("Failed to get coal bag");
                }
                Interaction.clickWidget(bag.get(), "Fill");
                Utility.sleepGaussian(100, 200);
            }
            return false;
        } else {
            if (hasCoalBag()) {
                var bag = getCoalBag();
                if (bag.isEmpty()) {
                    throw new RuntimeException("Failed to get coal bag");
                }
                Interaction.clickWidget(bag.get(), "Fill");
                Utility.sleepGaussian(100, 200);
            }
            Bank.withdraw(ItemID.ADAMANTITE_ORE, withdrawQuantity);
            return true;
        }
    }

    private static boolean handleRuniteWithdraw() {
        var coalCount = BlastFurnaceContents.COAL.getQuantity();
        var withdrawQuantity = hasCoalBag() ? 27 : 28;
        if (coalCount < 108) {
            Bank.withdraw(ItemID.COAL, withdrawQuantity);
            if (hasCoalBag()) {
                var bag = getCoalBag();
                if (bag.isEmpty()) {
                    throw new RuntimeException("Failed to get coal bag");
                }
                Interaction.clickWidget(bag.get(), "Fill");
                Utility.sleepGaussian(100, 200);
            }
            return false;
        } else {
            if (hasCoalBag()) {
                var bag = getCoalBag();
                if (bag.isEmpty()) {
                    throw new RuntimeException("Failed to get coal bag");
                }
                Interaction.clickWidget(bag.get(), "Fill");
                Utility.sleepGaussian(100, 200);
            }
            Bank.withdraw(ItemID.RUNITE_ORE, withdrawQuantity);
            return true;
        }
    }

    private static boolean enoughMithrilAndCoalToContinue() {
        return Bank.containsQuantity(ItemID.MITHRIL_ORE, 60) && Bank.containsQuantity(ItemID.COAL, 60);
    }

    private static boolean enoughAdamantiteAndCoalToContinue() {
        return Bank.containsQuantity(ItemID.ADAMANTITE_ORE, 60) && Bank.containsQuantity(ItemID.COAL, 60);
    }

    private static boolean enoughRuniteAndCoalToContinue() {
        return Bank.containsQuantity(ItemID.RUNITE_ORE, 60) && Bank.containsQuantity(ItemID.COAL, 60);
    }


    private static boolean enoughGoldOresToContinue() {
        return Bank.containsQuantity(ItemID.GOLD_ORE, 27);
    }

    private static boolean enoughSilverOresToContinue() {
        return Bank.containsQuantity(ItemID.SILVER_ORE, 28);
    }


    private static boolean enoughIronAndCoalOresToContinue() {
        return Bank.containsQuantity(ItemID.IRON_ORE, 60) && Bank.containsQuantity(ItemID.COAL, 60);
    }

    private static boolean enoughTinAndCopperOreToContinue() {
        return Bank.containsQuantity(ItemID.COPPER_ORE, 14) && Bank.containsQuantity(ItemID.TIN_ORE, 14);
    }

    private static boolean enoughIronToContinue() {
        return Bank.containsQuantity(ItemID.IRON_ORE, 28);
    }

    public static InventoryLoadout.InventoryLoadoutSetup getBlastFurnaceBaseGearSetup() {
        InventoryLoadout.InventoryLoadoutSetup setup = new InventoryLoadout.InventoryLoadoutSetup();
        setup.getInstructions().add(new EquipmentInstruction(1, true, ItemID.GRACEFUL_BOOTS));
        setup.getInstructions().add(new EquipmentInstruction(1, true, ItemID.GRACEFUL_CAPE));
        setup.getInstructions().add(new EquipmentInstruction(1, true, ItemID.GRACEFUL_HOOD));
        setup.getInstructions().add(new EquipmentInstruction(1, true, ItemID.GRACEFUL_LEGS));
        setup.getInstructions().add(new EquipmentInstruction(1, true, ItemID.GRACEFUL_TOP));
        setup.getInstructions().add(new EquipmentInstruction(1, true, ItemID.RING_OF_ENDURANCE));
        return setup;
    }

    private static boolean hasIceGloves() {
        return Inventory.search().withId(ItemID.ICE_GLOVES).first().isPresent() || Equipment.search().withId(ItemID.ICE_GLOVES).first().isPresent();
    }

    private static boolean hasGoldsmithGauntlets() {
        return Inventory.search().withId(ItemID.GOLDSMITH_GAUNTLETS).first().isPresent() || Equipment.search().withId(ItemID.GOLDSMITH_GAUNTLETS).first().isPresent();
    }

    private static boolean handleGoldLoadout() {
        var setup = getBlastFurnaceBaseGearSetup();
        if (!hasIceGloves() || !hasGoldsmithGauntlets()) {
            setup.getInstructions().add(new EquipmentInstruction(1, false, ItemID.ICE_GLOVES));
            setup.getInstructions().add(new InventoryInstruction(1, true, ItemID.GOLDSMITH_GAUNTLETS));
        }
        if (!Inventory.search().withId(ItemID.COAL_BAG, ItemID.COAL_BAG_12019, ItemID.COAL_BAG_25627, ItemID.OPEN_COAL_BAG).empty()) {
            setup.depositExcessInventoryItems();
        }
        if (setup.isSatisfied()) {
            return false;
        }

        return setup.handleWithdraw();
    }

    private static boolean handleCoalBagLoadout() {
        var setup = getBlastFurnaceBaseGearSetup();
        setup.getInstructions().add(new EquipmentInstruction(1, false, ItemID.ICE_GLOVES));
        setup.getInstructions().add(new InventoryInstruction(1, true, ItemID.COAL_BAG, ItemID.COAL_BAG_12019, ItemID.COAL_BAG_25627, ItemID.OPEN_COAL_BAG));
        if (!Inventory.search().withId(ItemID.GOLDSMITH_GAUNTLETS).empty()) {
            setup.depositExcessInventoryItems();
        }
        if (setup.isSatisfied()) {
            return false;
        }
        return setup.handleWithdraw();
    }

    private static boolean handleBasicLoadout() {
        var setup = getBlastFurnaceBaseGearSetup();
        setup.getInstructions().add(new EquipmentInstruction(1, false, ItemID.ICE_GLOVES));
        if (!Inventory.search().withId(ItemID.COAL_BAG, ItemID.COAL_BAG_12019, ItemID.COAL_BAG_25627, ItemID.OPEN_COAL_BAG, ItemID.GOLDSMITH_GAUNTLETS).empty()) {
            setup.depositExcessInventoryItems();
        }
        if (setup.isSatisfied()) {
            return false;
        }
        return setup.handleWithdraw();
    }

    @Override
    public String toString() {
        return this.name;
    }
}
