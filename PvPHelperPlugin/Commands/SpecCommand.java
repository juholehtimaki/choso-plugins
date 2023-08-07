package com.example.PvPHelperPlugin.Commands;

import com.example.PaistiUtils.API.Interaction;
import com.example.PaistiUtils.API.Utility;

public class SpecCommand implements PvpHelperCommand {

    public static final String INSTRUCTION_PREFIX = "SPEC";

    SpecCommand(){}

    @Override
    public boolean execute() {
        Utility.forceSpec();
        return true;
    }

    @Override
    public String serializeToString() {
        return INSTRUCTION_PREFIX;
    }

    static public SpecCommand deserializeFromString(String serializedString) {
        if (serializedString.equalsIgnoreCase(INSTRUCTION_PREFIX)) return new SpecCommand();
        throw new IllegalArgumentException("Received unknown command");
    }
}
