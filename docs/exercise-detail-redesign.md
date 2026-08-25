# Exercise Detail Screen — Redesign Implementation Plan

Status: **BUILT (2026-08-24)** — `:app:assembleDebug` green, wear compiles. Scope: the exercise
detail screen (`ui/exercises/ExerciseDetailScreen.kt`) and the surfaces it opens. Second screen of
the broader refresh; wears the same black-and-orange skin the live workout screen introduced.

**Two substitutions from the agreed plan, both flagged to the user:**
- **Muscle diagram is a custom-drawn schematic**, not an external open-licensed SVG — I couldn't
  fetch and license-verify a third-party SVG from the build environment (the fallback the plan
  called out). It sits behind `MuscleDiagram` / `MuscleRegion` / the 17-term `MUSCLE_MAP`, so a
  licensed anatomical SVG can be swapped in later without touching any caller.
- **Skills is plumbing only** — the `Skills` model + `ExerciseEnrichment.skills` field + the bars
  and empty state are built; the per-exercise content is authored later (the user will generate it
  with Sonnet into the enrichment batches). Every exercise currently shows the empty state.

---

## 0. Direction (locked with the user)

- **Layout:** a media **hero + persistent header** (title, body-part subtitle, difficulty pips,
  Start) that collapses on scroll, over a **sticky 5-tab strip**: `Guide · Muscles · Skills ·
  Progress · Details`. **Guide is the default tab** so the rich coaching is seen first, not buried.
- **Muscles:** front + back **anatomy diagram** with worked muscles highlighted (primary bright,
  secondary dim) + a muscle list. Driven by a **one-time 17-term map** (free-exercise-db's fixed
  muscle vocabulary) onto an **open-licensed** body SVG.
- **Skills:** five **authored** attribute bars — **Strength / Endurance / Skill / Mobility /
  Cardio** — carried on the `ExerciseEnrichment` overlay (the user generates the content later with
  Sonnet), shown alongside the real `efficiency` rating. Empty state where not yet authored.
- **Progress:** records that **adapt per movement** (reps + set-volume for bodyweight, added weight
  when loaded — never a sad "0.0 kg"), a **trend** over time, and a **history** list. Rides the
  existing `summarizeProgress` / `ExerciseProgress` engine.
- **Share:** an **image-card generator** — render the exercise / a personal record into a branded
  PNG with **multiple template designs** and share to social. Infra + a starter template set now,
  more designs as an ongoing track.
- Restyle into the black-and-orange skin (`Onyx`/`Flame`/`Chalk`/`Ash`). Difficulty pips. Share +
  Bookmark (Bookmark = existing favourites). Inline video only (no fullscreen this pass).

---

## 1. Reuse vs. new

| Piece | Status | Where |
|---|---|---|
| Swipeable media hero (video angles + animation) | ✅ reuse | `ui/exercises/ExerciseMediaCarousel.kt` |
| Black-and-orange tokens | ✅ reuse | `ui/theme/Color.kt` (`Onyx`/`Flame`/`Chalk`/`Ash`) |
| Rich coaching content | ✅ reuse | `Exercise.overview/instructions/tips/commonMistakes/problematicAreas/efficiency` |
| AI coaching fallback (un-authored exercises) | ✅ reuse | `ExerciseDetailViewModel.enrich` |
| Authored overlay layer (survives sync) | ✅ extend | `ExerciseEnrichment` + `ExerciseEnrichments` batches |
| Per-exercise **records** (reps PR, weight PR, sessions, last done) | ✅ reuse | `core/progress` `ExerciseProgress` / `exerciseProgress()` |
| Per-session history source | ✅ reuse | `SessionRepository.observePerformed()` → `PerformedSession` |
| "Appears in" source | ✅ reuse | `SavedWorkoutRepository` (plans containing this exerciseId) |
| Bookmark / favourites | ✅ reuse | `ExercisePrefsRepository.favourites` / `toggleFavourite` |
| Difficulty pips, body-part subtitle | ✅ from data | `Exercise.difficulty` / `Exercise.bodyPart` |
| **Body-diagram SVG + 17-term muscle map** | 🆕 new asset + map | — |
| **`Skills` model + `Exercise.skills` + enrichment field** | 🆕 new (plumbing) | content authored later |
| **Per-exercise trend series** (best set per session over time) | 🆕 small derive | from `observePerformed()` |
| **`maxVolume` record** | 🆕 small add | `ExerciseProgress` |
| **Share image-card generator** (FileProvider + render-to-bitmap + templates) | 🆕 new feature | — |
| **Tabbed detail screen** | 🆕 rebuild | `ExerciseDetailScreen.kt` |

