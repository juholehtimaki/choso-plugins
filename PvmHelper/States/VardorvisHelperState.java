package com.theplug.PvmHelper.States;

import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.PvmHelper.PvmHelperPlugin;
import com.theplug.PvmHelper.PvmHelperPluginConfig;
import net.runelite.api.Projectile;
import net.runelite.api.widgets.Widget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class VardorvisHelperState implements State {

    PvmHelperPlugin plugin;
    PvmHelperPluginConfig config;

    public VardorvisHelperState(PvmHelperPlugin plugin, PvmHelperPluginConfig config) {
        super();
        this.plugin = plugin;
        this.config = config;
    }

    private final AtomicReference<PPrayer> defensivePrayer = new AtomicReference<>(null);
    private static final int RANGE_PROJECTILE_ID = 2521;
    private static final int MAGE_PROJECTILE_ID = 2520;
    private static final int VARDORVIS_NPC_ID = 12223;
    private static final int PILLAR_GAMEOBJECT_ID = 48428;
    private final AtomicReference<List<Widget>> bloodBlobs = new AtomicReference<>();

    private void updateDefensivePrayer() {
        var vardorvisHead = NPCs.search().withName("Vardorvis' Head").first();
        if (vardorvisHead.isPresent()) {
            List<Projectile> projectilesList = new ArrayList<>();
            var projectiles = PaistiUtils.getClient().getProjectiles();
            for (var projectile : projectiles) {
                if (projectile.getRemainingCycles() > 0) projectilesList.add(projectile);
            }
            projectilesList.sort(Comparator.comparingInt(Projectile::getRemainingCycles));
            if (projectilesList.isEmpty()) {
                defensivePrayer.set(PPrayer.PROTECT_FROM_MELEE);
                return;
            } else {
                var firstProjectile = projectilesList.get(0);
                if (firstProjectile.getId() == RANGE_PROJECTILE_ID) {
                    defensivePrayer.set(PPrayer.PROTECT_FROM_MISSILES);
                    return;
                } else if (firstProjectile.getId() == MAGE_PROJECTILE_ID) {
                    defensivePrayer.set(PPrayer.PROTECT_FROM_MAGIC);
                    return;
                }
            }
            defensivePrayer.set(PPrayer.PROTECT_FROM_MISSILES);
        } else {
            defensivePrayer.set(PPrayer.PROTECT_FROM_MELEE);
        }
    }

    private void updateBloodBlobs() {
        Utility.runOnClientThread(() -> {
            var blobs = Widgets.search().withAction("Destroy").withParentId(54591493).result();
            bloodBlobs.set(blobs);
            return null;
        });
    }

    private boolean handlePrayers() {
        var isVardorvisPresent = NPCs.search().withId(VARDORVIS_NPC_ID).first().isPresent();
        if (!isVardorvisPresent) {
            if (plugin.handleDisableAllPrayers()) {
                return true;
            }
            return false;
        }
        var defPray = defensivePrayer.get();
        if (defPray == null) return false;
        if (!defPray.isActive() || !PPrayer.PIETY.isActive()) {
            defPray.setEnabled(true);
            PPrayer.PIETY.setEnabled(true);
            return true;
        }
        return false;
    }

    private boolean handleBloodBlobs() {
        if (!config.autoSpores()) return false;
        var blobs = bloodBlobs.get();
        if (blobs == null || blobs.isEmpty()) return false;
        for (var blob : blobs) {
            Interaction.clickWidget(blob, "Destroy");
            Utility.sleepGaussian(50, 100);
        }
        return true;
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public boolean shouldExecuteState() {
        return TileObjects.search().withId(PILLAR_GAMEOBJECT_ID).first().isPresent();
    }

    @Override
    public void threadedOnGameTick() {
        updateDefensivePrayer();
        Utility.sleepGaussian(50, 100);
        updateBloodBlobs();
        Utility.sleepGaussian(50, 100);
    }

    @Override
    public void threadedLoop() {
        if (handleBloodBlobs()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (handlePrayers()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        Utility.sleepGaussian(100, 200);
    }
}
