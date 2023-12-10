package com.theplug.PFisher;

public enum FishingPool {
    ROD_FISHING_SPOT("Rod Fishing spot"),
    FISHING_SPOT("Fishing spot");

    final String fishingPool;

    FishingPool(String fishingMethod) {
        this.fishingPool = fishingMethod;
    }

    @Override
    public String toString() {
        return this.fishingPool;
    }
}
