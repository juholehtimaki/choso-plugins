package com.theplug.VardorvisPlugin;


import com.theplug.PaistiUtils.API.NPCs;
import com.theplug.PaistiUtils.API.Utility;
import com.theplug.PaistiUtils.API.Walking;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

public class VardorvisPluginSceneOverlay extends Overlay {
    VardorvisPlugin plugin;
    Client client;
    VardorvisPluginConfig config;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    VardorvisPluginSceneOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, VardorvisPlugin plugin, VardorvisPluginConfig config) {
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
        /*
        var dangerousTiles = plugin.fightVardorvisState.dangerousTiles;
        for (var tile : dangerousTiles) {
            drawTile(graphics, tile, Color.RED, 50, "D", new BasicStroke(1));
        }
         */
        //var opTile = plugin.fightVardorvisState.optimalTile.get();

        //if (opTile != null) drawTile(graphics, opTile, Color.GREEN, 50, "", new BasicStroke(1));

        var axes = NPCs.search().withId(12227).result();
        var client = PaistiUtils.getClient();
        for (var axe : axes) {
            renderArea(graphics, client, axe.getWorldArea(), Color.CYAN, 50, "");
        }
        var vardorvis = plugin.fightVardorvisState.getVardorvis();
        if (vardorvis != null) renderArea(graphics, client, vardorvis.getWorldArea(), Color.YELLOW, 50, "V");

        var predictedtile = plugin.fightVardorvisState.simulatedPlayerLocationAfterAttack.get();
        if (predictedtile != null) drawTile(graphics, predictedtile, Color.RED, 50, "P", new BasicStroke(1));

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