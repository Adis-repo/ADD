package com.dualgpu.vulkan.device;

import org.lwjgl.vulkan.VKQueueFamilyProperties;

/**
 * Queue Family Information
 * 
 * Contains information about a Vulkan queue family.
 */
public class QueueFamilyInfo {
    
    private final int index;
    private final int flags;
    private final int queueCount;
    private final int timestampValidBits;
    
    public static final int VK_QUEUE_GRAPHICS_BIT = 0x00000001;
    public static final int VK_QUEUE_COMPUTE_BIT = 0x00000002;
    public static final int VK_QUEUE_TRANSFER_BIT = 0x00000004;
    
    public QueueFamilyInfo(int index, int flags, int queueCount, int timestampValidBits) {
        this.index = index;
        this.flags = flags;
        this.queueCount = queueCount;
        this.timestampValidBits = timestampValidBits;
    }
    
    public int getIndex() {
        return index;
    }
    
    public int getFlags() {
        return flags;
    }
    
    public int getQueueCount() {
        return queueCount;
    }
    
    public int getTimestampValidBits() {
        return timestampValidBits;
    }
    
    /**
     * Check if this queue family supports graphics operations
     */
    public boolean isGraphics() {
        return (flags & VK_QUEUE_GRAPHICS_BIT) != 0;
    }
    
    /**
     * Check if this queue family supports compute operations
     */
    public boolean isCompute() {
        return (flags & VK_QUEUE_COMPUTE_BIT) != 0;
    }
    
    /**
     * Check if this queue family supports transfer operations
     */
    public boolean isTransfer() {
        return (flags & VK_QUEUE_TRANSFER_BIT) != 0;
    }
    
    /**
     * Check if this queue family supports sparse binding
     */
    public boolean isSparseBinding() {
        return (flags & 0x00000010) != 0;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("QueueFamily[");
        sb.append("index=").append(index);
        sb.append(", queues=").append(queueCount);
        
        if (isGraphics()) sb.append(", graphics");
        if (isCompute()) sb.append(", compute");
        if (isTransfer()) sb.append(", transfer");
        if (isSparseBinding()) sb.append(", sparse");
        
        sb.append("]");
        return sb.toString();
    }
}
