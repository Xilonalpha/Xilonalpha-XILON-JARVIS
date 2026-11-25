# Android Vulkan + VLM build
The Vulkan job cross-compiles native llama.cpp for Android arm64-v8a. Host SPIRV-Headers CMake package discovery is not automatic when CMake is driven by Android Gradle Plugin; the workflow exports XILON_SPIRV_HEADERS_DIR and app/build.gradle.kts forwards it as -DSPIRV-Headers_DIR.
The ordinary CPU and VLM builds retain their existing ABI filters. The Vulkan job sets android.injected.build.abi=arm64-v8a to avoid attempting the Vulkan cross-build on x86_64.
This fixes package discovery only. It does not prove that shader compilation, linkage, runtime Vulkan support or device inference succeeds. Confirm the Vulkan workflow, inspect its APK artifact and test on a compatible physical device before describing GPU acceleration as working.
