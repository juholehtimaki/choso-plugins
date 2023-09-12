package com.PaistiPlugins.AutoNexPlugin;

import com.PaistiPlugins.PaistiUtils.API.*;
import com.PaistiPlugins.PaistiUtils.API.Potions.BoostPotion;
import com.PaistiPlugins.PaistiUtils.API.Prayer.PPrayer;
import com.PaistiPlugins.PaistiUtils.PaistiUtils;
import com.PaistiPlugins.PaistiUtils.PathFinding.LocalPathfinder;
import com.PaistiPlugins.VorkathKillerPlugin.States.State;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Skill;

import java.util.*;

@Slf4j
public class PrepareState implements State {
    AutoNexPlugin plugin;

    public PrepareState(AutoNexPlugin plugin) {
        super();
        this.plugin = plugin;
        this.nextEatAtHp = generateNextEatAtHp();
        this.nextPrayerPotionAt = generateNextPrayerPotAt();
    }

    private int nextEatAtHp;
    private int nextPrayerPotionAt;
    private long lastAteAt = 0;
    private static final int FOOD_DELAY = 1800;

    private static final int SPIRITUAL_RANGER_ID = 11291;

    private static final int ANCIENT_DOOR = 42934;
    private static final int EXIT_DOOR = 42933;

    @Override
    public String name() {
        return "Preparation";
    }

    public int generateNextEatAtHp() {
        return Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.random(25, 35);
    }

    public int generateNextPrayerPotAt() {
        return Utility.random(30, 50);
    }

    public int getAncientKc() {
        var gwdKcWidget = Widgets.getWidget(406, 18);
        if (gwdKcWidget == null) return 0;
        return Integer.parseInt(gwdKcWidget.getText());
    }

    public boolean isSpiritualRangePresentAndReachable() {
        var spiritualRanger = NPCs.search().withId(SPIRITUAL_RANGER_ID).nearestToPlayer();
        if (spiritualRanger.isEmpty()) return false;
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        return reachabilityMap.isReachable(spiritualRanger.get().getWorldLocation());
    }

    @Override
    public boolean shouldExecuteState() {
        return plugin.playerInsideGodWars() && !plugin.isInsideNexRoom();
    }

    @Override
    public void threadedOnGameTick() {
        //Utility.sleepGaussian(50, 150);
        //Utility.sleepGaussian(150, 200);
    }

