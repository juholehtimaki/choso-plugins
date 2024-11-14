package com.theplug.AutoMotherlodeMinePlugin;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.OBS.ThreadedRunner;
import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.SharedLogic.Pickaxe;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
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
@PluginDescriptor(name = "<HTML><FONT COLOR=#1BB532>AutoMotherlodeMine</FONT></HTML>", description = "Automates motherlode mine", enabledByDefault = false, tags = {"paisti", "choso", "motherlode mine", "mining"})
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

    private static final WorldPoint DEPOSIT_HOPPER_WORLDPOINT = new WorldPoint(3749, 5672, 0);
    private static final WorldPoint SACK_WORLDPOINT = new WorldPoint(3749, 5659, 0);
    private static final WorldPoint HAMMER_CRATE_WORLDPOINT = new WorldPoint(3752, 5664, 0);
    private static final WorldPoint BANK_CHEST_WORLDPOINT = new WorldPoint(3758, 5666, 0);
    public static final AtomicReference<Boolean> shouldEmptySack = new AtomicReference<>(false);
    private final AtomicReference<Integer> playersPresentForTicks = new AtomicReference<>(0);
    private final AtomicReference<Integer> previousMiningLevel = new AtomicReference<>(null);

    public ThreadedRunner runner = new ThreadedRunner();

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
        if (Utility.getIdleTicks() < 3 && Walking.getPlayerLocation().distanceTo(DEPOSIT_HOPPER_WORLDPOINT) > 2)
            return false;
        if (Inventory.isFull()) return false;
        if (!config.area().getCuboidArea().contains(Walking.getPlayerLocation())) {
            WebWalker.walkTo(config.area().getWp());
        }
        var ore = TileObjects.search().withName("Ore vein").withAction("Mine").withinCuboid(config.area().getCuboidArea()).nearestToPlayerTrueDistance();
        if (ore.isPresent()) {
            var rMap = LocalPathfinder.getReachabilityMap();
            if (!rMap.isReachable(ore.get())) {
                Utility.sendGameMessage("Moving to ore", "AutoMotherlodeMine");
                if (!WebWalker.walkTo(ore.get().getWorldLocation())) {
                    Utility.sendGameMessage("Failed to path to ore", "AutoMotherlodeMine");
                    return false;
                }
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
        var dirtsInInv = Inventory.search().withName("Pay-dirt").result().size();
        if (dirtsInInv == 0) return false;
        int SACK_CAPACITY = 108;

        var dirtsInSack = getQuantityInSack();
        if (dirtsInSack >= SACK_CAPACITY - 28) {
            if (dirtsInInv + dirtsInSack == SACK_CAPACITY) {
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
        return Inventory.search().matchesWildcard("*ore").first().isPresent() || Inventory.search().withName("Coal").first().isPresent();
    }

    private boolean handleWorldHop() {
        if (config.area() == Area.UPPER_AREA) return false;
        if (!config.area().getCuboidArea().contains(Walking.getPlayerLocation())) return false;
        if (playersPresentForTicks.get() >= 20) {
            Utility.sendGameMessage("Hopping to avoid players", "AutoMotherlodeMine");
            if (Worldhopping.hopToNext(false)) {
                playersPresentForTicks.set(16);
                Utility.sleepGaussian(2600, 3000);
                return true;
            }
        }
        return false;
    }

    private boolean handleDepositHopper() {
        if (!shouldDeposit()) return false;
        var rMap = LocalPathfinder.getReachabilityMap();
        if (Walking.getPlayerLocation().distanceTo(DEPOSIT_HOPPER_WORLDPOINT) > 6 ||
                !rMap.isReachable(DEPOSIT_HOPPER_WORLDPOINT)) {
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
        var dirtBefore = getDirtQuantity();
        if (!Interaction.clickTileObject(hopper.get(), "Deposit")) {
            Utility.sendGameMessage("Failed to deposit", "AutoMotherlodeMine");
            return false;
        } else {
            Utility.sleepUntilCondition(() -> getDirtQuantity() < dirtBefore);
        }
        int SACK_CAPACITY = 108;
        if (newTotal >= SACK_CAPACITY - 10) {
            shouldEmptySack.set(true);
        } else {
            handleStrutRepair();
        }
        return true;
    }

    private List<Widget> getExcessiveItemsInInventory() {
        var items = Inventory.search().filter(i -> {
            if (i.getName().contains("Gem bag")) return false;
            if (i.getName().toLowerCase().contains("coal bag")) return false;
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
            if (i.getName().toLowerCase().contains("coal bag")) return false;
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
        if (depositItems.isEmpty()) return false;
        HashSet<Integer> depositedIds = new HashSet<>();
        for (var item : depositItems) {
            if (depositedIds.contains(item.getItemId())) continue;
            depositedIds.add(item.getItemId());
            Bank.depositAll(item);
            Utility.sleepGaussian(200, 300);
        }
        var coalBag = BankInventory.search().withName("Open coal bag").withAction("Empty").first();
        if (coalBag.isPresent()) {
            Interaction.clickWidget(coalBag.get(), "Empty");
            Utility.sleepGaussian(100, 200);
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

    private boolean handleSack() {
        if (!shouldEmptySack.get() || getQuantityInSack() == 0) {
            shouldEmptySack.set(false);
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

        Utility.sleepUntilCondition(() -> {
            if (getQuantityInSack() == 0) {
                shouldEmptySack.set(false);
                return true;
            }
            if (Inventory.isFull()) {
                int coalAmount = Inventory.getItemAmount(ItemID.COAL);
                if (coalAmount >= 6) {
                    var coalBag = Inventory.search().withName("Open coal bag").withAction("Fill").first();
                    if (coalBag.isPresent()) {
                        Utility.sleepGaussian(200, 300);
                        Interaction.clickWidget(coalBag.get(), "Fill");
                        boolean didPutCoal = Utility.sleepUntilCondition(() -> Inventory.getItemAmount(ItemID.COAL) < coalAmount, 1200, 100);
                        return !didPutCoal; // Exit loop if coal bag was full
                    }
                }
                return true;
            }
            var nutsack = TileObjects.search().withName("Sack").withAction("Search").nearestToPlayer();
            if (nutsack.isEmpty()) return true;
            Interaction.clickTileObject(nutsack.get(), "Search");
            var varbitBefore = getQuantityInSack();
            int slotsBefore = Inventory.getEmptySlots();
            Utility.sleepUntilCondition(() -> getQuantityInSack() < varbitBefore || Inventory.getEmptySlots() < slotsBefore, 10000, 200);
            return false;
        }, 20000, 100);

        return true;
    }

    private boolean handleEquippingBestPickaxe() throws Exception {
        if (!config.upgradePickaxe()) return false;
        if (Inventory.getEmptySlots() < 1) return false;
        if (shouldEmptySack.get()) return false;
        var havePickaxe = !Inventory.search().matchesWildcard("*pickaxe").onlyUnnoted().empty() ||
                !Equipment.search().matchesWildCardNoCase("*pickaxe").empty();

        // Ensure we have ANY pickaxe and then evaluate whether we should check for upgrades based on mining level increases

        if (havePickaxe) {
            // No need to handle pick switches after dragon pickaxe tier
            if (Utility.getRealSkillLevel(Skill.MINING) > 61)
                return false;

            if (Utility.getRealSkillLevel(Skill.MINING) <= previousMiningLevel.get()) {
                return false;
            }
        }

        previousMiningLevel.set(Utility.getRealSkillLevel(Skill.MINING));

        // Check if inventory has the best pick for the level already and maybe equip it
        var bestPickForLevel = Pickaxe.getBestPickaxeForMiningLevel();
        if (bestPickForLevel.isEquipped()) {
            return false;
        } else if (bestPickForLevel.canEquip()) {
            var bestPickForLevelInInventory = bestPickForLevel.findOnPlayer();
            if (bestPickForLevelInInventory.isPresent()) {
                Utility.sendGameMessage("Equipping: " + bestPickForLevel.getName(), "AutoMotherlodeMine");
                Interaction.clickWidget(bestPickForLevelInInventory.get(), "Wield", "Wear", "Equip");
                Utility.sleepUntilCondition(bestPickForLevel::isEquipped, 3000, 600);
                return true;
            }
        }


        // Check that bank is updated before determining the best AVAILABLE pickaxe
        if (!Bank.isBankUpdated()) {
            Utility.sendGameMessage("Checking for better pickaxes in bank", "AutoMotherlodeMine");
            WebWalker.walkTo(BANK_CHEST_WORLDPOINT);
            if (!Bank.openBank() || !Utility.sleepUntilCondition(Bank::isOpen)) {
                Utility.sendGameMessage("Failed to walk to bank to check for pickaxes", "AutoMotherlodeMine");
                return false;
            }
        }


        var bestPickaxe = Pickaxe.getBestAvailablePickaxe();
        if (bestPickaxe == null) {
            throw new Exception("No pickaxe detected");
        }

        if (bestPickaxe.isEquipped()) return false;
        if (bestPickaxe.findOnPlayer().isPresent()) return false;

        // Best available pickaxe is in the bank
        if (bestPickaxe.findOnPlayer().isEmpty()) {
            Utility.sendGameMessage("Getting " + bestPickaxe.getName() + " from bank", "AutoMotherlodeMine");
            if (!Bank.isOpen() && !WebWalker.walkToNearestBank()) {
                Utility.sendGameMessage("Failed to walk to bank", "AutoMotherlodeMine");
                return false;
            }

            if (Bank.openBank() && Utility.sleepUntilCondition(Bank::isOpen, 10000, 600)) {
                Utility.sleepGaussian(1200, 1600);
                var bankPickaxe = bestPickaxe.findInBank();
                if (bankPickaxe.isPresent()) {
                    Bank.withdraw(bankPickaxe.get().getItemId(), 1);
                    if (Utility.sleepUntilCondition(() -> bestPickaxe.findOnPlayer().isPresent(), 3000, 600)) {
                        Utility.sendGameMessage("Withdrew: " + bestPickaxe.getName(), "AutoMotherlodeMine");
                    } else {
                        Utility.sendGameMessage("Failed to withdraw pickaxe", "AutoMotherlodeMine");
                        havePickaxe = !Inventory.search().matchesWildcard("*pickaxe").onlyUnnoted().empty() ||
                                Equipment.search().matchesWildCardNoCase("*pickaxe").first().isPresent();
                        if (!havePickaxe) {
                            Utility.sendGameMessage("Failed to retrieve a pickaxe", "AutoMotherlodeMine");
                            stop();
                            return true;
                        }
                    }
                } else {
                    Utility.sendGameMessage("No " + bestPickaxe.getName() + " in bank", "AutoMotherlodeMine");
                    return true;
                }
            } else {
                Utility.sendGameMessage("Failed to open bank to withdraw pickaxe", "AutoMotherlodeMine");
                return true;
            }
        }

        // Best available pickaxe is in inventory but not equipped
        if (!bestPickaxe.isEquipped() && bestPickaxe.canEquip()) {
            Utility.sleepGaussian(1200, 1600);
            var bestPickWidget = bestPickaxe.findOnPlayer();
            if (bestPickWidget.isPresent()) {
                Utility.sendGameMessage("Equipping: " + bestPickaxe.getName(), "AutoMotherlodeMine");
                Interaction.clickWidget(bestPickWidget.get(), "Wield", "Wear", "Equip");
                Utility.sleepUntilCondition(bestPickaxe::isEquipped, 3000, 600);
            }
        }

        // Unequip lower tier pickaxe
        for (var pickaxe : Pickaxe.values()) {
            if (pickaxe.getMiningLevel() >= bestPickaxe.getMiningLevel()) continue;
            if (pickaxe.isEquipped()) {
                var equipped = pickaxe.findOnPlayer();
                if (equipped.isPresent()) {
                    Utility.sendGameMessage("Unequipping: " + pickaxe.getName(), "AutoMotherlodeMine");
                    Interaction.clickWidget(equipped.get(), "Remove");
                    Utility.sleepUntilCondition(() -> !pickaxe.isEquipped(), 3000, 600);
                    break;
                }
            }
        }

        // Deposit all lower tier pickaxes
        if (Bank.isNearBank() && Bank.openBank() && Utility.sleepUntilCondition(Bank::isOpen, 10000, 600)) {
            Utility.sendGameMessage("Depositing previous pickaxes", "AutoMotherlodeMine");
            for (var pickaxe : Pickaxe.values()) {
                if (pickaxe.getMiningLevel() >= bestPickaxe.getMiningLevel()) continue;
                var inInventory = pickaxe.findOnPlayer();
                if (inInventory.isPresent()) {
                    Bank.depositAll(inInventory.get().getItemId());
                    Utility.sleepUntilCondition(() -> pickaxe.findOnPlayer().isEmpty(), 3000, 600);
                }
            }
            Bank.closeBank();
            return true;
        }

        return false;
    }


    private boolean inventoryContainsDirt() {
        return Inventory.search().withName("Pay-dirt").first().isPresent();
    }

    private boolean handleOpeningCoalBag() {
        if (shouldEmptySack.get()) return false;
        var coalBagToOpen = Inventory.search().withName("Coal bag").first();
        if (coalBagToOpen.isPresent()) {
            Utility.sendGameMessage("Opening coal sack", "AutoMotherlodeMine");
            Interaction.clickWidget(coalBagToOpen.get(), "Open");
            return Utility.sleepUntilCondition(() -> Inventory.search().withName("Coal bag").first().isEmpty());
        }
        return false;
    }

    private boolean handleStrutRepair() {
        if (Inventory.isFull()) return false;
        if (shouldEmptySack.get()) return false;
        var brokenStrut = TileObjects.search().withName("Broken strut").withAction("Hammer").first();
        if (brokenStrut.isEmpty()) return false;
        var inventoryContainsHammer = Inventory.search().withName("Hammer").first().isPresent();
        if (!inventoryContainsHammer) {
            Utility.sendGameMessage("Looting hammer to repair broken struct", "AutoMotherlodeMine");
            var crate = TileObjects.search().withId(357).withinDistanceToPoint(1, HAMMER_CRATE_WORLDPOINT).first();
            if (crate.isEmpty()) {
                Utility.sendGameMessage("Failed to find crate containing hammer", "AutoMotherlodeMine");
                return false;
            }
            Interaction.clickTileObject(crate.get(), "Search");
            if (!Utility.sleepUntilCondition(() -> Inventory.search().withName("Hammer").first().isPresent())) {
                Utility.sendGameMessage("Failed to find crate containing hammer", "AutoMotherlodeMine");
                return false;
            }
            brokenStrut = TileObjects.search().withName("Broken strut").withAction("Hammer").first();
            if (brokenStrut.isPresent()) {
                Utility.sendGameMessage("Repairing struct", "AutoMotherlodeMine");
                Interaction.clickTileObject(brokenStrut.get(), "Hammer");
                var structLoc = brokenStrut.get().getWorldLocation();
                Utility.sleepUntilCondition(() -> TileObjects.search().withName("Broken strut").withAction("Hammer").withinDistanceToPoint(3, structLoc).first().isEmpty(), 20000, 300);
            }

            // CHECK FOR OTHER BROKEN STRUCTS IN RARE CASES
            brokenStrut = TileObjects.search().withName("Broken strut").withAction("Hammer").first();
            if (brokenStrut.isPresent()) {
                Utility.sendGameMessage("Repairing struct", "AutoMotherlodeMine");
                Interaction.clickTileObject(brokenStrut.get(), "Hammer");
                var structLoc = brokenStrut.get().getWorldLocation();
                Utility.sleepUntilCondition(() -> TileObjects.search().withName("Broken strut").withAction("Hammer").withinDistanceToPoint(3, structLoc).first().isEmpty(), 20000, 300);
            }

            var hammerInInventory = Inventory.search().withName("Hammer").first();
            if (hammerInInventory.isPresent()) {
                Utility.sendGameMessage("Dropping hammer", "AutoMotherlodeMine");
                Interaction.clickWidget(hammerInInventory.get(), "Drop");
                Utility.sleepGaussian(200, 300);
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

    public static int getTotalNuggets() {
        int inventoryNuggets = Inventory.getItemAmount(ItemID.GOLDEN_NUGGET);
        if (Bank.isBankUpdated()) {
            int bankNuggets = Bank.getQuantityInBank(ItemID.GOLDEN_NUGGET);
            return inventoryNuggets + bankNuggets;
        }
        return inventoryNuggets;
    }

    private boolean handleToggleRun() {
        if (Walking.isRunEnabled() || Walking.getRunEnergy() < 15) return false;
        return Walking.setRun(true);
    }

    private void trackNearbyPlayersPresence() {
        var players = Players.search().notLocal().inCuboidArea(config.area().getCuboidArea()).result();
        if (players.isEmpty()) {
            playersPresentForTicks.set(0);
        } else {
            playersPresentForTicks.set(playersPresentForTicks.get() + 1);
        }
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
            Utility.sleepGaussian(600, 800);
            if (getTotalNuggets() < 100) return false;
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

    private void threadedLoop() throws Exception {
        if (paistiBreakHandler.shouldBreak(this) && !shouldEmptySack.get()) {
            Utility.sendGameMessage("Taking a break", "AutoMotherlodeMine");
            Utility.sleepGaussian(2000, 3000);
            paistiBreakHandler.startBreak(this);
            Utility.sleepGaussian(1000, 2000);
            Utility.sleepUntilCondition(() -> !paistiBreakHandler.isBreakActive(this), 99999999, 5000);
            return;
        }
        if (!getExcessiveItemsInInventory().isEmpty()) {
            handleDepositExcessItems();
            log.debug("handleDepositExcessItems");
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
        if (handleEquippingBestPickaxe()) {
            log.debug("handleEquippingBestPickaxe");
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
        if (handleToggleRun()) {
            log.debug("handleToggleRun");
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleOpeningCoalBag()) {
            log.debug("handleOpeningCoalBag");
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
        int SACK_CAPACITY = 108;
        if (getQuantityInSack() < SACK_CAPACITY - 10) shouldEmptySack.set(false);
        previousMiningLevel.set(Utility.getRealSkillLevel(Skill.MINING));
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
        if (config.area() != Area.UPPER_AREA) trackNearbyPlayersPresence();
    }

    public Duration getRunTimeDuration() {
        return Duration.between(runner.getStartedAt(), Instant.now());
    }
}