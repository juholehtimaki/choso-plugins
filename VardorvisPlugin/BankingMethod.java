package com.theplug.VardorvisPlugin;

import com.theplug.DontObfuscate;

@DontObfuscate
public enum BankingMethod {
    HOUSE("House -> CW");

    private String methodName;

    BankingMethod(String methodName) {
        this.methodName = methodName;
    }
}