No new Gradle dependencies. One manifest addition (a `FileProvider` for Share).

---

## 2. Screen architecture

Rebuild `ExerciseDetailScreen` as a collapsing-hero + sticky-tabs layout (a `LazyColumn` with a
`stickyHeader` for the tab strip, or a `Scaffold` + nested-scroll collapsing top). Regions:

1. **Hero** — `ExerciseMediaCarousel`, edge-to-edge, ~16:11; overlay a back button (top-left) and
   Share + Bookmark (top-right). Collapses to a slim bar as you scroll.
2. **Header** (persistent under the hero) — title, body-part subtitle, `Easy/Medium/Hard` + a
   3-pip difficulty meter, and the **Start workout** button (Flame).
3. **Tab strip** (sticky) — `Guide · Muscles · Skills · Progress · Details`.
4. **Tab body** — the selected tab's content scrolls beneath.

State: the current tab is local UI state; content per tab reads from the VM (below).

---

## 3. Feature specs

### 3.1 Guide tab (default)
Your existing coaching, restyled: `overview`, numbered `instructions` ("Steps"), `tips`,
`commonMistakes`, and the "goes easy on" (`problematicAreas`) note. For un-authored dataset
exercises with none of this, keep the **AI coaching card** (`enrich()`), restyled.

### 3.2 Muscles tab
- **Asset:** vendor an **MIT/CC-licensed** front+back muscle-map SVG into `res/` (license verified
  before committing — do **not** lift a commercial app's diagram). Candidate family:
  `react-body-highlighter`-style paths. Render via a Compose `Canvas`/`Painter`, tinting regions.
- **Map:** a single `MuscleMap` (17 entries) from free-exercise-db muscle name → SVG region id(s):
  `Chest, Shoulders, Triceps, Biceps, Forearms, Lats, Middle back, Lower back, Traps, Neck,
  Abdominals, Quadriceps, Hamstrings, Glutes, Calves, Abductors, Adductors`. Regions the chosen SVG
  doesn't draw separately fall back to the nearest group or go unhighlighted (graceful).
- **Highlight:** `primaryMuscles` in `Flame`, `secondaryMuscles` in `Flame @ ~45%`.
- **List** beneath: each worked muscle as a row (colour dot = primary/secondary + name). Data is
  coarse ("Chest", not "Lower chest") — highlight at muscle-group resolution.

### 3.3 Skills tab
- **Model (core):** `data class Skills(val strength: Int, val endurance: Int, val skill: Int, val
  mobility: Int, val cardio: Int)` — each 0–100. Add `val skills: Skills? = null` to `Exercise`.
- **Overlay:** add `skills: Skills? = null` to `ExerciseEnrichment`; merge in `applyTo` (`skills ?:
  base.skills`). The `ExerciseEnrichments` batches gain skills as the user authors them with Sonnet.
- **UI:** five labeled bars (Flame fill) + the `efficiency ★ x/5`. When `skills == null`, an empty
  state ("Skill profile not rated yet") rather than zeroed bars. Label the profile as an estimate.

### 3.4 Progress tab
Feed a `PerformedSession` list filtered to this exercise (from `observePerformed()`), reuse
`exerciseProgress()` for aggregates:
- **Records (adaptive):**
  - Weighted movement (`heaviest != null`): best weight, best reps, best **volume** (weight×reps).
  - Bodyweight: best reps, best **set-volume** (reps, or reps×bodyweight from profile), total reps.
  - Add `maxVolume: BestSet?` to `ExerciseProgress` (small).
- **Trend:** best set per session over time (metric = weight for loaded, reps for bodyweight), with
  a 30d / 90d / all toggle. New derive: `bestSetPerSession(exerciseId)` from the filtered sessions.
- **History:** past sessions with this movement — date · `sets×reps` · weight — tap → session detail
  (`Routes.detail`). Empty state invites a first session (→ Start).

### 3.5 Details tab
`equipment`, `force`, `mechanic`, `difficulty`, `tags`, and **Appears in** — the saved workouts
whose plan contains this `exerciseId` (`SavedWorkoutRepository`), each tapping into the planner.

### 3.6 Share — image-card generator
- **Infra:** add a `FileProvider` (`res/xml/file_paths.xml` + `<provider>` in the manifest) scoped
  to a cache subdir. Render a chosen template Composable to a `Bitmap` via
  `rememberGraphicsLayer()` + `toImageBitmap()` (Compose 1.7, already on the BOM), write PNG to
  cache, fire `ACTION_SEND` `image/png` through the provider.
- **Card types (starter):** (1) **Exercise card** — demo frame + name + muscles + difficulty;
  (2) **Achievement card** — a personal record ("12 pull-ups · new best") from the Progress data.
- **Templates:** each design is a Composable of a fixed export size (e.g. 1080×1350 and 1080×1920
  for stories). Ship ~3 designs to start; a picker lets you choose; add designs over time.
- **Entry:** the Share icon on the hero opens a template picker sheet → preview → share.

### 3.7 Header details
- **Difficulty pips:** `BEGINNER/INTERMEDIATE/ADVANCED` → "Easy/Medium/Hard" + 1/2/3 filled pips.
- **Subtitle:** `bodyPart.displayName` (the dataset has no movement-"family" field; noted).
- **Bookmark:** toggles `ExercisePrefsRepository` favourite; filled when saved.

---

## 4. Model / data changes

- `core/model` — new `Skills`; `Exercise.skills: Skills? = null`.
- `data/exercise/ExerciseEnrichment.kt` — `skills` field + merge in `applyTo`.
- `core/progress/TrainingProgress.kt` — `ExerciseProgress.maxVolume: BestSet?`; a
  `bestSetPerSession` helper for the trend (or compute in the VM).
- `ExerciseDetailViewModel` — inject `SessionRepository`, `SavedWorkoutRepository`,
  `ExercisePrefsRepository`; expose per-exercise progress, trend series, history, appears-in,
  favourite state; keep `enrich()`.
- No DB migration — `Skills` lives in the authored overlay and on the in-memory `Exercise`; nothing
  new is persisted to the sessions/exercise tables.

## 5. New assets / manifest
- `res/` — the muscle-map SVG(s) (front, back), license file recorded.
- `res/xml/file_paths.xml` + `<provider>` (androidx `FileProvider`) for Share.

---

## 6. Risks / open

- **SVG licensing** — must verify and record the source license before vendoring; fall back to a
  simple custom silhouette if nothing suitable is cleanly licensed.
- **Muscle map coverage** — a few free-exercise-db terms (neck, abductors/adductors) may lack a
  distinct region in the chosen SVG; handled by nearest-group fallback.
- **Skills content** — authored by the user later; the screen ships with the plumbing + empty state,
  so it's correct with zero authored skills and improves as batches land.
- **Share designs** — "a lot of designs" is an ongoing track; this pass ships the generator + a few
  templates, not a full library.

## 7. Build sequence (each phase compiles)

1. **Shell + skin** — rebuild the screen as collapsing hero + sticky tabs in the black-and-orange
   skin; move existing content into Guide/Details; wire difficulty pips, subtitle, Start, Bookmark.
2. **Muscles** — vendor the SVG, build the 17-term map + the highlighted diagram + list.
3. **Skills** — `Skills` model + enrichment field + the bars/empty-state UI.
4. **Progress** — records (adaptive) + `maxVolume`, the trend chart, the history list, appears-in.
5. **Share** — FileProvider + render-to-bitmap + starter templates + picker.

Phases 1–2 are the visible redesign; 3–5 layer on the data and the share feature.
