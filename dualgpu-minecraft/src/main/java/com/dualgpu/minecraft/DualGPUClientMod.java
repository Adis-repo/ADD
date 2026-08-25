package com.dualgpu.minecraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.dualgpu.config.DualGPUConfig;
import com.dualgpu.diagnostics.DualGPULogger;
import com.dualgpu.vulkan.VulkanBackend;
import com.dualgpu.vulkan.device.PhysicalDeviceEnumerator;

/**
 * DualGPU Client Mod - Main Client-Side Controller
 * 
 * Orchestrates the dual-GPU rendering system including:
 * - Vulkan initialization and device enumeration
 * - OpenGL interception setup
 * - Render graph management
 * - GPU scheduling
 * - Diagnostics and debugging
 */
@Environment(EnvType.CLIENT)
public class DualGPUClientMod implements ClientModInitializer {
    
    private static DualGPUClientMod instance;
    
    private final DualGPUConfig config;
    private final DualGPULogger logger;
    private VulkanBackend vulkanBackend;
    private PhysicalDeviceEnumerator deviceEnumerator;
    
    private boolean initialized = false;
    private boolean dualGpuAvailable = false;
    private boolean fallbackMode = false;
    
    public DualGPUClientMod() {
        this.config = new DualGPUConfig();
        this.logger = new DualGPULogger();
        instance = this;
    }
    
    public static DualGPUClientMod getInstance() {
        return instance;
    }
    
    /**
     * Initialize the DualGPU renderer
     * 
     * This is called during Fabric client initialization.
     * Steps:
     * 1. Load configuration
     * 2. Enumerate Vulkan physical devices
     * 3. Check for dual-GPU capability
     * 4. Initialize Vulkan backend if available
     * 5. Set up OpenGL interception
     */
    public void initialize() {
        try {
            logger.info("DualGPU Renderer initializing...");
            
            // Step 1: Load configuration
            config.load();
            logger.debug("Configuration loaded: {}", config);
            
            if (!config.isEnabled()) {
                logger.info("DualGPU rendering disabled in configuration");
                fallbackMode = true;
                return;
            }
            
            // Step 2: Enumerate Vulkan devices (Milestone 1)
            deviceEnumerator = new PhysicalDeviceEnumerator();
            var devices = deviceEnumerator.enumerateDevices();
            
            logger.info("Enumerated {} Vulkan-capable physical devices", devices.size());
            for (var device : devices) {
                logger.info("  Device {}: {}", device.getIndex(), device.getName());
                logger.info("    Vendor ID: 0x{:04X}, Device ID: 0x{:04X}", 
                    device.getVendorId(), device.getDeviceId());
                logger.info("    VRAM: {} MB", device.getVramSizeMB());
                logger.info("    Vulkan API: {}", device.getVulkanVersion());
            }
            
            // Step 3: Check for dual-GPU capability
            if (devices.size() >= 2) {
                checkDualGpuCapability(devices);
            } else {
                logger.warn("Only {} Vulkan device(s) found. Dual-GPU mode unavailable.", 
                    devices.size());
                fallbackMode = true;
            }
            
            // Step 4: Initialize Vulkan backend if dual-GPU available
            if (dualGpuAvailable && !fallbackMode) {
                initializeVulkanBackend();
            }
            
            initialized = true;
            logger.info("DualGPU Renderer initialization complete");
            logger.info("Status: {}", getStatusString());
            
        } catch (Exception e) {
            logger.error("Failed to initialize DualGPU Renderer", e);
            fallbackMode = true;
        }
    }
    
    /**
     * Check if the enumerated devices support dual-GPU operation
     */
    private void checkDualGpuCapability(java.util.List<com.dualgpu.vulkan.device.PhysicalDeviceInfo> devices) {
        // For Milestone 1: Simply check if we have at least 2 devices
        // Future milestones will check device-group capabilities
        
        var gpu0 = devices.get(config.getGpu0Index());
        var gpu1 = devices.get(config.getGpu1Index());
        
        logger.info("Checking dual-GPU capability...");
        logger.info("  GPU 0: {}", gpu0.getName());
        logger.info("  GPU 1: {}", gpu1.getName());
        
        // Check if both devices are from the same vendor (AMD in our case)
        if (gpu0.getVendorId() != gpu1.getVendorId()) {
            logger.warn("GPUs have different vendor IDs. Mixed-vendor multi-GPU may have limitations.");
        }
        
        // TODO: Check device-group capabilities via vkEnumeratePhysicalDeviceGroups
        // TODO: Check peer memory access capabilities
        
        dualGpuAvailable = true;
        logger.info("Dual-GPU mode: AVAILABLE (preliminary check)");
    }
    
    /**
     * Initialize the Vulkan backend for dual-GPU rendering
     */
    private void initializeVulkanBackend() {
        logger.info("Initializing Vulkan backend...");
        
        try {
            vulkanBackend = new VulkanBackend(config, deviceEnumerator);
            vulkanBackend.initialize();
            
            logger.info("Vulkan backend initialized successfully");
            logger.info("  Device Group: {}", vulkanBackend.isDeviceGroupAvailable() ? "YES" : "NO");
            logger.info("  Peer Access GPU0->GPU1: {}", vulkanBackend.hasPeerAccess0to1() ? "YES" : "NO");
            logger.info("  Peer Access GPU1->GPU0: {}", vulkanBackend.hasPeerAccess1to0() ? "YES" : "NO");
            
        } catch (Exception e) {
            logger.error("Failed to initialize Vulkan backend", e);
            logger.warn("Falling back to single-GPU mode");
            fallbackMode = true;
        }
    }
    
    /**
     * Get the current status of the DualGPU renderer
     */
    public String getStatusString() {
        if (fallbackMode) {
            return "FALLBACK (Single-GPU)";
        } else if (!initialized) {
            return "NOT INITIALIZED";
        } else if (dualGpuAvailable) {
            return "ENABLED (Dual-GPU)";
        } else {
            return "DISABLED (Insufficient devices)";
        }
    }
    
    // Getters
    
    public DualGPUConfig getConfig() {
        return config;
    }
    
    public VulkanBackend getVulkanBackend() {
        return vulkanBackend;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public boolean isDualGpuAvailable() {
        return dualGpuAvailable && !fallbackMode;
    }
    
    public boolean isFallbackMode() {
        return fallbackMode;
    }
}
