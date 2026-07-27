# Calistapp

An exercise tracker built around one idea most apps get lazy about: **accurate, personalized calorie burn.**

You build your workout up front — the exercises, the sets, the reps. During the session Calistapp streams **real-time BPM** from a Wear OS watch, knows **which exercise** you're on and **how many reps** you did, and integrates energy expenditure **sample-by-sample over real time**, crediting effort and recovery differently. Then it can hand the session to **Google Gemini** for personalized feedback.

> Status: working foundation. Phone + watch + AI + local storage all build and run. Architecture is deliberately modular so the many planned features slot in cleanly.

**Get it:** signed APKs for the phone and watch are attached to each [release](../../releases).

---

## Versioning

`0.0.x` for small fixes, `0.x.0` for bigger changes (patch resets to 0). Before any segment would
need a second digit, it carries into the one on its left instead — the release after `0.9.x` is
`1.0.0`, never `0.10.0`. Phone and watch ship the same version number, since they're always
released together and the sync protocol between them assumes a matched pair.

---

## Why it's more accurate

| Typical trackers | Calistapp |
|---|---|
| One average HR × a formula | Energy integrated at each HR reading over the real time delta |
| Can't tell work from rest | You mark active/rest in real time; each is scored differently |
| Treats all effort alike | Knows the movement — muscle mass recruited, compound vs isolation, static vs dynamic |
| Blind to what you actually did | Reps give a physics-based estimate heart rate cannot see |
| Ignores fitness | Uses VO₂max (when provided) via the fitness-adjusted model |
| Rest inflates the number | Rest is bounded by resting metabolism, not exercise formulas |

**The model** (see [`CalorieEngine`](core/src/main/kotlin/com/calistapp/core/calorie/CalorieEngine.kt) and [`ExerciseIntensity`](core/src/main/kotlin/com/calistapp/core/calorie/ExerciseIntensity.kt)):

