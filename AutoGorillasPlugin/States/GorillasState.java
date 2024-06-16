package com.theplug.AutoGorillasPlugin.States;

import com.theplug.AutoGorillasPlugin.AutoGorillasPlugin;
import com.theplug.AutoGorillasPlugin.AutoGorillasPluginConfig;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.GameSimulator.Data.NPCAttackType;
import com.theplug.PaistiUtils.API.GameSimulator.Trackers.NpcSpecific.PTrackerDemonicGorilla;
import com.theplug.PaistiUtils.API.Potions.BoostPotion;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.Collections.query.NPCQuery;
import com.theplug.PaistiUtils.Hooks.Hooks;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.VardorvisPlugin.States.State;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class GorillasState implements State {
    AutoGorillasPlugin plugin;
    AutoGorillasPluginConfig config;
    private static final AtomicReference<Integer> lastAteOnTick = new AtomicReference<>(-1);
    private static final AtomicReference<Integer> lastDrankOnTick = new AtomicReference<>(-1);
    private int nextPrayerPotionAt;
    private int nextEatAtHp;

    public GorillasState(AutoGorillasPlugin plugin, AutoGorillasPluginConfig config) {
        super();
        this.plugin = plugin;
        this.config = config;
        this.nextPrayerPotionAt = generateNextPrayerPotAt();
        this.nextEatAtHp = generateNextEatAtHp();
    }

    public boolean canDrinkThisTick() {
        int currTick = Utility.getTickCount();
        if (currTick - lastDrankOnTick.get() < 3) return false;
        if (currTick - lastAteOnTick.get() < 3 && lastAteOnTick.get() != 0) return false;
        return true;
    }

    public boolean canEatThisTick() {
        int currTick = Utility.getTickCount();
        if (currTick - lastAteOnTick.get() < 3) return false;
        if (currTick - lastDrankOnTick.get() < 3) return false;
        return true;
    }

    public boolean handleStatBoostPotions() {
        log.debug("handleStatBoostPotions");
        if (!canDrinkThisTick()) return false;
        var potionsToDrink = Utility.runOnClientThread(() -> Arrays.stream(BoostPotion.values()).filter(potion -> {
            if (potion.findBoost(Skill.PRAYER) != null) return false;
            if (potion.findInInventory().isEmpty()) return false;
            return potion.isAnyStatBoostBelow(config.drinkPotionsBelowBoost());
        }).collect(Collectors.toList()));

        if (potionsToDrink == null || potionsToDrink.isEmpty()) return false;

        potionsToDrink.sort(Comparator.comparingInt(t -> {
            if (t.getNameMatcher().toLowerCase().contains("divine")) return 0;
            return 1;
        }));

        var drankPotion = false;
        for (var boostPotion : potionsToDrink) {
            if (boostPotion.drink()) {
                lastDrankOnTick.set(Utility.getTickCount());
            }
            return true;
        }
        return drankPotion;
    }

    private boolean handlePrayerRestore() {
        log.debug("handlePrayerRestore");
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

    public List<Widget> getFoodItems() {
        return Inventory.search().onlyUnnoted().filter((item) -> plugin.foodStats.getHealAmount(item.getItemId()) >= 8).result();
    }

    public boolean eatFood() {
        log.debug("eatFood");
        var foodItem = getFoodItems().stream().findFirst();
        return foodItem.filter(widget -> Interaction.clickWidget(widget, "Eat", "Drink")).isPresent();
    }

    private boolean handleEating() {
        log.debug("handleEating");
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

    Actor cachedCurrTarget = null;
    int cachedCurrTargetTick = -1;

    public Actor getTarget() {
        if (cachedCurrTarget != null && cachedCurrTargetTick == Utility.getTickCount()) {
            return cachedCurrTarget;
        }
        var interactingWith = Utility.getInteractionTarget();
        var localPlayer = PaistiUtils.getClient().getLocalPlayer();
        var gorillas = NPCs.search().nameContains("Demonic gorilla").alive().result().stream().filter(npc -> npc.getInteracting() == localPlayer);
        var sortedGorillas = gorillas.sorted(Comparator.comparingInt(npc -> npc == interactingWith ? 0 : 1)).collect(Collectors.toList());
        if (sortedGorillas.isEmpty()) {
            cachedCurrTarget = null;
            return null;
        }
        cachedCurrTargetTick = Utility.getTickCount();
        return sortedGorillas.get(0);
    }

    private PPrayer getDefensivePrayer() {
        var target = getTarget();
        if (target == null) return null;
        var tracker = plugin.getActorTracker().getTrackerForActor(target);
        if (!(tracker instanceof PTrackerDemonicGorilla)) return null;
        var gorilla = (PTrackerDemonicGorilla) tracker;
        if (gorilla.getPredictedAttackType() == NPCAttackType.MELEE) {
            return PPrayer.PROTECT_FROM_MELEE;
        } else if (gorilla.getPredictedAttackType() == NPCAttackType.RANGED) {
            return PPrayer.PROTECT_FROM_MISSILES;
        } else if (gorilla.getPredictedAttackType() == NPCAttackType.MAGIC) {
            return PPrayer.PROTECT_FROM_MAGIC;
        }
        return null;
    }

    private boolean handleDefensivePrayers() {
        var prayer = getDefensivePrayer();
        if (prayer == null) {
            if (PPrayer.PROTECT_FROM_MELEE.isActive()) {
                PPrayer.PROTECT_FROM_MELEE.setEnabledWithoutClicks(false);
                return true;
            } else if (PPrayer.PROTECT_FROM_MISSILES.isActive()) {
                PPrayer.PROTECT_FROM_MISSILES.setEnabledWithoutClicks(false);
                return true;
            } else if (PPrayer.PROTECT_FROM_MAGIC.isActive()) {
                PPrayer.PROTECT_FROM_MAGIC.setEnabledWithoutClicks(false);
                return true;
            }
            return false;
        }
        if (prayer.isActive()) {
            return false;
        }
        return prayer.setEnabledWithoutClicks(true);
    }

    private boolean handleOffensivePrayers() {
        var target = getTarget();
        if (target == null) {
            return PPrayer.disableOffensivePrayers();
        }
        var offensivePrayer = PPrayer.getBestOffensivePrayers();
        if (offensivePrayer == null) return false;
        var changedPrayer = false;
        for (var prayer : offensivePrayer) {
            if (prayer.isActive()) continue;
            if (prayer.setEnabledWithoutClicks(true)) {
                changedPrayer = true;
            }
        }
        return changedPrayer;
    }

    private boolean handleGearSwitch() {
        HeadIcon overhead = Utility.runOnClientThread(() -> {
            var interactingWith = Utility.getInteractionTarget();
            var localPlayer = PaistiUtils.getClient().getLocalPlayer();
            var gorillas = NPCs.search().nameContains("Demonic gorilla").result().stream().filter(npc -> npc.getInteracting() == localPlayer);
            var sortedGorillas = gorillas.sorted(Comparator.comparingInt(npc -> npc == interactingWith ? 0 : 1)).collect(Collectors.toList());

            if (sortedGorillas.isEmpty()) {
                return null;
            }
            var comp = NPCQuery.getNPCComposition(sortedGorillas.get(0));
            if (comp == null) {
                return null;
            }
            return Hooks.getNpcCompositionOverheadIcon(comp);
        });

        if (overhead == null) {
            return false;
        }

        var loadout = plugin.gearSwitches.entrySet().stream().filter(entry -> !entry.getKey().getProtectedByHeadIcon().equals(overhead)).findFirst();

        return loadout.map(gorillaCombatStyleInventoryLoadoutSetupEntry -> gorillaCombatStyleInventoryLoadoutSetupEntry.getValue().handleSwitchTurbo()).orElse(false);
    }

    @Override
    public String name() {
        return "Fighting gorillas";
    }

    @Override
    public boolean shouldExecuteState() {
        return true;
    }

    @Override
    public void threadedOnGameTick() {
        handleDefensivePrayers();
        handleOffensivePrayers();
    }

    @Override
    public void threadedLoop() {
        if (handleGearSwitch()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        Utility.sleepGaussian(50, 100);
    }

    @Subscribe
    private void onGameTick(GameTick e) {
    }

    public int generateNextPrayerPotAt() {
        return Utility.random(30, 40);
    }

    public int generateNextEatAtHp() {
        return Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.random(45, 55);
    }
}