package com.theplug.AutoPrayFlickerPlugin;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

public class AutoPrayFlickerSceneOverlay extends Overlay {
    AutoPrayFlickerPlugin plugin;
    Client client;
    AutoPrayFlickerPluginConfig config;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoPrayFlickerSceneOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoPrayFlickerPlugin plugin, AutoPrayFlickerPluginConfig config) {
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
        if (config.drawNpcs()) {
            var npcs = plugin.getRelevantNpcs();
            if (npcs == null) ;
            for (var npc : npcs) {
                if (npc == null) continue;
                modelOutlineRenderer.drawOutline(npc, 1, Color.CYAN, 1);
            }
        }
        return null;
    }
}