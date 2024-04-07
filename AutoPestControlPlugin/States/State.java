package com.theplug.AutoPestControlPlugin.States;

public interface State {
    String name();

    boolean shouldExecuteState();

    void threadedOnGameTick();

    void threadedLoop();
}