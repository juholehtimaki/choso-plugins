package com.theplug.AutoPvpPlugin.Commands;

import com.theplug.GearSwitcherPlugin.GearSwitcherPlugin;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.Player;

@Slf4j
public class AttackCommand implements GearSwitcherCommand {

    public static final String INSTRUCTION_PREFIX = "ATTACK";

    AttackCommand() {
    }

    @Override
    public boolean execute() {
        if (GearSwitcherPlugin.getLastTargetInteractedWith() == null) return false;
        if (GearSwitcherPlugin.getLastTargetInteractedWith() instanceof Player) {
            if (Boolean.TRUE.equals(Utility.runOnClientThread(() -> PaistiUtils.getClient().getPlayers().stream().anyMatch(p -> p.equals(GearSwitcherPlugin.getLastTargetInteractedWith()))))) {
                return Interaction.clickPlayer((Player) GearSwitcherPlugin.getLastTargetInteractedWith(), "Attack");
            }
        }
        if (GearSwitcherPlugin.getLastTargetInteractedWith() instanceof NPC) {
            if (NPCs.search().result().stream().anyMatch(npc -> npc.equals(GearSwitcherPlugin.getLastTargetInteractedWith()))) {
                return Interaction.clickNpc((NPC) GearSwitcherPlugin.getLastTargetInteractedWith(), "Attack");
            }
        }
        return false;
    }

    @Override
    public String serializeToString() {
        return INSTRUCTION_PREFIX;
    }

    static public AttackCommand deserializeFromString(String serializedString) {
        if (serializedString.equalsIgnoreCase(INSTRUCTION_PREFIX)) return new AttackCommand();
        throw new IllegalArgumentException("Received unknown command");
    }
}
