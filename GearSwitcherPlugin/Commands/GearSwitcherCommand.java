package com.theplug.GearSwitcherPlugin.Commands;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.AttackTickTracker.AttackTickTracker;

import java.security.InvalidParameterException;

@DontObfuscate
public interface GearSwitcherCommand {
    boolean execute();

    String serializeToString();

    static GearSwitcherCommand deserializeFromString(String serialized, AttackTickTracker attackTickTracker) {
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
            case AutocastCommand.INSTRUCTION_PREFIX:
                return AutocastCommand.deserializeFromString(serialized);
            case WaitAnimationCommand.INSTRUCTION_PREFIX:
                return WaitAnimationCommand.deserializeFromString(serialized);
            case UnEquipCommand.INSTRUCTION_PREFIX:
                return UnEquipCommand.deserializeFromString(serialized);
            case DisablePrayCommand.INSTRUCTION_PREFIX:
                return DisablePrayCommand.deserializeFromString(serialized);
            case WaitAttackTickCommand.INSTRUCTION_PREFIX:
                return WaitAttackTickCommand.deserializeFromString(serialized, attackTickTracker);
            default:
                throw new InvalidParameterException("Invalid prefix: " + prefix + " in serialized string: " + serialized);
        }
    }
}
