# Jarvis architecture notes

## Current app layers

```
Jetpack Compose UI (MainActivity)
  ├── PromptSendPolicy
  │     └── Local commands remain available while offline model is not ready
  ├── ChatCompletionProvider
  │     └── HTTPS OpenAI-compatible /chat/completions client
  ├── OfflineModelRunner
  │     └── LiteRT-LM CPU engine + Qwen3-0.6B model file
  ├── ChatHistoryStore
  │     └── SQLite chat history + explicit memory facts + bounded action audit events
  ├── ProviderCredentialStore
  │     └── Android Keystore AES-GCM encrypted API key
  ├── SpeechRecognizer + TextToSpeech
  │     └── Push-to-talk input + optional spoken replies
  ├── LocalCommandPolicy
  │     ├── /open, /search, /maps, /dial
  │     └── Explicit user command → Android action executor
  └── AgentPlanParser
        └── /agent → one allowlisted proposal → approval dialog → executor
```

## Implemented safety boundaries

- Only explicitly typed local slash commands are parsed by `LocalCommandPolicy`. Model-generated output is not passed to this parser.
- Browser navigation accepts valid HTTPS URLs only and rejects embedded URL credentials.
- Web and maps searches are URL-encoded and handed to an Android chooser; Jarvis does not scrape pages or claim it has read the results.
- Dialer handoff uses ACTION_DIAL only; it never places a phone call or requests call permission.
- The policy module does not execute shell commands or arbitrary model-generated code.
- The tool-risk policy gates the first Android action executor. Direct local commands are explicitly user-entered; AI-proposed actions require a separate approval dialog.
- The SQLite action audit log stores only action category, outcome, and timestamp (not URL, search query, or phone number), keeps at most 100 events, and can be inspected or cleared by the user.
- Offline inference has no silent cloud fallback. Local command submission remains available even if the offline model has not loaded.
- Provider endpoints must use HTTPS. API keys are encrypted with an Android Keystore AES-GCM key.
- Chat and memory facts remain local in SQLite. Current memory recall is keyword matching, not semantic/vector retrieval.
- Microphone access is push-to-talk only; no background wake phrase service exists.

## Known limitations

- The offline model code is build-verified but has not been downloaded, loaded, or benchmarked on the actual SM-X110.
- A 2.8 GB available-RAM threshold is a conservative gate, not a guarantee that the 0.6B model will fit or run smoothly.
- The current online agent planner proposes only one browser, maps, or dialer handoff. It has no multi-step planning, page reading, form filling, autonomous background execution, tool-result verification, retry, or recovery loop.
- A successful CI build does not validate speech services, app intents, network provider compatibility, offline inference, or performance on the physical tablet.

## Next implementation sequence

1. Expand the typed Android tool registry with more narrowly scoped actions while preserving cancellation, approval, and privacy-minimized audit records.
2. Add approval screens for sensitive or destructive operations before registering those tools.
3. Add a bounded planner/executor/verifier loop that can call only registered tools and must verify tool results.
4. Add user-managed memory export and improve retrieval only after tests and device-performance evaluation. Current memory inspection and deletion commands are implemented.
5. Test push-to-talk, TTS, provider calls, browser intents, and local inference on the SM-X110.
6. Evaluate whether a visible foreground wake-word service is practical and appropriate on this tablet.
7. Run automated, integration, offline, permission, and physical-device tests before claiming completion.

## Status vocabulary

- **Implemented in source:** code has been committed.
- **Build verified:** Gradle build and tests completed successfully in a named CI run.
- **Device verified:** manually or instrumentally tested on the Samsung SM-X110.
- **Not started / blocked:** no implementation or test evidence exists.

Do not report a feature as working based only on a design, README, or successful GitHub commit.
