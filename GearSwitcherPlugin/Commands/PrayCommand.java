package com.theplug.GearSwitcherPlugin.Commands;

import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public class PrayCommand implements GearSwitcherCommand {

    PPrayer prayer;

    public static final String INSTRUCTION_PREFIX = "PRAY";

    PrayCommand(String prayerName) {
        this.prayer = Arrays.stream(PPrayer.values()).filter(f -> f.name().equalsIgnoreCase(prayerName)).findFirst().orElse(null);
        if (this.prayer == null) throw new IllegalArgumentException("Invalid prayer name:" + prayerName);
    }

    @Override
    public boolean execute() {
        if (prayer.isActive()) return false;
        return prayer.setEnabled(true);
    }

    @Override
    public String serializeToString() {
        return INSTRUCTION_PREFIX + ":" + prayer.toString();
    }

    static public PrayCommand deserializeFromString(String serializedString) {
        try {
            String command = serializedString.split(":")[0];
            if (!command.equalsIgnoreCase(INSTRUCTION_PREFIX))
                throw new IllegalArgumentException("Received unknown command");
            String prayerName = serializedString.split(":")[1];
            return new PrayCommand(prayerName);
        } catch (Exception e) {
            log.error("Failed to deserialize");
        }
        return null;
    }
}
