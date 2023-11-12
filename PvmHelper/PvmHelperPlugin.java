package com.theplug.PvmHelper;

import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.AttackTickTracker.AttackTickTracker;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.PvmHelper.States.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;


@Slf4j
@PluginDescriptor(name = "PvmHelper", description = "Helps with various different bosses", enabledByDefault = false, tags = {"paisti", "choso", "pvm", "helper"})
public class PvmHelperPlugin extends Plugin {
    @Inject
    public PvmHelperPluginConfig config;
    @Inject
    private KeyManager keyManager;
    @Inject
    PluginManager pluginManager;
    @Inject
    public AttackTickTracker attackTickTracker;
    @Inject
    EventBus eventBus;
    List<State> states;
    LeviathanHelperState leviathanHelper;
    VardorvisHelperState vardorvisHelperState;
    WhispererHelperState whispererHelperState;
    MuspahHelperState muspahHelperState;

    public ThreadedScriptRunner runner = new ThreadedScriptRunner();

    private final HotkeyListener shadowBarrageHotkeyListener = new HotkeyListener(() -> config.shadowBarrageHotkey() != null ? config.shadowBarrageHotkey() : new Keybind(0, 0)) {
        @Override
        public void hotkeyPressed() {
            leviathanHelper.castShadowBarrageOnLeviathan();
        }
    };

    private final HotkeyListener iceBarrageHotkeyListener = new HotkeyListener(() -> config.iceBarrageHotkey() != null ? config.iceBarrageHotkey() : new Keybind(0, 0)) {
        @Override
        public void hotkeyPressed() {
            whispererHelperState.castIceBarrageOnWhisperer();
        }
    };

    @Provides
    public PvmHelperPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(PvmHelperPluginConfig.class);
    }


    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("PvmHelper: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });

        runner.setOnGameTickAction(() -> {
            this.threadedOnGameTick();
            return null;
        });

        initialize();
        keyManager.registerKeyListener(shadowBarrageHotkeyListener);
        keyManager.registerKeyListener(iceBarrageHotkeyListener);
        runner.start();
    }

    @Override
    protected void shutDown() throws Exception {
        keyManager.unregisterKeyListener(shadowBarrageHotkeyListener);
        keyManager.registerKeyListener(iceBarrageHotkeyListener);
    }

    private void threadedLoop() {
        for (var state : states) {
            if (state.shouldExecuteState()) {
                state.threadedLoop();
                return;
            }
        }
        Utility.sleepGaussian(50, 100);
    }

    private void initialize() {
        if (states != null) {
            for (var state : states) {
                eventBus.unregister(state);
            }
        }

        leviathanHelper = new LeviathanHelperState(this, config);
        vardorvisHelperState = new VardorvisHelperState(this, config);
        muspahHelperState = new MuspahHelperState(this, config);
        whispererHelperState = new WhispererHelperState(this, config);

        states = List.of(
                leviathanHelper,
                vardorvisHelperState,
                muspahHelperState,
                whispererHelperState
        );

        for (var state : states) {
            eventBus.register(state);
        }
    }

    private void threadedOnGameTick() {
        for (var state : states) {
            if (state.shouldExecuteState()) {
                state.threadedOnGameTick();
                break;
            }
        }
    }

    @Subscribe(priority = 100)
    public void onGameTick(GameTick e) {
        runner.onGameTick();
    }

    public boolean handleDisableAllPrayers() {
        boolean disabledPrayer = false;
        for (var prayer : PPrayer.values()) {
            if (prayer.isActive()) {
                disabledPrayer = true;
                prayer.setEnabled(false);
                Utility.sleepGaussian(50, 100);
            }
        }
        return disabledPrayer;
    }

    public PPrayer getBestOffensiveRangedPrayer () {
        List<PPrayer> possiblePrayers = Arrays.asList(PPrayer.RIGOUR, PPrayer.EAGLE_EYE, PPrayer.HAWK_EYE, PPrayer.SHARP_EYE);
        Optional<PPrayer> bestPrayer = possiblePrayers.stream().filter(PPrayer::canUse).findFirst();
        return bestPrayer.orElse(null);
    }

    public PPrayer getBestOffensiveMagePrayer () {
        List<PPrayer> possiblePrayers = Arrays.asList(PPrayer.AUGURY, PPrayer.MYSTIC_MIGHT, PPrayer.MYSTIC_LORE, PPrayer.MYSTIC_WILL);
        Optional<PPrayer> bestPrayer = possiblePrayers.stream().filter(PPrayer::canUse).findFirst();
        return bestPrayer.orElse(null);
    }
}