package com.theplug.AutoPvpPlugin.Commands;

import com.theplug.PaistiUtils.API.Spells.Spell;
import com.theplug.PaistiUtils.API.Spells.Standard;
import com.theplug.PaistiUtils.API.Utility;
import com.theplug.PaistiUtils.API.Widgets;
import com.theplug.PaistiUtils.Hooks.Hooks;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;

import java.util.Arrays;

@Slf4j
public class AutocastCommand implements GearSwitcherCommand {

    Spell spell;

    public static final String INSTRUCTION_PREFIX = "AUTOCAST";

    AutocastCommand(String spellName) {
        this.spell = Arrays.stream(Standard.values()).filter(f -> f.name().replace("_", " ").equalsIgnoreCase(spellName) || f.name().equalsIgnoreCase(spellName)).findFirst().orElse(null);
        if (this.spell == null) throw new IllegalArgumentException("Invalid spell name:" + spellName);
    }

    private void toggleFireSurge() {
        MenuAction menuAction = MenuAction.CC_OP;
        int p0 = 51;
        int p1 = 13172737;
        int itemId = -1;
        int objectId = 1;
        String opt = "Fire Surge";

        var canvasHeight = PaistiUtils.getClient().getCanvasHeight();
        var canvasWidth = PaistiUtils.getClient().getCanvasWidth();
        Point clickPoint = new Point((int) (canvasWidth * 0.763) + Utility.random(0, 146), (int) (canvasHeight * 0.842) + Utility.random(0, 12));
        //PaistiUtils.consumeNextClick();
        //Mouse.clickPoint(clickPoint);
        Hooks.invokeMenuAction(p0, p1, menuAction.getId(), objectId, itemId, opt, "", clickPoint.getX(), clickPoint.getY());
    }

    private void toggleSpellbook() {
        MenuAction menuAction = MenuAction.CC_OP;
        int p0 = -1;
        int p1 = 38862874;
        int itemId = -1;
        int objectId = 1;
        String opt = "Choose spell";

        var canvasHeight = PaistiUtils.getClient().getCanvasHeight();
        var canvasWidth = PaistiUtils.getClient().getCanvasWidth();
        Point clickPoint = new Point((int) (canvasWidth * 0.763) + Utility.random(0, 146), (int) (canvasHeight * 0.842) + Utility.random(0, 12));
        //PaistiUtils.consumeNextClick();
        //Mouse.clickPoint(clickPoint);
        Hooks.invokeMenuAction(p0, p1, menuAction.getId(), objectId, itemId, opt, "", clickPoint.getX(), clickPoint.getY());
    }


    @Override
    public boolean execute() {
        Utility.runOnClientThread(() -> {
            toggleSpellbook();
            return true;
        });
        Utility.sleepUntilCondition(() -> Widgets.getWidget(201, 1) != null, 1200, 50);
        Utility.runOnClientThread(() -> {
            toggleFireSurge();
            return true;
        });
        return true;
    }

    @Override
    public String serializeToString() {
        return INSTRUCTION_PREFIX + ":" + spell.getName();
    }

    static public AutocastCommand deserializeFromString(String serializedString) {
        try {
            String command = serializedString.split(":")[0];
            if (!command.equalsIgnoreCase(INSTRUCTION_PREFIX))
                throw new IllegalArgumentException("Received unknown command");
            String spellName = serializedString.split(":")[1];
            return new AutocastCommand(spellName);
        } catch (Exception e) {
            log.error("Failed to deserialize");
            e.printStackTrace();
        }
        return null;
    }
}
