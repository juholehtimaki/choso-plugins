package com.theplug.GearSwitcherPlugin.Commands;

import com.theplug.PaistiUtils.API.Utility;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WaitAnimationCommand implements GearSwitcherCommand {

    int animationId;
    // Gmaul = 1667
    // AGS = 7644
    // Voidwaker = 1378

    public static final String INSTRUCTION_PREFIX = "WAIT_ANIM";

    WaitAnimationCommand(int animationId) {
        this.animationId = animationId;
    }

    @Override
    public boolean execute() {
        return Utility.sleepUntilCondition(() -> Utility.getLocalAnimation() == animationId, 3000, 100);
    }

    @Override
    public String serializeToString() {
        return INSTRUCTION_PREFIX + ":" + animationId;
    }

    static public WaitAnimationCommand deserializeFromString(String serializedString) {
        try {
            String command = serializedString.split(":")[0];
            if (!command.equalsIgnoreCase(INSTRUCTION_PREFIX))
                throw new IllegalArgumentException("Received unknown command");
            int animationId = Integer.parseInt(serializedString.split(":")[1]);
            return new WaitAnimationCommand(animationId);
        } catch (Exception e) {
            log.error("Failed to deserialize");
        }
        return null;
    }
}
