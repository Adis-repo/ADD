package com.dualgpu.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.dualgpu.minecraft.DualGPUModInitializer;

/**
 * DualGPU Configuration System
 * 
 * Manages all configuration options for the dual-GPU renderer.
 * Stored in config/dual_gpu.toml (or .properties for simplicity in early milestones)
 */
public class DualGPUConfig {
    
    private static final String CONFIG_FILE = "config/dual_gpu.properties";
    
    // General settings
    private boolean enabled = true;
    private SchedulingMode mode = SchedulingMode.MANUAL;
    
    // GPU assignment
    private int gpu0Index = 0;
    private int gpu1Index = 1;
    private int presentGpuIndex = 0;
    
    // Feature flags
    private boolean allowShaderSplitting = true;
    private boolean allowComputeSplit = true;
    private boolean allowDynamicScheduling = false; // Disabled until Milestone 9
    
    // Debug settings
    private boolean debugOverlay = false;
    private boolean debugLogging = true;
    private boolean benchmarkMode = false;
    private int maxFramesInFlight = 2;
    
    // Shader pass assignments (pass name -> GPU index or -1 for auto)
    private Map<String, Integer> shaderPassAssignments = new HashMap<>();
    
    public enum SchedulingMode {
        AUTO,           // Automatic scheduling (Milestone 9+)
        SINGLE,         // Force single-GPU mode
        MANUAL,         // Manual GPU assignment via config
        EXPERIMENTAL,   // Experimental features enabled
        BENCHMARK       // Benchmark mode with detailed timing
    }
    
    public DualGPUConfig() {
        // Initialize default shader pass assignments
        shaderPassAssignments.put("shadow", 0);
        shaderPassAssignments.put("gbuffers_terrain", 0);
        shaderPassAssignments.put("gbuffers_entities", 0);
        shaderPassAssignments.put("composite", 1);
        shaderPassAssignments.put("bloom", 1);
        shaderPassAssignments.put("final", 1);
    }
    
    /**
     * Load configuration from file
     */
    public void load() {
        File configFile = new File(CONFIG_FILE);
        
        if (!configFile.exists()) {
            DualGPUModInitializer.LOGGER.info("Configuration file not found, creating with defaults");
            save();
            return;
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            Properties props = new Properties();
            props.load(reader);
            
            // Parse general settings
            enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
            mode = SchedulingMode.valueOf(props.getProperty("mode", "MANUAL").toUpperCase());
            
            // Parse GPU settings
            gpu0Index = Integer.parseInt(props.getProperty("gpu0", "0"));
            gpu1Index = Integer.parseInt(props.getProperty("gpu1", "1"));
            presentGpuIndex = Integer.parseInt(props.getProperty("present_gpu", "0"));
            
            // Parse feature flags
            allowShaderSplitting = Boolean.parseBoolean(props.getProperty("allow_shader_splitting", "true"));
            allowComputeSplit = Boolean.parseBoolean(props.getProperty("allow_compute_split", "true"));
            allowDynamicScheduling = Boolean.parseBoolean(props.getProperty("allow_dynamic_scheduling", "false"));
            
            // Parse debug settings
            debugOverlay = Boolean.parseBoolean(props.getProperty("debug_overlay", "false"));
            debugLogging = Boolean.parseBoolean(props.getProperty("debug_logging", "true"));
            benchmarkMode = Boolean.parseBoolean(props.getProperty("benchmark_mode", "false"));
            maxFramesInFlight = Integer.parseInt(props.getProperty("max_frames_in_flight", "2"));
            
            // Parse shader pass assignments
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("pass.")) {
                    String passName = key.substring(5);
                    String value = props.getProperty(key);
                    if ("auto".equalsIgnoreCase(value)) {
                        shaderPassAssignments.put(passName, -1);
                    } else if ("disabled".equalsIgnoreCase(value)) {
                        shaderPassAssignments.put(passName, -2);
                    } else {
                        try {
                            int gpuIndex = Integer.parseInt(value);
                            shaderPassAssignments.put(passName, gpuIndex);
                        } catch (NumberFormatException e) {
                            DualGPUModInitializer.LOGGER.warn("Invalid shader pass assignment for {}: {}", passName, value);
                        }
                    }
                }
            }
            
