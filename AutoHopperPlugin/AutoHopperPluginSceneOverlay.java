package com.theplug.AutoHopperPlugin;


import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

public class AutoHopperPluginSceneOverlay extends Overlay {
    AutoHopperPlugin plugin;
    Client client;
    AutoHopperPluginConfig config;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoHopperPluginSceneOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoHopperPlugin plugin, AutoHopperPluginConfig config) {
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
        if (config.drawMatchingPlayers()) {
            renderPlayersMatchingConditions();
        }
        return null;
    }

    private void renderPlayersMatchingConditions() {
        var players = plugin.getMatchingPlayers();
        if (players == null) return;
        for (Player player : players) {
            if (player == null) continue;
            modelOutlineRenderer.drawOutline(player, 1, Color.CYAN, 1);
        }
    }

}