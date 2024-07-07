package com.theplug.AutoBankSkillerPlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.Collections.Pair;
import lombok.Getter;
import net.runelite.api.ItemID;

import java.util.ArrayList;
import java.util.List;

@DontObfuscate
public enum BankSkillerCrafting {
    WATER_BATTLESTAFF("Water battlestaff") {{
        addMaterial(ItemID.BATTLESTAFF, 1);
        addMaterial(ItemID.WATER_ORB, 1);
    }},
    EARTH_BATTLESTAFF("Earth battlestaff") {{
        addMaterial(ItemID.BATTLESTAFF, 1);
        addMaterial(ItemID.EARTH_ORB, 1);
    }},
    FIRE_BATTLESTAFF("Fire battlestaff") {{
        addMaterial(ItemID.BATTLESTAFF, 1);
        addMaterial(ItemID.FIRE_ORB, 1);
    }},
    AIR_BATTLESTAFF("Air battlestaff") {{
        addMaterial(ItemID.BATTLESTAFF, 1);
        addMaterial(ItemID.AIR_ORB, 1);
    }},
    GREEN_DRAGONHIDE_BODY("Green dragonhide body") {{
        addMaterial(ItemID.GREEN_DRAGON_LEATHER, 3);
        addMaterial(ItemID.THREAD, 1);
        requiresNeedleAndThread();
        addMatcher("*body");
    }},
    BLUE_DRAGONHIDE_BODY("Blue dragonhide body") {{
        addMaterial(ItemID.BLUE_DRAGON_LEATHER, 3);
        addMaterial(ItemID.THREAD, 1);
        requiresNeedleAndThread();
        addMatcher("*body");
    }},
    RED_DRAGONHIDE_BODY("Red dragonhide body") {{
        addMaterial(ItemID.RED_DRAGON_LEATHER, 3);
        addMaterial(ItemID.THREAD, 1);
        requiresNeedleAndThread();
        addMatcher("*body");
    }},
    BLACK_DRAGONHIDE_BODY("Black dragonhide body") {{
        addMaterial(ItemID.BLACK_DRAGON_LEATHER, 3);
        addMaterial(ItemID.THREAD, 1);
        requiresNeedleAndThread();
        addMatcher("*body");
    }};

    @Getter
    private final String name;
    @Getter
    private String matcher = null;
    @Getter
    private final List<Pair<Integer, Integer>> requiredMaterials = new ArrayList<>();

    @Getter
    private boolean requiresNeedleAndThread = false;

    BankSkillerCrafting(String name) {
        this.name = name;
    }

    public void addMaterial(int id, int amount) {
        requiredMaterials.add(new Pair<>(id, amount));
    }

    public void requiresNeedleAndThread() {
        requiresNeedleAndThread = true;
    }

    public void addMatcher(String matcher) {
        this.matcher = matcher;
    }

    @Override
    public String toString() {
        return this.name;
    }
}

