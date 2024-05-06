package com.theplug.AutoMotherlodeMinePlugin;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
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
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;


@Slf4j
@PluginDescriptor(name = "AutoMotherlodeMine", description = "Automates motherlode mine", enabledByDefault = false, tags = {"paisti", "choso", "motherlode mine", "mining"})
public class AutoMotherlodeMinePlugin extends Plugin {
    @Inject
    public AutoMotherlodeMinePluginConfig config;
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
    private AutoMotherlodeMinePluginScreenOverlay screenOverlay;

    @Inject
    private AutoMotherlodeMinePluginSceneOverlay sceneOverlay;

    @Inject
    private ConfigManager configManager;

    private static final WorldPoint NW_CORNER1 = new WorldPoint(3716, 5687, 0);
    private static final WorldPoint NW_CORNER2 = new WorldPoint(3724, 5659, 0);
    private static final WorldPoint NW_CORNER_TRAVEL_LOC = new WorldPoint(3722, 5685, 0);
    public static final Geometry.Cuboid NW_CUBOID = new Geometry.Cuboid(NW_CORNER1, NW_CORNER2);
    private static final WorldPoint DEPOSIT_HOPPER_WORLDPOINT = new WorldPoint(3749, 5672, 0);
    private static final WorldPoint SACK_WORLDPOINT = new WorldPoint(3749, 5659, 0);
    private static final WorldPoint HAMMER_CRATE_WORLDPOINT = new WorldPoint(3752, 5664, 0);

    private static final WorldPoint BANK_CHEST_WORLDPOINT = new WorldPoint(3758, 5666, 0);
    public static final AtomicReference<Boolean> shouldEmptyStack = new AtomicReference<>(false);

    public ThreadedScriptRunner runner = new ThreadedScriptRunner();

