package com.theplug.AutoBankSkillerPlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.Collections.Pair;
import lombok.Getter;
import net.runelite.api.ItemID;

import java.util.ArrayList;
import java.util.List;

@DontObfuscate
public enum BankSkillerPotion {
    GUAM_POTION("Guam potion", ItemID.GUAM_POTION_UNF) {{
        addMaterial(ItemID.GUAM_LEAF, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    MARREN_POTION_UNF("Marrentill potion", ItemID.MARRENTILL_POTION_UNF) {{
        addMaterial(ItemID.MARRENTILL, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    TARROMIN_POTION_UNF("Tarromin potion", ItemID.TARROMIN_POTION_UNF) {{
        addMaterial(ItemID.TARROMIN, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    HARRALANDER_POTION_UNF("Harralander potion", ItemID.HARRALANDER_POTION_UNF) {{
        addMaterial(ItemID.HARRALANDER, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    RANARR_POTION_UNF("Ranarr potion", ItemID.RANARR_POTION_UNF) {{
        addMaterial(ItemID.RANARR_WEED, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    TOADFLAX_POTION_UNF("Toadflax potion", ItemID.TOADFLAX_POTION_UNF) {{
        addMaterial(ItemID.TOADFLAX, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    IRIT_POTION_UNF("Irit potion", ItemID.IRIT_POTION_UNF) {{
        addMaterial(ItemID.IRIT_LEAF, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    AVANTOE_POTION_UNF("Avantoe potion", ItemID.AVANTOE_POTION_UNF) {{
        addMaterial(ItemID.AVANTOE, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    KWUARM_POTION_UNF("Kwuarm potion", ItemID.KWUARM_POTION_UNF) {{
        addMaterial(ItemID.KWUARM, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    SNAPDRAGON_POTION_UNF("Snapdragon potion", ItemID.SNAPDRAGON_POTION_UNF) {{
        addMaterial(ItemID.SNAPDRAGON, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    CADANTINE_POTION_UNF("Cadantine potion", ItemID.CADANTINE_POTION_UNF) {{
        addMaterial(ItemID.CADANTINE, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    LANTADYME_POTION_UNF("Lantadyme potion", ItemID.LANTADYME_POTION_UNF) {{
        addMaterial(ItemID.LANTADYME, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    DWARF_WEED_POTION_UNF("Dwarf weed potion", ItemID.DWARF_WEED_POTION_UNF) {{
        addMaterial(ItemID.DWARF_WEED, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    TORSTOL_POTION_UNF("Torstol potion", ItemID.TORSTOL_POTION_UNF) {{
        addMaterial(ItemID.TORSTOL, 1);
        addMaterial(ItemID.VIAL_OF_WATER, 1);
    }},
    ATTACK_POTION("Attack potion", 119) {{
        addMaterial(ItemID.GUAM_POTION_UNF, 1);
        addMaterial(ItemID.EYE_OF_NEWT, 1);
    }},
    ANTIPOISON("Antipoison", 121) {{
        addMaterial(ItemID.MARRENTILL_POTION_UNF, 1);
        addMaterial(ItemID.UNICORN_HORN_DUST, 1);
    }},
    STRENGTH_POTION("Strength potion", 123) {{
        addMaterial(ItemID.TARROMIN_POTION_UNF, 1);
        addMaterial(ItemID.LIMPWURT_ROOT, 1);
    }},
    SERUM_207("Serum 207", 125) {{
        addMaterial(ItemID.TARROMIN_POTION_UNF, 1);
        addMaterial(ItemID.ASHES, 1);
    }},
    COMPOST_POTION("Compost potion", 127) {{
        addMaterial(ItemID.HARRALANDER_POTION_UNF, 1);
        addMaterial(ItemID.VOLCANIC_ASH, 1);
    }},
    RESTORE_POTION("Restore potion", 129) {{
        addMaterial(ItemID.HARRALANDER_POTION_UNF, 1);
        addMaterial(ItemID.REDBERRIES, 1);
    }},
    ENERGY_POTION("Energy potion", 131) {{
        addMaterial(ItemID.HARRALANDER_POTION_UNF, 1);
        addMaterial(ItemID.CHOCOLATE_DUST, 1);
    }},
    AGILITY_POTION("Agility potion", 133) {{
        addMaterial(ItemID.TOADFLAX_POTION_UNF, 1);
        addMaterial(ItemID.TOADS_LEGS, 1);
    }},
    COMBAT_POTION("Combat potion", 135) {{
        addMaterial(ItemID.HARRALANDER_POTION_UNF, 1);
        addMaterial(ItemID.GOAT_HORN_DUST, 1);
    }},
    PRAYER_POTION("Prayer potion", 137) {{
        addMaterial(ItemID.RANARR_POTION_UNF, 1);
        addMaterial(ItemID.SNAPE_GRASS, 1);
    }},
    SUPER_ATTACK("Super attack", 139) {{
        addMaterial(ItemID.IRIT_POTION_UNF, 1);
        addMaterial(ItemID.EYE_OF_NEWT, 1);
    }},
    SUPER_ANTIPOISON("Super antipoison", 141) {{
        addMaterial(ItemID.IRIT_POTION_UNF, 1);
        addMaterial(ItemID.UNICORN_HORN_DUST, 1);
    }},
    FISHING_POTION("Fishing potion", 143) {{
        addMaterial(ItemID.AVANTOE_POTION_UNF, 1);
        addMaterial(ItemID.SNAPE_GRASS, 1);
    }},
    SUPER_ENERGY("Super energy", 145) {{
        addMaterial(ItemID.AVANTOE_POTION_UNF, 1);
        addMaterial(ItemID.MORT_MYRE_FUNGUS, 1);
    }},
    HUNT_POTION("Hunter potion", 147) {{
        addMaterial(ItemID.AVANTOE_POTION_UNF, 1);
        addMaterial(ItemID.KEBBIT_TEETH_DUST, 1);
    }},
    SUPER_STRENGTH("Super strength", 149) {{
        addMaterial(ItemID.KWUARM_POTION_UNF, 1);
        addMaterial(ItemID.LIMPWURT_ROOT, 1);
    }},
    SUPER_RESTORE("Super restore", 151) {{
        addMaterial(ItemID.SNAPDRAGON_POTION_UNF, 1);
        addMaterial(ItemID.RED_SPIDERS_EGGS, 1);
    }},
    SUPER_DEFENCE("Super defence", 153) {{
        addMaterial(ItemID.CADANTINE_POTION_UNF, 1);
        addMaterial(ItemID.WHITE_BERRIES, 1);
    }},
    ANTIFIRE("Antifire", 155) {{
        addMaterial(ItemID.LANTADYME_POTION_UNF, 1);
        addMaterial(ItemID.DRAGON_SCALE_DUST, 1);
    }},
    RANGING_POTION("Ranging potion", 157) {{
        addMaterial(ItemID.DWARF_WEED_POTION_UNF, 1);
        addMaterial(ItemID.WINE_OF_ZAMORAK, 1);
    }},
    MAGIC_POTION("Magic potion", 159) {{
        addMaterial(ItemID.LANTADYME_POTION_UNF, 1);
        addMaterial(ItemID.POTATO_CACTUS, 1);
    }},
    STAMINA_POTION("Stamina potion", 161) {{
        addMaterial(ItemID.SUPER_ENERGY4, 1);
        addMaterial(ItemID.AMYLASE_CRYSTAL, 4);
        oneTickable();
    }},
    BASTION_POTION("Bastion potion", 163) {{
        addMaterial(ItemID.CRYSTAL_DUST, 1);
        addMaterial(ItemID.TORSTOL, 1);
    }},
    BATTLE_MAGE_POTION("Battle mage potion", 165) {{
        addMaterial(ItemID.CRYSTAL_DUST, 1);
        addMaterial(ItemID.WINE_OF_ZAMORAK, 1);
    }},
    SARADOMIN_BREW("Saradomin brew", 167) {{
        addMaterial(ItemID.TOADFLAX_POTION_UNF, 1);
        addMaterial(ItemID.CRUSHED_NEST, 1);
    }},
    EXTENDED_ANTIFIRE("Extended antifire", 169) {{
        addMaterial(ItemID.ANTIFIRE_POTION4, 1);
        addMaterial(ItemID.LAVA_SCALE_SHARD, 4);
        oneTickable();
    }},
    ANCIENT_BREW("Ancient brew", 171) {{
        addMaterial(ItemID.DWARF_WEED_POTION_UNF, 1);
        addMaterial(ItemID.NIHIL_DUST, 1);
    }},
    ANTI_VENOM("Anti-venom", 173) {{
        addMaterial(ItemID.ANTIDOTE4_5952, 1);
        addMaterial(ItemID.ZULRAHS_SCALES, 20);
        oneTickable();
    }},
    MENAPHITE_REMEDY("Menaphite remedy", 175) {{
        addMaterial(ItemID.DWARF_WEED_POTION_UNF, 1);
        addMaterial(ItemID.LILY_OF_THE_SANDS, 1);
    }},
    SUPER_COMBAT_POTION("Super combat potion", 177) {{
        addMaterial(ItemID.TORSTOL, 1);
        addMaterial(ItemID.SUPER_ATTACK4, 1);
        addMaterial(ItemID.SUPER_STRENGTH4, 1);
        addMaterial(ItemID.SUPER_DEFENCE4, 1);
    }},
    FORGOTTEN_BREW("Forgotten brew", 177) {{
        addMaterial(ItemID.ANCIENT_BREW4, 1);
        addMaterial(ItemID.ANCIENT_ESSENCE, 80);
    }},
    SUPER_ANTIFIRE("Super antifire", 179) {{
        addMaterial(ItemID.ANTIFIRE_POTION4, 1);
        addMaterial(ItemID.CRUSHED_SUPERIOR_DRAGON_BONES, 1);
    }},
    ANTI_VENOM_PLUS("Anti-venom+", 181) {{
        addMaterial(ItemID.ANTIVENOM4, 1);
        addMaterial(ItemID.TORSTOL, 1);
    }},
    EXTENDED_SUPER_ANTIFIRE("Extended super antifire", 183) {{
        addMaterial(ItemID.SUPER_ANTIFIRE_POTION4, 1);
        addMaterial(ItemID.LAVA_SCALE_SHARD, 4);
        oneTickable();
    }};

    @Getter
    final int id;
    @Getter
    private final String name;
    @Getter
    private boolean isOneTickable = false;

    @Getter
    private final List<Pair<Integer, Integer>> requiredMaterials = new ArrayList<>();

    BankSkillerPotion(String name, int id) {
        this.name = name;
        this.id = id;
    }

    public void oneTickable() {
        this.isOneTickable = true;
    }

    public void addMaterial(int id, int amount) {
        requiredMaterials.add(new Pair<>(id, amount));
    }

    @Override
    public String toString() {
        return this.name;
    }
}
