package com.theplug.GearSwitcherPlugin;

import com.theplug.GearSwitcherPlugin.Commands.GearSwitcherCommand;
import com.theplug.PaistiUtils.API.AttackTickTracker.AttackTickTracker;
import com.theplug.PaistiUtils.API.Utility;
import com.theplug.PaistiUtils.API.Walking;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j

public class GearSwitcherScript {
    ArrayList<GearSwitcherCommand> commands;

    private final boolean allowPvPUsage;

    private final AtomicReference<Instant> lastExecuted = new AtomicReference<>(Instant.now());

    GearSwitcherScript(ArrayList<GearSwitcherCommand> commands) {
        this.commands = commands;
        allowPvPUsage = Boolean.parseBoolean(System.getenv("ALLOW_PVP_USAGE"));
    }

    public boolean execute(int sleepDurationBetweenActions) {
        if (Duration.between(lastExecuted.get(), Instant.now()).toMillis() < 300) return false;
        lastExecuted.set(Instant.now());

        log.debug("Running executor");
        PaistiUtils.runOnExecutor(() -> {
            log.debug("Check allowPvPUsage & wilderness level");
            if (!allowPvPUsage && (Utility.getWildernessLevelFrom(Walking.getPlayerLocation()) > 0 || Utility.isPlayerInDangerousPvpArea())) {
                Utility.sendGameMessage("Not available in wilderness / PVP worlds", "PGearSwitcher");
                return false;
            }

            log.debug("Executor started");
            for (var command : commands) {
                log.debug("Executing command: " + command.getClass().getSimpleName());
                if (command.execute()) {
                    Utility.sleepGaussian(Math.max(0, sleepDurationBetweenActions - 40), sleepDurationBetweenActions + 40);
                }
            }
            return null;
        });
        return true;
    }

    public String serializeToString() {
        StringBuilder serializedString = new StringBuilder();
        for (var command : commands) {
            serializedString.append(command.serializeToString()).append("\n");
        }
        return serializedString.toString();
    }

    static public GearSwitcherScript deSerializeFromString(String serializedString, AttackTickTracker attackTickTracker) {
        ArrayList<GearSwitcherCommand> deSerializedCommands = new ArrayList<>();
        try {
            String[] commands = serializedString.split("\n");
            for (var command : commands) {
                var trimmedCommand = command.trim();
                if (trimmedCommand.isEmpty()) continue;
                GearSwitcherCommand parsed = GearSwitcherCommand.deserializeFromString(command, attackTickTracker);
                deSerializedCommands.add(parsed);
            }
        } catch (Exception e) {
            log.error("Failed to deserialize");
            e.printStackTrace();
        }
        return new GearSwitcherScript(deSerializedCommands);
    }
}
