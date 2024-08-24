package com.theplug.ColosseumFarmerPlugin.States;

import com.theplug.ColosseumFarmerPlugin.ColosseumFarmerPlugin;
import com.theplug.ColosseumFarmerPlugin.ColosseumFarmerPluginConfig;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.NPCTickSimulation.NPCTickSimulation;
import com.theplug.PaistiUtils.API.Potions.BoostPotion;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.API.Spells.Ancient;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import jdk.jshell.execution.Util;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class FightColosseum implements State {
    ColosseumFarmerPlugin plugin;
    ColosseumFarmerPluginConfig config;
    private final AtomicReference<WorldPoint> centerTile = new AtomicReference<>(null);
    private final AtomicReference<Boolean> shouldMage = new AtomicReference<>(true);
    private final AtomicReference<Boolean> isFremennikTrioAlive = new AtomicReference<>(true);
    private static final AtomicReference<Integer> lastAteOnTick = new AtomicReference<>(-1);
    private static final AtomicReference<Integer> lastDrankOnTick = new AtomicReference<>(-1);
    private static final AtomicReference<Integer> clickedAttackOnTick = new AtomicReference<>(-1);
    private int nextPrayerPotionAt = -1;
    private int nextEatAtHp = -1;

    private final String WAVE_COMPLETED_MESSAGE = "wave 1 completed";

    private static final List<PPrayer> offensivePrayers = List.of(
            PPrayer.RIGOUR,
            PPrayer.EAGLE_EYE,
            PPrayer.AUGURY,
            PPrayer.MYSTIC_MIGHT,
            PPrayer.PIETY,
            PPrayer.CHIVALRY,
            PPrayer.ULTIMATE_STRENGTH,
            PPrayer.INCREDIBLE_REFLEXES,
            PPrayer.STEEL_SKIN
    );

    private static final List<Integer> possibleTargets = List.of(
            NpcID.FREMENNIK_WARBAND_BERSERKER,
            NpcID.FREMENNIK_WARBAND_ARCHER,
            NpcID.FREMENNIK_WARBAND_SEER,
            NpcID.SERPENT_SHAMAN,
            NpcID.JAGUAR_WARRIOR
    );

    private static final List<Integer> fremennikTrio = List.of(
            NpcID.FREMENNIK_WARBAND_BERSERKER,
            NpcID.FREMENNIK_WARBAND_ARCHER,
            NpcID.FREMENNIK_WARBAND_SEER
    );


    public FightColosseum(ColosseumFarmerPlugin plugin, ColosseumFarmerPluginConfig config) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.nextPrayerPotionAt = generateNextPrayerPotAt();
        this.nextEatAtHp = generateNextEatAtHp();
    }

    private void setFremennikTrioAlive() {
        var trioNpcs = NPCs.search().withIdInArr(NpcID.FREMENNIK_WARBAND_BERSERKER, NpcID.FREMENNIK_WARBAND_SEER, NpcID.FREMENNIK_WARBAND_ARCHER).alive().result();
        if (trioNpcs.isEmpty()) {
            isFremennikTrioAlive.set(false);
        } else {
            isFremennikTrioAlive.set(true);
        }
    }

    private NPC getTarget() {
        List<NPC> targetNPCs = NPCs.search().alive().result().stream()
                .filter(npc -> possibleTargets.contains(npc.getId()))
                .sorted(Comparator.comparingInt(npc -> possibleTargets.indexOf(npc.getId())))
                .collect(Collectors.toList());
        return targetNPCs.isEmpty() ? null : targetNPCs.get(0);
    }

    private void updateCenterTile() {
        var gate = TileObjects.search().withId(plugin.GATE_TILE_OBJECT_ID).first();
        gate.ifPresent(tileObject -> centerTile.set(tileObject.getWorldLocation().dx(16).dy(-1)));
    }

    private boolean handleSpec() {
        if (!plugin.config.useSpecialAttack()) return false;
        var target = getTarget();
        if (target == null || target.getId() != NpcID.SERPENT_SHAMAN) return false;
        if (Utility.getSpecialAttackEnergy() >= plugin.config.specEnergyMinimum() && !Utility.isSpecialAttackEnabled()) {
            Utility.specialAttack();
            return Utility.sleepUntilCondition(Utility::isSpecialAttackEnabled, 1200, 300);
        }
        return false;
    }

    public WorldPoint getOptimalTile() {
        return Utility.runOnClientThread(() -> {
            var playerLoc = Walking.getPlayerLocation();
            LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
            List<WorldPoint> tiles = new ArrayList<>();
            var target = getTarget();
            for (var dx = -6; dx <= 6; dx++) {
                for (var dy = -6; dy <= 6; dy++) {
                    var tile = playerLoc.dx(dx).dy(dy);
                    if (!reachabilityMap.isReachable(tile)) continue;
                    tiles.add(tile);
                }
            }

            if (tiles.isEmpty()) {
                Utility.sendGameMessage("No tiles available", "ColosseumFarmer");
                return null;
            }

            var trioNpcs = NPCs.search().withIdInArr(NpcID.FREMENNIK_WARBAND_BERSERKER, NpcID.FREMENNIK_WARBAND_SEER, NpcID.FREMENNIK_WARBAND_ARCHER).alive().result();
            tiles.sort(Comparator.comparingDouble(t -> {
                double cannotReachTargetCost = target != null && t.distanceTo(target.getWorldArea()) > plugin.attackTickTracker.getPlayerAttackRange() + 1 ? 100000 : 0;

                double noLineOfSightCost = (target != null && !t.toWorldArea().hasLineOfSightTo(PaistiUtils.getClient().getTopLevelWorldView(), target.getWorldArea())) ? 10000 : 0;

                double closestFremennikDist = trioNpcs.stream().mapToDouble(n -> Geometry.distanceToHypotenuse(t, n.getWorldLocation())).min().orElse(15);
                double fremennikProximityCost = Math.max(0, (2.5 - closestFremennikDist)) * (1500.0 / 2.5);

                double distanceFromPlayerCost = Math.floor((double) reachabilityMap.getCostTo(t) / (Walking.isRunEnabled() ? 2.0 : 1.0)) * 500.0;
                double isNotPlayerTileCost = !t.equals(Walking.getPlayerLocation()) ? 10 : 0;
                //double distanceFromCenterCost = (centerTile.get() != null ? Math.max((double) t.distanceTo(centerTile.get()) - 8.0, 0) : 0) * 10.0;

                return cannotReachTargetCost
                        + fremennikProximityCost
                        + distanceFromPlayerCost
                        //+ distanceFromCenterCost
                        + noLineOfSightCost
                        + isNotPlayerTileCost;
            }));

            return tiles.get(0);
        });
    }


    private boolean canDrinkThisTick() {
        int currTick = Utility.getTickCount();
        if (currTick - lastDrankOnTick.get() < 3) return false;
        if (currTick - lastAteOnTick.get() < 3 && lastAteOnTick.get() != 0) return false;
        return true;
    }

    private boolean canEatThisTick() {
        int currTick = Utility.getTickCount();
        if (currTick - lastAteOnTick.get() < 3) return false;
        if (currTick - lastDrankOnTick.get() < 3) return false;
        return true;
    }

    private boolean handlePrayerRestore() {
        if (!canDrinkThisTick()) return false;
        if (Utility.getBoostedSkillLevel(Skill.PRAYER) <= nextPrayerPotionAt) {
            BoostPotion prayerBoostPot = BoostPotion.PRAYER_POTION.findInInventoryWithLowestDose().isEmpty() ? BoostPotion.SUPER_RESTORE : BoostPotion.PRAYER_POTION;
            var potionInInventory = prayerBoostPot.findInInventoryWithLowestDose();
            if (potionInInventory.isPresent()) {
                var clicked = Interaction.clickWidget(potionInInventory.get(), "Drink");
                if (clicked) {
                    nextPrayerPotionAt = generateNextPrayerPotAt();
                    lastDrankOnTick.set(Utility.getTickCount());
                }
                return clicked;
            }
        }
        return false;
    }

    private void simulateAndSetPrayers() {
        var mostImportantPray = getMostImportantPrayer();
        boolean didPray = false;
        log.debug("simulateAndSetPrayers");
        if (mostImportantPray == null) {
            if (PPrayer.PROTECT_FROM_MELEE.isActive() && PPrayer.PROTECT_FROM_MELEE.setEnabledWithoutClicks(false)) {
                log.debug("Disabled melee prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
            } else if (PPrayer.PROTECT_FROM_MISSILES.isActive() && PPrayer.PROTECT_FROM_MISSILES.setEnabledWithoutClicks(false)) {
                log.debug("Disabled ranged prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
            } else if (PPrayer.PROTECT_FROM_MAGIC.isActive() && PPrayer.PROTECT_FROM_MAGIC.setEnabledWithoutClicks(false)) {
                log.debug("Disabled magic prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
            }
        } else {
            switch (mostImportantPray.getAttackType()) {
                case MELEE:
                    didPray = !PPrayer.PROTECT_FROM_MELEE.isActive() && PPrayer.PROTECT_FROM_MELEE.setEnabledWithoutClicks(true);
                    if (didPray) {
                        log.debug("Enabled melee prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
                    }
                    break;
                case MAGIC:
                    didPray = !PPrayer.PROTECT_FROM_MAGIC.isActive() && PPrayer.PROTECT_FROM_MAGIC.setEnabledWithoutClicks(true);
                    if (didPray) {
                        log.debug("Enabled magic prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
                    }
                    break;
                case RANGED:
                    didPray = !PPrayer.PROTECT_FROM_MISSILES.isActive() && PPrayer.PROTECT_FROM_MISSILES.setEnabledWithoutClicks(true);
                    if (didPray) {
                        log.debug("Enabled ranged prayer {}, {}", Utility.getTickCount(), Utility.getMsSinceStartOfTick());
                    }
                    break;
            }
        }
    }

    private NPCTickSimulation.PrayAgainstResult getMostImportantPrayer() {
        return Utility.runOncePerClientTickTask(() -> {
            var client = PaistiUtils.getClient();
            var relevantNpcs = NPCs.search().withinDistance(20).result();

            var _tickSimulation = new NPCTickSimulation(client, plugin.attackTickTracker, relevantNpcs);
            _tickSimulation.getPlayerState().setInteracting(plugin.attackTickTracker.getPredictedInteractionTarget());
            List<NPCTickSimulation.PrayAgainstResult> prayThisTick = new ArrayList<>();

            _tickSimulation.simulateNpcsTick(client);
            var prayAgainst = _tickSimulation.shouldPrayAgainst(client);
            if (prayAgainst != null) {
                prayThisTick.add(prayAgainst);
            }

            _tickSimulation.simulatePlayerTick(client);

            prayAgainst = _tickSimulation.shouldPrayAgainst(client);
            if (prayAgainst != null) {
                prayThisTick.add(prayAgainst);
            }

            return prayThisTick.isEmpty() ? null : prayThisTick.stream().max(Comparator.comparingInt(NPCTickSimulation.PrayAgainstResult::getPriority)).get();
        });
    }

    @Override
    public void threadedOnGameTick() {
        Utility.sleep(60);
        if (Utility.getTickCount() != clickedAttackOnTick.get()) {
            setOffensivePrayers();
        }
        Utility.sleep(60);
        var target = getTarget();
        if (target != null && target.getId() == NpcID.JAGUAR_WARRIOR) {
            if (!PPrayer.PROTECT_FROM_MELEE.isActive()) {
                PPrayer.PROTECT_FROM_MELEE.setEnabledWithoutClicks(true);
            }
        } else {
            simulateAndSetPrayers();
        }
    }

    private void setOffensivePrayers() {
        if (Utility.getInteractionTarget() instanceof NPC && getTarget() != null) {
            var npc = (NPC) Utility.getInteractionTarget();
            if (!npc.isDead() && plugin.attackTickTracker.getTicksUntilNextAttack() <= 1) {
                var target = getTarget();
                if (target.getId() == NpcID.FREMENNIK_WARBAND_BERSERKER) {
                    enableOffensiveMagePray(false);
                    return;
                } else {
                    enableOffensiveRangePray(false);
                    return;
                }
            }
        }
        disableAllOffensivePrayers();
    }

    private void setOffensivePrayersManual() {
        log.debug("Manual offensive prayer");
        var target = getTarget();
        if (target.getId() == NpcID.FREMENNIK_WARBAND_BERSERKER && Equipment.search().nameContains("blowpipe").empty()) {
            enableOffensiveMagePray(false);
        } else {
            enableOffensiveRangePray(false);
        }
        clickedAttackOnTick.set(Utility.getTickCount());
    }

    private boolean disableAllOffensivePrayers() {
        var didDisable = false;
        for (var prayer : offensivePrayers) {
            if (prayer.isActive()) {
                prayer.setEnabledWithoutClicks(false);
                didDisable = true;
                Utility.sleepGaussian(10, 30);
            }
        }

        return didDisable;
    }

    private boolean enableOffensiveRangePray(boolean allowThickSkin) {
        if (PPrayer.RIGOUR.canUse()) {
            if (!PPrayer.RIGOUR.isActive()) {
                PPrayer.RIGOUR.setEnabledWithoutClicks(true);
                return true;
            }
        } else {
            var toggled = false;
            if (!PPrayer.EAGLE_EYE.isActive()) {
                toggled = PPrayer.EAGLE_EYE.setEnabledWithoutClicks(true);
            }
            if (allowThickSkin) {
                if (!PPrayer.STEEL_SKIN.isActive()) {
                    if (toggled) Utility.sleepGaussian(10, 30);
                    toggled = PPrayer.STEEL_SKIN.setEnabledWithoutClicks(true) || toggled;
                }
            }
            return toggled;
        }
        return false;
    }

    private boolean enableOffensiveMagePray(boolean allowThickSkin) {
        if (PPrayer.AUGURY.canUse()) {
            if (!PPrayer.AUGURY.isActive()) {
                PPrayer.AUGURY.setEnabledWithoutClicks(true);
                return true;
            }
        } else {
            var toggled = false;
            if (!PPrayer.MYSTIC_MIGHT.isActive()) {
                toggled = PPrayer.MYSTIC_MIGHT.setEnabledWithoutClicks(true);
            }
            if (allowThickSkin) {
                if (!PPrayer.STEEL_SKIN.isActive()) {
                    if (toggled) Utility.sleepGaussian(10, 30);
                    toggled = PPrayer.STEEL_SKIN.setEnabledWithoutClicks(true) || toggled;
                }
            }
            return toggled;
        }
        return false;
    }

    private List<Widget> getFoodItems() {
        return Inventory.search().onlyUnnoted().filter((item) -> plugin.foodStats.getHealAmount(item.getItemId()) >= 8).result();
    }

    private boolean eatFood() {
        var foodItem = getFoodItems().stream().findFirst();
        return foodItem.filter(widget -> Interaction.clickWidget(widget, "Eat", "Drink")).isPresent();
    }

    private boolean handleEating() {
        if (!canEatThisTick()) return false;
        var isBelowHpTreshold = Utility.getBoostedSkillLevel(Skill.HITPOINTS) <= nextEatAtHp;
        if (isBelowHpTreshold) {
            if (eatFood()) {
                nextEatAtHp = generateNextEatAtHp();
                lastAteOnTick.set(Utility.getTickCount());
                return true;
            }
        }
        return false;
    }

    private boolean handleAttacking() {
        var target = getTarget();
        if (target == null) return false;
        if (Utility.getInteractionTarget() == target) return false;
        boolean attackReady = plugin.attackTickTracker.getTicksUntilNextAttack() <= 1;
        if (!attackReady) return false;

        if (target.getId() == NpcID.FREMENNIK_WARBAND_BERSERKER) {
            if (Ancient.ICE_BARRAGE.canCast()) {
                return Ancient.ICE_BARRAGE.castOnNpc(target);
            }
            if (Ancient.ICE_BURST.canCast()) {
                return Ancient.ICE_BURST.castOnNpc(target);
            }
        }

        if (Interaction.clickNpc(target, "Attack")) {
            if (attackReady && Walking.getPlayerLocation().distanceTo(target.getWorldArea()) <= plugin.attackTickTracker.getPlayerAttackRange() + 1) {
                setOffensivePrayersManual();
            }
            return Utility.sleepUntilCondition(() -> Utility.getInteractionTarget() == target, 600, 50);
        }
        return true;
    }

    private boolean handleGearSwitch() {
        var target = getTarget();
        if (target == null || target.getId() == NpcID.FREMENNIK_WARBAND_BERSERKER) {
            if (!plugin.getMageGear().isSatisfied(true)) {
                return plugin.getMageGear().handleSwitchTurbo();
            }
        } else if (!plugin.getRangeGear().isSatisfied(true)) {
            return plugin.getRangeGear().handleSwitchTurbo();
        }
        return false;
    }

    private boolean handleMoving() {
        if (!isFremennikTrioAlive.get()) return false;
        var opTile = getOptimalTile();
        if (opTile == null) return false;
        var playerLoc = Walking.getPlayerLocation();
        if (playerLoc.equals(opTile)) return false;
        Walking.sceneWalk(opTile);
        return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().equals(opTile), 600, 50);
    }

    private boolean handlePrepotting() {
        if (!canDrinkThisTick()) return false;
        if (!Utility.isIdle()) return false;
        if (Utility.getBoostedSkillLevel(Skill.RANGED) < Utility.getRealSkillLevel(Skill.RANGED) + 5) {
            var potionToDrink = Utility.runOnClientThread(() -> Arrays.stream(BoostPotion.values()).filter(potion -> potion.findBoost(Skill.RANGED) != null).findFirst());

            if (potionToDrink == null || potionToDrink.isEmpty()) return false;

            if (potionToDrink.get().drink()) {
                lastDrankOnTick.set(Utility.getTickCount());
                return true;
            }
        }

        return false;
    }

    private boolean handleRunning() {
        if (isFremennikTrioAlive.get()) {
            if (Walking.isRunEnabled()) {
                return Walking.setRun(false);
            }
        } else if (!isFremennikTrioAlive.get() && !Walking.isRunEnabled()) {
            return Walking.setRun(true);
        }
        return false;
    }

    @Override
    public String name() {
        return "Fight";
    }

    @Override
    public boolean shouldExecuteState() {
        return plugin.insideColosseum() && !plugin.isMinimusPresent() && Utility.getBoostedSkillLevel(Skill.HITPOINTS) > 0;
    }

    @Override
    public void threadedLoop() {
        if (handleGearSwitch()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handlePrayerRestore()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleEating()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleSpec()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleAttacking()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handleMoving()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handlePrepotting()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        Utility.sleepGaussian(50, 100);
    }

    @Subscribe
    private void onGameTick(GameTick e) {
        if (!shouldExecuteState()) return;
        setFremennikTrioAlive();
        //updateOptimalTile();
        updateCenterTile();
    }

    private int generateNextPrayerPotAt() {
        return Utility.random(10, 20);
    }

    private int generateNextEatAtHp() {
        return Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.random(30, 55);
    }

    @Subscribe(priority = 5000)
    public void onActorDeath(ActorDeath actorDeath) {
        if (!plugin.runner.isRunning()) return;
        Actor actor = actorDeath.getActor();
        if (actor instanceof NPC) {
            if (((NPC) actor).getId() == NpcID.JAGUAR_WARRIOR) {
                shouldMage.set(true);
            }
        }
    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;
        if (event.getMessage().toLowerCase().contains(WAVE_COMPLETED_MESSAGE.toLowerCase())) {
            shouldMage.set(true);
        }
    }
}
