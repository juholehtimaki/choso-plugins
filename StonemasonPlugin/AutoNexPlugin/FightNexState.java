package com.PaistiPlugins.AutoNexPlugin;

import com.PaistiPlugins.PaistiUtils.API.*;
import com.PaistiPlugins.PaistiUtils.API.Potions.BoostPotion;
import com.PaistiPlugins.PaistiUtils.API.Prayer.PPrayer;
import com.PaistiPlugins.VorkathKillerPlugin.States.State;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
public class FightNexState implements State {
    AutoNexPlugin plugin;

    enum NexPhase {
        SMOKE_PHASE,
        SHADOW_PHASE,
        BLOOD_PHASE,
        ICE_PHASE,
        ZAROS_PHASE
    }

    enum Minion {
        FUMUS("Fumus"),
        UMBRA("Umbra"),
        CRUOR("Cruor"),
        GLACIES("Glacies");

        final String minion;

        Minion(String minion) { this.minion = minion; }

        @Override
        public String toString() {
            return this.minion;
        }
    }

    static final String SMOKE_PHASE_MESSAGE = "fill my soul with smoke";
    static final String SHADOW_PHASE_MESSAGE = "darken my shadow";
    static final String BLOOD_PHASE_MESSAGE = "flood my lungs with blood";
    static final String ICE_PHASE_MESSAGE = "infuse me with the power of ice";
    static final String ZAROS_PHASE_MESSAGE = "now, the power of zaros";

    static final String FUMUS = "Fumus";
    static final String UMBRA = "Umbra";
    static final String CRUOR = "Cruor";
    static final String GLACIES = "Glacies";

    public FightNexState(AutoNexPlugin plugin) {
        super();
        this.plugin = plugin;
        this.nextEatAtHp = generateNextEatAtHp();
        this.nextPrayerPotionAt = generateNextPrayerPotAt();
    }

    private int nextEatAtHp;
    private int nextPrayerPotionAt;
    private long lastAteAt = 0;

    private static final int FOOD_DELAY = 1800;

    public NexPhase currPhase = null;

    private Minion currMinion = null;

    @Override
    public String name() {
        return "FightNex";
    }

    public int generateNextEatAtHp() {
        return Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.random(25, 35);
    }

    public int generateNextPrayerPotAt() {
        return Utility.random(30, 50);
    }

    public boolean isNexPresent() {
        var nex = NPCs.search().withName("Nex").withAction("Attack").first();
        if (nex.isEmpty() || nex.get().isDead()) {
            return false;
        }
        return true;
    }

