package com.theplug.AutoPrayFlickerPlugin;

import com.theplug.PaistiUtils.API.AttackTickTracker.AttackTickTracker;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class NPCDeserializer {
    public static List<AttackTickTracker.INPCAttackTickData> deserialize(String text) {
        List<AttackTickTracker.INPCAttackTickData> npcs = new ArrayList<>();

        String[] lines = text.split("\n"); // Split the text into lines

        for (String line : lines) {
            String[] npcParts = line.split(":");
            if (npcParts.length == 6) { // Ensure all fields are present
                try {
                    int npcId = Integer.parseInt(npcParts[0].trim());
                    int attackTickInterval = Integer.parseInt(npcParts[1].trim());
                    int attackRange = Integer.parseInt(npcParts[2].trim());
                    String[] attackTypes = npcParts[3].split(",");
                    AttackTickTracker.ATTACK_TYPE[] attackType = new AttackTickTracker.ATTACK_TYPE[attackTypes.length];
                    for (int i = 0; i < attackTypes.length; i++) {
                        if (attackTypes[i].equalsIgnoreCase("MELEE")) {
                            attackType[i] = AttackTickTracker.ATTACK_TYPE.MELEE;
                        } else if (attackTypes[i].equalsIgnoreCase("MAGIC")) {
                            attackType[i] = AttackTickTracker.ATTACK_TYPE.MAGIC;
                        } else if (attackTypes[i].equalsIgnoreCase("RANGED")) {
                            attackType[i] = AttackTickTracker.ATTACK_TYPE.RANGED;
                        } else {
                            throw new IllegalArgumentException("(invalid attack type: " + attackTypes[i] + ")");
                        }
                    }
                    int prayPriority = Integer.parseInt(npcParts[4].trim());
                    String[] attackAnimationStr = npcParts[5].trim().split(",");
                    int[] attackAnimations = new int[attackAnimationStr.length];
                    for (int i = 0; i < attackAnimationStr.length; i++) {
                        attackAnimations[i] = Integer.parseInt(attackAnimationStr[i].trim());
                    }
                    AttackTickTracker.INPCAttackTickData npc = new AttackTickTracker.CustomNPCAttackTickData(npcId, attackTickInterval, attackRange, attackType, prayPriority, attackAnimations);
                    log.debug("Deserialized npc -> id: {}, interval: {}, attackRange: {}, attackType: {}, prayPriority: {}, attackAnimations: {}", npc.getNpcId(), npc.getAttackTickInterval(), npc.getAttackRange(), npc.getAttackTypes(), npc.getPrayPriority(), npc.getAttackAnimations());
                    npcs.add(npc);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(line + " -> " + e.getMessage());
                }
            } else {
                throw new IllegalArgumentException(line + "(too few arguments were given)");
            }
        }

        return npcs;
    }
}