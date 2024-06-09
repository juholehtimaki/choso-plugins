package com.theplug.AutoItemCombinerPlugin;

import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.SES.PluginId;
import com.theplug.SES.SessionGuard;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(name = "PAutoItemCombiner", description = "Combines specified items while bankstanding", enabledByDefault = false, tags = {"paisti", "skilling"})
public class AutoItemCombinerPlugin extends Plugin {
    @Inject
    AutoItemCombinerConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    ThreadedScriptRunner runner = new ThreadedScriptRunner();
    SessionGuard sessionGuard = new SessionGuard(PluginId.PAUTOITEMCOMBINER, runner);
    @Inject
    PaistiBreakHandler paistiBreakHandler;

    private class WithdrawItemSetting {
        public final String itemNameOrId;
        public final int withdrawCount;

        public WithdrawItemSetting(String itemNameOrId, int withdrawCount) {
            this.itemNameOrId = itemNameOrId;
            this.withdrawCount = withdrawCount;
        }
    }

    private final List<WithdrawItemSetting> withdrawItemSettings = new ArrayList<WithdrawItemSetting>();

    private final HotkeyListener startHotkeyListener = new HotkeyListener(() -> config.startHotkey() != null ? config.startHotkey() : new Keybind(0, 0)) {
        @Override
        public void hotkeyPressed() {
            PaistiUtils.runOnExecutor(() -> {
                if (runner.isRunning()) {
                    stop();
                } else {
                    start();
                }
                return null;
            });
        }
    };

    @Provides
    public AutoItemCombinerConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoItemCombinerConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("PAutoItemCombiner: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });

        keyManager.registerKeyListener(startHotkeyListener);

        paistiBreakHandler.registerPlugin(this);
    }

    @Override
    protected void shutDown() throws Exception {
        paistiBreakHandler.unregisterPlugin(this);
        keyManager.unregisterKeyListener(startHotkeyListener);
        runner.stop();
    }

