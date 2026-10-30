# Jarvis architecture notes

## Current app layers

```
Jetpack Compose UI (MainActivity)
  ├── ChatCompletionProvider
  │     └── HTTPS OpenAI-compatible /chat/completions client
  ├── OfflineModelRunner
  │     └── LiteRT-LM CPU engine + Qwen3-0.6B model file
  ├── ChatHistoryStore
  │     └── SQLite chat history + explicit local memory facts
  ├── ProviderCredentialStore
  │     └── Android Keystore AES-GCM encrypted API key
  ├── SpeechRecognizer + TextToSpeech
  │     └── Push-to-talk input + optional spoken replies
  └── LocalCommandPolicy
        ├── /help
        ├── /open https://...
        └── /search ...
              └── Explicit user command → Android ACTION_VIEW chooser
```

## Implemented safety boundaries

- Only explicitly typed local slash commands are parsed by `LocalCommandPolicy`. Model-generated output is not passed to this parser.
- Browser navigation accepts valid HTTPS URLs only and rejects embedded URL credentials.
- Web and maps searches are URL-encoded and handed to an Android chooser; Jarvis does not scrape pages or claim it has read the results.
- Dialer handoff uses ACTION_DIAL only; it never places a phone call or requests call permission.
- The policy module does not execute shell commands or arbitrary model-generated code.
- The current tool-risk policy is still only a policy checker; broader Android actions are not implemented.
- Offline inference has no silent cloud fallback.
- Provider endpoints must use HTTPS. API keys are encrypted with an Android Keystore AES-GCM key.
- Chat and memory facts remain local in SQLite. Current memory recall is keyword matching, not semantic/vector retrieval.
- Microphone access is push-to-talk only; no background wake phrase service exists.

## Known limitations

- The offline model code is build-verified but has not been downloaded, loaded, or benchmarked on the actual SM-X110.
- A 2.8 GB available-RAM threshold is a conservative gate, not a guarantee that the 0.6B model will fit or run smoothly.
- Browser handoff is a small first tool. There is no general Android device automation, autonomous planner, tool executor, verifier, or recovery loop yet.
- A successful CI build does not validate speech services, app intents, network provider compatibility, offline inference, or performance on the physical tablet.

## Next implementation sequence

1. Build a typed Android tool registry with explicit inputs, execution results, cancellation, and audit records.
2. Add approval screens for sensitive or destructive operations before registering those tools.
3. Add a bounded planner/executor/verifier loop that can call only registered tools and must verify tool results.
4. Add user-managed memory inspection/export/deletion and improve retrieval only after tests and device-performance evaluation.
5. Test push-to-talk, TTS, provider calls, browser intents, and local inference on the SM-X110.
6. Evaluate whether a visible foreground wake-word service is practical and appropriate on this tablet.
7. Run automated, integration, offline, permission, and physical-device tests before claiming completion.

## Status vocabulary

- **Implemented in source:** code has been committed.
- **Build verified:** Gradle build and tests completed successfully in a named CI run.
- **Device verified:** manually or instrumentally tested on the Samsung SM-X110.
- **Not started / blocked:** no implementation or test evidence exists.

Do not report a feature as working based only on a design, README, or successful GitHub commit.
