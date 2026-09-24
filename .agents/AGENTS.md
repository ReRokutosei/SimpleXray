---
description: General instructions and context for developing the SimpleXray project.
---

# SimpleXray Project Context

This file provides the necessary context and constraints for AI agents interacting with the SimpleXray project. SimpleXray is an Android application acting as a VPN client/proxy tool using Xray-core, sing-tun, hev-socks5-tunnel, and Mihomo mipstack.

## Tech Stack
- **OS Target**: Android (minSdk 34, targetSdk 36, compileSdk 37)
- **Language**: Kotlin (for Android app) and C/C++ (for JNI / native tunnels).
- **UI Framework**: Jetpack Compose using the `miuix` component library.
- **Architecture**: MVVM with Android ViewModels.
- **Data Persistence**: Direct Android `SharedPreferences`.
- **Communication/RPC**: gRPC with Protocol Buffers (protobuf) to query Xray core status and traffic statistics through a dynamically allocated `127.0.0.1` TCP port. Service status and process logs are communicated reactively via in-memory `VpnStateHub` (`StateFlow` and `SharedFlow`).
- **Native Components & TUN Backends**: Uses CMake to build `hev-socks5-tunnel` (C/lwIP) and dependencies as native JNI libraries, integrates `sing-tun` (Go stack), `zeptun` (Zig userspace stack), and integrates `mipstack` (Mihomo pure Go stack). Supports 4 primary TUN backends: Hev (default, C/lwIP for optimal throughput and low power consumption), SingTUN (Go/sing-box), Zeptun (Zig userspace stack), and native Xray TUN (Go/gVisor). In native Xray TUN mode, a JNI launcher passes the Android VPN file descriptor to the Xray child process. In Hev, SingTUN, and Zeptun modes, the tunnel forwards traffic to Xray through its local SOCKS5 inbound.

## Project Structure
- `app/src/main/kotlin/com/simplexray/re/`:
  - `ui/`: Contains all Jetpack Compose screens, navigation, and scaffolds.
  - `viewmodel/`: Contains MVVM ViewModels.
  - `service/`: Contains Android background services, including `TProxyService` (VpnService implementation).
  - `prefs/`: Contains the preference contract, provider, and access wrapper for `SharedPreferences`.
  - `data/`: Data models and networking logic.
  - `common/`: Shared utilities and callbacks.
  - `activity/`: Android Activity classes (primarily `MainActivity` hosting Compose).
- `app/src/main/jni/`: C/C++ source code for native tunnels built via CMake.
- `app/src/main/proto/`: Protobuf definitions for gRPC.
- `third_party/miuix/`: Submodule containing the Miuix UI component library used for the application's design system. See `third_party/miuix/AGENTS.md` for specific UI constraints.
- `docs/benchmark/`: Benchmark whitepaper (`android-tun-benchmark.md`), device dataset directories (`docs/benchmark/<profile>/data/`), and generated chart dashboards (`docs/benchmark/<profile>/charts/`).
- `docs/images/`: Standardized 16:9 light-theme WebP dashboards, master infographic (`mega_benchmark_infographic.webp`), and architectural diagrams.
- `version.properties`: Root version contract tracking live core (`XRAY_CORE_VERSION`) and tunnel backend commits/hashes (`HEV_TUN_VERSION`, `SING_TUN_VERSION`, `MIPS_TUN_VERSION`, `GO_VERSION`, `NDK_VERSION`).
- `tools/`: Automated benchmarking tools & modular pipeline:
  - `benchmark.py`: Unified master CLI runner orchestrating throughput, idle memory, bufferbloat, stability, weaknet, and CPS suites. Supports `--preset light` and `--preset full`.
  - `presets.py`: Formal benchmark contract definitions (`light` vs `full`).
  - `generate_charts.py`: Unified publication dashboard generator producing standardized 16:9 WebP charts (Wi-Fi, Bufferbloat, Stability, Idle Memory, CPS, Weaknet, Scheme 2 Memory Attribution).
  - `generate_mega_dashboard.py`: 8-archetype 3-row master infographic generator.
  - `common/`: Core shared libraries (`adb.py`, `device.py`, `iperf.py`, `dataset.py`, `theme.py`, `netem.py`, `logging.py`).
  - `suites/`: High-cohesion benchmark suites (`throughput.py`, `idle_memory.py`, `bufferbloat.py`, `stability.py`, `weaknet.py`, `cps.py`).
  - `update_benchmark_doc.py`: Precise, in-place non-destructive markdown table synchronizer for `android-tun-benchmark.md`.
  - `sync_versions.py`: Automated submodule and go.mod dependency inspector and `version.properties` synchronizer.
  - `idle_bench/`: Low-overhead Go connection retention and PSS memory sampling probe (cross-compiled for `amd64` and `arm64`).
  - `microbench/`: Standalone Linux user-namespace microbench harness (`unshare -r -n`) testing pure user-space TUN stacks in isolation (Scheme 2).

## Build and Execution
- **Build System**: Gradle with Kotlin DSL/Groovy.
- **Native Build**: NDK via CMake (`externalNativeBuild`).
- **Standalone TUN CLI**: Go stacks (`third_party/sing-tun`, `third_party/mips-tun`) support standalone CLI compilation via standard `go build` with `#if defined(__ANDROID__)` guards for dual host/Android compatibility.
- **Version Verification**: Run `python3 tools/sync_versions.py --check` before committing to verify that `version.properties` matches all submodules and `go.mod` dependency hashes.
- **Headless Benchmark Service**: `BenchmarkService` operates headlessly via `am start-foreground-service` intents (`--es cmd start/stop --es backend ... --ei mtu ...`), validated by active `tun0` hardware interface polling.

