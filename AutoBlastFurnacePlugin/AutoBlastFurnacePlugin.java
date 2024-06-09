package com.theplug.AutoBlastFurnacePlugin;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
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
@PluginDescriptor(name = "AutoBlastFurnace", description = "Smelts using blast furnace", enabledByDefault = false, tags = {"paisti", "choso", "skilling", "smithing"})
public class AutoBlastFurnacePlugin extends Plugin {
    @Inject
    AutoBlastFurnacePluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    @Inject
    PaistiBreakHandler paistiBreakHandler;
    ThreadedScriptRunner runner = new ThreadedScriptRunner();
    private final AtomicReference<Boolean> shouldEmptyDispenser = new AtomicReference<>(false);
    private final AtomicReference<Integer> foremanPaidOnTick = new AtomicReference<>(-1000000);
    private final WorldPoint BLAST_FURNACE_BANK_TILE = new WorldPoint(1948, 4957, 0);
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
    @Inject
    private AutoBlastFurnaceSceneOverlay sceneOverlay;
    @Inject
    private AutoBlastFurnacePluginScreenOverlay screenOverlay;

    @Inject
    OverlayManager overlayManager;

    @Provides
    public AutoBlastFurnacePluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoBlastFurnacePluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("PBlastFurnace: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }
        overlayManager.add(sceneOverlay);
        overlayManager.add(screenOverlay);

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });

        keyManager.registerKeyListener(startHotkeyListener);
        paistiBreakHandler.registerPlugin(this);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(screenOverlay);
        keyManager.unregisterKeyListener(startHotkeyListener);
        paistiBreakHandler.unregisterPlugin(this);
        runner.stop();
    }

    private void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "AutoBlastFurnace");
        }
        paistiBreakHandler.stopPlugin(this);
        runner.stop();
    }

    public int getBlastFurnaceCofferCoins() {
        return Utility.getVarbitValue(Varbits.BLAST_FURNACE_COFFER);
    }

    private void start() {
        Utility.sendGameMessage("Started", "AutoBlastFurnace");
        paistiBreakHandler.startPlugin(this);
        shouldEmptyDispenser.set(false);
        foremanPaidOnTick.set(-1000000);
        runner.start();
    }

    private boolean inventoryContainsOres() {
        return Inventory.search().nameContains("ore", "Coal").onlyUnnoted().filter(w -> !w.getName().contains("bag")).first().isPresent();
    }

    private boolean handleOpeningCoalBag() {
        var coalBagToOpen = Inventory.search().withName("Coal bag").first();
        if (coalBagToOpen.isPresent()) {
            if (Bank.isOpen()) {
                Bank.closeBank();
                Utility.sleepUntilCondition(() -> !Bank.isOpen());
            }
            Utility.sendGameMessage("Opening coal sack", "AutoBlastFurnace");
            Interaction.clickWidget(coalBagToOpen.get(), "Open");
            return Utility.sleepUntilCondition(() -> Inventory.search().withName("Coal bag").first().isEmpty());
        }
        return false;
    }


    private boolean handleBanking() {
        handlePayingForeman();
        handleCoffer();
        if (inventoryContainsOres()) return false;
        if (!Bank.isOpen()) {
            Bank.openBank();
        }

        if (!Utility.sleepUntilCondition(Bank::isOpen, 20000)) {
            Utility.sendGameMessage("Failed to open bank", "AutoBlastFurnace");
            return false;
        }

        if (!config.method().enoughMaterialsToContinue()) {
            Utility.sendGameMessage("Not enough materials to continue so stopping", "AutoBlastFurnace");
            stop();
            return false;
        }

        var depositItems = Inventory.search().result();
        HashSet<Integer> idsDeposited = new HashSet<>();
        for (var depositItem : depositItems) {
            if (depositItem.getName().toLowerCase().contains("coal bag")) {
                continue;
            }
            if (depositItem.getName().toLowerCase().contains("ice gloves")) {
                continue;
            }
            if (depositItem.getName().toLowerCase().contains("goldsmith gauntlets")) {
                continue;
            }
            if (idsDeposited.contains(depositItem.getId())) {
                continue;
            }
            if (Bank.depositAll(depositItem)) {
                idsDeposited.add(depositItem.getId());
                Utility.sleepGaussian(150, 250);
            }
        }

        if (!Utility.sleepUntilCondition(() -> Inventory.getItemAmountWildcard("*bar") == 0, 3000)) {
            Utility.sendGameMessage("Failed to deposit bars", "AutoBlastFurnace");
            return false;
        }

        if (Walking.getRunEnergy() < Utility.random(30, 50)) {
            if (Bank.withdraw("Stamina potion(1)", 1, false)) {
                if (Utility.sleepUntilCondition(() -> Inventory.getItemAmount("Stamina potion(1)") > 0, 3000)) {
                    var stamina = BankInventory.search().withName("Stamina potion(1)").first();
                    if (stamina.isPresent()) {
                        Interaction.clickWidget(stamina.get(), "Drink");
                        Utility.sleepUntilCondition(() -> Inventory.getItemAmount("Stamina potion(1)") == 0);
                    }
                    var vial = Inventory.search().withName("Vial").first();
                    if (vial.isPresent()) {
                        Bank.depositAll(vial.get());
                        Utility.sleepGaussian(700, 900);
                    }
                }
            }
        }

        if (config.method().handleWithdraw()) {
            shouldEmptyDispenser.set(true);
        }

        var conveyorBelt = TileObjects.search().withName("Conveyor belt").withAction("Put-ore-on").first();
        if (conveyorBelt.isEmpty()) {
            Utility.sendGameMessage("Could not find conveyor belt after banking", "AutoBlastFurnace");
            return false;
        }
        Interaction.clickTileObject(conveyorBelt.get(), "Put-ore-on");
        Utility.sleepGaussian(1200, 1800);

        return true;
    }

    private boolean handleConveyorBelt() {
        if (!inventoryContainsOres()) return false;
        var conveyorBelt = TileObjects.search().withName("Conveyor belt").withAction("Put-ore-on").first();

        if (conveyorBelt.isEmpty()) {
            Utility.sendGameMessage("Failed to find conveyor belt", "AutoBlastFurnace");
            return false;
        }

        if (Interaction.clickTileObject(conveyorBelt.get(), "Put-ore-on")) {
            Utility.sleepGaussian(600, 1800);
        }

        var iceGloves = Inventory.search().withName("Ice gloves").first();
        if (iceGloves.isPresent()) {
            Interaction.clickWidget(iceGloves.get(), "Wear", "Wield", "Equip");
            Utility.sleepGaussian(600, 1000);
            if (Interaction.clickTileObject(conveyorBelt.get(), "Put-ore-on")) {
                Utility.sleepUntilCondition(() -> !inventoryContainsOres(), 18000);
            }
        }

        if (!Utility.sleepUntilCondition(() -> !inventoryContainsOres(), 18000)) {
            Bank.openBank();
            Utility.sleepUntilCondition(Bank::isOpen);
            var depos = Inventory.search().withId(
                    ItemID.TIN_ORE,
                    ItemID.COPPER_ORE,
                    ItemID.IRON_ORE,
                    ItemID.SILVER_ORE,
                    ItemID.COAL,
                    ItemID.GOLD_ORE,
                    ItemID.MITHRIL_ORE,
                    ItemID.ADAMANTITE_ORE,
                    ItemID.RUNITE_ORE
            ).result();
            for (var depoItem : depos) {
                Bank.depositAll(depoItem);
                Utility.sleepUntilCondition(() -> Inventory.getItemAmount(depoItem.getItemId()) == 0);
            }
        }

        var coalBag = Inventory.search().matchesWildCardNoCase("Open coal bag").first();
        if (coalBag.isPresent()) {
            if (Interaction.clickWidget(coalBag.get(), "Empty")) {
                Utility.sleepUntilCondition(this::inventoryContainsOres, 3000);
                if (Interaction.clickTileObject(conveyorBelt.get(), "Put-ore-on")) {
                    Utility.sleepUntilCondition(() -> !inventoryContainsOres(), 18000);
                }
            }
        }

        if (!shouldEmptyDispenser.get() && TileObjects.search().withName("Bar dispenser").withAction("Take").empty()) {
            return true;
        }

        var dispenser = TileObjects.search().withName("Bar dispenser").first();
        if (dispenser.isEmpty()) {
            return false;
        }

        var tileToClick = dispenser.get().getWorldLocation().dy(-1);
        Walking.sceneWalk(tileToClick);
        Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(dispenser.get().getWorldLocation()) <= 1);
        Utility.sleepUntilCondition(() -> TileObjects.search().withName("Bar dispenser").withAction("Take").first().isPresent());
        if (Interaction.clickTileObject(dispenser.get(), "Take")) {
            if (!Utility.sleepUntilCondition(MakeInterface::isMakeInterfaceOpen, 6000, 200)) {
                Utility.sendGameMessage("Failed to open dispenser", "AutoBlastFurnace");
                return false;
            }
        }

        Utility.sleepGaussian(50, 150);
        Keyboard.pressSpacebar();
        Utility.sleepGaussian(50, 150);
        var goldSmithGauntlets = Inventory.search().withName("Goldsmith gauntlets").first();
        if (goldSmithGauntlets.isPresent()) {
            Interaction.clickWidget(goldSmithGauntlets.get(), "Wear", "Wield", "Equip");
            Utility.sleepGaussian(100, 250);
        }
        var bankChest = TileObjects.search().withName("Bank chest").withAction("Use").first();
        if (bankChest.isPresent()) {
            Interaction.clickTileObject(bankChest.get(), "Use");
            Utility.sleepGaussian(600, 800);
        }
        shouldEmptyDispenser.set(false);
        return true;
    }

    private boolean handleDispenserEmptying() {
        if (!BlastFurnaceContents.areBarsAvailable()) {
            return false;
        }
        if (Inventory.isFull()) {
            return false;
        }
        Utility.sendGameMessage("Emptying dispenser to prevent getting stuck", "AutoBlastFurnace");

        Inventory.search().withName("Ice gloves").onlyUnnoted().first().ifPresent(gloves -> {
            Interaction.clickWidget(gloves, "Wear", "Wield", "Equip");
            Utility.sleepUntilCondition(() -> !Equipment.search().withName("Ice Gloves").empty());
        });

        var dispenser = TileObjects.search().withName("Bar dispenser").withAction("Take").first();
        if (dispenser.isEmpty()) {
            Utility.sendGameMessage("No dispenser found", "AutoBlastFurnace");
            return false;
        }
        if (Interaction.clickTileObject(dispenser.get(), "Take")) {
            if (!Utility.sleepUntilCondition(MakeInterface::isMakeInterfaceOpen, 6000, 200)) {
                Utility.sendGameMessage("Failed to open dispenser", "AutoBlastFurnace");
            }
        }
        Keyboard.pressSpacebar();
        Utility.sleepGaussian(1200, 1400);
        if (Inventory.getItemAmountWildcard("*bar") == 0) {
            Utility.sendGameMessage("Failed to get bars from dispenser", "AutoBlastFurnace");
            stop();
        }
        return true;
    }

    private boolean handlePayingForeman() {
        if (Utility.getRealSkillLevel(Skill.SMITHING) >= 60) return false;
        if (Utility.getTickCount() - foremanPaidOnTick.get() < 900) return false;
        if (!Bank.isOpen()) {
            Bank.openBank();
            if (!Utility.sleepUntilCondition(Bank::isOpen)) {
                Utility.sendGameMessage("Could not find bank when attempting to pay the foreman", "AutoBlastFurnace");
                stop();
                return false;
            }
        }
        if (Inventory.isFull()) {
            var item = Inventory.search().nameContains("Coal", "ore", "bar").filter(w -> !w.getName().contains("bag")).first();
            if (item.isEmpty()) {
                Utility.sendGameMessage("Failed to deposit item for withdrawing coins", "AutoBlastFurnace");
                stop();
                return false;
            }
            Bank.depositAll(item.get());
            Utility.sleepGaussian(100, 200);
        }
        Bank.withdraw(995, 2500);
        if (!Utility.sleepUntilCondition(() -> Inventory.getItemAmount(995) >= 2500)) {
            Utility.sendGameMessage("Failed to withdraw coins", "AutoBlastFurnace");
            stop();
            return false;
        }

        var foreman = NPCs.search().withAction("Pay").first();
        if (foreman.isEmpty()) {
            Utility.sendGameMessage("Failed to find foreman", "AutoBlastFurnace");
            stop();
            return false;
        }
        Interaction.clickNpc(foreman.get(), "Pay");
        if (!Utility.sleepUntilCondition(Dialog::isConversationWindowUp)) {
            Utility.sendGameMessage("Failed to open conversation window", "AutoBlastFurnace");
            stop();
            return false;
        }
        var coinsBefore = Inventory.getItemAmount(995);
        Dialog.handleGenericDialog(new String[]{"yes", "pay"});

        if (coinsBefore > Inventory.getItemAmount(995)) {
            foremanPaidOnTick.set(Utility.getTickCount());
            return true;
        }
        Utility.sendGameMessage("Failed to pay foreman", "AutoBlastFurnace");
        return false;
    }

    private boolean handleCoffer() {
        if (getBlastFurnaceCofferCoins() > 5000) return false;
        if (!Bank.isOpen()) {
            Bank.openBank();
            if (!Utility.sleepUntilCondition(Bank::isOpen)) {
                Utility.sendGameMessage("Could not find bank when attempting to fill coffer", "AutoBlastFurnace");
                stop();
                return false;
            }
        }
        if (Inventory.isFull()) {
            var item = Inventory.search().nameContains("Coal", "ore", "bar").filter(w -> !w.getName().contains("bag")).first();
            if (item.isEmpty()) {
                Utility.sendGameMessage("Failed to deposit item for withdrawing coins for coffer", "AutoBlastFurnace");
                stop();
                return false;
            }
            Bank.depositAll(item.get());
            Utility.sleepGaussian(100, 200);
        }
        Bank.withdraw(995, 200000);
        if (!Utility.sleepUntilCondition(() -> Inventory.getItemAmount(995) >= 200000)) {
            Utility.sendGameMessage("Failed to withdraw coins for coffer", "AutoBlastFurnace");
            stop();
            return false;
        }

        var coffer = TileObjects.search().withName("Coffer").withAction("Use").first();
        if (coffer.isEmpty()) {
            Utility.sendGameMessage("Failed to find coffer", "AutoBlastFurnace");
            stop();
            return false;
        }
        Interaction.clickTileObject(coffer.get(), "Use");
        if (!Utility.sleepUntilCondition(Dialog::isConversationWindowUp)) {
            Utility.sendGameMessage("Failed to open conversation window", "AutoBlastFurnace");
            stop();
            return false;
        }
        var coinsBefore = Inventory.getItemAmount(995);
        Dialog.handleGenericDialog(new String[]{"deposit"});
        Utility.sleepGaussian(1200, 1800);
        Keyboard.typeString("200000");
        Utility.sleepGaussian(1200, 1800);
        Keyboard.pressEnter();
        if (!Utility.sleepUntilCondition(() -> Inventory.getItemAmount(995) < coinsBefore)) {
            Utility.sendGameMessage("Failed to put coins into coffer", "AutoBlastFurnace");
            stop();
            return false;
        }
        return true;
    }

    private boolean handleLoadout() {
        return config.method().handleLoadout();
    }

    private static final List<Integer> BLAST_FURNACE_WORLD_IDS = List.of(
            352,
            355,
            356,
            357,
            358,
            386,
            387,
            395,
            424,
            466,
            494,
            495,
            496,
            515,
            516
    );

    private boolean handleWorldhop() {
        int worldId = Utility.getWorldId();
        if (BLAST_FURNACE_WORLD_IDS.stream().noneMatch(wid -> worldId == wid)) {
            Utility.sendGameMessage("Hopping to a Blast Furnace world", "AutoBlastFurnace");
            Worldhopping.hopToWorldId(BLAST_FURNACE_WORLD_IDS.get(Utility.random(0, BLAST_FURNACE_WORLD_IDS.size() - 1)));
            return true;
        }
        return false;
    }

    private void threadedLoop() {
        if (paistiBreakHandler.shouldBreak(this)) {
            Utility.sendGameMessage("Taking a break", "AutoBlastFurnace");
            foremanPaidOnTick.set(-1000000);
            Utility.sleepGaussian(2000, 3000);
            paistiBreakHandler.startBreak(this);
            Utility.sleepGaussian(1000, 2000);
            Utility.sleepUntilCondition(() -> !paistiBreakHandler.isBreakActive(this) && Utility.isLoggedIn(), 99999999, 5000);
            return;
        }
        if (Walking.getPlayerLocation().distanceTo(BLAST_FURNACE_BANK_TILE) > 50 && !WebWalker.walkTo(BLAST_FURNACE_BANK_TILE)) {
            Utility.sendGameMessage("Unable to walk to blast furnace", "AutoBlastFurnace");
            stop();
            return;
        }
        if (handleWorldhop()) {
            Utility.sleepGaussian(150, 250);
            return;
        }
        if (config.autoGear() && handleLoadout()) {
            Utility.sleepGaussian(150, 250);
            return;
        }
        if (handleOpeningCoalBag()) {
            Utility.sleepGaussian(150, 250);
            return;
        }
        if (handleDispenserEmptying()) {
            Utility.sleepGaussian(150, 250);
            return;
        }
        if (handleEnableRun()) {
            Utility.sleepGaussian(150, 250);
            return;
        }
        //Inventory.search().withName("Coal bag").first().ifPresent(bag -> Interaction.clickWidget(bag, "Open"));
        if (handleBanking()) {
            Utility.sleepGaussian(150, 250);
            return;
        }
        if (handleConveyorBelt()) {
            Utility.sleepGaussian(150, 250);
            return;
        }

        Utility.sleepGaussian(150, 250);
    }

    private boolean handleEnableRun() {
        if (Walking.getRunEnergy() > Utility.random(15, 25) && !Walking.isRunEnabled()) {
            Walking.setRun(true);
            return true;
        }
        return false;
    }

    public Duration getRunTimeDuration() {
        var started = runner.getStartedAt();
        if (started == null) {
            return Duration.ZERO;
        }
        return Duration.between(started, Instant.now());
    }

    public boolean isRunning() {
        return runner.isRunning();
    }
}
