# Jarvis architecture notes

## Current app layers

```
Compose UI
  └── MainActivity / JarvisHome
        ├── ChatHistoryStore (Android SharedPreferences, local-only)
        └── localReply (temporary deterministic responder)

Tool boundary
  └── ToolPolicy
        ├── READ_ONLY / REVERSIBLE → no extra approval in policy
        └── SENSITIVE / DESTRUCTIVE → PENDING until explicit approval
```

## Engineering decisions

- Keep the Android app native (Kotlin + Jetpack Compose); do not wrap a desktop UI.
- Keep model providers behind an interface so cloud providers can be switched without coupling UI to a vendor.
- Treat offline inference as an optional capability selected only after device benchmarks. 4 GB RAM is a tight budget; model file size is not equal to runtime memory.
- Store conversation history locally and cap it to 300 messages in this first implementation.
- The current tool policy only decides whether approval is required. It does not execute Android actions.
- Voice and wake-word support must use visible, permission-compliant Android microphone operation. A persistent 24/7 wake listener is not promised until validated on the actual tablet.

## Next implementation sequence

1. Add repository and unit tests for local memory.
2. Add provider abstraction and safe API-key storage.
3. Add push-to-talk speech recognition and text-to-speech.
4. Benchmark candidate quantized models on the target device.
5. Add a narrowly scoped Android tool registry and approval UI.
6. Add agent planner/executor/verifier with bounded steps and audit log.
7. Build APK, run automated tests, then test on the SM-X110.

## Status vocabulary

- **Implemented in source:** code has been committed.
- **Build verified:** Gradle build has completed successfully.
- **Device verified:** tested on the Samsung SM-X110.
- **Not started / blocked:** no implementation or test evidence yet.

Do not report a feature as working based only on a design, README, or successful GitHub commit.