## Coding Guidelines for AI Agents
1. **Jetpack Compose**: Follow standard Compose best practices (state hoisting, `remember` for complex derived states, non-blocking composition).
2. **Miuix Integration**: Use `miuix` components instead of standard Material components when possible, matching the library's design language. Do NOT use hardcoded colors; use `MiuixTheme`.
3. **Coroutines**: Use Kotlin Coroutines and Flows for all asynchronous operations.
4. **Service Lifecycle**: When modifying `VpnService` or background tasks, respect Android's strict background execution limits and ensure proper foreground service notifications.
5. **Protobuf/gRPC**: If data models change, ensure corresponding `.proto` files are updated and Gradle is synced to regenerate Java/Kotlin stubs.
6. **Native Code**: Native code changes in `jni/` require understanding of POSIX sockets, lwIP, and hev-socks5-tunnel architecture. Ensure ABI compatibility (`arm64-v8a` is the primary target).

## Key Constraints
- NEVER break the `VpnService` transparent proxy behavior. Testing native traffic routing is critical.
- Keep the UI responsive and aesthetic, prioritizing the `miuix` design system.
- **SOCKS5 UDP ASSOCIATE Protocol Compliance**: When integrating or modifying user-space TUN handlers in Go (`sing-tun`, `mips-tun`), `client.ListenPacket` must pass an unspecified bind address (`0.0.0.0:0` / `M.Socksaddr{}`) as `BND.ADDR`, NOT the remote target destination. Passing foreign destinations violates RFC 1928 and causes Xray's SOCKS5 inbound to drop client packets.
- **Android 14+ Foreground Service Limits**: Calling `ContextCompat.startForegroundService()` from background components (such as `BroadcastReceiver`) will throw `ForegroundServiceStartNotAllowedException` on Android 14+ unless an activity is brought to the foreground first (e.g. `startActivity` with `FLAG_ACTIVITY_NEW_TASK`).
- **Rule File Background Updates**: Rule files (geoip/geosite) default to GitHub URLs. In mainland network environments, downloading from GitHub requires an active proxy; additionally, Chinese OEM ROMs (e.g. HyperOS/MIUI) restrict background WorkManager execution. Retain the in-service periodic check coroutine (`startPeriodicGeoUpdateCheck`) in `TProxyService`—it executes while the VPN is active as a foreground service with guaranteed local proxy availability.

## Benchmark Visualization & Chart Design Guidelines
1. **Visual Encoding & Cognitive Load**:
   - **Zero-Legend Overhead when Self-Describing (Context-Aware)**: For categorical horizontal bar charts where category names, brand colors, and flow directions are embedded directly on the Y-axis or adjacent to bars, omit top legends entirely. For continuous line charts (e.g. `sustained_stability.webp`, `idle_memory.webp`, `weaknet_throughput.webp`) where multiple backend curves share the same coordinate space, retain a clean, centered top legend using strictly standard brand colors.
   - **Brand Color Binding**: Always bind backend labels and elements strictly to project brand colors:
     - `Hev`: `#555555`
     - `SingTUN`: `#00ADD8`
     - `Zeptun`: `#F7A41D`
   - **Y-Axis Hierarchy & Symmetry**: For multi-panel comparisons (e.g. 1×2 layouts), render Y-axis ticks and category labels on the left panel only; omit redundant labels and ticks on the right panel. Category headers (e.g. Backend name) should be vertically centered across their sub-items (e.g. between 4W and 8W).
   - **Natural Ordering**: Order concurrent workloads progressively (e.g. top-to-bottom: 4 Workers before 8 Workers).
   - **No Meaningless Spacers**: If a backend is not applicable to a workload (e.g. Hev exceeding PCB limits on 5,000 CPS), do not allocate empty bar rows that break layout continuity; annotate via a concise, centered bottom footnote instead.
   - **Title Conventions**: Do NOT use ` · ` in chart titles or subtitles. Always suffix metrics with direction indicators where appropriate: `(Higher is Better)` or `(Lower is Better)`.
   - **Typography**: Strictly use JetBrains Mono via `prop_regular`, `prop_bold`, `prop_medium`.

## Benchmark Technical Report Guidelines
1. **Engineering Neutrality & Factuality**:
   - Write in an objective, factual, reproducible, and neutral tone.
   - Avoid subjective or promotional superlatives (e.g. `excellent`, `superior`, `best`, `amazing`). Prefer descriptive measurements: `achieved`, `recorded`, `measured`, `decreased to`, `exceeded`.
   - Differentiate strictly between **measured facts** and **hypothesized mechanisms**. Do not state correlation as causation without profiling proof.
2. **Structure & Density (High Signal-to-Noise Ratio)**:
   - Follow standard technical paper structure: `Test Environment -> Methodology -> Results -> Discussion -> Limitations -> Summary`.
   - **Privacy & Noise Filtering**: In environment tables, omit private or redundant local network noise (e.g. SSIDs, internal LAN IPs, retail branding suffixes, full kernel hashes). Keep only essential technical variables: SoC platform/cores, OS version, kernel release, network band/type, baseline RTT.
   - **Results Writing**: Charts and tables present the raw data; body text should summarize key findings, trends, and relative ratios using concise bullet points (`-`). Do NOT mechanically recite every cell of the tables in prose.
   - **Limitations & Scope**: Always state the hardware, network, and software boundaries. Avoid extrapolating single-device lab findings into universal conclusions.

## Device Testing and Commits
- For Android/VPN/TUN or other device-dependent changes, do not create a commit until the user confirms that the change has passed real-device testing.
- Keep such changes uncommitted in the working tree while waiting for real-device verification.
