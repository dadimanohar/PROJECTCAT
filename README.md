# Jarvis — Android personal AI assistant

**Repository:** PROJECTCAT  
**Target device:** Samsung Galaxy Tab A9 (SM-X110), Android 16, 4 GB RAM, approximately 22–32 GB free storage.

Jarvis is intended to be a local-first Android assistant with an online/offline intelligence layer, voice interaction, persistent memory, permission-controlled Android tools, and verifiable agent workflows.

## Honest status
The Android foundation, online-provider implementation, push-to-talk voice layer, SQLite-backed chat history, and explicit local memory facts are committed. Jarvis is **not** a completed application and is **not** evidence of AGI. The previous build passed for commit f52457a1; the current database and memory changes are awaiting a fresh CI run. Physical-device testing and live provider testing remain outstanding.

### Implemented in this foundation
- Kotlin + Jetpack Compose Android app structure.
- Basic chat-style screen.
- Local conversation history using SQLite (bounded to the latest 300 valid messages).
- One-time migration of existing SharedPreferences chat history into SQLite.
- User-confirmed clear-history action that deletes locally stored messages.
- Explicit offline memory facts with `/remember`, keyword `/recall`, and exact `/forget` commands.
- Memory fact validation (1–500 characters), whitespace normalization, and duplicate replacement.
- Small deterministic local command router.
- Explicit response when a requested operation is not implemented; no fake device actions.
- Initial tool-risk policy that blocks sensitive/destructive tool requests pending explicit approval.
- Unit tests for the tool approval policy (committed; not yet executed).

### Not yet implemented
- OpenAI-compatible HTTPS chat provider client (non-streaming Chat Completions format).
- Android Keystore AES-GCM encrypted API-key storage and provider settings UI (implementation committed; device behavior not yet verified).
- Permission-gated push-to-talk speech recognition that fills the composer for user review.
- Optional text-to-speech for Jarvis replies, with a user-controlled toggle.
- Android 11+ package visibility queries for speech recognition and TTS services.
- Local/offline model runtime and performance validation on the Tab A9.
- Semantic/vector memory retrieval (not implemented; current explicit memory recall uses keyword matching, not embeddings or semantic similarity).
- Always-on wake phrase and background microphone service (not implemented; push-to-talk only).
- Android app/device automation and permission/approval flows.
- Agent planning, tool execution, verification, and recovery.
- GitHub Actions workflow runs unit tests and builds/uploads a debug APK. Previous successful run: [GitHub Actions run 41021188956](https://github.com/dadimanohar/PROJECTCAT/actions/runs/41021188956). The latest SQLite changes require a new run.
- APK installation and physical-device validation.

## Voice interaction
The current voice feature is push-to-talk. Android asks for microphone permission when the user taps Speak; recognised text is placed in the composer and must be reviewed and sent by the user. Jarvis replies can be spoken using Android TextToSpeech. This does not provide a wake word or background listening, and speech service availability/language behavior must be checked on the physical tablet.

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
1. Add semantic memory retrieval and user-facing memory management on top of explicit local facts.
2. Live-test the OpenAI-compatible provider using a configured test account.
3. Evaluate small quantized offline models against real RAM/latency limits.
4. Test speech recognition and text-to-speech on the Tab A9; then investigate wake-word feasibility.
5. Add narrowly scoped Android tools with permission gates.
6. Add planner, executor, verifier, retries, and audit trail.
7. Run integration, offline, permission, battery, and physical-device tests before calling the app complete.
