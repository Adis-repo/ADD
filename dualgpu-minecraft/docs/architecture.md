# DualGPU Renderer Architecture

## Overview

The DualGPU Renderer is a Fabric-compatible Minecraft client mod that enables two discrete AMD Radeon RX 6600 GPUs to contribute to rendering the same Minecraft frame through an OpenGL-to-Vulkan translation layer with explicit multi-GPU scheduling.

## System Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    Minecraft 1.21.4                          │
│                 (Vanilla / Sodium / Iris)                    │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ OpenGL API Calls
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              Layer A: OpenGL Compatibility                   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  • OpenGL State Tracker                               │  │
│  │  • Resource Abstraction                               │  │
│  │  • Framebuffer Interception                           │  │
│  │  • Shader Program Capture                             │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ Translated Commands
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              Layer B: Vulkan Translation                     │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  • Command Buffer Generation                          │  │
│  │  • Pipeline State Objects                             │  │
│  │  • Descriptor Management                              │  │
│  │  • Memory Allocation                                  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ Vulkan Commands
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              Layer C: Multi-GPU Scheduler                    │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  • Render Graph                                       │  │
│  │  • Task Scheduling                                    │  │
│  │  • Resource Sharing                                   │  │
│  │  • Synchronization                                    │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
            ┌───────────────┴───────────────┐
            │                               │
            ▼                               ▼
┌───────────────────────┐       ┌───────────────────────┐
│   AMD RX 6600 #0      │       │   AMD RX 6600 #1      │
│   (Primary GPU)       │       │   (Secondary GPU)     │
│                       │       │                       │
│  • Terrain            │       │  • Post-processing    │
│  • Entities           │       │  • Bloom              │
│  • Shadows            │       │  • SSAO               │
│  • G-Buffer           │       │  • Composite          │
│  • Depth              │       │  • Tonemapping        │
└───────────────────────┘       └───────────────────────┘
            │                               │
            └───────────────┬───────────────┘
                            │
                            ▼
                  Final Composition
                            │
                            ▼
                        Display
```

## Module Structure

```
com.dualgpu/
├── minecraft/           # Minecraft integration
│   ├── DualGPUModInitializer
│   ├── DualGPUClientMod
│   └── mixins/          # Mixin classes for interception
│
├── config/              # Configuration system
│   └── DualGPUConfig
│
├── diagnostics/         # Logging and debugging
│   ├── DualGPULogger
│   └── DebugOverlay
│
├── vulkan/              # Vulkan backend
│   ├── VulkanBackend
│   ├── device/          # Device enumeration
│   │   ├── PhysicalDeviceEnumerator
│   │   ├── PhysicalDeviceInfo
│   │   └── QueueFamilyInfo
│   ├── memory/          # Memory management
│   ├── command/         # Command buffers
│   └── sync/            # Synchronization
│
├── opengl/              # OpenGL compatibility layer
│   ├── GLStateTracker
│   ├── GLResourceMapper
│   └── GLInterceptor
│
├── graph/               # Render graph
│   ├── RenderGraph
│   ├── RenderPass
│   └── DependencyResolver
│
├── scheduler/           # GPU scheduling
│   ├── GpuScheduler
│   ├── RenderTask
│   └── LoadBalancer
│
├── resource/            # Resource management
│   ├── GpuResource
│   ├── GpuTexture
│   ├── GpuBuffer
│   └── ResourceManager
│
└── shader/              # Shader integration
    ├── ShaderPassManager
    └── IrisIntegration
```

## Data Flow

### Frame Rendering Flow

1. **Minecraft renders** using standard OpenGL calls
2. **OpenGL interceptor** captures state and commands
3. **State tracker** maintains current OpenGL state
4. **Vulkan translator** converts OpenGL commands to Vulkan
5. **Render graph** determines task dependencies
6. **GPU scheduler** assigns tasks to GPU 0 or GPU 1
7. **Resource manager** handles cross-GPU transfers
8. **Synchronization** ensures correct ordering
9. **Final composition** produces output frame
10. **Presentation** displays on monitor

### Resource Sharing Flow

```
GPU 0 renders geometry
        │
        ▼
Intermediate texture created on GPU 0
        │
        ▼
Ownership transfer via peer memory
        │
        ▼
GPU 1 reads intermediate texture
        │
        ▼
GPU 1 performs post-processing
        │
        ▼
Result transferred back to GPU 0
        │
        ▼
