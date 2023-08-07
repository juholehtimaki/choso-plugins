package com.example.PvPHelperPlugin.Commands;

import com.example.PaistiUtils.API.*;
import com.example.PaistiUtils.PaistiUtils;
import com.example.PvPHelperPlugin.PvPHelperPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;

@Slf4j


public class AttackCommand implements PvpHelperCommand {

    /*
    P0: 0
P1: 0
MenuActId: 46
ObjectId: 731
ItemId: -1
Opt: Follow
Target: <col=ffffff>rooste059<col=ff00>  (level-81)
ItemOp: -1


P0: 0
P1: 0
MenuActId: 11
ObjectId: 20473
ItemId: -1
Opt: Bank
Target: <col=ffff00>Banker
ItemOp: -1
    * */

    public static final String INSTRUCTION_PREFIX = "ATTACK";

    AttackCommand(){}

    @Override
    public boolean execute() {
        System.out.println("Executing");
        System.out.println(PvPHelperPlugin.getLastTargetInteractedWith());
        if (PvPHelperPlugin.getLastTargetInteractedWith() == null) return false;
        if (PvPHelperPlugin.getLastTargetInteractedWith() instanceof Player) {
            if (Boolean.TRUE.equals(Utility.runOnClientThread(() -> PaistiUtils.getClient().getPlayers().stream().anyMatch(p -> p.equals(PvPHelperPlugin.getLastTargetInteractedWith()))))) {
                return Interaction.clickPlayer((Player) PvPHelperPlugin.getLastTargetInteractedWith(), "Attack");
            }
        }
        if (PvPHelperPlugin.getLastTargetInteractedWith() instanceof NPC) {
            if (NPCs.search().result().stream().anyMatch(npc -> npc.equals(PvPHelperPlugin.getLastTargetInteractedWith()))) {
                return Interaction.clickNpc((NPC) PvPHelperPlugin.getLastTargetInteractedWith(), "Attack");
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
