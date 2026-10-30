# Offline model evaluation — Jarvis

## Candidate selected

- Model: `litert-community/Qwen3-0.6B`
- Artifact: `Qwen3-0.6B.litertlm`
- Download size: 614 MB (Hugging Face file page; model card rounds this to 586 MB).
- License shown by the model repository: Apache-2.0.
- Runtime: Google LiteRT-LM Android 0.18.1.
- Initial backend: CPU only. GPU is deliberately not enabled until the Tab A9's OpenCL support and memory behavior are tested.
- Download location: app-specific external downloads directory; the model is not bundled in the APK.
- Download policy: explicit user tap, Wi-Fi only, at least 1 GB free storage, SHA-256 verification before loading.
- Expected SHA-256: `c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108`.

Source artifact:
https://huggingface.co/litert-community/Qwen3-0.6B/blob/main/Qwen3-0.6B.litertlm

## Device-memory risk

The target device is a Samsung Galaxy Tab A9 SM-X110 with 4 GB RAM. Google AI Edge's published Qwen3-0.6B reference measurements on other retail devices show peak private memory around 2.7 GB on CPU for some phones, and higher on some GPU configurations. Those are not measurements from the Tab A9 and cannot predict its exact runtime use.

For that reason:
- Jarvis uses CPU backend only initially.
- Loading is gated on at least 2.8 GB of currently available RAM.
- The app displays available RAM and does not silently switch to an online model if offline inference fails.
- A device-level benchmark is still required. If the safety gate never passes on the Tab A9, this model is not a viable local runtime for that device; do not bypass the gate just to force a load.

## Current implementation

- Pinned LiteRT-LM Android runtime 0.18.1.
- Upgraded Kotlin and Compose compiler plugins to 2.4.21 to match the runtime's Kotlin metadata.
- In-app Wi-Fi-only model download using Android DownloadManager.
- Size-range and SHA-256 checks before model loading.
- Single-threaded background engine/conversation ownership.
- Explicit offline-mode switch; no hidden online fallback.
- User can unload or delete the model to release RAM/storage.

## Not yet verified

- Successful download on the actual Tab A9.
- SHA verification against a real device download.
- Model initialization on the Tab A9.
- Inference speed, sustained thermals, battery use, memory pressure, and app stability.
- Whether a lower-memory quantized alternative is necessary.

## Verification plan on the physical tablet

1. Install the debug APK.
2. Connect to Wi-Fi and download the 614 MB model from the Offline model card.
3. Confirm the app reports SHA-256 verification success.
4. Check the available-RAM value. The Load model action remains disabled below 2.8 GB.
5. If enabled, load the model and test a short greeting, arithmetic, a Telugu/English translation, and a 100–200 word response.
6. Record load time, first-token delay, tokens per second if measurable, available RAM before/after, battery drain, temperature, and any app restart/crash.
7. Repeat after unloading and reloading to check cache behavior.

Do not claim offline AI works on this tablet until these steps pass.
