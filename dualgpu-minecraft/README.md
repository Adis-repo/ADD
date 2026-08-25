# DualGPU Minecraft Renderer

A Fabric-compatible Minecraft client mod that enables two discrete AMD Radeon RX 6600 GPUs to contribute to rendering the same Minecraft frame through an OpenGL-to-Vulkan translation layer with explicit multi-GPU scheduling.

## Project Status: **Milestone 1 - Vulkan Device Probe**

This project is in early development. The current focus is on Milestone 1: standalone dual-GPU Vulkan probe to detect and enumerate both RX 6600 GPUs and determine device-group capabilities.

## Target Specifications

- **Minecraft Version**: 1.21.4 (selected for Fabric/LWJGL/Vulkan compatibility)
- **Loader**: Fabric Loader
- **Renderer Backend**: Vulkan 1.3+
- **Target GPUs**: AMD Radeon RX 6600 × 2
- **Compatibility Levels**:
  - Level 0: Vanilla Minecraft ✓ (planned)
  - Level 1: Sodium (planned)
  - Level 2: Iris without shaders (planned)
  - Level 3: Iris basic shader packs (planned)
  - Level 4: Iris advanced shader packs (planned)
  - Level 5: Experimental dual-GPU shader scheduling (current development)

## Architecture Overview

```
Minecraft 1.21.X
       │
       │ Existing OpenGL rendering API
       ▼
OpenGL interception / translation layer
       │
       ▼
Custom Vulkan rendering backend
       │
       ├───────────────────────────────┐
       ▼                               ▼
AMD RX 6600 #1                       AMD RX 6600 #2
Main Minecraft rendering            Selected rendering/shader work
       │                               │
       └──────────────┬────────────────┘
                      ▼
                Final composition
                      │
                      ▼
                   Display
```

## Development Milestones

| Milestone | Description | Status |
|-----------|-------------|--------|
| 1 | Standalone dual-GPU Vulkan probe | **IN PROGRESS** |
| 2 | Standalone dual-GPU Vulkan renderer | Planned |
| 3 | OpenGL → Vulkan test layer | Planned |
| 4 | Minecraft integration | Planned |
| 5 | Sodium compatibility | Planned |
| 6 | Iris compatibility | Planned |
| 7 | Dual-GPU single-pass experiment | Planned |
| 8 | Configurable shader-pass assignment | Planned |
| 9 | Automatic scheduling | Planned |
| 10 | Performance optimization | Planned |

## Current Focus: Milestone 1

The first objective is proving that a single Minecraft frame can be rendered correctly using meaningful work from both physical RX 6600 GPUs, with the second GPU's output incorporated into the final image.

Before Minecraft integration, we must verify:

1. Both RX 6600s are detected by Vulkan
2. Device-group capabilities are enumerated
3. Peer memory access is available between GPUs
4. Resources can be shared without CPU readbacks
5. GPU 0 can render an image that GPU 1 processes
6. The result can be presented

## Project Structure

```
dualgpu-minecraft/
│
├── README.md                    # This file
├── LICENSE                      # MIT License
├── build.gradle                 # Gradle build configuration
├── gradle.properties            # Version properties
│
├── src/main/java/
│   └── com/dualgpu/
│       ├── minecraft/           # Minecraft integration (Fabric)
│       ├── opengl/              # OpenGL interception & state tracking
│       ├── vulkan/              # Vulkan backend abstraction
│       ├── scheduler/           # GPU task scheduler
│       ├── graph/               # Render graph & dependencies
│       ├── resource/            # Resource management & virtualization
│       ├── shader/              # Iris/shader integration
│       ├── diagnostics/         # Debug overlay & logging
│       └── config/              # Configuration system
│
├── src/main/cpp/
│   ├── vulkan/                  # Native Vulkan backend
│   ├── device/                  # Device enumeration & management
│   ├── sync/                    # Synchronization primitives
│   └── native/                  # JNI bindings
│
├── docs/
│   ├── architecture.md          # System architecture details
│   ├── gpu-scheduling.md        # GPU scheduling algorithms
│   ├── synchronization.md       # Cross-GPU synchronization
│   ├── shader-integration.md    # Iris/shader pack integration
│   ├── compatibility.md         # Compatibility matrix
│   └── troubleshooting.md       # Common issues & solutions
│
└── tests/
    ├── Vulkan device tests
    ├── device-group tests
    ├── resource-sharing tests
    ├── synchronization tests
    ├── shader tests
    └── benchmark tests
```

