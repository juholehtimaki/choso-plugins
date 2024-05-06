package com.theplug.AutoHopperPlugin;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.PaistiUtils.API.Utility;
import com.theplug.PaistiUtils.API.Walking;
import com.theplug.PaistiUtils.API.Worldhopping;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.kit.KitType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(name = "AutoHopper", description = "Hops to a different world when near other players and conditions match", enabledByDefault = false, tags = {"paisti", "choso"})
public class AutoHopperPlugin extends Plugin {
    @Inject
    AutoHopperPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private AutoHopperPluginSceneOverlay sceneOverlay;
    @Inject
    OverlayManager overlayManager;

    @Provides
    public AutoHopperPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoHopperPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("AutoHopper: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }
        overlayManager.add(sceneOverlay);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(sceneOverlay);
    }

    private boolean isWithinAttackRange(int otherPlayerLevel) {
        if (config.hopOnlyIfPlayerCanAttack() && !Utility.isPlayerInDangerousPvpArea()) return false;
        if (!config.hopOnlyIfPlayerCanAttack()) return true;
        var MARGIN = 2;
        var wildernessLevel = Utility.getWildernessLevelFrom(Walking.getPlayerLocation()) + MARGIN;
        var playerLevel = PaistiUtils.getClient().getLocalPlayer().getCombatLevel();

        int maxLevelDifference = playerLevel + wildernessLevel;

        // Calculate the minimum level difference for attack range
        int minLevelDifference = playerLevel - wildernessLevel;

        // Check if the other player's level falls within the range
        return (otherPlayerLevel >= minLevelDifference) && (otherPlayerLevel <= maxLevelDifference);
    }

    private boolean hasWeapon(Player player) {
        if (!config.hopOnlyIfPlayerHasWeapon()) return true;
        var weaponId = player.getPlayerComposition().getEquipmentId(KitType.WEAPON);
        if (weaponId != -1) {
            return true;
        }
        return false;
    }

    public List<Player> getMatchingPlayers() {
        var players = PaistiUtils.getClient().getPlayers();
        return players.stream().filter(p ->
                !p.equals(PaistiUtils.getClient().getLocalPlayer())
                        && p.getWorldLocation().distanceTo(Walking.getPlayerLocation()) <= 12
                        && isWithinAttackRange(p.getCombatLevel())
                        && p.getWorldLocation().distanceTo(Walking.getPlayerLocation()) <= config.playerRadius()
                        && hasWeapon(p)).collect(Collectors.toList());
    }

    @Subscribe(priority = 10000)
    public void onGameTick(GameTick e) {
        if (config.isHoppingDisabled()) return;
        if (PaistiUtils.getClient().getGameState() == GameState.HOPPING) return;
        var players = getMatchingPlayers();
        if (players.size() > 0) {
            Worldhopping.tryHopToNext(false);
        }
    }
}
