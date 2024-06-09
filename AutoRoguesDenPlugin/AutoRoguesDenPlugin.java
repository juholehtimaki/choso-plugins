package com.theplug.AutoRoguesDenPlugin;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.SharedLogic.RoguesDenHandler.RoguesDenHandler;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@PluginDescriptor(name = "AutoRoguesDen", description = "Automatically does rogues den", enabledByDefault = false, tags = {"paisti", "choso", "skilling", "thieving"})
public class AutoRoguesDenPlugin extends Plugin {
    @Inject
    AutoRoguesDenPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    @Inject
    PaistiBreakHandler paistiBreakHandler;
    ThreadedScriptRunner runner = new ThreadedScriptRunner();

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
    @Inject
    private AutoRoguesDenPluginSceneOverlay sceneOverlay;
    @Inject
    private AutoRoguesDenPluginScreenOverlay screenOverlay;

    private RoguesDenHandler.RoguesDenSettings settings;
    @Inject
    OverlayManager overlayManager;

    @Provides
    public AutoRoguesDenPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoRoguesDenPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("AutoRoguesDen: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }
        overlayManager.add(sceneOverlay);
        overlayManager.add(screenOverlay);

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });

        keyManager.registerKeyListener(startHotkeyListener);
        paistiBreakHandler.registerPlugin(this);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(screenOverlay);
        keyManager.unregisterKeyListener(startHotkeyListener);
        paistiBreakHandler.unregisterPlugin(this);
        runner.stop();
    }

    private void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "AutoRoguesDen");
        }
        paistiBreakHandler.stopPlugin(this);
        runner.stop();
    }

    private void start() {
        Utility.sendGameMessage("Started", "AutoRoguesDen");
        paistiBreakHandler.startPlugin(this);

        this.settings = RoguesDenHandler.RoguesDenSettings.builder()
                .useRunRestores(config.useRunRestores())
                .stopAtFiveCrates(config.stopAfterFiveCrates())
                .build();
        runner.start();
    }


    private void threadedLoop() {
        boolean isInRoguesDenGame = RoguesDenHandler.hasMysticJewel();

        if (!isInRoguesDenGame && paistiBreakHandler.shouldBreak(this)) {
            Utility.sendGameMessage("Taking a break", "AutoRoguesDen");
            Utility.sleepGaussian(2000, 3000);
            paistiBreakHandler.startBreak(this);
            Utility.sleepGaussian(1000, 2000);
            Utility.sleepUntilCondition(() -> !paistiBreakHandler.isBreakActive(this) && Utility.isLoggedIn(), 99999999, 5000);
            return;
        }

        if (config.stopAfterFiveCrates() && Bank.containsQuantity(ItemID.ROGUES_EQUIPMENT_CRATE, 5)) {
            Utility.sendGameMessage("5 Rogues equipment crates acquired. Stopping", "AutoRoguesDen");
            stop();
            return;
        }

        RoguesDenHandler.handleRoguesDenPluginLogic(this.settings);
    }

    public Duration getRunTimeDuration() {
        return Duration.between(runner.getStartedAt(), Instant.now());
    }

    public boolean isRunning() {
        return runner.isRunning();
    }

}
