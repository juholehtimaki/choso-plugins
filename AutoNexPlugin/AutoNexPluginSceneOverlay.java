package com.theplug.AutoNexPlugin;


import com.theplug.PaistiUtils.API.Walking;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

public class AutoNexPluginSceneOverlay extends Overlay {
    AutoNexPlugin plugin;
    Client client;
    AutoNexPluginConfig config;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoNexPluginSceneOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoNexPlugin plugin, AutoNexPluginConfig config) {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.isRunning()) return null;
        var dangerousTiles = FightNexState.getDangerousTiles();
        var spikeTiles = FightNexState.getSpikeTiles();
        //var mostOptimalTile = plugin.fightNexState.getOptimalTile();
        for (var tile : dangerousTiles) {
            drawTile(graphics, tile, Color.RED, 50, "", new BasicStroke(1));
        }

        /*
        if (plugin.fightNexState.simulatedPlayerLocationAfterAttack.get() != null) {
            drawTile(graphics, plugin.fightNexState.simulatedPlayerLocationAfterAttack.get(), Color.PINK, 30, "A", new BasicStroke(1));
        }
         */

        //renderPrimaryTarget();
        renderTargets();

        /*
        for (var tile : spikeTiles) {
            drawTile(graphics, tile, Color.BLUE, 50, "", new BasicStroke(1));
        }


        if (mostOptimalTile.isPresent()) {
            drawTile(graphics, mostOptimalTile.get(), Color.GREEN, 50, "", new BasicStroke(1));
        }*/
        return null;
    }

    private static void drawTile(Graphics2D graphics, WorldPoint point, Color color, int alpha, String label, Stroke borderStroke) {
        WorldPoint playerLocation = PaistiUtils.getClient().getLocalPlayer().getWorldLocation();

        if (point.distanceTo(playerLocation) >= 32) {
            return;
        }

        LocalPoint lp = LocalPoint.fromWorld(PaistiUtils.getClient(), point);
        if (lp == null) {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(PaistiUtils.getClient(), lp);
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, color, new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha), borderStroke);
        }
        if (!Strings.isNullOrEmpty(label)) {
            Point canvasTextLocation = Perspective.getCanvasTextLocation(PaistiUtils.getClient(), graphics, lp, label, 0);
            if (canvasTextLocation != null) {
                graphics.setFont(new Font("Arial", 1, 15));
                OverlayUtil.renderTextLocation(graphics, canvasTextLocation, label, color);
            }
        }
    }

    private void renderPrimaryTarget() {
        if (!plugin.fightNexState.shouldExecuteState()) return;
        var target = plugin.fightNexState.getDesiredTarget();
        if (target == null) return;
        if (Walking.getPlayerLocation().distanceTo(target.getWorldArea()) <= plugin.attackTickTracker.getPlayerAttackRange()) {
            modelOutlineRenderer.drawOutline(target, 3, Color.CYAN, 1);
        } else {
            modelOutlineRenderer.drawOutline(target, 3, Color.RED, 1);
        }
    }

    private void renderTargets() {
        if (!plugin.fightNexState.shouldExecuteState() || !plugin.config.drawNpcs()) return;
        var targets = plugin.fightNexState.getNpcs();
        if (targets == null) return;
        for (NPC target : targets) {
            if (Walking.getPlayerLocation().distanceTo(target.getWorldArea()) <= plugin.attackTickTracker.getPlayerAttackRange()) {
                modelOutlineRenderer.drawOutline(target, 2, Color.GREEN, 1);
            } else {
                modelOutlineRenderer.drawOutline(target, 2, Color.RED, 1);
            }
        }
    }
}