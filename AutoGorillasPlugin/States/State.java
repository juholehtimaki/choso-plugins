package com.theplug.AutoGorillasPlugin.States;

public interface State {
    String name();

    boolean shouldExecuteState();

    void threadedOnGameTick();

    void threadedLoop();
}