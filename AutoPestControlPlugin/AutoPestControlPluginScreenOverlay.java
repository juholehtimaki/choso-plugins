package com.theplug.AutoPestControlPlugin;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;
import com.theplug.AutoPestControlPlugin.States.State;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public class AutoPestControlPluginScreenOverlay extends OverlayPanel {
    AutoPestControlPlugin plugin;
    Client client;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoPestControlPluginScreenOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoPestControlPlugin plugin) {
        this.plugin = plugin;
        this.client = client;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(final Graphics2D graphics) {
        if (!plugin.isRunning()) return null;

        var currentState = plugin.states.stream().filter(State::shouldExecuteState).findFirst();
        var formatedState = currentState.isPresent() ? currentState.get().name() : "NO STATE";
        var runtimeMs = plugin.getRunTimeDuration().toMillis();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("AutoPestControl")
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

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .leftColor(Color.CYAN)
                .right(formatedState)
                .rightColor(Color.CYAN)
                .build());

        var lastAttackTick = plugin.attackTickTracker.getLastAttackedOnTick();
        var ticksSinceLastAttack = client.getTickCount() - lastAttackTick;
        var ticksUntilNextAttack = Math.max(0, plugin.attackTickTracker.getPredictedNextPossibleAttackTick() - client.getTickCount());

        // Format kills to 1 decimal place
        String ticksSinceLastAttackFormatted = String.format("%d (%d)", ticksSinceLastAttack, ticksUntilNextAttack);
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Attack tick:")
                .leftColor(Color.CYAN)
                .right(ticksSinceLastAttackFormatted)
                .rightColor(Color.CYAN)
                .build());

        var weaponRange = Integer.toString(plugin.attackTickTracker.getPlayerAttackRange());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Weapon range:")
                .leftColor(Color.CYAN)
                .right(weaponRange)
                .rightColor(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Ticks until attack:")
                .leftColor(Color.CYAN)
                .right(Integer.toString(ticksUntilNextAttack))
                .rightColor(Color.CYAN)
                .build());

        return super.render(graphics);
    }
}