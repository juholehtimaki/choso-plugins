package com.theplug.AutoPvpPlugin;

import com.theplug.PaistiUtils.API.AttackTickTracker.AttackTickTracker;
import com.theplug.PaistiUtils.API.Interaction;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.API.Players;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.API.Spells.Ancient;
import com.theplug.PaistiUtils.API.Spells.Standard;
import com.theplug.PaistiUtils.API.Utility;
import com.theplug.PaistiUtils.API.Walking;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.Hooks.Hooks;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@PluginDescriptor(name = "AutoPvP", description = "Auto PvP for you", enabledByDefault = false, tags = {"choso", "paisti", "combat"})
public class AutoPvpPlugin extends Plugin {

    @Inject
    AutoPvpPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    private final Map<Trigger, PvpScript> triggers = new HashMap<>();
    InventoryLoadout.InventoryLoadoutSetup mageLoadout;
    InventoryLoadout.InventoryLoadoutSetup rangeLoadout;
    InventoryLoadout.InventoryLoadoutSetup meleeLoadout;
    InventoryLoadout.InventoryLoadoutSetup tankLoadout;
    private PvpScript specLoadout;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AutoPvpPluginScreenOverlay screenOverlay;
    @Inject
    private AutoPvpPluginSceneOverlay sceneOverlay;

    @Getter
    static Actor lastTargetInteractedWith;
    @Getter
    static Actor lastTargetInteractedWithMe;
    @Getter
    Actor pvpTarget;
    @Getter
    int pvpTargetId;

    @Inject
    public AttackTickTracker attackTickTracker;

    public ThreadedScriptRunner runner = new ThreadedScriptRunner();

    @Getter
    public AtomicReference<Integer> shouldFreezeAgainOnTick = new AtomicReference<>(-1);

    @Getter
    public AtomicReference<Integer> tbDurationInTicks = new AtomicReference<>(-1);

    public boolean isRunning() {
        return runner.isRunning();
    }

