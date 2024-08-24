package com.theplug.AutoStunAlcherPlugin;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Potions.BoostPotion;
import com.theplug.PaistiUtils.API.Potions.Potion;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
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
import net.runelite.client.util.WildcardMatcher;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;


@Slf4j
@PluginDescriptor(name = "<HTML><FONT COLOR=#1BB532>AutoStunAlcher</FONT></HTML>", description = "Automates magic training", enabledByDefault = false, tags = {"paisti", "choso", "magic"})
public class AutoStunAlcherPlugin extends Plugin {
    @Inject
    public AutoStunAlcherPluginConfig config;
    @Inject
    private KeyManager keyManager;
    @Inject
    PluginManager pluginManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    EventBus eventBus;

    @Inject
    public PaistiBreakHandler paistiBreakHandler;

    @Inject
    private AutoStunAlcherPluginScreenOverlay screenOverlay;

    @Inject
    private ConfigManager configManager;

    public ThreadedScriptRunner runner = new ThreadedScriptRunner();

    private ArrayList<Integer> alchIds;
    private ArrayList<String> alchMatchers;

    private int highAlchCastOnTick = 0;

    private int stunCastOnTick = 0;

    @Provides
    public AutoStunAlcherPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoStunAlcherPluginConfig.class);
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
        Utility.sendGameMessage("Started", "AutoStunAlcher");
        initialize();
        paistiBreakHandler.startPlugin(this);
        runner.start();
    }

    @Override
    protected void startUp() throws Exception {
        keyManager.registerKeyListener(startHotkeyListener);
        overlayManager.add(screenOverlay);

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });

        runner.setOnGameTickAction(() -> {
            this.threadedOnGameTick();
            return null;
        });

        paistiBreakHandler.registerPlugin(this);
    }

    private boolean handleRandomMiniAfk() {
        if (Utility.random(1, 100) <= config.randomMiniAfkChance()) {
            Utility.sendGameMessage("Random mini afk", "AutoStunAlcher");
            Utility.sleepGaussian(500, 5500);
            return true;
        }
        return false;
    }

    private boolean handleHopOnPlayerNearby() {
        if (!config.hopOnPlayerNearby() && !config.elementalBalance()) return false;
        if (Utility.worldHopIfPlayersNearby(16)) {
            Utility.sendGameMessage("Hopping world because of nearby player", "AutoStunAlcher");
            return true;
        }
        return false;
    }

    private boolean handleMagicBoost() {
        if (Potion.isPotionOnCooldown()) return false;
        if (BoostPotion.SATURATED_HEART.isAnyStatBoostBelow(1) && BoostPotion.SATURATED_HEART.drink()) {
            return true;
        }
        if (BoostPotion.IMBUED_HEART.isAnyStatBoostBelow(1) && BoostPotion.IMBUED_HEART.drink()) {
            return true;
        }
        var realSkillLevel = Utility.getRealSkillLevel(Skill.MAGIC);
        var boostedSkillLevel = Utility.getBoostedSkillLevel(Skill.MAGIC);
        var currBestAlchemySpell = config.alchSpell().getBestAlchemySpell(boostedSkillLevel);
        var currBestStunSpell = config.stunSpell().getBestStunSpell(boostedSkillLevel);
        for (var boostPotion : BoostPotion.values()) {
            var boost = boostPotion.findBoost(Skill.MAGIC);
            if (boost == null) continue;
            if (boost.getBoostAmount() <= 0) continue;
            var boostedMagicLevel = realSkillLevel + Math.max(boost.getBoostAmount() - 3, 0);
            if (boostedMagicLevel <= boostedSkillLevel) continue;
            var newBestAlchemySpell = config.alchSpell().getBestAlchemySpell(boostedMagicLevel);
            var newBestStunSpell = config.stunSpell().getBestStunSpell(boostedMagicLevel);
            if (newBestAlchemySpell != null && !newBestAlchemySpell.equals(currBestAlchemySpell) && boostPotion.drink()) {
                return true;
            }
            if (newBestStunSpell != null && !newBestStunSpell.equals(currBestStunSpell) && boostPotion.drink()) {
                return true;
            }
        }
        return false;
    }

    private void threadedLoop() {
        if (handleTargetLevelReached()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (paistiBreakHandler.shouldBreak(this)) {
            Utility.sendGameMessage("Taking a break", "AutoStunAlcher");
            paistiBreakHandler.startBreak(this);
            Utility.sleepGaussian(1000, 2000);
            Utility.sleepUntilCondition(() -> !paistiBreakHandler.isBreakActive(this), 99999999, 5000);
        }
        if (handleElementalBalance()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (handleHopOnPlayerNearby()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (handleRandomMiniAfk()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (handleMagicBoost()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (handleStunning()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (handleAlching()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        Utility.sleepGaussian(100, 200);
    }

    private boolean handleTargetLevelReached() {
        if (Utility.getRealSkillLevel(Skill.MAGIC) >= config.targetLevel()) {
            Utility.sendGameMessage("Target level reached", "AutoStunAlcher");
            stop();
            return true;
        }
        return false;
    }

    @Override
    protected void shutDown() throws Exception {
        paistiBreakHandler.unregisterPlugin(this);
        overlayManager.remove(screenOverlay);
        keyManager.unregisterKeyListener(startHotkeyListener);
        stop();
    }

    private void initialize() {
        alchIds = new ArrayList<>();
        alchMatchers = new ArrayList<>();
        var splitAlchItems = config.alchItems().trim().split("[,\n]");
        for (var str : splitAlchItems) {
            var itemIdOrName = str.trim().toLowerCase();
            var isOnlyDigits = itemIdOrName.matches("\\d+");
            if (isOnlyDigits) {
                alchIds.add(Integer.parseInt(itemIdOrName));
            } else {
                alchMatchers.add(itemIdOrName);
            }
        }
    }

    private boolean handleElementalBalance() {
        if (!config.elementalBalance()) return false;
        var elementalBalanceNpc = NPCs.search().nameContains("Elemental balance").first();
        if (elementalBalanceNpc.isPresent()) return false;
        if (!House.doesPlayerOwnHouse()) {
            Utility.sendGameMessage("You need to own a house to use elemental balance", "AutoStunAlcher");
            stop();
            return false;
        }
        if (!House.isPlayerInsideHouse()) {
            var portal = TileObjects.search().withName("Portal").withAction("Home").first();
            if (portal.isPresent()) {
                Interaction.clickTileObject(portal.get(), "Home");
                Utility.sleepUntilCondition(House::isPlayerInsideHouse);
            } else {
                var houseLoc = House.getHouseLocation();
                if (houseLoc == null) {
                    return false;
                }
                WebWalker.walkTo(houseLoc.getLocation());
                portal = TileObjects.search().withName("Portal").withAction("Home").first();
                if (portal.isPresent()) {
                    Interaction.clickTileObject(portal.get(), "Home");
                    Utility.sleepUntilCondition(House::isPlayerInsideHouse);
                } else return false;
            }
        }
        var elementalBalance = TileObjects.search().nameContains("Elemental balance").first();
        if (elementalBalance.isEmpty()) {
            return false;
        }
        Interaction.clickTileObject(elementalBalance.get(), "Activate");
        return Utility.sleepUntilCondition(() -> TileObjects.search().nameContains("Elemental balance").first().isEmpty());
    }

    private boolean handleAlching() {
        if (Utility.getTickCount() - highAlchCastOnTick <= 4) return false;
        if (config.alchSpell() == StunAlcherAlchSpell.NONE) {
            return false;
        }
        var alchSpell = config.alchSpell() == StunAlcherAlchSpell.PROGRESSIVE ? StunAlcherAlchSpell.PROGRESSIVE.getBestAlchemySpell() : config.alchSpell().getSpell();
        if (alchSpell == null || !alchSpell.canCast()) {
            Utility.sendGameMessage("Out of runes to cast alch spell", "AutoStunAlcher");
            stop();
            return false;
        }
        var alchItem = Utility.runOnClientThread(() -> Inventory.search().filter(i -> {
            if (alchIds.stream().anyMatch(id -> id == i.getItemId())) return true;
            return alchMatchers.stream().anyMatch(matcher -> WildcardMatcher.matches(matcher, Widgets.getCleanName(i).toLowerCase()));
        }).first());

        if (alchItem.isEmpty()) {
            Utility.sendGameMessage("Out of items to alch", "AutoStunAlcher");
            stop();
            return false;
        }
        if (alchSpell.castOnItem(alchItem.get())) {
            highAlchCastOnTick = Utility.getTickCount();
            return true;
        }
        return false;
    }

    private boolean handleStunning() {
        if (Utility.getTickCount() - stunCastOnTick <= 4) return false;
        if (config.stunSpell() == StunAlcherStunSpell.NONE) {
            return false;
        }
        var stunSpell = config.stunSpell() == StunAlcherStunSpell.PROGRESSIVE ? StunAlcherStunSpell.PROGRESSIVE.getBestStunSpell() : config.stunSpell().getSpell();
        if (stunSpell == null || !stunSpell.canCast()) {
            Utility.sendGameMessage("Out of runes to cast stun spell", "AutoStunAlcher");
            stop();
            return false;
        }
        var possibleTargets = config.elementalBalance() ? NPCs.search().withName(config.stunTarget(), "Elemental balance").notInteractingWithOtherPlayers().result() : NPCs.search().withName(config.stunTarget()).notInteractingWithOtherPlayers().result();
        if (possibleTargets.isEmpty()) {
            return false;
        }
        possibleTargets.sort(Comparator.comparingInt(npc -> {
            if (npc.getInteracting() != null && npc.getInteracting().equals(PaistiUtils.getClient().getLocalPlayer())) {
                return npc.getWorldArea().distanceTo(Walking.getPlayerLocation()) - 100;
            }
            if (npc.getInteracting() == null) {
                return npc.getWorldArea().distanceTo(Walking.getPlayerLocation()) - 50;
            }
            return Integer.MAX_VALUE;
        }));
        var bestTarget = possibleTargets.get(0);
        if (stunSpell.castOnNpc(bestTarget)) {
            stunCastOnTick = Utility.getTickCount();
            Utility.sleepOneTick();
            return true;
        }
        return false;
    }

    public void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "AutoStunAlcher");
        }
        paistiBreakHandler.stopPlugin(this);
        runner.stop();
    }

    private void threadedOnGameTick() {
        if (!isRunning()) return;
    }


    @Subscribe(priority = 100)
    public void onGameTick(GameTick e) {
        if (!isRunning()) return;

        runner.onGameTick();
    }

    public Duration getRunTimeDuration() {
        return Duration.between(runner.getStartedAt(), Instant.now());
    }
}