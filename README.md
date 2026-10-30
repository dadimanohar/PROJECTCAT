# Jarvis — Android personal AI assistant

**Repository:** PROJECTCAT  
**Target device:** Samsung Galaxy Tab A9 (SM-X110), Android 16, 4 GB RAM, approximately 22–32 GB free storage.

Jarvis is intended to be a local-first Android assistant with an online/offline intelligence layer, voice interaction, persistent memory, permission-controlled Android tools, and verifiable agent workflows.

## Honest status
This commit establishes the native Android project foundation only. It is **not** a completed Jarvis application and is **not** evidence of AGI. No APK build or physical-device test has yet been run.

### Implemented in this foundation
- Kotlin + Jetpack Compose Android app structure.
- Basic chat-style screen.
- Small deterministic local command router.
- Explicit response when a requested operation is not implemented; no fake device actions.

### Not yet implemented
- LLM provider integration and secure credential storage.
- Local/offline model runtime and performance validation on the Tab A9.
- Persistent conversation and long-term memory.
- Speech recognition, text-to-speech, wake phrase, and background microphone service.
- Android app/device automation and permission/approval flows.
- Agent planning, tool execution, verification, and recovery.
- Automated test suite, APK packaging, installation, and physical-device validation.

## Build prerequisites
Android Studio, JDK 17, Android SDK Platform 35, and Gradle sync. Open this repository as a project and run the `app` configuration on an Android device or emulator. Build compatibility is not yet verified.

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
3. Add provider abstraction for online models.
4. Evaluate small quantized offline models against real RAM/latency limits.
5. Add push-to-talk, speech output, then investigate wake-word support.
6. Add narrowly scoped Android tools with permission gates.
7. Add planner, executor, verifier, retries, and audit trail.
8. Run unit, integration, offline, permission, battery, and device tests; publish APK and test report only after they pass.
