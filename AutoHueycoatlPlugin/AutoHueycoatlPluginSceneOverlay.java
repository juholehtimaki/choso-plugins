package com.theplug.AutoHueycoatlPlugin;


import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

public class AutoHueycoatlPluginSceneOverlay extends Overlay {
    AutoHueycoatlPlugin plugin;
    Client client;
    AutoHueycoatlPluginConfig config;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoHueycoatlPluginSceneOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoHueycoatlPlugin plugin, AutoHueycoatlPluginConfig config) {
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
        return null;
    }

}