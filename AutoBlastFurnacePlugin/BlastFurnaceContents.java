package com.theplug.AutoBlastFurnacePlugin;

import com.theplug.PaistiUtils.API.Utility;
import com.theplug.PaistiUtils.Collections.IntPair;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.ItemID;
import net.runelite.api.Varbits;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
enum BlastFurnaceContents {
    COPPER_ORE(Varbits.BLAST_FURNACE_COPPER_ORE, ItemID.COPPER_ORE),
    TIN_ORE(Varbits.BLAST_FURNACE_TIN_ORE, ItemID.TIN_ORE),
    IRON_ORE(Varbits.BLAST_FURNACE_IRON_ORE, ItemID.IRON_ORE),
    COAL(Varbits.BLAST_FURNACE_COAL, ItemID.COAL),
    MITHRIL_ORE(Varbits.BLAST_FURNACE_MITHRIL_ORE, ItemID.MITHRIL_ORE),
    ADAMANTITE_ORE(Varbits.BLAST_FURNACE_ADAMANTITE_ORE, ItemID.ADAMANTITE_ORE),
    RUNITE_ORE(Varbits.BLAST_FURNACE_RUNITE_ORE, ItemID.RUNITE_ORE),
    SILVER_ORE(Varbits.BLAST_FURNACE_SILVER_ORE, ItemID.SILVER_ORE),
    GOLD_ORE(Varbits.BLAST_FURNACE_GOLD_ORE, ItemID.GOLD_ORE),
    BRONZE_BAR(Varbits.BLAST_FURNACE_BRONZE_BAR, ItemID.BRONZE_BAR),
    IRON_BAR(Varbits.BLAST_FURNACE_IRON_BAR, ItemID.IRON_BAR),
    STEEL_BAR(Varbits.BLAST_FURNACE_STEEL_BAR, ItemID.STEEL_BAR),
    MITHRIL_BAR(Varbits.BLAST_FURNACE_MITHRIL_BAR, ItemID.MITHRIL_BAR),
    ADAMANTITE_BAR(Varbits.BLAST_FURNACE_ADAMANTITE_BAR, ItemID.ADAMANTITE_BAR),
    RUNITE_BAR(Varbits.BLAST_FURNACE_RUNITE_BAR, ItemID.RUNITE_BAR),
    SILVER_BAR(Varbits.BLAST_FURNACE_SILVER_BAR, ItemID.SILVER_BAR),
    GOLD_BAR(Varbits.BLAST_FURNACE_GOLD_BAR, ItemID.GOLD_BAR);

    private final int varbit;
    private final int itemID;

    public int getQuantity() {
        return Utility.getVarbitValue(this.varbit);
    }

    public static List<IntPair> getAllContents() {
        return Arrays.stream(values())
                .map(content -> new IntPair(content.getItemID(), content.getQuantity()))
                .collect(Collectors.toList());
    }

    public static List<IntPair> getAllBars() {
        return Arrays.stream(values())
                .filter(content -> content.name().endsWith("_BAR"))
                .map(content -> new IntPair(content.getItemID(), content.getQuantity()))
                .collect(Collectors.toList());
    }

    public static boolean areBarsAvailable() {
        return getAllBars().stream().anyMatch(pair -> pair.getSecond() > 0);
    }
}