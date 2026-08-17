cmake_minimum_required(VERSION 3.22.1)
project(simplexray_native C CXX ASM)

# Global flags
add_compile_options(-O3 -DPKGNAME=com/simplexray/re/service)

# -----------------------------------------------------------------------------
# 1. hev-socks5-tunnel third-party static libraries
# -----------------------------------------------------------------------------

# YAML static lib
file(GLOB_RECURSE YAML_SOURCES hev-socks5-tunnel/third-part/yaml/src/*.c)
add_library(yaml STATIC ${YAML_SOURCES})
target_include_directories(yaml PUBLIC hev-socks5-tunnel/third-part/yaml/include)
target_compile_definitions(yaml PRIVATE
    YAML_VERSION_MAJOR=0
    YAML_VERSION_MINOR=2
    YAML_VERSION_PATCH=5
    YAML_VERSION_STRING="0.2.5"
)

# lwIP static lib
file(GLOB_RECURSE LWIP_SOURCES hev-socks5-tunnel/third-part/lwip/src/*.c)
add_library(lwip STATIC ${LWIP_SOURCES})
target_include_directories(lwip PUBLIC
    hev-socks5-tunnel/third-part/lwip/src/include
    hev-socks5-tunnel/third-part/lwip/src/ports/include
)
target_compile_definitions(lwip PRIVATE FD_SET_DEFINED SOCKLEN_T_DEFINED)

# hev-task-system static lib (arm64-v8a dedicated assembly & architecture header)
file(GLOB_RECURSE TASK_SYS_C_SOURCES hev-socks5-tunnel/third-part/hev-task-system/src/*.c)
set(TASK_SYS_ASM hev-socks5-tunnel/third-part/hev-task-system/src/arch/arm/hev-task-execute-aarch64.s)
set(TASK_SYS_ARCH_INC hev-socks5-tunnel/third-part/hev-task-system/src/arch/arm)

set_source_files_properties(${TASK_SYS_ASM} PROPERTIES COMPILE_FLAGS "-x assembler-with-cpp")

add_library(hev-task-system STATIC ${TASK_SYS_C_SOURCES} ${TASK_SYS_ASM})
target_include_directories(hev-task-system PUBLIC
    hev-socks5-tunnel/third-part/hev-task-system/src
    hev-socks5-tunnel/third-part/hev-task-system/include
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
    hev-socks5-tunnel/src/*.c
)

add_library(hev-socks5-tunnel SHARED ${TUNNEL_SOURCES})

target_include_directories(hev-socks5-tunnel PRIVATE
    hev-socks5-tunnel/src
    hev-socks5-tunnel/src/misc
    hev-socks5-tunnel/src/core/include
    hev-socks5-tunnel/third-part/yaml/include
    hev-socks5-tunnel/third-part/lwip/src/include
    hev-socks5-tunnel/third-part/lwip/src/ports/include
    hev-socks5-tunnel/third-part/hev-task-system/include
)

target_compile_definitions(hev-socks5-tunnel PRIVATE
    FD_SET_DEFINED
    SOCKLEN_T_DEFINED
    ENABLE_LIBRARY
    COMMIT_ID="git"
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
# 3. xray-exec shared library
# -----------------------------------------------------------------------------
add_library(xray-exec SHARED xray-exec/xray_exec.c)
target_link_libraries(xray-exec log)
target_link_options(xray-exec PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)
