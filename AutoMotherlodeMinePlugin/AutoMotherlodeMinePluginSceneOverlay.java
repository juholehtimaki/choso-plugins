package com.theplug.AutoMotherlodeMinePlugin;

import com.theplug.PaistiUtils.API.Geometry;
import com.theplug.PaistiUtils.API.TileObjects;
import com.theplug.PaistiUtils.API.Walking;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

public class AutoMotherlodeMinePluginSceneOverlay extends Overlay {
    AutoMotherlodeMinePlugin plugin;
    Client client;
    AutoMotherlodeMinePluginConfig config;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoMotherlodeMinePluginSceneOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoMotherlodeMinePlugin plugin, AutoMotherlodeMinePluginConfig config) {
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
        var ores = TileObjects.search().withName("Ore vein").withAction("Mine").result();
        for (var ore : ores) {
            var o = (WallObject) ore;
            drawTile(graphics, ore.getWorldLocation(), Color.CYAN, 50, Integer.toString(o.getOrientationA()), new BasicStroke(1));
            //var areas = LocalPathfinder.ReachabilityMap.getPointsTouchingArea(ore.getWorldLocation().toWorldArea());
            var rMap = LocalPathfinder.getReachabilityMap();
            var interactionPoints = rMap.getInteractionPointsForArea(ore.getWorldLocation().toWorldArea());
            for (var ip : interactionPoints) {
                drawTile(graphics, ip, Color.CYAN, 50, "TP", new BasicStroke(1));
            }
        }
        renderCuboid(graphics, client, plugin.NW_CUBOID, Color.CYAN, 5, "DESIRED");
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

    private static void renderCuboid(Graphics2D graphics, Client client, Geometry.Cuboid cuboid, Color color, int alpha, String label) {
        LocalPoint lp = LocalPoint.fromWorld(client, cuboid.getSouthWestTile());
        if (lp == null) return;
        final LocalPoint centerLp = new LocalPoint(
                lp.getX() + Perspective.LOCAL_TILE_SIZE * (cuboid.getWidth() - 1) / 2,
                lp.getY() + Perspective.LOCAL_TILE_SIZE * (cuboid.getHeight() - 1) / 2);
        Polygon tilePoly = Perspective.getCanvasTileAreaPoly(client, centerLp, cuboid.getWidth(), cuboid.getHeight(), cuboid.getPlane(), 0);
        if (tilePoly == null) return;
        OverlayUtil.renderPolygon(graphics, tilePoly, color, new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha), new BasicStroke(1));
        if (label != null && !label.isEmpty()) {
            var loc = Perspective.getCanvasTextLocation(PaistiUtils.getClient(), graphics, centerLp, label, 5);
            OverlayUtil.renderTextLocation(graphics, loc, label, color);
        }
    }
}