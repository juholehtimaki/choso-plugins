package com.theplug.PvmHelper.States;

public interface State {
    String name();

    boolean shouldExecuteState();

    void threadedOnGameTick();

    void threadedLoop();
}