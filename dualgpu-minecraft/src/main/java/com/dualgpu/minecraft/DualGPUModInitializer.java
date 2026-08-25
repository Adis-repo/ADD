package com.dualgpu.minecraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DualGPU Renderer Mod Initializer
 * 
 * Main entry point for the DualGPU rendering backend.
 * This mod enables two discrete AMD Radeon RX 6600 GPUs to contribute
 * to rendering the same Minecraft frame.
 * 
 * Current Status: Milestone 1 - Vulkan Device Probe
 */
@Environment(EnvType.CLIENT)
public class DualGPUModInitializer implements ClientModInitializer {
    
    public static final String MOD_ID = "dualgpu";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static DualGPUClientMod instance;
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing DualGPU Renderer v{} (Milestone {})", 
            getVersion(), getMilestone());
        
        instance = new DualGPUClientMod();
        instance.initialize();
    }
    
    public static DualGPUClientMod getInstance() {
        return instance;
    }
    
    private String getVersion() {
        return "0.1.0-m1";
    }
    
    private int getMilestone() {
        return 1;
    }
}
