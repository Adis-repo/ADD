package com.dualgpu.vulkan.device;

import java.util.ArrayList;
import java.util.List;

/**
 * Physical Device Information
 * 
 * Contains detailed information about a Vulkan physical device (GPU).
 */
public class PhysicalDeviceInfo {
    
    private final int index;
    private final long physicalDeviceHandle;
    
    private String name;
    private int vendorId;
    private int deviceId;
    private String vulkanVersion;
    private long vramSizeMB;
    
    private final List<QueueFamilyInfo> queueFamilies = new ArrayList<>();
    
    public PhysicalDeviceInfo(int index, long physicalDeviceHandle) {
        this.index = index;
        this.physicalDeviceHandle = physicalDeviceHandle;
    }
    
    // Getters
    
    public int getIndex() {
        return index;
    }
    
    public long getPhysicalDeviceHandle() {
        return physicalDeviceHandle;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public int getVendorId() {
        return vendorId;
    }
    
    public void setVendorId(int vendorId) {
        this.vendorId = vendorId;
    }
    
    public int getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }
    
    public String getVulkanVersion() {
        return vulkanVersion;
    }
    
    public void setVulkanVersion(String vulkanVersion) {
        this.vulkanVersion = vulkanVersion;
    }
    
    public long getVramSizeMB() {
        return vramSizeMB;
    }
    
    public void setVramSizeMB(long vramSizeMB) {
        this.vramSizeMB = vramSizeMB;
    }
    
    public List<QueueFamilyInfo> getQueueFamilies() {
        return queueFamilies;
    }
    
    public void addQueueFamily(QueueFamilyInfo info) {
        this.queueFamilies.add(info);
    }
    
    /**
     * Check if this device has graphics capability
     */
    public boolean hasGraphicsCapability() {
        for (QueueFamilyInfo qf : queueFamilies) {
            if (qf.isGraphics()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if this device has compute capability
     */
    public boolean hasComputeCapability() {
        for (QueueFamilyInfo qf : queueFamilies) {
            if (qf.isCompute()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the first graphics queue family index
     */
    public int getGraphicsQueueFamilyIndex() {
        for (QueueFamilyInfo qf : queueFamilies) {
            if (qf.isGraphics()) {
                return qf.getIndex();
            }
        }
        return -1;
    }
    
    /**
     * Get the first compute queue family index
     */
    public int getComputeQueueFamilyIndex() {
        for (QueueFamilyInfo qf : queueFamilies) {
            if (qf.isCompute()) {
                return qf.getIndex();
            }
        }
        return -1;
    }
    
    @Override
    public String toString() {
        return String.format(
            "PhysicalDeviceInfo[index=%d, name='%s', vendorId=0x%04X, deviceId=0x%04X, vram=%d MB]",
            index, name, vendorId, deviceId, vramSizeMB
        );
    }
}
