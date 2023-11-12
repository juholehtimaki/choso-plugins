package com.theplug.PvmHelper.States;

import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.NPCTickSimulation.NPCTickSimulation;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.PvmHelper.PvmHelperPlugin;
import com.theplug.PvmHelper.PvmHelperPluginConfig;
import net.runelite.api.NPC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import java.util.concurrent.atomic.AtomicReference;

public class MuspahHelperState implements State{
    PvmHelperPlugin plugin;
    PvmHelperPluginConfig config;

    public MuspahHelperState(PvmHelperPlugin plugin, PvmHelperPluginConfig config) {
        super();
        this.plugin = plugin;
        this.config = config;
    }

    public enum MuspahPhase {
        MELEE,
        RANGED,
        DARKNESS,
        SHIELD,
        POST_SHIELD
    }

    private static final int MUSPAH_RANGE_FORM_ID = 12077;
    private static final int MUSPAH_MELEE_FORM_ID = 12078;
    private static final int MUSPAH_DARKNESS_FORM_ID = 12082;
    private static final int MUSPAH_SHIELD_FORM_ID = 12079;
    private static final int MUSPAH_POST_SHIELD_FORM_ID = 12080;
    private static final int MUSPAH_MAGE_ATTACK_ANIMATION_ID = 9918;
    private static final int MUSPAH_MELEE_ATTACK_ANIMATION_ID = 9920;
    private static final int MUSPAH_RANGE_ATTACK_ANIMATION_ID = 9922;
    public final AtomicReference<MuspahPhase> currMuspahPhase = new AtomicReference<>(null);
    public final AtomicReference<Integer> magicAttackTick = new AtomicReference<>(-1);
    private static final AtomicReference<Integer> lastMeleeAttackTick = new AtomicReference<>(-1);
    private static final AtomicReference<Integer> muspahLastAttackTick = new AtomicReference<>(-1);
    private static final int EXIT_GAME_OBJECT_ID = 46599;
    private static final int EXIT_GAME_OBJECT_ID2 = 46598;

    private void setPray() {
        Utility.runOnClientThread(() -> {
            var client = PaistiUtils.getClient();
            var relevantNpcs = NPCs.search().withName("Phantom Muspah").result();

            if (relevantNpcs.isEmpty()) {
                plugin.handleDisableAllPrayers();
                return null;
            }
            
            var currPhase = currMuspahPhase.get();
            var shouldCareAboutMagicAttack = Utility.getTickCount() - magicAttackTick.get() == 3;

            var defensivePrayer = shouldCareAboutMagicAttack ? PPrayer.PROTECT_FROM_MAGIC : currPhase == MuspahPhase.MELEE ? PPrayer.PROTECT_FROM_MELEE : PPrayer.PROTECT_FROM_MISSILES;
            var offensivePrayer = currPhase == MuspahPhase.MELEE && config.usingMage() ? plugin.getBestOffensiveMagePrayer() : plugin.getBestOffensiveRangedPrayer();

            if (currPhase != MuspahPhase.SHIELD) {
                if (!defensivePrayer.isActive() || !offensivePrayer.isActive()) {
                    defensivePrayer.setEnabled(true);
                    offensivePrayer.setEnabled(true);
                }
                return null;
            }

            var _tickSimulation = new NPCTickSimulation(client, plugin.attackTickTracker, relevantNpcs);
            _tickSimulation.getPlayerState().setInteracting(client.getLocalPlayer().getInteracting());

            _tickSimulation.simulateNpcsTick(client);
            var prayAgainst = _tickSimulation.shouldPrayAgainst(client);

            var attackingSoon = false;
            var noAttacksToCareAbout = prayAgainst == null;

            if (Utility.getInteractionTarget() instanceof NPC) {
                var npc = (NPC) Utility.getInteractionTarget();
                if (!npc.isDead() && plugin.attackTickTracker.getTicksUntilNextAttack() <= 1) {
                    attackingSoon = true;
                }
            }

            var overheadPrayer = shouldCareAboutMagicAttack ? PPrayer.PROTECT_FROM_MAGIC : attackingSoon && noAttacksToCareAbout && isPlayerInteractingWithMuspah() ? PPrayer.SMITE : PPrayer.PROTECT_FROM_MISSILES;

            if (!overheadPrayer.isActive() || !offensivePrayer.isActive()) {
                overheadPrayer.setEnabled(true);
                offensivePrayer.setEnabled(true);
            }

            return null;
        });
    }

    private boolean isPlayerInteractingWithMuspah() {
        var muspah = getMuspah();
        if (muspah == null) return false;
        var interactionTarget = Utility.getInteractionTarget();
        if (interactionTarget == null) return false;
        return interactionTarget.equals(muspah);
    }

    private NPC _cachedMuspah = null;
    private int _cachedMuspahTick = -1;
    public NPC getMuspah() {
        if (_cachedMuspah != null && _cachedMuspahTick == Utility.getTickCount()) {
            return _cachedMuspah;
        }
        var muspah = NPCs.search().withName("Phantom Muspah").first();
        if (muspah.isEmpty()) {
            _cachedMuspah = null;
            _cachedMuspahTick = Utility.getTickCount();
            return _cachedMuspah;
        }
        _cachedMuspahTick = Utility.getTickCount();
        _cachedMuspah = muspah.get();
        return _cachedMuspah;
    }


    private void updateMuspahPhase() {
        var muspah = NPCs.search().withName("Phantom Muspah").alive().first();
        if (muspah.isPresent()) {
            if (muspah.get().getId() == MUSPAH_RANGE_FORM_ID) currMuspahPhase.set(MuspahPhase.RANGED);
            else if (muspah.get().getId() == MUSPAH_MELEE_FORM_ID) currMuspahPhase.set(MuspahPhase.MELEE);
            else if (muspah.get().getId() == MUSPAH_DARKNESS_FORM_ID) currMuspahPhase.set(MuspahPhase.DARKNESS);
            else if (muspah.get().getId() == MUSPAH_POST_SHIELD_FORM_ID) currMuspahPhase.set(MuspahPhase.POST_SHIELD);
            else if (muspah.get().getId() == MUSPAH_SHIELD_FORM_ID) currMuspahPhase.set(MuspahPhase.SHIELD);
            else currMuspahPhase.set(null);
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        int animation = event.getActor().getAnimation();
        if (animation == MUSPAH_MAGE_ATTACK_ANIMATION_ID) {
            magicAttackTick.set(Utility.getTickCount());
        }
        if (animation == MUSPAH_MELEE_ATTACK_ANIMATION_ID) lastMeleeAttackTick.set(Utility.getTickCount());
        if (animation == MUSPAH_RANGE_ATTACK_ANIMATION_ID || animation == MUSPAH_MAGE_ATTACK_ANIMATION_ID || animation == MUSPAH_MELEE_ATTACK_ANIMATION_ID) {
            muspahLastAttackTick.set(Utility.getTickCount());
        }
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public boolean shouldExecuteState() {
        return TileObjects.search().withId(EXIT_GAME_OBJECT_ID).first().isPresent() || TileObjects.search().withId(EXIT_GAME_OBJECT_ID2).first().isPresent();
    }

    @Override
    public void threadedOnGameTick() {
        setPray();
    }

    @Override
    public void threadedLoop() {
        Utility.sleepGaussian(100, 200);
    }

    @Subscribe
    public void onGameTick(GameTick e) {
        updateMuspahPhase();
    }
}
