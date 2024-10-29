package com.theplug.ColosseumFarmerPlugin;

import com.theplug.ColosseumFarmerPlugin.States.FightColosseum;
import com.theplug.ColosseumFarmerPlugin.States.PreAndPostGame;
import com.theplug.ColosseumFarmerPlugin.States.Prepare;
import com.theplug.ColosseumFarmerPlugin.States.State;
import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.AttackTickTracker.AttackTickTracker;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.OBS.ThreadedRunner;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
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
import java.util.concurrent.atomic.AtomicReference;


@Slf4j
@PluginDescriptor(name = "<HTML><FONT COLOR=#1BB532>ColosseumFarmer</FONT></HTML>", description = "Farms first colosseum wave", enabledByDefault = false, tags = {"paisti", "choso", "colosseum"})
public class ColosseumFarmerPlugin extends Plugin {
    @Inject
    public ColosseumFarmerPluginConfig config;
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

    FightColosseum fightColosseum;
    PreAndPostGame restockState;
    Prepare prepareState;

    @Inject
    private ColosseumFarmerPluginSceneOverlay sceneOverlay;

    @Inject
    private ColosseumFarmerPluginScreenOverlay screenOverlay;

    @Inject
    private ConfigManager configManager;

    public ThreadedRunner runner = new ThreadedRunner();

    @Getter
    @Setter
    private int totalRuns = 0;

    public final int GATE_TILE_OBJECT_ID = 52157;
    public final int LOOT_CHEST_OBJECT_ID = 50741;
    public final int ENTRANCE_OBJECT_ID = 50751;
    public final int MINIMUS_NPC_ID = 12808;
    public static final WorldPoint COLOSSEUM_ENTRANCE_WORLD_POINT = new WorldPoint(1806, 9506, 0);
    @Getter
    private InventoryLoadout.InventoryLoadoutSetup rangeGear = null;
    @Getter
    private InventoryLoadout.InventoryLoadoutSetup mageGear = null;
    public final AtomicReference<Integer> playerDiedOnTick = new AtomicReference<>(-1);

    static final String OUT_OF_SCALES_MESSAGE = "your blowpipe needs to be charged";
    static final String OUT_OF_DARTS_MESSAGE1 = "your blowpipe has run out of darts";
    static final String OUT_OF_DARTS_MESSAGE2 = "your blowpipe contains no darts";

    @Provides
    public ColosseumFarmerPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(ColosseumFarmerPluginConfig.class);
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
        Utility.sendGameMessage("Started", "ColosseumFarmer");
        initialize();
        setTotalRuns(0);
        this.rangeGear = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.rangeGear());
        this.mageGear = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.mageGear());
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

        fightColosseum = new FightColosseum(this, config);
        restockState = new PreAndPostGame(this, config);
        prepareState = new Prepare(this, config);

        states = List.of(
                fightColosseum,
                restockState,
                prepareState
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
            Utility.sendGameMessage("Stopped", "ColosseumFarmer");
        }
        paistiBreakHandler.stopPlugin(this);
        runner.stop();
    }

    public boolean isMinimusPresent() {
        var minimus = NPCs.search().withName("Minimus").first();
        return minimus.isPresent();
    }

    @Subscribe(priority = 5000)
    public void onActorDeath(ActorDeath actorDeath) {
        if (!runner.isRunning()) return;
        Actor actor = actorDeath.getActor();
        if (actor instanceof Player) {
            Player player = (Player) actor;
            if (player == PaistiUtils.getClient().getLocalPlayer()) {
                Utility.sendGameMessage("Player has died!", "ColosseumFarmer");
                if (!config.deathWalking()) stop();
                playerDiedOnTick.set(Utility.getTickCount());
            }
        }
    }

    @Subscribe(priority = 100)
    public void onGameTick(GameTick e) {
        if (!isRunning()) return;

        runner.onGameTick();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;
        if (event.getMessage().toLowerCase().contains(OUT_OF_SCALES_MESSAGE.toLowerCase())) {
            stop();
            return;
        }
        if (event.getMessage().toLowerCase().contains(OUT_OF_DARTS_MESSAGE1.toLowerCase())) {
            stop();
            return;
        }
        if (event.getMessage().toLowerCase().contains(OUT_OF_DARTS_MESSAGE2.toLowerCase())) {
            stop();
            return;
        }
    }

    public Duration getRunTimeDuration() {
        return Duration.between(runner.getStartedAt(), Instant.now());
    }

    public boolean insideColosseum() {
        if (!Utility.isInInstancedRegion()) return false;
        var gate = TileObjects.search().withId(GATE_TILE_OBJECT_ID).first();
        return gate.isPresent();
    }
}