# Jarvis — Android personal AI assistant

**Repository:** PROJECTCAT  
**Target device:** Samsung Galaxy Tab A9 (SM-X110), Android 16, 4 GB RAM, approximately 22–32 GB free storage.

Jarvis is intended to be a local-first Android assistant with an online/offline intelligence layer, voice interaction, persistent memory, permission-controlled Android tools, and verifiable agent workflows.

## Honest status
The Android foundation, online-provider implementation, push-to-talk voice layer, SQLite-backed chat history, explicit local memory facts, and first LiteRT-LM offline-model integration are committed. Jarvis is **not** a completed application and is **not** evidence of AGI. GitHub Actions passed unit tests, built the debug APK, and uploaded the artifact for commit 539688ae in [run 41022435853](https://github.com/dadimanohar/PROJECTCAT/actions/runs/41022435853). Physical-device installation, model download, and inference testing remain outstanding.

### Implemented in this foundation
- Kotlin + Jetpack Compose Android app structure.
- Basic chat-style screen.
- Local conversation history using SQLite (bounded to the latest 300 valid messages).
- One-time migration of existing SharedPreferences chat history into SQLite.
- User-confirmed clear-history action that deletes locally stored messages.
- Explicit offline memory facts with `/remember`, keyword `/recall`, and exact `/forget` commands.
- Memory fact validation (1–500 characters), whitespace normalization, and duplicate replacement.
- LiteRT-LM 0.18.1 runtime integration for an optional local Qwen3-0.6B model.
- Wi-Fi-only in-app model download, size checks, SHA-256 verification, and CPU-only inference runner.
- Explicit offline-mode selection with no silent online fallback.
- 2.8 GB available-RAM safety gate before model loading.
- Small deterministic local command router.
- Explicit response when a requested operation is not implemented; no fake device actions.
- Initial tool-risk policy that blocks sensitive/destructive tool requests pending explicit approval.
- Unit tests for chat-history retention, memory-fact validation, provider endpoint validation, and tool approval policy (executed by GitHub Actions; latest changes are undergoing a fresh run).

### Not yet implemented
- Actual model download, inference, speed, thermal, battery, and stability validation on the Tab A9 (integration code is present, but device behavior is unverified).
- Semantic/vector memory retrieval (current explicit memory recall uses keyword matching, not embeddings or semantic similarity).
- Always-on wake phrase and background microphone service (not implemented; push-to-talk only).
- Android app/device automation and permission/approval flows.
- Agent planning, tool execution, verification, and recovery.
- Physical installation and validation on the Tab A9; CI APK packaging does not prove device compatibility.

## Voice interaction
The current voice feature is push-to-talk. Android asks for microphone permission when the user taps Speak; recognised text is placed in the composer and must be reviewed and sent by the user. Jarvis replies can be spoken using Android TextToSpeech. This does not provide a wake word or background listening, and speech service availability/language behavior must be checked on the physical tablet.

## Local memory commands
- `/remember <fact>` stores an explicit fact locally (maximum 500 characters).
- `/recall <keyword>` searches saved facts by keyword.
- `/forget <exact fact>` deletes one exact saved fact.
- `/remember`, `/recall`, and `/forget` are handled on-device and do not call the online model. Retrieval is keyword-based, not semantic.

## Offline model setup
Jarvis can download the standard Qwen3-0.6B LiteRT-LM artifact (614 MB) from Hugging Face after the user taps Download model. The download is Wi-Fi-only, remains outside the APK, and is checked against a pinned SHA-256 before loading. Inference initially uses CPU. The app blocks model loading unless at least 2.8 GB RAM is currently available; this is a conservative safety gate, not proof that the model will run on the Tab A9. The model has not yet been downloaded or benchmarked on the physical tablet. See [offline model evaluation](docs/OFFLINE_MODEL_EVALUATION.md).

## Online provider setup
In the app, enter an HTTPS OpenAI-compatible API base URL, model name, and optional API key, then save the provider. The key is encrypted with an Android Keystore AES-GCM key and is not stored in source control. The first provider implementation uses the standard `/chat/completions` endpoint and non-streaming responses. Provider compatibility still needs live testing.

## Build prerequisites
Android Studio, JDK 17, Android SDK Platform 35, Kotlin 2.4.21, and Gradle sync. Open this repository as a project and run the `app` configuration on an Android device or emulator. CI is being used to verify compilation and packaging; wait for a successful workflow before treating the APK as build-verified.

## Safety requirements
- Obtain Android runtime permissions and show an ongoing notification for microphone foreground services.
- Require confirmation for destructive or externally visible actions.
- Never put API keys in source code or commit them to Git.
- Do not execute arbitrary model-generated shell commands.
- Keep an inspectable local action history and user-controlled data deletion.
- Treat model output as untrusted; verify tool results independently.

## Roadmap
1. Add semantic memory retrieval and richer user-facing memory management on top of explicit local facts.
2. Live-test the OpenAI-compatible provider using a configured test account.
3. Evaluate small quantized offline models against real RAM/latency limits.
4. Test speech recognition and text-to-speech on the Tab A9; then investigate wake-word feasibility.
5. Add narrowly scoped Android tools with permission gates.
6. Add planner, executor, verifier, retries, and audit trail.
7. Run integration, offline, permission, battery, and physical-device tests before calling the app complete.
