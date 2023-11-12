package com.theplug.AutoNexPlugin;

import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.NPCTickSimulation.NPCTickSimulation;
import com.theplug.PaistiUtils.API.Potions.BoostPotion;
import com.theplug.PaistiUtils.API.Potions.PotionStatusEffect;
import com.theplug.PaistiUtils.API.Potions.StatusPotion;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.API.Spells.Necromancy;
import com.theplug.PaistiUtils.Collections.query.NPCQuery;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.theplug.PaistiUtils.API.Potions.Potion.dosePattern;


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

    static final String NEX_PRISON_MESSAGE = "you've been trapped in an ice prison";

    static final String NEX_PRISON_FREED_MESSAGE = "you've been freed from the ice prison";

    static final String NEX_PRISON_PIERCES_MESSAGE = "the ice prison shatters and pierces your flesh";

    static final String MVP_MESSAGE = "you were the mvp for this fight";

    static final String BLOOD_MARKED_MESSAGE = "nex has marked you for a blood sacrifice";

    static final String BLOOD_MARKED_SUCCESSFUL_MESSAGE = "you managed to escape from nex's blood sacrifice";

    static final String BLOOD_MARKED_FAILED_MESSAGE = "you failed to escape from nex's blood sacrifice";

    static final String CONTAINTMENT_MESSAGE = "contain this";

    static final String FUMUS = "Fumus";
    static final String UMBRA = "Umbra";
    static final String CRUOR = "Cruor";
    static final String GLACIES = "Glacies";

    @Getter
    private AtomicReference<Integer> MINIMUM_DISTANCE_TO_NEX = new AtomicReference<>(1);

    private AtomicReference<Integer> nexDiedOnTick = new AtomicReference<>(-1);

    private int nexImmuneTick = -1;

    @Getter
    private static Set<WorldPoint> dangerousTiles = new HashSet<>();

    private final Object dangerousTilesLock = new Object();

    private final AtomicReference<Boolean> isInPrison = new AtomicReference<>(false);

    private final AtomicReference<WorldPoint> optimalTile = new AtomicReference<>(null);

    @Getter
    private static Set<WorldPoint> spikeTiles = new HashSet<>();

    public final AtomicReference<WorldPoint> simulatedPlayerLocationAfterAttack = new AtomicReference<>(null);
    public final AtomicReference<Boolean> canAttackSafelyThisTick = new AtomicReference<>(false);
    private final AtomicReference<Boolean> deathChargeCasted = new AtomicReference<>(false);
    private final AtomicReference<Integer> nextDeathChargeHp = new AtomicReference<>(200);
    private final AtomicReference<Integer> nextThrallOnTick = new AtomicReference<>(-1);

    public FightNexState(AutoNexPlugin plugin) {
        super();
        this.plugin = plugin;
        this.nextEatAtHp = generateNextEatAtHp();
        this.nextPrayerPotionAt = generateNextPrayerPotAt();
        this.nextStaminaAt = generateNextStatimeAt();
        this.nextRandomWait.set(generateNextRandomWait());
    }

    private int nextEatAtHp;
    private int nextPrayerPotionAt;
    private int nextStaminaAt;
    private static AtomicReference<Integer> lastAteOnTick = new AtomicReference<>(-1);
    AtomicReference<NexPhase> currPhase = new AtomicReference<>(null);

    private AtomicReference<Minion> currMinion = new AtomicReference<>(null);

    private static final int POISON_VALUE_CUTOFF = 0; // Antivenom < -38 <= Antipoison < 0

    private AtomicReference<Integer> nextRandomWait = new AtomicReference<>(8);

    private AtomicReference<Boolean> shouldAvoidNex = new AtomicReference<>(false);

    @Override
    public String name() {
        return "FightNex";
    }

    public int generateNextEatAtHp() {
        return Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.random(25, 35);
    }

    public int generateNextRandomWait() {
        return Utility.random(15, 20);
    }


    public int generateNextPrayerPotAt() {
        return Utility.random(30, 40);
    }

    public int generateNextStatimeAt() {
        return Utility.random(15, 25);
    }

    public int generateNextDeathChargeAt() {
        return Utility.random(204, 310);
    }

    public boolean isNexPresentAndAttackable() {
        var nex = getNex();
        if (nex == null || nex.isDead()) {
            return false;
        }
        var nexComposition = NPCQuery.getNPCComposition(nex);
        if (nexComposition == null) return false;
        var nexActions = nexComposition.getActions();
        return nexActions != null && Arrays.stream(nexActions).anyMatch(a -> a != null && a.equalsIgnoreCase("attack"));
    }

    public boolean isNexPresent() {
        var nex = getNex();
        return nex != null;
    }

    public WorldPoint getWaitSpot() {
        var altar = TileObjects.search().withId(AutoNexPlugin.NEX_ALTAR).nearestToPlayer();
        if (altar.isEmpty()) return null;

        if (plugin.config.waitSpot() == WaitSpot.EDGE_OPTIMAL) {
            return altar.get().getWorldLocation().dx(-23).dy(8);
        } else if (plugin.config.waitSpot() == WaitSpot.EDGE_RANDOM) {
            var edgeWaitTile = altar.get().getWorldLocation().dx(-23).dy(8);
            List<WorldPoint> possibleEdgeWaitTile = Arrays.asList(edgeWaitTile, edgeWaitTile.dx(1).dy(1), edgeWaitTile.dx(-1).dy(-1));
            return possibleEdgeWaitTile.get(Utility.random(0, 2));
        } else if (plugin.config.waitSpot() == WaitSpot.MIDDLE_OPTIMAL) {
            return altar.get().getWorldLocation().dx(-15).dy(9);
        } else if (plugin.config.waitSpot() == WaitSpot.MIDDLE_RANDOM) {
            return altar.get().getWorldLocation().dx(-15).dy(9).dx(Utility.random(-1, 1)).dy(Utility.random(-1, 0));
        }

        return null;
    }

    public boolean handleNexSpawnWait() {
        log.debug("handleNexSpawnWait");
        if (isNexPresent()) {
            return false;
        }
        if (Utility.getTickCount() - nexDiedOnTick.get() < nextRandomWait.get() + 5) return false;
        var desiredWaitTile = getWaitSpot();
        if (desiredWaitTile == null) return false;
        if (Walking.getPlayerLocation().distanceTo(desiredWaitTile) <= 3) {
            return false;
        }
        Walking.sceneWalk(desiredWaitTile);
        return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(desiredWaitTile) == 0, 3000, 200);
    }

    private boolean handleDeathCharge() {
        log.debug("handleDeathCharge");
        if (!plugin.config.useDeathCharge()) return false;
        if (deathChargeCasted.get()) return false;
        var nex = getNex();
        if (nex == null) return false;
        int currBossHp = Utility.getVarbitValue(Varbits.BOSS_HEALTH_CURRENT);
        if (currBossHp > nextDeathChargeHp.get() || currBossHp == 0) return false;
        if (Necromancy.DEATH_CHARGE.tryCast("Cast")) {
            deathChargeCasted.set(true);
            nextDeathChargeHp.set(generateNextDeathChargeAt());
            return true;
        }
        return false;
    }

    private boolean handleThralls() {
        log.debug("handleThralls");
        var selectedThrall = plugin.config.selectedThrall().getThrall();
        if (selectedThrall == null) return false;
        var nex = getNex();
        if (nex == null) return false;
        if (Utility.getVarbitValue(Varbits.BOSS_HEALTH_CURRENT) == 0) return false;
        if (Utility.getTickCount() < nextThrallOnTick.get()) return false;
        if (selectedThrall.tryCast("Cast")) {
            nextThrallOnTick.set(Utility.getTickCount() + Utility.random(94, 105));
            return true;
        }
        return false;
    }


    public NPC getDesiredMinion() {
        var fumus = NPCs.search().withName("Fumus").alive().first();
        if (fumus.isPresent()) return fumus.get();
        var umbra = NPCs.search().withName("Umbra").alive().first();
        if (umbra.isPresent()) return umbra.get();
        var cruor = NPCs.search().withName("Cruor").alive().first();
        if (cruor.isPresent()) return cruor.get();
        var glacies = NPCs.search().withName("Glacies").alive().first();
        return glacies.orElse(null);
    }

    public WorldPoint getOptimalTile() {
        if (!isNexPresent()) return null;
        var nexWorldArea = getNex().getWorldArea();
        var playerLoc = Walking.getPlayerLocation();
        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        List<WorldPoint> tiles = new ArrayList<>();
        var nex = getNex();
        for (var dx = -35; dx <= 35; dx++) {
            for (var dy = -35; dy <= 35; dy++) {
                var tile = playerLoc.dx(dx).dy(dy);
                if (dangerousTiles.contains(tile)) continue;
                if (!reachabilityMap.isReachable(tile)) continue;
                if (nex.getWorldArea().distanceTo(tile) < MINIMUM_DISTANCE_TO_NEX.get()) continue;
                tiles.add(tile);
            }
        }

        if (plugin.config.shouldRandomizeOptimalTile() && isNexPresentAndAttackable()) {
            Collections.shuffle(tiles);

            var closestTileCost = tiles.stream().mapToInt(reachabilityMap::getCostTo).min().orElse(0);
            tiles.sort(Comparator.comparingInt(t -> Math.max(closestTileCost + 1, reachabilityMap.getCostTo(t))));
        } else {
            tiles.sort(Comparator.comparingInt(reachabilityMap::getCostTo));
        }

        if (tiles.isEmpty()) {
            Utility.sendGameMessage("No optimal tiles available", "AutoNex");
            return null;
        }

        var minionWeShouldCareAbout = getDesiredMinion();
        // No more minions left, no calculations to do
        if (minionWeShouldCareAbout == null) {
            var tilesWithinRangeOfNex = tiles.stream().filter(
                    t -> t.distanceTo(nexWorldArea) <= plugin.attackTickTracker.getPlayerAttackRange()
            ).collect(Collectors.toList());
            if (tilesWithinRangeOfNex.size() > 0) return tilesWithinRangeOfNex.get(0);
            return null;
        } else {
            NPC primaryTarget = currMinion.get() != null ? minionWeShouldCareAbout : nex;
            WorldArea primaryTargetWorldArea = currMinion.get() != null ? minionWeShouldCareAbout.getWorldArea() : nex.getWorldArea();
            WorldArea secondaryTargetWorldArea = primaryTarget.equals(nex) ? minionWeShouldCareAbout.getWorldArea() : nex.getWorldArea();
            // Try to find tile within the range of nex and next minion
            var sortedTiles = tiles.stream().filter(
                            t -> t.distanceTo(primaryTargetWorldArea) <= plugin.attackTickTracker.getPlayerAttackRange()
                    ).sorted(Comparator.comparingInt(t -> Math.max(t.distanceTo(secondaryTargetWorldArea), plugin.attackTickTracker.getPlayerAttackRange())))
                    .sorted(Comparator.comparingInt(t -> t.toWorldArea().hasLineOfSightTo(PaistiUtils.getClient(), secondaryTargetWorldArea) ? 0 : 1))
                    .sorted(Comparator.comparingInt(t -> t.toWorldArea().hasLineOfSightTo(PaistiUtils.getClient(), primaryTargetWorldArea) ? 0 : 1))
                    .collect(Collectors.toList());
            if (!sortedTiles.isEmpty()) {
                return sortedTiles.get(0);
            }
            // If no available tile within range of targets, return a safe tile
            var tilesWithinRangeOfNex = tiles.stream().filter(
                    t -> t.distanceTo(nexWorldArea) <= plugin.attackTickTracker.getPlayerAttackRange()
            ).collect(Collectors.toList());
            if (!tilesWithinRangeOfNex.isEmpty()) {
                currMinion.set(null);
                return tilesWithinRangeOfNex.get(0);
            }
            return null;
        }
    }

    @Override
    public boolean shouldExecuteState() {
        return plugin.isInsideNexRoom();
    }

    @Override
    public void threadedOnGameTick() {

    }

    @Subscribe(priority = 5)
    public void onGameTick(GameTick e) {
        if (!shouldExecuteState()) return;

        optimalTile.set(getOptimalTile());

        watchNexHp();

        var relevantNpcs = NPCs.search().withinDistance(32).result();
        var _tickSimulation = new NPCTickSimulation(PaistiUtils.getClient(), plugin.attackTickTracker, relevantNpcs);
        var target = currMinion.get() == null ? getNex() : getDesiredMinion();
        if (target != null) {
            _tickSimulation.getPlayerState().setInteracting(target);
            _tickSimulation.simulatePlayerTick(PaistiUtils.getClient());
        }
        var newPlayerLocation = _tickSimulation.getPlayerState().getArea().toWorldPoint();
        simulatedPlayerLocationAfterAttack.set(newPlayerLocation);
        canAttackSafelyThisTick.set(true);


        if (dangerousTiles.stream().anyMatch(t -> t.equals(newPlayerLocation) || (getNex() != null && newPlayerLocation.distanceTo(getNex().getWorldArea()) < MINIMUM_DISTANCE_TO_NEX.get()))) {
            canAttackSafelyThisTick.set(false);
        }
    }

    private boolean handleEmergencyRestore() {
        log.debug("handleEmergencyRestore");
        if (Utility.getTickCount() - lastAteOnTick.get() < 3) return false;
        if (Utility.getBoostedSkillLevel(Skill.PRAYER) <= 10) {
            BoostPotion prayerBoostPot = BoostPotion.PRAYER_POTION.findInInventory().isEmpty() ? BoostPotion.SUPER_RESTORE : BoostPotion.PRAYER_POTION;
            var potionInInventory = prayerBoostPot.findInInventory();
            if (potionInInventory.isPresent()) {
                var clicked = Interaction.clickWidget(potionInInventory.get(), "Drink");
                if (clicked) {
                    nextPrayerPotionAt = generateNextPrayerPotAt();
                    lastAteOnTick.set(Utility.getTickCount());
                }
                return clicked;
            }
        }
        return false;
    }

    private boolean handlePrayerRestore() {
        log.debug("handlePrayerRestore");
        if (Utility.getTickCount() - lastAteOnTick.get() < 3) return false;
        if (Utility.getBoostedSkillLevel(Skill.PRAYER) <= nextPrayerPotionAt) {
            BoostPotion prayerBoostPot = BoostPotion.PRAYER_POTION.findInInventoryWithLowestDose().isEmpty() ? BoostPotion.SUPER_RESTORE : BoostPotion.PRAYER_POTION;
            var potionInInventory = prayerBoostPot.findInInventoryWithLowestDose();
            if (potionInInventory.isPresent()) {
                var clicked = Interaction.clickWidget(potionInInventory.get(), "Drink");
                if (clicked) {
                    nextPrayerPotionAt = generateNextPrayerPotAt();
                    lastAteOnTick.set(Utility.getTickCount());
                }
                return clicked;
            }
        }
        return false;
    }

    private boolean handleStatRestore() {
        log.debug("handleStatRestore");
        if (plugin.config.useRemedy()) {
            var remedyVal = Utility.getVarbitValue(Varbits.MENAPHITE_REMEDY);
            if (remedyVal > 0) return false;
            var remedyPotions = Inventory.search().matchesWildCardNoCase("Menaphite remedy*").result();
            if (!remedyPotions.isEmpty()) return false;
        }
        if (Utility.getTickCount() - lastAteOnTick.get() < 3) return false;
        if (Utility.getBoostedSkillLevel(Skill.RANGED) < Utility.getRealSkillLevel(Skill.RANGED) - 10) {
            BoostPotion statRestorePotion = BoostPotion.SUPER_RESTORE;
            var potionInInventory = statRestorePotion.findInInventoryWithLowestDose();
            if (potionInInventory.isPresent()) {
                var clicked = Interaction.clickWidget(potionInInventory.get(), "Drink");
                Utility.sendGameMessage("Using restore", "AutoNex");
                if (clicked) {
                    lastAteOnTick.set(Utility.getTickCount());
                }
                return clicked;
            }
        }
        return false;
    }

    private boolean isHoldingNexAggro() {
        var nex = getNex();
        if (nex == null) return false;
        var nexTarget = nex.getInteracting();
        var client = PaistiUtils.getClient();
        var player = client.getLocalPlayer();
        return player.equals(nexTarget);
    }

    public boolean handlePrayers() {
        log.debug("handlePrayers");
        var offensivePrayer = plugin.getOffensivePray().get();
        var distanceToNex = getNexDistance();
        if (!isNexPresentAndAttackable()) {
            if (PPrayer.PROTECT_FROM_MAGIC.isActive() || PPrayer.PROTECT_FROM_MISSILES.isActive() || offensivePrayer.isActive() || PPrayer.PROTECT_FROM_MELEE.isActive()) {
                offensivePrayer.setEnabled(false);
                PPrayer.PROTECT_FROM_MISSILES.setEnabled(false);
                PPrayer.PROTECT_FROM_MAGIC.setEnabled(false);
                PPrayer.PROTECT_FROM_MELEE.setEnabled(false);
                return true;
            }
        } else if (isInPrison.get()) {
            if (!offensivePrayer.isActive() || !PPrayer.PROTECT_FROM_MISSILES.isActive()) {
                Utility.sendGameMessage("Attempting to pray against ice prison", "AutoNex");
                offensivePrayer.setEnabled(true);
                PPrayer.PROTECT_FROM_MISSILES.setEnabled(true);
                return true;
            }
        } else if (isNexPresentAndAttackable() && currPhase.get() == NexPhase.SHADOW_PHASE) {
            if (!offensivePrayer.isActive() || !PPrayer.PROTECT_FROM_MISSILES.isActive()) {
                offensivePrayer.setEnabled(true);
                PPrayer.PROTECT_FROM_MISSILES.setEnabled(true);
                return true;
            }
        } else if (isNexPresentAndAttackable() && currPhase.get() == NexPhase.ZAROS_PHASE && isHoldingNexAggro() && distanceToNex <= 1) {
            if (!offensivePrayer.isActive() || !PPrayer.PROTECT_FROM_MELEE.isActive()) {
                Utility.sendGameMessage("Attempting to pray against Nex's melee attacks", "AutoNex");
                offensivePrayer.setEnabled(true);
                PPrayer.PROTECT_FROM_MELEE.setEnabled(true);
                return true;
            }
        } else if (isNexPresentAndAttackable() && (!offensivePrayer.isActive() || !PPrayer.PROTECT_FROM_MAGIC.isActive())) {
            offensivePrayer.setEnabled(true);
            PPrayer.PROTECT_FROM_MAGIC.setEnabled(true);
            return true;
        }
        return false;
    }

    private boolean handleEating() {
        log.debug("handleEating");
        if (Utility.getTickCount() - lastAteOnTick.get() < 3) return false;
        var isBelowHpTreshold = isNexPresent() ? Utility.getBoostedSkillLevel(Skill.HITPOINTS) <= nextEatAtHp : Utility.getBoostedSkillLevel(Skill.HITPOINTS) <= Utility.getRealSkillLevel(Skill.HITPOINTS) - 5;
        var isMinusStatsAndNotOverhealed = Utility.getBoostedSkillLevel(Skill.RANGED) < Utility.getRealSkillLevel(Skill.RANGED) - 5 && Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.getBoostedSkillLevel(Skill.HITPOINTS) > 0;
        if (isBelowHpTreshold || isMinusStatsAndNotOverhealed) {
            if (eatFood()) {
                nextEatAtHp = generateNextEatAtHp();
                lastAteOnTick.set(Utility.getTickCount());
                return true;
            }
        }
        return false;
    }

    NPC cachedDesiredTarget = null;
    int cachedDesiredTargetTick = -1;

    public NPC getDesiredTarget() {
        if (cachedDesiredTarget != null && cachedDesiredTargetTick == Utility.getTickCount()) {
            return cachedDesiredTarget;
        }
        var desiredMinion = currMinion.get();
        if (desiredMinion != null) {
            var minion = NPCs.search().withName(desiredMinion.toString()).withAction("Attack").alive().first();
            if (minion.isEmpty()) {
                var nex = getNex();
                if (nex == null) {
                    cachedDesiredTarget = null;
                } else {
                    var nexComposition = NPCQuery.getNPCComposition(nex);
                    if (nexComposition == null) {
                        cachedDesiredTarget = null;
                    } else {
                        var nexActions = nexComposition.getActions();
                        cachedDesiredTarget = nexActions != null && Arrays.stream(nexActions).anyMatch(a -> a != null && a.equalsIgnoreCase("attack")) ? nex : null;
                    }
                }
            } else if (minion.isPresent()) {
                cachedDesiredTarget = minion.get();
            }
        } else {
            var nex = getNex();
            if (nex == null) {
                cachedDesiredTarget = null;
            } else {
                var nexComposition = NPCQuery.getNPCComposition(nex);
                if (nexComposition == null) {
                    cachedDesiredTarget = null;
                } else {
                    var nexActions = nexComposition.getActions();
                    cachedDesiredTarget = nexActions != null && Arrays.stream(nexActions).anyMatch(a -> a != null && a.equalsIgnoreCase("attack")) ? nex : null;
                }
            }
        }
        cachedDesiredTargetTick = Utility.getTickCount();
        return cachedDesiredTarget;
    }

    private boolean handleAttacking() {
        log.debug("handleAttacking");
        if (Utility.getTickCount() - nexDiedOnTick.get() < 20) return false;

        if (getNexDistance() < 1) {
            return false;
        }

        if (shouldAvoidNex.get() && getNexDistance() < MINIMUM_DISTANCE_TO_NEX.get()) {
            Utility.sendGameMessage("Not attacking Nex due to containment", "AutoNex");
            return false;
        }

        var desiredTarget = getDesiredTarget();

        if (desiredTarget == null) {
            return false;
        }

        var target = Utility.getInteractionTarget();

        if (desiredTarget == target) {
            return false;
        }

        if (!plugin.config.allowDrag()) {
            if (desiredTarget.getWorldArea().distanceTo(Walking.getPlayerLocation()) > plugin.attackTickTracker.getPlayerAttackRange()) {
                return false;
            }
        }

        boolean attackReady = plugin.attackTickTracker.getTicksUntilNextAttack() <= 1;

        var opTile = optimalTile.get();
        if (opTile == null) return false;

        if (!opTile.equals(Walking.getPlayerLocation())) {
            if (!attackReady) return false;
        }

        if (shouldAvoidNex.get()) {
            if (getNexDistance() < MINIMUM_DISTANCE_TO_NEX.get()) {
                return false;
            }
        }

        if (!canAttackSafelyThisTick.get()) return false;

        if (Interaction.clickNpc(desiredTarget, "Attack")) {
            //Utility.sendGameMessage("Attempting to attack: " + desiredTarget.getName(), "AutoNex");
            return Utility.sleepUntilCondition(() -> Utility.getInteractionTarget() == desiredTarget, 1800, 50);
        }

        return false;
    }

    private boolean isInteractingWithNex() {
        var target = Utility.getInteractionTarget();
        if (target == null || target.getName() == null) return false;
        return target.getName().equalsIgnoreCase("Nex");
    }

    public Optional<Widget> findInInventoryWithLowestDose(String potionNameWithWildCard) {
        var matchingItems = Inventory.search().matchesWildCardNoCase(potionNameWithWildCard).result();

        matchingItems.sort(Comparator.comparingInt(i -> {
            Matcher matcher = dosePattern.matcher(i.getName());
            while (matcher.find()) {
                if (matcher.group(1) != null) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
            return Integer.MAX_VALUE;
        }));
        if (matchingItems.isEmpty()) return Optional.empty();
        return Optional.of(matchingItems.get(0));
    }

    public boolean eatFood() {
        var foodItem = plugin.getFoodItems().stream().findFirst();
        var saradominBrew = findInInventoryWithLowestDose("saradomin brew*");
        if (foodItem.isPresent()) {
            //Utility.sendGameMessage("Eating " + foodItem.get().getName(), "AutoNex");
            return Interaction.clickWidget(foodItem.get(), "Eat", "Drink");
        } else if (saradominBrew.isPresent()) {
            //Utility.sendGameMessage("Drinking " + saradominBrew.get().getName(), "AutoNex");
            return Interaction.clickWidget(saradominBrew.get(), "Eat", "Drink");
        }
        return false;
    }

    public boolean handleStatusEffectPotions() {
        log.debug("handleStatusEffectPotions");
        var poisonVarp = Utility.getVarpValue(VarPlayer.POISON);
        var isAntipoisonActive = poisonVarp < POISON_VALUE_CUTOFF;
        if (isAntipoisonActive) return false;

        if (Utility.getTickCount() - lastAteOnTick.get() < 3) return false;

        var drankPot = Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            var statusPotions = Arrays.stream(StatusPotion.values()).filter(potion -> potion.findInInventory().isPresent()).collect(Collectors.toList());
            var antiVenoms = statusPotions
                    .stream()
                    .filter(potion -> potion.findEffect(PotionStatusEffect.StatusEffect.ANTIVENOM) != null)
                    .collect(Collectors.toList());
            for (var antiVenom : antiVenoms) {
                if (antiVenom.drink()) {
                    //Utility.sendGameMessage("Drank " + antiVenom.name(), "AutoNex");
                    lastAteOnTick.set(Utility.getTickCount());
                    return true;
                }
            }
            return false;
        }));
        return drankPot;
    }

    public boolean handleRemedy() {
        log.debug("handleRemedy");
        if (Utility.getTickCount() - lastAteOnTick.get() < 3) return false;
        if (!plugin.config.useRemedy()) return false;
        var remedyVal = Utility.getVarbitValue(Varbits.MENAPHITE_REMEDY);
        if (remedyVal > 0) return false;
        var remedyPotion = findInInventoryWithLowestDose("Menaphite remedy*");
        if (remedyPotion.isEmpty()) return false;
        if (Interaction.clickWidget(remedyPotion.get(), "Eat", "Drink")) {
            lastAteOnTick.set(Utility.getTickCount());
            return true;
        }
        return false;
    }

    public int getNexDistance() {
        var nex = getNex();
        if (nex == null) {
            return -1;
        }
        return Walking.getPlayerLocation().distanceTo(nex.getWorldArea());
    }

    private NPC _cachedNex = null;
    private int _cachedNexTick = -1;

    public NPC getNex() {
        if (_cachedNex != null && _cachedNexTick == Utility.getTickCount()) {
            return _cachedNex;
        }
        var nex = NPCs.search().withName("Nex").first();
        if (nex.isEmpty()) {
            _cachedNex = null;
            _cachedNexTick = Utility.getTickCount();
            return _cachedNex;
        }
        _cachedNexTick = Utility.getTickCount();
        _cachedNex = nex.get();
        return _cachedNex;
    }

    private final Set<String> _npcsToTrack = new HashSet<>(Arrays.asList("Nex", "Fumus", "Cruor", "Glacies", "Umbra"));
    private List<NPC> _cachedNexNpcs;
    private int _cachedNexNpcsTick = -1;

    public List<NPC> getNpcs() {
        if (_cachedNexNpcs != null && _cachedNexNpcsTick == Utility.getTickCount()) {
            return _cachedNexNpcs;
        }

        List<NPC> npcs = NPCs.search().alive().result().stream()
                .filter(npc -> _npcsToTrack.contains(npc.getName()))
                .collect(Collectors.toList());

        if (npcs.isEmpty()) {
            _cachedNexNpcs = null; // Clear the cache
        } else {
            _cachedNexNpcs = npcs; // Update the cache
            _cachedNexNpcsTick = Utility.getTickCount();
        }

        return _cachedNexNpcs;
    }

    public boolean handleNexDistance() {
        log.debug("handleNexDistance");
        if (getNex() == null) return false;
        if (getNexDistance() >= MINIMUM_DISTANCE_TO_NEX.get()) return false;

        var playerLoc = Walking.getPlayerLocation();

        var opTile = optimalTile.get();

        if (opTile == null) {
            //Utility.sendGameMessage("Failed to find optimal tile during distance handling", "AutoNex");
            return false;
        }

        if (opTile.equals(playerLoc)) {
            //Utility.sendGameMessage("Already in optimal tile", "AutoNex");
            return false;
        }

        Walking.sceneWalk(opTile);

        return Utility.sleepUntilCondition(() -> {
            var nexDistance = getNexDistance();
            var newPlayerLoc = Walking.getPlayerLocation();
            return nexDistance >= MINIMUM_DISTANCE_TO_NEX.get() && !dangerousTiles.contains(newPlayerLoc) || newPlayerLoc.equals(opTile);
        }, 1200, 50);
    }

    public boolean handleImportantLooting() {
        log.debug("handleImportantLooting");
        var groundItems = TileItems.search().filter(itm -> Math.max(itm.getStackGePrice(), itm.getStackHaPrice()) >= 200000).result();
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
        log.debug("handleRegularLooting");
        try {
            if (isNexPresent()) return false;
            var groundItems = plugin.config.lootOnlyMine() ? TileItems.search().filter(itm -> (itm.getName().contains("Shark") || Math.max(itm.getStackGePrice(), itm.getStackHaPrice()) > 3000) && !itm.getName().contains("dragon bolts") && itm.isMine()).result()
                    : TileItems.search().filter(itm -> (itm.getName().contains("Shark") || Math.max(itm.getStackGePrice(), itm.getStackHaPrice()) > 3000) && !itm.getName().contains("dragon bolts")).result();
            if (groundItems.isEmpty()) return false;
            groundItems.sort(Comparator.comparingInt(itm -> -Math.max(itm.getStackGePrice(), itm.getStackHaPrice())));
            LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
            groundItems.sort(Comparator.comparingInt(itm -> reachabilityMap.getCostTo(itm.getLocation())));
            Utility.sleepUntilCondition(() -> Utility.getTickCount() - lastAteOnTick.get() >= 1, 1200, 150);
            boolean pickedUpLoot = false;
            for (var groundItem : groundItems) {
                if (Inventory.isFull()) {
                    if (!groundItem.isStackable() || Inventory.search().withId(groundItem.getId()).first().isEmpty()) {
                        continue;
                    }
                }
                //Utility.sendGameMessage("Attempting to loot regular items: " + groundItem.getName(), "AutoNex");
                Interaction.clickGroundItem(groundItem, "Take");
                var quantityBeforeClick = Inventory.getItemAmount(groundItem.getId());
                if (Utility.sleepUntilCondition(() -> Inventory.getItemAmount(groundItem.getId()) > quantityBeforeClick, 6000, 600)) {
                    pickedUpLoot = true;
                }
            }
            return pickedUpLoot;
        } catch (Exception e) {
            log.debug("ERROR picking up the loot");
            return false;
        }
    }


    public boolean handleStatBoostPotions() {
        log.debug("handleStatBoostPotions");
        if (Utility.getTickCount() - lastAteOnTick.get() < 3) return false;
        if (isNexPresent()) return false;
        if (Utility.getRealSkillLevel(Skill.RANGED) != Utility.getBoostedSkillLevel(Skill.RANGED)) {
            return false;
        }
        var potionToDrink = Utility.runOnClientThread(() -> Arrays.stream(BoostPotion.values()).filter(potion -> potion.findBoost(Skill.RANGED) != null).findFirst());

        if (potionToDrink == null || potionToDrink.isEmpty()) return false;

        if (potionToDrink.get().drink()) {
            //Utility.sendGameMessage("Drank " + potionToDrink.get().name(), "AutoNex");
            lastAteOnTick.set(Utility.getTickCount());
            return true;
        }
        return false;
    }

    public boolean handleEmergencyExit() {
        log.debug("handleEmergencyExit");
        boolean isLowHp = Utility.getBoostedSkillLevel(Skill.HITPOINTS) < 50;
        boolean isLowPrayer = Utility.getBoostedSkillLevel(Skill.PRAYER) < 20;
        if (isLowHp || isLowPrayer) {
            if (isLowHp) {
                var brews = Inventory.search().matchesWildCardNoCase("Saradomin brew*");
                var foods = plugin.getFoodItems().stream().findFirst();
                if (brews.empty() && foods.isEmpty()) {
                    //Utility.sendGameMessage("Attempting to exit Nex due to low HP and no food left");
                    var altar = TileObjects.search().withName("Altar").withAction("Teleport").nearestToPlayer();
                    if (altar.isEmpty()) {
                        //Utility.sendGameMessage("Could not find altar");
                        return false;
                    }
                    var altarLoc = altar.get().getWorldLocation();
                    Interaction.clickTileObject(altar.get(), "Teleport");
                    return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(altarLoc) > 30, 10000, 300);
                }
            }
            if (isLowPrayer) {
                var restorePotions = Inventory.search().matchesWildCardNoCase("Super restore*");
                var prayerPotions = Inventory.search().matchesWildCardNoCase("Prayer potion*");
                if (restorePotions.empty() && prayerPotions.empty()) {
                    //Utility.sendGameMessage("Attempting to exit Nex due to low prayer and no potions left");
                    var altar = TileObjects.search().withName("Altar").withAction("Teleport").nearestToPlayer();
                    if (altar.isEmpty()) {
                        //Utility.sendGameMessage("Could not find altar");
                        return false;
                    }
                    var altarLoc = altar.get().getWorldLocation();
                    Interaction.clickTileObject(altar.get(), "Teleport");
                    return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(altarLoc) > 30, 10000, 300);
                }
            }
        }
        return false;
    }

    public boolean handleShadowBarrage() {
        log.debug("handleShadowBarrage");
        if (dangerousTiles.isEmpty()) return false;
        var playerLoc = Walking.getPlayerLocation();

        if (dangerousTiles.contains(playerLoc)) {
            var opTile = optimalTile.get();
            if (opTile == null) {
                Utility.sendGameMessage("Failed to find optimal tile during smoke barrage", "AutoNex");
                return false;
            }
            if (opTile.equals(playerLoc)) {
                return false;
            }

            Walking.sceneWalk(opTile);
            return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().equals(opTile), 1200, 50);
        }
        return false;
    }

    public boolean handleImprovePosition() {
        log.debug("handleImprovePosition");
        var playerLoc = Walking.getPlayerLocation();

        var opTile = optimalTile.get();

        if (opTile == null) {
            //Utility.sendGameMessage("Failed to find optimal tile for position optimisation", "AutoNex");
            return false;
        }
        if (opTile.equals(playerLoc)) {
            //Utility.sendGameMessage("Already in optimal position (handleImprovePosition)", "AutoNex");
            return false;
        }
        if (opTile.equals(Walking.getDestination())) {
            return false;
        }

        if (shouldAvoidNex.get()) {
            var nex = getNex();
            if (nex != null) {
                LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
                var tiles = reachabilityMap.getPathTo(opTile);
                var nexWorldArea = nex.getWorldArea();
                if (!nexWorldArea.contains(playerLoc)) {
                    for (var tile : tiles) {
                        if (nexWorldArea.contains(tile)) {
                            Utility.sendGameMessage("Not passing through Nex", "AutoNex");
                            return false;
                        }
                    }
                }
            }
        }

        if (plugin.config.shouldRandomizeOptimalTile() && isNexPresentAndAttackable()) {
            var nex = getNex();
            var minionWeShouldCareAbout = getDesiredMinion();
            NPC primaryTarget = currMinion.get() != null ? minionWeShouldCareAbout : nex;

            var isNotTooCloseToNex = getNexDistance() >= MINIMUM_DISTANCE_TO_NEX.get();
            var isNotInDangerousTile = !dangerousTiles.contains(playerLoc);
            var isInAttackRangeOfPrimaryTarget = primaryTarget == null || primaryTarget.getWorldArea().distanceTo(playerLoc) <= plugin.attackTickTracker.getPlayerAttackRange();
            var isOptimalTileNear = playerLoc.distanceTo(opTile) <= 2;

            if (isNotTooCloseToNex && isNotInDangerousTile && isInAttackRangeOfPrimaryTarget && isOptimalTileNear) {
                return false;
            }
        }

        Walking.sceneWalk(opTile);
        var originalPlayerLoc = Walking.getPlayerLocation();
        return Utility.sleepUntilCondition(() -> {
            var newPlayerLoc = Walking.getPlayerLocation();
            return !newPlayerLoc.equals(originalPlayerLoc) && !dangerousTiles.contains(newPlayerLoc);
        }, 600, 50);
    }

    public boolean handleSpec() {
        log.debug("handleSpec");
        if (!plugin.config.useSpecialAttack()) return false;
        if (Utility.getSpecialAttackEnergy() >= plugin.config.specEnergyMinimum() && !Utility.isSpecialAttackEnabled() && isInteractingWithNex()) {
            if (Utility.getTickCount() - nexImmuneTick > 10 && Utility.getVarbitValue(Varbits.BOSS_HEALTH_CURRENT) >= plugin.config.specHpThreshold()) {
                Utility.specialAttack();
                return Utility.sleepUntilCondition(Utility::isSpecialAttackEnabled, 1200, 300);
            }
        }
        return false;
    }

    public final AtomicReference<Boolean> isNexAlive = new AtomicReference<>(false);

    void watchNexHp() {
        if (!isNexAlive.get() && Utility.getVarbitValue(Varbits.BOSS_HEALTH_CURRENT) > 0) {
            isNexAlive.set(true);
        } else if (isNexAlive.get() && Utility.getVarbitValue(Varbits.BOSS_HEALTH_CURRENT) <= 0) {
            currMinion.set(null);
            currPhase.set(null);
            dangerousTiles.clear();
            MINIMUM_DISTANCE_TO_NEX.set(Utility.random(3, 6));
            nexDiedOnTick.set(Utility.getTickCount());
            nextRandomWait.set(generateNextRandomWait());
            plugin.setTotalKillCount(plugin.getTotalKillCount() + 1);
            plugin.setKillsThisTrip(plugin.getKillsThisTrip() + 1);
            isNexAlive.set(false);
            shouldAvoidNex.set(false);
            deathChargeCasted.set(false);
            Utility.sendGameMessage("Nex has died!", "AutoNex");
        }
    }


    public boolean handleToggleRun() {
        log.debug("handleToggleRun");
        if (Walking.isRunEnabled() || Walking.getRunEnergy() < 15) return false;
        return Walking.setRun(true);
    }

    public boolean handleStamina() {
        log.debug("handleStamina");
        if (Walking.getRunEnergy() > nextStaminaAt) return false;
        if (Utility.getTickCount() - lastAteOnTick.get() < 3) return false;
        var staminaPotion = Inventory.search().matchesWildCardNoCase("stamina potion*").first();
        if (staminaPotion.isPresent()) {
            //Utility.sendGameMessage("Drinking " + staminaPotion.get().getName(), "AutoNex");
            lastAteOnTick.set(Utility.getTickCount());
            nextStaminaAt = generateNextStatimeAt();
            return Interaction.clickWidget(staminaPotion.get(), "Eat", "Drink");
        }
        return false;
    }

    public boolean lowOnSupplies() {
        var saradominBrews = Inventory.search().matchesWildCardNoCase("Saradomin brew*").result();
        var superRestores = Inventory.search().matchesWildCardNoCase("Super restore*").result();

        var brewSipCount = 0;
        var superRestoreSipCount = 0;

        for (var brew : saradominBrews) {
            if (brew.getName().contains("4")) brewSipCount += 4;
            else if (brew.getName().contains("3")) brewSipCount += 3;
            else if (brew.getName().contains("2")) brewSipCount += 2;
            else if (brew.getName().contains("1")) brewSipCount += 1;
        }

        for (var restore : superRestores) {
            if (restore.getName().contains("4")) superRestoreSipCount += 4;
            else if (restore.getName().contains("3")) superRestoreSipCount += 3;
            else if (restore.getName().contains("2")) superRestoreSipCount += 2;
            else if (restore.getName().contains("1")) superRestoreSipCount += 1;
        }

        if (brewSipCount < plugin.config.brewMinimum() || superRestoreSipCount < plugin.config.restoreMinimum()) {
            Utility.sendGameMessage("Low on supplies", "AutoNex");
            return true;
        }

        return false;
    }

    public boolean handleSmartExit() {
        log.debug("handleSmartExit");
        if (isNexPresent()) return false;
        if (Utility.getTickCount() - nexDiedOnTick.get() < nextRandomWait.get()) return false;

        var shouldLeaveDueToTeamSettings = plugin.config.leaveAfterKills() && plugin.getKillsThisTrip() >= plugin.config.leaveAfterKillsCount();
        var isSmartRestockEnabledAndLowOnSupplies = plugin.config.smartRestock() && lowOnSupplies();
        var isSmartRestockDisabledAndAbove40Kc = !plugin.config.smartRestock() && plugin.getAncientKc() >= 40;

        if (shouldLeaveDueToTeamSettings || isSmartRestockEnabledAndLowOnSupplies || isSmartRestockDisabledAndAbove40Kc) {
            var altar = TileObjects.search().withName("Altar").withAction("Teleport").nearestToPlayer();
            if (altar.isEmpty()) return false;
            Utility.sendGameMessage("Exiting Nex via altar", "AutoNex");
            Interaction.clickTileObject(altar.get(), "Teleport");
            return Utility.sleepUntilCondition(() -> !plugin.isInsideNexRoom(), 10000, 300);
        }

        return false;
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned e) {
        if (e.getGameObject().getId() == 42942) {
            synchronized (dangerousTilesLock) {
                dangerousTiles.add(e.getGameObject().getWorldLocation());
            }
        }
        /*
        if (e.getGameObject().getId() == 42944) {
            spikeTiles.add(e.getGameObject().getWorldLocation());
        }
         */
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned e) {
        if (e.getGameObject().getId() == 42942) {
            synchronized (dangerousTilesLock) {
                dangerousTiles.clear();
            }
        }
        if (e.getGameObject().getId() == 42943) {
            MINIMUM_DISTANCE_TO_NEX.set(1);
            shouldAvoidNex.set(false);
        }
        if (e.getGameObject().getId() == 42944) {
            spikeTiles.clear();
        }
    }

    @Override
    public void threadedLoop() {
        var isNotInAssistMode = !plugin.config.assistMode();
        if (isNotInAssistMode && handleImportantLooting()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleEmergencyExit()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleShadowBarrage()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (handlePrayers()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleEmergencyRestore()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleEating()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleNexDistance()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handlePrayerRestore()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleStatRestore()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleRemedy()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleStatusEffectPotions()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleSpec()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleThralls()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleDeathCharge()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleRegularLooting()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleSmartExit()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleNexSpawnWait()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleStatBoostPotions()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleToggleRun()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleStamina()) {
            Utility.sleepGaussian(50, 100);
            return;
        }
        if (isNotInAssistMode && handleAttacking()) {
        } else if (isNotInAssistMode) {
            handleImprovePosition();
        }
        Utility.sleepGaussian(50, 100);
    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        // NEX PHASES
        if (event.getMessage().toLowerCase().contains(SMOKE_PHASE_MESSAGE.toLowerCase())) {
            currPhase.set(NexPhase.SMOKE_PHASE);
            MINIMUM_DISTANCE_TO_NEX.set(6);
        } else if (event.getMessage().toLowerCase().contains(SHADOW_PHASE_MESSAGE.toLowerCase())) {
            currPhase.set(NexPhase.SHADOW_PHASE);
            MINIMUM_DISTANCE_TO_NEX.set(plugin.attackTickTracker.getPlayerAttackRange());
        } else if (event.getMessage().toLowerCase().contains(BLOOD_PHASE_MESSAGE.toLowerCase())) {
            currPhase.set(NexPhase.BLOOD_PHASE);
            MINIMUM_DISTANCE_TO_NEX.set(1);
        } else if (event.getMessage().toLowerCase().contains(ICE_PHASE_MESSAGE.toLowerCase())) {
            currPhase.set(NexPhase.ICE_PHASE);
            MINIMUM_DISTANCE_TO_NEX.set(1);
        } else if (event.getMessage().toLowerCase().contains(ZAROS_PHASE_MESSAGE.toLowerCase())) {
            currPhase.set(NexPhase.ZAROS_PHASE);
            MINIMUM_DISTANCE_TO_NEX.set(1);
        }

        // MINIONS
        if (event.getMessage().toLowerCase().contains(FUMUS.toLowerCase() + ",")) {
            currMinion.set(Minion.FUMUS);
        } else if (event.getMessage().toLowerCase().contains(UMBRA.toLowerCase() + ",")) {
            currMinion.set(Minion.UMBRA);
        } else if (event.getMessage().toLowerCase().contains(CRUOR.toLowerCase() + ",")) {
            currMinion.set(Minion.CRUOR);
        } else if (event.getMessage().toLowerCase().contains(GLACIES.toLowerCase() + ",")) {
            currMinion.set(Minion.GLACIES);
        }

        // Nex immunity
        if (event.getMessage().toLowerCase().contains(NEX_IMMUNITY_MESSAGE.toLowerCase())) {
            nexImmuneTick = Utility.getTickCount();
        }

        if (event.getMessage().toLowerCase().contains("torva")) {
            plugin.setSeenUniqueItems(plugin.getSeenUniqueItems() + 1);
        }
        if (event.getMessage().toLowerCase().contains("zaryte")) {
            plugin.setSeenUniqueItems(plugin.getSeenUniqueItems() + 1);
        }
        if (event.getMessage().toLowerCase().contains("horn")) {
            plugin.setSeenUniqueItems(plugin.getSeenUniqueItems() + 1);
        }
        if (event.getMessage().toLowerCase().contains("hilt")) {
            plugin.setSeenUniqueItems(plugin.getSeenUniqueItems() + 1);
        }

        if (event.getMessage().toLowerCase().contains(MVP_MESSAGE)) {
            plugin.setMvps(plugin.getMvps() + 1);
        }

        // Mechanics
        if (event.getMessage().toLowerCase().contains(NEX_PRISON_MESSAGE)) {
            Utility.sendGameMessage("Got trapped in a prison", "AutoNex");
            isInPrison.set(true);
            nextEatAtHp = 90;
        }
        if (event.getMessage().toLowerCase().contains(NEX_PRISON_FREED_MESSAGE)) {
            Utility.sendGameMessage("Got freed from the prison", "AutoNex");
            isInPrison.set(false);
        }
        if (event.getMessage().toLowerCase().contains(NEX_PRISON_PIERCES_MESSAGE)) {
            Utility.sendGameMessage("Got damaged by the prison", "AutoNex");
            isInPrison.set(false);
        }
        if (event.getMessage().toLowerCase().contains(BLOOD_MARKED_MESSAGE)) {
            MINIMUM_DISTANCE_TO_NEX.set(Math.max(plugin.attackTickTracker.getPlayerAttackRange(), 8));
        }
        if (event.getMessage().toLowerCase().contains(BLOOD_MARKED_SUCCESSFUL_MESSAGE) || event.getMessage().toLowerCase().contains(BLOOD_MARKED_FAILED_MESSAGE)) {
            MINIMUM_DISTANCE_TO_NEX.set(1);
        }
        if (event.getMessage().toLowerCase().contains(CONTAINTMENT_MESSAGE)) {
            shouldAvoidNex.set(true);
            MINIMUM_DISTANCE_TO_NEX.set(2);
        }
    }

    @Subscribe(priority = 5000)
    public void onActorDeath(ActorDeath actorDeath) {
        if (!plugin.runner.isRunning()) return;
        Actor actor = actorDeath.getActor();
        if (actor instanceof NPC) {
            if (actor.getName() != null) {
                if (actor.getName().toLowerCase().contains("nex")) {
                    /*
                    currMinion = null;
                    currPhase = null;
                    dangerousTiles.clear();
                    MINIMUM_DISTANCE_TO_NEX.set(3);
                    nexDiedOnTick.set(Utility.getTickCount());
                    nextRandomWait.set(generateNextRandomWait());
                    plugin.setTotalKillCount(plugin.getTotalKillCount() + 1);
                    Utility.sendGameMessage("Nex has died!", "AutoNex");
                     */
                } else if (actor.getName().toLowerCase().contains(FUMUS.toLowerCase()) || actor.getName().toLowerCase().contains(UMBRA.toLowerCase()) || actor.getName().toLowerCase().contains(CRUOR.toLowerCase()) || actor.getName().toLowerCase().contains(GLACIES.toLowerCase())) {
                    Utility.sendGameMessage(actor.getName() + " has died!", "AutoNex");
                    currMinion.set(null);
                }
            }
        }
    }
}
