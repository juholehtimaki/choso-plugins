package com.theplug.AutoNexPlugin;

import com.theplug.GearSwitcherPlugin.GearSwitcherScript;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.AttackTickTracker.AttackTickTracker;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@PluginDescriptor(name = "AutoNex", description = "Automates Nex", enabledByDefault = false, tags = {"paisti", "nex"})
public class AutoNexPlugin extends Plugin {
    @Inject
    AutoNexPluginConfig config;
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

    KillCountState killCountState;

    FightNexState fightNexState;

    RestockState restockState;

    static final int NEX_ALTAR = 42965;


    @Inject
    private AutoNexPluginScreenOverlay screenOverlay;

    @Inject
    private AutoNexPluginSceneOverlay sceneOverlay;

    @Inject
    private ConfigManager configManager;

    ThreadedScriptRunner runner = new ThreadedScriptRunner();

    @Getter
    @Setter
    private int totalKillCount = 0;

    @Getter
    @Setter
    private int killsThisTrip = 0;

    @Getter
    @Setter
    private int seenUniqueItems = 0;

    @Getter
    @Setter
    private int mvps = 0;

    @Getter
    @Setter
    private Boolean stopHasBeenRequested = false;

    @Getter
    private final AtomicReference<PPrayer> offensivePray = new AtomicReference<>(null);


    static final String LOG_OUT_MESSAGE = "you will be logged out in approximately 10 minutes";

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
                configManager.setConfiguration("AutoNexPluginConfig", "shouldExecute", !config.shouldExecute());
                return null;
            });
        }
    };

    public void start() {
        Utility.sendGameMessage("Started", "AutoNex");
        setStopHasBeenRequested(false);
        initialize();
        runner.start();
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("AutoNex: PaistiUtils is required for this plugin to work");
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

    public int getAncientKc() {
        return Utility.getVarbitValue(13080);
    }

    @Override
    protected void shutDown() throws Exception {
        stop();
        keyManager.unregisterKeyListener(startHotkeyListener);
        overlayManager.remove(screenOverlay);
        overlayManager.remove(sceneOverlay);
    }

    private void initialize() {
        if (states != null) {
            for (var state : states) {
                eventBus.unregister(state);
            }
        }

        // Statistics
        setTotalKillCount(0);
        setKillsThisTrip(0);
        setMvps(0);
        setSeenUniqueItems(0);

        updateOffensivePray();

        killCountState = new KillCountState(this);
        fightNexState = new FightNexState(this);
        restockState = new RestockState(this, config);

        states = List.of(
                killCountState,
                fightNexState,
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

    @Subscribe
    private void onMenuEntryAdded(MenuEntryAdded event) {
        if (event.getOption().contains("Inventory") && event.getType() == MenuAction.CC_OP.getId()) {
            if (runner.isRunning()) {
                Utility.addMenuEntry(event, ColorUtil.wrapWithColorTag("AutoNex", Color.cyan) + " Request stop", 0, (e) -> {
                    Utility.sendGameMessage("Stop request has been received", "AutoNex");
                    setStopHasBeenRequested(true);
                });
            }
        }
    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        //if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        if (config.killSwitch() && event.getType() == ChatMessageType.PUBLICCHAT && event.getMessage().toLowerCase().contains(config.killSwitchCommand().toLowerCase())) {
            setStopHasBeenRequested(true);
            Utility.sendGameMessage("Kill switch command received, stopping when banking next time", "AutoNex");
        }

        if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().toLowerCase().contains(LOG_OUT_MESSAGE.toLowerCase())) {
            setStopHasBeenRequested(true);
            Utility.sendGameMessage("Log out message received, stopping on next restock", "AutoNex");
        }
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
        if (!e.getGroup().equalsIgnoreCase("AutoNexPluginConfig")) return;
        if (e.getKey().equals("shouldExecute")) {
            if (config.shouldExecute() && !runner.isRunning()) {
                start();
            } else if (!config.shouldExecute() && runner.isRunning()) {
                stop();
            }
        }
    }

    public List<Widget> getFoodItems() {
        return Inventory.search().onlyUnnoted().filter((item) -> foodStats.getHealAmount(item.getItemId()) >= 8).result();
    }

    public boolean isInsideNexRoom() {
        var nexAltar = TileObjects.search().withId(NEX_ALTAR).nearestToPlayer();
        return nexAltar.isPresent() && Utility.isInInstancedRegion();
    }

    public boolean isInsideBankRoom() {
        var bank = NPCs.search().withName("Ashuelot Reis").withAction("Bank").first();
        var reachabilityMap = LocalPathfinder.getReachabilityMap();
        if (bank.isEmpty()) {
            return false;
        }
        return reachabilityMap.isReachable(bank.get());
    }

    public boolean isInsideKcRoom() {
        if (isInsideNexRoom() || isInsideBankRoom()) return false;
        var npcs = NPCs.search().withName(config.selectedNpc().toString()).result();
        if (npcs.isEmpty()) return false;
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        return npcs.stream().anyMatch(reachabilityMap::isReachable);
    }


    public Duration getRunTimeDuration() {
        return Duration.between(runner.getStartedAt(), Instant.now());
    }

    public void updateOffensivePray() {
        if (Utility.getRealSkillLevel(Skill.PRAYER) < 43) {
            Utility.sendGameMessage("Must have 43 prayer to run the plugin", "AutoNex");
            stop();
        }
        List<PPrayer> possiblePrayers = Arrays.asList(PPrayer.RIGOUR, PPrayer.EAGLE_EYE, PPrayer.HAWK_EYE);
        Optional<PPrayer> bestPrayer = possiblePrayers.stream().filter(PPrayer::canUse).findFirst();
        if (bestPrayer.isEmpty()) {
            Utility.sendGameMessage("No offensive prayer available", "AutoNex");
        } else {
            offensivePray.set(bestPrayer.get());
        }
    }
}
