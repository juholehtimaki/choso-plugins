package com.PaistiPlugins.AutoNexPlugin;

import com.PaistiPlugins.PaistiUtils.API.*;
import com.PaistiPlugins.PaistiUtils.API.Potions.BoostPotion;
import com.PaistiPlugins.PaistiUtils.API.Potions.PotionStatusEffect;
import com.PaistiPlugins.PaistiUtils.API.Potions.StatusPotion;
import com.PaistiPlugins.PaistiUtils.API.Prayer.PPrayer;
import com.PaistiPlugins.PaistiUtils.Collections.query.NPCQuery;
import com.PaistiPlugins.PaistiUtils.PathFinding.LocalPathfinder;
import com.PaistiPlugins.VorkathKillerPlugin.States.State;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
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

        Minion(String minion) {
            this.minion = minion;
        }

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

    static final String NEX_IMMUNITY_MESSAGE = "nex is currently immune";

    static final String NEX_ICICLES_MESSAGE = "nex's icicles spike you to the spot";

    static final String FUMUS = "Fumus";
    static final String UMBRA = "Umbra";
    static final String CRUOR = "Cruor";
    static final String GLACIES = "Glacies";

    static final WorldPoint NEX_WAIT_TILE = new WorldPoint(8677, 92, 0);

    static final int NEX_DISTANCE = 3;

    private int nextDiedOnTick = -1;

    private long nexImmuneTick = -1;

    static Set<WorldPoint> dangerousTiles = new HashSet<>();

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

    private static final int POISON_VALUE_CUTOFF = 0; // Antivenom < -38 <= Antipoison < 0

    @Override
    public String name() {
        return "FightNex";
    }

    public int generateNextEatAtHp() {
        return Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.random(25, 35);
    }

    public int generateNextPrayerPotAt() {
        return Utility.random(20, 35);
    }

    public boolean isNexPresentAndAttackable() {
        var nex = NPCs.search().withName("Nex").withAction("Attack").first();
        if (nex.isEmpty() || nex.get().isDead()) {
            return false;
        }
        return true;
    }

    public boolean isNexPresent() {
        var nex = NPCs.search().withName("Nex").first();
        if (nex.isEmpty()) {
            return false;
        }
        return true;
    }

    public boolean handleNexSpawnWait() {
        if (isNexPresent()) {
            return false;
        }
        if (Walking.getPlayerLocation().distanceTo(NEX_WAIT_TILE) <= 2) {
            return false;
        }
        var randomizedNexWaitSpot = NEX_WAIT_TILE.dx(Utility.random(-1, 1)).dy(Utility.random(-1, 2));
        Walking.sceneWalk(randomizedNexWaitSpot);
        return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(randomizedNexWaitSpot) == 0, 3000, 200);
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

    private boolean handlePrayerRestore() {
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

    private boolean handleStatRestore() {
        if (System.currentTimeMillis() - lastAteAt < FOOD_DELAY) return false;
        if (Utility.getBoostedSkillLevel(Skill.RANGED) < Utility.getRealSkillLevel(Skill.RANGED) - 10) {
            BoostPotion statRestorePotion = BoostPotion.SUPER_RESTORE;
            var potionInInventory = statRestorePotion.findInInventory();
            if (potionInInventory.isPresent()) {
                var clicked = Interaction.clickWidget(potionInInventory.get(), "Drink");
                if (clicked) {
                    lastAteAt = System.currentTimeMillis();
                }
                return clicked;
            }
        }
        return false;
    }

    public boolean handlePrayers() {
        if (!isNexPresentAndAttackable()) {
            if (PPrayer.PROTECT_FROM_MAGIC.isActive() || PPrayer.PROTECT_FROM_MISSILES.isActive() || PPrayer.RIGOUR.isActive()) {
                PPrayer.RIGOUR.setEnabled(false);
                PPrayer.PROTECT_FROM_MISSILES.setEnabled(false);
                PPrayer.PROTECT_FROM_MAGIC.setEnabled(false);
                return true;
            }
        } else if (isNexPresentAndAttackable() && currPhase == NexPhase.SHADOW_PHASE) {
            if (!PPrayer.RIGOUR.isActive() || !PPrayer.PROTECT_FROM_MISSILES.isActive()) {
                PPrayer.RIGOUR.setEnabled(true);
                PPrayer.PROTECT_FROM_MISSILES.setEnabled(true);
                return true;
            }
        } else if (isNexPresentAndAttackable() && (!PPrayer.RIGOUR.isActive() || !PPrayer.PROTECT_FROM_MAGIC.isActive())) {
            PPrayer.RIGOUR.setEnabled(true);
            PPrayer.PROTECT_FROM_MAGIC.setEnabled(true);
            return true;
        }
        return false;
    }

    private boolean handleEating() {
        if (System.currentTimeMillis() - lastAteAt < FOOD_DELAY) return false;
        var isBelowHpTreshold = Utility.getBoostedSkillLevel(Skill.HITPOINTS) <= nextEatAtHp;
        var isMinusStatsAndNotOverhealed = Utility.getBoostedSkillLevel(Skill.RANGED) < Utility.getRealSkillLevel(Skill.RANGED) - 5 && Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.getBoostedSkillLevel(Skill.HITPOINTS) > 0;
        if (isBelowHpTreshold || isMinusStatsAndNotOverhealed) {
            if (eatFood()) {
                nextEatAtHp = generateNextEatAtHp();
                lastAteAt = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    public Optional<NPC> getDesiredTarget() {
        if (currMinion != null) {
            var minion = NPCs.search().withName(currMinion.toString()).withAction("Attack").alive().first();
            if (minion.isPresent() && Walking.getPlayerLocation().distanceTo(minion.get().getWorldLocation()) < 20) {
                return minion;
            }
        }
        var nex = getNex();
        if (nex.isEmpty()) return Optional.empty();
        var nexComposition = NPCQuery.getNPCComposition(nex.get());
        if (nexComposition == null) return Optional.empty();
        var nexActions = nexComposition.getActions();
        return nexActions != null && Arrays.stream(nexActions).anyMatch(a -> a != null && a.equalsIgnoreCase("attack")) ? nex : Optional.empty();
    }

    private boolean handleAttacking() {
        if (!isNexPresentAndAttackable() || !plugin.config.shouldAttackNex()) return false;
        if (getNexDistance().isPresent() && getNexDistance().get() < NEX_DISTANCE) return false;
        var desiredTarget = getDesiredTarget();
        if (desiredTarget.isEmpty()) {
            return false;
        }
        var target = Utility.getInteractionTarget();

        if (desiredTarget.get() == target) {
            return false;
        }

        if (Interaction.clickNpc(desiredTarget.get(), "Attack")) {
            Utility.sendGameMessage("Attempting to attack: " + desiredTarget.get().getName(), "AutoNex");
            Utility.sleepUntilCondition(() -> Utility.getInteractionTarget() == desiredTarget.get(), 1200, 50);
            return true;
        }

        return false;
    }

    private boolean isInteractingWithNex() {
        var target = Utility.getInteractionTarget();
        if (target == null || target.getName() == null) return false;
        return target.getName().equalsIgnoreCase("Nex");
    }

    public boolean eatFood() {
        var foodItem = plugin.getFoodItems().stream().findFirst();
        var saradominBrew = Inventory.search().matchesWildCardNoCase("saradomin brew*").first();
        if (saradominBrew.isPresent()) {
            Utility.sendGameMessage("Drinking " + saradominBrew.get().getName(), "AutoNex");
            return Interaction.clickWidget(saradominBrew.get(), "Eat", "Drink");
        } else if (foodItem.isPresent()) {
            Utility.sendGameMessage("Eating " + foodItem.get().getName(), "AutoNex");
            return Interaction.clickWidget(foodItem.get(), "Eat", "Drink");
        }
        return false;
    }

    public boolean handleStatusEffectPotions() {
        var poisonVarp = Utility.getVarpValue(VarPlayer.POISON);
        var isAntipoisonActive = poisonVarp < POISON_VALUE_CUTOFF;
        if (isAntipoisonActive) return false;

        int eatCooldownLeft = (int) Math.max(0, (FOOD_DELAY - (System.currentTimeMillis() - lastAteAt)));
        // Allow "tick eating" these if it came just after food eat
        if (eatCooldownLeft > 0 & eatCooldownLeft < FOOD_DELAY - 300) {
            return false;
        }

        var drankPot = Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            var statusPotions = Arrays.stream(StatusPotion.values()).filter(potion -> potion.findInInventory().isPresent()).collect(Collectors.toList());
            var antiVenoms = statusPotions
                    .stream()
                    .filter(potion -> potion.findEffect(PotionStatusEffect.StatusEffect.ANTIVENOM) != null)
                    .collect(Collectors.toList());
            for (var antiVenom : antiVenoms) {
                if (antiVenom.drink()) {
                    Utility.sendGameMessage("Drank " + antiVenom.name(), "AutoNex");
                    lastAteAt = System.currentTimeMillis();
                    return true;
                }
            }
            return false;
        }));
        return drankPot;
    }

    public Optional<Integer> getNexDistance() {
        var nex = getNex();
        return nex.map(npc -> Walking.getPlayerLocation().distanceTo(npc.getWorldLocation()));
    }

    private Optional<NPC> _cachedNex = Optional.empty();
    private int _cachedNexTick = -1;

    public Optional<NPC> getNex() {
        if (_cachedNexTick == Utility.getTickCount()) {
            return _cachedNex;
        }
        _cachedNex = NPCs.search().withName("Nex").first();
        _cachedNexTick = Utility.getTickCount();
        return _cachedNex;
    }

    public boolean handleNexDistance() {
        var distanceToNex = getNexDistance();
        if (distanceToNex.isEmpty()) return false;
        if (distanceToNex.get() > NEX_DISTANCE) return false;
        var playerLoc = Walking.getPlayerLocation();
        var nex = getNex();
        if (nex.isEmpty()) return false;
        var nexLocation = nex.get().getWorldLocation();
        List<WorldPoint> tiles = new ArrayList<>();
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        for (var dx = -30; dx <= 30; dx++) {
            for (var dy = -30; dy <= 30; dy++) {
                var tile = playerLoc.dx(dx).dy(dy);
                if (tile.distanceTo(nexLocation) <= NEX_DISTANCE + 3) continue;
                if (tile.distanceTo(nexLocation) >= 11) continue;
                if (dangerousTiles.contains(tile)) continue;
                if (!reachabilityMap.isReachable(tile)) continue;
                tiles.add(tile);
            }
        }

        tiles.sort(Comparator.comparingInt(reachabilityMap::getCostTo));
        if (tiles.isEmpty()) {
            Utility.sendGameMessage("No safe tiles available", "AutoNex");
            return false;
        }
        var firstSafeTile = tiles.get(0);
        Utility.sendGameMessage("Attempting to keep distance from Nex", "AutoNex");

        if (firstSafeTile.equals(playerLoc)) return false;

        Walking.sceneWalk(firstSafeTile);
        return Utility.sleepUntilCondition(() -> playerLoc.distanceTo(firstSafeTile) == 0, 2400, 300);
    }

    public boolean handleImportantLooting() {
        var groundItems = TileItems.search().filter(itm -> Math.max(itm.getGePrice(), itm.getHaPrice()) >= 100000).result();
        if (groundItems.isEmpty()) return false;
        boolean pickedUpLoot = false;
        for (var groundItem : groundItems) {
            if (Inventory.isFull()) {
                var inventoryItems = Inventory.search().filter(i -> !i.getName().contains("house")).result();
                var emptySlotsBeforeDropping = Inventory.getEmptySlots();
                inventoryItems.sort(Comparator.comparingInt(item -> Utility.getItemMaxPrice(item.getItemId())));
                var itemToDrop = inventoryItems.stream().findFirst();
                Utility.sendGameMessage("Dropping " + itemToDrop.get().getName() + " to make room for " + groundItem.getName(), "AutoNex");
                Interaction.clickWidget(itemToDrop.get(), "Drop");
                Utility.sleepUntilCondition(() -> Inventory.getEmptySlots() > emptySlotsBeforeDropping, 3600, 600);
            }
            Utility.sendGameMessage("Attempting to loot: " + groundItem.getName(), "AutoNex");
            Interaction.clickGroundItem(groundItem, "Take");
            var quantityBeforeClick = Inventory.getItemAmount(groundItem.getId());
            Utility.sleepUntilCondition(() -> Inventory.getItemAmount(groundItem.getId()) > quantityBeforeClick, 6000, 300);
            if (quantityBeforeClick > Inventory.getItemAmount(groundItem.getId())) {
                pickedUpLoot = true;
                Utility.sleepGaussian(300, 600);
            }
        }
        return pickedUpLoot;
    }

    public boolean handleRegularLooting() {
        if (Inventory.isFull() || isNexPresent()) return false;
        var groundItems = TileItems.search().filter(itm -> itm.getName().contains("Shark") || Math.max(itm.getGePrice(), itm.getHaPrice()) > 3000).result();
        if (groundItems.isEmpty()) return false;
        groundItems.sort(Comparator.comparingInt(itm -> Math.max(itm.getGePrice(), itm.getHaPrice())));
        boolean pickedUpLoot = false;
        for (var groundItem : groundItems) {
            if (Inventory.isFull()) return false;
            Utility.sendGameMessage("Attempting to loot regular items: " + groundItem.getName(), "AutoNex");
            Interaction.clickGroundItem(groundItem, "Take");
            var quantityBeforeClick = Inventory.getItemAmount(groundItem.getId());
            if (Utility.sleepUntilCondition(() -> Inventory.getItemAmount(groundItem.getId()) > quantityBeforeClick, 6000, 600)) {
                pickedUpLoot = true;
            }
        }
        return pickedUpLoot;
    }

    // 29733


    public boolean handleStatBoostPotions() {
        if (System.currentTimeMillis() - lastAteAt < FOOD_DELAY) return false;
        if (isNexPresent()) return false;
        if (Utility.getRealSkillLevel(Skill.RANGED) != Utility.getBoostedSkillLevel(Skill.RANGED)) {
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

    public boolean handleEmergencyExit() {
        boolean isLowHp = Utility.getBoostedSkillLevel(Skill.HITPOINTS) < 50;
        boolean isLowPrayer = Utility.getBoostedSkillLevel(Skill.PRAYER) < 20;
        if (isLowHp || isLowPrayer) {
            if (isLowHp) {
                var brews = Inventory.search().matchesWildCardNoCase("Saradomin brew*");
                var foods = plugin.getFoodItems().stream().findFirst();
                if (brews.empty() && foods.isEmpty()) {
                    Utility.sendGameMessage("Attempting to exit Nex due to low HP and no food left");
                    var altar = TileObjects.search().withName("Altar").withAction("Teleport").nearestToPlayer();
                    if (altar.isEmpty()) {
                        Utility.sendGameMessage("Could not find altar");
                        return false;
                    }
                    Interaction.clickTileObject(altar.get(), "Teleport");
                    return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(plugin.NEX_TILE) > 20, 10000, 300);
                }
            }
            if (isLowPrayer) {
                var restorePotions = Inventory.search().matchesWildCardNoCase("Super restore*");
                var prayerPotions = Inventory.search().matchesWildCardNoCase("Prayer potion*");
                if (restorePotions.empty() && prayerPotions.empty()) {
                    Utility.sendGameMessage("Attempting to exit Nex due to low prayer and no potions left");
                    var altar = TileObjects.search().withName("Altar").withAction("Teleport").nearestToPlayer();
                    if (altar.isEmpty()) {
                        Utility.sendGameMessage("Could not find altar");
                        return false;
                    }
                    Interaction.clickTileObject(altar.get(), "Teleport");
                    return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(plugin.NEX_TILE) > 20, 10000, 300);
                }
            }
        }
        return false;
    }

    public boolean handleExit() {
        if (plugin.prepareState.getAncientKc() >= 40 && !isNexPresent()) {
            Utility.sendGameMessage("Attempting to exit due to having over 40 kc", "AutoNex");
            var altar = TileObjects.search().withName("Altar").withAction("Teleport").nearestToPlayer();
            if (altar.isEmpty()) {
                Utility.sendGameMessage("Could not find altar", "AutoNex");
                return false;
            }
            Interaction.clickTileObject(altar.get(), "Teleport");
            return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(plugin.NEX_TILE) > 20, 10000, 300);
        }
        return false;
    }

    public boolean handleSmokeBarrage() {
        if (dangerousTiles.isEmpty()) return false;
        var playerLoc = Walking.getPlayerLocation();
        if (dangerousTiles.contains(playerLoc)) {
            Utility.sendGameMessage("Dangerous tiles contain player loc", "AutoNex");
            List<WorldPoint> tiles = new ArrayList<>();
            LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
            for (var dx = -10; dx <= 10; dx++) {
                for (var dy = -10; dy <= 10; dy++) {
                    var tile = playerLoc.dx(dx).dy(dy);
                    if (tile.distanceTo(playerLoc) >= 5) continue;
                    if (!reachabilityMap.isReachable(tile)) continue;
                    if (dangerousTiles.contains(tile)) continue;
                    tiles.add(tile);
                }
            }
            tiles.sort(Comparator.comparingInt(reachabilityMap::getCostTo));
            if (tiles.isEmpty()) {
                Utility.sendGameMessage("No safe tiles available for smoke barrage", "AutoNex");
                return false;
            }
            var firstSafeTile = tiles.get(0);
            Utility.sendGameMessage("Attempting to move away from smoke barrage", "AutoNex");

            if (firstSafeTile.equals(playerLoc)) return false;

            Walking.sceneWalk(firstSafeTile);
            return Utility.sleepUntilCondition(() -> playerLoc.distanceTo(firstSafeTile) == 0, 2400, 300);
        }
        return false;
    }

    public boolean handleSpec() {
        if (Utility.getSpecialAttackEnergy() >= 75 && !Utility.isSpecialAttackEnabled() && isInteractingWithNex()) {
            if (Utility.getTickCount() - nexImmuneTick > 10 && Utility.getVarbitValue(Varbits.BOSS_HEALTH_CURRENT) >= 300) {
                Utility.specialAttack();
                return Utility.sleepUntilCondition(Utility::isSpecialAttackEnabled, 1200, 300);
            }
        }
        return false;
    }

    public boolean handleToggleRun() {
        if (Walking.isRunEnabled() || Walking.getRunEnergy() < 15) return false;
        Walking.setRun(true);
        return true;
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned e) {
        if (e.getGameObject().getId() == 42942) {
            dangerousTiles.add(e.getGameObject().getWorldLocation());
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned e) {
        if (e.getGameObject().getId() == 29733) {
            dangerousTiles.clear();
        }
    }

    @Override
    public void threadedLoop() {
        if (Utility.getTickCount() - nextDiedOnTick < 10) {
            Utility.sleepUntilCondition(() -> Utility.getTickCount() - nextDiedOnTick >= 10, 3000, 100);
            return;
        }
        if (handleImportantLooting()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleEmergencyExit()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleSmokeBarrage()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handlePrayers()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleNexDistance()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleEating()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleStatRestore()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handlePrayerRestore()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleStatusEffectPotions()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleAttacking()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleSpec()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleRegularLooting()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleNexSpawnWait()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleStatBoostPotions()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleExit()) {
            Utility.sleepGaussian(200, 300);
            return;
        }
        if (handleToggleRun()) {
            Utility.sleepGaussian(200, 300);
            return;
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
        } else if (event.getMessage().toLowerCase().contains(ZAROS_PHASE_MESSAGE.toLowerCase())) {
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

        // Nex immunity
        if (event.getMessage().toLowerCase().contains(NEX_IMMUNITY_MESSAGE.toLowerCase())) {
            nexImmuneTick = Utility.getTickCount();
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
                    dangerousTiles.clear();
                    nextDiedOnTick = Utility.getTickCount();
                    Utility.sendGameMessage("Nex has died!", "AutoNex");
                } else if (actor.getName().toLowerCase().contains(FUMUS.toLowerCase()) || actor.getName().toLowerCase().contains(UMBRA.toLowerCase()) || actor.getName().toLowerCase().contains(CRUOR.toLowerCase()) || actor.getName().toLowerCase().contains(GLACIES.toLowerCase())) {
                    Utility.sendGameMessage(actor.getName() + " has died!", "AutoNex");
                    currMinion = null;
                }
            }
        }
    }
}
