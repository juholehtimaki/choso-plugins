package com.theplug.AutoTitheFarmPlugin;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.OBS.ThreadedRunner;
import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectSpawned;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@PluginDescriptor(name = "<HTML><FONT COLOR=#1BB532>AutoTitheFarm</FONT></HTML>", description = "Automates tithe farm", enabledByDefault = false, tags = {"paisti", "choso", "tithe farm", "farming"})
public class AutoTitheFarmPlugin extends Plugin {
    @Inject
    public AutoTitheFarmPluginConfig config;
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
    private AutoTitheFarmPluginScreenOverlay screenOverlay;

    @Inject
    private AutoTitheFarmPluginSceneOverlay sceneOverlay;

    @Inject
    private ConfigManager configManager;

    public ThreadedRunner runner = new ThreadedRunner();

    InventoryLoadout.InventoryLoadoutSetup loadout;

    private static int EMPTY_PATCH_ID = 27383;
    private static int WATER_BARREL_ID = 5598;
    private static int DEPOSIT_SACK_ID = 27431;

    public Set<TitheFarmPlant> plants = new HashSet<>();

    public static Geometry.CuboidArea FARM_AREA = new Geometry.CuboidArea(
            new Geometry.Cuboid(new WorldPoint(1818, 3487, 0), new WorldPoint(1809, 3515, 0)),
            new Geometry.Cuboid(new WorldPoint(1818, 3487, 0), new WorldPoint(1819, 3500, 0))
    );

    public final WorldPoint PATCH_1_NW_CORNER = new WorldPoint(1818, 3487, 0);
    public final WorldPoint PATCH_1_SE_CORNER = new WorldPoint(1809, 3515, 0);
    public final WorldPoint PATCH_2_NW_CORNER = new WorldPoint(1823, 3487, 0);
    public final WorldPoint PATCH_2_SE_CORNER = new WorldPoint(1819, 3500, 0);

    public final WorldPoint START_POSITION = new WorldPoint(1813, 3488, 0);

    public Geometry.CuboidArea getFarmingArea() {
        return new Geometry.CuboidArea(
                new Geometry.Cuboid(Utility.threadSafeGetInstanceWorldPoint(PATCH_1_NW_CORNER), Utility.threadSafeGetInstanceWorldPoint(PATCH_1_SE_CORNER)),
                new Geometry.Cuboid(Utility.threadSafeGetInstanceWorldPoint(PATCH_2_NW_CORNER), Utility.threadSafeGetInstanceWorldPoint(PATCH_2_SE_CORNER)));
    }


