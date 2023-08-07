package com.example.PvPHelperPlugin.Commands;

import java.security.InvalidParameterException;

public interface PvpHelperCommand {
    boolean execute();
    String serializeToString();

    static PvpHelperCommand deserializeFromString(String serialized) {
        var prefix = serialized.contains(":") ? serialized.split(":")[0].trim() : serialized;
        switch (prefix) {
            case EquipCommand.INSTRUCTION_PREFIX:
                return EquipCommand.deserializeFromString(serialized);
            case PrayCommand.INSTRUCTION_PREFIX:
                return PrayCommand.deserializeFromString(serialized);
            case SpecCommand.INSTRUCTION_PREFIX:
                return SpecCommand.deserializeFromString(serialized);
            case AttackCommand.INSTRUCTION_PREFIX:
                return AttackCommand.deserializeFromString(serialized);
            case SleepCommand.INSTRUCTION_PREFIX:
                return SleepCommand.deserializeFromString(serialized);
            case SpellCommand.INSTRUCTION_PREFIX:
                return SpellCommand.deserializeFromString(serialized);
            default:
                throw new InvalidParameterException("Invalid prefix: " + prefix + " in serialized string: " + serialized);
        }
    }
}
