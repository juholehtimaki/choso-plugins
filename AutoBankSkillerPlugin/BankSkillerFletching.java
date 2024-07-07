package com.theplug.AutoBankSkillerPlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.Collections.Pair;
import lombok.Getter;
import net.runelite.api.ItemID;

import java.util.ArrayList;
import java.util.List;

@DontObfuscate
public enum BankSkillerFletching {
    ARROW_SHAFTS("Arrow shafts") {{
        addMaterial(ItemID.LOGS, 1);
        requiresKnife();
        addMatcher("*arrow shafts");
    }},
    REGULAR_SHORTBOW("Regular shortbow (u)") {{
        addMaterial(ItemID.LOGS, 1);
        requiresKnife();
        addMatcher("*hortbow");
    }},
    REGULAR_SHORTBOW_STRING("Regular shortbow") {{
        addMaterial(ItemID.BOW_STRING, 1);
        addMaterial(ItemID.SHORTBOW_U, 1);
    }},
    REGULAR_LONGBOW("Regular longbow (u)") {{
        addMaterial(ItemID.LOGS, 1);
        requiresKnife();
        addMatcher("*ongbow");
    }},
    REGULAR_LONGBOW_STRING("Regular longbow") {{
        addMaterial(ItemID.BOW_STRING, 1);
        addMaterial(ItemID.LONGBOW_U, 1);
    }},
    OAK_SHORTBOW("Oak shortbow (u)") {{
        addMaterial(ItemID.OAK_LOGS, 1);
        requiresKnife();
        addMatcher("*hortbow");
    }},
    OAK_SHORTBOW_STRING("Oak shortbow") {{
        addMaterial(ItemID.BOW_STRING, 1);
        addMaterial(ItemID.OAK_SHORTBOW_U, 1);
    }},
    OAK_LONGBOW("Oak longbow (u)") {{
        addMaterial(ItemID.OAK_LOGS, 1);
        requiresKnife();
        addMatcher("*ongbow");
    }},
    OAK_LONGBOW_STRING("Oak longbow") {{
        addMaterial(ItemID.BOW_STRING, 1);
        addMaterial(ItemID.OAK_SHORTBOW_U, 1);
    }},
    WILLOW_SHORTBOW("Willow shortbow (u)") {{
        addMaterial(ItemID.WILLOW_LOGS, 1);
        requiresKnife();
        addMatcher("*hortbow");
    }},
    WILLOW_SHORTBOW_STRING("Willow shortbow") {{
        addMaterial(ItemID.BOW_STRING, 1);
        addMaterial(ItemID.WILLOW_SHORTBOW_U, 1);
    }},
    WILLO_LONGBOW("Willow longbow (u)") {{
        addMaterial(ItemID.WILLOW_LOGS, 1);
        requiresKnife();
        addMatcher("*ongbow");
    }},
    WILLOW_LONGBOW_STRING("Willow longbow") {{
        addMaterial(ItemID.BOW_STRING, 1);
        addMaterial(ItemID.WILLOW_LONGBOW_U, 1);
    }},
    MAPLE_SHORTBOW("Maple shortbow (u)") {{
        addMaterial(ItemID.MAPLE_LOGS, 1);
        requiresKnife();
        addMatcher("*hortbow");
    }},
    MAPLE_SHORTBOW_STRING("Maple shortbow") {{
        addMaterial(ItemID.BOW_STRING, 1);
        addMaterial(ItemID.MAPLE_SHORTBOW_U, 1);
    }},
    MAPLE_LONGBOW("Maple longbow (u)") {{
        addMaterial(ItemID.MAPLE_LOGS, 1);
        requiresKnife();
        addMatcher("*ongbow");
    }},
    MAPLE_LONGBOW_STRING("Maple longbow") {{
        addMaterial(ItemID.BOW_STRING, 1);
        addMaterial(ItemID.MAPLE_LONGBOW_U, 1);
    }},
    YEW_SHORTBOW("Yew shortbow (u)") {{
        addMaterial(ItemID.YEW_LOGS, 1);
        requiresKnife();
        addMatcher("*hortbow");
    }},
    YEW_SHORTBOW_STRING("Yew shortbow") {{
        addMaterial(ItemID.BOW_STRING, 1);
        addMaterial(ItemID.YEW_SHORTBOW_U, 1);
    }},
    YEW_LONGBOW("Yew longbow (u)") {{
        addMaterial(ItemID.YEW_LOGS, 1);
        requiresKnife();
        addMatcher("*ongbow");
    }},
    YEW_LONGBOW_STRING("Yew longbow") {{
        addMaterial(ItemID.BOW_STRING, 1);
        addMaterial(ItemID.YEW_LONGBOW_U, 1);
    }},
    MAGIC_SHORTBOW("Magic shortbow (u)") {{
        addMaterial(ItemID.MAGIC_LOGS, 1);
        requiresKnife();
        addMatcher("*hortbow");
    }},
    MAGIC_SHORTBOW_STRING("Magic shortbow") {{
        addMaterial(ItemID.BOW_STRING, 1);
        addMaterial(ItemID.MAGIC_SHORTBOW_U, 1);
    }},
    MAGIC_LONGBOW("Magic longbow (u)") {{
        addMaterial(ItemID.MAGIC_LOGS, 1);
        requiresKnife();
        addMatcher("*ongbow");
    }},
    MAGIC_LONGBOW_STRING("Magic longbow") {{
        addMaterial(ItemID.BOW_STRING, 1);
        addMaterial(ItemID.MAGIC_LONGBOW_U, 1);
    }};

    @Getter
    private final String name;
    @Getter
    private String matcher = null;
    @Getter
    private boolean requiresKnife = false;

    @Getter
    private final List<Pair<Integer, Integer>> requiredMaterials = new ArrayList<>();

    BankSkillerFletching(String name) {
        this.name = name;
    }

    public void addMaterial(int id, int amount) {
        requiredMaterials.add(new Pair<>(id, amount));
    }

    public void requiresKnife() {
        requiresKnife = true;
    }

    public void addMatcher(String matcher) {
        this.matcher = matcher;
    }

    @Override
    public String toString() {
        return this.name;
    }
}

