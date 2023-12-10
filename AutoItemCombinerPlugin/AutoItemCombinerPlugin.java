package com.theplug.AutoItemCombinerPlugin;

import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Queue;

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
    @Inject
    PaistiBreakHandler paistiBreakHandler;

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
        runner.stop();
    }

    public boolean enoughMaterialsInBankToProceed() {
        var primaryIsOnlyDigits = config.primaryItemNameOrId().matches("\\d+");
        if (primaryIsOnlyDigits) {
            var itemId = Integer.parseInt(config.primaryItemNameOrId());
            if (!Bank.containsQuantity(itemId, config.primaryItemWithdrawCount())) {
                Utility.sendGameMessage("Not enough primary items left in bank", "PAutoItemCombiner");
                return false;
            }
        } else {
            if (!Bank.containsQuantity(config.primaryItemNameOrId(), config.primaryItemWithdrawCount())) {
                Utility.sendGameMessage("Not enough primary items left in bank", "PAutoItemCombiner");
                return false;
            }
        }
        var secondaryIsOnlyDigits = config.secondaryItemNameOrId().matches("\\d+");
        if (secondaryIsOnlyDigits) {
            var itemId = Integer.parseInt(config.secondaryItemNameOrId());
            if (!Bank.containsQuantity(itemId, config.secondaryItemWithdrawCount())) {
                Utility.sendGameMessage("Not enough secondary items left in bank", "PAutoItemCombiner");
                return false;
            }
        } else {
            if (!Bank.containsQuantity(config.secondaryItemNameOrId(), config.secondaryItemWithdrawCount())) {
                Utility.sendGameMessage("Not enough secondary items left in bank", "PAutoItemCombiner");
                return false;
            }
        }
        return true;
    }

    public boolean enoughMaterialsInInventoryToProceed() {
        var primaryIsOnlyDigits = config.primaryItemNameOrId().matches("\\d+");
        var enoughPrimaryItems = true;
        if (primaryIsOnlyDigits) {
            var itemId = Integer.parseInt(config.primaryItemNameOrId());
            if (Inventory.getItemAmount(itemId) == 0) {
                enoughPrimaryItems = false;
            }
        } else {
            if (Inventory.getItemAmount(config.primaryItemNameOrId()) == 0) {
                enoughPrimaryItems = false;
            }
        }
        var secondaryIsOnlyDigits = config.secondaryItemNameOrId().matches("\\d+");
        var enoughSecondaryItems = true;
        if (secondaryIsOnlyDigits) {
            var itemId = Integer.parseInt(config.secondaryItemNameOrId());
            if (Inventory.getItemAmount(itemId) == 0) {
                enoughSecondaryItems = false;
            }
        } else {
            if (Inventory.getItemAmount(config.secondaryItemNameOrId()) == 0) {
                enoughSecondaryItems = false;
            }
        }
        return enoughPrimaryItems && enoughSecondaryItems;
    }

    public void handleWithdraw(String itemNameOrId, int amount) {
        var isOnlyDigits = itemNameOrId.matches("\\d+");
        if (isOnlyDigits) {
            var itemId = Integer.parseInt(itemNameOrId);
            Bank.withdraw(itemId, amount, false);
            Utility.sleepUntilCondition(() -> Inventory.getItemAmount(itemId) == amount, 3000, 100);
        } else {
            Bank.withdraw(itemNameOrId, amount, false);
            Utility.sleepUntilCondition(() -> Inventory.getItemAmount(itemNameOrId) == amount, 3000, 100);
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
            Utility.sleepUntilCondition(() -> Bank.isOpen(), 10000, 100);
        }
        Bank.depositInventory();
        if (!enoughMaterialsInBankToProceed()) {
            Utility.sendGameMessage("Stopping because not enough items left in bank", "PAutoItemCombiner");
            stop();
        }

        Utility.sleepGaussian(600, 1200);
        handleWithdraw(config.primaryItemNameOrId(), config.primaryItemWithdrawCount());
        handleWithdraw(config.secondaryItemNameOrId(), config.secondaryItemWithdrawCount());

        Bank.closeBank();
        Utility.sleepUntilCondition(() -> !Bank.isOpen(), 3000, 100);
        return true;
    }

    public Widget getFirstItemInInventory() {
        var isOnlyDigits = config.primaryItemNameOrId().matches("\\d+");
        if (isOnlyDigits) {
            var itemId = Integer.parseInt(config.primaryItemNameOrId());
            var item = Inventory.search().withId(itemId).first();
            if (item.isPresent()) {
                return item.get();
            }
        } else {
            var item = Inventory.search().matchesWildCardNoCase(config.primaryItemNameOrId()).first();
            if (item.isPresent()) {
                return item.get();
            }
        }
        return null;
    }

    final Queue<Integer> lastTargetedSecondItemIndexes = new ArrayDeque<>();

    public Widget getSecondItemInInventory() {
        var isOnlyDigits = config.secondaryItemNameOrId().matches("\\d+");
        if (isOnlyDigits) {
            var itemId = Integer.parseInt(config.secondaryItemNameOrId());
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
                    .matchesWildCardNoCase(config.secondaryItemNameOrId())
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
        if (!config.spamCombine()) {
            Utility.sleepUntilCondition(MakeInterface::isMakeInterfaceOpen, 1800, 100);
            Keyboard.pressSpacebar();
            Utility.sleepGaussian(1200, 1800);
        }
        return true;
    }

    public boolean shouldCombine() {
        if (config.spamCombine()) return true;
        return Utility.getIdleTicks() >= 2;
    }

    private void start() {
        lastTargetedSecondItemIndexes.clear();
        Utility.sendGameMessage("Started", "PAutoItemCombiner");
        paistiBreakHandler.startPlugin(this);
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
                Utility.sleepGaussian(config.spamMin(), config.spamMax());
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
