package com.theplug.VardorvisPlugin.States;

public interface State {
    String name();

    boolean shouldExecuteState();

    void threadedOnGameTick();

    void threadedLoop();
}