package com.theplug.AutoHueycoatlPlugin;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.AutoHueycoatlPlugin.States.BossState;
import com.theplug.AutoHueycoatlPlugin.States.PreBossState;
import com.theplug.AutoHueycoatlPlugin.States.RestockState;
import com.theplug.AutoHueycoatlPlugin.States.State;
import com.theplug.OBS.ThreadedRunner;
import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@PluginDescriptor(name = "<HTML><FONT COLOR=#1BB532>AutoHueycoatl</FONT></HTML>", description = "AutoHueycoatl", enabledByDefault = false, tags = {"paisti", "choso", "pvm", "Hueycoatl"})
public class AutoHueycoatlPlugin extends Plugin {
    @Inject
    AutoHueycoatlPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    @Inject
    PaistiBreakHandler paistiBreakHandler;
    ThreadedRunner runner = new ThreadedRunner();
    @Inject
    private AutoHueycoatlPluginScreenOverlay screenOverlay;
    @Inject
    private AutoHueycoatlPluginSceneOverlay sceneOverlay;
    @Inject
    private ConfigManager configManager;
    @Inject
    EventBus eventBus;
    @Inject
    FoodStats foodStats;
    @Inject
    OverlayManager overlayManager;

    List<State> states;
    BossState bossState;
    PreBossState preBossState;
    RestockState restockState;

    @Getter
    private final int MELEE_MISSILE_ID = 2969;
    @Getter
    private final int MAGIC_MISSILE_ID = 2975;
    @Getter
    private final int RANGED_MISSILE_ID = 2972;
    @Getter
    private final int PUDDLE_GRAPHIC_ID = 3001;
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
    public AutoHueycoatlPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoHueycoatlPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        overlayManager.add(sceneOverlay);
        overlayManager.add(screenOverlay);

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });

        runner.setOnGameTickAction(() -> {
            this.threadedOnGameTick();
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
            Utility.sendGameMessage("Stopped", "AutoHueycoatl");
        }
        paistiBreakHandler.stopPlugin(this);
        runner.stop();
    }

    private void start() {
        Utility.sendGameMessage("Started", "AutoHueycoatl");
        initialize();
        paistiBreakHandler.startPlugin(this);
        runner.start();
    }

    private void initialize() {
        if (states != null) {
            for (var state : states) {
                eventBus.unregister(state);
            }
        }

        bossState = new BossState(this, config);
        preBossState = new PreBossState(this, config);
        restockState = new RestockState(this, config);

        states = List.of(
                bossState,
                preBossState,
                restockState
        );

        for (var state : states) {
            eventBus.register(state);
        }
    }

    private void threadedLoop() {
        if (!Utility.isLoggedIn()) {
            if (!Utility.sleepUntilCondition(Utility::isLoggedIn, 10000, 300)) {
                log.info("Player is not logged in, stopping");
                stop();
                return;
            }
        }
        for (var state : states) {
            if (state.shouldExecuteState()) {
                state.threadedLoop();
                return;
            }
        }
        Utility.sleepGaussian(20, 60);
    }

    private void threadedOnGameTick() {
        for (var state : states) {
            if (state.shouldExecuteState()) {
                state.threadedOnGameTick();
                break;
            }
        }
    }

    public Duration getRunTimeDuration() {
        var started = runner.getStartedAt();
        if (started == null) {
            return Duration.ZERO;
        }
        return Duration.between(started, Instant.now());
    }

    public boolean isRunning() {
        return runner.isRunning();
    }

    public List<Widget> getFoodItems() {
        return Inventory.search().onlyUnnoted().filter((item) -> foodStats.getHealAmount(item.getItemId()) >= 8).result();
    }

}
