package com.theplug.AutoPvpPlugin.Commands;

import com.theplug.PaistiUtils.API.Utility;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SleepCommand implements GearSwitcherCommand {

    public static final String INSTRUCTION_PREFIX = "SLEEP";

    public int sleepTime;

    SleepCommand(int sleepTime) {
        this.sleepTime = sleepTime;
    }

    @Override
    public boolean execute() {
        Utility.sleepGaussian(sleepTime, sleepTime + 50);
        return false;
    }

    @Override
    public String serializeToString() {
        return INSTRUCTION_PREFIX + ":" + sleepTime;
    }

    static public SleepCommand deserializeFromString(String serializedString) {
        try {
            String command = serializedString.split(":")[0];
            if (!command.equalsIgnoreCase(INSTRUCTION_PREFIX))
                throw new IllegalArgumentException("Received unknown command");
            int sleepTime = Integer.parseInt(serializedString.split(":")[1].trim());
            return new SleepCommand(sleepTime);
        } catch (Exception e) {
            log.error("Failed to deserialize");
        }
        return null;
    }
}
