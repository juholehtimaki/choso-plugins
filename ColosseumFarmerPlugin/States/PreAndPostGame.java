package com.theplug.ColosseumFarmerPlugin.States;

import com.theplug.ColosseumFarmerPlugin.ColosseumFarmerPlugin;
import com.theplug.ColosseumFarmerPlugin.ColosseumFarmerPluginConfig;
import com.theplug.PaistiUtils.API.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.TileObject;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class PreAndPostGame implements State {
    static ColosseumFarmerPlugin plugin;
    ColosseumFarmerPluginConfig config;

    private final AtomicReference<Boolean> alreadyLootedChestThisRun = new AtomicReference<>(false);

    public PreAndPostGame(ColosseumFarmerPlugin plugin, ColosseumFarmerPluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public String name() {
        return "PreAndPostGame";
    }

    @Override
    public boolean shouldExecuteState() {
        return plugin.insideColosseum() && plugin.isMinimusPresent();
    }

    @Override
    public void threadedOnGameTick() {

    }

    enum WidgetType {
        PRE_GAME,
        POST_GAME,
        UNKNOWN
    }

    private final String ALREADY_LOOTED_MESSAGE = "chest appears to be empty";

    private boolean isModifierWidgetVisible() {
        return Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            var widget = Widgets.search().withId(56688641).first();
            return widget.isPresent() && Widgets.isValidAndVisible(widget.get());
        }));
    }

    private boolean handleOpenModifierWidget() {
        var isModifierWidgetOpen = isModifierWidgetVisible();
        if (!isModifierWidgetOpen) {
            var minimus = NPCs.search().withId(plugin.MINIMUS_NPC_ID).withAction("Start-wave").first();
            if (minimus.isEmpty()) return false;
            var lootChest = TileObjects.search().withId(plugin.LOOT_CHEST_OBJECT_ID).first();
            if (lootChest.isPresent()) return false;
            Interaction.clickNpc(minimus.get(), "Start-wave");
            return Utility.sleepUntilCondition(this::isModifierWidgetVisible, 3000, 100);
        } else {
            return true;
        }
    }

    private WidgetType getModifierWidgetType() {
        return Utility.runOnClientThread(() -> {
            var setModifierWidget = Widgets.search().withId(56688642).withTextContains("Fortis Colosseum").first();
            if (setModifierWidget.isPresent() && Widgets.isValidAndVisible(setModifierWidget.get())) {
                return WidgetType.PRE_GAME;
            }
            var rewardWidget = Widgets.search().withId(56688642).withTextContains("Completed").first();
            if (rewardWidget.isPresent() && Widgets.isValidAndVisible(rewardWidget.get())) {
                return WidgetType.POST_GAME;
            }
            return WidgetType.UNKNOWN;
        });
    }

    private boolean handleSetModifierWidget() {
        var modifierSet = Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            var setModifierWidget = Widgets.search().nameContains("Frailty").first();
            if (setModifierWidget.isPresent()) {
                Interaction.clickWidget(setModifierWidget.get(), "Set");
                return true;
            }
            return false;
        }));
        Utility.sleepGaussian(600, 1200);
        // ID 56688681
        var continuePressed = Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            var continueWidget = Widgets.search().withAction("Continue").first();
            if (continueWidget.isPresent()) {
                Interaction.clickWidget(continueWidget.get());
                return true;
            }
            return false;
        }));
        return modifierSet && continuePressed;
    }

    private boolean isHandleConfirmWidgetVisible() {
        return Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            var confirmWidget = Widgets.search().withAction("Confirm").first();
            return confirmWidget.isPresent() && Widgets.isValidAndVisible(confirmWidget.get());
        }));
    }

    private boolean handleConfirmWidget(){
        return Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            var confirmWidget = Widgets.search().withAction("Confirm").first();
            if (confirmWidget.isPresent()) {
                Interaction.clickWidget(confirmWidget.get(), "Confirm");
                return true;
            }
            return false;
        }));
    }

    private boolean handleClaimLootWidget() {
        var lootClaimed = Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            var setModifierWidget = Widgets.search().withAction("Claim").first();
            if (setModifierWidget.isPresent()) {
                Interaction.clickWidget(setModifierWidget.get(), "Claim");
                return true;
            }
            return false;
        }));
        if (!lootClaimed) {
            return false;
        }
        Utility.sleepUntilCondition(this::isHandleConfirmWidgetVisible, 3000, 100);
        handleConfirmWidget();
        if (Utility.sleepUntilCondition(() -> !isHandleConfirmWidgetVisible(), 3000, 100)) {
            return true;
        }
        return false;
    }

    private boolean handleWaveOptions() {
        if (!Utility.isIdle()) return false;
        if (!handleOpenModifierWidget()) return false;

        var widgetType = getModifierWidgetType();

        if (widgetType == WidgetType.POST_GAME) {
            return handleClaimLootWidget();
        }
        else if (widgetType == WidgetType.PRE_GAME) {
            return handleSetModifierWidget();
        } else {
            return false;
        }
    }

    private TileObject getLootChest() {
        var chest = TileObjects.search().withId(plugin.LOOT_CHEST_OBJECT_ID).first();
        return chest.orElse(null);
    }

    private boolean isRewardWidgetVisible() {
        return Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            var confirmWidget = Widgets.search().withId(56623106).first();
            return confirmWidget.isPresent() && Widgets.isValidAndVisible(confirmWidget.get());
        }));
    }

    private boolean chestContainsSplinters() {
        return Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            var splintersWidget = Widgets.search().withAction("Take").first();
            if (splintersWidget.isPresent() && Widgets.isValidAndVisible(splintersWidget.get())) return true;
            return false;
        }));
    }

    private boolean handleLootingSplinters() {
        if (chestContainsSplinters()) {
             Utility.runOnClientThread(() -> {
                var takeAllOptionWidget = Widgets.search().withAction("Bank-all").first();
                if (takeAllOptionWidget.isPresent() && Widgets.isValidAndVisible(takeAllOptionWidget.get())) {
                    Interaction.clickWidget(takeAllOptionWidget.get(), "Bank-all");
                }
                return null;
            });
             return Utility.sleepUntilCondition(() -> !chestContainsSplinters(), 3000, 100);
        }
        return true;
    }

    private boolean handleLootChest() {
        if (!isRewardWidgetVisible()) {
            var lootChest = getLootChest();
            Interaction.clickTileObject(lootChest, "Search");
            if (!Utility.sleepUntilCondition(this::isRewardWidgetVisible, 5000, 100)) {
                return false;
            }
        }
        return handleLootingSplinters();
    }

    private boolean handleExit() {
        var minimus = NPCs.search().withId(plugin.MINIMUS_NPC_ID).withAction("Leave").first();
        if (minimus.isPresent()) {
            Interaction.clickNpc(minimus.get(), "Leave");
            Utility.sleepUntilCondition(Dialog::isConversationWindowUp, 5000, 100);
            Dialog.handleGenericDialog(new String[]{"Yes."});
            return Utility.sleepUntilCondition(() -> getLootChest() == null, 5000, 100);
        }
        return false;
    }

    private boolean handleWaveCompletion() {
        var lootChest = getLootChest();
        if (lootChest == null) return false;
        if (alreadyLootedChestThisRun.get() || handleLootChest()) return handleExit();
        return false;
    }

    @Override
    public void threadedLoop() {
        if (handleWaveOptions()) {
            Utility.sleepGaussian(1200, 1800);
            return;
        }
        if (handleWaveCompletion()) {
            alreadyLootedChestThisRun.set(false);
            plugin.setTotalRuns(plugin.getTotalRuns() + 1);
            Utility.sleepGaussian(1200, 1800);
            return;
        }
        Utility.sleepGaussian(100, 200);
    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;
        if (event.getMessage().toLowerCase().contains(ALREADY_LOOTED_MESSAGE.toLowerCase())) {
            alreadyLootedChestThisRun.set(true);
        }
    }
}