## Configuration Example

```toml
# config/dual_gpu.toml

[general]
enabled = true
mode = "manual"  # auto, single, manual, experimental, benchmark

[gpu]
gpu0 = 0
gpu1 = 1
present_gpu = 0

[features]
allow_shader_splitting = true
allow_compute_split = true
allow_dynamic_scheduling = true

[debug]
debug_overlay = false
debug_logging = false
benchmark_mode = false
max_frames_in_flight = 2

[passes]
# Shader pass assignments (gpu=0, gpu=1, gpu=auto, disabled)
shadow = 0
gbuffers_terrain = 0
gbuffers_entities = 0
composite = 1
bloom = 1
final = 1
```

## GPU Assignment Model

The renderer uses a **Render Task** model rather than splitting individual OpenGL calls:

```
RenderTask:
    name = "Bloom"
    type = COMPUTE
    source = HDRColor
    destination = BloomTexture
    gpu = GPU1
    dependencies = [GBuffer, HDRColor]
    estimated_cost = 2.3ms
    synchronization_requirements = [timeline_semaphore]
```

## Render Graph Example

```
Minecraft Geometry
        │
        ▼
G-Buffer (GPU0)
        │
        ├───────────────┐
        ▼               ▼
Shadow Map (GPU0)   Depth Buffer (GPU0)
        │               │
        └───────┬───────┘
                ▼
             Lighting (GPU0)
                │
                ▼
               HDR (GPU0)
                │
                ▼
             Bloom (GPU1) ← Second GPU contributes here
                │
                ▼
            Tonemapping (GPU1)
                │
                ▼
              Output (GPU0)
                │
                ▼
              Display
```

## Diagnostic Overlay

When enabled, the debug overlay shows:

```
Dual GPU Renderer
Status: ENABLED (Experimental)

GPU 0: AMD Radeon RX 6600
  Utilization: 92%
  VRAM: 6142 MB / 8192 MB
  Frame time: 8.4 ms

GPU 1: AMD Radeon RX 6600
  Utilization: 61%
  VRAM: 2840 MB / 8192 MB
  Frame time: 4.1 ms

Cross-GPU Transfer:
  GPU0→GPU1: 16.6 MB (0.42 ms)
  GPU1→GPU0: 8.3 MB (0.21 ms)

Synchronization: 0.8 ms
Total Frame: 13.3 ms
FPS: 75

Mode: Manual
```

## Absolute Development Rules

1. **Never invent APIs** - Verify all Vulkan/LWJGL/Minecraft APIs against documentation
2. **Never assume two GPUs can share memory** - Detect actual capability via `vkEnumeratePhysicalDeviceGroups`
3. **Never claim dual-GPU support merely because both devices are detected** - Verify peer memory access
4. **Never introduce CPU readbacks as the normal frame-transfer mechanism** - Use peer memory or device-group transfers
5. **Never silently fall back while claiming dual-GPU mode is active** - Clear status reporting
6. **Every GPU assignment must be visible in debug mode** - Visual proof of which GPU renders what
7. **Every synchronization point must be explainable** - Document why each barrier/semaphore exists
8. **Every cross-GPU transfer must be measurable** - Track size, time, and frequency
9. **Prioritize correctness over performance initially** - Working > fast
10. **Do not implement the entire system in one step** - Validate each milestone separately

