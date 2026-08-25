package com.dualgpu.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.List;

import com.dualgpu.config.DualGPUConfig;
import com.dualgpu.diagnostics.DualGPULogger;
import com.dualgpu.vulkan.device.PhysicalDeviceEnumerator;
import com.dualgpu.vulkan.device.PhysicalDeviceInfo;

/**
 * Vulkan Backend
 * 
 * Main Vulkan backend for dual-GPU rendering.
 * Currently implements Milestone 1: device enumeration and basic initialization.
 */
public class VulkanBackend {
    
    private static final DualGPULogger logger = new DualGPULogger();
    
    private final DualGPUConfig config;
    private final PhysicalDeviceEnumerator deviceEnumerator;
    
    private long vkInstance;
    private long physicalDevice0;
    private long physicalDevice1;
    private long logicalDevice;
    
    private boolean deviceGroupAvailable = false;
    private boolean peerAccess0to1 = false;
    private boolean peerAccess1to0 = false;
    
    private List<PhysicalDeviceInfo> devices = new ArrayList<>();
    
    public VulkanBackend(DualGPUConfig config, PhysicalDeviceEnumerator deviceEnumerator) {
        this.config = config;
        this.deviceEnumerator = deviceEnumerator;
    }
    
    /**
     * Initialize the Vulkan backend
     */
    public void initialize() {
        logger.info("Initializing Vulkan backend...");
        
        // Get devices from enumerator
        devices = deviceEnumerator.enumerateDevices();
        vkInstance = deviceEnumerator.getInstance();
        
        if (devices.size() < 2) {
            logger.error("Insufficient devices for dual-GPU operation: {}", devices.size());
            throw new IllegalStateException("At least 2 Vulkan devices required for dual-GPU mode");
        }
        
        // Select GPUs based on configuration
        int gpu0Index = config.getGpu0Index();
        int gpu1Index = config.getGpu1Index();
        
        if (gpu0Index >= devices.size() || gpu1Index >= devices.size()) {
            logger.error("Invalid GPU indices in configuration");
            throw new IllegalArgumentException("GPU index out of range");
        }
        
        physicalDevice0 = devices.get(gpu0Index).getPhysicalDeviceHandle();
        physicalDevice1 = devices.get(gpu1Index).getPhysicalDeviceHandle();
        
        logger.info("Selected GPU 0: {}", devices.get(gpu0Index).getName());
        logger.info("Selected GPU 1: {}", devices.get(gpu1Index).getName());
        
        // Check device group capabilities
        checkDeviceGroupCapabilities();
        
        // For Milestone 1, we stop at device enumeration
        // Future milestones will create logical devices and command queues
        logger.info("Vulkan backend initialized (Milestone 1 - Device Probe)");
    }
    
    /**
     * Check device group and peer memory capabilities
     */
    private void checkDeviceGroupCapabilities() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Get physical device group count
            IntBuffer pPhysicalDeviceGroupCount = stack.mallocInt(1);
            int err = vkEnumeratePhysicalDeviceGroups(vkInstance, pPhysicalDeviceGroupCount, null);
            
            if (err != VK.SUCCESS) {
                logger.warn("vkEnumeratePhysicalDeviceGroups failed: {}", err);
                deviceGroupAvailable = false;
                return;
            }
            
            int groupCount = pPhysicalDeviceGroupCount.get(0);
            logger.info("Found {} device group(s)", groupCount);
            
            if (groupCount == 0) {
                deviceGroupAvailable = false;
                logger.info("No device groups available - GPUs may operate independently");
                return;
            }
            
            // Get device groups
            VkPhysicalDeviceGroupProperties.Buffer groups = 
                VkPhysicalDeviceGroupProperties.calloc(groupCount, stack);
            
            err = vkEnumeratePhysicalDeviceGroups(vkInstance, pPhysicalDeviceGroupCount, groups);
            if (err != VK.SUCCESS) {
                logger.warn("Failed to retrieve device groups: {}", err);
                deviceGroupAvailable = false;
                return;
            }
            
            // Check if our selected GPUs are in the same device group
            for (int i = 0; i < groupCount; i++) {
                VkPhysicalDeviceGroupProperties group = groups.get(i);
                int deviceCount = group.physicalDeviceCount();
                
                boolean foundGpu0 = false;
                boolean foundGpu1 = false;
                
                LongBuffer physicalDevices = group.physicalDevices();
                for (int j = 0; j < deviceCount; j++) {
                    long device = physicalDevices.get(j);
                    if (device == physicalDevice0) foundGpu0 = true;
                    if (device == physicalDevice1) foundGpu1 = true;
                }
                
                if (foundGpu0 && foundGpu1) {
                    deviceGroupAvailable = true;
                    logger.info("Both GPUs are in device group {}", i);
                    
                    // Check PCIe topology
                    if (group.samePCIeMask() != 0) {
                        logger.info("Device group has unified PCIe topology");
                        peerAccess0to1 = true;
                        peerAccess1to0 = true;
                    } else {
                        logger.info("Device group has separate PCIe domains - peer access uncertain");
                        peerAccess0to1 = false;
                        peerAccess1to0 = false;
                    }
                    
                    break;
                }
            }
            
            if (!deviceGroupAvailable) {
                logger.info("GPUs are not in the same device group - will use independent device mode");
            }
            
            logger.logDeviceGroupStatus(deviceGroupAvailable, peerAccess0to1, peerAccess1to0);
            
        } catch (Exception e) {
            logger.error("Error checking device group capabilities", e);
            deviceGroupAvailable = false;
        }
    }
    
    /**
     * Clean up Vulkan resources
     */
    public void cleanup() {
        logger.info("Cleaning up Vulkan backend...");
        
        if (logicalDevice != VK.NULL_HANDLE) {
            vkDestroyDevice(logicalDevice, null);
            logicalDevice = VK.NULL_HANDLE;
        }
        
        if (vkInstance != VK.NULL_HANDLE) {
            vkDestroyInstance(vkInstance, null);
            vkInstance = VK.NULL_HANDLE;
        }
        
        logger.info("Vulkan backend cleanup complete");
    }
    
    // Getters
    
    public boolean isDeviceGroupAvailable() {
        return deviceGroupAvailable;
    }
    
    public boolean hasPeerAccess0to1() {
        return peerAccess0to1;
    }
    
    public boolean hasPeerAccess1to0() {
        return peerAccess1to0;
    }
    
    public List<PhysicalDeviceInfo> getDevices() {
        return devices;
    }
    
    public long getInstance() {
        return vkInstance;
    }
}
