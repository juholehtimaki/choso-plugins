package com.theplug.AutoPestControlPlugin;

import com.theplug.DontObfuscate;
import com.theplug.PaistiUtils.API.Utility;
import com.theplug.PaistiUtils.API.Walking;
import com.theplug.PaistiUtils.API.Widgets;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.theplug.AutoPestControlPlugin.Portal.*;

@Slf4j
@DontObfuscate
public class Game {
    // Game starts with all possible rotations
    private Rotation[] possibleRotations = Rotation.values();
    // Number of shields dropped
    private int shieldsDropped;

    @Getter
    PortalContext purple = new PortalContext(PURPLE);
    @Getter
    PortalContext blue = new PortalContext(BLUE);
    @Getter
    PortalContext yellow = new PortalContext(YELLOW);
    @Getter
    PortalContext red = new PortalContext(RED);
    @Getter
    PortalContext desiredPortal;

    public Game() {
        PortalContext[] portals = {purple, blue, yellow, red};
        desiredPortal = portals[Utility.random(0, 3)];
    }

    public void fall(String color) {
        switch (color.toLowerCase()) {
            case "purple":
                fall(purple);
                break;
            case "red":
                fall(red);
                break;
            case "yellow":
                fall(yellow);
                break;
            case "blue":
                fall(blue);
                break;
        }
    }

    public void fall(PortalContext portal) {
        if (!portal.isShielded()) {
            return;
        }

        log.debug("Shield dropped for {}", portal.getPortal());

        portal.setShielded(false);
        int shieldDrop = shieldsDropped++;

        // Remove impossible rotations
        List<Rotation> rotations = new ArrayList<>();

        for (Rotation rotation : possibleRotations) {
            if (rotation.getPortal(shieldDrop) == portal.getPortal()) {
                rotations.add(rotation);
            }
        }

        possibleRotations = rotations.toArray(new Rotation[0]);
    }

    public void die(PortalContext portal) {
        if (portal.isDead()) {
            return;
        }

        log.debug("Portal {} died", portal.getPortal());

        portal.setDead(true);
    }

    public List<Portal> getNextPortals() {
        List<Portal> portals = new ArrayList<>();

        for (Rotation rotation : possibleRotations) {
            Portal portal = rotation.getPortal(shieldsDropped);

            if (portal != null && !portals.contains(portal)) {
                portals.add(portal);
            }
        }

        return portals;
    }

    public List<PortalContext> getAlivePortals() {
        return Stream.of(blue, purple, yellow, red).filter(p -> isPortalAlive(p.getPortal())).collect(Collectors.toList());
    }

    private boolean isZero(Widget widget) {
        return widget.getText().trim().equals("0");
    }

    public void updateGame() {
        //updatePortalStatuses();
        updateDesiredPortal();
    }

    private boolean isPortalAlive(Portal portal) {
        return portal != null && !isZero(Widgets.getWidget(portal.getHitpoints()));
    }

    private void updatePortalStatuses() {
        PortalContext purple = getPurple();
        PortalContext blue = getBlue();
        PortalContext yellow = getYellow();
        PortalContext red = getRed();

        Widget purpleHealth = Widgets.getWidget(PURPLE.getHitpoints());
        Widget blueHealth = Widgets.getWidget(BLUE.getHitpoints());
        Widget yellowHealth = Widgets.getWidget(YELLOW.getHitpoints());
        Widget redHealth = Widgets.getWidget(RED.getHitpoints());

        if (isZero(purpleHealth)) {
            die(purple);
        }
        if (isZero(blueHealth)) {
            die(blue);
        }
        if (isZero(yellowHealth)) {
            die(yellow);
        }
        if (isZero(redHealth)) {
            die(red);
        }
    }

    private void updateDesiredPortal() {
        Widget desiredPortalWidget = Widgets.getWidget(desiredPortal.getPortal().getHitpoints());
        if (isZero(desiredPortalWidget)) {
            var alivePortals = getAlivePortals();
            if (alivePortals.isEmpty()) return;
            desiredPortal = alivePortals.get(Utility.random(0, alivePortals.size() - 1));
        }
    }
}

