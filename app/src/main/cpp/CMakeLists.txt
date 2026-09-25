cmake_minimum_required(VERSION 3.22.1)
project(simplexray_native C CXX ASM)

# Global flags
add_compile_options(-O3 -DPKGNAME=com/simplexray/re/service)

# Resolve path to third_party directory
get_filename_component(PROJECT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
set(HEV_DIR "${PROJECT_ROOT}/third_party/hev-socks5-tunnel")
set(ZEPTUN_DIR "${PROJECT_ROOT}/third_party/zeptun")
set(ZEPTUN_ANDROID_DIR "${PROJECT_ROOT}/third_party/zeptun-android")

# Zeptun is built by its Zig Android target before the Gradle build. The
# project currently packages arm64-v8a only, so consume that prebuilt static
# library here and keep the upstream submodule itself unmodified.
set(ZEPTUN_STATIC_LIB "${ZEPTUN_DIR}/zig-out/android/prebuilt/arm64-v8a/libzeptun.a")
if(NOT EXISTS "${ZEPTUN_STATIC_LIB}")
    message(FATAL_ERROR
        "Missing Zeptun Android artifact: ${ZEPTUN_STATIC_LIB}. "
        "Run 'ANDROID_NDK_HOME=<ndk> zig build android -Doptimize=ReleaseSmall "
        "in third_party/zeptun first.")
endif()

# -----------------------------------------------------------------------------
# 1. hev-socks5-tunnel third-party static libraries
# -----------------------------------------------------------------------------

# YAML static lib
file(GLOB_RECURSE YAML_SOURCES ${HEV_DIR}/third-part/yaml/src/*.c)
add_library(yaml STATIC ${YAML_SOURCES})
target_include_directories(yaml PUBLIC ${HEV_DIR}/third-part/yaml/include)
target_compile_definitions(yaml PRIVATE
    YAML_VERSION_MAJOR=0
    YAML_VERSION_MINOR=2
    YAML_VERSION_PATCH=5
    YAML_VERSION_STRING="0.2.5"
)

# lwIP static lib
file(GLOB_RECURSE LWIP_SOURCES ${HEV_DIR}/third-part/lwip/src/*.c)
add_library(lwip STATIC ${LWIP_SOURCES})
target_include_directories(lwip PUBLIC
    ${HEV_DIR}/third-part/lwip/src/include
    ${HEV_DIR}/third-part/lwip/src/ports/include
)
target_compile_definitions(lwip PRIVATE
    FD_SET_DEFINED
    SOCKLEN_T_DEFINED
    LWIP_WND_SCALE=1
    TCP_RCV_SCALE=3
)

# hev-task-system static lib (arm64-v8a dedicated assembly & architecture header)
file(GLOB_RECURSE TASK_SYS_C_SOURCES ${HEV_DIR}/third-part/hev-task-system/src/*.c)
set(TASK_SYS_ASM ${HEV_DIR}/third-part/hev-task-system/src/arch/arm/hev-task-execute-aarch64.s)
set(TASK_SYS_ARCH_INC ${HEV_DIR}/third-part/hev-task-system/src/arch/arm)

set_source_files_properties(${TASK_SYS_ASM} PROPERTIES COMPILE_FLAGS "-x assembler-with-cpp")

add_library(hev-task-system STATIC ${TASK_SYS_C_SOURCES} ${TASK_SYS_ASM})
target_include_directories(hev-task-system PUBLIC
    ${HEV_DIR}/third-part/hev-task-system/src
    ${HEV_DIR}/third-part/hev-task-system/include
    ${TASK_SYS_ARCH_INC}
)
target_compile_definitions(hev-task-system PRIVATE
    ENABLE_STACK_OVERFLOW_DETECTION
    ENABLE_MEMALLOC_SLICE
    ENABLE_IO_SPLICE_SYSCALL
    CONFIG_STACK_BACKEND=STACK_MMAP
    CONFIG_STACK_OVERFLOW_DETECTION=1
    CONFIG_MEMALLOC_SLICE_ALIGN=64
    CONFIG_MEMALLOC_SLICE_MAX_SIZE=4096
    CONFIG_MEMALLOC_SLICE_MAX_COUNT=1000
    CONFIG_SCHED_CLOCK=CLOCK_NONE
)
target_compile_options(hev-task-system PRIVATE -fvisibility=hidden)

# -----------------------------------------------------------------------------
# 2. hev-socks5-tunnel shared library
# -----------------------------------------------------------------------------
file(GLOB_RECURSE TUNNEL_SOURCES
    ${HEV_DIR}/src/*.c
)

add_library(hev-socks5-tunnel SHARED ${TUNNEL_SOURCES})

target_include_directories(hev-socks5-tunnel PRIVATE
    ${HEV_DIR}/src
    ${HEV_DIR}/src/misc
    ${HEV_DIR}/src/core/include
    ${HEV_DIR}/third-part/yaml/include
    ${HEV_DIR}/third-part/lwip/src/include
    ${HEV_DIR}/third-part/lwip/src/ports/include
    ${HEV_DIR}/third-part/hev-task-system/include
)

target_compile_definitions(hev-socks5-tunnel PRIVATE
    FD_SET_DEFINED
    SOCKLEN_T_DEFINED
    ENABLE_LIBRARY
    COMMIT_ID="git"
    LWIP_WND_SCALE=1
    TCP_RCV_SCALE=3
)

target_link_libraries(hev-socks5-tunnel
    yaml
    lwip
    hev-task-system
    log
)

target_link_options(hev-socks5-tunnel PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

# -----------------------------------------------------------------------------
# 3. Zeptun JNI bridge and static engine
# -----------------------------------------------------------------------------
add_library(zeptun-prebuilt STATIC IMPORTED GLOBAL)
set_target_properties(zeptun-prebuilt PROPERTIES
    IMPORTED_LOCATION "${ZEPTUN_STATIC_LIB}"
    INTERFACE_INCLUDE_DIRECTORIES "${ZEPTUN_DIR}/include"
)

add_library(zeptun-jni SHARED "${ZEPTUN_ANDROID_DIR}/bridge.c")
target_include_directories(zeptun-jni PRIVATE "${ZEPTUN_DIR}/include")
target_compile_options(zeptun-jni PRIVATE -O3 -Wall -Wextra -Werror -std=c11)
target_link_libraries(zeptun-jni zeptun-prebuilt log)
target_link_options(zeptun-jni PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

# -----------------------------------------------------------------------------
# 4. SimpleTUN JNI bridge and static engine
# -----------------------------------------------------------------------------
set(SIMPLETUN_DIR "${PROJECT_ROOT}/third_party/simpletun")
set(SIMPLETUN_STATIC_LIB "${SIMPLETUN_DIR}/zig-out/android/prebuilt/arm64-v8a/libsimpletun.a")
if(NOT EXISTS "${SIMPLETUN_STATIC_LIB}")
    message(FATAL_ERROR
        "Missing SimpleTUN Android artifact: ${SIMPLETUN_STATIC_LIB}. "
        "Run 'zig build android' in third_party/simpletun first.")
endif()

add_library(simpletun-prebuilt STATIC IMPORTED GLOBAL)
set_target_properties(simpletun-prebuilt PROPERTIES
    IMPORTED_LOCATION "${SIMPLETUN_STATIC_LIB}"
    INTERFACE_INCLUDE_DIRECTORIES "${SIMPLETUN_DIR}/include"
)

add_library(simpletun-jni SHARED "${SIMPLETUN_DIR}/android/bridge.c")
target_include_directories(simpletun-jni PRIVATE "${SIMPLETUN_DIR}/include")
target_compile_options(simpletun-jni PRIVATE -O3 -Wall -Wextra -Werror -std=c11)
target_link_libraries(simpletun-jni simpletun-prebuilt log)
target_link_options(simpletun-jni PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

# -----------------------------------------------------------------------------
# 5. xray-exec shared library
# -----------------------------------------------------------------------------
add_library(xray-exec SHARED xray_exec.c)
target_link_libraries(xray-exec log)
target_link_options(xray-exec PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

