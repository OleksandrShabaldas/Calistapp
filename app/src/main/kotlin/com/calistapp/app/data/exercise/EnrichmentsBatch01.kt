package com.calistapp.app.data.exercise

/**
 * Hand-authored coaching overlays, batch 01 (exercises A…, alphabetical). Merged onto the dataset
 * base by [ExerciseSyncManager]. See [ExerciseEnrichment].
 */
internal val enrichmentBatch01 = listOf(
    ExerciseEnrichment(
        id = "3_4_Sit-Up",
        overview = "A partial-range sit-up that keeps constant tension on the abs by stopping short of the bottom. Good for beginners who lose tension at full lockout.",
        commonMistakes = listOf(
            "Lowering all the way and resting, killing the tension.",
            "Pulling on the neck to get up.",
            "Anchoring the feet and yanking with the hip flexors.",
        ),
        tips = listOf(
            "Stop about three-quarters down and go straight back up.",
            "Keep the movement slow and driven by the abs, not momentum.",
        ),
        problematicAreas = listOf("Lower back", "Neck"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "90_90_Hamstring",
        overview = "A gentle hamstring stretch performed lying on your back with the hip and knee at 90°. Great for improving hamstring flexibility without stressing the lower back.",
        commonMistakes = listOf(
            "Forcing the knee straight and bouncing.",
            "Lifting the lower back and hips off the floor.",
        ),
        tips = listOf(
            "Extend the knee only until you feel a mild stretch, then hold and breathe.",
            "Keep the non-working leg relaxed on the floor.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Ab_Crunch_Machine",
        overview = "A machine crunch that lets you load the abs with resistance and progress over time. Useful for adding measurable weight to ab training.",
        commonMistakes = listOf(
            "Pulling with the arms instead of crunching with the abs.",
            "Using so much weight that the hip flexors take over.",
            "Rushing the reps and bouncing out of the bottom.",
        ),
        tips = listOf(
            "Round the spine and shorten the distance between ribs and pelvis.",
            "Control the return — the negative builds the abs too.",
        ),
        problematicAreas = listOf("Neck", "Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Ab_Roller",
        overview = "An ab-wheel rollout, one of the most effective anti-extension core exercises there is. Builds serious strength through the whole front of the trunk.",
        commonMistakes = listOf(
            "Letting the lower back arch and hips sag as you extend.",
            "Rolling out further than you can control and collapsing.",
            "Pushing with the arms instead of bracing the core.",
        ),
        tips = listOf(
            "Tuck the pelvis and keep ribs down the entire roll.",
            "Start with a short range from the knees and extend it as you get stronger.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Adductor",
        overview = "Self-myofascial release for the inner-thigh adductors using a foam roll. Helps reduce tightness and improve hip mobility, especially before lower-body work.",
        commonMistakes = listOf(
            "Rolling too fast to actually release the tissue.",
            "Holding your breath and tensing up on tender spots.",
        ),
        tips = listOf(
            "Pause on tight spots for 20–30 seconds and breathe.",
            "Keep the pressure tolerable — it should be uncomfortable, not sharp.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Adductor_Groin",
        overview = "A static groin/adductor stretch (often a seated butterfly or wall variation) to open the hips and inner thighs. Handy for squat depth and lateral movement.",
        commonMistakes = listOf(
            "Bouncing the knees down instead of holding a steady stretch.",
            "Rounding the back to force more range.",
        ),
        tips = listOf(
            "Sit tall and hinge from the hips into the stretch.",
            "Hold each stretch 20–40 seconds and relax into it.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Advanced_Kettlebell_Windmill",
        overview = "A loaded overhead windmill that trains the obliques, hips and shoulder stability through a big range. Advanced — demands mobility and overhead control.",
        commonMistakes = listOf(
            "Taking your eyes off the overhead bell (it should stay locked out and watched).",
            "Bending the supporting knee instead of hinging the hips.",
            "Rounding the back at the bottom.",
        ),
        tips = listOf(
            "Push the hip out toward the loaded side and hinge, keeping the top arm vertical.",
            "Start light and grease the pattern before adding load.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Air_Bike",
        overview = "The bicycle crunch (a.k.a. air bike) — a highly effective ab exercise that hits the rectus abdominis and obliques together through a pedaling, rotating motion.",
        commonMistakes = listOf(
            "Yanking the head with the hands.",
            "Speeding up until it becomes flailing instead of controlled rotation.",
            "Not extending the legs fully or rotating from the ribcage.",
        ),
        tips = listOf(
            "Rotate the torso, bringing the shoulder (not the elbow) to the opposite knee.",
            "Slow down — a controlled tempo makes it far harder and more effective.",
        ),
        problematicAreas = listOf("Neck", "Lower back"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "All_Fours_Quad_Stretch",
        overview = "A kneeling quad and hip-flexor stretch that opens the front of the thigh and hip — useful after squatting or sitting all day.",
        commonMistakes = listOf(
            "Arching the lower back to feel a bigger stretch.",
            "Letting the front knee cave inward.",
        ),
        tips = listOf(
            "Tuck the pelvis under to target the hip flexor, not the lumbar spine.",
            "Hold steady and breathe rather than bouncing.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Alternate_Hammer_Curl",
        overview = "A neutral-grip dumbbell curl alternating arms. Builds the biceps and — thanks to the hammer grip — the brachialis and forearms, adding arm thickness.",
        commonMistakes = listOf(
            "Swinging the torso to heave the weight up.",
            "Letting the elbows drift forward instead of staying pinned to the sides.",
        ),
        tips = listOf(
            "Keep the palms facing each other throughout.",
            "Lower slowly and fully straighten the arm each rep.",
        ),
        problematicAreas = listOf("Elbows", "Wrists"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Alternate_Heel_Touchers",
        overview = "A lying oblique exercise where you reach side to side toward your heels. Simple and beginner-friendly for training the sides of the waist.",
        commonMistakes = listOf(
            "Just moving the arms without curling the shoulders off the floor.",
            "Straining the neck by tucking the chin hard.",
        ),
        tips = listOf(
            "Lift the shoulder blades slightly and crunch sideways to reach the heel.",
            "Keep a steady, controlled tempo.",
        ),
        problematicAreas = listOf("Neck", "Lower back"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Alternate_Incline_Dumbbell_Curl",
        overview = "A dumbbell curl done lying back on an incline bench, which puts the biceps on a deep stretch at the bottom for a strong stimulus — great for building the biceps peak.",
        commonMistakes = listOf(
            "Swinging the dumbbells up instead of curling from a dead stretch.",
            "Letting the shoulders roll forward off the bench.",
        ),
        tips = listOf(
            "Let the arms hang fully back to load the stretch, then curl.",
            "Keep the upper arms still — only the forearms move.",
        ),
        problematicAreas = listOf("Elbows", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Alternate_Leg_Diagonal_Bound",
        overview = "A plyometric bounding drill that develops explosive single-leg power and coordination for sprinting and jumping. Athletic, not a hypertrophy move.",
        commonMistakes = listOf(
            "Landing stiff-legged instead of absorbing softly.",
            "Sacrificing height/distance quality for more reps.",
        ),
        tips = listOf(
            "Drive up and out diagonally, swinging the arms for momentum.",
            "Keep reps low and crisp — this is about power, not fatigue.",
        ),
        problematicAreas = listOf("Knees", "Ankles"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Alternating_Cable_Shoulder_Press",
        overview = "A standing overhead press on cables, one arm at a time. Constant cable tension and the anti-rotation demand make it a solid shoulder and core builder.",
        commonMistakes = listOf(
            "Leaning back and turning it into an incline press.",
            "Letting the torso twist toward the pressing arm.",
        ),
        tips = listOf(
            "Brace the abs and glutes to resist rotation.",
            "Press to a full lockout and control the return.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Alternating_Deltoid_Raise",
        overview = "Alternating dumbbell raises to the front/side that isolate the deltoids. An accessory for shoulder shape and volume rather than a heavy strength move.",
        commonMistakes = listOf(
            "Swinging the weights up with body english.",
            "Shrugging the traps to lift instead of the delts.",
        ),
        tips = listOf(
            "Use light weight and raise to about shoulder height.",
            "Lead with the elbows and lower slowly.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Alternating_Floor_Press",
        overview = "A kettlebell press done lying on the floor, one arm at a time. The floor limits shoulder range, making it triceps- and lockout-focused and easier on the shoulders.",
        commonMistakes = listOf(
            "Bouncing the elbow off the floor.",
            "Letting the non-pressing bell drift and pull you off balance.",
        ),
        tips = listOf(
            "Touch the elbow lightly to the floor and press with control.",
            "Keep the resting arm braced to stay stable.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Alternating_Hang_Clean",
        overview = "An explosive kettlebell clean from the hang, alternating hands. Builds hip power, grip and conditioning — technical, so form matters more than load.",
        commonMistakes = listOf(
            "Curling the bell up with the arm instead of driving with the hips.",
            "Letting the bell crash onto the wrist in the rack.",
            "Rounding the lower back on the hinge.",
        ),
        tips = listOf(
            "Snap the hips and guide the bell around the hand into a soft rack.",
            "Keep the arm relaxed — power comes from the hip drive.",
        ),
        problematicAreas = listOf("Lower back", "Wrists"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Alternating_Kettlebell_Press",
        overview = "A standing overhead kettlebell press alternating arms. The offset bell and single-arm load build shoulder strength plus core stability.",
        commonMistakes = listOf(
            "Leaning away from the pressing arm.",
            "Flaring the elbow instead of pressing in a strong groove.",
        ),
        tips = listOf(
            "Brace hard and keep the ribs down as you press overhead.",
            "Lock out fully with the biceps near the ear.",
        ),
        problematicAreas = listOf("Shoulders", "Lower back"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Alternating_Kettlebell_Row",
        overview = "A bent-over kettlebell row alternating arms, training the lats and mid-back. The single-arm version lets you row through a big range and fight rotation.",
        commonMistakes = listOf(
            "Standing too upright so it becomes an upright row.",
            "Twisting the torso to yank the bell up.",
        ),
        tips = listOf(
            "Hinge to a flat-back position and row the bell to the hip.",
            "Keep the hips square and pull with the back, not just the arm.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Alternating_Renegade_Row",
        overview = "A plank on two kettlebells while rowing one arm at a time — a demanding anti-rotation core and back exercise. Expert-level; the plank is the hard part.",
        commonMistakes = listOf(
            "Letting the hips rock and rotate as you row.",
            "Setting the feet too narrow, making balance harder than needed.",
            "Rowing with a rounded, twisting torso.",
        ),
        tips = listOf(
            "Widen the feet for a stable base and keep the hips dead level.",
            "Row one bell while pushing hard through the other to stay square.",
        ),
        problematicAreas = listOf("Wrists", "Lower back", "Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Ankle_Circles",
        overview = "A simple ankle-mobility drill circling the foot through its full range. Good as a warm-up or for restoring ankle motion after injury or stiffness.",
        commonMistakes = listOf(
            "Rushing tiny circles instead of moving through the full range.",
            "Moving the whole leg rather than isolating the ankle.",
        ),
        tips = listOf(
            "Make the largest slow circles you can in both directions.",
            "Keep the shin still and move only the foot.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Ankle_On_The_Knee",
        overview = "A lying figure-four glute stretch (ankle crossed on the opposite knee). Relieves tight glutes and hips, useful for desk-bound people and after leg day.",
        commonMistakes = listOf(
            "Yanking the leg toward you and forcing the stretch.",
            "Lifting the head and tensing the neck.",
        ),
        tips = listOf(
            "Gently pull the thigh toward your chest until you feel the glute stretch.",
            "Keep the crossed ankle flexed to protect the knee.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Anterior_Tibialis-SMR",
        overview = "Self-myofascial release for the muscle on the front of the shin. Can help with shin tightness and 'shin splint' discomfort from running.",
        commonMistakes = listOf(
            "Rolling directly and hard on the shin bone itself.",
            "Going too fast to release anything.",
        ),
        tips = listOf(
            "Target the muscle just outside the shin bone, pausing on tender spots.",
            "Keep pressure moderate and breathe through it.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Anti-Gravity_Press",
        overview = "A standing barbell press variation for the shoulders and upper back. Builds overhead pressing strength with a strong postural component.",
        commonMistakes = listOf(
            "Overarching the lower back to press the bar up.",
            "Pressing around the face instead of moving the head back and bar up in a straight line.",
        ),
        tips = listOf(
            "Squeeze the glutes and brace the abs to protect the spine.",
            "Push the head 'through the window' once the bar clears it.",
        ),
        problematicAreas = listOf("Shoulders", "Lower back"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Arm_Circles",
        overview = "A shoulder warm-up circling the arms to raise temperature and mobilize the joint. Best used before pressing or overhead work, not as a strength exercise.",
        commonMistakes = listOf(
            "Making frantic tiny circles instead of controlled full ones.",
            "Shrugging the shoulders up toward the ears.",
        ),
        tips = listOf(
            "Start small and gradually widen the circles, both directions.",
            "Keep the neck relaxed and shoulders down.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Arnold_Dumbbell_Press",
        overview = "A dumbbell shoulder press that rotates the palms as you press, hitting all three deltoid heads through a large range. Great for well-rounded shoulder development.",
        commonMistakes = listOf(
            "Rotating too fast and losing control of the weights.",
            "Overarching the back to push heavy dumbbells up.",
            "Starting the rotation before the bells are moving.",
        ),
        tips = listOf(
            "Begin with palms facing you, rotating to palms-forward as you press.",
            "Use moderate weight — the rotation makes it harder than a normal press.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Around_The_Worlds",
        overview = "A dumbbell chest/shoulder exercise circling the weights around the body. A stretch-focused accessory; use light weight as the leverage is unforgiving.",
        commonMistakes = listOf(
            "Using too much weight and stressing the shoulder at the stretched position.",
            "Bending the elbows to cheat the arc.",
        ),
        tips = listOf(
            "Keep the arms nearly straight and move slowly through the circle.",
            "Start very light — the long lever puts big demand on the shoulder.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Atlas_Stone_Trainer",
        overview = "A trainer that mimics lifting an atlas stone, teaching the strongman stone-lift pattern. Builds full-body pulling and lifting strength from the floor.",
        commonMistakes = listOf(
            "Rounding and jerking the lower back instead of hugging and extending the hips.",
            "Losing the stone away from the body.",
        ),
        tips = listOf(
            "Keep the implement glued to your torso and drive with the hips and legs.",
            "Master the pattern light before loading it heavily.",
        ),
        problematicAreas = listOf("Lower back", "Biceps"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Atlas_Stones",
        overview = "The strongman classic: lifting a heavy round stone from the ground to a platform. A brutal full-body lift building total-body strength and grit. Expert-level.",
        commonMistakes = listOf(
            "Lifting with a rounded, unbraced spine.",
            "Trying to use arms instead of the hips and legs.",
            "Skipping tacky/technique and getting hurt on heavy stones.",
        ),
        tips = listOf(
            "Hug the stone tight, extend the hips to lap it, then stand and extend to the platform.",
            "Progress gradually — the spinal load is enormous.",
        ),
        problematicAreas = listOf("Lower back", "Biceps"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Axle_Deadlift",
        overview = "A deadlift with a thick, non-rotating axle bar. Same posterior-chain builder as a barbell deadlift but with a serious grip challenge from the fat bar.",
        commonMistakes = listOf(
            "Rounding the lower back to break the bar off the floor.",
            "Letting the grip fail and jerking the bar.",
        ),
        tips = listOf(
            "Keep a flat back, brace hard, and push the floor away.",
            "The thick bar taxes grip — train double-overhand to build it.",
        ),
        problematicAreas = listOf("Lower back", "Forearms"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Back_Flyes_-_With_Bands",
        overview = "A banded reverse fly for the rear delts and upper back. An easy-to-set-up accessory that improves posture and balances all the front-side pressing.",
        commonMistakes = listOf(
            "Using the arms/biceps instead of pulling with the rear delts.",
            "Shrugging the traps up to move the band.",
        ),
        tips = listOf(
            "Pull the band apart leading with the elbows, squeezing the shoulder blades.",
            "Keep a slight bend in the elbows and move slowly.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Backward_Drag",
        overview = "A sled dragged backward, loading the quads heavily with almost no eccentric. Excellent low-impact conditioning and knee-friendly quad work.",
        commonMistakes = listOf(
            "Leaning too far back and losing quad tension.",
            "Taking huge steps instead of short powerful ones.",
        ),
        tips = listOf(
            "Stay low with a slight forward lean and drive through the quads.",
            "Because there's no eccentric, it's great for recovery and building work capacity.",
        ),
        problematicAreas = listOf("Knees"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Backward_Medicine_Ball_Throw",
        overview = "An explosive overhead-backward med-ball throw for full-body power, especially the posterior chain and shoulders. A power/athletic drill.",
        commonMistakes = listOf(
            "Using only the arms instead of a full-body hip-driven throw.",
            "Throwing where the ball can bounce back at you.",
        ),
        tips = listOf(
            "Dip at the hips and explode up, releasing overhead and behind.",
            "Throw for max effort with full recovery between reps.",
        ),
        problematicAreas = listOf("Shoulders", "Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Balance_Board",
        overview = "Balancing on an unstable board to train ankle stability and proprioception. Useful for rehab and injury prevention rather than building strength.",
        commonMistakes = listOf(
            "Gripping with the toes and tensing up instead of relaxing into small corrections.",
            "Looking down at the feet the whole time.",
        ),
        tips = listOf(
            "Fix your gaze ahead and let the ankles make small adjustments.",
            "Progress to single-leg balance as you improve.",
        ),
        problematicAreas = listOf("Ankles"),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Ball_Leg_Curl",
        overview = "A hamstring curl using a stability ball, curling it toward you with the heels while bridging. A great equipment-light hamstring and glute exercise.",
        commonMistakes = listOf(
            "Letting the hips drop as you curl the ball in.",
            "Using momentum instead of controlled hamstring contraction.",
        ),
        tips = listOf(
            "Keep the hips lifted in a bridge throughout.",
            "Curl the heels toward you, pause, and extend slowly.",
        ),
        problematicAreas = listOf("Hamstrings", "Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Band_Assisted_Pull-Up",
        overview = "A pull-up with a resistance band under the foot or knee to offload part of your weight. The best-scaled way to train full-range pull-ups before you can do them unassisted.",
        commonMistakes = listOf(
            "Using such a thick band that it launches you and does the work.",
            "Bouncing out of the bottom on the band's rebound.",
        ),
        tips = listOf(
            "Pick a band that lets you grind out clean reps, and thin it out over time.",
            "Still pull to a full dead hang each rep.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Band_Good_Morning",
        overview = "A hip-hinge with a band around the neck/shoulders and under the feet. Teaches the hinge and trains the hamstrings, glutes and lower back with low spinal load.",
        commonMistakes = listOf(
            "Squatting down instead of hinging back at the hips.",
            "Rounding the lower back at the bottom.",
        ),
        tips = listOf(
            "Push the hips straight back and keep a flat back and soft knees.",
            "Great for grooving the hinge before barbell good mornings or deadlifts.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Band_Good_Morning_Pull_Through",
        overview = "A hinge/pull-through hybrid with a band that hammers the glutes and hamstrings. Low-back friendly and great for learning to drive the hips.",
        commonMistakes = listOf(
            "Turning it into a squat or a back extension.",
            "Using the arms to pull instead of snapping the hips.",
        ),
        tips = listOf(
            "Hinge back, then finish by squeezing the glutes to stand tall.",
            "Keep the arms relaxed — the hips do the work.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Band_Hip_Adductions",
        overview = "A banded inner-thigh (adductor) exercise pulling the leg across the midline. A simple accessory for hip strength and stability.",
        commonMistakes = listOf(
            "Rotating the hips/torso instead of moving just the leg.",
            "Using a band so heavy the movement gets sloppy.",
        ),
        tips = listOf(
            "Keep the working leg straight and pull it across in front, controlling the return.",
            "Stand tall and brace the core to isolate the adductor.",
        ),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Band_Pull_Apart",
        overview = "Pulling a band apart at chest height to strengthen the rear delts and mid-back. One of the best cheap 'prehab' moves for shoulder health and posture.",
        commonMistakes = listOf(
            "Bending the elbows to cheat the range.",
            "Shrugging the shoulders up during the pull.",
        ),
        tips = listOf(
            "Keep arms straight and squeeze the shoulder blades together.",
            "Do high reps daily — it's a great warm-up and posture fix.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Band_Skull_Crusher",
        overview = "A banded triceps extension mimicking the skull crusher. Constant tension and joint-friendly resistance make it a good triceps builder or finisher.",
        commonMistakes = listOf(
            "Letting the elbows flare and drift, turning it into a press.",
            "Moving the upper arms instead of just the forearms.",
        ),
        tips = listOf(
            "Keep the elbows fixed and pointed forward; extend from the forearms only.",
            "Squeeze the triceps hard at lockout.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Barbell_Ab_Rollout",
        overview = "A standing/kneeling rollout using a loaded barbell as the wheel. A brutal anti-extension core builder — one of the best for total trunk strength.",
        commonMistakes = listOf(
            "Letting the lower back sag into extension as you roll out.",
            "Rolling further than you can pull back from.",
        ),
        tips = listOf(
            "Brace hard, tuck the pelvis, and keep ribs down the whole way.",
            "Limit the range to what you can control, then extend it over time.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Barbell_Ab_Rollout_-_On_Knees",
        overview = "The kneeling version of the barbell rollout — still extremely demanding on the core, and a step toward standing rollouts. Expert-level anti-extension work.",
        commonMistakes = listOf(
            "Sagging the hips and hyperextending the lower back.",
            "Using the arms to pull back instead of the abs.",
        ),
        tips = listOf(
            "Keep a posterior pelvic tilt and move as one rigid unit.",
            "Only roll as far as you can return without the back arching.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Barbell_Bench_Press_-_Medium_Grip",
        overview = "The classic flat barbell bench press — the benchmark upper-body pressing lift for building the chest, shoulders and triceps and measuring pressing strength.",
        commonMistakes = listOf(
            "Flaring the elbows to 90°, stressing the shoulders.",
            "Bouncing the bar off the chest.",
            "Losing the arch and letting the shoulders round forward.",
        ),
        tips = listOf(
            "Retract and pin the shoulder blades, tuck the elbows to ~45–75°.",
            "Lower to the lower chest with control and drive the bar back over the shoulders.",
            "Always use a spotter or safeties when going heavy.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows", "Wrists"),
        efficiency = 5,
    ),
)
