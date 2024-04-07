package com.theplug.AutoChaosAltarPlugin;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.kit.KitType;
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


@Slf4j
@PluginDescriptor(name = "AutoChaosAltar", description = "Automates chaos altar", enabledByDefault = false, tags = {"paisti", "choso", "chaos altar", "prayer"})
public class AutoChaosAltarPlugin extends Plugin {
    @Inject
    public AutoChaosAltarPluginConfig config;
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
    private AutoChaosAltarPluginScreenOverlay screenOverlay;

    @Inject
    private ConfigManager configManager;

    public ThreadedScriptRunner runner = new ThreadedScriptRunner();

    InventoryLoadout.InventoryLoadoutSetup loadout;
    static final WorldPoint WINE_LOCATION = new WorldPoint(2950, 3823, 0);
    private static WorldPoint CHAOS_ALTAR_LOCATION = new WorldPoint(2948, 3820, 0);
    private static WorldPoint CHAOS_DRUID_LOCATION = new WorldPoint(2955, 3817, 0);

    @Provides
    public AutoChaosAltarPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoChaosAltarPluginConfig.class);
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
        Utility.sendGameMessage("Started", "AutoChaosAltar");
        initialize();
        paistiBreakHandler.startPlugin(this);
        runner.start();
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("AutoChaosAltar: PaistiUtils is required for this plugin to work");
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

        paistiBreakHandler.registerPlugin(this);
    }

    private boolean isWithinAttackRange(int otherPlayerLevel) {
        var MARGIN = 2;
        var wildernessLevel = Utility.getWildernessLevelFrom(Walking.getPlayerLocation()) + MARGIN;
        var playerLevel = PaistiUtils.getClient().getLocalPlayer().getCombatLevel();

        int maxLevelDifference = playerLevel + wildernessLevel;

        // Calculate the minimum level difference for attack range
        int minLevelDifference = playerLevel - wildernessLevel;

        // Check if the other player's level falls within the range
        return (otherPlayerLevel >= minLevelDifference) && (otherPlayerLevel <= maxLevelDifference);
    }

    private boolean hasGear(Player player) {
        var weaponId = player.getPlayerComposition().getEquipmentId(KitType.WEAPON);
        if (weaponId != -1) {
            return true;
        }
        return false;
    }

    private boolean isInWilderness() {
        var wildernessLevel = Utility.getWildernessLevelFrom(Walking.getPlayerLocation());
        if (wildernessLevel >= 1) return true;
        return false;
    }

    private boolean handleHopOnPlayerNearby() {
        if (!isPlayerAlive()) return false;
        if (!isInWilderness()) return false;
        var bone = Inventory.search().nameContains("bones").first();
        if (bone.isEmpty()) return false;
        boolean shouldHop = Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            var players = PaistiUtils.getClient().getPlayers();
            return players.stream().anyMatch(p ->
                    !p.equals(PaistiUtils.getClient().getLocalPlayer())
                            && p.getWorldLocation().distanceTo(Walking.getPlayerLocation()) <= 12
                            && isWithinAttackRange(p.getCombatLevel())
                            && hasGear(p));
        }));

        if (shouldHop) {
            Utility.sendGameMessage("Player nearby. Hopping worlds.", "AutoChaosAltar");
            return Worldhopping.hopToNext(false);
        }
        return false;
    }

    private boolean isPlayerAlive() {
        return Utility.getBoostedSkillLevel(Skill.HITPOINTS) >= 1;
    }

    private boolean handleBones() {
        if (!isPlayerAlive()) return false;
        var bone = Inventory.search().nameContains("bones").onlyUnnoted().first();
        if (bone.isEmpty()) return false;
        var altar = TileObjects.search().withName("Chaos altar").nearestToPlayer();
        if (altar.isEmpty()) return false;
        var distanceToAltar = altar.get().getWorldLocation().distanceTo(Walking.getPlayerLocation());
        if (distanceToAltar > 1) return false;
        return Interaction.useItemOnTileObject(bone.get(), altar.get());
    }

    private boolean canUnnoteBones() {
        var notedBones = Inventory.search().nameContains("bones").onlyNoted().first().isPresent();
        if (!notedBones) return false;
        var gp = Inventory.search().withId(995).first();
        if (gp.isEmpty() || gp.get().getItemQuantity() < 50) return false;
        return true;
    }

    private boolean shouldUnnoteBones() {
        if (hasUnnotedBones()) return false;
        return canUnnoteBones();
    }

    private boolean hasUnnotedBones() {
        return Inventory.search().nameContains("bones").onlyUnnoted().first().isPresent();
    }

    private boolean shouldSuicide() {
        if (!isPlayerAlive()) return false;
        if (!isInWilderness()) return false;
        if (canUnnoteBones()) return false;
        if (hasUnnotedBones()) return false;
        return true;
    }

    private boolean handleSuicide() {
        if (!shouldSuicide()) return false;

        var wine = TileItems.search().withName("Wine of zamorak").first();
        if (wine.isEmpty()) return false;

        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        if (!reachabilityMap.isReachable(WINE_LOCATION)) {
            return WebWalker.walkToExact(WINE_LOCATION);
        }

        return Interaction.clickGroundItem(wine.get(), "Take");
    }

    private boolean handleUnnoting() {
        if (!shouldUnnoteBones()) return false;

        var elderChaosDruid = NPCs.search().withName("Elder Chaos druid").first();
        if (elderChaosDruid.isEmpty()) return false;

        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        if (!reachabilityMap.isReachable(elderChaosDruid.get())) {
            WebWalker.walkToExact(CHAOS_DRUID_LOCATION.dy(Utility.random(-1, 1)).dx(Utility.random(-1, 1)));
            Utility.sleepUntilCondition(() -> reachabilityMap.isReachable(elderChaosDruid.get()), 3000, 100);
        }

        var notedBones = Inventory.search().nameContains("bones").onlyNoted().first();
        if (notedBones.isEmpty()) return false;

        if (reachabilityMap.isReachable(elderChaosDruid.get())) {
            Interaction.useItemOnNpc(notedBones.get(), elderChaosDruid.get());
            Utility.sleepUntilCondition(Dialog::isConversationWindowUp, 3000, 100);
            if (Dialog.isConversationWindowUp()) {
                Dialog.handleGenericDialog(new String[]{"All"});
            }
            return Utility.sleepUntilCondition(() -> Inventory.search().nameContains("bones").onlyUnnoted().first().isPresent(), 1200, 100);
        }
        return false;
    }

    private boolean handleTravel() {
        var unnotedBones = Inventory.search().nameContains("bones").onlyUnnoted().first();
        if (unnotedBones.isEmpty()) return false;
        var altar = TileObjects.search().withName("Chaos altar").nearestToPlayer();
        if (altar.isPresent() && altar.get().getWorldLocation().distanceTo(Walking.getPlayerLocation()) == 1) {
            return false;
        }
        return WebWalker.walkToExact(CHAOS_ALTAR_LOCATION.dy(Utility.random(0, 1)));
    }

    private boolean handleToggleRun() {
        if (Walking.isRunEnabled() || Walking.getRunEnergy() < 15) return false;
        return Walking.setRun(true);
    }

    private boolean handleLoadout() {
        if (loadout == null || loadout.isSatisfied()) return false;
        if (isInWilderness()) return false;

        if (!Bank.isNearBank()) {
            WebWalker.walkToNearestBank();
            Utility.sleepUntilCondition(Bank::isNearBank, 2000, 100);
        }

        var successfullyWithdrew = loadout.handleWithdraw();
        if (!successfullyWithdrew) {
            stop();
            Utility.sendGameMessage("Failed to withdraw loadout", "AutoChaosAltar");
            return false;
        }
        return true;
    }

    private void threadedLoop() {
        var client = PaistiUtils.getClient();
        if (client.getGameState() == GameState.LOADING && !Utility.isLoggedIn()) {
            return;
        }
        if (handleBones()) {
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleUnnoting()) {
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleTravel()) {
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleToggleRun()) {
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleSuicide()) {
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleLoadout()) {
            Utility.sleepGaussian(300, 600);
            if (!isInWilderness() && paistiBreakHandler.shouldBreak(this)) {
                Utility.sendGameMessage("Taking a break", "AutoChaosAltar");
                paistiBreakHandler.startBreak(this);

                Utility.sleepGaussian(1000, 2000);
                Utility.sleepUntilCondition(() -> !paistiBreakHandler.isBreakActive(this) && Utility.isLoggedIn(), 99999999, 5000);
            }
            return;
        }

        Utility.sleepGaussian(300, 600);
    }

    @Override
    protected void shutDown() throws Exception {
        paistiBreakHandler.unregisterPlugin(this);
        overlayManager.remove(screenOverlay);
        keyManager.unregisterKeyListener(startHotkeyListener);
        stop();
    }

    private void initialize() {
        loadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.inventoryLoadout());
    }

    private void threadedOnGameTick() {
        if (handleHopOnPlayerNearby()) {
            Utility.sendGameMessage("Hopping", "AutoChaosAltar");
            Utility.sleepGaussian(1200, 1800);
            Utility.sleepUntilCondition(Utility::isLoggedIn, 10000, 1200);
        }
        Utility.sleepGaussian(200, 300);
    }


    public void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "AutoChaosAltar");
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