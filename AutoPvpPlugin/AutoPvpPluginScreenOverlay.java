package com.theplug.AutoPvpPlugin;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

public class AutoPvpPluginScreenOverlay extends OverlayPanel {
    AutoPvpPlugin plugin;
    AutoPvpPluginConfig config;
    Client client;
    ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    AutoPvpPluginScreenOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer, AutoPvpPlugin plugin, AutoPvpPluginConfig config) {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(final Graphics2D graphics) {
        if (!plugin.isRunning()) return null;

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("AutoPrayFlicker")
                .color(Color.CYAN)
                .build());

        var lastAttackTick = plugin.attackTickTracker.getLastAttackedOnTick();
        var ticksSinceLastAttack = client.getTickCount() - lastAttackTick;
        var ticksUntilNextAttack = Math.max(0, plugin.attackTickTracker.getPredictedNextPossibleAttackTick() - client.getTickCount());

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

        if (plugin.pvpTarget != null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Target:")
                    .leftColor(Color.CYAN)
                    .right(plugin.pvpTarget.getName())
                    .rightColor(Color.CYAN)
                    .build());
        }

        if (plugin.pvpTarget != null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Next freeze:")
                    .leftColor(Color.CYAN)
                    .right(Integer.toString(plugin.getShouldFreezeAgainOnTick().get()))
                    .rightColor(Color.CYAN)
                    .build());
        }

        return super.render(graphics);
    }
}
