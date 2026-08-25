package com.dualgpu.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DualGPU Diagnostic Logger
 * 
 * Provides structured logging with multiple verbosity levels.
 * All dual-GPU operations are logged for debugging and performance analysis.
 */
public class DualGPULogger {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("dualgpu");
    
    public enum LogLevel {
        ERROR,
        WARN,
        INFO,
        DEBUG,
        TRACE
    }
    
    private LogLevel currentLevel = LogLevel.INFO;
    private boolean enabled = true;
    
    public DualGPULogger() {
        // Check system properties for log level override
        String logLevelProp = System.getProperty("dualgpu.logging");
        if (logLevelProp != null) {
            try {
                currentLevel = LogLevel.valueOf(logLevelProp.toUpperCase());
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid log level: {}, defaulting to INFO", logLevelProp);
            }
        }
        
        String debugProp = System.getProperty("dualgpu.debug");
        if ("true".equalsIgnoreCase(debugProp)) {
            currentLevel = LogLevel.DEBUG;
        }
    }
    
    public void setLevel(LogLevel level) {
        this.currentLevel = level;
    }
    
    public void enable() {
        this.enabled = true;
    }
    
    public void disable() {
        this.enabled = false;
    }
    
    public void error(String message) {
        if (enabled && currentLevel.ordinal() <= LogLevel.ERROR.ordinal()) {
            LOGGER.error("[DualGPU] {}", message);
        }
    }
    
    public void error(String message, Throwable t) {
        if (enabled && currentLevel.ordinal() <= LogLevel.ERROR.ordinal()) {
            LOGGER.error("[DualGPU] {}", message, t);
        }
    }
    
    public void warn(String message) {
        if (enabled && currentLevel.ordinal() <= LogLevel.WARN.ordinal()) {
            LOGGER.warn("[DualGPU] {}", message);
        }
    }
    
    public void info(String message) {
        if (enabled && currentLevel.ordinal() <= LogLevel.INFO.ordinal()) {
            LOGGER.info("[DualGPU] {}", message);
        }
    }
    
    public void debug(String message) {
        if (enabled && currentLevel.ordinal() <= LogLevel.DEBUG.ordinal()) {
            LOGGER.debug("[DualGPU] {}", message);
        }
    }
    
    public void trace(String message) {
        if (enabled && currentLevel.ordinal() <= LogLevel.TRACE.ordinal()) {
            LOGGER.debug("[DualGPU-TRACE] {}", message);
        }
    }
    
    /**
     * Log GPU device information
     */
    public void logDeviceInfo(int gpuIndex, String name, int vendorId, int deviceId, 
                              long vramMB, String vulkanVersion) {
        info(String.format(
            "Physical device %d: %s (Vendor: 0x%04X, Device: 0x%04X, VRAM: %d MB, Vulkan: %s)",
            gpuIndex, name, vendorId, deviceId, vramMB, vulkanVersion
        ));
    }
    
    /**
     * Log frame timing information
     */
    public void logFrameTiming(long frameNumber, double gpu0TimeMs, double gpu1TimeMs, 
                               double syncTimeMs, double transferTimeMs) {
        debug(String.format(
            "Frame %d: GPU0=%.2fms, GPU1=%.2fms, Sync=%.2fms, Transfer=%.2fms",
            frameNumber, gpu0TimeMs, gpu1TimeMs, syncTimeMs, transferTimeMs
        ));
    }
    
    /**
     * Log resource transfer information
     */
    public void logResourceTransfer(String resourceName, long sizeBytes, 
                                    int sourceGpu, int destGpu, double timeMs) {
        debug(String.format(
            "Transfer: %s (%.2f MB) GPU%d -> GPU%d in %.2fms",
            resourceName, sizeBytes / (1024.0 * 1024.0), sourceGpu, destGpu, timeMs
        ));
    }
    
    /**
     * Log render task assignment
     */
    public void logTaskAssignment(String taskName, int gpuIndex, long frameNumber) {
        trace(String.format(
            "Task '%s' assigned to GPU%d (frame %d)",
            taskName, gpuIndex, frameNumber
        ));
    }
    
    /**
     * Log device group status
     */
    public void logDeviceGroupStatus(boolean available, boolean peerAccess0to1, 
                                     boolean peerAccess1to0) {
        info(String.format(
            "Device Group: %s, Peer Access GPU0->GPU1: %s, GPU1->GPU0: %s",
            available ? "YES" : "NO",
            peerAccess0to1 ? "YES" : "NO",
            peerAccess1to0 ? "YES" : "NO"
        ));
    }
}
