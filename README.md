# Jarvis — Android personal AI assistant

**Repository:** PROJECTCAT  
**Target device:** Samsung Galaxy Tab A9 (SM-X110), Android 16, 4 GB RAM, approximately 22–32 GB free storage.

Jarvis is intended to be a local-first Android assistant with an online/offline intelligence layer, voice interaction, persistent memory, permission-controlled Android tools, and verifiable agent workflows.

## Honest status
The Android foundation and first online-provider implementation are committed. Jarvis is **not** a completed application and is **not** evidence of AGI. The first CI run exposed a Java/Kotlin JVM target mismatch; the targets have now been aligned to Java 17 and a fresh CI run is pending. No successful APK build or physical-device test is confirmed yet.

### Implemented in this foundation
- Kotlin + Jetpack Compose Android app structure.
- Basic chat-style screen.
- Local conversation history using Android SharedPreferences (bounded to the latest 300 messages).
- Small deterministic local command router.
- Explicit response when a requested operation is not implemented; no fake device actions.
- Initial tool-risk policy that blocks sensitive/destructive tool requests pending explicit approval.
- Unit tests for the tool approval policy (committed; not yet executed).

### Not yet implemented
- OpenAI-compatible HTTPS chat provider client (non-streaming Chat Completions format).
- Android Keystore AES-GCM encrypted API-key storage and provider settings UI (implementation committed; device behavior not yet verified).
- Local/offline model runtime and performance validation on the Tab A9.
- Semantic long-term memory, retrieval, and user-facing memory management (basic chat history is implemented; richer memory remains pending).
- Speech recognition, text-to-speech, wake phrase, and background microphone service.
- Android app/device automation and permission/approval flows.
- Agent planning, tool execution, verification, and recovery.
- GitHub Actions workflow committed to run unit tests and build/upload a debug APK. Initial run failed at Kotlin compilation because Java and Kotlin JVM targets differed; both are now configured for Java 17 and the rerun is pending.
- APK installation and physical-device validation.

## Online provider setup
In the app, enter an HTTPS OpenAI-compatible API base URL, model name, and optional API key, then save the provider. The key is encrypted with an Android Keystore AES-GCM key and is not stored in source control. The first provider implementation uses the standard `/chat/completions` endpoint and non-streaming responses. Provider compatibility still needs live testing.

## Build prerequisites
Android Studio, JDK 17, Android SDK Platform 35, and Gradle sync. Open this repository as a project and run the `app` configuration on an Android device or emulator. CI is being used to verify compilation and packaging; wait for a successful workflow before treating the APK as build-verified.

## Safety requirements
- Obtain Android runtime permissions and show an ongoing notification for microphone foreground services.
- Require confirmation for destructive or externally visible actions.
- Never put API keys in source code or commit them to Git.
- Do not execute arbitrary model-generated shell commands.
- Keep an inspectable local action history and user-controlled data deletion.
- Treat model output as untrusted; verify tool results independently.

## Roadmap
1. Build and test the Android shell.
2. Add local database-backed chat and memory.
3. Extend and validate the OpenAI-compatible provider integration.
4. Evaluate small quantized offline models against real RAM/latency limits.
5. Add push-to-talk, speech output, then investigate wake-word support.
6. Add narrowly scoped Android tools with permission gates.
7. Add planner, executor, verifier, retries, and audit trail.
8. Run unit, integration, offline, permission, battery, and device tests; publish APK and test report only after they pass.