    @Provides
    public AutoMotherlodeMinePluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoMotherlodeMinePluginConfig.class);
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
        Utility.sendGameMessage("Started", "AutoMotherlodeMine");
        initialize();
        paistiBreakHandler.startPlugin(this);
        runner.start();
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("AutoMotherlodeMine: PaistiUtils is required for this plugin to work");
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

        paistiBreakHandler.registerPlugin(this);
    }

    private boolean useDragonPickaxeSpec() {
        if (Utility.getSpecialAttackEnergy() < 100) return false;
        if (Equipment.search().withName("Dragon pickaxe").result().isEmpty() && Equipment.search().withName("Infernal pickaxe").result().isEmpty()) {
            return false;
        }
        Utility.specialAttack();
        Utility.sleepGaussian(600, 800);
        return true;
    }

    private boolean handleMining() {
        if (Utility.getIdleTicks() < 3) return false;
        if (Inventory.isFull()) return false;
        if (!NW_CUBOID.contains(Walking.getPlayerLocation())) {
            WebWalker.walkTo(NW_CORNER_TRAVEL_LOC);
        }
        var ore = TileObjects.search().withName("Ore vein").withAction("Mine").withinCuboid(NW_CUBOID).nearestToPlayerTrueDistance();
        if (ore.isPresent()) {
            var rMap = LocalPathfinder.getReachabilityMap();
            if (!rMap.isReachable(ore.get())) {
                Utility.sendGameMessage("Moving to ore", "AutoMotherlodeMine");
                WebWalker.walkTo(ore.get().getWorldLocation());
            }
            Utility.sendGameMessage("Mining ore", "AutoMotherlodeMine");
            useDragonPickaxeSpec();
            Interaction.clickTileObject(ore.get(), "Mine");
            return Utility.sleepUntilCondition(() -> Utility.getLocalAnimation() == 6752, 3000, 200);
        }
        return false;
    }

    private boolean shouldDeposit() {
        if (inventoryContainsOres()) return false;
        var currDirts = Inventory.search().withName("Pay-dirt").result().size();
        var dirtsInSack = getQuantityInSack();
        if (dirtsInSack < 80) {
            if (Inventory.isFull() || currDirts + dirtsInSack == 80) {
                return true;
            }
        }
        return Inventory.isFull();
    }

    private int getQuantityInSack() {
        return Utility.getVarbitValue(Varbits.SACK_NUMBER);
    }

    private int getDirtQuantity() {
        return Inventory.search().withName("Pay-dirt").result().size();
    }

    private boolean inventoryContainsOres() {
        return Inventory.search().matchesWildCardNoCase("*ore").first().isPresent() || Inventory.search().withName("Coal").first().isPresent();
    }

    private boolean handleWorldHop() {
        if (!NW_CUBOID.contains(Walking.getPlayerLocation())) return false;
        return Utility.worldHopIfPlayersNearby(30);
    }

    private boolean handleDepositHopper() {
        if (!shouldDeposit()) return false;
        if (Walking.getPlayerLocation().distanceTo(DEPOSIT_HOPPER_WORLDPOINT) > 8) {
            var path = WebWalker.findPath(DEPOSIT_HOPPER_WORLDPOINT);
            if (path.isEmpty()) {
                Utility.sendGameMessage("Failed to find path to hopper: " + DEPOSIT_HOPPER_WORLDPOINT, "AutoMotherlodeMine");
                stop();
                return false;
            }
            var partialPath = path.get().getPath().subList(0, Math.max(path.get().getPath().size() - Utility.random(3, 6), 7));
            if (!WebWalker.walkPath(partialPath, WebWalker.getConfigFromUtils())) {
                Utility.sendGameMessage("Failed to find path to hopper: " + DEPOSIT_HOPPER_WORLDPOINT, "AutoMotherlodeMine");
                stop();
                return false;
            }
        }
        var newTotal = getDirtQuantity() + getQuantityInSack();
        var hopper = TileObjects.search().withName("Hopper").withAction("Deposit").first();
        if (hopper.isEmpty()) {
            Utility.sendGameMessage("No hopper", "AutoMotherlodeMine");
            return false;
        }
        if (!Interaction.clickTileObject(hopper.get(), "Deposit")) {
            Utility.sendGameMessage("Failed to deposit", "AutoMotherlodeMine");
            return false;
        }
        if (newTotal > 80) shouldEmptyStack.set(true);
        Utility.sleepGaussian(600, 1200);
        if (handleStrutRepair()) {
            Utility.sleepGaussian(600, 1200);
        }
        return true;
    }

    private List<Widget> getExcessiveItemsInInventory() {
        var items = Inventory.search().filter(i -> {
            if (i.getName().contains("Gem bag")) return false;
            if (i.getName().contains("Coal bag")) return false;
            if (i.getName().contains("pickaxe")) return false;
            if (i.getName().contains("Pay-dirt")) return false;
            if (i.getName().contains("Uncut")) return false;
            if (i.getName().contains("Golden nugget")) return false;
            return true;
        }).result();
        return items;
    }

    private List<Widget> getItemsToDeposit() {
        var items = Inventory.search().filter(i -> {
            if (i.getName().contains("Gem bag")) return false;
            if (i.getName().contains("Coal bag")) return false;
            if (i.getName().contains("pickaxe")) return false;
            if (i.getName().contains("Pay-dirt")) return false;
            return true;
        }).result();
        return items;
    }

    private boolean handleDepositExcessItems() {
        if (!Bank.isOpen()) {
            if (TileObjects.search().withName("Bank chest").reachable().empty()) {
                WebWalker.walkTo(BANK_CHEST_WORLDPOINT);
            }
            Bank.openBank();
            if (!Utility.sleepUntilCondition(Bank::isOpen, 20000, 200)) {
                Utility.sendGameMessage("Failed to handle excess items", "AutoMotherlodeMine");
                return false;
            }
        }
        var depositItems = getItemsToDeposit();
        if (depositItems.size() == 0) return false;
        HashSet<Integer> depositedIds = new HashSet<>();
        for (var item : depositItems) {
            if (depositedIds.contains(item.getItemId())) continue;
            depositedIds.add(item.getItemId());
            Bank.depositAll(item);
            Utility.sleepGaussian(200, 300);
        }
        return true;
    }

    private boolean handleBanking() {
        if (!inventoryContainsOres()) return false;
        if (inventoryContainsDirt()) {
            var dirts = Inventory.search().withName("Pay-dirt").result();
            for (var dirt : dirts) {
                Interaction.clickWidget(dirt, "Drop");
                Utility.sleepGaussian(150, 300);
            }
        }
        if (!Bank.isOpen()) {
            Bank.openBank();
            Utility.sleepUntilCondition(Bank::isOpen);
        }
        if (Bank.isOpen()) {
            return handleDepositExcessItems();
        }
        return false;
    }

    private boolean inventoryContainsCoal() {
        return Inventory.search().withName("Coal").first().isPresent();
    }

    private boolean handleSack() {
        if (!shouldEmptyStack.get() || getQuantityInSack() == 0) {
            shouldEmptyStack.set(false);
            return false;
        }
        if (Inventory.isFull()) return false;
        if (getQuantityInSack() > 60) {
            if (handleStrutRepair()) {
                Utility.sleepGaussian(600, 1200);
            }
        }
        var sack = TileObjects.search().withName("Sack").withAction("Search").nearestToPlayer();
        if (sack.isEmpty()) {
            WebWalker.walkTo(SACK_WORLDPOINT);
            sack = TileObjects.search().withName("Sack").withAction("Search").nearestToPlayer();
            if (sack.isEmpty()) {
                Utility.sendGameMessage("Failed to find sack", "AutoMotherlodeMine");
                return false;
            }
        }
        Interaction.clickTileObject(sack.get(), "Search");
        var varbitBefore = getQuantityInSack();
        if (!Utility.sleepUntilCondition(() -> getQuantityInSack() < varbitBefore, 10000, 200)) {
            Utility.sendGameMessage("Failed to search sack", "AutoMotherlodeMine");
            return false;
        }
        if (getQuantityInSack() == 0) {
            shouldEmptyStack.set(false);
            return true;
        }
        var coalBag = Inventory.search().matchesWildCardNoCase("coal bag*").withAction("Fill").first();
        if (inventoryContainsCoal() && coalBag.isPresent()) {
            Interaction.clickWidget(coalBag.get(), "Fill");
            Utility.sleepGaussian(200, 300);
            Interaction.clickTileObject(sack.get(), "Search");
            if (!Utility.sleepUntilCondition(() -> getQuantityInSack() < varbitBefore, 10000, 200)) {
                Utility.sendGameMessage("Failed to search sack after filling coal bag", "AutoMotherlodeMine");
                return false;
            }
        }
        if (getQuantityInSack() == 0) {
            shouldEmptyStack.set(false);
        }
        return true;
    }


    private boolean inventoryContainsDirt() {
        return Inventory.search().withName("Pay-dirt").first().isPresent();
    }

    private boolean handleStrutRepair() {
        if (Inventory.isFull()) return false;
        var brokenStrut = TileObjects.search().withName("Broken strut").withAction("Hammer").first();
        if (brokenStrut.isEmpty()) return false;
        if (!inventoryContainsDirt()) return false;
        var inventoryContainsHammer = Inventory.search().withName("Hammer").first().isPresent();
        if (!inventoryContainsHammer) {
            Utility.sendGameMessage("Looting hammer to repair broken struct", "AutoMotherlodeMine");
            var crate = TileObjects.search().withId(357).withinDistanceToPoint(1, HAMMER_CRATE_WORLDPOINT).first();
            if (crate.isEmpty()) {
                Utility.sendGameMessage("Failed to find crate containing hammer", "AutoMotherlodeMine");
                return false;
            }
            Interaction.clickTileObject(crate.get(), "Search");
            if (!Utility.sleepUntilCondition(() -> Inventory.search().withName("Hammer").first().isPresent(), 6000, 200)) {
                Utility.sendGameMessage("Failed to find crate containing hammer", "AutoMotherlodeMine");
                return false;
            }
            brokenStrut = TileObjects.search().withName("Broken strut").withAction("Hammer").first();
            if (brokenStrut.isPresent()) {
                Utility.sendGameMessage("Repairing struct", "AutoMotherlodeMine");
                Interaction.clickTileObject(brokenStrut.get(), "Hammer");
                Utility.sleepUntilCondition(() -> TileObjects.search().withName("Broken strut").withAction("Hammer").first().isEmpty(), 20000, 300);
            }

            var hammerInInventory = Inventory.search().withName("Hammer").first();
            if (hammerInInventory.isPresent()) {
                Utility.sendGameMessage("Dropping hammer", "AutoMotherlodeMine");
                Interaction.clickWidget(hammerInInventory.get(), "Drop");
            }
            return true;
        }
        return false;
    }

    private boolean haveCoalBag() {
        var inventorySearchEmpty = Inventory.search().nameContains("coal bag").empty();
        if (!inventorySearchEmpty) return true;
        var bankSearchEmpty = Bank.search().nameContains("coal bag").empty();
        return !bankSearchEmpty;
    }

    private int getTotalNuggets() {
        int inventoryNuggets = Inventory.getItemAmount(ItemID.GOLDEN_NUGGET);
        if (Bank.isBankUpdated()) {
            int bankNuggets = Bank.getQuantityInBank(ItemID.GOLDEN_NUGGET);
            return inventoryNuggets + bankNuggets;
        }
        return inventoryNuggets;
    }

    private boolean handleBuyingItems() {
        if (!config.buyCoalBag()) return false;
        if (Inventory.getEmptySlots() < 2) return false;
        if (!Bank.isBankUpdated()) return false;
        if (!haveCoalBag() && getTotalNuggets() >= 100) {
            WebWalker.walkTo(BANK_CHEST_WORLDPOINT);
            if (!Bank.openBank() || !Utility.sleepUntilCondition(Bank::isOpen) || !Bank.withdraw(ItemID.GOLDEN_NUGGET, Bank.getQuantityInBank(ItemID.GOLDEN_NUGGET))) {
                return false;
            }
            var prospector = NPCs.search().withName("Prospector Percy").withAction("Trade").first();
            if (prospector.isEmpty()) {
                return false;
            }
            Interaction.clickNpc(prospector.get(), "Trade");
            if (!Utility.sleepUntilCondition(Shop::isOpen)) return false;
            Shop.buy("Coal bag", 1);
            return Utility.sleepUntilCondition(this::haveCoalBag);
        }
        return false;
    }

    private void threadedLoop() {
        if (paistiBreakHandler.shouldBreak(this) && !shouldEmptyStack.get()) {
            Utility.sendGameMessage("Taking a break", "AutoMotherlodeMine");
            Utility.sleepGaussian(2000, 3000);
            paistiBreakHandler.startBreak(this);
            Utility.sleepGaussian(1000, 2000);
            Utility.sleepUntilCondition(() -> !paistiBreakHandler.isBreakActive(this) && Utility.isLoggedIn(), 99999999, 5000);
            return;
        }
        if (!getExcessiveItemsInInventory().isEmpty()) {
            handleDepositExcessItems();
            log.debug("handleDepositHopper");
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleDepositHopper()) {
            log.debug("handleDepositHopper");
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleBanking()) {
            log.debug("handleBanking");
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleSack()) {
            log.debug("handleSack");
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleBuyingItems()) {
            log.debug("handleBuyingItems");
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleWorldHop()) {
            log.debug("handleWorldHop");
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleMining()) {
            log.debug("handleMining");
            Utility.sleepGaussian(300, 600);
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
    }


    public void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "AutoMotherlodeMine");
        }
        paistiBreakHandler.stopPlugin(this);
        runner.stop();
    }

    @Subscribe(priority = 100)
    public void onGameTick(GameTick e) {
        if (!isRunning()) return;
    }

    public Duration getRunTimeDuration() {
        return Duration.between(runner.getStartedAt(), Instant.now());
    }
}