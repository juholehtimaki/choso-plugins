package com.theplug.AutoTitheFarmPlugin;

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

public class AutoTitheFarmPluginSceneOverlay extends Overlay {
    AutoTitheFarmPlugin plugin;
    Client client;
    AutoTitheFarmPluginConfig config;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoTitheFarmPluginSceneOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoTitheFarmPlugin plugin, AutoTitheFarmPluginConfig config) {
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
        for (var plant : plugin.plants) {
            var lastWateredOn = plant.getTimeFromLastInteraction();
            var debugText = "state: " + plant.getState() + ", plant time: " + plant.getPlantTimeDiff() + "interacted on: " + lastWateredOn;
            renderArea(graphics, client, plant.getGameObject().getWorldLocation().toWorldArea(), Color.RED, 50, debugText);
        }
        return null;
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