    public boolean isInteractingWithNex() {
        var target = Utility.getInteractionTarget();
        if (target.getName().equalsIgnoreCase("Nex")) {
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldExecuteState() {
        return plugin.isInsideNexRoom();
    }

    @Override
    public void threadedOnGameTick() {
        //Utility.sleepGaussian(50, 150);
        //Utility.sleepGaussian(150, 200);
    }

    public boolean handleStatBoostPotions() {
        var potionsToDrink = Utility.runOnClientThread(() -> Arrays.stream(BoostPotion.values()).filter(potion -> {
            if (potion.findBoost(Skill.PRAYER) != null) return false;
            if (potion.findInInventory().isEmpty()) return false;
            return potion.isAnyCurrentBoostBelow(105);
        }).collect(Collectors.toList()));

        if (potionsToDrink == null || potionsToDrink.isEmpty()) return false;

        var drankPotion = false;
        for (var boostPotion : potionsToDrink) {
            if (boostPotion.drink()) {
                Utility.sendGameMessage("Drank " + boostPotion.name(), "AutoNexSystem.currentTimeMillis() - lastAteAt > 1800System.currentTimeMillis() - lastAteAt > 1800");
                lastAteAt = System.currentTimeMillis();
            }
            return true;
        }
        return drankPotion;
    }

    private boolean handlePrayer() {
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

    public boolean handlePrayers() {
        if (!isNexPresent()) {
            if (PPrayer.PROTECT_FROM_MAGIC.isActive() || PPrayer.PROTECT_FROM_MISSILES.isActive() || PPrayer.RIGOUR.isActive()) {
                PPrayer.RIGOUR.setEnabled(false);
                PPrayer.PROTECT_FROM_MISSILES.setEnabled(false);
                PPrayer.PROTECT_FROM_MAGIC.setEnabled(false);
                return true;
            }
        }
        else if (isNexPresent() && currPhase == NexPhase.SHADOW_PHASE) {
            if (!PPrayer.RIGOUR.isActive() || !PPrayer.PROTECT_FROM_MISSILES.isActive()) {
                PPrayer.RIGOUR.setEnabled(true);
                PPrayer.PROTECT_FROM_MISSILES.setEnabled(true);
                return true;
            }
        } else if (isNexPresent() && (!PPrayer.RIGOUR.isActive() || !PPrayer.PROTECT_FROM_MAGIC.isActive())) {
            PPrayer.RIGOUR.setEnabled(true);
            PPrayer.PROTECT_FROM_MAGIC.setEnabled(true);
            return true;
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

    public Optional<NPC> getCurrentTarget() {
        if (currMinion != null) {
            var minion = NPCs.search().withName(currMinion.toString()).withAction("Attack").alive().first();
            if (minion.isEmpty()) {
                return NPCs.search().withName("Nex").withAction("Attack").alive().first();
            }
            return minion;
        }
        return NPCs.search().withName("Nex").withAction("Attack").alive().first();
    }

    private boolean handleAttacking() {
        if (!isNexPresent() || !plugin.config.shouldAttackNex()) return false;
        var currentTarget = getCurrentTarget();
        if (currentTarget.isEmpty()) {
            return false;
        }
        var target = Utility.getInteractionTarget();

        if (currentTarget.get() == target) {
            return false;
        }

        if (Interaction.clickNpc(currentTarget.get(), "Attack")) {
            Utility.sendGameMessage("Attempting to attack: " + currentTarget.get().getName(), "AutoNex");
            Utility.sleepUntilCondition(() -> Utility.getInteractionTarget() == currentTarget.get(), 1200, 50);
            return true;
        }

        return false;
    };

    public boolean eatFood() {
        var foodItems = plugin.getFoodItems();
        if (foodItems.isEmpty()) return false;
        Utility.sendGameMessage("Eating " + foodItems.get(0).getName(), "AutoNex");
        return Interaction.clickWidget(foodItems.get(0), "Eat", "Drink");
    }

    @Override
    public void threadedLoop() {
        if (handlePrayers()) {
            Utility.sendGameMessage("Handled prayer switch", "AutoNex");
            Utility.sleepGaussian(200, 300);
        }

        if (handleAttacking()) {
            Utility.sendGameMessage("Tried attacking NPC", "AutoNex");
            Utility.sleepGaussian(200, 300);
        }
        Utility.sleepGaussian(50, 100);
    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        // NEX PHASES
        if (event.getMessage().toLowerCase().contains(SMOKE_PHASE_MESSAGE.toLowerCase())) {
            currPhase = NexPhase.SMOKE_PHASE;
        } else if (event.getMessage().toLowerCase().contains(SHADOW_PHASE_MESSAGE.toLowerCase())) {
            currPhase = NexPhase.SHADOW_PHASE;
        } else if (event.getMessage().toLowerCase().contains(BLOOD_PHASE_MESSAGE.toLowerCase())) {
            currPhase = NexPhase.BLOOD_PHASE;
        } else if (event.getMessage().toLowerCase().contains(ICE_PHASE_MESSAGE.toLowerCase())) {
            currPhase = NexPhase.ICE_PHASE;
        }  else if (event.getMessage().toLowerCase().contains(ZAROS_PHASE_MESSAGE.toLowerCase())) {
            currPhase = NexPhase.ZAROS_PHASE;
        }

        // MINIONS
        if (event.getMessage().toLowerCase().contains(FUMUS.toLowerCase() + ",")) {
            currMinion = Minion.FUMUS;
        } else if (event.getMessage().toLowerCase().contains(UMBRA.toLowerCase() + ",")) {
            currMinion = Minion.UMBRA;
        } else if (event.getMessage().toLowerCase().contains(CRUOR.toLowerCase() + ",")) {
            currMinion = Minion.CRUOR;
        } else if (event.getMessage().toLowerCase().contains(GLACIES.toLowerCase() + ",")) {
            currMinion = Minion.GLACIES;
        }
    }

    @Subscribe(priority = 5000)
    public void onActorDeath(ActorDeath actorDeath) {
        if (!plugin.runner.isRunning()) return;
        Actor actor = actorDeath.getActor();
        if (actor instanceof NPC) {
            if (actor.getName() != null) {
                if (actor.getName().toLowerCase().contains("nex")) {
                    currMinion = null;
                    currPhase = null;
                    Utility.sendGameMessage("Nex has died!", "AutoNex");
                } else if (actor.getName().toLowerCase().contains(FUMUS.toLowerCase()) || actor.getName().toLowerCase().contains(UMBRA.toLowerCase()) || actor.getName().toLowerCase().contains(CRUOR.toLowerCase()) || actor.getName().toLowerCase().contains(GLACIES.toLowerCase())) {
                    Utility.sendGameMessage("Minion has died!", "AutoNex");
                    currMinion = null;
                }
            }
        }
    }
}
