package com.theplug.AutoPestControlPlugin;


import com.theplug.AutoPestControlPlugin.States.MinigameState;
import com.theplug.PaistiUtils.API.Walking;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

@Slf4j
public class AutoPestControlPluginSceneOverlay extends Overlay {
    AutoPestControlPlugin plugin;
    Client client;
    AutoPestControlPluginConfig config;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoPestControlPluginSceneOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoPestControlPlugin plugin, AutoPestControlPluginConfig config) {
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
        if (!plugin.isRunning()) {
            return null;
        }


        var path = MinigameState.PATH_DEBUG.get();
        if (path != null && config.drawDebug()) {
            for (var p : path) {
                drawTile(graphics, p, Color.CYAN, 30, "", new BasicStroke(1));
            }
        }

        /*

        var red = MinigameState.desiredWorldAreaRed.get();
        if (red != null) {
            renderArea(graphics, client, red, Color.RED, 5, "RED");
        }

        var yellow = MinigameState.desiredWorldAreaYellow.get();
        if (yellow != null) {
            renderArea(graphics, client, yellow, Color.YELLOW, 5, "YELLOW");
        }

        var purple = MinigameState.desiredWorldAreaPurple.get();
        if (purple != null) {
            renderArea(graphics, client, purple, Color.RED, 5, "PURPLE");
        }

        var blue = MinigameState.desiredWorldAreaBlue.get();
        if (blue != null) {
            renderArea(graphics, client, blue, Color.BLUE, 5, "BLUE");
        }
         */

        var area = MinigameState.desiredWorldArea.get();
        if (area != null && config.drawDebug()) {
            renderArea(graphics, client, area, Color.CYAN, 5, "DESIRED");
        }

        return null;
    }

    private static void drawTile(Graphics2D graphics, WorldPoint point, Color color, int alpha, String label, Stroke borderStroke) {
        if (point.distanceTo(Walking.getPlayerLocation()) >= 32) {
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
        final LocalPoint centerLp = new LocalPoint(
                lp.getX() + Perspective.LOCAL_TILE_SIZE * (area.getWidth() - 1) / 2,
                lp.getY() + Perspective.LOCAL_TILE_SIZE * (area.getHeight() - 1) / 2);
        Polygon tilePoly = Perspective.getCanvasTileAreaPoly(client, centerLp, area.getWidth(), area.getHeight(), area.getPlane(), 0);
        if (tilePoly == null) return;
        OverlayUtil.renderPolygon(graphics, tilePoly, color, new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha), new BasicStroke(1));
        if (label != null && !label.isEmpty()) {
            var loc = Perspective.getCanvasTextLocation(PaistiUtils.getClient(), graphics, centerLp, label, 5);
            OverlayUtil.renderTextLocation(graphics, loc, label, color);
        }
    }

}