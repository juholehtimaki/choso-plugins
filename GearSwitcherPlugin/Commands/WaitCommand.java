package com.PaistiPlugins.GearSwitcherPlugin.Commands;

import com.PaistiPlugins.PaistiUtils.API.Utility;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WaitCommand implements GearSwitcherCommand {

    int animationId;
    // Gmaul = 1667
    // AGS = 7644
    // Voidwaker = 1378

    public static final String INSTRUCTION_PREFIX = "WAIT";

    WaitCommand(int animationId) {
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

    static public WaitCommand deserializeFromString(String serializedString) {
        try {
            String command = serializedString.split(":")[0];
            if (!command.equalsIgnoreCase(INSTRUCTION_PREFIX))
                throw new IllegalArgumentException("Received unknown command");
            int animationId = Integer.parseInt(serializedString.split(":")[1]);
            return new WaitCommand(animationId);
        } catch (Exception e) {
            log.error("Failed to deserialize");
        }
        return null;
    }
}
