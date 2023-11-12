package com.theplug.PvmHelper.States;

import com.theplug.PaistiUtils.API.NPCs;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.API.Spells.Ancient;
import com.theplug.PaistiUtils.API.TileObjects;
import com.theplug.PaistiUtils.API.Utility;
import com.theplug.PaistiUtils.API.Walking;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.PvmHelper.PvmHelperPlugin;
import com.theplug.PvmHelper.PvmHelperPluginConfig;
import net.runelite.api.Projectile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class WhispererHelperState implements State {

    PvmHelperPlugin plugin;
    PvmHelperPluginConfig config;

    public WhispererHelperState(PvmHelperPlugin plugin, PvmHelperPluginConfig config) {
        super();
        this.plugin = plugin;
        this.config = config;
    }

    private static final int REMAINS_GAME_OBJECT_ID = 48322;
    private final int RANGE_PROJECTILE_ID = 2444;
    private final int MAGIC_PROJECTILE_ID = 2445;
    private final AtomicReference<PPrayer> defensivePrayer = new AtomicReference<>(null);

    private void updateDefensivePrayer() {
        var projectiles = PaistiUtils.getClient().getProjectiles();
        List<Projectile> importantProjectiles = new ArrayList<>();
        for (var projectile : projectiles) {
            if (projectile.getRemainingCycles() < 10) continue;
            int projectileId = projectile.getId();
            if (projectileId == RANGE_PROJECTILE_ID || projectileId == MAGIC_PROJECTILE_ID) {
                importantProjectiles.add(projectile);
            }
        }
        if (importantProjectiles.isEmpty()) {
            /*
            var whisperer = NPCs.search().withName("The Whisperer").first();
            if (whisperer.isPresent()) {
                var distance = whisperer.get().getWorldArea().distanceTo(Walking.getPlayerLocation());
                if (distance == 1) {
                    defensivePrayer.set(PPrayer.PROTECT_FROM_MELEE);
                    return;
                }
            }
             */
            defensivePrayer.set(null);
            return;
        }

        importantProjectiles.sort(Comparator.comparingInt(Projectile::getRemainingCycles));
        var cloestProjectile = importantProjectiles.get(0);
        if (cloestProjectile.getId() == RANGE_PROJECTILE_ID) defensivePrayer.set(PPrayer.PROTECT_FROM_MISSILES);
        if (cloestProjectile.getId() == MAGIC_PROJECTILE_ID) defensivePrayer.set(PPrayer.PROTECT_FROM_MAGIC);
    }

    private boolean handlePrayers(){
        var isWhispererPresent = NPCs.search().withName("The Whisperer").first().isPresent();
        var offensivePrayer = plugin.getBestOffensiveMagePrayer();
        if (!isWhispererPresent) {
            return plugin.handleDisableAllPrayers();
        };
        var toggledOffensivePrayer = false;
        if (offensivePrayer != null && !offensivePrayer.isActive()) {
            offensivePrayer.setEnabled(true);
        }
        if (defensivePrayer.get() == null) {
            if (PPrayer.PROTECT_FROM_MAGIC.isActive()) {
                PPrayer.PROTECT_FROM_MAGIC.setEnabled(false);
                return true;
            }
            if (PPrayer.PROTECT_FROM_MISSILES.isActive()) {
                PPrayer.PROTECT_FROM_MISSILES.setEnabled(false);
                return true;
            }
            if (PPrayer.PROTECT_FROM_MELEE.isActive()) {
                PPrayer.PROTECT_FROM_MELEE.setEnabled(false);
                return true;
            }
            return false;
        }
        if (defensivePrayer.get().equals(PPrayer.PROTECT_FROM_MAGIC) && !PPrayer.PROTECT_FROM_MAGIC.isActive()) {
            PPrayer.PROTECT_FROM_MAGIC.setEnabled(true);
            return true;
        }
        if (defensivePrayer.get().equals(PPrayer.PROTECT_FROM_MISSILES) && !PPrayer.PROTECT_FROM_MISSILES.isActive()) {
            PPrayer.PROTECT_FROM_MISSILES.setEnabled(true);
            return true;
        }
        if (defensivePrayer.get().equals(PPrayer.PROTECT_FROM_MELEE) && !PPrayer.PROTECT_FROM_MELEE.isActive()) {
            PPrayer.PROTECT_FROM_MELEE.setEnabled(true);
            return true;
        }
        return toggledOffensivePrayer;
    }

    private boolean isWhispererPresent() {
        return NPCs.search().withName("The Whisperer").first().isPresent();
    }

    public void castIceBarrageOnWhisperer() {
        Utility.runOnClientThread(() -> {
            var leviathan = NPCs.search().withName("The Whisperer").first();
            leviathan.ifPresent(Ancient.ICE_BARRAGE::castOnActor);
            return null;
        });
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public boolean shouldExecuteState() {
        return TileObjects.search().withId(REMAINS_GAME_OBJECT_ID).first().isPresent() || isWhispererPresent();
    }

    @Override
    public void threadedOnGameTick() {
        updateDefensivePrayer();
        handlePrayers();
    }

    @Override
    public void threadedLoop() {
    }
}
