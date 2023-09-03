package com.PaistiPlugins.StonemasonPlugin;

import com.PaistiPlugins.PaistiUtils.API.*;
import com.PaistiPlugins.PaistiUtils.API.Shop;
import com.PaistiPlugins.PaistiUtils.Framework.ThreadedScriptRunner;
import com.PaistiPlugins.PaistiUtils.PaistiUtils;
import com.PaistiPlugins.PaistiUtils.PathFinding.LocalPathfinder;
import com.PaistiPlugins.PaistiUtils.PathFinding.WebWalker;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(name = "PStonemason", description = "Buys gold leafs from Stonemason", enabledByDefault = false, tags = {"Money", "Choso"})
public class StonemasonPlugin extends Plugin {
    static final WorldPoint BANK_SPOT = new WorldPoint(2838, 10209, 0);
    static final WorldPoint STONEMASON_SPOT = new WorldPoint(2849, 10184, 0);

    @Inject
    StonemasonPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    ThreadedScriptRunner runner = new ThreadedScriptRunner();
    private HotkeyListener startHotkeyListener = null;

    @Provides
    public StonemasonPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(StonemasonPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("PStonemasor: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });

        startHotkeyListener = config.startHotkey() != null ? new HotkeyListener(() -> config.startHotkey()) {
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
        } : null;
        if (startHotkeyListener != null) {
            keyManager.registerKeyListener(startHotkeyListener);
        }
    }


    @Override
    protected void shutDown() throws Exception {
        runner.stop();
    }

    private void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "PStonemason");
        }
        runner.stop();
    }

    private boolean handleBuying() {
        var stonemasor = NPCs.search().withName("Stonemason").withAction("Trade").nearestToPlayer();
        if (stonemasor.isPresent()) {
            var stonemasorLocation = stonemasor.get().getWorldLocation();
            if (!LocalPathfinder.getReachabilityMap().isReachable(stonemasorLocation) || Walking.getPlayerLocation().distanceTo(stonemasorLocation) > 20) {
                return goToStonemason();
            }
            if (!Shop.isOpen()) {
                Interaction.clickNpc(stonemasor.get(), "Trade");
                var shopOpened = Utility.sleepUntilCondition(() -> Shop.isOpen(), 10000);
                if (!shopOpened) {
                    Utility.sendGameMessage("Failed to open stonemason shop", "PStonemason");
                    stop();
                }

            } else {
                if (Shop.getQuantity("Gold leaf") == 20) {
                    Shop.buy("Gold leaf", 1);
                    Utility.sleepUntilCondition(() -> Shop.getQuantity("Gold leaf") < 20, 3000);
                }
                Shop.closeShop();
                Utility.sleepUntilCondition(() -> !Shop.isOpen());
                if (!Worldhopping.hopToNext(false)) {
                    Utility.sendGameMessage("Failed to hop", "PStonemason");
                    stop();
                }
            }
        } else {
            Utility.sendGameMessage("Failed to find stonemason", "PStonemason");
            stop();
        }
        return true;
    }

    private boolean goToStonemason() {
        return WebWalker.walkTo(STONEMASON_SPOT);
    }

    private boolean handleBanking() {
        if (Walking.getPlayerLocation().distanceTo(BANK_SPOT) > 10) {
            var walkedToBank = WebWalker.walkTo(BANK_SPOT.dx(Utility.random(-1, 1)).dy(Utility.random(-1, 1)));
            if (!walkedToBank) {
                Utility.sendGameMessage("Failed to bank", "PStonemason");
                stop();
            }
            return true;
        }

        var bank = Bank.openBank();

        if (bank && Utility.sleepUntilCondition(Bank::isOpen)) {
            var itemsToDeposit = Inventory.search().filter(i -> i.getItemId() != 995).result();
            for (var items : itemsToDeposit) {
                Bank.depositAll(items);
                Utility.sleepGaussian(300, 600);
            }
            return true;
        }

        Utility.sendGameMessage("Failed to reach bank", "PStonemason");
        stop();
        return false;
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
        if (e.getGroup().equals("StonemasonPluginConfig") && e.getKey().equals("startHotkey")) {
            if (startHotkeyListener != null) {
                keyManager.unregisterKeyListener(startHotkeyListener);
            }
            startHotkeyListener = config.startHotkey() != null ? new HotkeyListener(() -> config.startHotkey()) {
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
            } : null;
            if (startHotkeyListener != null) {
                keyManager.registerKeyListener(startHotkeyListener);
            }
        }
    }

    private void start() {
        Utility.sendGameMessage("Started", "PStonemason");
        runner.start();
    }

    private void threadedLoop() {
        try {
            if (!Utility.isLoggedIn()) {
                stop();
                return;
            }
            Utility.sleepGaussian(300, 500);
            if (Inventory.getItemAmount(995) < 150000) {
                Utility.sendGameMessage("Out of money", "PStonemason");
                stop();
            }
            if (Inventory.isFull()) {
                handleBanking();
                return;
            }
            if (Walking.getPlayerLocation().distanceTo(STONEMASON_SPOT) >= 5) {
                WebWalker.walkTo(STONEMASON_SPOT);
                return;
            }
            handleBuying();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Subscribe
    private void onGameTick(GameTick e) {
        /*
        log.debug("onGametick");
        var shopInventory = Shop.getQuantity("Gold leaf");
        System.out.println(shopInventory);
         */
    }
}
