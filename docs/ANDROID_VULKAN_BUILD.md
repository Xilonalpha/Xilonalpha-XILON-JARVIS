# Android Vulkan + VLM build

The Android NDK supplies the C Vulkan headers but may not include the Vulkan-Hpp C++ header `vulkan/vulkan.hpp`. ggml-vulkan uses that C++ header, so checking only `vulkan.h` is insufficient.

The GitHub Actions Vulkan job installs Khronos Vulkan headers, copies the entire matching `vulkan/` include directory into a temporary staging directory, and exports `XILON_VULKAN_HEADERS_INCLUDE`. Gradle forwards it to CMake, which explicitly adds the include directory to the `ggml-vulkan` target. This avoids depending on the host's implicit include search paths during Android cross-compilation.

The job also forwards the SPIRV-Headers CMake package path and limits the Vulkan build to arm64-v8a. The ordinary CPU and VLM artifacts are built separately.

This is a source-level fix for the missing header, not a confirmed passing build or proof of GPU inference on a physical phone. Check the latest workflow logs, native linking, APK signatures, and a real Android Vulkan device before declaring the GPU backend production-ready.
