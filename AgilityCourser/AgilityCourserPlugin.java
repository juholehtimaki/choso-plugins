package com.theplug.AgilityCourser;

import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Spells.Standard;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.WildcardMatcher;

import java.util.*;

@Slf4j
@PluginDescriptor(name = "PAgilityCourser", description = "Trains agility", enabledByDefault = false, tags = {"paisti", "agility"})
public class AgilityCourserPlugin extends Plugin {
    @Inject
    AgilityCourserPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    PaistiBreakHandler paistiBreakHandler;
    @Inject
    private KeyManager keyManager;
    ThreadedScriptRunner runner = new ThreadedScriptRunner();
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

    private Obstacle lastObstacle = null;

    private HashSet<Course> allowedCourses;

    private ArrayList<Integer> alchIds;
    private ArrayList<String> alchMatchers;
    private int highAlchCastOnTick = 0;

    Course course;

    @Provides
    public AgilityCourserPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AgilityCourserPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("AgilityCourser: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }

        keyManager.registerKeyListener(startHotkeyListener);

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });

        paistiBreakHandler.registerPlugin(this);
    }

    @Inject
    public FoodStats foodStats;

    @Override
    protected void shutDown() throws Exception {
        keyManager.unregisterKeyListener(startHotkeyListener);
        paistiBreakHandler.unregisterPlugin(this);
        runner.stop();
    }

    private void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "AgilityCourser");
        }
        paistiBreakHandler.stopPlugin(this);
        runner.stop();
    }

    public List<Widget> getFoodItems() {
        return Inventory.search().onlyUnnoted().filter((item) -> foodStats.getHealAmount(item.getItemId()) >= 3).result();
    }

    public boolean handleEating() {
        if (Utility.getBoostedSkillLevel(Skill.HITPOINTS) > 7) return false;

        var foods = getFoodItems();

        if (foods.isEmpty()) {
            Utility.sendGameMessage("Waiting for HP.", "PAgilityCourser");
            Utility.sleepGaussian(15000, 30000);
            return true;
        }

        var foodToEat = foods.get(0);

        return Interaction.clickWidget(foodToEat, "Eat", "Drink");
    }


    public boolean handleAlchItem() {
        if (!config.alchDuringCourses()) return false;
        if (Utility.random(1, 100) <= 10) return false;
        var alchSpell = Utility.getRealSkillLevel(Skill.MAGIC) >= 55 ? Standard.HIGH_LEVEL_ALCHEMY : Standard.LOW_LEVEL_ALCHEMY;
        if (!alchSpell.canCast()) return false;
        if (Utility.getTickCount() - highAlchCastOnTick <= 4) return false;
        var alchItem = Utility.runOnClientThread(() -> Inventory.search().filter(i -> {
            if (alchIds.stream().anyMatch(id -> id == i.getItemId())) return true;
            return alchMatchers.stream().anyMatch(matcher -> WildcardMatcher.matches(matcher, Widgets.getCleanName(i).toLowerCase()));
        }).first());
        if (alchItem.isEmpty()) return false;

        Utility.sleepGaussian(225, 450);
        if (alchSpell.castOnItem(alchItem.get())) {
            highAlchCastOnTick = Utility.getTickCount();
            return true;
        }

        return false;
    }

    public boolean handleNextObstacle() {
        var nextObstacle = course.getNextObstacle(lastObstacle);

        if (nextObstacle != null && nextObstacle.worldPoint.distanceTo(Walking.getPlayerLocation()) > 16) {
            var path = WebWalker.findPath(nextObstacle.worldPoint);
            if (path.isEmpty()) {
                Utility.sendGameMessage("Failed to find path to obstacle: " + nextObstacle, "PAgilityCourser");
                stop();
                return false;
            }
            var partialPath = path.get().getPath().subList(0, Math.max(path.get().getPath().size() - Utility.random(5, 10), 7));
            if (!WebWalker.walkPath(partialPath, WebWalker.getConfigFromUtils())) {
                Utility.sendGameMessage("Failed to find path to obstacle: " + nextObstacle, "PAgilityCourser");
                stop();
                return false;
            }
        } else if (nextObstacle == null) {
            Utility.sleepGaussian(2400, 3000);
            nextObstacle = course.getNextObstacle(lastObstacle);
            if (nextObstacle == null) {
                if (!WebWalker.walkTo(course.obstacles.get(0).worldPoint)) {
                    Utility.sendGameMessage("Failed to walk to obstacle: " + nextObstacle, "PAgilityCourser");
                    return false;
                }
            }
        }

        if (nextObstacle != course.obstacles.get(0) && handleHopOnPlayerNearby()) {
            log.debug("Hopped worlds");
            Utility.sleepGaussian(1200, 1800);
            Utility.sleepUntilCondition(() -> Utility.isLoggedIn(), 10000, 1200);
            Utility.sleepGaussian(1200, 1800);
        }

        if (config.progressiveMode() && nextObstacle == course.obstacles.get(0) && getCourseForCurrentLevel() != course) {
            var nextCourse = getCourseForCurrentLevel();
            if (nextCourse != null && nextCourse != course) {
                if (WebWalker.walkTo(nextCourse.obstacles.get(0).worldPoint)) {
                    course = nextCourse;
                    lastObstacle = null;
                    return true;
                } else {
                    allowedCourses.remove(nextCourse);
                    Utility.sendGameMessage("Failed to walk to course: " + nextCourse.courseName + ". Skipping from progressive mode.", "PAgilityCourser");
                }
            }
        }

        lastObstacle = nextObstacle;
        log.debug("Next obstacle: {}", nextObstacle);
        if (nextObstacle != null) {
            if (Utility.random(1, 100) <= config.randomMiniAfkChance()) {
                Utility.sendGameMessage("Random mini afk", "PAgilityCourser");
                Utility.sleepGaussian(500, 5500);
            }
            nextObstacle.cross();
            if (nextObstacle.worldPoint.distanceTo(Walking.getPlayerLocation()) >= 3 && handleAlchItem()) {
                Utility.sleepGaussian(250, 550);
                nextObstacle.cross();
            }
            if (Utility.random(1, 100) <= config.randomExtraClickChance()) {
                for (int i = 0; i < Utility.random(1, 2); i++) {
                    Utility.sleepGaussian(120, 240);
                    nextObstacle.cross();
                }
            }
            Utility.sleep(1200);
            if (!Utility.sleepUntilCondition(() -> !Utility.isIdle() || !Walking.didNotMoveThisTick(), 1800, 300)) {
                nextObstacle.cross();
            }
            Utility.sleep(600);
            Obstacle finalNextObstacle = nextObstacle;
            var success = Utility.sleepUntilCondition(() -> {
                if (!Utility.isIdle()) {
                    return false;
                }

                var next = course.getNextObstacle(lastObstacle);
                if (next == null) {
                    return false;
                }

                if (next == finalNextObstacle) {
                    return false;
                }

                if (!LocalPathfinder.isWalkable(Walking.getPlayerLocation())
                        && !Walking.getPlayerLocation().equals(next.worldPoint)) {
                    return false;
                }
                return true;
            }, 10000, 600);
            log.debug("Success: {}", success);
            if (success && nextObstacle.extraActionAfterObstacle != null) {
                try {
                    log.debug("Running extra action after obstacle: {}", nextObstacle.extraActionAfterObstacle);
                    nextObstacle.extraActionAfterObstacle.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return success;
        }

        return false;
    }

    public boolean lootMarkOfGraces() {
        var mark = TileItems.search().withName("Mark of grace").nearestToPlayer();
        if (mark.isEmpty()) {
            return false;
        }
        if (mark.get().getQuantity() <= 2 && course == Course.ARDOUGNE) {
            log.debug("Mark of grace quantity too low (Ardougne)");
            return false;
        }
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        // Also check tile above mark of grace as a special case for pollnivneach / ardy marks that spawns on nonreachable tiles
        if (reachabilityMap.isReachable(mark.get().getLocation())
                || reachabilityMap.isReachable(mark.get().getLocation().dy(1))
                || reachabilityMap.isReachable(mark.get().getLocation().dx(-1))) {
            Utility.sleepGaussian(600, 1200);
            Utility.sendGameMessage("Attempting to loot: " + mark.get().getName(), "PAgilityCourser");
            Interaction.clickGroundItem(mark.get(), "Take");
            var quantityBeforeClick = Inventory.getItemAmount(mark.get().getId());
            Utility.sleepUntilCondition(() -> Inventory.getItemAmount(mark.get().getId()) > quantityBeforeClick, 7000, 300);
            return true;
        }
        return false;
    }

    public boolean handleToggleRun() {
        if (Walking.isRunEnabled() || Walking.getRunEnergy() < 15) return false;
        return Walking.setRun(true);
    }

    public boolean handleStamina() {
        if (Walking.getRunEnergy() >= 15) {
            return false;
        }
        var staminaPotion = Inventory.search().matchesWildCardNoCase("stamina potion*").first();
        if (staminaPotion.isPresent()) {
            return Interaction.clickWidget(staminaPotion.get(), "Eat", "Drink");
        }
        return false;
    }

    public boolean playerHasReachedTargetedLevel() {
        if (Utility.getRealSkillLevel(Skill.AGILITY) >= config.targetLevel()) {
            Utility.sendGameMessage("Stopping as we reached the targeted level");
            stop();
            return true;
        }
        return false;
    }

    private void start() {
        allowedCourses = new HashSet<>(Arrays.asList(Course.values()));
        if (config.skipCanifis()) allowedCourses.remove(Course.CANIFIS);
        course = config.progressiveMode() ? getCourseForCurrentLevel() : config.selectedCourse();
        lastObstacle = null;
        Utility.sendGameMessage("Started", "AgilityCourser");
        paistiBreakHandler.startPlugin(this);

        alchIds = new ArrayList<>();
        alchMatchers = new ArrayList<>();
        var splitAlchItems = config.alchNamesOrIds().trim().split("[,\n]");
        for (var str : splitAlchItems) {
            var itemIdOrName = str.trim().toLowerCase();
            var isOnlyDigits = itemIdOrName.matches("\\d+");
            if (isOnlyDigits) {
                alchIds.add(Integer.parseInt(itemIdOrName));
            } else {
                alchMatchers.add(itemIdOrName);
            }
        }
        runner.start();

    }

    public boolean handleHopOnPlayerNearby() {
        if (!config.hopOnPlayerNearby()) return false;
        boolean shouldHop = Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            var players = PaistiUtils.getClient().getPlayers();
            return players.stream().anyMatch(p ->
                    !p.equals(PaistiUtils.getClient().getLocalPlayer())
                            && p.getWorldLocation().distanceTo(Walking.getPlayerLocation()) <= 13
                            && p.getCombatLevel() > 3);
        }));

        if (shouldHop) {
            Utility.sendGameMessage("Player nearby. Hopping worlds.", "PAgilityCourser");
            return Worldhopping.hopToNext(false);
        }
        return false;
    }

    private Course getCourseForCurrentLevel() {
        return Arrays.stream(Course.values())
                .filter(c -> Utility.getRealSkillLevel(Skill.AGILITY) >= c.minimumLevel && allowedCourses.contains(c))
                .max(Comparator.comparingInt(c -> c.minimumLevel))
                .orElse(null);
    }


    private void threadedLoop() {
        if (!Utility.isLoggedIn()) {
            stop();
            return;
        }
        if (playerHasReachedTargetedLevel()) {
            Utility.sendGameMessage("Reached targeted level", "PAgilityCourser");
            paistiBreakHandler.logoutNow(this);
            stop();
            return;
        }
        if (lootMarkOfGraces()) {
            Utility.sleepGaussian(200, 400);
            return;
        }
        if (handleEating()) {
            Utility.sleepGaussian(200, 400);
            return;
        }
        if (handleToggleRun()) {
            Utility.sleepGaussian(200, 400);
            return;
        }
        if (handleStamina()) {
            Utility.sleepGaussian(200, 400);
            return;
        }
        if (handleNextObstacle()) {
            Utility.sleepGaussian(100, 200);
            if (paistiBreakHandler.shouldBreak(this)) {
                Utility.sendGameMessage("Taking a break", "PAgilityCourser");
                Utility.sleepGaussian(2000, 3000);
                paistiBreakHandler.startBreak(this);
                Utility.sleepGaussian(1000, 2000);
                Utility.sleepUntilCondition(() -> !paistiBreakHandler.isBreakActive(this) && Utility.isLoggedIn(), 99999999, 5000);
                return;
            }
            return;
        }

        Utility.sleep(50);
    }
}