GPU 0 presents final image
```

## Key Design Decisions

### 1. OpenGL → Vulkan Translation

Rather than implementing a complete OpenGL driver, we:
- Track only the state Minecraft actually uses
- Translate at the draw-call level, not individual GL calls
- Batch multiple GL operations into single Vulkan commands
- Use dynamic rendering where possible (Vulkan 1.3)

### 2. Explicit Multi-GPU Scheduling

We do NOT rely on:
- CrossFire/SLI
- Driver-level AFR/SFR
- Implicit device groups

Instead we:
- Explicitly assign render passes to specific GPUs
- Track resource ownership per-GPU
- Use timeline semaphores for synchronization
- Minimize cross-GPU transfers

### 3. Render Graph Approach

Benefits:
- Clear dependency tracking
- Automatic parallelization opportunities
- Easy to visualize and debug
- Shader pack compatible

### 4. Resource Virtualization

All GPU resources are tracked with:
- Current residency (which GPU has it)
- Valid copies (which GPUs have valid copies)
- Last writer (for synchronization)
- Next readers (for prefetching)

### 5. Fallback Strategy

If dual-GPU fails:
1. Log error with detailed diagnostics
2. Disable dual-GPU mode
3. Continue with single-GPU Vulkan
4. If Vulkan fails, fall back to Minecraft's native OpenGL

## Milestone Progression

| Milestone | Goal | Status |
|-----------|------|--------|
| 1 | Vulkan device probe | IN PROGRESS |
| 2 | Standalone dual-GPU renderer | Planned |
| 3 | OpenGL → Vulkan test layer | Planned |
| 4 | Minecraft integration | Planned |
| 5 | Sodium compatibility | Planned |
| 6 | Iris compatibility | Planned |
| 7 | Dual-GPU single-pass experiment | Planned |
| 8 | Configurable shader-pass assignment | Planned |
| 9 | Automatic scheduling | Planned |
| 10 | Performance optimization | Planned |

## Compatibility Levels

| Level | Description | Target |
|-------|-------------|--------|
| 0 | Vanilla Minecraft | v1.0 |
| 1 | Sodium | v1.1 |
| 2 | Iris without shaders | v1.2 |
| 3 | Iris basic shader packs | v1.3 |
| 4 | Iris advanced shader packs | v2.0 |
| 5 | Experimental dual-GPU shader scheduling | v2.0+ |

## Performance Considerations

### Transfer Costs

Cross-GPU transfers are expensive. We track:
- Transfer size (bytes)
- Transfer time (ms)
- Synchronization overhead (ms)
- Frequency (transfers per frame)

Rule: Only transfer if `benefit > transfer_cost + sync_cost`

### Memory Policy

Resources have residency policies:
- `GPU0_ONLY`: Static vertex buffers, terrain
- `GPU1_ONLY`: Post-processing intermediates
- `BOTH`: Frequently accessed textures
- `MIGRATE`: Dynamic resources

### Synchronization Strategy

Prefer asynchronous overlap:
```
Frame N:   GPU0 → Geometry
Frame N-1: GPU1 → Post-process
Frame N-2: Present
```

Avoid full GPU synchronization unless necessary.

## Debugging Support

### Diagnostic Overlay

Shows in real-time:
- GPU utilization
- VRAM usage
- Frame times per GPU
- Transfer sizes and times
- Current scheduling mode

### Logging Levels

- `ERROR`: Critical failures
- `WARN`: Non-fatal issues
- `INFO`: Normal operation
- `DEBUG`: Detailed troubleshooting
- `TRACE`: Per-frame details

### Visual Debugging

Hotkey-toggled overlays:
- GPU 0 content: Normal colors
- GPU 1 content: Magenta tint
- Transfer visualization: Arrows showing data flow

## Testing Strategy

### Unit Tests
- Vulkan initialization
- Device enumeration
- Resource allocation

### Integration Tests
- OpenGL state tracking
- Command translation
- Synchronization primitives

### End-to-End Tests
- Full frame rendering
- Benchmark comparisons
- Shader pack compatibility

### Correctness Tests
- Pixel-perfect comparison with reference
- RMSE/PSNR metrics
- Maximum deviation thresholds

## Future Extensions

### Dynamic Scheduling (Milestone 9)

Automatic load balancing based on:
- Previous frame timings
- Current GPU load
- Transfer costs
- Resource locality

### Advanced Shader Integration (Milestone 8+)

Per-pass configuration:
```toml
[passes]
shadow = 0
gbuffers_terrain = 0
composite = 1
bloom = 1
ssao = 1
final = 1
```

### Developer API

For other mods to register GPU tasks:
```java
GpuRenderTask task = scheduler.registerTask(
    "CustomEffect",
    GpuTaskType.COMPUTE,
    requirements
);
task.preferredGpu(1);
task.submit();
```
