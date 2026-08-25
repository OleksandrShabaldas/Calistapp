# App-Wide Redesign + Feature Expansion — Plan

Status: **all 7 phases built** (2026-08-25). `:app:assembleDebug` builds; wear + core compile; core
tests green. Scope: migrate the entire app onto the
new onyx/orange design (the live-workout and exercise-detail screens already wear it), and add four
features the references imply. Delivered as **phases**, each of which compiles and is reviewable.

---

## 0. Direction (locked with the user)

- **Design:** promote onyx/orange to the real app-wide theme; retire glass + emerald; keep a
  **warm ambient wash** (retuned orange) behind screens; use **colored gradient cards** where colour
  carries meaning (the stats grid). Drop the Playfair serif app-wide in favour of the athletic bold
  sans already used on the new screens.
- **Statistics:** restyle + a period selector (week/month/all) + extra metrics (volume, duration,
  averages), reusing the existing progress engine.
- **Per-set customization (full):** each planned set carries its own reps/seconds, added weight,
  **target effort, and note**. Replaces the uniform `targetReps`/`weight` model.
- **Curated routines:** a new lightweight **Routine** concept (warm-up / stretching), with authored
  content, selectable on a new **session-setup** screen alongside session settings + timers.
- **Saved-workout detail:** a dedicated screen (exercises · history · start), instead of loading a
  saved workout straight into the planner.
- **Delete exercises:** hard-delete user-added ones; soft-hide dataset ones via a persisted
  hidden-ids overlay (survives the sync), with a restore list.

---

## Phase 1 — Design-system foundation ✅ built

The keystone: once the theme and shared components move, every screen shifts at once.

**Done.** Palette repointed to onyx/orange in `Color.kt` (legacy token _names_ kept but values
repointed — `Emerald`→`Flame`, `Cream`→`Chalk`, `Ink`→`Onyx`, `Glass*`→`Onyx*` — so all ~22 files
restyle without edits; rename deferred to the Phase 7 sweep). `Theme.kt` now `primary = Flame,
background = Onyx`, content colour `Chalk`. `Type.kt` headings moved off Playfair onto heavy sans
(serif family kept defined, no longer routed through the theme). `Ambient.kt` washes warm-on-onyx.
`Glass.kt` reskinned + new `GradientStatCard` (backed by `StatGradient` pairs) ready for Phase 2.
Route tints warmed (`CalistApp.kt`). Compiles clean.

- **`ui/theme/Color.kt`** — the live-screen tokens (`Onyx`/`Flame`/`Chalk`/`Ash`) become the app
  palette. Keep the semantic accents (`Coral` = HR/destructive, `Amber` = warning) but re-tune to sit
  on warm onyx. Add a small set of **stat-card gradient pairs** (red/amber/olive/green/teal/blue) for
  the metric grid.
- **`ui/theme/Theme.kt`** — `darkColorScheme(primary = Flame, background = Onyx, …)`; `LocalContentColor`
  → `Chalk`.
- **`ui/theme/Type.kt`** — retire `Display` (Playfair) usage; headings become bold sans. Keep the
  `Display` family defined but stop routing `MaterialTheme.typography` display/headline styles through
  it (or repoint them to sans). Numeric styles unchanged.
- **`ui/common/Ambient.kt`** — retune `AmbientScreen` to a warm orange-on-onyx glow; keep the
  per-route tint hook (`AmbientOverride`) but shift route tints to warm variants.
- **`ui/common/Glass.kt`** — repurpose `GlassCard`/`GlassRow`/`PillChip`/`SegmentedToggle`/
  `SectionHeading` to the flat onyx-with-hairline look (optionally a subtle gradient). Same API, new
  skin — so the ~56 call sites restyle for free. Add a `GradientStatCard` variant for the stats grid.
- **`ui/common/Components.kt`, `Rings.kt`, `SessionRow.kt`, `WatchStatus.kt`, `EditableStepper.kt`,
  `NoHeartRateDialog.kt`, `HeartRateChart.kt`, `FloatingNavBar.kt`, `UpdateCard.kt`** — swap old color
  tokens for the new ones. Mechanical.
