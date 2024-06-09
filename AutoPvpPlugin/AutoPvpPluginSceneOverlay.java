package com.theplug.AutoPvpPlugin;

import com.google.inject.Inject;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

public class AutoPvpPluginSceneOverlay extends Overlay {
    AutoPvpPlugin plugin;
    Client client;
    AutoPvpPluginConfig config;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoPvpPluginSceneOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoPvpPlugin plugin, AutoPvpPluginConfig config) {
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
        if (plugin.pvpTarget != null) {
            /*
            var lp = plugin.pvpTarget.getLocalLocation();
            graphics.setFont(new Font("Arial", 1, 30));
            var freeze = Perspective.getCanvasTextLocation(PaistiUtils.getClient(), graphics, lp, Integer.toString(plugin.getShouldFreezeAgainOnTick().get()), 5);
            OverlayUtil.renderTextLocation(graphics, freeze, Integer.toString(plugin.getShouldFreezeAgainOnTick().get()), Color.BLUE);
            var tb = Perspective.getCanvasTextLocation(PaistiUtils.getClient(), graphics, lp, "tb", -60);
            OverlayUtil.renderTextLocation(graphics, tb, "tb", Color.GREEN);

             */
        }
        return null;
    }
}