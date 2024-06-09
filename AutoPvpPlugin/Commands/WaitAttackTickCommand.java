package com.theplug.AutoPvpPlugin.Commands;

import com.theplug.PaistiUtils.API.AttackTickTracker.AttackTickTracker;
import com.theplug.PaistiUtils.API.Utility;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WaitAttackTickCommand implements GearSwitcherCommand {


    public static final String INSTRUCTION_PREFIX = "WAIT_ATTACK_TICK";

    private AttackTickTracker attackTickTracker;

    WaitAttackTickCommand(AttackTickTracker attackTickTracker) {
        this.attackTickTracker = attackTickTracker;
    }

    @Override
    public boolean execute() {
        return Utility.sleepUntilCondition(() -> attackTickTracker.getTicksUntilNextAttack() <= 1, 3000, 100);
    }

    @Override
    public String serializeToString() {
        return INSTRUCTION_PREFIX;
    }

    static public WaitAttackTickCommand deserializeFromString(String serializedString, AttackTickTracker attackTickTracker) {
        try {
            String command = serializedString.split(":")[0];
            if (!command.equalsIgnoreCase(INSTRUCTION_PREFIX))
                throw new IllegalArgumentException("Received unknown command");
            return new WaitAttackTickCommand(attackTickTracker);
        } catch (Exception e) {
            log.error("Failed to deserialize");
        }
        return null;
    }
}
