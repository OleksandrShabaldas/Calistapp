# Calistapp UX/UI + feature audit — 2026-08-25

A full pass after the 13-item feedback batch, hunting the "feels off / broken / unconventional /
missing" class of issues — the kind a user notices in the first five minutes but that don't surface
during development. Findings are grouped by type and ordered by impact within each group. File:line
references are to the state right after the feedback batch.

Severity: **P1** = wrong/broken or clearly missing; **P2** = feels off / inconsistent; **P3** = polish / nice-to-have.

---

## Status — implemented 2026-08-25 (build green, both APKs)

**Fixed:** A1 (circuit set-count in summary), A2 (decimal weights via `DecimalPadSheet`), A3 (detail
tab/trend survive rotation), B1–B4 (rest-override + hands-free toggle removed, vibration copy, dead
code pruned), C1 (nav labels), C2 (dumbbell/resume FAB icon), C5 (greeting de-italicised), C6 (enrich
moved to Settings → AI), E1 (dead no-op), E3 (quota copy). **Plus the deep audit found + fixed:** the
exercise editor lost unsaved edits on rotation (now `rememberSaveable`), and the stats records showed
truncated weights (now `formatKg`). Filter sheet and watch app audited clean.

**Delivered features:** D2 (session share card), D3 (personal-best celebration on the summary + shared
card), D5 (JSON training-data export in Settings → About).

**Second pass — also done:** **C3** (shared `BackButton` across the custom-chrome screens —
exercise detail, session setup, settings sub-pages), **C4** (one shared `AiActionCard` behind both the
session-analysis and exercise-coaching cards), **C8** (NavHost crossfade + a slight scale between
screens; a pop on the live rep counter — the countdown already animated). **Plus** the *effort-logging
discoverability* problem (raised in testing): per-set effort (RIR/RPE/%RM) was only reachable via the
Journal, so nobody found it — now a **"Rate that set" chip appears in the rest state**, right after you
bank a set, pre-filled from the plan's target effort (`RateSetChip` + `EffortInputSheet`).

**Deliberately not done (bigger, or a design call):** D1 Health Connect (largest; wants device
testing), D4 reminders (needs WorkManager + a scheduling UI), D7 onboarding, D6 (rest stays a pure
count-up by choice), E2 touch targets. C7 left as-is (session RPE copy already reads clearly).

---

## A. Real bugs