            DualGPUModInitializer.LOGGER.debug("Configuration loaded successfully");
            
        } catch (Exception e) {
            DualGPUModInitializer.LOGGER.error("Failed to load configuration", e);
            DualGPUModInitializer.LOGGER.warn("Using default configuration");
        }
    }
    
    /**
     * Save configuration to file
     */
    public void save() {
        try {
            File configFile = new File(CONFIG_FILE);
            configFile.getParentFile().mkdirs();
            
            Properties props = new Properties();
            
            // General settings
            props.setProperty("enabled", String.valueOf(enabled));
            props.setProperty("mode", mode.name().toLowerCase());
            
            // GPU settings
            props.setProperty("gpu0", String.valueOf(gpu0Index));
            props.setProperty("gpu1", String.valueOf(gpu1Index));
            props.setProperty("present_gpu", String.valueOf(presentGpuIndex));
            
            // Feature flags
            props.setProperty("allow_shader_splitting", String.valueOf(allowShaderSplitting));
            props.setProperty("allow_compute_split", String.valueOf(allowComputeSplit));
            props.setProperty("allow_dynamic_scheduling", String.valueOf(allowDynamicScheduling));
            
            // Debug settings
            props.setProperty("debug_overlay", String.valueOf(debugOverlay));
            props.setProperty("debug_logging", String.valueOf(debugLogging));
            props.setProperty("benchmark_mode", String.valueOf(benchmarkMode));
            props.setProperty("max_frames_in_flight", String.valueOf(maxFramesInFlight));
            
            // Shader pass assignments
            for (Map.Entry<String, Integer> entry : shaderPassAssignments.entrySet()) {
                String key = "pass." + entry.getKey();
                String value;
                if (entry.getValue() == -1) {
                    value = "auto";
                } else if (entry.getValue() == -2) {
                    value = "disabled";
                } else {
                    value = String.valueOf(entry.getValue());
                }
                props.setProperty(key, value);
            }
            
            try (FileWriter writer = new FileWriter(configFile)) {
                props.store(writer, "DualGPU Renderer Configuration");
            }
            
            DualGPUModInitializer.LOGGER.info("Configuration saved to {}", CONFIG_FILE);
            
        } catch (Exception e) {
            DualGPUModInitializer.LOGGER.error("Failed to save configuration", e);
        }
    }
    
    // Getters and setters
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public SchedulingMode getMode() { return mode; }
    public void setMode(SchedulingMode mode) { this.mode = mode; }
    
    public int getGpu0Index() { return gpu0Index; }
    public void setGpu0Index(int gpu0Index) { this.gpu0Index = gpu0Index; }
    
    public int getGpu1Index() { return gpu1Index; }
    public void setGpu1Index(int gpu1Index) { this.gpu1Index = gpu1Index; }
    
    public int getPresentGpuIndex() { return presentGpuIndex; }
    public void setPresentGpuIndex(int presentGpuIndex) { this.presentGpuIndex = presentGpuIndex; }
    
    public boolean isAllowShaderSplitting() { return allowShaderSplitting; }
    public boolean isAllowComputeSplit() { return allowComputeSplit; }
    public boolean isAllowDynamicScheduling() { return allowDynamicScheduling; }
    
    public boolean isDebugOverlay() { return debugOverlay; }
    public boolean isDebugLogging() { return debugLogging; }
    public boolean isBenchmarkMode() { return benchmarkMode; }
    public int getMaxFramesInFlight() { return maxFramesInFlight; }
    
    public Integer getShaderPassAssignment(String passName) {
        return shaderPassAssignments.get(passName);
    }
    
    public void setShaderPassAssignment(String passName, int gpuIndex) {
        shaderPassAssignments.put(passName, gpuIndex);
    }
    
    @Override
    public String toString() {
        return String.format(
            "DualGPUConfig[enabled=%s, mode=%s, gpu0=%d, gpu1=%d, debug=%s]",
            enabled, mode, gpu0Index, gpu1Index, debugOverlay
        );
    }
}
