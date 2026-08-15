---
description: General instructions and context for developing the SimpleXray project.
---

# SimpleXray Project Context

This file provides the necessary context and constraints for AI agents interacting with the SimpleXray project. SimpleXray is an Android application acting as a VPN client/proxy tool using Xray core and hev-socks5-tunnel.

## Tech Stack
- **OS Target**: Android (minSdk 29, targetSdk 36, compileSdk 37)
- **Language**: Kotlin (for Android app) and C/C++ (for JNI / native tunnels).
- **UI Framework**: Jetpack Compose using the `miuix` component library.
- **Architecture**: MVVM with Android ViewModels.
- **Data Persistence**: Android DataStore Preferences (`androidx.datastore.preferences`).
- **Communication/RPC**: gRPC with Protocol Buffers (protobuf) to communicate with the Xray core. Note that the gRPC channel leverages **Unix Domain Sockets (UDS)** (e.g., using the `unix:$socketPath` target scheme via `ManagedChannelBuilder.forTarget`) for efficient local inter-process communication between the Android app and the native Xray process.
- **Native Components**: Uses CMake to build `hev-socks5-tunnel` and dependencies (`yaml`, `lwip`, `hev-task-system`) as native JNI libraries. The native Xray process is started with TUN file descriptors and UDS APIs.

## Project Structure
- `app/src/main/kotlin/com/simplexray/re/`:
  - `ui/`: Contains all Jetpack Compose screens, navigation, and scaffolds.
  - `viewmodel/`: Contains MVVM ViewModels.
  - `service/`: Contains Android background services, including `TProxyService` (VpnService implementation).
  - `prefs/`: Contains DataStore preference management.
  - `data/`: Data models and networking logic.
  - `common/`: Shared utilities and callbacks.
  - `activity/`: Android Activity classes (primarily `MainActivity` hosting Compose).
- `app/src/main/jni/`: C/C++ source code for native tunnels built via CMake.
- `app/src/main/proto/`: Protobuf definitions for gRPC.
- `third_party/miuix/`: Submodule containing the Miuix UI component library used for the application's design system. See `third_party/miuix/AGENTS.md` for specific UI constraints.

## Build and Execution
- **Build System**: Gradle with Kotlin DSL/Groovy.
- **Native Build**: NDK via CMake (`externalNativeBuild`).

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
