package com.theplug.PvmHelper.States;

import com.theplug.PaistiUtils.API.NPCs;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.API.Spells.Ancient;
import com.theplug.PaistiUtils.API.TileObjects;
import com.theplug.PaistiUtils.API.Utility;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.PvmHelper.PvmHelperPlugin;
import com.theplug.PvmHelper.PvmHelperPluginConfig;
import net.runelite.api.Projectile;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LeviathanHelperState implements State {

    PvmHelperPlugin plugin;
    PvmHelperPluginConfig config;

    public LeviathanHelperState(PvmHelperPlugin plugin, PvmHelperPluginConfig config) {
        super();
        this.plugin = plugin;
        this.config = config;
    }

    private static final int REMAINS_GAME_OBJECT_ID = 49349;
    private final int RANGE_PROJECTILE_ID = 2487;
    private final int MAGIC_PROJECTILE_ID = 2489;
    private final int MELEE_PROJECTILE_ID = 2488;

    private PPrayer getDefensivePrayer() {
        var projectiles = PaistiUtils.getClient().getProjectiles();
        List<Projectile> importantProjectiles = new ArrayList<>();
        for (var projectile : projectiles) {
            if (projectile.getRemainingCycles() < 25) continue;
            int projectileId = projectile.getId();
            if (projectileId == RANGE_PROJECTILE_ID || projectileId == MAGIC_PROJECTILE_ID || projectileId == MELEE_PROJECTILE_ID) {
                importantProjectiles.add(projectile);
            }
        }
        if (importantProjectiles.isEmpty()) {
            return null;
        }

        importantProjectiles.sort(Comparator.comparingInt(Projectile::getRemainingCycles));

        var cloestProjectile = importantProjectiles.get(0);
        if (cloestProjectile.getId() == RANGE_PROJECTILE_ID) return PPrayer.PROTECT_FROM_MISSILES;
        if (cloestProjectile.getId() == MAGIC_PROJECTILE_ID) return PPrayer.PROTECT_FROM_MAGIC;
        return PPrayer.PROTECT_FROM_MELEE;
    }

    private boolean handlePrayers(){
        var isLeviathanPresent = NPCs.search().withName("The Leviathan").first().isPresent();
        var offensivePrayer = plugin.getBestOffensiveRangedPrayer();
        if (!isLeviathanPresent) {
            return plugin.handleDisableAllPrayers();
        };
        var toggledOffensivePrayer = false;
        if (offensivePrayer != null && !offensivePrayer.isActive()) {
            offensivePrayer.setEnabled(true);
        }

        var defPray = getDefensivePrayer();
        var isDefPrayActive = PPrayer.PROTECT_FROM_MELEE.isActive() || PPrayer.PROTECT_FROM_MISSILES.isActive() || PPrayer.PROTECT_FROM_MAGIC.isActive();

        if (defPray == null) {
            if (isDefPrayActive) {
                PPrayer.PROTECT_FROM_MAGIC.setEnabled(false);
                PPrayer.PROTECT_FROM_MISSILES.setEnabled(false);
                PPrayer.PROTECT_FROM_MELEE.setEnabled(false);
                return true;
            }
            return toggledOffensivePrayer;
        }

        if (defPray.equals(PPrayer.PROTECT_FROM_MAGIC) && !PPrayer.PROTECT_FROM_MAGIC.isActive()) {
            PPrayer.PROTECT_FROM_MAGIC.setEnabled(true);
            return true;
        }
        if (defPray.equals(PPrayer.PROTECT_FROM_MISSILES) && !PPrayer.PROTECT_FROM_MISSILES.isActive()) {
            PPrayer.PROTECT_FROM_MISSILES.setEnabled(true);
            return true;
        }
        if (defPray.equals(PPrayer.PROTECT_FROM_MELEE) && !PPrayer.PROTECT_FROM_MELEE.isActive()) {
            PPrayer.PROTECT_FROM_MELEE.setEnabled(true);
            return true;
        }
        return toggledOffensivePrayer;
    }

    public void castShadowBarrageOnLeviathan() {
        Utility.runOnClientThread(() -> {
            var leviathan = NPCs.search().withName("The Leviathan").first();
            leviathan.ifPresent(Ancient.SHADOW_BARRAGE::castOnActor);
            return null;
        });
    }


    @Override
    public String name() {
        return null;
    }

    @Override
    public boolean shouldExecuteState() {
        return TileObjects.search().withId(REMAINS_GAME_OBJECT_ID).first().isPresent();
    }

    @Override
    public void threadedOnGameTick() {

    }

    @Override
    public void threadedLoop() {

    }

    @Subscribe(priority = 1000)
    private void onGameTick(GameTick e) {
        if (shouldExecuteState()) {
            handlePrayers();
        }
    }
}
