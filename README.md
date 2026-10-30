# Jarvis — Android personal AI assistant

**Repository:** PROJECTCAT  
**Target device:** Samsung Galaxy Tab A9 (SM-X110), Android 16, 4 GB RAM, approximately 22–32 GB free storage.

Jarvis is intended to be a local-first Android assistant with an online/offline intelligence layer, voice interaction, persistent memory, permission-controlled Android tools, and verifiable agent workflows.

## Honest status
The Android foundation, online-provider implementation, push-to-talk voice layer, SQLite-backed chat history, explicit local memory facts, LiteRT-LM offline-model integration, and first explicit browser, maps, and dialer tools are committed. Jarvis is **not** a completed application and is **not** evidence of AGI. Commit `7bf77bdcf14c8d220a8ba8ea3ba01d37b7966134` passed GitHub Actions run [41039734720](https://github.com/dadimanohar/PROJECTCAT/actions/runs/41039734720): unit tests passed, the debug APK built, and the `jarvis-debug-apk` artifact was uploaded. Physical-device installation, model download, and inference testing remain outstanding.

### Implemented in this foundation
- Kotlin + Jetpack Compose Android app structure.
- Basic chat-style screen.
- Local conversation history using SQLite (bounded to the latest 300 valid messages).
- One-time migration of existing SharedPreferences chat history into SQLite.
- User-confirmed clear-history action that deletes locally stored messages.
- Explicit offline memory facts with `/remember`, keyword `/recall`, exact `/forget`, `/memories` inspection, and two-step `/forget-all` deletion.
- Memory fact validation (1–500 characters), whitespace normalization, and duplicate replacement.
- LiteRT-LM 0.18.1 runtime integration for an optional local Qwen3-0.6B model.
- Wi-Fi-only in-app model download, size checks, SHA-256 verification, and CPU-only inference runner.
- Explicit offline-mode selection with no silent online fallback.
- 2.8 GB available-RAM safety gate before model loading.
- Explicit local command router: `/help`, `/open https://…`, `/search …`, `/maps …`, and `/dial …`.
- Bounded online agent proposal: `/agent <task>` requests at most one allowlisted browser/maps/dialer action and waits for an explicit approval dialog. It does not run arbitrary code or chain actions.
- Browser and maps handoff use Android `ACTION_VIEW` chooser; URLs must be valid HTTPS and cannot contain embedded credentials. `/dial` uses `ACTION_DIAL` (not `ACTION_CALL`) and never places the call.
- Direct local actions are initiated by commands typed by the user. In `/agent` mode, raw model text is never executed: the proposal is parsed against the allowlist and a separate approval dialog is required.
- Explicit response when a requested operation is not implemented; no fake device actions.
- Tool-risk policy and a typed Android action executor for the supported browser, maps, and dialer handoffs; sensitive dialer handoffs require the explicit command/approval path.
- Unit tests for chat-history retention, memory-fact validation, provider endpoint validation, tool approval policy, local command validation, memory command validation, and bounded agent-plan validation (all passed in GitHub Actions run [41039734720](https://github.com/dadimanohar/PROJECTCAT/actions/runs/41039734720)).

### Not yet implemented
- Actual model download, inference, speed, thermal, battery, and stability validation on the Tab A9 (integration code is present, but device behavior is unverified).
- Semantic/vector memory retrieval (current explicit memory recall uses keyword matching, not embeddings or semantic similarity).
- Always-on wake phrase and background microphone service (not implemented; push-to-talk only).
- Broader Android app/device automation and permission/approval flows (the first browser handoff commands are implemented, but this is not general device control).
- Multi-step agent planning, tool verification, retries, recovery, and general Android device automation. The current `/agent` path proposes one browser/maps/dialer handoff only and waits for explicit approval.
- Physical installation and validation on the Tab A9; CI APK packaging does not prove device compatibility.

## Voice interaction
The current voice feature is push-to-talk. Android asks for microphone permission when the user taps Speak; recognised text is placed in the composer and must be reviewed and sent by the user. Jarvis replies can be spoken using Android TextToSpeech. This does not provide a wake word or background listening, and speech service availability/language behavior must be checked on the physical tablet.

## Bounded agent action proposal
- `/agent <task>` asks the configured online provider to propose one action using a strict three-line tool format.
- The parser accepts only HTTPS URL open, web search, map search, or a dialer handoff. Unknown tool names, malformed output, unsafe URLs, and invalid numbers are rejected.
- Jarvis shows the goal, proposed action, and reason in an approval dialog. Nothing happens unless the user chooses **Approve and open**.
- The offline model does not make or execute action plans. The current planner supports one action, not multi-step autonomy, web-page reading, form filling, or background work.

## Explicit local commands
- `/help` lists the available local commands.
- `/open https://example.com` opens the Android app chooser for a valid HTTPS URL. HTTP, non-web schemes, and URLs containing embedded credentials are rejected.
- `/search <query>` opens an encoded Google search URL through the Android app chooser.
- `/maps <place>` opens an encoded Google Maps search through the Android app chooser.
- `/dial <number>` opens the dialer with a validated number prefilled; the user must review and tap Call.
- These commands run only when typed by the user. Jarvis does not execute command-like text generated by the model.

## Local memory commands
- `/remember <fact>` stores an explicit fact locally (maximum 500 characters).
- `/recall <keyword>` searches saved facts by keyword.
- `/forget <exact fact>` deletes one exact saved fact.
- `/memories` lists up to the 50 most recently saved facts.
- `/forget-all` asks for a separate `/forget-all confirm` command before deleting every saved memory fact.
- All memory commands are handled on-device and do not call the online model. Retrieval is keyword-based, not semantic.

## Action audit history
- `/action-log` lists the latest 30 local Android handoffs.
- `/clear-action-log` asks for a separate `/clear-action-log confirm` command before clearing the log.
- The database retains at most 100 events and stores only an action category (`open_url`, `web_search`, `maps_search`, or `dial_number`), a handoff/failure/cancellation outcome, and a timestamp. It does not store URLs, search text, or phone numbers.

## Offline model setup
Jarvis can download the standard Qwen3-0.6B LiteRT-LM artifact (614 MB) from Hugging Face after the user taps Download model. The download is Wi-Fi-only, remains outside the APK, and is checked against a pinned SHA-256 before loading. Inference initially uses CPU. The app blocks model loading unless at least 2.8 GB RAM is currently available; this is a conservative safety gate, not proof that the model will run on the Tab A9. The model has not yet been downloaded or benchmarked on the physical tablet. See [offline model evaluation](docs/OFFLINE_MODEL_EVALUATION.md).

## Online provider setup
In the app, enter an HTTPS OpenAI-compatible API base URL, model name, and optional API key, then save the provider. The key is encrypted with an Android Keystore AES-GCM key and is not stored in source control. The first provider implementation uses the standard `/chat/completions` endpoint and non-streaming responses. Provider compatibility still needs live testing.

## Build prerequisites
Android Studio, JDK 17, Android SDK Platform 35, Kotlin 2.4.21, and Gradle sync. Open this repository as a project and run the `app` configuration on an Android device or emulator. The current source commit was build-verified by GitHub Actions run [41039734720](https://github.com/dadimanohar/PROJECTCAT/actions/runs/41039734720). Its debug artifact is a debug build, not a Play Store release.

## Safety requirements
- Obtain Android runtime permissions and show an ongoing notification for microphone foreground services.
- Require confirmation for destructive or externally visible actions.
- Never put API keys in source code or commit them to Git.
- Do not execute arbitrary model-generated shell commands.
- Keep an inspectable local action history with data minimization and user-controlled deletion.
- Treat model output as untrusted; verify tool results independently.

## Roadmap
1. Add semantic memory retrieval and richer user-facing memory management on top of explicit local facts.
2. Live-test the OpenAI-compatible provider using a configured test account.
3. Evaluate small quantized offline models against real RAM/latency limits.
4. Test speech recognition and text-to-speech on the Tab A9; then investigate wake-word feasibility.
5. Expand the narrowly scoped Android tool registry and test the persistent action audit trail on-device.
6. Extend the one-action online proposal flow into bounded multi-step plans with per-step approval, verification, retries, and recovery.
7. Run integration, offline, permission, battery, and physical-device tests before calling the app complete.