### A1 · Circuit "planned vs performed" shows the wrong set count — **P1**
`SessionDetailScreen.kt:193` renders `"${done.size}/${slot.targetSets} sets"`. `targetSets` is the
slot's raw default (3); in a **circuit** the real per-slot count is the round count. This is the exact
bug that was fixed on the planner card (#1) — it just also lives here. A 2-round circuit shows
"2/3 sets" and reads as if you quit early.
**Fix:** use `current.plan.targetSetsFor(slot.slotId)` (the live screen, journal and watch already do).

### A2 · Fractional weights are impossible to enter — **P1**
`NumberPadSheet` is integer-only (`entry.toIntOrNull()`, digit keys, no decimal point), and it's the
one control behind every weight field (planner per-set weight, the live weight pill, added-weight).
`PlannedSet.weightKg`/`SetLog.weightKg` are `Double`, but you can never enter **2.5 / 7.5 / 12.5 kg** —
the exact increments weighted calisthenics uses (plates, small dumbbells, a loaded belt). The circuit
weight stepper is also `Int`, step 5. Every logged weight is silently truncated.
**Fix:** add a `decimal` mode to `NumberPadSheet` (a "." key) for weight, or give weight a dedicated
stepper with 2.5 kg increments. Then feed real Doubles through.

### A3 · Exercise-detail tab (and trend window) reset on rotation — **P2**
`ExerciseDetailScreen.kt:104` `var tab by remember { … }` and `:543` `var window by remember { … }`
are plain `remember`, so a rotation drops you back to the Guide tab / All-time window. Same class as
the #12 rotation bug.
**Fix:** `rememberSaveable`.

---

## B. Loose ends from the #7 change (rest is now a count-up)

Making rest a pure count-up left three controls stranded that still imply a rest *target*. They should
be finished off, or the app half-says two contradictory things about rest.

### B1 · SessionSetup "Rest between sets" override is now inert — **P1**
`SessionSetupScreen.kt:149-169` + `SessionSetupViewModel.setDefaultRest` (and the `restSeconds` rewrite
at `SessionSetupViewModel.kt:91`) still let you set a session-wide rest that "overrides every exercise's
rest". But the live screen no longer *shows* a rest target, so this does nothing visible now. A control
that changes nothing is worse than no control.
**Fix:** remove it (rest is a stopwatch), or make it a real, honest thing again (see D6).

### B2 · "Hands-free cues" is a dead toggle — **P1**
`SessionSetupScreen.kt:177` toggles `handsFree`, wired end-to-end to DataStore — but there is **no TTS
engine**. The memory notes the pause-screen copy of this switch was deliberately removed for exactly
this reason ("a dead switch was worse than no switch"); this one survived.
**Fix:** remove it until spoken cues actually exist (then re-add both together).

### B3 · Stale vibration copy — **P3**
`SessionSetupScreen.kt:175` subtitle: "Buzz on **rest-over** and phase changes." The rest-over buzz is
gone. Vibration now only fires on banking a set.
**Fix:** reword, e.g. "Buzz when you bank a set."

### B4 · Dead code left behind — **P3**
`WorkoutPlannerViewModel.setRest` (planner rest stepper removed), `LiveSession.restRemainingSeconds`
(only `RestAlert` read it), and `RestAlert` + `Haptics.restOver` (no longer wired) are now unused.
**Fix:** prune, or repurpose under D6.

---

## C. UX polish / consistency ("feels off")

### C1 · Bottom nav is icon-only — no labels — **P2**
`FloatingNavBar` shows four bare icons (Home / Exercises / History / Settings) with only a tint change
on selection. The Material convention is labels (at least on the selected tab); icon-only nav is a
common "which one is History again?" friction, especially with the Settings/History icons.
**Fix:** show a small label under each icon (or at least the selected one), or a tooltip on long-press.

### C2 · Start-workout FAB is a "+" even when it means "resume" — **P2**
`FloatingNavBar` uses `Icons.Filled.Add`; `contentDescription` flips to "Open running workout" when a
session is live, but the glyph stays "+". A plus doesn't say "start a workout", and definitely not
"resume the one you're in".
**Fix:** use a play/dumbbell glyph; swap to a distinct resume glyph while a session runs.

### C3 · Back buttons are three different things — **P2**
`SessionDetailScreen` uses a Material `TopAppBar` back arrow; `ExerciseDetailScreen` a custom 38dp
round icon; `SessionSetupScreen` a 30dp tinted icon; the new Settings sub-pages a 36dp `IconButton`.
Same gesture, four looks. Pick one back-affordance and reuse it.

### C4 · AI cards don't match each other — **P2**
`SessionDetailScreen.AiCoachCard` (Material `Button`, `colorScheme` surface), `ExerciseDetailScreen.AiCard`
(custom `FlameSoft` card + Flame capsule button), and the gallery `EnrichAllCard` (Material) are three
visual treatments of the same "AI does a thing" idea. Unify into one AI-card component.

### C5 · Dashboard greeting is a leftover from the old identity — **P3**
`DashboardScreen.Greeting` sets the first word in *italic* and the comment still calls it "accented
italic **serif** — the reference's signature opening move." The serif (Playfair) was dropped app-wide
for athletic bold sans, so this now renders as an italic sans word that reads editorial on an otherwise
athletic screen. Either drop the italic or commit to the treatment deliberately.

### C6 · The "Enrich all with AI" card sits in the browse flow — **P2**
`ExerciseGalleryScreen.EnrichAllCard` is a bulk-admin action wedged between the results count and the
exercise cards, in the main browse path. It reads as clutter ("why is this here every time I search?").
**Fix:** move it into Settings → AI (or a dedicated "Library" settings row).

### C7 · Two effort systems, unrelated — **P3**
Session detail rates the whole session on RPE 1–10 (`RpeCard`); the live journal rates each set on
RIR/RPE/%RM. Both are legitimate, but a user meets "RPE" in two different shapes with no link between
them. Consider deriving/relating them, or labelling the session one "overall effort".

### C8 · Animation gaps (continuing #4) — **P3**
No screen-to-screen transitions (nav is a hard cut); the story-dash `SegmentBar` and the dashboard rings
snap rather than animate on change; the live rep counter number doesn't animate when it changes. These
are the remaining "feels cheap" surfaces after the live-screen first pass.

---

## D. Missing features that fit this app

### D1 · Health Connect (or Google Fit) sync — **P1 for an Android fitness app**
Sessions, calories and HR live only in Room. Android users increasingly expect workouts to flow into
**Health Connect** so their rings/other apps see them. This is the single biggest "a real fitness app
would do this" gap. (Write sessions + active calories + HR series; optionally read weight back.)

### D2 · Post-workout share card — **P2**
Exercises have a polished `ShareCard`; a **session** has none. A shareable summary (calories, peak HR,
duration, any PRs, the HR trace) is both a delight and a growth loop, and most of the data/rendering
pattern already exists.

### D3 · Personal-best celebration — **P2**
The app computes bests per exercise (`ExerciseProgress`), but hitting a new one — live or on the summary
— is never acknowledged. A "New best: 12 reps 💥" moment is cheap to add and disproportionately raises
the "polished" feel.

### D4 · Reminders / scheduling — **P2**
Weekly goals exist, but nothing nudges toward them. A simple "train X times/week" reminder (WorkManager
notification) closes the loop the goals open.

### D5 · Data export / backup — **P2**
No way to export sessions (CSV/JSON) or back up. For an accuracy-focused tracker people invest months
in, "my data is trapped and un-backupable" is a trust problem.

### D6 · Optional rest target (opt-in), done right — **P3**
#7 removed rest targets entirely, which is the right default (rest is a stopwatch). But some users do
want "aim for ~90s" guidance. Reintroduce it as an **opt-in per-session** setting that only *highlights*
the count-up once it passes the target — never a forced countdown. This also gives B1's control a
real job again.

### D7 · Onboarding — **P3**
First run is the dashboard plus a "finish profile" nudge; calories read 0/garbage until the profile and
watch are set up, which looks broken to a new user. A 3-step guided start (profile → connect watch →
build first workout) would remove the "why is everything zero" confusion.

---

## E. Robustness / accessibility (smaller)

- **E1 (P3):** `NumberPadSheet.kt:258-260` has a dead `if (!active) { Box(Modifier) }` no-op. Remove.
- **E2 (P3):** Several custom icon buttons have hit areas below the 48dp min — live top bar icons (28dp),
  SessionSetup back (30dp). Bump the touch target (keep the visual size).
- **E3 (P2):** Bulk "Enrich all" fires 800+ requests; the free tier caps at ~500/day (the reason the new
  AI tiering routes it to the higher-quota Lite models). When the cap hits, surface "paused — daily quota
  reached, resumes tomorrow" instead of a wall of generic errors, and auto-resume.

---

## Suggested order to tackle

1. **Finish #7** (B1–B4) — it's half-done and actively contradictory. Small.
2. **A1, A3** — quick correctness/polish. A2 (decimal weight) is slightly bigger but high-value.
3. **C1–C4** — the consistency wins that most move the "polished" needle. Medium.
4. **D2, D3** (share + PR celebration) — high delight per unit effort, reuse existing data.
5. **D1** (Health Connect) — the biggest feature, worth its own effort block.
