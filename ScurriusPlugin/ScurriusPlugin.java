package com.theplug.ScurriusPlugin;

import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.AttackTickTracker.AttackTickTracker;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.ScurriusPlugin.States.FightScurriusState;
import com.theplug.ScurriusPlugin.States.RestockState;
import com.theplug.ScurriusPlugin.States.State;
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
@PluginDescriptor(name = "<HTML><FONT COLOR=#1BB532>AutoScurrius</FONT></HTML>", description = "Automates Scurrius", enabledByDefault = false, tags = {"paisti", "choso", "scurrius"})
public class ScurriusPlugin extends Plugin {
    @Inject
    public ScurriusPluginConfig config;
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

    @Inject
    public PaistiBreakHandler paistiBreakHandler;

    List<State> states;

    FightScurriusState fightScurriusState;
    RestockState restockState;

    @Inject
    private ScurriusPluginSceneOverlay sceneOverlay;

    @Inject
    private ScurriusPluginScreenOverlay screenOverlay;

    @Inject
    private ConfigManager configManager;

    public ThreadedScriptRunner runner = new ThreadedScriptRunner();

    @Getter
    @Setter
    private int totalKillCount = 0;

    public static final WorldPoint SCURRIUS_ENTRANCE_WORLD_POINT = new WorldPoint(3276, 9871, 0);

    @Provides
    public ScurriusPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(ScurriusPluginConfig.class);
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
        Utility.sendGameMessage("Started", "AutoScurrius");
        initialize();
        setTotalKillCount(0);
        paistiBreakHandler.startPlugin(this);
        runner.start();
    }

    @Override
    protected void startUp() throws Exception {
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

        paistiBreakHandler.registerPlugin(this);
    }

    private void threadedLoop() {
        if (!Utility.isLoggedIn()) {
            if (!Utility.sleepUntilCondition(Utility::isLoggedIn, 10000, 600)) {
                Utility.sendGameMessage("Not logged in, stopping.", "AutoScurrius");
                stop();
            }
            Utility.sleepGaussian(600, 700);
        }

        for (var state : states) {
            if (state.shouldExecuteState()) {
                state.threadedLoop();
                return;
            }
        }
        Utility.sleepGaussian(60, 100);
    }

    @Override
    protected void shutDown() throws Exception {
        paistiBreakHandler.unregisterPlugin(this);
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

        fightScurriusState = new FightScurriusState(this, config);
        restockState = new RestockState(this, config);

        states = List.of(
                fightScurriusState,
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
            Utility.sendGameMessage("Stopped", "AutoScurrius");
        }
        paistiBreakHandler.stopPlugin(this);
        runner.stop();
    }

    @Subscribe(priority = 5000)
    public void onActorDeath(ActorDeath actorDeath) {
        if (!runner.isRunning()) return;
        Actor actor = actorDeath.getActor();
        if (actor instanceof Player) {
            Player player = (Player) actor;
            if (player == PaistiUtils.getClient().getLocalPlayer()) {
                Utility.sendGameMessage("Player has died!", "AutoScurrius");
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

    public boolean isInsideScurriusArea() {
        return Utility.isInInstancedRegion() && TileObjects.search().withId(14204).first().isPresent();
    }
}