package com.theplug.ColosseumFarmerPlugin.States;

public interface State {
    String name();

    boolean shouldExecuteState();

    void threadedOnGameTick();

    void threadedLoop();
}