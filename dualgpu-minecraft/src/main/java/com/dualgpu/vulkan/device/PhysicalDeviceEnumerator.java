package com.dualgpu.vulkan.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.List;

import com.dualgpu.diagnostics.DualGPULogger;

/**
 * Physical Device Enumerator
 * 
 * Enumerates all Vulkan-capable physical devices (GPUs) in the system.
 * This is Milestone 1: detecting both RX 6600 GPUs.
 */
public class PhysicalDeviceEnumerator {
    
    private static final DualGPULogger logger = new DualGPULogger();
    
    private long vkInstance;
    private List<PhysicalDeviceInfo> devices = new ArrayList<>();
    
    /**
     * Create a Vulkan instance and enumerate physical devices
     */
    public List<PhysicalDeviceInfo> enumerateDevices() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Step 1: Create Vulkan instance
            vkInstance = createVulkanInstance(stack);
            
            if (vkInstance == VK.NULL_HANDLE) {
                logger.error("Failed to create Vulkan instance");
                return devices;
            }
            
            logger.info("Vulkan instance created successfully");
            
            // Step 2: Enumerate physical devices
            IntBuffer pPhysicalDeviceCount = stack.mallocInt(1);
            
            // First call to get count
            int err = vkEnumeratePhysicalDevices(vkInstance, pPhysicalDeviceCount, null);
            if (err != VK.SUCCESS) {
                logger.error("Failed to enumerate physical devices: {}", err);
                return devices;
            }
            
            int deviceCount = pPhysicalDeviceCount.get(0);
            logger.info("Found {} physical device(s)", deviceCount);
            
            if (deviceCount == 0) {
                return devices;
            }
            
            // Second call to get actual devices
            long[] physicalDevices = new long[deviceCount];
            LongBuffer pPhysicalDevices = stack.longs(physicalDevices);
            
            err = vkEnumeratePhysicalDevices(vkInstance, pPhysicalDeviceCount, pPhysicalDevices);
            if (err != VK.SUCCESS) {
                logger.error("Failed to retrieve physical devices: {}", err);
                return devices;
            }
            
            // Step 3: Gather information about each device
            for (int i = 0; i < deviceCount; i++) {
                long physicalDevice = pPhysicalDevices.get(i);
                PhysicalDeviceInfo info = queryDeviceInfo(physicalDevice, i, stack);
                devices.add(info);
                
                logger.logDeviceInfo(
                    info.getIndex(),
                    info.getName(),
                    info.getVendorId(),
                    info.getDeviceId(),
                    info.getVramSizeMB(),
                    info.getVulkanVersion()
                );
            }
            
