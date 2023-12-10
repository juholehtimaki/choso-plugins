package com.theplug.PFisher;

import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@PluginDescriptor(name = "PFisher", description = "Power levels fishing", enabledByDefault = false, tags = {"paisti", "fishing"})
public class PFisherPlugin extends Plugin {

    @Inject
    PFisherPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    ThreadedScriptRunner runner = new ThreadedScriptRunner();
    private HotkeyListener startHotkeyListener = null;
    WorldPoint startingLocation;

    @Inject
    PaistiBreakHandler paistiBreakHandler;

    public static final int ROD_FISHING_ANIMATION = 622;

    private static AtomicReference<Integer> fishingAnimatedTick = new AtomicReference<>(-1);

    @Provides
    public PFisherPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(PFisherPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("PFisher: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });

        startHotkeyListener = config.startHotkey() != null ? new HotkeyListener(() -> config.startHotkey()) {
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
        } : null;
        if (startHotkeyListener != null) {
            keyManager.registerKeyListener(startHotkeyListener);
        }

        paistiBreakHandler.registerPlugin(this);
    }

    @Override
    protected void shutDown() throws Exception {
        paistiBreakHandler.unregisterPlugin(this);
        runner.stop();
    }

    private void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "PFisher");
        }
        paistiBreakHandler.stopPlugin(this);
        runner.stop();
    }

    private boolean playerHasRequiredFishingItems() {
        if (config.fishingMethod() == FishingMethod.LURE) {
            var feather = Inventory.search().withName("Feather").first();
            var rod = Inventory.search().withName("Fly fishing rod").first();
            if (feather.isEmpty() || rod.isEmpty()) {
                stop();
                Utility.sendGameMessage("Stopping because no feathers or fly fishing rod", "PFisher");
                return false;
            }
        }
        if (config.fishingMethod() == FishingMethod.BAIT) {
            var feather = Inventory.search().withName("Fishing bait").first();
            var rod = Inventory.search().withName("Fishing rod").first();
            if (feather.isEmpty() || rod.isEmpty()) {
                stop();
                Utility.sendGameMessage("Stopping because no baits or fishing rod", "PFisher");
                return false;
            }
        }
        if (config.fishingMethod() == FishingMethod.SMALL_NET) {
            var net = Inventory.search().withName("Small fishing net").first();
            if (net.isEmpty()) {
                stop();
                Utility.sendGameMessage("Stopping because no small fishing net", "PFisher");
                return false;
            }
        }
        if (config.fishingMethod() == FishingMethod.USE_ROD) {
            var net = Inventory.search().withName("Barbarian rod").first();
            if (net.isEmpty()) {
                stop();
                Utility.sendGameMessage("Stopping because barbarian rod", "PFisher");
                return false;
            }
        }
        return true;
    }

    public boolean isValidMethodForThreeTickFishing() {
        if (config.fishingMethod().equals(FishingMethod.SMALL_NET)) return false;
        return true;
    }

    private boolean handleThreeTickFishing() {
        if (!config.threeTickFish()) return false;
        if (!isValidMethodForThreeTickFishing()) {
            stop();
            Utility.sendGameMessage("Three tick fishing is not available for this method", "PFisher");
            return false;
        }
        if (Inventory.isFull()) return false;
        if (!playerHasRequiredFishingItems()) return false;

        if (Walking.getPlayerLocation().distanceTo(startingLocation) > config.fishingPoolRadius() + 10) {
            WebWalker.walkToExact(startingLocation);
        }

        var fishingPool = NPCs.search().withName(config.fishingPool().toString()).withAction(config.fishingMethod().toString()).nearestToPlayer();
        if (fishingPool.isEmpty() || startingLocation.distanceTo(fishingPool.get().getWorldLocation()) > config.fishingPoolRadius()) {
            return false;
        }

        var interactionTarget = Utility.getInteractionTarget();

        if (interactionTarget != null && interactionTarget.equals(fishingPool.get())) {
            return false;
        }

        var guamLeaf = Inventory.search().withName("Guam leaf").first();
        var tar = Inventory.search().withName("Swamp tar").first();
        var pestleAndMortar = Inventory.search().withName("Pestle and mortar").first();

        if (tar.isEmpty() || guamLeaf.isEmpty() || pestleAndMortar.isEmpty()) {
            Utility.sendGameMessage("Stopping. Must have guam leaf, grimy guam leaf, swamp tar and pestle and mortar in inventory", "PFisher");
            stop();
        }

        fishingAnimatedTick.set(-1);

        Interaction.clickNpc(fishingPool.get(), config.fishingMethod().toString());


        Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(fishingPool.get().getWorldLocation()) <= 1);
        var animationStarted = Utility.sleepUntilCondition(() -> fishingAnimatedTick.get() != -1 && Utility.getTickCount() >= fishingAnimatedTick.get() + 2, 2400, 100);

        Utility.sleepGaussian(100, 250);
        if (animationStarted) {
            Interaction.useItemOnItem(tar.get(), guamLeaf.get());
            Utility.sleepGaussian(100, 250);
        }
        handleThreeTickDropping();
        return true;
    }

    public boolean handleThreeTickDropping() {
        var fish = Inventory.search().nameContains("Raw").first();
        if (fish.isEmpty()) return false;
        return Interaction.clickWidget(fish.get(), "Drop");
    }

    private boolean handleDropping() {
        if (!Inventory.isFull()) return false;
        var fish = Inventory.search().nameContains("Raw").result();
        var leapingFish = Inventory.search().nameContains("Leaping").result();
        fish.addAll(leapingFish);
        boolean fishDropped = false;
        for (var singleFish : fish) {
            Interaction.clickWidget(singleFish, "Drop");
            Utility.sleepGaussian(200, 400);
            fishDropped = true;
        }
        return fishDropped;
    }

    private void useHarpoonSpecial() {
        if (Utility.getSpecialAttackEnergy() < 100) return;
        if (Equipment.search().withName("Dragon harpoon").result().isEmpty())
            return;
        Utility.specialAttack();
        Utility.sleepGaussian(500, 800);
    }

    private boolean handleFishing() {
        if (config.threeTickFish()) return false;
        if (!Utility.isIdle() || Inventory.isFull()) {
            return false;
        }
        if (!playerHasRequiredFishingItems()) return false;

        if (Walking.getPlayerLocation().distanceTo(startingLocation) > config.fishingPoolRadius() + 10) {
            WebWalker.walkToExact(startingLocation);
        }
        var fishingPool = NPCs.search().withName(config.fishingPool().toString()).withAction(config.fishingMethod().toString()).nearestToPlayerTrueDistance();
        if (fishingPool.isEmpty() || startingLocation.distanceTo(fishingPool.get().getWorldLocation()) > config.fishingPoolRadius()) {
            return false;
        }
        var interactionTarget = Utility.getInteractionTarget();

        if (interactionTarget != null && interactionTarget.equals(fishingPool.get())) {
            return false;
        }
        useHarpoonSpecial();
        Interaction.clickNpc(fishingPool.get(), config.fishingMethod().toString());
        return Utility.sleepUntilCondition(() -> !Utility.isIdle(), 5000, 200);
    }

    private boolean handleFullInventory() {
        if (!Inventory.isFull()) return false;
        return handleDropping();
    }

    public boolean playerHasReachedTargetedLevel() {
        return Utility.getRealSkillLevel(Skill.FISHING) >= config.targetLevel();
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
        if (e.getGroup().equals("PFisherPluginConfig") && e.getKey().equals("startHotkey")) {
            if (startHotkeyListener != null) {
                keyManager.unregisterKeyListener(startHotkeyListener);
            }
            startHotkeyListener = config.startHotkey() != null ? new HotkeyListener(() -> config.startHotkey()) {
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
            } : null;
            if (startHotkeyListener != null) {
                keyManager.registerKeyListener(startHotkeyListener);
            }
        }
    }

    private void start() {
        Utility.sendGameMessage("Started", "PFisher");
        startingLocation = Walking.getPlayerLocation();
        paistiBreakHandler.startPlugin(this);
        runner.start();
    }

    private void threadedLoop() {
        if (!Utility.isLoggedIn()) {
            stop();
            return;
        }
        if (paistiBreakHandler.shouldBreak(this)) {
            Utility.sendGameMessage("Taking a break", "PFisher");
            Utility.sleepGaussian(2000, 3000);
            paistiBreakHandler.startBreak(this);
            Utility.sleepGaussian(1000, 2000);
            Utility.sleepUntilCondition(() -> !paistiBreakHandler.isBreakActive(this) && Utility.isLoggedIn(), 99999999, 5000);
            return;
        }
        if (handleThreeTickFishing()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleFishing()) {
            Utility.sleepGaussian(175, 250);
            return;
        }
        if (handleFullInventory()) {
            Utility.sleepGaussian(175, 250);
            return;
        }
        if (playerHasReachedTargetedLevel()) {
            Utility.sendGameMessage("Stopping since player has reached the targeted level", "PFisher");
            stop();
        }
        Utility.sleepGaussian(50, 150);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        Actor actor = event.getActor();
        if (actor instanceof Player && actor.equals(PaistiUtils.getClient().getLocalPlayer())) {
            if (event.getActor().getAnimation() == ROD_FISHING_ANIMATION) {
                fishingAnimatedTick.set(Utility.getTickCount());
            }
        }
    }
}
