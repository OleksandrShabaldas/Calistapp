# Dashboard redesign — the new home screen

Replaces the old dashboard (glass cards + one big central calorie ring, which read as a calorie
tracker) with the onyx/orange **"Focus" prototype** the user authored — an athletic home screen that
is the *face* of the app. Full type treatment (Space Grotesk + Archivo, app-wide) and every feature
the prototype shows. Nav is unchanged: the prototype's 5-slot bottom bar already matches
`FloatingNavBar` (Home · Exercises · center Start · History · Settings).

Prototype source: `~/Downloads/Focus Prototype.html` (a bundled artifact; rendered via a local HTTP
server to read it). Scope agreed with the user: **full type treatment + everything in one pass.**

## The screen, top to bottom

1. **Header** — `TUESDAY · SEP 2` (Flame, letter-spaced caps) + `Good morning, {name}.` (Space
   Grotesk display). Right: **streak pill** `🔥 12` (custom flame icon, tinted pill).
2. **This week** — `This week` + `1,240 kcal`. Seven bars (Mon–Sun), orange gradient height ∝ kcal
   that day, flat stub for no-data/future. Under each: day letter (today = Flame) and a **dot** —
   Flame = trained that day, gray = a workout is *planned* that day.
3. **Next Up card** — training photo (first exercise's frame), `NEXT UP` badge, workout name
   (`Push · Skill Work`), `48 min · 7 exercises · Intermediate`, glowing **Start workout** button.
4. **Steps + goal ring** — footsteps icon (custom) + `Steps` + count; conic ring `78% / OF 10K`
   (progress toward the day's energy goal — see math).
5. **Training recommendations** — two gauges: **Readiness** (`Train 84%`, from sleep + load) and
   **Conditions** (`Indoor 12°·rain`, from weather + air). Both AI-interpreted.

## Design tokens

- **Ambient**: warm radial wash `#1C140D → #0D0C0E → #0A0A0B` from top-centre (dashboard tunes the
  existing `AmbientHost` warmer).
- **Orange**: keep `Flame` app-wide; add a hotter *display* gradient `FlameHot #FF6A1A → #FF8A3D`
  for bars, the Start button, rings and the streak pill.
- **Cards**: flat `#131215` / white 4–9%, hairline border white ~6%, radius 20–24, soft drop shadow;
  orange glow only on the primary button.
- **Type**: Space Grotesk (display / headings / big numbers) + Archivo (body / labels), bundled
  variable TTFs, routed through `MaterialTheme.typography`. Playfair retired for real.

## The streak / goal math (`:core`, pure + tested)

The user sets a **daily step goal**; the app turns it into a **calorie goal** and credits workouts,
so a training day needs fewer steps.

- `perStepRate = recentDay.calories / recentDay.steps` — reuses FitPal's own per-day figure (its
  formula `steps × 0.04 × weight/70` *and* its user-set trim), no re-derivation. Fallback when no
  imported day yet: `0.04 × weightKg/70 × (1 − defaultTrim)`.
- `dailyTargetKcal = round(stepGoal × perStepRate)`.
- `earned(date) = stepCalories(date) + workoutKcal(date)` (workout kcal = Calistapp's HR-based total).
- `hit(date) = earned ≥ target`. **Streak** = consecutive hit days counting back from today; a
  not-yet-hit *today* does not break a streak earned through yesterday.
- Ring % = `earned(today) / target`.

## Data-model additions

- `TrainingGoals.dailyStepGoal` (default 8000, range 1000..50000) — new DataStore key in
  `ProfileRepository`.
- **Scheduling** (Room, migration 8→9):
  - `scheduled_workouts(id, savedWorkoutId, dayOfWeek 1..7)` — recurring weekly rules.
  - `schedule_overrides(id, weekStartMs, dayOfWeek, action MOVE|SKIP, movedToDay?)` — this-week
    reschedules. Planned-dot for a day = recurring rule for that weekday, minus SKIP/MOVE-out, plus
    MOVE-in. `ScheduleRepository` resolves a week to per-day planned workouts.
- Next Up = today's planned workout → else next upcoming planned → else most-recent `SavedWorkout`.

## AI recommendations contract

One Gemini call (THINKING tier), cached per-day, graceful fallback when inputs/AI unavailable:

- **Readiness** in: last-night sleep (h + quality), TRIMP last 7/28d + `TrainingLoad.ramp` band, days
  since last workout, weekly cadence. Out: `{score 0..100, label, reason}`.
- **Conditions** in: temp / apparent temp / weather_code / wind / precip / uv_index / pm2_5 / us_aqi.
  Out: `{label (Indoor|Outdoor|Outdoor · SPF…), detail ("12°·rain"), reason}`.

## New integrations

- **Health Connect** `connect-client:1.1.0` — READ `SleepSessionRecord`; degrades when not
  installed / denied.
- **Weather** — Open-Meteo (no key): `/v1/forecast` (current weather + UV) + `/v1/air-quality`
  (pm2_5, us_aqi, uv_index). Needs `ACCESS_COARSE_LOCATION`; degrades when denied.

## Phases

0. **Foundations** — fonts + tokens; `TrainingGoals.dailyStepGoal`; core `DailyEnergyGoal` + streak
   calc + tests.
1. **Data** — schedule Room + repo + migration; step-goal persistence; rebuild `DashboardViewModel`.
2. **Dashboard UI** — header/streak, week bars, Next Up, steps+ring, recommendations shell; custom
   flame + footstep icons; drop glass cards.
3. **Recommendations backend** — weather + Health Connect + AI repo wired into the widget.
4. **Scheduling UI** — assign recurring + reschedule this week; entry points (week strip + saved
   workout).
5. **Manifest / permissions / settings / build / screenshot.**