- **`ui/CalistApp.kt`** — route tints → warm palette.

Outcome: the whole app is onyx/orange and coherent; later phases are polish + features.

---

## Phase 2 — Statistics ✅ built

**Done.** New `core/progress/StatsSummary.kt` (`StatsPeriod` week/month/all + `statsSummary()`,
period-scoped: workouts, distinct exercises, reps, added-load volume, duration, avg — warm-ups
excluded; 6 unit tests). `TrainingProgress` gained a `recentDays` 30-day strip (additive, default).
`HistoryViewModel` exposes `period`/`setPeriod`/`stats`. `ProgressTab` rebuilt: period selector →
six `GradientStatCard` tiles → 30-day day-dot strip → (kept, restyled) training load, weekly chart,
bodyweight, and "Most performed exercises" (renamed from "By movement"). Volume is honestly zero for
bodyweight work, so reps + exercise count sit beside it. Compiles; core tests green.

Rebuild `ui/history/ProgressTab.kt` (and its host `HistoryScreen`) to the reference's shape:

- **Period selector** — This week / This month / All. New: the current `summarizeProgress` is
  12-week; add a period filter over `PerformedSession` before summarizing (or a windowed variant).
- **Gradient stat grid** — Total Workouts, Total Volume, Total Duration, Avg Duration, Avg Volume,
  Total Exercises — each a `GradientStatCard`. Volume = Σ(weight×reps); needs `PerformedSession`
  duration (already have `activeDurationMs`/`totalDurationMs`).
- **Streak strip** — the 30-day dot calendar (from session days).
- **Keep** training load, weekly chart, per-movement records (restyled).
- Renamed "By movement" → "Most performed exercises".

---

## Phase 3 — Planner + per-set model ✅ built

**Done.** Model: `EffortTarget`, `PlannedSet`, `PlannedExercise.plannedSets` + a `sets()` accessor
(returns the explicit column or synthesizes a uniform one); `targetSetsFor`/`totalSets`/`targetLabel`/
`isWarmup` re-derived through it — fully backward compatible (7 new `PlannedSetTest` cases; all core
tests green). Engine: `SessionController` opens each work block on *that set's* reps and weight,
banks the per-set weight, and scores the block on the per-set load (segment metabolics override);
mid-session weight edits land on the current set for a per-set plan. `LiveSession` exposes
`currentSet`/`currentSetWeightKg`/`currentEffortTarget`; the live counter target, weight pill and
weight-pad now read the current set, and the journal shows each set's target effort as a "→ 8 RPE"
hint that pre-fills the editor. Watch reads per-set via `sets()` (older builds fall back). Planner:
each split exercise is an editable per-set column — reps/seconds · load · target effort per row (all
via the shared numeric pad, effort on its RIR/RPE/%RM tabs), warm-up toggle, add/remove set; a
circuit keeps one uniform definition (switching to circuit collapses any column). Compiles across
app + wear + core.

The biggest change, because per-set ripples through the live session and the watch.