    @Provides
    public AutoTitheFarmPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoTitheFarmPluginConfig.class);
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
        Utility.sendGameMessage("Started", "AutoTitheFarm");
        initialize();
        paistiBreakHandler.startPlugin(this);
        runner.start();
    }

    @Override
    protected void startUp() throws Exception {
        keyManager.registerKeyListener(startHotkeyListener);
        overlayManager.add(screenOverlay);
        overlayManager.add(sceneOverlay);

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });


        paistiBreakHandler.registerPlugin(this);
    }

    private boolean handleToggleRun() {
        if (Walking.isRunEnabled() || Walking.getRunEnergy() < 15) return false;
        return Walking.setRun(true);
    }

    public int getTotalWaterCount() {
        // Search for all items matching the criteria
        var waterCansWithWater = Inventory.search().nameContains("Watering can(").result();

        int totalWater = 0;

        // Iterate through each item and parse the water amount
        for (var waterCan : waterCansWithWater) {
            String name = waterCan.getName();

            // Extract the number inside the parentheses using regex with a capture group
            if (name != null) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\((\\d+)\\)").matcher(name);
                if (matcher.find()) {
                    int waterAmount = Integer.parseInt(matcher.group(1)); // Get the captured number
                    totalWater += waterAmount;
                }
            }
        }

        return totalWater;
    }

    private boolean shouldPlant() {
        if (TileObjects.search().withAction("Harvest").first().isPresent()) return false;
        if (Inventory.getItemAmount(ItemID.BOLOGANO_FRUIT) > 0) return false;
        return true;
    }

    private boolean shouldDeposit() {
        return Inventory.getItemAmount(ItemID.BOLOGANO_FRUIT) > 0 && TileObjects.search().withId(EMPTY_PATCH_ID).withinCuboid(getFarmingArea()).result().size() >= 20;
    }

    private boolean shouldFillWateringCans() {
        return TileObjects.search().withId(EMPTY_PATCH_ID).withinCuboid(getFarmingArea()).result().size() >= 20 && getTotalWaterCount() < 64;
    }

    private boolean handleDepositing() {
        if (!shouldDeposit()) return false;
        var sack = TileObjects.search().withId(DEPOSIT_SACK_ID).withAction("Deposit").nearestToPlayer();
        if (sack.isEmpty()) {
            Utility.sendGameMessage("Couldn't find sack", "AutoTitheFarm");
            return false;
        }
        var fruitsBefore = Inventory.getItemAmount(ItemID.BOLOGANO_FRUIT);
        Interaction.clickTileObject(sack.get(), "Deposit");
        return Utility.sleepUntilCondition(() -> Inventory.getItemAmount(ItemID.BOLOGANO_FRUIT) < fruitsBefore);
    }

    private boolean handleWateringCanFilling() {
        if (shouldDeposit()) return false;
        if (!shouldFillWateringCans()) return false;
        var barrel = TileObjects.search().withId(WATER_BARREL_ID).nearestToPlayer();
        if (barrel.isEmpty()) {
            Utility.sendGameMessage("Couldn't find water barrel", "AutoTitheFarm");
            return false;
        }
        var wateringCan = Inventory.search().nameContains("Watering can").filter(t -> !t.getName().contains("8")).first();
        if (wateringCan.isEmpty()) {
            Utility.sendGameMessage("Couldn't find watering can to fill", "AutoTitheFarm");
            return false;
        }
        Interaction.useItemOnTileObject(wateringCan.get(), barrel.get());
        return Utility.sleepUntilCondition(() -> getTotalWaterCount() >= 64, 45000, 1000);
    }

    private boolean handlePlanting() {
        if (!shouldPlant()) return false;
        var farmingArea = getFarmingArea();
        var patchToPlant = TileObjects.search().withId(EMPTY_PATCH_ID).withinCuboid(farmingArea).nearestToPlayerTrueDistance();
        if (patchToPlant.isEmpty()) {
            return false;
        }
        var seeds = Inventory.search().withId(ItemID.BOLOGANO_SEED).first();
        if (seeds.isEmpty()) return false;
        Interaction.useItemOnTileObject(seeds.get(), patchToPlant.get());
        var seedCountBefore = seeds.get().getItemQuantity();
        Utility.sleepUntilCondition(() -> Inventory.getItemAmount(ItemID.BOLOGANO_SEED) < seedCountBefore);
        var patchToWater = TileObjects.search().withAction("Water").withinCuboid(farmingArea).nearestToPlayerTrueDistance();
        if (patchToWater.isEmpty()) return false;
        var wateringCan = Inventory.search().nameContains("Watering can(").first();
        if (wateringCan.isEmpty()) return false;
        var waterLeftBefore = getTotalWaterCount();
        Interaction.useItemOnTileObject(wateringCan.get(), patchToWater.get());
        return Utility.sleepUntilCondition(() -> getTotalWaterCount() < waterLeftBefore);
    }

    private TitheFarmPlant getPlantFromCollection(GameObject gameObject) {
        WorldPoint gameObjectLocation = gameObject.getWorldLocation();
        for (TitheFarmPlant plant : plants) {
            if (gameObjectLocation.equals(plant.getWorldLocation())) {
                return plant;
            }
        }
        return null;
    }

    private boolean handleWatering() {
        var plantsWithWaterAction = TileObjects.search().withAction("Water").result();
        var plantsWithTimer = new ArrayList<>(plants).stream().filter(p -> p.getState() == TitheFarmPlantState.UNWATERED && plantsWithWaterAction.contains(p.getGameObject())).sorted(Comparator.comparingDouble(t -> -t.getTimeFromLastInteraction())).collect(Collectors.toList());
        if (plantsWithTimer.isEmpty()) {
            Utility.sendGameMessage("No plants to water");
            return false;
        }
        var plantToWater = plantsWithTimer.get(0);
        var wateringCan = Inventory.search().nameContains("Watering can(").first();
        if (wateringCan.isEmpty()) return false;
        var waterLeftBefore = getTotalWaterCount();
        Interaction.useItemOnTileObject(wateringCan.get(), plantToWater.getGameObject());
        return Utility.sleepUntilCondition(() -> getTotalWaterCount() < waterLeftBefore);
    }

    private boolean shouldHarvest() {
        return TileObjects.search().withAction("Water").result().isEmpty() && TileObjects.search().withAction("Harvest").first().isPresent();
    }

    private boolean handleStartPosition() {
        if (TileObjects.search().withId(EMPTY_PATCH_ID).withinCuboid(getFarmingArea()).result().size() < 20)
            return false;
        if (getTotalWaterCount() < 64) return false;
        var transformedStartPos = Utility.threadSafeGetInstanceWorldPoint(START_POSITION);
        if (Walking.getPlayerLocation().equals(transformedStartPos)) return false;
        Walking.sceneWalk(transformedStartPos);
        return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(transformedStartPos) <= 0);
    }

    private boolean handleHarvesting() {
        if (!shouldHarvest()) return false;
        var plantsWithTimer = new ArrayList<>(plants).stream().filter(p -> p.getState() == TitheFarmPlantState.GROWN).sorted(Comparator.comparingDouble(t -> -t.getTimeFromLastInteraction())).collect(Collectors.toList());
        if (plantsWithTimer.isEmpty()) return false;
        var plantToHarvest = plantsWithTimer.get(0);
        var fruitCountBefore = Inventory.getItemAmount(ItemID.BOLOGANO_FRUIT);
        Interaction.clickTileObject(plantToHarvest.getGameObject(), "Harvest");
        return Utility.sleepUntilCondition(() -> Inventory.getItemAmount(ItemID.BOLOGANO_FRUIT) > fruitCountBefore);
    }

    private void threadedLoop() {
        if (!Utility.isLoggedIn()) {
            if (!Utility.sleepUntilCondition(Utility::isLoggedIn, 10000, 300)) {
                log.info("Player is not logged in, stopping");
                stop();
                return;
            }
        }
        if (handleToggleRun()) {
            Utility.sendGameMessage("handleToggleRun", "AutoTitheFarm");
            Utility.sleepGaussian(600, 1000);
            return;
        }
        if (handleDepositing()) {
            Utility.sendGameMessage("handleDepositing", "AutoTitheFarm");
            Utility.sleepGaussian(600, 1000);
            return;
        }
        if (handleWateringCanFilling()) {
            Utility.sendGameMessage("handleWateringCanFilling", "AutoTitheFarm");
            Utility.sleepGaussian(600, 1000);
            return;
        }
        if (handleStartPosition()) {
            Utility.sendGameMessage("handleStartPosition", "AutoTitheFarm");
            Utility.sleepGaussian(600, 1000);
            return;
        }
        if (handlePlanting()) {
            Utility.sleepGaussian(600, 1000);
            return;
        }
        if (handleWatering()) {
            Utility.sendGameMessage("handleWatering", "AutoTitheFarm");
            Utility.sleepGaussian(600, 1000);
            return;
        }
        if (handleHarvesting()) {
            Utility.sendGameMessage("handleHarvesting", "AutoTitheFarm");
            Utility.sleepGaussian(600, 1000);
            return;
        }
        Utility.sleepGaussian(300, 600);
    }

    @Override
    protected void shutDown() throws Exception {
        paistiBreakHandler.unregisterPlugin(this);
        overlayManager.remove(screenOverlay);
        overlayManager.remove(sceneOverlay);
        keyManager.unregisterKeyListener(startHotkeyListener);
        stop();
    }

    private void initialize() {
        loadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.inventoryLoadout());
        plants.clear();
    }

    public void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "AutoTitheFarm");
        }
        paistiBreakHandler.stopPlugin(this);
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

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();

        TitheFarmPlantType type = TitheFarmPlantType.getPlantType(gameObject.getId());
        if (type == null) {
            return;
        }

        TitheFarmPlantState state = TitheFarmPlantState.getState(gameObject.getId());

        TitheFarmPlant newPlant = new TitheFarmPlant(state, type, gameObject);
        TitheFarmPlant oldPlant = getPlantFromCollection(gameObject);

        if (oldPlant == null && newPlant.getType() != TitheFarmPlantType.EMPTY) {
            log.debug("Added plant {}", newPlant);
            plants.add(newPlant);
        } else if (oldPlant == null) {
            return;
        } else if (newPlant.getType() == TitheFarmPlantType.EMPTY) {
            log.debug("Removed plant {}", oldPlant);
            plants.remove(oldPlant);
        } else if (oldPlant.getGameObject().getId() != newPlant.getGameObject().getId()) {
            if (oldPlant.getState() != TitheFarmPlantState.WATERED && newPlant.getState() == TitheFarmPlantState.WATERED) {
                log.debug("Updated plant (watered)");
                newPlant.setPlanted(oldPlant.getPlanted());
                newPlant.setLastInteraction(Instant.now());
                plants.remove(oldPlant);
                plants.add(newPlant);
            } else {
                log.debug("Updated plant");
                plants.remove(oldPlant);
                newPlant.setLastInteraction(oldPlant.getLastInteraction());
                plants.add(newPlant);
            }
        }
    }
}