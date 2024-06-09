package com.theplug.AutoBlastFurnacePlugin;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class AutoBlastFurnacePluginScreenOverlay extends OverlayPanel {
    AutoBlastFurnacePlugin plugin;
    Client client;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoBlastFurnacePluginScreenOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoBlastFurnacePlugin plugin) {
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
                .text("AutoBlastFurnace")
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

        for (var pair : BlastFurnaceContents.getAllContents()) {
            if (pair.getRight() == 0) continue;
            BlastFurnaceContents content = Arrays.stream(BlastFurnaceContents.values()).filter(c -> c.getItemID() == pair.getLeft()).findFirst().orElse(null);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(content != null ? content.name() : "Unknown")
                    .leftColor(Color.CYAN)
                    .right(Integer.toString(pair.getRight()))
                    .rightColor(Color.CYAN)
                    .build());
        }

        return super.render(graphics);
    }
}