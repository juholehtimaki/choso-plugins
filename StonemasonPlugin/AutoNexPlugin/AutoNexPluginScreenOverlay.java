package com.PaistiPlugins.AutoNexPlugin;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

public class AutoNexPluginScreenOverlay extends OverlayPanel {
    AutoNexPlugin plugin;
    Client client;
    AutoNexPluginConfig config;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoNexPluginScreenOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoNexPlugin plugin) {
        this.plugin = plugin;
        this.client = client;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(final Graphics2D graphics) {
        if (!plugin.isRunning()) return null;

        var currentState = plugin.states.stream().filter(state -> state.shouldExecuteState()).findFirst();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("AutoNexPlugin")
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .leftColor(Color.CYAN)
                .right(currentState.get().name())
                .rightColor(Color.CYAN)
                .build());

        var currTarget = plugin.fightNexState.getCurrentTarget();
        String targetName = currTarget.isPresent() ? currTarget.get().getName() : "unknown";

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Target:")
                .leftColor(Color.CYAN)
                .right(targetName)
                .rightColor(Color.CYAN)
                .build());

        var currPhase = plugin.fightNexState.currPhase;
        String currPhaseName = currPhase != null ?  currPhase.name() : "unknown";

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Phase:")
                .leftColor(Color.CYAN)
                .right(currPhaseName)
                .rightColor(Color.CYAN)
                .build());

        return super.render(graphics);
    }
}