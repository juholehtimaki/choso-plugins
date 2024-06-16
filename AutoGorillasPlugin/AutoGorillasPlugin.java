package com.theplug.AutoGorillasPlugin;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.AutoGorillasPlugin.States.GorillasState;
import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.GameSimulator.Trackers.ActorTracker;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.VardorvisPlugin.States.State;
import lombok.Getter;
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
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@PluginDescriptor(name = "AutoGorillas", description = "Automates demonic gorillas", enabledByDefault = false, tags = {"paisti", "choso", "gorillas", "pvm"})
public class AutoGorillasPlugin extends Plugin {
    @Inject
    public AutoGorillasPluginConfig config;
    @Inject
    private KeyManager keyManager;
    @Inject
    PluginManager pluginManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    EventBus eventBus;

    @Inject
    public PaistiBreakHandler paistiBreakHandler;

    @Inject
    private AutoGorillasPluginScreenOverlay screenOverlay;

    @Inject
    private AutoGorillasSceneOverlay sceneOverlay;

    @Inject
    public FoodStats foodStats;

    @Inject
    private ConfigManager configManager;

    public ThreadedScriptRunner runner = new ThreadedScriptRunner();
    List<State> states;
    GorillasState gorillasState;

    @Inject
    @Getter
    private ActorTracker actorTracker;

    InventoryLoadout.InventoryLoadoutSetup loadout;

    public Map<GorillaCombatStyle, InventoryLoadout.InventoryLoadoutSetup> gearSwitches = new HashMap<>();

    @Provides
    public AutoGorillasPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoGorillasPluginConfig.class);
    }

    public boolean isRunning() {
        return runner.isRunning();
    }

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

    public void start() {
        Utility.sendGameMessage("Started", "AutoGorillas");
        initialize();
        paistiBreakHandler.startPlugin(this);
        runner.start();
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("AutoGorillas: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }
        keyManager.registerKeyListener(startHotkeyListener);
        overlayManager.add(screenOverlay);
        overlayManager.add(sceneOverlay);

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });

        runner.setOnGameTickAction(() -> {
            this.threadedOnGameTick();
            return null;
        });

        paistiBreakHandler.registerPlugin(this);
    }

    private void threadedLoop() {
        for (var state : states) {
            if (state.shouldExecuteState()) {
                state.threadedLoop();
                return;
            }
        }
        Utility.sleepGaussian(100, 200);
    }

    @Override
    protected void shutDown() throws Exception {
        paistiBreakHandler.unregisterPlugin(this);
        overlayManager.remove(screenOverlay);
        overlayManager.remove(sceneOverlay);
        keyManager.unregisterKeyListener(startHotkeyListener);
        stop();
    }

    private void initialize() {
        if (states != null) {
            for (var state : states) {
                eventBus.unregister(state);
            }
        }

        gorillasState = new GorillasState(this, config);
        states = List.of(gorillasState);
        for (var state : states) {
            eventBus.register(state);
        }
        gearSwitches.put(config.firstLoadoutCombatStyle(), InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.firstLoadout()));
        gearSwitches.put(config.secondLoadoutCombatStyle(), InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.secondLoadout()));
    }

    private void threadedOnGameTick() {
        for (var state : states) {
            if (state.shouldExecuteState()) {
                state.threadedOnGameTick();
                break;
            }
        }
    }


    public void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "AutoGorillas");
        }
        paistiBreakHandler.stopPlugin(this);
        runner.stop();
    }

    @Subscribe(priority = 100)
    public void onGameTick(GameTick e) {
        if (!isRunning()) return;

        runner.onGameTick();
    }

    public Duration getRunTimeDuration() {
        return Duration.between(runner.getStartedAt(), Instant.now());
    }
}