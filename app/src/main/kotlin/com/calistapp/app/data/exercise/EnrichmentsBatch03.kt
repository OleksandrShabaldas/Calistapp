package com.calistapp.app.data.exercise

/** Hand-authored coaching overlays, batch 03 (Bicycling, Stationary … Calf Press). */
internal val enrichmentBatch03 = listOf(
    ExerciseEnrichment(
        id = "Bicycling_Stationary",
        overview = "Indoor stationary cycling for cardio and leg endurance. Convenient, low-impact, and easy to control intensity for steady state or intervals.",
        commonMistakes = listOf(
            "Saddle set too low, cramping the knees.",
            "Gripping the bars and hunching for long periods.",
            "Always pedaling at the same easy resistance.",
        ),
        tips = listOf(
            "Set saddle height so the knee is almost straight at the bottom of the stroke.",
            "Alternate steady rides with hard intervals to build fitness.",
        ),
        problematicAreas = listOf("Knees"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Board_Press",
        overview = "A bench press to a stack of boards, shortening the range to overload the lockout and triceps. A powerlifting tool for building the top of the bench.",
        commonMistakes = listOf(
            "Bouncing off the boards instead of a controlled touch-and-pause.",
            "Letting the upper back lose tightness with the heavier loads used.",
        ),
        tips = listOf(
            "Touch the boards under control, pause, then drive up.",
            "Pick a board height that targets your specific sticking point.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows", "Wrists"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Body_Tricep_Press",
        overview = "A bodyweight triceps extension (hands on a bar/edge, lowering the head past the hands). A no-equipment way to overload the triceps through a big range.",
        commonMistakes = listOf(
            "Letting the hips pike to turn it into a push-up.",
            "Flaring the elbows wide.",
        ),
        tips = listOf(
            "Keep the body straight and hinge only at the elbows.",
            "Raise or lower the bar height to adjust difficulty.",
        ),
        problematicAreas = listOf("Elbows", "Wrists"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Body-Up",
        overview = "A bodyweight triceps and core move pressing the torso up from the forearms to the hands. Builds triceps and shoulder stability with no equipment.",
        commonMistakes = listOf(
            "Sagging or piking the hips instead of a straight body.",
            "Rushing and losing control on the way down.",
        ),
        tips = listOf(
            "Press up one arm at a time from forearms to hands, keeping the core braced.",
            "Move slowly and controlled both directions.",
        ),
        problematicAreas = listOf("Elbows", "Wrists", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Bodyweight_Flyes",
        overview = "A suspended bodyweight chest flye (hands on a bar or straps, arms opening wide). A tough, equipment-light way to train the chest with a big stretch.",
        commonMistakes = listOf(
            "Opening the arms wider than the shoulders can safely handle.",
            "Bending the elbows to turn it into a press.",
        ),
        tips = listOf(
            "Keep a slight fixed elbow bend and control the stretch.",
            "Raise the anchor/hands to reduce the difficulty.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Bodyweight_Mid_Row",
        overview = "A horizontal bodyweight row (inverted row) hitting the mid-back and lats. One of the best beginner-to-intermediate pulling exercises and a pull-up builder.",
        commonMistakes = listOf(
            "Letting the hips sag so it becomes a partial rep.",
            "Shrugging instead of pulling the shoulder blades together.",
        ),
        tips = listOf(
            "Keep a straight line and pull the chest to the bar.",
            "Lower the bar (more horizontal) to make it harder.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Bosu_Ball_Cable_Crunch_With_Side_Bends",
        overview = "A cable crunch combined with side bends on a Bosu, working the front and sides of the core with added instability. A varied, moderate core exercise.",
        commonMistakes = listOf(
            "Pulling with the arms instead of crunching the abs.",
            "Losing balance and rushing the reps.",
        ),
        tips = listOf(
            "Crunch down with the abs, then add a controlled side bend.",
            "Keep the movement deliberate on the unstable surface.",
        ),
        problematicAreas = listOf("Lower back", "Neck"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Bottoms_Up",
        overview = "A lying hip raise/reverse-crunch variation lifting the hips straight up. A beginner-friendly lower-ab move that keeps the neck out of it.",
        commonMistakes = listOf(
            "Swinging the legs for momentum.",
            "Arching the lower back off the floor.",
        ),
        tips = listOf(
            "Push the feet toward the ceiling by curling the pelvis up.",
            "Lower slowly under control.",
        ),
        problematicAreas = listOf("Lower back", "Neck"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Bottoms-Up_Clean_From_The_Hang_Position",
        overview = "A kettlebell clean holding the bell upside-down (bottoms-up), demanding intense grip and shoulder stability. Excellent for wrist/grip and shoulder control.",
        commonMistakes = listOf(
            "Loose grip letting the bell tip over.",
            "Muscling with the arm instead of a hip-driven clean.",
        ),
        tips = listOf(
            "Crush the handle and keep the wrist straight and vertical.",
            "Use a light bell — stability, not load, is the point.",
        ),
        problematicAreas = listOf("Wrists", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Box_Jump_Multiple_Response",
        overview = "Repeated box jumps for reactive lower-body power and conditioning. Builds explosiveness — quality landings matter far more than height or reps.",
        commonMistakes = listOf(
            "Landing stiff or with the knees caving.",
            "Chasing a box height you can't land softly on.",
        ),
        tips = listOf(
            "Land softly in a quarter-squat and absorb the impact.",
            "Step down between reps to protect the joints.",
        ),
        problematicAreas = listOf("Knees", "Ankles"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Box_Skip",
        overview = "A skipping/bounding plyometric over or onto low boxes for coordination and elastic leg power. An athletic drill, not a strength exercise.",
        commonMistakes = listOf(
            "Landing heavily instead of springing off the ground.",
            "Losing rhythm and posture as you fatigue.",
        ),
        tips = listOf(
            "Drive the knees and stay tall, minimizing ground contact time.",
            "Keep volume low and crisp.",
        ),
        problematicAreas = listOf("Knees", "Ankles"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Box_Squat",
        overview = "A squat sitting back to a box at a set depth. Teaches sitting back, builds explosive strength out of the hole, and is a powerlifting staple.",
        commonMistakes = listOf(
            "Collapsing/relaxing onto the box and rounding the back.",
            "Rocking back to stand instead of staying braced.",
        ),
        tips = listOf(
            "Sit back under control, stay tight on the box, then drive up without bouncing.",
            "Set the box to your target depth (parallel or below).",
        ),
        problematicAreas = listOf("Lower back", "Knees"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Box_Squat_with_Bands",
        overview = "A box squat with bands adding tension toward the top. Develops explosive power and strength through the whole range — advanced accommodating resistance.",
        commonMistakes = listOf(
            "Slowing at the top instead of accelerating against the bands.",
            "Losing position on the box under the added tension.",
        ),
        tips = listOf(
            "Explode off the box and keep driving as band tension increases.",
            "Set the bands to give even, symmetrical tension.",
        ),
        problematicAreas = listOf("Lower back", "Knees"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Box_Squat_with_Chains",
        overview = "A box squat with chains that add weight as you stand. Builds power and top-end strength; an advanced tool for experienced lifters.",
        commonMistakes = listOf(
            "Decelerating near lockout instead of driving through the chain weight.",
            "Chains hung so they don't deload at the bottom.",
        ),
        tips = listOf(
            "Set chains to unload on the box and re-load as you rise.",
            "Focus on explosive intent out of the box.",
        ),
        problematicAreas = listOf("Lower back", "Knees"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Bradford_Rocky_Presses",
        overview = "A continuous-tension shoulder press moving the bar front-to-back over the head. Builds shoulder endurance and stability, but keep the load light.",
        commonMistakes = listOf(
            "Clunking the bar off the head/neck.",
            "Using heavy weight and overarching the back.",
        ),
        tips = listOf(
            "Press just over the head and lower behind, keeping constant tension.",
            "Stay light and controlled — the shoulders are in a vulnerable range.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Brachialis-SMR",
        overview = "Self-myofascial release for the brachialis (upper-arm muscle under the biceps). Can relieve elbow-flexor tightness from heavy curling or pulling.",
        commonMistakes = listOf(
            "Rolling too fast to release anything.",
            "Applying so much pressure it's painful rather than a firm release.",
        ),
        tips = listOf(
            "Pause on tender spots for 20–30 seconds and breathe.",
            "Keep the pressure firm but tolerable.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Butterfly",
        overview = "The pec-deck machine flye, isolating the chest with a fixed, stable path. Easy to learn and great for adding chest volume and a strong squeeze.",
        commonMistakes = listOf(
            "Setting the seat so the elbows go far behind the torso, straining the shoulder.",
            "Slamming the pads together with momentum.",
        ),
        tips = listOf(
            "Adjust the seat so the arms open only to a comfortable stretch.",
            "Squeeze the chest at the middle and control the return.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Butt-Ups",
        overview = "From a forearm plank, piking the hips up and back to work the abs and shoulders. A simple bodyweight core move with a dynamic component.",
        commonMistakes = listOf(
            "Barely lifting the hips instead of a full pike.",
            "Letting the lower back round harshly at the top.",
        ),
        tips = listOf(
            "Push the hips up and back, contracting the abs at the top.",
            "Lower back to a solid plank each rep.",
        ),
        problematicAreas = listOf("Shoulders", "Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Crossover",
        overview = "A standing cable flye from high pulleys, giving constant tension and a great chest squeeze at the middle. A go-to for chest shape and definition.",
        commonMistakes = listOf(
            "Turning it into a press by bending the elbows a lot.",
            "Using momentum and leaning too far forward.",
        ),
        tips = listOf(
            "Keep a slight fixed elbow bend and bring the hands together in front, squeezing the chest.",
            "Step forward for a stretch and control the return.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Crunch",
        overview = "A kneeling cable crunch that lets you load the abs with resistance for progressive overload. One of the best exercises for building thicker abs.",
        commonMistakes = listOf(
            "Bending at the hips instead of crunching the spine.",
            "Pulling with the arms rather than the abs.",
        ),
        tips = listOf(
            "Anchor the hands by the head and curl the ribs toward the pelvis.",
            "Keep the hips fixed so only the spine flexes.",
        ),
        problematicAreas = listOf("Lower back", "Neck"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Cable_Deadlifts",
        overview = "A hip-hinge deadlift pattern against a cable. Constant tension and a lighter spinal load make it a joint-friendly way to train the hinge and posterior chain.",
        commonMistakes = listOf(
            "Squatting down instead of hinging back at the hips.",
            "Rounding the lower back.",
        ),
        tips = listOf(
            "Push the hips back with a flat back, then drive them forward to stand.",
            "Good for learning the hinge before heavy barbell work.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Hammer_Curls_-_Rope_Attachment",
        overview = "A neutral-grip cable curl with a rope, keeping constant tension on the biceps and brachialis. Great for arm thickness with a smooth resistance curve.",
        commonMistakes = listOf(
            "Swinging the body to move the weight.",
            "Letting the elbows drift forward.",
        ),
        tips = listOf(
            "Keep palms facing in and elbows pinned; pull the rope ends apart at the top.",
            "Control the negative and get a full stretch.",
        ),
        problematicAreas = listOf("Elbows", "Wrists"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Hip_Adduction",
        overview = "A standing cable adduction pulling the leg across the body's midline. Isolates the inner-thigh adductors for hip strength and stability.",
        commonMistakes = listOf(
            "Twisting the torso instead of moving just the leg.",
            "Using too much weight and losing control.",
        ),
        tips = listOf(
            "Keep the working leg straight and pull it across in front of the standing leg.",
            "Brace the core and stand tall to isolate the adductor.",
        ),
        problematicAreas = listOf("Hips", "Groin"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Cable_Chest_Press",
        overview = "A standing chest press on cables, combining pressing strength with anti-extension core work. Constant tension and a free path make it shoulder-friendly.",
        commonMistakes = listOf(
            "Leaning too far forward and losing balance.",
            "Letting the torso rotate during single-arm reps.",
        ),
        tips = listOf(
            "Brace the core and press straight forward, bringing the hands together.",
            "Keep the ribs down and stand in a staggered stance for stability.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Incline_Pushdown",
        overview = "A straight-arm cable pushdown/pullover that isolates the lats. Great for building the mind-muscle connection to the lats and adding back detail.",
        commonMistakes = listOf(
            "Bending the elbows to turn it into a triceps pushdown.",
            "Using the lower back to swing the weight down.",
        ),
        tips = listOf(
            "Keep the arms nearly straight and pull down using the lats.",
            "Hinge slightly and keep the core braced.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Incline_Triceps_Extension",
        overview = "A cable triceps extension performed leaning forward, keeping constant tension through the range. A solid triceps isolation with a smooth resistance curve.",
        commonMistakes = listOf(
            "Letting the elbows drift and flare.",
            "Using the shoulders to help push.",
        ),
        tips = listOf(
            "Fix the elbows and extend from the forearms only.",
            "Squeeze the triceps at full extension.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Internal_Rotation",
        overview = "A rotator-cuff exercise rotating the arm inward against a cable. Prehab/rehab work for shoulder health rather than a strength or size builder.",
        commonMistakes = listOf(
            "Using the whole body to twist instead of rotating from the shoulder.",
            "Loading it heavy — the cuff prefers light, controlled work.",
        ),
        tips = listOf(
            "Keep the elbow pinned to the side at 90° and rotate the forearm in.",
            "Use light resistance and higher reps.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Iron_Cross",
        overview = "A cable chest exercise pulling both handles down and together in front. Trains the chest with constant tension and a strong contracted position.",
        commonMistakes = listOf(
            "Letting the shoulders roll forward under load.",
            "Bending the elbows excessively into a press.",
        ),
        tips = listOf(
            "Keep the chest up and a slight fixed elbow bend, squeezing the pecs at the bottom.",
            "Control the stretch at the top.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Cable_Judo_Flip",
        overview = "A dynamic rotational core exercise pulling a cable over the shoulder like a judo throw. Trains explosive rotation and the obliques.",
        commonMistakes = listOf(
            "Using only the arms instead of rotating through the hips and core.",
            "Jerking with a rounded, unbraced spine.",
        ),
        tips = listOf(
            "Rotate from the hips and trunk, keeping the arms mostly along for the ride.",
            "Brace the core and control the return.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Lying_Triceps_Extension",
        overview = "A lying cable skull-crusher that keeps tension on the triceps throughout — no dead spot at the top. A great triceps mass builder.",
        commonMistakes = listOf(
            "Flaring the elbows and letting them drift back.",
            "Turning it into a press by moving the upper arms.",
        ),
        tips = listOf(
            "Keep the elbows fixed and pointed up; extend from the forearms.",
            "Lower for a deep stretch behind the head, then extend fully.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_One_Arm_Tricep_Extension",
        overview = "A single-arm cable pushdown/extension isolating each triceps and ironing out side-to-side differences with constant tension.",
        commonMistakes = listOf(
            "Letting the elbow drift away from the side.",
            "Leaning into it to use bodyweight.",
        ),
        tips = listOf(
            "Pin the elbow and extend the forearm fully, squeezing at the bottom.",
            "Control the return and keep the torso still.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Cable_Preacher_Curl",
        overview = "A preacher curl on a cable, keeping constant tension on the biceps at the stretched, hardest position. Excellent for strict, peak-focused biceps work.",
        commonMistakes = listOf(
            "Coming off the pad or lifting the elbows.",
            "Bouncing out of the bottom stretch.",
        ),
        tips = listOf(
            "Keep the upper arms flat on the pad and curl only the forearms.",
            "Control the stretch — don't let the cable yank the arm straight.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Rear_Delt_Fly",
        overview = "A cable reverse fly isolating the rear delts with constant tension. One of the best moves for shoulder balance and better posture.",
        commonMistakes = listOf(
            "Using the arms/traps instead of the rear delts.",
            "Bending the elbows into a row.",
        ),
        tips = listOf(
            "Sweep the hands out and back leading with the elbows, squeezing the rear delts.",
            "Keep the movement slow and the weight light.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Reverse_Crunch",
        overview = "A reverse crunch against cable resistance, curling the pelvis up. Loads the lower abs progressively while sparing the neck.",
        commonMistakes = listOf(
            "Swinging the legs instead of curling the pelvis.",
            "Arching the lower back at the bottom.",
        ),
        tips = listOf(
            "Tilt the pelvis and bring the knees toward the chest against the cable.",
            "Lower slowly with control.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Rope_Overhead_Triceps_Extension",
        overview = "An overhead cable triceps extension with a rope, stretching the long head of the triceps under load. Great for overall triceps size.",
        commonMistakes = listOf(
            "Letting the elbows flare wide.",
            "Overarching the lower back to press overhead.",
        ),
        tips = listOf(
            "Keep the elbows close and pointing forward; extend and split the rope at the top.",
            "Get a full stretch behind the head each rep.",
        ),
        problematicAreas = listOf("Elbows", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Rope_Rear-Delt_Rows",
        overview = "A high-elbow cable row with a rope to the face/upper chest, hitting the rear delts and upper back. Similar to a face pull — excellent for shoulder health.",
        commonMistakes = listOf(
            "Rowing low to the belly (that's lats, not rear delts).",
            "Shrugging the traps up.",
        ),
        tips = listOf(
            "Pull with high, wide elbows toward the face and split the rope.",
            "Squeeze the rear delts and upper back at the end.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Russian_Twists",
        overview = "A standing/seated cable Russian twist training rotational core strength with constant tension. Good for building oblique strength and control.",
        commonMistakes = listOf(
            "Twisting only the arms instead of the trunk.",
            "Rounding and yanking with a loose core.",
        ),
        tips = listOf(
            "Rotate from the ribcage with braced abs, keeping the arms fairly fixed.",
            "Control the return rather than letting the cable pull you back.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Seated_Crunch",
        overview = "A seated cable crunch flexing the spine against resistance. A stable, easy-to-learn way to load the abs for progressive overload.",
        commonMistakes = listOf(
            "Bending at the hips instead of rounding the spine.",
            "Pulling with the arms.",
        ),
        tips = listOf(
            "Curl the ribs toward the pelvis and hold the contraction briefly.",
            "Keep the hips still so only the abs work.",
        ),
        problematicAreas = listOf("Lower back", "Neck"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Seated_Lateral_Raise",
        overview = "A seated cable lateral raise isolating the side delts with constant tension and no cheating. Great for building shoulder width.",
        commonMistakes = listOf(
            "Shrugging the traps to lift.",
            "Swinging or leaning to move heavier weight.",
        ),
        tips = listOf(
            "Lead with the elbow and raise to about shoulder height.",
            "Use light weight and a smooth, controlled tempo.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Cable_Shoulder_Press",
        overview = "An overhead press on cables giving constant tension and a natural pressing arc. Builds the shoulders while challenging core stability.",
        commonMistakes = listOf(
            "Leaning back into an incline press.",
            "Not locking out fully overhead.",
        ),
        tips = listOf(
            "Brace the core and press straight up to lockout.",
            "Control the descent against the cable.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Cable_Shrugs",
        overview = "A cable shrug for the upper traps, with steady tension throughout. A joint-friendly alternative to barbell shrugs.",
        commonMistakes = listOf(
            "Rolling the shoulders instead of shrugging straight up.",
            "Using momentum with a tiny range.",
        ),
        tips = listOf(
            "Shrug straight up toward the ears and pause at the top.",
            "Let the traps stretch fully at the bottom.",
        ),
        problematicAreas = listOf("Neck"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Cable_Wrist_Curl",
        overview = "A cable wrist curl isolating the forearm flexors. Builds forearm size and grip endurance with smooth, constant tension.",
        commonMistakes = listOf(
            "Moving the whole arm instead of just the wrists.",
            "Using a range so small it does little.",
        ),
        tips = listOf(
            "Anchor the forearms and curl only at the wrists, through a full range.",
            "Squeeze at the top and control the stretch.",
        ),
        problematicAreas = listOf("Wrists"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Calf_Press",
        overview = "A machine calf press (seated or via a plate-loaded machine) for building calf size with an easy-to-load, stable path.",
        commonMistakes = listOf(
            "Using a short, bouncy range.",
            "Not pausing at the top or stretching at the bottom.",
        ),
        tips = listOf(
            "Press to a full stretch, then rise onto the toes as high as possible.",
            "Pause and squeeze the calves at the top.",
        ),
        problematicAreas = listOf("Ankles"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Calf_Press_On_The_Leg_Press_Machine",
        overview = "Calf raises done on the leg-press sled, letting you load the calves heavily and safely through a big range.",
        commonMistakes = listOf(
            "Locking the knees hard to push (keep a soft bend).",
            "Using a tiny partial range.",
        ),
        tips = listOf(
            "Place the balls of the feet on the platform and press through a full stretch-to-contraction range.",
            "Keep the knees slightly bent and never let the sled slam.",
        ),
        problematicAreas = listOf("Ankles", "Knees"),
        efficiency = 3,
    ),
)
