# GPU Scheduling System

## Overview

The DualGPU scheduler assigns rendering tasks to specific GPUs based on:
- Configuration (manual mode)
- Resource locality
- Load balancing (automatic mode, future)
- Dependency resolution

## Render Task Model

Each unit of work is represented as a `RenderTask`:

```java
public class RenderTask {
    String name;              // e.g., "Bloom", "ShadowMap"
    GpuTaskType type;         // GRAPHICS, COMPUTE, TRANSFER
    int preferredGpu;         // 0 or 1, -1 for auto
    List<Resource> inputs;    // Resources this task reads
    List<Resource> outputs;   // Resources this task writes
    List<String> dependencies;// Task IDs that must complete first
    long estimatedCostMs;     // Estimated execution time
    Priority priority;        // HIGH, NORMAL, LOW
}
```

## Task Types

### GRAPHICS Tasks
Traditional rasterization operations:
- Terrain rendering
- Entity rendering
- Shadow map generation
- G-buffer creation
- Depth pre-pass

### COMPUTE Tasks
Compute shader operations:
- Bloom filter
- SSAO
- Volumetric fog
- Denoising
- Image effects

### TRANSFER Tasks
Resource movement operations:
- GPU 0 → GPU 1 transfer
- GPU 1 → GPU 0 transfer
- Memory copies
- Buffer updates

## Scheduling Modes

### Manual Mode (Milestone 1-8)

User explicitly configures which GPU handles each pass:

```toml
[passes]
shadow = 0
gbuffers_terrain = 0
bloom = 1
composite = 1
final = 1
```

**Pros:**
- Predictable behavior
- Easy to debug
- User has full control

**Cons:**
- Requires manual tuning
- Doesn't adapt to workload changes

### Auto Mode (Milestone 9+)

Scheduler automatically assigns tasks based on:
- Previous frame timings
- Current GPU load
- Transfer costs
- Resource residency

**Decision algorithm:**
```
for each task in render_graph:
    cost_gpu0 = estimate_cost(task, gpu=0)
    cost_gpu1 = estimate_cost(task, gpu=1) + transfer_cost
    
    if cost_gpu1 < cost_gpu0 * 0.9:  # 10% threshold
        assign(task, gpu=1)
    else:
        assign(task, gpu=0)
```

### Single Mode

Force all tasks to GPU 0 (fallback/disabled dual-GPU).

### Experimental Mode

Enable cutting-edge features that may be unstable.

### Benchmark Mode

Run comparative tests between single and dual-GPU configurations.

## Dependency Resolution

The scheduler builds a directed acyclic graph (DAG) of task dependencies:

```
Terrain ──┬── GBuffer ──┬── Lighting ── HDR ──┬── Bloom ── Final
          │             │                     │
Entities ─┘             │                     └── Composite
                        │
Shadows ────────────────┘
```

**Scheduling constraints:**
1. A task cannot start until all dependencies complete
2. Cross-GPU dependencies require synchronization
3. Same-GPU tasks can potentially overlap (different queues)

## Synchronization Points

### Timeline Semaphores

Preferred synchronization method (Vulkan 1.2+):
- Binary semaphore per task completion
- Wait value = signal value from dependency
- No CPU involvement required

### Pipeline Barriers

For same-GPU resource transitions:
- Layout transitions
- Access flag changes
- Queue family ownership transfers

### Fences

For CPU-GPU synchronization (minimized):
- Frame completion
- Resource cleanup
- Debug markers

## Resource Residency Policies

### GPU0_ONLY
Resource stays on GPU 0:
- Static vertex buffers
- Terrain chunk data
- Minecraft texture atlas (if rarely used by GPU 1)

### GPU1_ONLY
Resource stays on GPU 1:
- Post-processing intermediates
- Bloom pyramid
- SSAO texture

### BOTH (Replicated)
Resource exists on both GPUs:
- Frequently accessed uniforms
- Common lookup tables
- Static skybox textures

### MIGRATE
Resource moves between GPUs:
- HDR framebuffer
- Depth buffer (if needed by both)
- Dynamic shadow maps

## Load Balancing Algorithm (Future)

```python
def schedule_frame(tasks, gpu_stats):
    schedule = {}
    
    for task in topological_sort(tasks):
        # Calculate cost for each GPU
        cost_0 = task.base_cost * gpu_stats[0].load_factor
        cost_1 = task.base_cost * gpu_stats[1].load_factor
        
        # Add transfer cost if crossing GPUs
        for dep in task.dependencies:
            if schedule[dep].gpu == 0:
                cost_1 += transfer_cost_0_to_1
            else:
                cost_0 += transfer_cost_1_to_0
        
        # Assign to cheaper GPU
        if cost_0 < cost_1:
            schedule[task] = Assignment(gpu=0, cost=cost_0)
        else:
            schedule[task] = Assignment(gpu=1, cost=cost_1)
    
    return schedule
```

## Performance Metrics

### Per-Frame Statistics
- GPU 0 execution time (ms)
- GPU 1 execution time (ms)
- Synchronization overhead (ms)
- Transfer time (ms)
- Total frame time (ms)

### Long-term Statistics
- Average FPS
- 1% low FPS
- 0.1% low FPS
- GPU utilization percentage
- VRAM usage per GPU
- PCIe bandwidth usage

## Debug Features

### Task Visualization
Color-coded overlay showing which GPU rendered each portion:
- Green: GPU 0 tasks
- Magenta: GPU 1 tasks
- Yellow: Synchronization points
- Red: Stalls/bottlenecks

### Timing Graph
Real-time graph of:
- Task execution times
- GPU idle periods
- Transfer durations

### Log Output
```
[DualGPU] Frame 382 scheduling:
[DualGPU]   Terrain -> GPU0 (est. 4.2ms)
[DualGPU]   GBuffer -> GPU0 (est. 2.1ms)
[DualGPU]   Bloom -> GPU1 (est. 1.8ms, +0.3ms transfer)
[DualGPU]   Composite -> GPU1 (est. 1.2ms)
[DualGPU]   Final -> GPU0 (est. 0.5ms, +0.2ms transfer)
[DualGPU]   Sync points: 3
[DualGPU]   Estimated total: 8.3ms
```

## Future Extensions

### Dynamic Re-scheduling
Mid-frame adjustment if a task takes longer than estimated.

### Machine Learning
Predict optimal scheduling based on historical data.

### Shader Pack Awareness
Understand shader pass structure for better assignment.

### Multi-threaded Command Recording
Parallel command buffer generation on CPU side.