    public boolean handleStatBoostPotions() {
        if (System.currentTimeMillis() - lastAteAt < FOOD_DELAY) return false;
        if (Utility.getBoostedSkillLevel(Skill.RANGED) > 105) {
            return false;
        }
        var potionToDrink = Utility.runOnClientThread(() -> Arrays.stream(BoostPotion.values()).filter(potion -> potion.findBoost(Skill.RANGED) != null).findFirst());

        if (potionToDrink == null || potionToDrink.isEmpty()) return false;

        if (potionToDrink.get().drink()) {
            Utility.sendGameMessage("Drank " + potionToDrink.get().name(), "AutoNex");
            lastAteAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public boolean toggleRun() {
        if (Walking.isRunEnabled()) return false;
        if (Walking.getRunEnergy() > 20) {
            Walking.setRun(true);
            return true;
        }
        return false;
    }

    private boolean handlePrayer() {
        if (System.currentTimeMillis() - lastAteAt < FOOD_DELAY) return false;
        if (Utility.getBoostedSkillLevel(Skill.PRAYER) <= nextPrayerPotionAt) {
            BoostPotion prayerBoostPot = BoostPotion.PRAYER_POTION.findInInventory().isEmpty() ? BoostPotion.SUPER_RESTORE : BoostPotion.PRAYER_POTION;
            var potionInInventory = prayerBoostPot.findInInventory();
            if (potionInInventory.isPresent()) {
                var clicked = Interaction.clickWidget(potionInInventory.get(), "Drink");
                if (clicked) {
                    nextPrayerPotionAt = generateNextPrayerPotAt();
                    lastAteAt = System.currentTimeMillis();
                }
                return clicked;
            }
        }
        return false;
    }

    public boolean handleEmergencyExit() {
        boolean isLowHp = Utility.getBoostedSkillLevel(Skill.HITPOINTS) < 50;
        boolean isLowPrayer = Utility.getBoostedSkillLevel(Skill.PRAYER) < 20;
        if (isLowHp || isLowPrayer) {
            if (isLowHp) {
                var brews = Inventory.search().matchesWildCardNoCase("Saradomin brew*");
                var foods = plugin.getFoodItems().stream().findFirst();
                if (brews.empty() && foods.isEmpty()) {
                    Utility.sendGameMessage("Attempting to exit kc room due to low HP and no food left");
                    var door = TileObjects.search().withId(EXIT_DOOR).withAction("Open").nearestToPlayer();
                    if (door.isEmpty()) {
                        Utility.sendGameMessage("Could not find exit door");
                        return false;
                    }
                    Interaction.clickTileObject(door.get(), "Open");
                    return Utility.sleepUntilCondition(() -> {
                        var chest = TileObjects.search().withName("Chest").withAction("Claim").nearestToPlayer();
                        if (chest.isEmpty()) return false;
                        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
                        return reachabilityMap.isReachable(chest.get());
                    }, 10000, 200);
                }
            }
            if (isLowPrayer) {
                var restorePotions = Inventory.search().matchesWildCardNoCase("Super restore*");
                var prayerPotions = Inventory.search().matchesWildCardNoCase("Prayer potion*");
                if (restorePotions.empty() && prayerPotions.empty()) {
                    Utility.sendGameMessage("Attempting to exit kc room due to low prayer and no prayer pots left");
                    var door = TileObjects.search().withId(EXIT_DOOR).withAction("Open").nearestToPlayer();
                    if (door.isEmpty()) {
                        Utility.sendGameMessage("Could not find exit door");
                        return false;
                    }
                    Interaction.clickTileObject(door.get(), "Open");
                    return Utility.sleepUntilCondition(() -> {
                        var chest = TileObjects.search().withName("Chest").withAction("Claim").nearestToPlayer();
                        if (chest.isEmpty()) return false;
                        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
                        return reachabilityMap.isReachable(chest.get());
                    }, 10000, 200);
                }
            }
        }
        return false;
    }

    private boolean handleEating() {
        if (System.currentTimeMillis() - lastAteAt < FOOD_DELAY) return false;
        var hp = Utility.getBoostedSkillLevel(Skill.HITPOINTS);
        if (hp <= nextEatAtHp) {
            if (eatFood()) {
                nextEatAtHp = generateNextEatAtHp();
                lastAteAt = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    public boolean eatFood() {
        var foodItems = plugin.getFoodItems();
        if (foodItems.isEmpty()) return false;
        Utility.sendGameMessage("Eating " + foodItems.get(0).getName(), "AutoNex");
        return Interaction.clickWidget(foodItems.get(0), "Eat", "Drink");
    }

    public boolean currentlyInterActingWithNpc() {
        var client = PaistiUtils.getClient();
        var spiritualRangers = NPCs.search().withId(SPIRITUAL_RANGER_ID).withAction("Attack").filter(npc -> npc.getInteracting() == client.getLocalPlayer()).result();
        if (spiritualRangers.isEmpty() && Utility.getInteractionTarget() == null) {
            return false;
        }
        return true;
    }

    public boolean handlePrayers() {
        if (currentlyInterActingWithNpc() && (!PPrayer.RIGOUR.isActive() || !PPrayer.PROTECT_FROM_MISSILES.isActive())) {
            PPrayer.RIGOUR.setEnabled(true);
            PPrayer.PROTECT_FROM_MISSILES.setEnabled(true);
            return true;
        } else if (!currentlyInterActingWithNpc() && (PPrayer.RIGOUR.isActive() || PPrayer.PROTECT_FROM_MISSILES.isActive())) {
            PPrayer.RIGOUR.setEnabled(false);
            PPrayer.PROTECT_FROM_MISSILES.setEnabled(false);
            return true;
        }
        return false;
    }

    public boolean handleAttackSpiritualRanger() {
        var target = Utility.getInteractionTarget();
        var client = PaistiUtils.getClient();
        if (target instanceof NPC) {
            NPC npc = (NPC) target;
            Actor npcTarget = npc.getInteracting();
            if (npc.getId() == SPIRITUAL_RANGER_ID && !npc.isDead() && (npcTarget == client.getLocalPlayer() || npcTarget == null)) {
                return false;
            }
        }
        var spiritualRanger = NPCs.search()
                .withId(SPIRITUAL_RANGER_ID)
                .withAction("Attack")
                .alive()
                .filter(npc -> npc.getInteracting() == null || npc.getInteracting() == client.getLocalPlayer())
                .result();

        spiritualRanger.sort(Comparator.comparingInt((NPC npc) -> {
            if (npc.getInteracting() == client.getLocalPlayer()) {
                return 0;
            }
            return 1;
        }).thenComparingInt((NPC npc) -> npc.getWorldLocation().distanceTo(Walking.getPlayerLocation())));

        if (spiritualRanger.isEmpty()) return false;
        if (Interaction.clickNpc(spiritualRanger.get(0), "Attack")) {
            Utility.sleepUntilCondition(() -> Utility.getInteractionTarget() == spiritualRanger.get(0), 1200, 50);
            return true;
        }
        return false;
    }

    public boolean handleEnterNex() {
        var ancientDoor = TileObjects.search().withId(ANCIENT_DOOR).withAction("Open").nearestToPlayer();
        if (ancientDoor.isEmpty()) {
            Utility.sendGameMessage("Could not find door!", "AutoNex");
            return false;
        }
        Utility.sendGameMessage("Opening door!", "AutoNex");
        Interaction.clickTileObject(ancientDoor.get(), "Open");
        return Utility.sleepUntilCondition(() -> getAncientKc() < 40, 10000, 600);
    }

    public boolean isBankReachAble() {
        var bank = NPCs.search().withName("Ashuelot Reis").withAction("Bank").first();
        var reachabilityMap = LocalPathfinder.getReachabilityMap();
        if (bank.isEmpty()) {
            return false;
        }
        if (reachabilityMap.isReachable(bank.get())) {
            return true;
        }
        return false;
    }

    @Override
    public void threadedLoop() {
        if (isBankReachAble()) {
            Utility.sendGameMessage("Bank reached!", "AutoNex");
            plugin.stop();
            return;
        }
        if (handleEmergencyExit()) {
            Utility.sleepGaussian(200, 300);
            plugin.stop();
            return;
        }
        if (getAncientKc() >= 40 && handleEnterNex()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handlePrayer()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleEating()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleStatBoostPotions()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (getAncientKc() < 40 && handleAttackSpiritualRanger()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handlePrayers()) {
            Utility.sleepGaussian(200, 300);
            return;
        }

        if (toggleRun()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        Utility.sleepGaussian(50, 100);
    }
}
