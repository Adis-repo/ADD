# Synchronization System

## Overview

Cross-GPU synchronization is critical for correct dual-GPU rendering. This document describes the synchronization primitives and strategies used by the DualGPU renderer.

## Vulkan Synchronization Primitives

### Timeline Semaphores (Preferred)

Vulkan 1.2+ feature that provides:
- Monotonically increasing signal values
- Multiple waits on single semaphore
- No need to recreate semaphores each frame
- CPU wait-free operation (mostly)

```cpp
VkSemaphoreTypeCreateInfo timelineInfo = {
    .sType = VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO,
    .semaphoreType = VK_SEMAPHORE_TYPE_TIMELINE,
    .initialValue = 0
};

VkSemaphoreCreateInfo createInfo = {
    .sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO,
    .pNext = &timelineInfo
};

vkCreateSemaphore(device, &createInfo, nullptr, &timelineSemaphore);
```

**Usage pattern:**
```
GPU0 submits task A, signals timeline[0] = 1
GPU1 waits timeline[0] == 1, then submits task B
GPU1 signals timeline[1] = 1 when complete
GPU0 waits timeline[1] == 1 for next frame dependencies
```

### Binary Semaphores

For simple one-wait-one-signal scenarios:
- Frame completion
- Image acquisition
- Queue-to-queue handoff

### Fences

CPU-GPU synchronization (minimized):
- Frame boundary detection
- Resource cleanup confirmation
- Error recovery

### Pipeline Barriers

Intra-queue synchronization:
- Layout transitions
- Access flag changes
- Execution dependencies

## Cross-GPU Synchronization Strategies

### Strategy 1: Device Group with Peer Memory

**Requirements:**
- Both GPUs in same device group
- Peer memory access available

**Mechanism:**
```
GPU0 renders to shared image
     │
     │ (no explicit transfer needed)
     ▼
GPU1 reads shared image directly
     │
     │ (ownership transfer via barrier)
     ▼
GPU1 writes result to shared image
     │
     ▼
GPU0 presents final output
```

**Synchronization:**
```cpp
// GPU0 completes rendering
VkMemoryBarrier2 memBarrier = {
    .srcStageMask = VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT,
    .srcAccessMask = VK_ACCESS_MEMORY_WRITE_BIT,
    .dstStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
    .dstAccessMask = VK_ACCESS_MEMORY_READ_BIT
};

// Signal completion to GPU1
VkSemaphoreSubmitInfo signalInfo = {
    .semaphore = crossGpuSemaphore,
    .value = frameIndex,
    .stageMask = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT
};

// GPU1 waits for signal
VkSemaphoreSubmitInfo waitInfo = {
    .semaphore = crossGpuSemaphore,
    .value = frameIndex,
    .stageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
};
```

### Strategy 2: Independent Devices with Explicit Transfer

**Requirements:**
- Standard Vulkan (no device group required)
- More portable but higher latency

**Mechanism:**
```
GPU0 renders to local image
     │
     │ (explicit copy to peer-visible memory)
     ▼
Copy to staging buffer/texture
     │
     │ (GPU1 reads from its address space)
     ▼
GPU1 reads copied image
     │
     │ (GPU1 renders post-process)
     ▼
Copy result back to GPU0
     │
     ▼
GPU0 presents
```

**Performance note:** This strategy has higher overhead due to explicit copies. Use only if device groups are unavailable.

## Frame Lifecycle

### Multi-Frame Pipelining

To maximize GPU utilization, we pipeline multiple frames:

```
Time ──────────────────────────────────────►

Frame N:   [GPU0: Geometry]──┬──[Sync]──[GPU1: Post]──┬──[Present]
                             │                        │
Frame N+1:                   [GPU0: Geometry]─────────┼──[Sync]──[GPU1: Post]
                                                      │
Frame N+2:                                           [GPU0: Geometry]
```

**Benefits:**
- GPU0 and GPU1 can work simultaneously on different frames
- Reduces idle time
- Increases throughput

**Requirements:**
- Multiple swapchain images (triple buffering minimum)
- Per-frame synchronization objects
- Careful resource lifetime management

### Frame Indexing

Each frame in flight has its own set of synchronization primitives:

```java
class FrameResources {
    int frameIndex;              // 0, 1, or 2
    long gpu0Fence;              // Signals GPU0 completion
    long gpu1Fence;              // Signals GPU1 completion
    long crossGpuSemaphore;      // GPU0 → GPU1 handoff
    long presentSemaphore;       // Present timing
    CommandBuffer gpu0Commands;  // GPU0 command buffer
    CommandBuffer gpu1Commands;  // GPU1 command buffer
}
```

## Ownership Transfers

### Queue Family Ownership

When resources move between queue families (or devices):

1. **Release** on source:
```cpp
VkImageMemoryBarrier releaseBarrier = {
    .srcAccessMask = VK_ACCESS_MEMORY_WRITE_BIT,
    .dstAccessMask = 0,
    .oldLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
    .newLayout = VK_IMAGE_LAYOUT_GENERAL,
    .srcQueueFamilyIndex = gpu0QueueFamily,
    .dstQueueFamilyIndex = gpu1QueueFamily,
    .image = sharedImage,
    .subresourceRange = { ... }
};
```

2. **Acquire** on destination:
```cpp
VkImageMemoryBarrier acquireBarrier = {
    .srcAccessMask = 0,
    .dstAccessMask = VK_ACCESS_MEMORY_READ_BIT,
    .oldLayout = VK_IMAGE_LAYOUT_GENERAL,
    .newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
    .srcQueueFamilyIndex = gpu0QueueFamily,
    .dstQueueFamilyIndex = gpu1QueueFamily,
    .image = sharedImage,
    .subresourceRange = { ... }
};
```

### Peer Memory Access

If peer memory is available:
- No explicit copy needed
- Only ownership barrier required
- Much lower latency

If peer memory is NOT available:
- Must use explicit copy operations
- Higher latency
- May negate dual-GPU benefits for small resources

## Synchronization Points

### Minimum Required Sync Points

Per frame, minimum synchronization:

1. **After GPU0 geometry** - GPU1 needs G-buffer for post-processing
2. **After GPU1 post-process** - GPU0 needs final image for presentation

### Optional Sync Points

Additional synchronization for correctness:

3. **Before shadow maps** - If shadows computed separately
4. **After bloom** - If tone mapping depends on bloom result
5. **Before present** - Ensure all work complete

## Debug Features

### Sync Visualization

Overlay showing:
- Green: GPU executing normally
- Yellow: GPU waiting on synchronization
- Red: GPU stalled (excessive wait)

### Timing Measurement

Track time spent in each sync operation:
```
[DualGPU] Frame 382 synchronization:
[DualGPU]   GPU0→GPU1 semaphore wait: 0.12ms
[DualGPU]   GPU1→GPU0 semaphore wait: 0.08ms
[DualGPU]   Total sync overhead: 0.20ms (2.4% of frame)
```

### Validation

Enable Vulkan validation layers to catch:
- Missing synchronization
- Incorrect semaphore usage
- Race conditions
- Deadlocks

## Common Pitfalls

### 1. Semaphore Reuse

**Wrong:** Using same binary semaphore multiple times without re-signaling.

**Right:** Use timeline semaphores or recreate binary semaphores each frame.

### 2. Missing Ownership Transfer

**Wrong:** Accessing resource on GPU1 without releasing from GPU0.

**Right:** Always pair release barrier with acquire barrier.

### 3. CPU Wait Bottleneck

**Wrong:** Calling `vkWaitForFences` every frame before submitting next.

**Right:** Use semaphores for GPU-GPU sync, fences only for CPU-GPU.

### 4. Over-Synchronization

**Wrong:** Synchronizing after every single operation.

**Right:** Batch operations and synchronize at logical boundaries.

## Performance Optimization

### Reduce Sync Frequency

Instead of per-task synchronization:
```
Task A → Sync → Task B → Sync → Task C
```

Batch independent tasks:
```
[Task A, Task B] → Sync → [Task C, Task D]
```

### Async Compute

If GPU supports graphics + compute queues:
```
Graphics Queue: Terrain → Entities → Shadows
Compute Queue:                    Bloom → SSAO
```

### Early Z Pre-Pass

Reduce fragment shader synchronization by resolving depth early.

## Future Extensions

### GPU-Driven Scheduling

Move scheduling logic to GPU compute shaders for minimal CPU overhead.

### Adaptive Sync Rate

Dynamically adjust synchronization frequency based on:
- Frame time budget
- GPU load imbalance
- Transfer queue depth

### Predictive Synchronization

Use historical timing data to predict optimal sync points rather than fixed positions.
