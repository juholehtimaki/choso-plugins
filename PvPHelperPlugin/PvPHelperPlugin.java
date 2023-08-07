package com.example.PvPHelperPlugin;

import com.example.PaistiUtils.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
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
@PluginDescriptor(name = "PvPHelper", description = "PvP Helper", enabledByDefault = false, tags = {"PvP", "Choso"})
public class PvPHelperPlugin extends Plugin {

    @Inject
    PvPHelperPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    private final Map<Trigger, PvpHelperScript> triggers = new HashMap<>();
    private PvpHelperScript firstLoadout;
    private PvpHelperScript secondLoadout;
    private PvpHelperScript thirdLoadout;

    @Getter
    static Actor lastTargetInteractedWith;
    @Getter
    static Actor lastTargetInteractedWithMe;


    private final HotkeyListener firstHotkeyListener = new HotkeyListener(() -> config.firstLoadoutHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            firstLoadout.execute();
        }
    };

    private final HotkeyListener secondHotkeyListener = new HotkeyListener(() -> config.secondLoadoutHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            secondLoadout.execute();
        }
    };

    private final HotkeyListener thirdHotkeyListener = new HotkeyListener(() -> config.thirdLoadoutHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            thirdLoadout.execute();
        }
    };

    @Provides
    public PvPHelperPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(PvPHelperPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("PvPHelper: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }
        keyManager.registerKeyListener(firstHotkeyListener);
        keyManager.registerKeyListener(secondHotkeyListener);
        keyManager.registerKeyListener(thirdHotkeyListener);
        firstLoadout = PvpHelperScript.deSerializeFromString(config.firstLoadout());
        secondLoadout = PvpHelperScript.deSerializeFromString(config.secondLoadout());
        thirdLoadout = PvpHelperScript.deSerializeFromString(config.thirdLoadout());
        triggers.put(config.firstLoadoutTrigger(), firstLoadout);
        triggers.put(config.secondLoadoutTrigger(), secondLoadout);
        triggers.put(config.thirdLoadoutTrigger(), thirdLoadout);
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        for (var keyValue : triggers.entrySet()) {
            if (keyValue.getKey().shouldTriggerOnMenuOptionClicked(event)) {
                keyValue.getValue().execute();
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
        triggers.clear();
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
        if (e.getGroup().equals("PvPHelperPluginConfig") && e.getKey().equals("firstLoadout")) {
            firstLoadout = PvpHelperScript.deSerializeFromString(config.firstLoadout());
        }
        if (e.getGroup().equals("PvPHelperPluginConfig") && e.getKey().equals("secondLoadout")) {
            secondLoadout = PvpHelperScript.deSerializeFromString(config.secondLoadout());
        }
        if (e.getGroup().equals("PvPHelperPluginConfig") && e.getKey().equals("thirdLoadout")) {
            thirdLoadout = PvpHelperScript.deSerializeFromString(config.thirdLoadout());
        }
        triggers.clear();
        triggers.put(config.firstLoadoutTrigger(), firstLoadout);
        triggers.put(config.secondLoadoutTrigger(), secondLoadout);
        triggers.put(config.thirdLoadoutTrigger(), thirdLoadout);
    }
}
