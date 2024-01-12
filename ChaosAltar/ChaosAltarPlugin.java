package com.theplug.ChaosAltar;

import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.API.Spells.Standard;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.WildcardMatcher;

import java.util.*;

@Slf4j
@PluginDescriptor(name = "AutoChaosAltar", description = "Trains prayer at chaos alter", enabledByDefault = false, tags = {"choso", "prayer"})
public class ChaosAltarPlugin extends Plugin {
    @Inject
    ChaosAltarPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    ThreadedScriptRunner runner = new ThreadedScriptRunner();
    InventoryLoadout.InventoryLoadoutSetup loadout;
    static final WorldPoint WINE_LOCATION = new WorldPoint(2950, 3823, 0);
    private static WorldPoint CHAOS_ALTAR_LOCATION = new WorldPoint(2948, 3820, 0);
    private static WorldPoint CHAOS_DRUID_LOCATION = new WorldPoint(2955, 3817, 0);

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
    public ChaosAltarPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(ChaosAltarPluginConfig.class);
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

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });
    }

    @Override
    protected void shutDown() throws Exception {
        keyManager.unregisterKeyListener(startHotkeyListener);
        runner.stop();
    }

    private void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "AutoChaosAltar");
        }
        runner.stop();
    }

    private void start() {
        Utility.sendGameMessage("Started", "AutoChaosAltar");
        loadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.inventoryLoadout());
        runner.start();
    }

    private boolean isWithinAttackRange(int otherPlayerLevel) {
        var wildernessLevel = Utility.getWildernessLevelFrom(Walking.getPlayerLocation());
        var playerLevel = PaistiUtils.getClient().getLocalPlayer().getCombatLevel();

        int maxLevelDifference = playerLevel + wildernessLevel;

        // Calculate the minimum level difference for attack range
        int minLevelDifference = playerLevel - wildernessLevel;

        // Check if the other player's level falls within the range
        return (otherPlayerLevel >= minLevelDifference) && (otherPlayerLevel <= maxLevelDifference);
    }

    private boolean hasGear(Player player) {
        var weaponId = player.getPlayerComposition().getKitId(KitType.WEAPON);
        if (weaponId != -1) {
            Utility.sendGameMessage("should hop");
            return true;
        }
        return false;
    }

    private boolean isInWilderness() {
        var wildernessLevel = Utility.getWildernessLevelFrom(Walking.getPlayerLocation());
        if (wildernessLevel > 1) return true;
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

    private boolean handleSuicide() {
        if (!isPlayerAlive()) return false;
        if (!isInWilderness()) return false;

        var bone = Inventory.search().nameContains("bones").first();
        if (bone.isPresent()) return false;

        var wine = TileItems.search().withName("Wine of zamorak").first();
        if (wine.isEmpty()) return false;

        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        if (!reachabilityMap.isReachable(WINE_LOCATION)) {
            return WebWalker.walkToExact(WINE_LOCATION);
        }

        return Interaction.clickGroundItem(wine.get(), "Take");
    }

    private boolean handleUnnoting() {
        var unnotedBones = Inventory.search().nameContains("bones").onlyUnnoted().first();
        if (unnotedBones.isPresent()) return false;
        var notedBones = Inventory.search().nameContains("bones").onlyNoted().first();
        if (notedBones.isEmpty()) return false;
        var gp = Inventory.search().withId(995).first();
        if (gp.isEmpty() || gp.get().getItemQuantity() < 50) return false;

        var elderChaosDruid = NPCs.search().withName("Elder Chaos druid").first();
        if (elderChaosDruid.isEmpty()) return false;

        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        if (!reachabilityMap.isReachable(elderChaosDruid.get())) {
            WebWalker.walkToExact(CHAOS_DRUID_LOCATION.dy(Utility.random(-1, 1)).dx(Utility.random(-1, 1)));
            Utility.sleepUntilCondition(() -> reachabilityMap.isReachable(elderChaosDruid.get()), 3000, 100);
        }

        if (reachabilityMap.isReachable(elderChaosDruid.get())) {
            Interaction.useItemOnNpc(notedBones.get(), elderChaosDruid.get());
            Utility.sleepUntilCondition(Dialog::isConversationWindowUp, 3000, 100);
            if (Dialog.isConversationWindowUp()) {
                Dialog.handleGenericDialog(new String[]{"All"});
            }
            return Utility.sleepUntilCondition(() -> Inventory.search().nameContains("bones").first().isPresent(), 1200, 100);
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

    private boolean handleDialog() {
        if (!isPlayerAlive()) return false;
        if (!Dialog.isConversationWindowUp()) return false;
        var dialogOptions = new String[]{
                "teleport",
        };
        return Dialog.handleGenericDialog(dialogOptions);
    }

    private boolean handleLoadout() {
        if (loadout.isSatisfied()) return false;
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
        if (client.getGameState() == GameState.LOADING) {
            return;
        }
        if (!Utility.isLoggedIn()) {
            stop();
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
        }
        Utility.sleepGaussian(300, 600);
    }

    @Subscribe
    public void onGameTick(GameTick e) {
        handleHopOnPlayerNearby();
        //handleDialog();
    }
}
