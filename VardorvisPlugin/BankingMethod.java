package com.theplug.VardorvisPlugin;

import com.theplug.DontObfuscate;

@DontObfuscate
public enum BankingMethod {
    HOUSE("House"),
    FEROX("Ferox");

    private String methodName;

    @Override
    public String toString() {
        return this.methodName;
    }

    BankingMethod(String methodName) {
        this.methodName = methodName;
    }
}