### Model (`core/model/Workout.kt`)
- New `EffortTarget(scale: EffortScale, value: Double)` (reuses `EffortScale`).
- New `PlannedSet(reps: Int, weightKg: Double = 0.0, effort: EffortTarget? = null, note: String = "",
  isWarmup: Boolean = false)`. For holds, `reps` carries seconds (matching today's convention).
- `PlannedExercise` gains `plannedSets: List<PlannedSet> = emptyList()` **plus a `sets()` accessor**
  that returns `plannedSets` when present, else synthesizes them from the existing uniform
  `targetSets`/`targetReps`/`warmupSets`/`addedWeight`. **Backward compatible** — old saved workouts
  and stored sessions (uniform) keep working; new ones are per-set. `targetSetsFor`, `targetLabel`,
  `isWarmup(i)` are re-derived from `sets()`.

### Session engine
- `SessionController.defaultRepsFor` / `changeSegmentLocked` open each work block on **that set's**
  target: `slot.sets()[setIndex-1].reps` and its weight. Effort target flows to the journal as a
  pre-fill.
- `LiveSession` / journal: the counter's ghost target and the weight pill read the current set;
  the journal shows target effort next to logged effort.
- **Watch:** `WorkoutPlan` serialized to the watch now carries `plannedSets`; `WearSessionManager`
  reads per-set targets (falls back to uniform via `sets()` so an older watch build still works).

### Planner UI (`ui/planner/WorkoutPlannerScreen.kt`)
- Restyle to the reference's **block cards**: per exercise, a row per set (reps · weight · effort ·
  note), "Turn into superset", "Add specific rest after", "Add block". Session-level **Name,
  Difficulty, Session type, Timers** (rest between blocks/sets, time cap) header.
- The numpad (shared) drives every figure; effort uses the effort input.

---

## Phase 4 — Session-setup screen + curated routines ✅ built

**Done.** `core/model/Routine.kt` (`RoutineKind` warm-up/stretch, `RoutineItem`, `Routine` with
summary) + a seeded `RoutineCatalog` (2 warm-ups, 2 stretches, real gallery ids for thumbnails).
`RoutineRepository` (app) is the seam over the catalog. New `Routes.SETUP` + `SessionSetupScreen` +
`SessionSetupViewModel`: reached from the planner ("Continue"), it offers an optional warm-up, an
optional stretch, the four `SessionPrefs` toggles, and a session-wide rest default — then Start
(watch check) threads the chosen routines onto the plan as single-set timed warm-up blocks (opening)
and stretch blocks (closing), applies the rest default, and hands the finished plan to the
controller. Routines are gated to splits (a circuit rotates every slot by rounds, which a
once-through warm-up must not join — the screen says so). The old direct planner→start path was
removed. Compiles; Hilt graph valid.

### Routine model (`core/model`)
- `enum class RoutineKind { WARM_UP, STRETCH }`.
- `Routine(id, name, kind, bodyFocus: String, items: List<RoutineItem>)`, where
  `RoutineItem(exerciseId, name, seconds)` — a timed sequence.
- Authored content in a `RoutineCatalog` (a handful seeded now; expandable like the exercise
  catalogs), read through the repository layer.

### Setup screen (new route)
- Sections: **Warm-up** (pick a `WARM_UP` routine, optional), **Stretching** (pick a `STRETCH`
  routine, optional), **Settings** (the `SessionPrefs` toggles — voice/vibration/autoplay/hands-free),
  **Timers** (session-level rest defaults, applied to the plan). Then **Start session**.
- A chosen warm-up runs as an opening block; stretching as a closing block. Threaded into the plan
  the `SessionController` starts.

---

## Phase 5 — Saved-workout detail ✅ built

**Done.** New `Routes.SAVED_WORKOUT` + `SavedWorkoutDetailScreen`/`SavedWorkoutDetailViewModel`:
tapping a saved workout in the planner now opens it here — exercises (read-only rows w/ thumbnails,
tap to open the movement), history, Start / Edit / Delete. History matches by **plan name**, not
structural equality, since the setup screen threads warm-up/stretch blocks and can override rest, so
a performed plan is rarely identical but carries the same name. Start loads the plan into the draft
and heads to setup; Edit drops back to the planner (which reflects the loaded draft); Delete confirms
then pops. The planner's saved rows now say "Open". Compiles; Hilt valid.

- New route + `SavedWorkoutDetailScreen`: the workout's exercises (read-only summary), its **history**
  (sessions run from it — filter `observePerformed()` by matching plan), a **Start** button, and
  **Edit** (loads into the planner). Reached by tapping a saved workout instead of loading it
  straight in.

---

## Phase 6 — Gallery, filters, delete-exercise ✅ built

