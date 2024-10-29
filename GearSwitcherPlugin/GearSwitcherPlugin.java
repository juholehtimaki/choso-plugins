package com.theplug.GearSwitcherPlugin;

import com.theplug.PaistiUtils.API.AttackTickTracker.AttackTickTracker;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@PluginDescriptor(name = "<HTML><FONT COLOR=#1BB532>PGearSwitcher</FONT></HTML>", description = "Gear switching hotkeys / triggers", enabledByDefault = false, tags = {"paisti", "combat"})
public class GearSwitcherPlugin extends Plugin {

    @Inject
    GearSwitcherPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    private final Map<Trigger, GearSwitcherScript> triggers = new HashMap<>();
    private GearSwitcherScript firstLoadout;
    private GearSwitcherScript secondLoadout;
    private GearSwitcherScript thirdLoadout;
    private GearSwitcherScript fourthLoadout;
    private GearSwitcherScript fifthLoadout;

    @Getter
    static volatile Actor lastTargetInteractedWith;
    @Getter
    static volatile Actor lastTargetInteractedWithMe;

    @Inject
    public AttackTickTracker attackTickTracker;

    private final HotkeyListener firstHotkeyListener = new HotkeyListener(() -> config.firstLoadoutHotkey() != null ? config.firstLoadoutHotkey() : new Keybind(0, 0)) {
        @Override
        public void hotkeyPressed() {
            firstLoadout.execute(config.sleepBetweenActions());
        }
    };

    private final HotkeyListener secondHotkeyListener = new HotkeyListener(() -> config.secondLoadoutHotkey() != null ? config.secondLoadoutHotkey() : new Keybind(0, 0)) {
        @Override
        public void hotkeyPressed() {
            secondLoadout.execute(config.sleepBetweenActions());
        }
    };

    private final HotkeyListener thirdHotkeyListener = new HotkeyListener(() -> config.thirdLoadoutHotkey() != null ? config.thirdLoadoutHotkey() : new Keybind(0, 0)) {
        @Override
        public void hotkeyPressed() {
            thirdLoadout.execute(config.sleepBetweenActions());
        }
    };

    private final HotkeyListener fourthHotkeyListener = new HotkeyListener(() -> config.fourthLoadoutHotkey() != null ? config.fourthLoadoutHotkey() : new Keybind(0, 0)) {
        @Override
        public void hotkeyPressed() {
            fourthLoadout.execute(config.sleepBetweenActions());
        }
    };

    private final HotkeyListener fifthHotkeyListener = new HotkeyListener(() -> config.fifthLoadoutHotkey() != null ? config.fifthLoadoutHotkey() : new Keybind(0, 0)) {
        @Override
        public void hotkeyPressed() {
            fifthLoadout.execute(config.sleepBetweenActions());
        }
    };

    @Provides
    public GearSwitcherPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(GearSwitcherPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        keyManager.registerKeyListener(firstHotkeyListener);
        keyManager.registerKeyListener(secondHotkeyListener);
        keyManager.registerKeyListener(thirdHotkeyListener);
        keyManager.registerKeyListener(fourthHotkeyListener);
        keyManager.registerKeyListener(fifthHotkeyListener);
        firstLoadout = GearSwitcherScript.deSerializeFromString(config.firstLoadout(), attackTickTracker);
        secondLoadout = GearSwitcherScript.deSerializeFromString(config.secondLoadout(), attackTickTracker);
        thirdLoadout = GearSwitcherScript.deSerializeFromString(config.thirdLoadout(), attackTickTracker);
        fourthLoadout = GearSwitcherScript.deSerializeFromString(config.fourthLoadout(), attackTickTracker);
        fifthLoadout = GearSwitcherScript.deSerializeFromString(config.fifthLoadout(), attackTickTracker);
        triggers.put(config.firstLoadoutTrigger(), firstLoadout);
        triggers.put(config.secondLoadoutTrigger(), secondLoadout);
        triggers.put(config.thirdLoadoutTrigger(), thirdLoadout);
        triggers.put(config.fourthLoadoutTrigger(), fourthLoadout);
        triggers.put(config.fifthLoadoutTrigger(), fifthLoadout);
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        for (var keyValue : triggers.entrySet()) {
            if (keyValue.getKey().shouldTriggerOnMenuOptionClicked(event)) {
                keyValue.getValue().execute(config.sleepBetweenActions());
            }
        }
    }

    @Subscribe
    private void onInteractingChanged(InteractingChanged e) {
        if (e.getSource().equals(PaistiUtils.getClient().getLocalPlayer())) {
            if (e.getTarget() != null && ((e.getTarget() instanceof Player) || (e.getTarget() instanceof NPC))) {
                lastTargetInteractedWith = e.getTarget();
            }
        }
        if (e.getTarget() != null && e.getSource() != null && e.getTarget() instanceof Player && e.getTarget().equals(PaistiUtils.getClient().getLocalPlayer())) {
            lastTargetInteractedWithMe = e.getSource();
        }
    }

    @Override
    protected void shutDown() throws Exception {
        keyManager.unregisterKeyListener(firstHotkeyListener);
        keyManager.unregisterKeyListener(secondHotkeyListener);
        keyManager.unregisterKeyListener(thirdHotkeyListener);
        keyManager.unregisterKeyListener(fourthHotkeyListener);
        keyManager.unregisterKeyListener(fifthHotkeyListener);
        triggers.clear();
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
        if (!e.getGroup().equalsIgnoreCase("GearSwitcherPluginConfig")) return;
        if (e.getKey().equals("firstLoadout")) {
            firstLoadout = GearSwitcherScript.deSerializeFromString(config.firstLoadout(), attackTickTracker);
        }
        if (e.getKey().equals("secondLoadout")) {
            secondLoadout = GearSwitcherScript.deSerializeFromString(config.secondLoadout(), attackTickTracker);
        }
        if (e.getKey().equals("thirdLoadout")) {
            thirdLoadout = GearSwitcherScript.deSerializeFromString(config.thirdLoadout(), attackTickTracker);
        }
        if (e.getKey().equals("fourthLoadout")) {
            fourthLoadout = GearSwitcherScript.deSerializeFromString(config.fourthLoadout(), attackTickTracker);
        }
        if (e.getKey().equals("fifthLoadout")) {
            fifthLoadout = GearSwitcherScript.deSerializeFromString(config.fifthLoadout(), attackTickTracker);
        }
        triggers.clear();
        triggers.put(config.firstLoadoutTrigger(), firstLoadout);
        triggers.put(config.secondLoadoutTrigger(), secondLoadout);
        triggers.put(config.thirdLoadoutTrigger(), thirdLoadout);
        triggers.put(config.fourthLoadoutTrigger(), fourthLoadout);
        triggers.put(config.fifthLoadoutTrigger(), fifthLoadout);
    }

    @Subscribe
    private void onGameTick(GameTick e) {
        //System.out.println(Hooks.getSelectedSpellWidget());
    }
}
