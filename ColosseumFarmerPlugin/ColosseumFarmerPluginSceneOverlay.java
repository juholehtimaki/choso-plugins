package com.theplug.ColosseumFarmerPlugin;


import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

public class ColosseumFarmerPluginSceneOverlay extends Overlay {
    ColosseumFarmerPlugin plugin;
    Client client;
    ColosseumFarmerPluginConfig config;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    ColosseumFarmerPluginSceneOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, ColosseumFarmerPlugin plugin, ColosseumFarmerPluginConfig config) {
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

        var centerTile = plugin.fightColosseum.getCenterTile().get();
        if (centerTile != null) {
            drawTile(graphics, centerTile, Color.RED, 50, "C", new BasicStroke(1));
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

    private static void renderArea(Graphics2D graphics, Client client, WorldArea area, Color color, int alpha, String label) {
        LocalPoint lp = LocalPoint.fromWorld(client, area.toWorldPoint());
        if (lp == null) return;
        final int size = area.getWidth();
        final LocalPoint centerLp = new LocalPoint(
                lp.getX() + Perspective.LOCAL_TILE_SIZE * (size - 1) / 2,
                lp.getY() + Perspective.LOCAL_TILE_SIZE * (size - 1) / 2);
        Polygon tilePoly = Perspective.getCanvasTileAreaPoly(client, centerLp, size);
        if (tilePoly == null) return;
        OverlayUtil.renderPolygon(graphics, tilePoly, color, new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha), new BasicStroke(1));
        if (label != null && !label.isEmpty()) {
            var loc = Perspective.getCanvasTextLocation(PaistiUtils.getClient(), graphics, centerLp, label, 5);
            OverlayUtil.renderTextLocation(graphics, loc, label, color);
        }
    }

}