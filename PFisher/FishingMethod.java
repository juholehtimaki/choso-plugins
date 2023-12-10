package com.theplug.PFisher;

public enum FishingMethod {
    USE_ROD("Use-rod"),
    LURE("Lure"),
    BAIT("Bait"),
    SMALL_NET("Small Net");

    final String fishingMethod;

    FishingMethod(String fishingMethod) {
        this.fishingMethod = fishingMethod;
    }

    @Override
    public String toString() {
        return this.fishingMethod;
    }
}
