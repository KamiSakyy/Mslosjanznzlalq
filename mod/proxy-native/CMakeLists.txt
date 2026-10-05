cmake_minimum_required(VERSION 3.22.1)
project(sakura_proxy LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(MBEDTLS_FATAL_WARNINGS OFF CACHE BOOL "" FORCE)
set(USE_STATIC_MBEDTLS_LIBRARY ON CACHE BOOL "" FORCE)
set(USE_SHARED_MBEDTLS_LIBRARY OFF CACHE BOOL "" FORCE)
set(DISABLE_PACKAGE_CONFIG_AND_INSTALL ON CACHE BOOL "" FORCE)

set(MBEDTLS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/mbedtls-3.6.3")
if(NOT EXISTS "${MBEDTLS_DIR}/CMakeLists.txt")
    include(FetchContent)
    FetchContent_Declare(
        mbedtls_src
        URL https://github.com/Mbed-TLS/mbedtls/archive/refs/tags/v3.6.3.tar.gz
        URL_HASH SHA256=e69c4c13377e89b9d696006ef9c8258e3be75b6dbd464cfd573360482b1a1f4e
    )
    FetchContent_GetProperties(mbedtls_src)
    if(NOT mbedtls_src_POPULATED)
        FetchContent_Populate(mbedtls_src)
    endif()
    set(MBEDTLS_DIR "${mbedtls_src_SOURCE_DIR}")
endif()

add_subdirectory("${MBEDTLS_DIR}" "${CMAKE_BINARY_DIR}/mbedtls" EXCLUDE_FROM_ALL)

add_library(sakura_proxy SHARED
    crypto.cpp
    tsproxy.cpp
    jni_bridge.cpp)
target_include_directories(sakura_proxy PRIVATE "${MBEDTLS_DIR}/include")
target_compile_options(sakura_proxy PRIVATE
    -O2
    -ffunction-sections
    -fdata-sections
    -fvisibility=hidden
    -fvisibility-inlines-hidden)
target_link_options(sakura_proxy PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
    -Wl,-z,relro
    -Wl,-z,now
    -Wl,-z,noexecstack)
target_link_libraries(sakura_proxy PRIVATE mbedtls mbedx509 mbedcrypto)
