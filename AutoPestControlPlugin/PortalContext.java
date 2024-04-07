package com.theplug.AutoPestControlPlugin;

import com.theplug.DontObfuscate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
@Getter
@Setter
@DontObfuscate
public class PortalContext {
    private final Portal portal;
    private boolean isShielded = true;
    private boolean isDead;
}


