# Optional Vulkan inference

Default Android builds remain CPU-only. To request an experimental Vulkan build, run `gradle :app:assembleDebug -PenableVulkan=true` with the Vulkan shader compiler and SDK dependencies required by upstream ggml installed. The native loader requests up to 16 GPU layers when GPU offload is supported and retries CPU loading if GPU model loading returns null. This does not recover from native crashes or GPU context allocation failures. GPU performance and device compatibility require testing.
