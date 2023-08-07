package com.example.PvPHelperPlugin;

import com.example.PaistiUtils.API.Utility;
import com.example.PaistiUtils.PaistiUtils;
import com.example.PvPHelperPlugin.Commands.EquipCommand;
import com.example.PvPHelperPlugin.Commands.PvpHelperCommand;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

@Slf4j

public class PvpHelperScript implements PvpHelperCommand {

    ArrayList<PvpHelperCommand> commands;

    PvpHelperScript(ArrayList<PvpHelperCommand> commands) {
        this.commands = commands;
    }

    @Override
    public boolean execute() {
        PaistiUtils.runOnExecutor(() -> {
            for (var command : commands) {
                if (command.execute()) {
                    Utility.sleepGaussian(10, 30);
                }
            }
            return null;
        });
        return true;
    }

    @Override
    public String serializeToString() {
        StringBuilder serializedString = new StringBuilder();
        for (var command : commands) {
            serializedString.append(command.serializeToString()).append("\n");
        }
        return serializedString.toString();
    }

    static public PvpHelperScript deSerializeFromString(String serializedString) {
        ArrayList<PvpHelperCommand> deSerializedCommands = new ArrayList<>();
        try {
            String[] commands = serializedString.split("\n");
            for (var command : commands) {
                var trimmedCommand = command.trim();
                if (trimmedCommand.length() == 0) continue;
                PvpHelperCommand parsed = PvpHelperCommand.deserializeFromString(command);
                deSerializedCommands.add(parsed);
            }
        } catch (Exception e) {
            log.error("Failed to deserialize");
            e.printStackTrace();
        }
        return new PvpHelperScript(deSerializedCommands);
    }
}
