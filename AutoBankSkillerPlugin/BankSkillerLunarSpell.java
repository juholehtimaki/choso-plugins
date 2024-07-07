package com.theplug.AutoBankSkillerPlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.Spells.Lunar;
import com.theplug.PaistiUtils.Collections.Pair;
import lombok.Getter;
import net.runelite.api.ItemID;

import java.util.ArrayList;
import java.util.List;

@DontObfuscate
public enum BankSkillerLunarSpell {
    REGULAR_PLANK("Make plank -> Regular", Lunar.PLANK_MAKE) {{
        addMaterial(ItemID.LOGS, 1);
        addMaterial(ItemID.COINS_995, 70);
    }},
    OAK_PLANK("Make plank -> Oak", Lunar.PLANK_MAKE) {{
        addMaterial(ItemID.OAK_LOGS, 1);
        addMaterial(ItemID.COINS_995, 175);
    }},
    TEAK_PLANK("Make plank -> Teak", Lunar.PLANK_MAKE) {{
        addMaterial(ItemID.TEAK_LOGS, 1);
        addMaterial(ItemID.COINS_995, 350);
    }},
    MAHOGANY_PLANK("Make plank -> Mahogany", Lunar.PLANK_MAKE) {{
        addMaterial(ItemID.MAHOGANY_LOGS, 1);
        addMaterial(ItemID.COINS_995, 1050);
    }},
    GREEN_DRAGONHIDE("Tan -> Green dragonhide", Lunar.TAN_LEATHER) {{
        addMaterial(ItemID.GREEN_DRAGONHIDE, 1);
    }},
    BLUE_DRAGONHIDE("Tan -> Blue dragonhide", Lunar.TAN_LEATHER) {{
        addMaterial(ItemID.BLUE_DRAGONHIDE, 1);
    }},
    RED_DRAGONHIDE("Tan -> Red dragonhide", Lunar.TAN_LEATHER) {{
        addMaterial(ItemID.RED_DRAGONHIDE, 1);
    }},
    BLACK_DRAGONHIDE("Tan -> Black dragonhide", Lunar.TAN_LEATHER) {{
        addMaterial(ItemID.BLACK_DRAGONHIDE, 1);
    }},
    SPIN_FLAX("Spin flax", Lunar.SPIN_FLAX) {{
        addMaterial(ItemID.FLAX, 1);
    }},
    MOLTEN_GLASS1("Glass from soda ash", Lunar.SUPERGLASS_MAKE) {{
        addMaterial(ItemID.SODA_ASH, 1);
        addMaterial(ItemID.BUCKET_OF_SAND, 1);
    }},
    MOLTEN_GLASS2("Glass from seaweed", Lunar.SUPERGLASS_MAKE) {{
        addMaterial(ItemID.SEAWEED, 1);
        addMaterial(ItemID.BUCKET_OF_SAND, 1);
    }},
    HUMIDIFY_CLAY("Humidify clay", Lunar.HUMIDIFY) {{
        addMaterial(ItemID.CLAY, 1);
    }};

    @Getter
    private final String name;
    @Getter
    private Lunar spell;

    @Getter
    private final List<Pair<Integer, Integer>> requiredMaterials = new ArrayList<>();

    BankSkillerLunarSpell(String name, Lunar spell) {
        this.name = name;
        this.spell = spell;
    }

    public void addMaterial(int id, int amount) {
        requiredMaterials.add(new Pair<>(id, amount));
    }

    @Override
    public String toString() {
        return this.name;
    }
}