    private void initialize() {
        mageLoadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.mageLoadout());
        rangeLoadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.rangeLoadout());
        meleeLoadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.meleeLoadout());
        tankLoadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.tankLoadout());
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

    public void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "AutoPvP");
        }
        runner.stop();
    }

    public void start() {
        Utility.sendGameMessage("Started", "AutoPvP");
        initialize();
        runner.start();
    }

    private void checkForFrozen() {
        if (pvpTarget == null) return;
        if (pvpTarget.getGraphic() == 179 && shouldFreezeAgainOnTick.get() <= Utility.getTickCount() + 5) {
            shouldFreezeAgainOnTick.set(Utility.getTickCount() + 29);
        }
        if (pvpTarget.getGraphic() == 345) {

        }
    }

    private boolean shouldFreeze() {
        return Utility.getTickCount() >= shouldFreezeAgainOnTick.get();
    }

    private void threadedOnGameTick() {
        checkForFrozen();
        //handleGearSwitch();
    }

    private final HotkeyListener specHotkeyListener = new HotkeyListener(() -> config.specLoadoutHotkey() != null ? config.specLoadoutHotkey() : new Keybind(0, 0)) {
        @Override
        public void hotkeyPressed() {
            specLoadout.execute(50);
        }
    };

    @Provides
    public AutoPvpPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoPvpPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("AutoPvP: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }
        keyManager.registerKeyListener(specHotkeyListener);
        specLoadout = PvpScript.deSerializeFromString(config.specLoadout(), attackTickTracker);

        overlayManager.add(screenOverlay);
        overlayManager.add(sceneOverlay);

        keyManager.registerKeyListener(startHotkeyListener);
        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });
        runner.setOnGameTickAction(() -> {
            this.threadedOnGameTick();
            return null;
        });
    }

    private void threadedLoop() throws Exception {
        if (handleGearSwitch()) {
        }
        Utility.sleepGaussian(50, 100);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(screenOverlay);
        overlayManager.remove(sceneOverlay);
        keyManager.unregisterKeyListener(specHotkeyListener);
        triggers.clear();
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
        if (!e.getGroup().equalsIgnoreCase("AutoPvpPluginConfig")) return;
        if (e.getKey().equals("specLoadout")) {
            specLoadout = PvpScript.deSerializeFromString(config.specLoadout(), attackTickTracker);
        }
    }

    @Subscribe(priority = 5000)
    public void onGameTick(GameTick e) {
        if (!isRunning()) return;

        runner.onGameTick();
    }

    private boolean shouldAttackTarget() {
        if (pvpTarget == null) return false;
        if (pvpTarget.getHealthRatio() == 0) return false;
        if (Walking.getPlayerLocation().distanceTo(pvpTarget.getWorldLocation()) > 10) return false;
        if (Utility.getInteractionTarget() == pvpTarget) return false;
        return true;
    }

    private boolean handleGearSwitch() {
        var currTicks = Utility.getTickCount();
        if (pvpTarget == null || attackTickTracker.getTicksUntilNextAttack() > 1) {
            if (!tankLoadout.isSatisfied(true)) {
                tankLoadout.handleSwitchTurbo();
                if (pvpTarget != null) {
                    Walking.sceneWalk(pvpTarget.getWorldLocation());
                }
                return Utility.sleepUntilCondition(() -> tankLoadout.isSatisfied(true), 1000, 50);
            }
            return false;
        } else if (PPrayer.PIETY.isActive()) {
            if (!meleeLoadout.isSatisfied(true)) {
                meleeLoadout.handleSwitchTurbo();
                Utility.sleepUntilCondition(() -> meleeLoadout.isSatisfied(true), 1000, 50);
            }
            if (!shouldAttackTarget()) return false;
            Interaction.clickPlayer((Player) pvpTarget, "Attack");
            return Utility.sleepUntilCondition(() -> Utility.getTickCount() > currTicks + 2);
        } else if (PPrayer.RIGOUR.isActive()) {
            if (Utility.getInteractionTarget() == pvpTarget) return false;
            if (!rangeLoadout.isSatisfied(true)) {
                rangeLoadout.handleSwitchTurbo();
                Utility.sleepUntilCondition(() -> rangeLoadout.isSatisfied(true), 1000, 50);
            }
            if (!shouldAttackTarget()) return false;
            Interaction.clickPlayer((Player) pvpTarget, "Attack");
            return Utility.sleepUntilCondition(() -> Utility.getTickCount() > currTicks + 2);
        } else if (PPrayer.AUGURY.isActive()) {
            if (!mageLoadout.isSatisfied(true)) {
                mageLoadout.handleSwitchTurbo();
                Utility.sleepUntilCondition(() -> mageLoadout.isSatisfied(true), 1000, 50);
            }
            if (!shouldAttackTarget()) return false;
            if (shouldFreeze()) {
                Interaction.useSpellOnPlayer(Standard.ENTANGLE, (Player) pvpTarget);
            } else {
                Interaction.useSpellOnPlayer(Standard.FIRE_SURGE, (Player) pvpTarget);
            }
            return Utility.sleepUntilCondition(() -> Utility.getTickCount() > currTicks + 2);
        }
        return false;
    }

    @Subscribe
    private void onMenuEntryAdded(MenuEntryAdded event) {
        if (event.getOption().contains("Follow")) {
            Utility.addMenuEntry(event, "Target", 0, (e) -> {
                if (e.getActor() != null) {
                    Utility.sendGameMessage("Actor was not null");
                    //target = e.getActor();
                }
                if (e.getPlayer() != null) {
                    Utility.sendGameMessage("Player was not null");
                    //target = e.getActor();
                }
                var target = Players.search().withId(event.getIdentifier()).first();

                if (target.isPresent()) {
                    pvpTarget = target.get();
                    pvpTargetId = target.get().getId();
                    Utility.sendGameMessage("Target set: " + target.get().getName(), "AutoPvP");
                }
            });
        }
    }
}
