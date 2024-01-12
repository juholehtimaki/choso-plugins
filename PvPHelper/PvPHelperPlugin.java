package com.theplug.PvPHelper;

import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

import java.util.Arrays;

@Slf4j
@PluginDescriptor(name = "PvPHelper", description = "Helps with PvP", enabledByDefault = false, tags = {"choso", "pvp"})
public class PvPHelperPlugin extends Plugin {
    @Inject
    PvPHelperPluginConfig config;
    @Inject
    PluginManager pluginManager;

    final PPrayer itemProtection = PPrayer.PROTECT_ITEM;

    static WorldService worldService = RuneLite.getInjector().getInstance(WorldService.class);

    private int lastTickEatenOn = Utility.getTickCount();

    @Provides
    public PvPHelperPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(PvPHelperPluginConfig.class);
    }

    private void toggleItemProtection(boolean shouldActiveItemProtection) {

        WorldResult worldResult = worldService.getWorlds();
        World currentWorld = worldResult.findWorld(Utility.getWorldId());

        if (currentWorld.getTypes().contains(WorldType.HIGH_RISK)) {
            return;
        }

        if (shouldActiveItemProtection && !itemProtection.isActive()) {
            itemProtection.setEnabled(true);
        }
        if (!shouldActiveItemProtection && itemProtection.isActive()) {
            itemProtection.setEnabled(false);
        }
    }

    private void handleComboEat() {
        if (Utility.getTickCount() - lastTickEatenOn < 3 || Utility.isSpecialAttackEnabled()) return;
        Utility.sendGameMessage("Attempting to combo eat", "PvPHelper");
        PaistiUtils.runOnExecutor(() -> {
            lastTickEatenOn = Utility.getTickCount();
            var givenRegularFoods = config.regularFoods().split("\n");
            var regularFoodToEat = Inventory.search().filter(i -> Arrays.stream(givenRegularFoods).anyMatch(r -> r.equalsIgnoreCase(Widgets.getCleanName(i)))).first();
            if (regularFoodToEat.isPresent()) {
                Interaction.clickWidget(regularFoodToEat.get(), "Eat");
                Utility.sleepGaussian(50, 100);
            }
            if (config.shouldUseSaradominBrew()) {
                var brewToDrink = Inventory.search().matchesWildCardNoCase("Saradomin brew*").first();
                if (brewToDrink.isPresent()) {
                    Interaction.clickWidget(brewToDrink.get(), "Drink");
                    Utility.sleepGaussian(50, 100);
                }
            }
            var givenComboFoods = config.comboFoods().split("\n");
            var comboFoodToEat = Inventory.search().filter(i -> Arrays.stream(givenComboFoods).anyMatch(r -> r.equalsIgnoreCase(Widgets.getCleanName(i)))).first();
            if (comboFoodToEat.isPresent()) {
                Interaction.clickWidget(comboFoodToEat.get(), "Eat");
                Utility.sleepGaussian(50, 100);
            }
            return null;
        });
    }

    @Subscribe
    private void onGameTick(GameTick e) {
        if (config.shouldAutoEat() && Utility.getBoostedSkillLevel(Skill.HITPOINTS) < config.eatingThreshold()) {
            handleComboEat();
        }
        if (config.turnOnItemProtectionInWilderness() && (Utility.getWildernessLevelFrom(Walking.getPlayerLocation()) > 0) || Utility.isPlayerInDangerousPvpArea()) {
            toggleItemProtection(true);
        } else if (config.turnOffItemProtectionInSafeArea() && (Utility.getWildernessLevelFrom(Walking.getPlayerLocation()) == 0 || !Utility.isPlayerInDangerousPvpArea())) {
            toggleItemProtection(false);
        }
    }
}
