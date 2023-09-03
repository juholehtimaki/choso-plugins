package com.PaistiPlugins.GearSwitcherPlugin;

import com.PaistiPlugins.GearSwitcherPlugin.Commands.GearSwitcherCommand;
import com.PaistiPlugins.PaistiUtils.API.Bank;
import com.PaistiPlugins.PaistiUtils.API.Utility;
import com.PaistiPlugins.PaistiUtils.API.Walking;
import com.PaistiPlugins.PaistiUtils.PaistiUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.worlds.WorldType;

import java.util.ArrayList;

@Slf4j

public class GearSwitcherScript {
    ArrayList<GearSwitcherCommand> commands;

    private final boolean allowPvPUsage = Boolean.parseBoolean(System.getenv("ALLOW_PVP_USAGE"));

    GearSwitcherScript(ArrayList<GearSwitcherCommand> commands) {
        this.commands = commands;
    }

    public boolean execute(int sleepDurationBetweenActions) {
        if (Bank.isOpen()) return false;
        if (!allowPvPUsage && (Utility.getWildernessLevelFrom(Walking.getPlayerLocation()) > 0 || Utility.isPlayerInDangerousPvpArea())) {
            Utility.sendGameMessage("Not available in wilderness / PVP worlds", "PGearSwitcher");
            return false;
        }

        PaistiUtils.runOnExecutor(() -> {
            for (var command : commands) {
                if (command.execute()) {
                    Utility.sleepGaussian(Math.min(0, sleepDurationBetweenActions - 40), sleepDurationBetweenActions + 40);
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

    static public GearSwitcherScript deSerializeFromString(String serializedString) {
        ArrayList<GearSwitcherCommand> deSerializedCommands = new ArrayList<>();
        try {
            String[] commands = serializedString.split("\n");
            for (var command : commands) {
                var trimmedCommand = command.trim();
                if (trimmedCommand.length() == 0) continue;
                GearSwitcherCommand parsed = GearSwitcherCommand.deserializeFromString(command);
                deSerializedCommands.add(parsed);
            }
        } catch (Exception e) {
            log.error("Failed to deserialize");
            e.printStackTrace();
        }
        return new GearSwitcherScript(deSerializedCommands);
    }
}
