package com.theplug.AutoNexPlugin;

import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Potions.BoostPotion;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Skill;

import java.util.*;

@Slf4j
public class KillCountState implements State {
    AutoNexPlugin plugin;

    public KillCountState(AutoNexPlugin plugin) {
        super();
        this.plugin = plugin;
        this.nextEatAtHp = generateNextEatAtHp();
        this.nextPrayerPotionAt = generateNextPrayerPotAt();
    }

    private int nextEatAtHp;
    private int nextPrayerPotionAt;
    private static int lastAteOnTick = -1;
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

    @Override
    public boolean shouldExecuteState() {
        return plugin.isInsideKcRoom();
    }

    @Override
    public void threadedOnGameTick() {
        //Utility.sleepGaussian(50, 150);
        //Utility.sleepGaussian(150, 200);
    }

    public boolean handleStatBoostPotions() {
        log.debug("handleStatBoostPotions");
        if (Utility.getTickCount() - lastAteOnTick < 3) return false;

        // 105 <= 99 return false
        if (Utility.getBoostedSkillLevel(Skill.RANGED) < Utility.getRealSkillLevel(Skill.RANGED) + 5) {
            var potionToDrink = Utility.runOnClientThread(() -> Arrays.stream(BoostPotion.values()).filter(potion -> potion.findBoost(Skill.RANGED) != null).findFirst());

            if (potionToDrink == null || potionToDrink.isEmpty()) return false;

            if (potionToDrink.get().drink()) {
                Utility.sendGameMessage("Drank " + potionToDrink.get().name(), "AutoNex");
                lastAteOnTick = Utility.getTickCount();
                return true;
            }
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

    private boolean handlePrayerRestore() {
        if (Utility.getTickCount() - lastAteOnTick < 3) return false;
        if (Utility.getBoostedSkillLevel(Skill.PRAYER) <= nextPrayerPotionAt) {
            BoostPotion prayerBoostPot = BoostPotion.PRAYER_POTION.findInInventoryWithLowestDose().isEmpty() ? BoostPotion.SUPER_RESTORE : BoostPotion.PRAYER_POTION;
            var potionInInventory = prayerBoostPot.findInInventoryWithLowestDose();
            if (potionInInventory.isPresent()) {
                var clicked = Interaction.clickWidget(potionInInventory.get(), "Drink");
                if (clicked) {
                    nextPrayerPotionAt = generateNextPrayerPotAt();
                    lastAteOnTick = Utility.getTickCount();
                }
                return clicked;
            }
        }
        return false;
    }

    public boolean handleEmergencyExit() {
        log.debug("handleEmergencyExit");
        if (plugin.getAncientKc() >= 40) return false;
        boolean isLowPrayer = Utility.getBoostedSkillLevel(Skill.PRAYER) < 20;
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
        return false;
    }

    private boolean handleEating() {
        log.debug("handleEating");
        if (Utility.getTickCount() - lastAteOnTick < 3) return false;
        var isBelowHpTreshold = Utility.getBoostedSkillLevel(Skill.HITPOINTS) <= nextEatAtHp;
        var isMinusStatsAndNotOverhealed = Utility.getBoostedSkillLevel(Skill.RANGED) < Utility.getRealSkillLevel(Skill.RANGED) - 5 && Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.getBoostedSkillLevel(Skill.HITPOINTS) > 0;
        if (isBelowHpTreshold || isMinusStatsAndNotOverhealed) {
            if (eatFood()) {
                nextEatAtHp = generateNextEatAtHp();
                lastAteOnTick = Utility.getTickCount();
                return true;
            }
        }
        return false;
    }

    public boolean eatFood() {
        log.debug("eatFood");
        var foodItem = plugin.getFoodItems().stream().findFirst();
        var saradominBrew = plugin.fightNexState.findInInventoryWithLowestDose("saradomin brew*");
        if (foodItem.isPresent()) {
            //Utility.sendGameMessage("Eating " + foodItem.get().getName(), "AutoNex");
            return Interaction.clickWidget(foodItem.get(), "Eat", "Drink");
        } else if (saradominBrew.isPresent()) {
            //Utility.sendGameMessage("Drinking " + saradominBrew.get().getName(), "AutoNex");
            return Interaction.clickWidget(saradominBrew.get(), "Eat", "Drink");
        }
        return false;
    }

    private boolean handleStatRestore() {
        log.debug("handleStatRestore");
        if (Utility.getTickCount() - lastAteOnTick < 3) return false;
        if (Utility.getBoostedSkillLevel(Skill.RANGED) < Utility.getRealSkillLevel(Skill.RANGED) - 10) {
            BoostPotion statRestorePotion = BoostPotion.SUPER_RESTORE;
            var potionInInventory = statRestorePotion.findInInventoryWithLowestDose();
            if (potionInInventory.isPresent()) {
                var clicked = Interaction.clickWidget(potionInInventory.get(), "Drink");
                if (clicked) {
                    lastAteOnTick = Utility.getTickCount();
                }
                return clicked;
            }
        }
        return false;
    }

    public boolean currentlyInterActingWithNpc() {
        var client = PaistiUtils.getClient();
        var npcs = NPCs.search().withName(plugin.config.selectedNpc().toString()).withAction("Attack").filter(npc -> npc.getInteracting() == client.getLocalPlayer()).result();
        if (npcs.isEmpty() && Utility.getInteractionTarget() == null) {
            return false;
        }
        return true;
    }

    public boolean handlePrayers() {
        log.debug("handlePrayers");
        var offensivePrayer = plugin.getOffensivePray().get();
        if (offensivePrayer == null) return false;
        if (currentlyInterActingWithNpc() && (!offensivePrayer.isActive() || !PPrayer.PROTECT_FROM_MAGIC.isActive())) {
            offensivePrayer.setEnabled(true);
            PPrayer.PROTECT_FROM_MAGIC.setEnabled(true);
            return true;
        } else if (!currentlyInterActingWithNpc() && (offensivePrayer.isActive() || PPrayer.PROTECT_FROM_MAGIC.isActive())) {
            offensivePrayer.setEnabled(false);
            PPrayer.PROTECT_FROM_MAGIC.setEnabled(false);
            return true;
        }
        return false;
    }

    public boolean handleAttack() {
        log.debug("handleAttack");
        if (plugin.getAncientKc() >= 40) return false;
        var target = Utility.getInteractionTarget();
        var client = PaistiUtils.getClient();
        if (target instanceof NPC) {
            NPC npc = (NPC) target;
            Actor npcTarget = npc.getInteracting();
            if (Objects.equals(npc.getName(), plugin.config.selectedNpc().toString()) && !npc.isDead() && (npcTarget == client.getLocalPlayer() || npcTarget == null)) {
                return false;
            }
        }
        var killCountNpc = NPCs.search()
                .withName(plugin.config.selectedNpc().toString())
                .withAction("Attack")
                .alive()
                .filter(npc -> npc.getInteracting() == null || npc.getInteracting() == client.getLocalPlayer())
                .result();

        killCountNpc.sort(Comparator.comparingInt((NPC npc) -> {
            if (npc.getInteracting() == client.getLocalPlayer()) {
                return 0;
            }
            return 1;
        }).thenComparingInt((NPC npc) -> npc.getWorldLocation().distanceTo(Walking.getPlayerLocation())));

        if (killCountNpc.isEmpty()) return false;
        if (Interaction.clickNpc(killCountNpc.get(0), "Attack")) {
            Utility.sleepUntilCondition(() -> Utility.getInteractionTarget() == killCountNpc.get(0), 1200, 50);
            return true;
        }
        return false;
    }

    public boolean handleEnterBankRoom() {
        log.debug("handleEnterBankRoom");
        var ancientDoor = TileObjects.search().withId(ANCIENT_DOOR).withAction("Open").nearestToPlayer();
        if (ancientDoor.isEmpty()) {
            Utility.sendGameMessage("Could not find door!", "AutoNex");
            return false;
        }
        Utility.sendGameMessage("Opening door!", "AutoNex");
        Interaction.clickTileObject(ancientDoor.get(), "Open");
        var kcBefore = plugin.getAncientKc();
        return Utility.sleepUntilCondition(() -> plugin.getAncientKc() < kcBefore, 10000, 600);
    }

    @Override
    public void threadedLoop() {
        if (!plugin.config.assistMode()) {
            if (handleEmergencyExit()) {
                Utility.sleepGaussian(200, 300);
                plugin.stop();
                return;
            }
            if (plugin.getAncientKc() >= 40 && handleEnterBankRoom()) {
                Utility.sleepGaussian(200, 300);
                return;
            }
            if (handlePrayerRestore()) {
                Utility.sleepGaussian(200, 300);
                return;
            }
            if (handleEating()) {
                Utility.sleepGaussian(200, 300);
                return;
            }
            if (handleStatRestore()) {
                Utility.sleepGaussian(50, 100);
                return;
            }
            if (handleStatBoostPotions()) {
                Utility.sleepGaussian(200, 300);
                return;
            }
            if (handleAttack()) {
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
        }
        Utility.sleepGaussian(50, 100);
    }
}
