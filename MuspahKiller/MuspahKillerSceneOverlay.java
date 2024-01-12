package com.theplug.MuspahKiller;


import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

public class MuspahKillerSceneOverlay extends Overlay {
    MuspahKillerPlugin plugin;
    Client client;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    MuspahKillerSceneOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, MuspahKillerPlugin plugin) {
        this.plugin = plugin;
        this.client = client;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.isRunning()) return null;
        if (plugin.fightMuspahState.shouldExecuteState()) {
            var tile = plugin.fightMuspahState.darknessSafeTile.get();
            if (tile != null) {
                drawTile(graphics, tile, Color.GREEN, 50, "SAFE", new BasicStroke(1));
            }
            /*
            var dangerousTiles = plugin.fightMuspahState.dangerousSpikeTiles;
            for (var dangerousTile : dangerousTiles) {
                drawTile(graphics, dangerousTile, Color.RED, 50, "", new BasicStroke(1));
            }
            var optimalTile = plugin.fightMuspahState.optimalTile.get();
            if (optimalTile != null) {
                drawTile(graphics, optimalTile, Color.BLUE, 50, "O", new BasicStroke(1));
            }
                         */
            var safeTilesDuringAoe = plugin.fightMuspahState.safeTilesDuringAoe;
            for (var safeTile : safeTilesDuringAoe) {
                drawTile(graphics, safeTile, Color.CYAN, 50, "S", new BasicStroke(1));
            }
        }
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
}