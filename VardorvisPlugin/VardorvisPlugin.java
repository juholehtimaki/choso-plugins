package com.theplug.VardorvisPlugin;

import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.AttackTickTracker.AttackTickTracker;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.VardorvisPlugin.States.FightVardorvisState;
import com.theplug.VardorvisPlugin.States.RestockState;
import com.theplug.VardorvisPlugin.States.State;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
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
import java.util.List;


@Slf4j
@PluginDescriptor(name = "AutoVardorvis", description = "Automates Vardorvis", enabledByDefault = false, tags = {"paisti", "choso", "vardorvis"})
public class VardorvisPlugin extends Plugin {
    @Inject
    public VardorvisPluginConfig config;
    @Inject
    private KeyManager keyManager;
    @Inject
    PluginManager pluginManager;

    @Inject
    public AttackTickTracker attackTickTracker;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    EventBus eventBus;

    @Inject
    public FoodStats foodStats;

    List<State> states;

    FightVardorvisState fightVardorvisState;
    RestockState restockState;

    @Inject
    private VardorvisPluginSceneOverlay sceneOverlay;

    @Inject
    private VardorvisPluginScreenOverlay screenOverlay;

    @Inject
    private ConfigManager configManager;

    public ThreadedScriptRunner runner = new ThreadedScriptRunner();

    @Getter
    @Setter
    private int totalKillCount = 0;

    public static final WorldPoint NEAR_VARDORVIS_ENTRANCE = new WorldPoint(1117, 3430, 0);

    @Provides
    public VardorvisPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(VardorvisPluginConfig.class);
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
        Utility.sendGameMessage("Started", "AutoVardorvis");
        initialize();
        setTotalKillCount(0);
        runner.start();
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("AutoVardorvis: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }
        keyManager.registerKeyListener(startHotkeyListener);
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
        stop();
        keyManager.unregisterKeyListener(startHotkeyListener);
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(screenOverlay);
    }

    private void initialize() {
        if (states != null) {
            for (var state : states) {
                eventBus.unregister(state);
            }
        }

        fightVardorvisState = new FightVardorvisState(this, config);
        restockState = new RestockState(this, config);

        states = List.of(
                fightVardorvisState,
                restockState
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


    public void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "AutoVardorvis");
        }
        runner.stop();
    }

    @Subscribe(priority = 5000)
    public void onActorDeath(ActorDeath actorDeath) {
        if (!runner.isRunning()) return;
        Actor actor = actorDeath.getActor();
        if (actor instanceof Player) {
            Player player = (Player) actor;
            if (player == PaistiUtils.getClient().getLocalPlayer()) {
                Utility.sendGameMessage("Player has died!", "AutoVardorvis");
                stop();
            }
        }
    }

    @Subscribe(priority = 100)
    public void onGameTick(GameTick e) {
        if (!isRunning()) return;

        runner.onGameTick();
    }
    public Duration getRunTimeDuration() {
        return Duration.between(runner.getStartedAt(), Instant.now());
    }

    public boolean isInsideVardorvisArea() {
        var northWestObj = TileObjects.search().withId(48428).first();
        return northWestObj.isPresent() && Utility.isInInstancedRegion();
    }
}