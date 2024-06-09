package com.theplug.AutoRoguesDenPlugin;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public class AutoRoguesDenPluginScreenOverlay extends OverlayPanel {
    AutoRoguesDenPlugin plugin;
    Client client;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoRoguesDenPluginScreenOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoRoguesDenPlugin plugin) {
        this.plugin = plugin;
        this.client = client;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(final Graphics2D graphics) {
        if (!plugin.isRunning()) return null;

        var runtimeMs = plugin.getRunTimeDuration().toMillis();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("AutoRoguesDen")
                .color(Color.CYAN)
                .build());

        var runTimeCopy = runtimeMs;
        long hours = TimeUnit.MILLISECONDS.toHours(runTimeCopy);
        runTimeCopy -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(runTimeCopy);
        runTimeCopy -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(runTimeCopy);
        String runTimeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Runtime:")
                .leftColor(Color.CYAN)
                .right(runTimeFormatted)
                .rightColor(Color.CYAN)
                .build());

        return super.render(graphics);
    }
}