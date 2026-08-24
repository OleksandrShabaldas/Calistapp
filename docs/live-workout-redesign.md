# Live Workout Screen — Redesign Implementation Plan

Status: **BUILT (2026-08-24)** — `:app:assembleDebug` and `:wear:compileDebugKotlin` are green.
Scope: the ongoing (live) workout screen and every surface it opens. This is the first screen of a
broader visual refresh; the rest of the app follows later.

**Deferred from this pass** (everything else in the plan is implemented):
- **Global numpad adoption** — `NumberPadSheet` is built and drives every figure on the live screen
  (reps, weight, effort, journal), but the planner and other screens still use the existing
  system-keyboard steppers. Wiring the shared numpad into `EditableNumber` app-wide is a follow-up
  (it's a global behaviour change, kept out of this pass to avoid regressing other screens).
- **Hands-free (TTS)** — the pause toggle exists and persists, but the spoken-cue engine isn't wired
  yet, so the toggle is currently inert.

---

## 0. Direction (locked)

- **Style = the reference app** (black background, single orange accent, bold sans throughout).
  The reference is a **style guide, not a template to clone** — everything is adapted to Calistapp.
- **Drop** the Playfair serif and the emerald/glass "cyber" look *on this screen*. (App-wide
  migration of type/palette is a later pass; tokens are added now so it can propagate.)
- **Model:** immersive full-screen video player + an **always-on HR/kcal HUD** (Calistapp's
  identity — the reference shows neither, we must). **Self-paced**: you tap to bank a set; rest is
  a count-up stopwatch, never a forced countdown (keeps the calorie engine's time-crediting valid).
- **Keep** everything discussed: the on-screen rep counter, the tap-to-expand HUD, the multi-angle
  video swipe, the split swipe-up panel, the journal, effort logging, the shared numpad, the pause
  screen. Nothing is stripped.

The one change to the reference's own design: its **journal rows are too spacious** → we ship a
**compact** journal (slim one-line exercise rows; a dense per-set grid when expanded).

---

## 1. What already exists (reuse) vs. what's new

| Piece | Status | File |
|---|---|---|
| Media3 / ExoPlayer dependency | ✅ present | `gradle/libs.versions.toml` (`media3 = 1.5.1`) |
| Looping, cached, auth'd video player | ✅ reuse/adapt | `ui/exercises/ExerciseVideoPlayer.kt` |
| Swipeable media carousel + page dots | ✅ adapt | `ui/exercises/ExerciseMediaCarousel.kt` |
| Multi-angle video data per exercise | ✅ present | `Exercise.media` + `ExerciseVideoCatalog` |
| Per-exercise weight in the planner | ✅ **already built** | `ui/planner/WorkoutPlannerScreen.kt` (`PlannedExerciseCard`) |
| Live weight edit (syncs to watch) | ✅ present | `SessionController.setAddedWeight` |
| Set storage as JSON (no migration for new fields) | ✅ present | `data/local/SessionEntity.kt` (`setLogsJson`) |
| HR zones (5-zone, from effective max HR) | ✅ present | `core/model/HeartRate.kt` (`HrZone.forHr`) |
| Live avg/peak HR, kcal, time-in-zones (finished) | ✅ present | `core/model/SessionSummary.kt` |
| HR sparkline chart | ✅ reuse | `ui/common/HeartRateChart.kt` |
| Progress rings | ✅ reuse | `ui/common/Rings.kt` |
| Coaching text for the info panel (tips, mistakes, muscles) | ✅ present | `Exercise.tips/commonMistakes/instructions` |
| Watch link state + reconnect | ✅ present | `WatchConnectionMonitor`, `WatchLinkState` |
| Haptics + sound fx (for pause toggles) | ✅ present | `ui/common/Haptics.kt`, `SoundEffects.kt` |
| DataStore prefs pattern (for pause toggles) | ✅ present | `data/exercise/ExercisePrefsRepository.kt` |
| **Shared numeric keypad (reference-style, app-wide)** | 🆕 new | — |
| **Per-set effort (RIR/RPE/%RM) + note** | 🆕 new (model + UI) | — |
| **Journal-to-storage wiring** (per-set weight/effort/note persist) | 🆕 new | — |
| **Live HR stats for the HUD** (running avg/peak/rate + recent-HR ring) | 🆕 small addition | `LiveCalorieAccumulator` / `SessionController` |
| **Restart control** (redo current set) | 🆕 new | — |
| **Pause screen** with toggles | 🆕 new (reuses fx/haptics) | — |

**No new Gradle dependencies are required.** Video token (`GITHUB_VIDEO_TOKEN` in
`local.properties`) is needed for real-person clips to actually play; without it the hero falls
back to the animation/stills automatically.

---

## 2. Design tokens (reference skin)

Add to `ui/theme/Color.kt` (keep existing names working; add new ones):

- `Onyx = #0B0B0C` — neutral near-black page base (replaces the blue-tinted `Ink #07090D` **on this
  screen**; the blue tint is part of the "cyber" feel we're dropping).
- `Flame = #EE6C2B` — primary accent (work, progress, primary buttons, kcal).
- `FlameDeep = #C0521C`, `FlameSoft = rgba(238,108,43,0.15)` — pressed / tint fills.
- Keep `Coral #FF6B6B` for **heart rate** (red heart reads correctly).
- Keep `Amber` for rest-overrun warning; keep semantic greens for "done".
- `Chalk = #F4F4F5` (neutral near-white) + `Ash = #8A8A8E` (muted) for text on this screen
  (swap the warm `Cream/CreamMuted`).
- Card fill flatter than glass: `SurfaceFill = rgba(255,255,255,0.05)`, `SurfaceBorder =
  rgba(255,255,255,0.10)`, radius 18–22.

Typography (`ui/theme/Type.kt`): on the live screen use **bold sans** for the exercise title
(new `TitleSans` style) instead of `displaySmall` (Playfair). Numbers keep `NumericLarge/Medium`
(already sans). Serif stays elsewhere until the app-wide pass.

**Ambient wash** (`ui/common/Ambient.kt`): retune `AmbientOverride` to a *restrained* orange —
a faint warm glow while WORKING, dimmed/neutral while RESTING (keep the across-the-room state cue,
lose the neon). Optional; can be flat black if preferred.

---

## 3. Screen architecture

`ui/session/ActiveSessionScreen.kt` → `LiveControls` is rebuilt as a fixed (non-scrolling)
full-screen layout with a bottom control dock, instead of today's vertical scroll of cards.

Regions (top → bottom):

1. **Top bar** — collapse chevron · `Round n / N` (circuits) or blank · pause.
2. **Segment progress** — one dash per exercise in the current block/round; filled = done, bright = current.
3. **Video hero** (fills the upper ~60%) with overlays: elapsed clock (top-left), **HUD chip**
   (top-right), exercise name + reps/target + "Exercise info" (bottom-left), page dots + pause glyph.
4. **Control dock** (rounded top, grabber) — rep counter (− / number / +), weight pill.
5. **Primary button** — phase-dependent (see state table).
6. **Journal handle** — Journal · Pause · Finish row (or swipe the dock grabber).

### State → UI mapping (drives from `LiveSession`)

| State | Detected by | Hero overlay | Dock | Primary button |
|---|---|---|---|---|
| GET READY | `countdownSeconds != null` | big 3·2·1 over video | hidden | "Cancel" |
| WORKING | `currentSegment == ACTIVE` | reps label + info | rep counter + weight | "Done — log & rest" (or "…log last set") |
| RESTING | else, not done | count-up clock + "up next" | hidden (or quick next) | "Start set n" / "Start <next>" |
| ALL DONE | `allSetsDone` | summary | hidden | "Finish & save workout" |
| OPENING WARM-UP | `isOpeningWarmup` | "warm-up" | hidden | "Start first set" |

All of these fields already exist on `LiveSession`.

---

## 4. Feature specs

### 4.1 Top bar
- Collapse chevron → **minimize the live screen while the session keeps running** (foreground
  service already holds the process; navigate back to the app, session survives). Confirm this is
  the intended "collapse" behavior; alternative is a plain back.
- Round label only for circuits (`plan.isCircuit`).
- Pause → opens the pause screen (§4.12).

### 4.2 Segment progress
- New `SegmentBar` composable. Segments = the exercises worked in the current block:
  - Split: the plan's exercises (or the current superset group).
  - Circuit: the exercises in the current round.
- Fill state from `completedSets` vs `targetSetsFor`; current = `currentSlotId`.

### 4.3 Video hero — `LiveExerciseHero`
Adapt `ExerciseMediaCarousel` into a **2-page** hero:
- **Page 1 — real footage playlist:** all `Exercise.media` of `type == VIDEO`, played as an
  ExoPlayer **playlist** (angle 1 → angle 2 → loop) via `setMediaItems(...)` + `REPEAT_MODE_ALL`
  (ExoPlayer auto-advances and loops the list natively). Requires a small variant of
  `ExerciseVideoPlayer` that accepts `List<String>` instead of one `url`.
- **Page 2 — animation:** the GIF (`media` `IMAGE` labelled "Animation") via `ExerciseImage`.
- **Fallback:** no video → the animation/stills become page 1, no second page ("video with fallback").
- **Tap to pause/play** the video (toggle `playWhenReady`; show a play glyph when paused). Today's
  player has no tap control — add it.
- Data: the VM must expose the **full `Exercise`** for the current slot (for `media`), not just
  thumbnails — add `exerciseRepository.observe(currentExercise.exerciseId)` to
  `ActiveSessionViewModel`.
- Autoplay honors the pause-screen "Autoplay" toggle (§4.12).

### 4.4 HR/kcal HUD chip + in-place expand
- **Collapsed chip** (top-right of the hero): `♥ <bpm>  🔥 <kcal>`. Data: `live.lastBpm`,
  `live.summary.totalKcal`.
- **Tap → expands in place** (animated growth anchored to the top-right corner — not a centered
  dialog, not a new screen). Use an `AnimatedContent`/`expandIn` from the top-right; a scrim behind
  dismisses. Contents (all confirmed "keep all"):
  - Heart rate: current bpm, **zone** (`HrZone.forHr(lastBpm, profile.effectiveMaxHr)` →
    e.g. "Zone 3 · Moderate"), session **avg** + **peak**, and a **live HR line**
    (reuse `HeartRateChart`).
  - Calories: **total** + **burn rate** (kcal/min).
  - Watch: connected / streaming / lost (`WatchLinkState`) + a **reconnect** button when dropped.
  - Elapsed.
- New data to expose (small):
  - `profile.effectiveMaxHr` → add to the VM (from `ProfileRepository`) or into `LiveSession`.
  - **Verify** `LiveCalorieAccumulator.snapshot()` populates `avgHr`/`peakHr` live; if not, add
    running trackers (min/max/sum + count) — cheap.
  - kcal rate: derive from Δ`totalKcal` over Δt, or expose from the accumulator.
  - **Recent-HR ring buffer** for the sparkline: the raw sample list is deliberately kept out of
    `LiveSession` (perf). Add a bounded `recentBpm: List<Int>` (last ~60) to `LiveSession`, appended
    in `SessionController.onSample`.

### 4.5 Exercise label + "Exercise info"
- Bottom-left of the hero: reps/seconds target (orange) + exercise `displayName` (bold sans).
- "Exercise info" → navigate to the existing exercise **detail** route for
  `currentExercise.exerciseId` (same destination the planner's `onOpenExercise` uses). Wire an
  `onOpenExercise` callback through `ActiveSessionScreen`.

### 4.6 Rep counter dock
- Rounded top corners + **grabber** line (swipe up → §4.7).
- Controls: **− / editable number / +** (reuse `EditableNumberLarge`; **remove the ±5 bulk
  buttons**). Tapping the number opens the **shared numpad** (§4.10) instead of the system keyboard.
- **Ghost target**: opens showing the plan's target faded until touched (behavior already
  implemented — keep).
- **Weight pill**: shows current load ("Bodyweight" / "+20 kg"); tap → numpad/stepper → live
  `onSetWeight` (already wired, syncs to watch). Consistent with the planner's weight control.
- Holds (`ExerciseMeasure.SECONDS`) show "seconds held" instead of reps (keep existing behavior).

### 4.7 "This exercise" swipe-up panel (SPLIT from the journal)
New read-only bottom sheet, opened by swiping the dock grabber:
- Target (sets × reps/seconds, rest, weight).
- **Last time**: most recent `SetLog`s for this `exerciseId` from history.
- **Personal best**: best reps/weight for this exercise from history.
- **Form cues**: `Exercise.tips` + `Exercise.commonMistakes` (already authored).
- Needs a **history query**: `SessionRepository` → sets for an `exerciseId` across past sessions
  (from `setLogsJson`/`planJson`; see `SessionPerformanceRow`). Add a repository function.

### 4.8 Journal sheet (compact) — `JournalSheet`
Slides up from the bottom (ModalBottomSheet). Compacted vs. the reference:
- Header: `Journal` · `Done`. Round label.
- **Exercise rows**: slim single line — small thumb · name · `sets × reps` · done check · chevron.
- **Expanded**: a **dense grid**, one row per set: `set # | result | weight | effort`, small tap
  targets (each cell opens the numpad / effort input), plus an inline "note".
- Editing writes back per set (see §4.9 wiring). "Add" logs an extra set.

### 4.9 Effort logging (RIR / RPE / %RM) + wiring
- **Model** (`core/model/Workout.kt`):
  - `enum class EffortScale { RIR, RPE, PERCENT_RM }`
  - Extend `SetLog` with `weightKg: Double = 0.0`, `effortScale: EffortScale? = null`,
    `effortValue: Double? = null`, `note: String = ""`. All defaulted → **no DB migration**
    (`setLogsJson` deserializes old rows fine).
- **Input UI**: segmented `RIR | RPE | %RM` (reuse `PillChip`) + the shared numpad; a **"?"**
  button opens an explainer (what each scale means, with examples). Store the explainer copy once,
  reuse anywhere effort is entered.
- **Wiring**: `SessionController` currently creates a `SetLog` only on banking with reps > 0.
  Add methods to set per-set effort/weight/note for a given `slotId`+`setIndex`, merged into
  `setLogs` and checkpointed. The journal edits call these. Effort feeds **history/AI only** — it
  does **not** enter the calorie estimate (per the calorie-model rule).

### 4.10 Shared numeric keypad (app-wide) — `NumberPadSheet`
New reusable component (the reference's dark numpad):
- Big `0–9`, backspace, optional `MAX`, optional unit toggle (`REPS | TIME` for the counter;
  the scale tabs for effort).
- Presented as a bottom sheet. Returns the entered value via callback.
- **Adoption**: route `EditableNumber`/`EditableStepper`/`EditableNumberLarge` typing to open this
  instead of the system numeric keyboard, so reps, weight, effort, and **every planner stepper**
  share one keypad. Keep − / + tap behavior; the keypad replaces only the "tap-to-type" path.

### 4.11 Weight
- Planner: **already done** (`PlannedExerciseCard` "Added weight" + "Kilograms" stepper). Only
  change: its stepper's type-path uses the new numpad.
- Live: **already done** (`setAddedWeight`), surfaced via the dock weight pill (§4.6).

### 4.12 Pause screen — `PauseScreen`
Full-screen "PAUSED" + big clock + Resume / End. 2×2 toggle grid:
- **No sound** → mutes `SoundEffects`.
- **Vibration** → toggles `Haptics`.
- **Autoplay** → whether the hero video auto-plays (§4.3).
- **Hands-free** → spoken cues (TTS) for countdown/rest so you needn't touch the phone.
  *New capability; propose Phase 6 / optional.*
- **End now** = finish & save; move **Discard** here (off the main screen) as a secondary destructive action.
- Persist the four toggles via a small DataStore prefs repo (mirror `ExercisePrefsRepository`).

### 4.13 Skip / Restart / Get-ready
- **Skip**: already exists (`advanceToNext`) — present as a control on the hero/rest.
- **Restart** (new): redo the current set/exercise — reset `currentReps` to target and reopen the
  active segment without banking. Add `SessionController.restartCurrentSet()`.
- **Get-ready**: keep the 3·2·1 lead-in (`countdownSeconds`); restyle as big numerals over the
  video with a "Start now" shortcut (`startWorkNow`, exists).

---

## 5. Files touched (summary)

**New**
- `ui/session/` — `LiveExerciseHero.kt`, `LiveHud.kt`, `RepCounterDock.kt`, `ThisExercisePanel.kt`,
  `JournalSheet.kt`, `EffortInput.kt`, `PauseScreen.kt`, `SegmentBar.kt`.
- `ui/common/NumberPadSheet.kt` (shared numpad).
- `data/session/SessionPrefsRepository.kt` (pause toggles).

**Changed**
- `ui/session/ActiveSessionScreen.kt` (rebuild `LiveControls`), `ActiveSessionViewModel.kt`
  (current `Exercise`, `effectiveMaxHr`, effort/note/history calls, prefs).
- `session/SessionController.kt` (per-set effort/weight/note; `restartCurrentSet`; recent-HR buffer;
  live avg/peak/rate), `session/LiveSession.kt` (`recentBpm`, maybe `effectiveMaxHr`).
- `core/model/Workout.kt` (`SetLog` fields, `EffortScale`).
- `core/calorie/LiveCalorieAccumulator.kt` (expose live avg/peak/rate if missing).
- `data/session/SessionRepository(Impl).kt` (history query for a given exercise).
- `ui/theme/Color.kt`, `Type.kt`, `ui/common/Ambient.kt` (reference tokens).
- `ui/exercises/ExerciseVideoPlayer.kt` (playlist variant + tap-to-pause), or a new sibling.
- The planner + any numeric fields (adopt the shared numpad).

---

## 6. Risks / to verify / open

- **`LiveCalorieAccumulator`** — confirm what `snapshot()` fills live (avg/peak/zones). If only
  kcal, add cheap running trackers. (Read this file before Phase 2.)
- **Hands-free (TTS)** — genuinely new; recommend deferring to a later phase. Confirm scope.
- **Collapse/minimize** — confirm the chevron minimizes the running session vs. plain back.
- **Watch parity** — new per-set effort/notes are phone-side history; confirm the watch doesn't
  need them (it currently logs reps only).

---

## 7. Build sequence (each phase compiles & runs)

1. **Skin + skeleton** — tokens (Color/Type/Ambient), rebuild `LiveControls` as the fixed layout
   with the state table, reuse the existing counter/metrics inline. No new data. *Visible reskin.*
2. **Hero + HUD (collapsed)** — `LiveExerciseHero` (playlist + tap-pause + fallback), `LiveHud`
   chip; VM exposes current `Exercise`. Add recent-HR buffer + live stats.
3. **Numpad + counter + weight** — `NumberPadSheet`, rep counter dock, weight pill; adopt numpad
   in the planner.
4. **Journal + effort** — `SetLog` fields + `EffortScale`, `JournalSheet` (compact), `EffortInput`
   + "?", controller wiring + persistence.
5. **This-exercise panel + HUD expand + history** — split panel, history query, animated HUD expand.
6. **Pause + settings + skip/restart/get-ready** — `PauseScreen`, prefs repo, restart, get-ready
   restyle, (optional) hands-free TTS.

Phases 1–3 deliver the visible redesign; 4–6 complete the logging depth and polish.
