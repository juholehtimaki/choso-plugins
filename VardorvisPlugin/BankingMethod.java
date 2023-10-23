package com.theplug.VardorvisPlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.API.Spells.Standard;
import com.theplug.PaistiUtils.PathFinding.Teleports.ItemTeleport;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;

import java.util.function.BooleanSupplier;
import java.util.function.Function;

@DontObfuscate
public enum BankingMethod {
    HOUSE("House -> CW");

    private String methodName;

    BankingMethod(String methodName) {
        this.methodName = methodName;
    }
}
