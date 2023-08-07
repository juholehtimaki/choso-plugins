package com.example.PWoodcutter;


import com.example.PaistiUtils.PaistiUtils;
import com.example.VorkathKillerPlugin.States.FightVorkathState;
import com.example.VorkathKillerPlugin.VorkathKillerPlugin;
import com.example.VorkathKillerPlugin.VorkathKillerPluginConfig;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

public class WoodcutterPluginSceneOverlay extends Overlay {
    WoodcutterPlugin plugin;
    Client client;
    WoodcutterPluginConfig config;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    WoodcutterPluginSceneOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, WoodcutterPlugin plugin, WoodcutterPluginConfig config) {
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
        if (!plugin.runner.isRunning()) return null;

        if (plugin.firemakingLanes != null && config.burnLogsInsteadOfDrop()) {
            for (var lane : plugin.firemakingLanes) {
                for (var tile : lane) {
                    drawTile(graphics, tile, Color.YELLOW, 50, "", new BasicStroke(1));
                }
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