**Done.** `ExercisePrefsRepository` gained `hiddenIds` (hide/unhide) and `recentIds` (markRecent,
bounded, ordered) alongside favourites — user data that survives the sync. Delete-exercise:
user-added movements (`custom_…` ids) hard-delete; dataset ones soft-hide (the sync re-seeds a hard
delete, so hiding is the honest option) — same branch in the detail overflow menu and the editor.
The gallery filters hidden ids out of its list/count/facets, and shows a horizontal **Recent** strip
on the default unfiltered view (fed by opening a detail or adding to a plan). Profile gained a
**Hidden exercises** restore list. The filter sheet was **not** rebuilt into pip-meters — it already
reads onyx/orange via the token flip; that redesign is deferred as polish. Compiles; Hilt valid.

- **`ExerciseGalleryScreen`** — restyle; add a **Recent** section (recently opened/used ids, a small
  prefs list) above All exercises.
- **`ExerciseFilterSheet`** — restyle to the reference (Sort, Category, Difficulty as pip meters,
  Equipment, Muscles, "Show N results"). Drop facets that don't apply to single exercises.
- **Delete exercise:**
  - `ExercisePrefsRepository` gains a `hiddenIds` set (persisted, like favourites).
  - The gallery query / `ExerciseSyncManager` filters hidden ids out, so a hidden dataset exercise
    stays hidden across syncs. A "Hidden exercises" list in Profile/settings restores them.
  - User-added exercises (source not a dataset) are **hard-deleted** via `ExerciseRepository.delete`.
  - Delete/hide entry points: the exercise detail overflow and the editor.

---

## Phase 7 — Remaining screens ✅ built

**Done.** The token flip (Phase 1) already carried every remaining screen — Dashboard, Profile,
Session detail, editor, the session start controls — onto onyx/orange, so this phase was the
promised cleanup + finishing: the deferred **token rename** landed (`Emerald`→`Flame`,
`Cream`→`Chalk`, `Ash`; `Ink`→`Onyx`, `OnyxRaised`; `Glass*`→`Onyx*` across ~16 files, aliases
removed from `Color.kt` — everything now names the real onyx/orange tokens, no `Emerald`/`Cream`/
`Glass*` left anywhere), and the **editor delete control** now offers "Delete" for user-added
(`custom_…`) or "Hide" for dataset exercises, matching the detail overflow. Full `:app:assembleDebug`
builds; wear + core compile; core tests green. (Minor: import ordering in the renamed files is no
longer alphabetical — a cosmetic "Optimize Imports" away, left untouched to keep the diff mechanical.)

Restyle to the new system: **Dashboard**, **Profile**, **Session detail** (+ `CalorieBreakdownCard`),
**Exercise editor** (`ExerciseEditScreen`, + the delete control), the **"New workout" start screen**
(fold into / link to the new setup screen). Sweep any leftover glass/emerald usages.

---

## Cross-cutting model changes (summary)
- `PlannedSet` + `EffortTarget`; `PlannedExercise.plannedSets` + `sets()` (Phase 3).
- `Routine`/`RoutineItem`/`RoutineKind` + `RoutineCatalog` (Phase 4).
- `ExercisePrefsRepository.hiddenIds` + recent ids (Phase 6).
- No DB migrations — plans/sessions are JSON; new fields are additive with defaults. Watch stays
  compatible via the `sets()` fallback.

## Risks / open
- **Per-set is the risk** — it touches the live screen we just shipped, the journal, and the watch.
  The `sets()` fallback keeps everything backward compatible; live-session opening targets and the
  watch sync are the spots to test hardest.
- **Routine content** — a few seeded routines now; the curated library is an ongoing authoring track
  (like exercises/videos/skills).
- **Theme promotion** — a handful of screens use explicit accent tokens (Sky/Violet) that need a
  per-screen judgement call, not just a global swap.

## Build order
Phases run 1→7. Phase 1 is the visible flip; 2–7 layer polish and features. Each phase is a
compilable checkpoint.
