package com.theplug.AutoGorillasPlugin;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

public class AutoGorillasSceneOverlay extends Overlay {
    AutoGorillasPlugin plugin;
    Client client;
    AutoGorillasPluginConfig config;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoGorillasSceneOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoGorillasPlugin plugin, AutoGorillasPluginConfig config) {
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
        return null;
    }

    private void renderTarget() {
        if (!plugin.gorillasState.shouldExecuteState()) return;

        var currTarget = plugin.gorillasState.getTarget();
        if (currTarget == null) return;
        modelOutlineRenderer.drawOutline((NPC) currTarget, 2, Color.GREEN, 1);
    }
}