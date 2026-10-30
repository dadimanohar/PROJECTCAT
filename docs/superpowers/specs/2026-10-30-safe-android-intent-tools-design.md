# Safe Android Intent Tools — Design

## Intent and why
Extend Jarvis with a small set of useful Android handoffs for the Samsung tablet while keeping actions explicit, reviewable, and constrained to Android system intents. This builds on the existing Kotlin/Jetpack Compose app and the existing user-entered local-command boundary.

## Goals
- Launch an installed app by an explicitly supplied package name, only after a confirmation dialog.
- Open Android's share sheet with user-supplied text, after the user explicitly invokes the command.
- Open Android Clock's timer setup UI for a requested reminder, so the user can review and save it.
- Open Android's Storage Access Framework document picker with a validated MIME type; the user chooses a document and Jarvis does not read it as part of this feature.
- Validate all arguments before creating an Intent.
- Record only action category, outcome, and timestamp in the local action audit, never package names, message text, file URIs, or reminder titles.

## Non-goals and anti-goals
- No arbitrary shell, AccessibilityService click automation, hidden launches, background actions, SMS, call placement, payments, settings changes, installation, or file deletion.
- No background file indexing or reading selected file content.
- No silently setting alarms: reminder commands hand off to the Clock UI and the user must review and save.
- No AI/model-generated raw command execution. Only explicit user commands can start these actions.

## Constraints
- Existing project: Kotlin, Jetpack Compose, Android SDK compile/target 35, min SDK 26.
- Existing app supports HTTPS browser/maps handoffs and ACTION_DIAL only.
- Target tablet has 4 GB RAM; avoid heavy dependencies and background services.
- Executor catches ActivityNotFoundException and other OS failures and reports them honestly.
- User approval is required before app launch. Share and picker are explicit user-entered commands. Reminder hands off to Clock UI and does not silently save an alarm.
- File picker uses ACTION_OPEN_DOCUMENT and must not request broad storage permissions.

## Approaches considered
1. Direct intents from MainActivity: quick, but adds security-sensitive branching to an already large Composable.
2. Typed policy plus dedicated executor and confirmation UI (selected): separates parsing, risk policy, Intent execution, and UI confirmation. A focused executor handles only the four command types.
3. General Android tool registry or agent-driven intents: flexible, but too broad for this stage and risks turning model output into device-control instructions. Rejected until the narrow tools prove reliable on-device.

## Implementation decisions
- Add AndroidIntentCommandPolicy as a pure Kotlin parser for four explicit commands: /open-app <package>, /share <text>, /remind <title> in <minutes|hours>, /pick-file [mime].
- Add an /open-app approval dialog showing the package name; launch only after a second tap. Do not inspect app data.
- /share opens Intent.ACTION_SEND with text/plain and Android chooser.
- /remind opens AlarmClock.ACTION_SET_TIMER with EXTRA_LENGTH, EXTRA_MESSAGE, and EXTRA_SKIP_UI=false. Do not use notification or alarm permissions.
- /pick-file opens Intent.ACTION_OPEN_DOCUMENT with CATEGORY_OPENABLE and validated MIME; do not read the returned URI in this feature.
- Extend audit policy with categories open_app, share_text, set_timer, and pick_file; retain at most 100 events and record no arguments.
- App-launch dismissal records cancelled. Execution happens only in an approved callback.
- Help text states the limits plainly.

## Verification
- Test valid and invalid package names, text length/control characters, reminder range/overflow, MIME validation, and command lookalikes.
- Test audit allowlist includes the four categories and rejects unknown labels.
- Test intent construction where Android framework dependencies can be isolated. Ensure SET_TIMER keeps UI visible and the other intents do not include call placement, notification, or file-read actions.
- Run gradle testDebugUnitTest and gradle assembleDebug in GitHub Actions.
- Physical testing on SM-X110 remains required for app resolution, Clock UI, share chooser, and document-provider behavior; CI does not establish these.