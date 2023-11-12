package com.theplug.AutoNexPlugin;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public class AutoNexPluginScreenOverlay extends OverlayPanel {
    AutoNexPlugin plugin;
    Client client;
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

        var currentState = plugin.states.stream().filter(State::shouldExecuteState).findFirst();
        var formatedState = currentState.isPresent() ? currentState.get().name() : "NO STATE";

        var runtimeMs = plugin.getRunTimeDuration().toMillis();
        var killCount = plugin.getTotalKillCount();
        double killsPerHour = (((double) killCount) / (((double) runtimeMs) / (1000 * 60 * 60)));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("AutoNexPlugin")
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

        // Format kills to 1 decimal place
        String killsAndKillsPerHourFormatted = String.format("%d (%.1f/h)", killCount, killsPerHour);
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Kills:")
                .leftColor(Color.CYAN)
                .right(killsAndKillsPerHourFormatted)
                .rightColor(Color.CYAN)
                .build());

        String killsThisTrip = Integer.toString(plugin.getKillsThisTrip());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Kills this trip:")
                .leftColor(Color.CYAN)
                .right(killsThisTrip)
                .rightColor(Color.CYAN)
                .build());
        
        String seenUniques = Integer.toString(plugin.getSeenUniqueItems());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Seen uniques:")
                .leftColor(Color.CYAN)
                .right(seenUniques)
                .rightColor(Color.CYAN)
                .build());

        String mvps = Integer.toString(plugin.getMvps());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("MVPs:")
                .leftColor(Color.CYAN)
                .right(mvps)
                .rightColor(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .leftColor(Color.CYAN)
                .right(formatedState)
                .rightColor(Color.CYAN)
                .build());

        var currTarget = plugin.fightNexState.getDesiredTarget();
        String targetName = currTarget != null ? currTarget.getName() : "UNKNOWN";

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Target:")
                .leftColor(Color.CYAN)
                .right(targetName)
                .rightColor(Color.CYAN)
                .build());

        var currPhase = plugin.fightNexState.currPhase.get();
        String currPhaseName = currPhase != null ? currPhase.name() : "UNKNOWN";

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Phase:")
                .leftColor(Color.CYAN)
                .right(currPhaseName)
                .rightColor(Color.CYAN)
                .build());

        var distanceToNex = plugin.fightNexState.getNexDistance();
        String currDistanceToNex = Integer.toString(distanceToNex);

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Distance:")
                .leftColor(Color.CYAN)
                .right(currDistanceToNex)
                .rightColor(Color.CYAN)
                .build());

        String minimumDesiredDistance = Integer.toString(plugin.fightNexState.getMINIMUM_DISTANCE_TO_NEX().get());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Min distance:")
                .leftColor(Color.CYAN)
                .right(minimumDesiredDistance)
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

        return super.render(graphics);
    }
}