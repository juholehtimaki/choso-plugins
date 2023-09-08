package com.PaistiPlugins.AutoNexPlugin;

import com.PaistiPlugins.AutoGauntletPlugin.AutoGauntletPluginScreenOverlay;
import com.PaistiPlugins.PaistiUtils.API.*;
import com.PaistiPlugins.PaistiUtils.API.AttackTickTracker.AttackTickTracker;
import com.PaistiPlugins.PaistiUtils.Framework.ThreadedScriptRunner;
import com.PaistiPlugins.PaistiUtils.PaistiUtils;
import com.PaistiPlugins.VorkathKillerPlugin.States.State;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
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

import java.util.List;

@Slf4j
@PluginDescriptor(name = "AutoNex", description = "Helps with PvP", enabledByDefault = false, tags = {"paisti", "pvp"})
public class AutoNexPlugin extends Plugin {
    @Inject
    AutoNexPluginConfig config;
    @Inject
    private KeyManager keyManager;
    @Inject
    PluginManager pluginManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    AttackTickTracker attackTickTracker;

    @Inject
    EventBus eventBus;

    @Inject
    public FoodStats foodStats;

    List<State> states;

    PrepareState prepareState;

    FightNexState fightNexState;

    private static final WorldPoint NEX_TILE = new WorldPoint(7141, 1235, 0);

    @Inject
    private AutoNexPluginScreenOverlay screenOverlay;

    ThreadedScriptRunner runner = new ThreadedScriptRunner();

    @Provides
    public AutoNexPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoNexPluginConfig.class);
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
        Utility.sendGameMessage("Started", "AutoNex");
        initialize();
        runner.start();
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("PAutoGauntlet: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }
        keyManager.registerKeyListener(startHotkeyListener);
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
        if (!playerInsideGodWars()) {
            Utility.sendGameMessage("Must be inside GWD", "AutoNex");
            stop();
        }
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
        overlayManager.remove(screenOverlay);
    }

    private void initialize() {
        if (states != null) {
            for (var state : states) {
                eventBus.unregister(state);
            }
        }

        prepareState = new PrepareState(this);
        fightNexState = new FightNexState(this);
        states = List.of(
                prepareState,
                fightNexState
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
            Utility.sendGameMessage("Stopped", "AutoNex");
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
                Utility.sendGameMessage("Player has died!", "AutoNex");
                stop();
            }
        }
    }

    @Subscribe(priority = 100)
    public void onGameTick(GameTick e) {
        if (!isRunning()) return;

        runner.onGameTick();
    }

    public boolean playerInsideGodWars() {
        return Widgets.isValidAndVisible(Widgets.getWidget(WidgetInfo.GWD_KC));
    }

    public List<Widget> getFoodItems() {
        return Inventory.search().onlyUnnoted().filter((item) -> foodStats.getHealAmount(item.getItemId()) >= 8).result();
    }

    public boolean isInsideNexRoom() {
        return Walking.getPlayerLocation().distanceTo(NEX_TILE) < 15;
    }
}