    private void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "PAutoItemCombiner");
        }
        paistiBreakHandler.stopPlugin(this);
        withdrawItemSettings.clear();
        runner.stop();
    }

    public boolean enoughMaterialsInBankToProceed() {
        for (var withdrawItemSetting : withdrawItemSettings) {
            if (!getHaveEnoughItemsInBank(withdrawItemSetting.itemNameOrId, withdrawItemSetting.withdrawCount - getItemQuantityInInventory(withdrawItemSetting.itemNameOrId))) {
                Utility.sendGameMessage("Not enough " + withdrawItemSetting.itemNameOrId + " in bank.", "PAutoItemCombiner");
                return false;
            }
        }
        return true;
    }

    private int getItemQuantityInInventory(String nameOrId) {
        var isOnlyDigits = nameOrId.matches("\\d+");
        if (isOnlyDigits) {
            var itemId = Integer.parseInt(nameOrId);
            return Inventory.getItemAmount(itemId);
        } else {
            return Inventory.getItemAmount(nameOrId);
        }
    }

    private boolean getHaveEnoughItemsInBank(String nameOrId, int requiredAmount) {
        var isOnlyDigits = nameOrId.matches("\\d+");
        if (isOnlyDigits) {
            var itemId = Integer.parseInt(nameOrId);
            if (!Bank.containsQuantity(itemId, requiredAmount)) {
                return false;
            }
        } else {
            if (!Bank.containsQuantity(nameOrId, requiredAmount)) {
                return false;
            }
        }

        return true;
    }

    public boolean enoughMaterialsInInventoryToProceed() {
        for (var withdrawItemSetting : withdrawItemSettings) {
            if (getItemQuantityInInventory(withdrawItemSetting.itemNameOrId) <= 0) {
                return false;
            }
        }
        return true;
    }

    public boolean handleWithdrawToQuantity(String itemNameOrId, int amount) {
        var isOnlyDigits = itemNameOrId.matches("\\d+");
        int withdrawAmount = amount - getItemQuantityInInventory(itemNameOrId);

        if (itemNameOrId.equalsIgnoreCase("thread")
                || itemNameOrId.equalsIgnoreCase("crystal shard")
                || itemNameOrId.toLowerCase().contains("tips")
                || itemNameOrId.equalsIgnoreCase("arrow shaft")
                || itemNameOrId.equalsIgnoreCase("feather")) {
            if (isOnlyDigits) {
                withdrawAmount = Bank.getQuantityInBank(Integer.parseInt(itemNameOrId));
            } else {
                withdrawAmount = Bank.getQuantityInBank(itemNameOrId);
            }
        }

        int finalWithdrawAmount = withdrawAmount;
        if (finalWithdrawAmount <= 0) return true;
        if (isOnlyDigits) {
            var itemId = Integer.parseInt(itemNameOrId);
            Bank.withdraw(itemId, withdrawAmount, false);
            return Utility.sleepUntilCondition(() -> Inventory.getItemAmount(itemId) >= amount, 3000, 100);
        } else {
            Bank.withdraw(itemNameOrId, withdrawAmount, false);
            return Utility.sleepUntilCondition(() -> Inventory.getItemAmount(itemNameOrId) >= amount, 3000, 100);
        }
    }

    public boolean handleBanking() {
        if (!Bank.isNearBank()) {
            Utility.sendGameMessage("Stopping because there's no nearby bank", "PAutoItemCombiner");
            stop();
        }
        if (enoughMaterialsInInventoryToProceed()) return false;
        if (!Bank.isOpen()) {
            Bank.openBank();
            Utility.sleepUntilCondition(Bank::isOpen, 10000, 100);
        }

        // Deposit all except the items we list as withdraw items
        var bankInventory = BankInventory.search().result();
        var itemsToDeposit = Utility.runOnClientThread(() -> bankInventory.stream().filter(i -> {
            for (var withdrawItemSetting : withdrawItemSettings) {
                var isOnlyDigits = withdrawItemSetting.itemNameOrId.matches("\\d+");
                if (isOnlyDigits && i.getItemId() == Integer.parseInt(withdrawItemSetting.itemNameOrId)) {
                    return false;
                }
                if (!isOnlyDigits) {
                    String cleanName = Widgets.getCleanName(i);
                    if (cleanName != null && cleanName.toLowerCase().contains(withdrawItemSetting.itemNameOrId.toLowerCase())) {
                        return false;
                    }
                }
            }
            return true;
        }).collect(Collectors.toList()));

        boolean shouldDepositAll = bankInventory.size() == itemsToDeposit.size();
        if (shouldDepositAll) {
            Bank.depositInventory();
            Utility.sleepUntilCondition(Inventory::isEmpty, 1800, 300);
        } else {
            HashSet<Integer> deposited = new HashSet<>();
            for (var item : itemsToDeposit) {
                if (deposited.contains(item.getItemId())) continue;
                deposited.add(item.getItemId());
                Bank.depositAll(item.getItemId());
                Utility.sleepGaussian(200, 350);
            }
        }


        if (!enoughMaterialsInBankToProceed()) {
            Utility.sleepGaussian(600, 800); // Sleep and check again in case the bank didn't update yet
            if (!enoughMaterialsInBankToProceed()) {
                Utility.sendGameMessage("Stopping because not enough items left in bank", "PAutoItemCombiner");
                stop();
            }
        }
        boolean withdrawFailed = false;
        for (var withdrawItemSetting : withdrawItemSettings) {
            if (!handleWithdrawToQuantity(withdrawItemSetting.itemNameOrId, withdrawItemSetting.withdrawCount)) {
                Utility.sendGameMessage("Failed to withdraw " + withdrawItemSetting.itemNameOrId, "PAutoItemCombiner");
                withdrawFailed = true;
                break;
            }
        }
        if (withdrawFailed) {
            stop();
            return false;
        }


        Bank.closeBank();
        Utility.sleepUntilCondition(() -> !Bank.isOpen(), 3000, 100);
        return true;
    }

    public Widget getFirstItemInInventory() {
        var isOnlyDigits = config.firstItemNameOrId().matches("\\d+");
        if (isOnlyDigits) {
            var itemId = Integer.parseInt(config.firstItemNameOrId());
            var item = Inventory.search().withId(itemId).first();
            if (item.isPresent()) {
                return item.get();
            }
        } else {
            var item = Inventory.search().matchesWildCardNoCase(config.firstItemNameOrId()).first();
            if (item.isPresent()) {
                return item.get();
            }
        }
        return null;
    }

    final Queue<Integer> lastTargetedSecondItemIndexes = new ArrayDeque<>();

    public Widget getSecondItemInInventory() {
        var isOnlyDigits = config.secondItemNameOrId().matches("\\d+");
        if (isOnlyDigits) {
            var itemId = Integer.parseInt(config.secondItemNameOrId());
            var item = Inventory.search()
                    .withId(itemId)
                    .result()
                    .stream().min(Comparator.comparingInt(i -> lastTargetedSecondItemIndexes.contains(i.getIndex()) ? 1 : 0));
            if (item.isPresent()) {
                lastTargetedSecondItemIndexes.add(item.get().getIndex());
                if (lastTargetedSecondItemIndexes.size() > 5) lastTargetedSecondItemIndexes.poll();
                return item.get();
            }
        } else {
            var item = Inventory.search()
                    .matchesWildCardNoCase(config.secondItemNameOrId())
                    .result()
                    .stream().min(Comparator.comparingInt(i -> lastTargetedSecondItemIndexes.contains(i.getIndex()) ? 1 : 0));
            if (item.isPresent()) {
                lastTargetedSecondItemIndexes.add(item.get().getIndex());
                if (lastTargetedSecondItemIndexes.size() > 5) lastTargetedSecondItemIndexes.poll();
                return item.get();
            }
        }
        return null;
    }

    public boolean handleCombining() {
        if (!shouldCombine()) return false;
        if (!enoughMaterialsInInventoryToProceed()) return false;
        var primaryItem = getFirstItemInInventory();
        var secondaryItem = getSecondItemInInventory();
        if (primaryItem == null || secondaryItem == null) {
            Utility.sendGameMessage("Primary or secondary item was null somehow", "PAutoItemCombiner");
            stop();
        }
        Interaction.useItemOnItem(primaryItem, secondaryItem);
        if (!config.spamCombine() || (config.spamCombine() && config.spamMin() >= 6000)) {
            Utility.sleepUntilCondition(MakeInterface::isMakeInterfaceOpen, 1800, 200);
        }
        if (MakeInterface.isMakeInterfaceOpen()) {
            if (config.makeInterfaceOptionName() == null || config.makeInterfaceOptionName().isEmpty()) {
                Keyboard.pressSpacebar();
            } else if (!MakeInterface.selectOptionWildcard(config.makeInterfaceOptionName())) {
                Keyboard.pressSpacebar();
            }
            Utility.sleepUntilCondition(() -> !MakeInterface.isMakeInterfaceOpen(), 1800, 600);
        }
        return true;
    }

    public boolean shouldCombine() {
        if (config.spamCombine()) return true;

        int idleTickThreshold = 3;
        // Glassblowing animations can have long idle ticks
        if (config.firstItemNameOrId().toLowerCase().contains("glassblowing")
                || config.secondItemNameOrId().toLowerCase().contains("glassblowing")) {
            idleTickThreshold = 5;
        }

        return Utility.getIdleTicks() >= idleTickThreshold;
    }

    private void start() {
        if (config.firstItemNameOrId().isEmpty() || config.firstItemWithdrawCount() == 0) {
            Utility.sendGameMessage("First item is required in config.", "PAutoItemCombiner");
            return;
        }
        if (config.secondItemNameOrId().isEmpty() || config.secondItemWithdrawCount() == 0) {
            Utility.sendGameMessage("Second item is required in config.", "PAutoItemCombiner");
            return;
        }
        lastTargetedSecondItemIndexes.clear();
        Utility.sendGameMessage("Started", "PAutoItemCombiner");
        paistiBreakHandler.startPlugin(this);
        withdrawItemSettings.clear();
        if (!config.firstItemNameOrId().isEmpty() && config.firstItemWithdrawCount() > 0) {
            withdrawItemSettings.add(new WithdrawItemSetting(config.firstItemNameOrId(), config.firstItemWithdrawCount()));
        }
        if (!config.secondItemNameOrId().isEmpty() && config.secondItemWithdrawCount() > 0) {
            withdrawItemSettings.add(new WithdrawItemSetting(config.secondItemNameOrId(), config.secondItemWithdrawCount()));
        }
        if (!config.extraItemNameOrId().isEmpty() && config.extraItemWithdrawCount() > 0) {
            withdrawItemSettings.add(new WithdrawItemSetting(config.extraItemNameOrId(), config.extraItemWithdrawCount()));
        }

        runner.start();
    }

    private void threadedLoop() {
        if (!Utility.isLoggedIn()) {
            stop();
            return;
        }
        if (handleCombining()) {
            if (!config.spamCombine()) {
                Utility.sleepGaussian(600, 1200);
            } else {
                Utility.sleepGaussian(config.spamMin(), Math.max(config.spamMax(), config.spamMin() + 20));
            }
            return;
        }
        if (paistiBreakHandler.shouldBreak(this)) {
            Utility.sendGameMessage("Taking a break", "PAutoItemCombiner");
            Utility.sleepGaussian(2000, 3000);
            paistiBreakHandler.startBreak(this);
            Utility.sleepGaussian(1000, 2000);
            Utility.sleepUntilCondition(() -> !paistiBreakHandler.isBreakActive(this) && Utility.isLoggedIn(), 99999999, 5000);
            return;
        }
        if (handleBanking()) {
            Utility.sleepGaussian(600, 1200);
            return;
        }

        Utility.sleepGaussian(200, 300);
    }
}