## Success Criteria for Version 1.0

Version 1.0 is complete when:

- [ ] Minecraft launches with the mod installed
- [ ] Minecraft renders correctly using the new backend
- [ ] Two RX 6600s are detected and enumerated
- [ ] GPU 0 performs Minecraft terrain/entity rendering
- [ ] GPU 1 performs a real rendering/post-processing task (e.g., bloom)
- [ ] GPU 1's output contributes to the displayed frame
- [ ] Single-GPU fallback works if dual-GPU fails
- [ ] Diagnostics prove which GPU performed which work
- [ ] Benchmark system compares single vs dual GPU performance

## Performance Objectives

The target is **not necessarily 2× FPS**. Realistic goals:

- **Initial version**: GPU0 alone: 100 FPS → GPU0 + GPU1: 110 FPS (proof of concept)
- **Future version**: Substantial improvement for expensive shader packs where GPU 1 handles post-processing

The benchmark system must honestly report whether dual-GPU mode improves performance.

## Testing Matrix

Minimum testing requirements:

- [ ] Minecraft vanilla (single GPU)
- [ ] Minecraft vanilla (dual GPU)
- [ ] Minecraft + Sodium (single GPU)
- [ ] Minecraft + Sodium (dual GPU)
- [ ] Minecraft + Iris (single GPU)
- [ ] Minecraft + Iris (dual GPU)
- [ ] Minecraft + Iris + shader pack (single GPU)
- [ ] Minecraft + Iris + shader pack (dual GPU)
- [ ] Multiple resolutions (1080p, 1440p)
- [ ] Different render distances
- [ ] Lightweight and expensive shader packs

## Failure Recovery

The mod implements mandatory fallback behavior:

```
Dual-GPU available → use dual GPU
Dual-GPU unavailable → use selected single GPU
Dual-GPU crashes/fails → disable dual mode, log error
Vulkan unavailable → use normal Minecraft renderer
```

The mod must never make Minecraft unplayable due to dual-GPU failures.

## Driver Requirements

- **Primary Target**: Windows 10/11 + AMD Adrenalin drivers
- **Vulkan Version**: 1.3+ recommended
- **Required Capabilities**: Device-group support, peer memory access

If required capabilities are missing:
```
Dual-GPU mode unavailable.
Reason: Required Vulkan device-group/peer-memory capability not supported.
Falling back to single-GPU mode.
```

## Logging Levels

- **ERROR**: Critical failures requiring immediate attention
- **WARN**: Non-fatal issues that may affect performance/correctness
- **INFO**: Normal operational messages (startup, mode changes)
- **DEBUG**: Detailed information for troubleshooting
- **TRACE**: Per-frame resource transfers, GPU assignments, timing

## Developer API (Planned)

Future versions will expose an API for other mods:

```java
GpuRenderTask registerTask(
    String name,
    GpuTaskType type,
    ResourceRequirements resources
);

task.preferredGpu(1);
task.allowMigration(true);
task.setPriority(RenderPriority.NORMAL);
```

Exposed interfaces:
- `GpuCapabilities`
- `RenderGraph`
- `GpuScheduler`
- `GpuResource`
- `GpuTexture`
- `GpuBuffer`

## License

MIT License - See LICENSE file for details.

## Contributing

This is a research/development project. Contributors should:

1. Understand the absolute development rules
2. Verify all API usage against official documentation
3. Test changes on both single and dual GPU configurations
4. Provide benchmark data for performance changes
5. Document any driver-specific behavior

## Disclaimer

This mod is experimental and may cause:
- Game crashes
- Driver instability
- Reduced performance compared to single-GPU
- Incompatibility with certain shader packs

Use at your own risk. Always backup your Minecraft installation before installing experimental mods.

---

**First Objective**: Prove that a single Minecraft frame can be rendered correctly using meaningful work from both physical RX 6600 GPUs, with the second GPU's output incorporated into the final image.