- **Active energy** — [Keytel et al. (2005)](https://pubmed.ncbi.nlm.nih.gov/15966347/) HR→energy regression, evaluated at each instantaneous heart rate. Uses the VO₂max-adjusted variant when your profile has it.
- **Resting floor** — Mifflin–St Jeor basal metabolic rate, so no minute counts below true resting metabolism.
- **Segment-aware integration** — the HR stream is walked in slices bounded by both samples *and* active/rest edges, so calories are never smeared across a work↔rest boundary.
- **Dropout guard** — gaps between samples are capped (default 30 s) so a sensor dropout can't run the estimate away.

### What the exercise and rep data add

Heart rate already reflects effort, so multiplying it by a "difficulty score" would double-count and make the number *worse*. Calistapp only corrects where HR is **demonstrably blind**, and every correction is **clamped to ±35%** so a mis-tagged exercise can't wreck a session:

- **Muscle-mass recruitment** — Keytel was regressed on cycling/treadmill work. At the same heart rate a pull-up recruits far more muscle mass than a curl, and costs more. Recruitment is derived from the exercise's primary/secondary muscles.
- **Cardiac lag on short sets** — HR response has a time constant around 30–60 s, so a 40-second set is over before heart rate reflects its cost. Short work blocks get an uplift that decays exponentially with block length.
- **Isometric holds** — planks and L-sits produce less cardiac output than dynamic work of comparable cost, so HR reads low.

Plus a **mechanical-work floor** from your reps, which is *independent evidence* rather than another correlate of HR — force × distance ÷ muscular efficiency, counting the eccentric phase. It matters most exactly where wrist-worn optical HR is known to fail: grip-heavy work like pull-ups and hangs, and outright sensor dropout mid-set. It's applied as a floor, never a replacement. **Added weight** (a vest, a dip belt, dumbbells) is part of this term — any planned exercise can carry extra load, and those kilograms go straight into the force calculation.

### Why the number isn't inflated

Most HR trackers overcount, and in two specific ways this engine deliberately avoids:

- **It reports energy *above resting*, not gross.** [Keytel](https://pubmed.ncbi.nlm.nih.gov/15966347/) returns total metabolic rate at a given heart rate — including the resting metabolism you'd burn on the sofa. Reporting that as "calories burned" credits the workout with roughly 80 kcal an hour you didn't earn. The engine subtracts the resting rate from every slice ([`Formulas.netKcalPerMin`](core/src/main/kotlin/com/calistapp/core/calorie/Formulas.kt)), which is what "active calories" means on every serious tracker.
- **It corrects for field bias.** Keytel was fitted on steady-state lab cycling; resistance training keeps HR elevated between sets, and gripping/bracing plus wrist-worn optical sensors all push the estimate high. A modest calibration factor (`Config.hrCalibration`) scales the HR-derived term down — but not the mechanical floor, which is physics and needs no correction.

Both are pinned by [tests](core/src/test/kotlin/com/calistapp/core/calorie/CalorieEngineTest.kt), including one asserting that sitting at resting HR yields essentially zero exercise calories.

Calibration, pinned by [unit tests](core/src/test/kotlin/com/calistapp/core/calorie/CalorieEngineTest.kt) at 75 kg bodyweight:

| Movement | Model | Published |
|---|---|---|
| Pull-up | ≈0.45 kcal/rep | ≈0.5 |
| Push-up | ≈0.24 kcal/rep | ≈0.2–0.3 |
| Bodyweight squat | ≈0.32 kcal/rep | ≈0.3 |

Run `./gradlew :core:test` — the engine is pure Kotlin and fully unit-tested.

---

## Design

Dark-only, and deliberately so — the whole system rests on a coloured ambient wash bleeding out of a
near-black page, which has no meaningful light-mode equivalent.

- **Ambient wash** ([`AmbientScreen`](app/src/main/kotlin/com/calistapp/app/ui/common/Ambient.kt)) —
  a soft glow behind every screen, hued per destination (emerald home, violet library, sky history,
  amber profile). During a workout the tint is driven by **work vs rest**, so the room changes colour
  when you switch — a cue you can read from across the gym without focusing on a number.
- **Glass surfaces** ([`GlassCard`](app/src/main/kotlin/com/calistapp/app/ui/common/Glass.kt)) —
  translucent fills over a hairline border, so the wash reads *through* the cards. Opaque panels on
  black are what make a dark UI look like stacked grey boxes.
- **Two typographic voices** ([`Type.kt`](app/src/main/kotlin/com/calistapp/app/ui/theme/Type.kt)) —
  **Playfair Display** (bundled, variable, [OFL](app/licenses/PlayfairDisplay-OFL.txt)) for titles and
  section headings; the system sans for body text and every number. Data wants a neutral face, so
  heart rate is never set in a display serif.
- **Rings over bars** — the hero metric sits inside a progress arc, which communicates "how far
  through" pre-attentively.
- **Floating capsule nav** with the primary action raised out of its centre, so starting a workout is
  always one reachable tap.

Tokens live in `ui/theme/` (`Color`, `Type`, `Shape`) and the shared primitives in `ui/common/`
(`Ambient`, `Glass`, `Rings`, `FloatingNavBar`) — screens compose those rather than styling
themselves, so the look stays consistent as features land.

---

## Architecture

Multi-module, clean-layered, so shared logic is written once and features stay isolated.

```
Calistapp/
├── core/     Pure-Kotlin shared library — no Android deps
│   ├── model/     UserProfile, HeartRateSample, Segment, WorkoutSession, WorkoutPlan,
│   │              PlannedExercise, SetLog, ExerciseMetabolics, SessionSummary, HrZone
│   ├── calorie/   Formulas (Keytel, Mifflin) + CalorieEngine + ExerciseIntensity
│   │              + LiveCalorieAccumulator  ← the accuracy engine
│   └── sync/      WearSync contracts + payloads (the phone↔watch wire format)
│
├── app/      Phone app (Kotlin, Jetpack Compose, Hilt)
│   ├── data/
│   │   ├── local/     Room: SessionEntity, SessionDao, CalistDatabase (+ migrations)
│   │   ├── profile/   DataStore-backed ProfileRepository
│   │   ├── session/   SessionRepository, PlanDraftRepository
│   │   ├── ai/        GeminiClient + InsightsRepository (prompt engineering)
│   │   └── sync/      LiveSessionBus, PhoneWearListenerService, WatchCommandSender
│   ├── session/   SessionController — the live workout state machine
│   └── ui/        Compose screens: dashboard, planner, active session, history, detail(+AI)
│
└── wear/     Wear OS app (Kotlin, Wear Compose)
    ├── hr/       HeartRateSource ── HealthServices (real sensor)
    ├── session/  WearSessionManager (app-scoped state machine) + WearSessionService (foreground)
    ├── sync/     PhoneSender, WearListenerService
    └── ui/       WearSessionScreen (current exercise, set counter, work/rest, wrist rep logging)
```

### Phone ↔ watch

Control flows **both ways**. Either device may issue a `ControlPayload`; the receiver applies it locally and **does not re-broadcast**. That one rule keeps start/stop, work/rest, exercise selection and rep logging in step without the two devices echoing each other into a loop.

The workout you build on the phone is pushed to the watch with the `START` command, so the watch runs the same plan — current exercise, set number, and a rep counter you can bump from the wrist.

The session lives in [`WearSessionManager`](wear/src/main/kotlin/com/calistapp/wear/session/WearSessionManager.kt), which is **application-scoped, not owned by a ViewModel**. That's what lets tracking survive the screen turning off mid-set, and lets a command from the phone start a workout while the watch UI isn't even open. A foreground service holds the process open for the duration.

**Connection status.** [`WatchConnectionMonitor`](app/src/main/kotlin/com/calistapp/app/data/sync/WatchConnectionMonitor.kt) reports the link as one of four states, because they have genuinely different fixes and collapsing them into one "connected" boolean is what makes sync problems feel unexplainable:

| State | Meaning | What to do |
|---|---|---|
| **No watch connected** | Nothing paired to the phone | Pair it in Galaxy Wearable / Wear OS |
| **Watch app not reachable** | Watch is paired, Calistapp isn't running on it | Open the app on your watch |
| **Watch connected** | App answered a ping, no data flowing yet | Nothing — HR starts with the workout |
| **Connected — live** | Data arriving right now | Nothing (indicator pulses) |

Reachability uses `CapabilityClient` against the capability the watch declares in its `wear.xml`, so it reflects the *app* rather than the hardware pairing. **Reconnect** buttons on the dashboard, the pre-workout screen, and the watch itself drop the cached node list, re-resolve, and send a `PING` — the reported status then reflects a real round trip rather than a stale lookup.

**Performance.** Live calories are accumulated incrementally by [`LiveCalorieAccumulator`](core/src/main/kotlin/com/calistapp/core/calorie/LiveCalorieAccumulator.kt) — O(1) per sample. Re-running the full engine over the whole history on every reading is quadratic in session length and was what made the watch crawl after ten minutes. Connected-node lookups are cached and HR samples are batched rather than sent one message per reading. The finished session is still scored by `CalorieEngine`, so what gets stored stays authoritative; a [test](core/src/test/kotlin/com/calistapp/core/calorie/LiveCalorieAccumulatorTest.kt) pins the two to agree.

---

## Setup

**Requirements:** Android Studio (Ladybug or newer), JDK 17 (bundled with Studio as the JBR), Android SDK 35.

1. **Open** the project folder in Android Studio and let it sync.
2. **Add your Gemini key** — get a free one at [aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey), then put it in `local.properties`:
   ```properties
   GEMINI_API_KEY=your_key_here
   # optional: GEMINI_MODEL=gemini-2.0-flash
   ```
   (Leaving it blank is fine — the app just disables AI analysis gracefully.)
3. **Run the phone app** — pick the `app` configuration and a phone emulator/device.
4. **Run the watch app** — pick the `wear` configuration and a **Wear OS emulator**, paired with the phone emulator so the Data Layer connects.

### Heart rate on the Wear emulator

Heart rate comes only from real sensor hardware via Health Services — there is no simulated HR source in the app. The Wear emulator has no HR sensor, so feed it synthetic sensor data from outside the app:

- **Extended Controls** (`...` next to the emulator) → **Virtual sensors** → **Heart rate**, or
- via adb:
  ```bash
  adb -s emulator-5554 emu sensor set heart-rate 140
  ```

Grant the `BODY_SENSORS` permission on first run (the watch UI prompts for it).

### Command line

```bash
./gradlew :core:test        # calorie-engine + accumulator unit tests
./gradlew assembleDebug     # build both APKs
```

---

## How a workout flows

1. **Build the workout** on the phone (`New workout → Build a workout`): search the gallery — the same fuzzy relevance search the gallery tab uses — add exercises, set sets and reps, reorder, add weight to any of them. Choose **exercise-by-exercise** (all sets of one movement, then the next) or **circuit** (one set of each, then repeat for N rounds). Each exercise's physical profile is derived once and baked into the plan.
2. **Start it.** The plan is pushed to the watch, which is brought to the foreground and starts the same session streaming HR. Sessions **open in REST** — the workout begins when you do, not when the app launches.
3. **Work through it.** Tap to start a set and a **3-second lead-in** counts you in (so getting to the bar isn't scored as work); toggle **Work / Rest** on either device and the other follows. Log reps from the phone or the wrist. Leaving *Work* banks the set — and if you left the reps at zero, it asks first, in case you forgot to log them. The current exercise, its demo animation and set progress are the focus of the screen; calories and HR sit below.
4. On finish, the session is scored and stored in **Room**, with a per-exercise energy breakdown.
5. Open it in **History → session detail** for the breakdown, planned-vs-performed, and the HR-over-time chart, then tap **Analyze with AI** for Gemini feedback that now sees your exercises and reps.

---

## Exercise gallery

A browsable, filterable library of exercises (`Exercises` tab):

- **Breadth** comes from the open, public-domain [free-exercise-db](https://github.com/yuhonas/free-exercise-db) (~873 exercises with consistent photos, muscles, equipment, instructions), synced on first launch and cached in Room.
- **Depth** comes from Calistapp's hand-authored [`CalisthenicsCatalog`](app/src/main/kotlin/com/calistapp/app/data/exercise/CalisthenicsCatalog.kt) — an **efficiency rating**, **problematic areas**, **common mistakes**, **tips**, and an honest **overview**.
### Search

[`ExerciseSearch`](core/src/main/kotlin/com/calistapp/core/search/ExerciseSearch.kt) is a relevance
engine, not a `contains` check — nobody remembers whether the dataset spells it "Pull-Up", "Pull Up"
or "Pullup":

- **Normalisation** strips punctuation and spacing before comparing, so all three collapse to one key.
- **Token coverage** matches multi-word queries in any order — "wide pull up" finds "Wide-Grip Pull-Up".
- **Typo tolerance** via a bounded edit distance, so "squt" still finds squats.
- **Gym shorthand** is mapped to dataset vocabulary — "abs", "quads", "delts", "ohp", "db".
- **Ranking puts the plain movement first.** An exact name match scores far above a partial one, and
  longer names are penalised in proportion to the extra they carry, so "pullup" returns Pull-Up, then
  the weighted and grip variations — not whatever sorted first alphabetically.

Matching is anchored deliberately: only the *candidate* may extend the query, and coarse metadata
(force, mechanic, category) can refine a hit but never qualify one on its own. Both rules exist
because breaking them turned a twelve-result query into 800 — every exercise with `force = "pull"`
matched "pullup". The behaviours above are pinned by
[unit tests](core/src/test/kotlin/com/calistapp/core/search/ExerciseSearchTest.kt).

**Sorting:** best match, name A–Z / Z–A, most / least efficient, easiest / hardest first.

**Filtering** is multi-select throughout (because "chest *or* shoulders" is a normal thing to want),
with options drawn from the loaded gallery so no chip can lead to an empty list: body part,
difficulty, target muscle, secondary muscle, equipment, and bodyweight-only.

One deliberate inversion: **problematic areas filter as an exclusion** — "Avoid stressing: wrists"
hides the exercises that load them. Filtering *for* the movements that hurt your bad shoulder is
almost never the question you're asking.

**Exercises animate.** free-exercise-db ships two frames per exercise — the start and finish positions — so [`ExerciseImage`](app/src/main/kotlin/com/calistapp/app/ui/exercises/ExerciseImage.kt) cycles them at roughly rep tempo instead of showing a dead thumbnail. Every frame is composed at once and revealed by animating alpha, so the loop is a true crossfade rather than a reload flicker, and gallery cards are phase-offset by exercise id so a scrolling list doesn't strobe in unison. The detail screen adds tap-to-pause. Animated GIFs work through the same path (Coil's GIF decoder is installed in `CalistApplication`).

Entries with no artwork at all — a real slice of the dataset, plus anything you add yourself — get a named placeholder rather than a bare icon. To fix one, edit the exercise and paste **one URL per line**: a single GIF, or several frames that get played in order.

The gallery's muscle and mechanic data is also what `ExerciseIntensity.deriveMetabolics` reads to build each movement's physical profile, so a better-tagged exercise scores more accurately.

## Notes & roadmap hooks

- **Health Services is alpha.** [`HealthServicesHeartRateSource`](wear/src/main/kotlin/com/calistapp/wear/hr/HealthServicesHeartRateSource.kt) is the *only* file touching that API; everything else depends on the `HeartRateSource` interface.
- **Known gap:** a workout run on the watch with no phone connected isn't stored — the phone is the system of record. Standalone watch sessions would need Room on `:wear` plus sync-on-reconnect.
- **Plans aren't reusable yet.** `PlanDraftRepository` holds the workout being built in memory; the plan is persisted with the session it ran, but there's no saved-workout library. That'd slot in behind the same interface.
- **Next:** cross-session progress analysis (`InsightsRepository.analyzeProgress` is already stubbed), and per-exercise progression tracking now that reps are recorded.
- The default calorie config (`CalorieEngine.Config`) and the correction bounds (`ExerciseIntensity`) are tunable constants.
