package com.theplug.AutoTitheFarmPlugin;

import com.google.inject.Inject;
import com.theplug.PaistiUtils.API.TileObjects;
import com.theplug.PaistiUtils.API.Utility;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public class AutoTitheFarmPluginScreenOverlay extends OverlayPanel {
    AutoTitheFarmPlugin plugin;
    Client client;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoTitheFarmPluginScreenOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoTitheFarmPlugin plugin) {
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
                .text("AutoTitheFarm")
                .color(Color.CYAN)
                .build());

        var runTimeCopy = runtimeMs;
        long hours = TimeUnit.MILLISECONDS.toHours(runTimeCopy);
        runTimeCopy -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(runTimeCopy);
        runTimeCopy -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(runTimeCopy);
        String runTimeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        //String emptyPatches = Integer.toString(TileObjects.search().withId(27383).withinCuboid(plugin.getFarmingArea()).result().size());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Runtime:")
                .leftColor(Color.CYAN)
                .right(runTimeFormatted)
                .rightColor(Color.CYAN)
                .build());

        /*
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Water count:")
                .leftColor(Color.CYAN)
                .right(Integer.toString(plugin.getTotalWaterCount()))
                .rightColor(Color.CYAN)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Empty patches count:")
                .leftColor(Color.CYAN)
                .right(emptyPatches)
                .rightColor(Color.CYAN)
                .build());

         */

        return super.render(graphics);
    }
}