            // Step 4: Check for device groups (Milestone 1 basic check)
            checkDeviceGroups(stack);
            
        } catch (Exception e) {
            logger.error("Exception during device enumeration", e);
        }
        
        return devices;
    }
    
    /**
     * Create a Vulkan instance with appropriate extensions
     */
    private long createVulkanInstance(MemoryStack stack) {
        VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack);
        appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
               .pApplicationName(stack.UTF8("DualGPU Renderer"))
               .applicationVersion(VK_MAKE_VERSION(0, 1, 0))
               .pEngineName(stack.UTF8("DualGPU Engine"))
               .engineVersion(VK_MAKE_VERSION(0, 1, 0))
               .apiVersion(VK_API_VERSION_1_3); // Request Vulkan 1.3
        
        // Required extensions for device groups
        String[] requiredExtensions = new String[] {
            VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME,
            VK_KHR_DEVICE_GROUP_CREATION_EXTENSION_NAME
        };
        
        // Get available instance extensions
        IntBuffer pExtensionCount = stack.mallocInt(1);
        int err = vkEnumerateInstanceExtensionProperties((String)null, pExtensionCount, null);
        if (err != VK.SUCCESS) {
            logger.warn("Failed to enumerate instance extensions");
            return VK.NULL_HANDLE;
        }
        
        int extensionCount = pExtensionCount.get(0);
        VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.calloc(extensionCount, stack);
        vkEnumerateInstanceExtensionProperties((String)null, pExtensionCount, availableExtensions);
        
        // Check which extensions are available
        List<String> enabledExtensions = new ArrayList<>();
        for (String ext : requiredExtensions) {
            boolean found = false;
            for (int i = 0; i < extensionCount; i++) {
                if (availableExtensions.get(i).extensionNameString().equals(ext)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                enabledExtensions.add(ext);
            } else {
                logger.warn("Required extension not available: {}", ext);
            }
        }
        
        PointerBuffer ppEnabledExtensionNames = stack.mallocPointer(enabledExtensions.size());
        for (String ext : enabledExtensions) {
            ppEnabledExtensionNames.put(stack.UTF8(ext));
        }
        ppEnabledExtensionNames.flip();
        
        // Create instance create info
        VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack);
        createInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                  .pApplicationInfo(appInfo)
                  .ppEnabledExtensionNames(ppEnabledExtensionNames);
        
        // Enable device group creation feature
        VkDeviceGroupInstanceCreateInfoKHR deviceGroupCreate = VkDeviceGroupInstanceCreateInfoKHR.calloc(stack);
        deviceGroupCreate.sType(VK_STRUCTURE_TYPE_DEVICE_GROUP_INSTANCE_CREATE_INFO_KHR);
        createInfo.pNext(deviceGroupCreate);
        
        LongBuffer pInstance = stack.mallocLong(1);
        err = vkCreateInstance(createInfo, null, pInstance);
        
        if (err != VK.SUCCESS) {
            logger.error("Failed to create Vulkan instance: {}", err);
            return VK.NULL_HANDLE;
        }
        
        return pInstance.get(0);
    }
    
    /**
     * Query detailed information about a physical device
     */
    private PhysicalDeviceInfo queryDeviceInfo(long physicalDevice, int index, MemoryStack stack) {
        PhysicalDeviceInfo info = new PhysicalDeviceInfo(index, physicalDevice);
        
        // Get device properties
        VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
        vkGetPhysicalDeviceProperties(physicalDevice, props);
        
        info.setName(props.deviceNameString());
        info.setVendorId(props.vendorID());
        info.setDeviceId(props.deviceID());
        info.setVulkanVersion(formatVulkanVersion(props.apiVersion()));
        
        // Get memory properties to determine VRAM
        VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.calloc(stack);
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProps);
        
        long vramSize = 0;
        for (int i = 0; i < memProps.memoryHeapCount(); i++) {
            VkMemoryHeap heap = memProps.memoryHeaps(i);
            if ((heap.flags() & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0) {
                vramSize += heap.size();
            }
        }
        info.setVramSizeMB(vramSize / (1024 * 1024));
        
        // Get queue family properties
        IntBuffer pQueueFamilyCount = stack.mallocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyCount, null);
        
        int queueFamilyCount = pQueueFamilyCount.get(0);
        VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.calloc(queueFamilyCount, stack);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyCount, queueFamilies);
        
        for (int i = 0; i < queueFamilyCount; i++) {
            VkQueueFamilyProperties qf = queueFamilies.get(i);
            info.addQueueFamily(new QueueFamilyInfo(
                i,
                qf.queueFlags(),
                qf.queueCount(),
                qf.timestampValidBits()
            ));
        }
        
        return info;
    }
    
    /**
     * Check for device group capabilities
     */
    private void checkDeviceGroups(MemoryStack stack) {
        if (vkInstance == VK.NULL_HANDLE) return;
        
        // Get physical device group count
        IntBuffer pPhysicalDeviceGroupCount = stack.mallocInt(1);
        int err = vkEnumeratePhysicalDeviceGroups(vkInstance, pPhysicalDeviceGroupCount, null);
        
        if (err != VK.SUCCESS) {
            logger.debug("vkEnumeratePhysicalDeviceGroups not available or failed: {}", err);
            return;
        }
        
        int groupCount = pPhysicalDeviceGroupCount.get(0);
        logger.info("Found {} device group(s)", groupCount);
        
        if (groupCount > 0) {
            VkPhysicalDeviceGroupProperties.Buffer groups = 
                VkPhysicalDeviceGroupProperties.calloc(groupCount, stack);
            
            err = vkEnumeratePhysicalDeviceGroups(vkInstance, pPhysicalDeviceGroupCount, groups);
            if (err == VK.SUCCESS) {
                for (int i = 0; i < groupCount; i++) {
                    VkPhysicalDeviceGroupProperties group = groups.get(i);
                    logger.debug("Device Group {}: {} devices", i, group.physicalDeviceCount());
                }
            }
        }
    }
    
    private String formatVulkanVersion(int version) {
        return String.format("%d.%d.%d",
            VK_API_VERSION_MAJOR(version),
            VK_API_VERSION_MINOR(version),
            VK_API_VERSION_PATCH(version));
    }
    
    public long getInstance() {
        return vkInstance;
    }
}
