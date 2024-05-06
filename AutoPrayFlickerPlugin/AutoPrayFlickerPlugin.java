package com.theplug.AutoPrayFlickerPlugin;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.AttackTickTracker.AttackTickTracker;
import com.theplug.PaistiUtils.API.NPCTickSimulation.NPCTickSimulation;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.Collections.query.NPCQuery;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@PluginDescriptor(name = "AutoPrayFlicker", description = "Automatically flicks prayers for you", enabledByDefault = false, tags = {"paisti", "choso", "combat", "prayer"})
public class AutoPrayFlickerPlugin extends Plugin {
    @Inject
    public AutoPrayFlickerPluginConfig config;
    @Inject
    private KeyManager keyManager;
    @Inject
    PluginManager pluginManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    EventBus eventBus;

    @Inject
    AttackTickTracker attackTickTracker;

    @Inject
    private Client client;

    @Inject
    private AutoPrayFlickerPluginScreenOverlay screenOverlay;

    @Inject
    private AutoPrayFlickerSceneOverlay sceneOverlay;

    @Inject
    private ConfigManager configManager;

    public List<AttackTickTracker.INPCAttackTickData> npcData;

    public ThreadedScriptRunner runner = new ThreadedScriptRunner();

    @Provides
    public AutoPrayFlickerPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoPrayFlickerPluginConfig.class);
    }

    public boolean isRunning() {
        return runner.isRunning();
    }

    private final HotkeyListener startHotkeyListener = new HotkeyListener(() -> config.startHotkey() != null ? config.startHotkey() : new Keybind(0, 0)) {
        @Override
        public void hotkeyPressed() {
            PaistiUtils.runOnExecutor(() -> {
                if (runner.isRunning()) {
                    stop();
                } else {
                    start();
                }
                return null;
            });
        }
    };

    public void start() {
        Utility.sendGameMessage("Started", "AutoPrayFlicker");
        try {
            var deserializedNpcData = NPCDeserializer.deserialize(config.npcData());
            npcData = deserializedNpcData;
            attackTickTracker.setCustomNpcData(deserializedNpcData);
            attackTickTracker.initializeNearbyNpcs();
        } catch (Exception e) {
            Utility.sendGameMessage("Failed to parse line: " + e.getMessage(), "AutoPrayFlicker");
        }
        runner.start();
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("AutoPrayFlicker: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }
        keyManager.registerKeyListener(startHotkeyListener);
        overlayManager.add(screenOverlay);
        overlayManager.add(sceneOverlay);

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });

        runner.setOnGameTickAction(() -> {
            this.threadedOnGameTick();
            return null;
        });
    }

    private void threadedLoop() {
        Utility.sleepGaussian(300, 600);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(screenOverlay);
        overlayManager.remove(sceneOverlay);
        keyManager.unregisterKeyListener(startHotkeyListener);
        stop();
    }

    private void simulateAndSetOffensivePrayers() {
        if (!(Utility.getInteractionTarget() instanceof NPC)) return;
        var playerTarget = (NPC) Utility.getInteractionTarget();
        var comp = NPCQuery.getNPCComposition((NPC) playerTarget);
        if (comp == null || comp.getActions() == null || Arrays.stream(comp.getActions()).noneMatch(a -> a != null && a.equalsIgnoreCase("Attack"))) {
            return;
        }
        if (!playerTarget.isDead() && attackTickTracker.getTicksUntilNextAttack() <= 1) {
            PPrayer.enableBestOffensivePrayers(true);
            return;
        }
        PPrayer.disableOffensivePrayers(true);
    }

    private boolean isUnderAttackOrAttacking(NPC npc) {
        var interactingWithNpc = client.getLocalPlayer().getInteracting() != null && client.getLocalPlayer().getInteracting() == npc;
        var npcInteractingWithPlayer = npc.getInteracting() != null && npc.getInteracting() == client.getLocalPlayer();
        return interactingWithNpc || npcInteractingWithPlayer;
    }

    public List<NPC> getRelevantNpcs() {
        if (npcData == null) return new ArrayList<>();
        var relevantNpcs = NPCs.search().alive().withinDistance(17).result().stream().filter(npc -> npcData.stream().anyMatch(n -> n.getNpcId() == npc.getId())).collect(Collectors.toList());
        if (!config.predictDefensive()) {
            relevantNpcs = relevantNpcs.stream().filter(this::isUnderAttackOrAttacking).collect(Collectors.toList());
        }
        return relevantNpcs;
    }

    private NPCTickSimulation.PrayAgainstResult getMostImportantPrayer() {
        return Utility.runOncePerClientTickTask(() -> {
            var client = PaistiUtils.getClient();

            var relevantNpcs = getRelevantNpcs();

            var _tickSimulation = new NPCTickSimulation(client, attackTickTracker, relevantNpcs);
            _tickSimulation.getPlayerState().setInteracting(client.getLocalPlayer().getInteracting());
            List<NPCTickSimulation.PrayAgainstResult> prayThisTick = new ArrayList<>();

            _tickSimulation.simulateNpcsTick(client);
            var prayAgainst = _tickSimulation.shouldPrayAgainst(client);
            if (prayAgainst != null) {
                prayThisTick.add(prayAgainst);
            }

            _tickSimulation.simulatePlayerTick(client);

            prayAgainst = _tickSimulation.shouldPrayAgainst(client);
            if (prayAgainst != null) {
                prayThisTick.add(prayAgainst);
            }

            return prayThisTick.isEmpty() ? null : prayThisTick.stream().max(Comparator.comparingInt(NPCTickSimulation.PrayAgainstResult::getPriority)).get();
        });
    }

    private void simulateAndSetDefensivePrayers() {
        var mostImportantPray = getMostImportantPrayer();
        boolean didPray = false;
        if (mostImportantPray == null) {
            if (PPrayer.PROTECT_FROM_MELEE.isActive() && PPrayer.PROTECT_FROM_MELEE.setEnabledWithoutClicks(false)) {
                log.debug("Disabled melee prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
            } else if (PPrayer.PROTECT_FROM_MISSILES.isActive() && PPrayer.PROTECT_FROM_MISSILES.setEnabledWithoutClicks(false)) {
                log.debug("Disabled ranged prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
            } else if (PPrayer.PROTECT_FROM_MAGIC.isActive() && PPrayer.PROTECT_FROM_MAGIC.setEnabledWithoutClicks(false)) {
                log.debug("Disabled magic prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
            }
        } else {
            switch (mostImportantPray.getAttackType()) {
                case MELEE:
                    didPray = !PPrayer.PROTECT_FROM_MELEE.isActive() && PPrayer.PROTECT_FROM_MELEE.setEnabledWithoutClicks(true);
                    if (didPray) {
                        log.debug("Enabled melee prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
                    }
                    break;
                case MAGIC:
                    didPray = !PPrayer.PROTECT_FROM_MAGIC.isActive() && PPrayer.PROTECT_FROM_MAGIC.setEnabledWithoutClicks(true);
                    if (didPray) {
                        log.debug("Enabled magic prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
                    }
                    break;
                case RANGED:
                    didPray = !PPrayer.PROTECT_FROM_MISSILES.isActive() && PPrayer.PROTECT_FROM_MISSILES.setEnabledWithoutClicks(true);
                    if (didPray) {
                        log.debug("Enabled ranged prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
                    }
                    break;
            }
        }
    }

    private void threadedOnGameTick() {
        if (!runner.isRunning()) return;
        if (config.autoFlickOffensive()) simulateAndSetOffensivePrayers();
        if (config.autoFlickDefensive()) simulateAndSetDefensivePrayers();
    }

    public void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "AutoPrayFlicker");
        }
        attackTickTracker.clearCustomNpcData();
        runner.stop();
    }

    @Subscribe(priority = 100)
    public void onGameTick(GameTick e) {
        if (!isRunning()) return;

        runner.onGameTick();
    }

    public Duration getRunTimeDuration() {
        return Duration.between(runner.getStartedAt(), Instant.now());
    }

    @Subscribe(priority = 4000)
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!runner.isRunning() || !Utility.isLoggedIn()) {
            return;
        }

        if (event.getMenuAction().equals(MenuAction.NPC_SECOND_OPTION) && event.getMenuOption().equalsIgnoreCase("Attack")
                || (event.getMenuAction().equals(MenuAction.WIDGET_TARGET_ON_NPC) && event.getMenuOption().contains("Cast"))) {
            var clickedNpc = NPCs.search().filter(n -> n.getIndex() == event.getId()).first();
            if (clickedNpc.isPresent()) {
                if (config.autoFlickOffensive()
                        && attackTickTracker.getTicksUntilNextAttack() <= 1
                        && Walking.getPlayerLocation().distanceTo(clickedNpc.get().getWorldLocation()) <= attackTickTracker.getPlayerAttackRange() + 2) {
                    PaistiUtils.scheduleOncePerClientTickTask(() -> {
                        PPrayer.enableBestOffensivePrayers(true);
                        return null;
                    });
                }
            }
        }
    }
}