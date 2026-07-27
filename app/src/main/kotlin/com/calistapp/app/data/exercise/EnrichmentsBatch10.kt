package com.calistapp.app.data.exercise

/** Hand-authored coaching overlays, batch 10 (Leverage Deadlift … Machine Bicep Curl). */
internal val enrichmentBatch10 = listOf(
    ExerciseEnrichment(
        id = "Leverage_Deadlift",
        overview = "A plate-loaded machine deadlift with a fixed path, letting you train the deadlift pattern and posterior chain with less technical demand and lower injury risk.",
        commonMistakes = listOf(
            "Rounding the lower back despite the machine's support.",
            "Yanking the handles instead of a smooth leg-driven pull.",
        ),
        tips = listOf(
            "Set a flat back and push the floor away, extending the hips and knees together.",
            "Great for beginners or for pushing close to failure safely.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Leverage_Decline_Chest_Press",
        overview = "A plate-loaded decline chest press machine, targeting the lower chest with a stable path. Easy to load and to push near failure safely.",
        commonMistakes = listOf(
            "Flaring the elbows to 90°.",
            "Using a partial range or bouncing at the bottom.",
        ),
        tips = listOf(
            "Keep the elbows moderately tucked and press through a full range.",
            "Control the negative since the machine won't punish sloppiness immediately.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Leverage_High_Row",
        overview = "A plate-loaded high row machine, hitting the mid-back and lats with a chest support that removes lower-back strain. Great for back thickness.",
        commonMistakes = listOf(
            "Shrugging instead of driving the elbows down and back.",
            "Cutting the range short.",
        ),
        tips = listOf(
            "Pull the handles to your torso, squeezing the shoulder blades.",
            "Let the arms extend fully for a stretch each rep.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Leverage_Chest_Press",
        overview = "A plate-loaded chest press machine with a fixed path. A beginner-friendly, joint-stable way to build the chest and press near failure without a spotter.",
        commonMistakes = listOf(
            "Letting the elbows flare wide.",
            "Using a tiny range or relying on momentum.",
        ),
        tips = listOf(
            "Retract the shoulder blades and press through a full range.",
            "Control the stretch at the bottom.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Leverage_Incline_Chest_Press",
        overview = "A plate-loaded incline press machine, biasing the upper chest with a stable, easy-to-load path. Good for beginners and for high-effort sets.",
        commonMistakes = listOf(
            "Flaring the elbows straight out.",
            "Shortening the range at the top or bottom.",
        ),
        tips = listOf(
            "Keep the elbows tucked and press to a full lockout over the upper chest.",
            "Control the descent for a good stretch.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Leverage_Iso_Row",
        overview = "A plate-loaded iso-lateral row machine, working each side of the back independently through a chest-supported path. Excellent for back thickness and fixing imbalances.",
        commonMistakes = listOf(
            "Twisting the torso to move more weight on one side.",
            "Shrugging instead of rowing with the back.",
        ),
        tips = listOf(
            "Keep the chest on the pad and drive each elbow back, squeezing the lat.",
            "Work each side evenly and control the negative.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Leverage_Shoulder_Press",
        overview = "A plate-loaded shoulder press machine for the delts and triceps. A stable, spotter-free way to build overhead pressing strength and volume.",
        commonMistakes = listOf(
            "Setting the seat so the handles start too low, straining the shoulders.",
            "Using a partial range.",
        ),
        tips = listOf(
            "Adjust the seat so the handles start near shoulder height and press to lockout.",
            "Keep the ribs down and core braced.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Leverage_Shrug",
        overview = "A plate-loaded shrug machine for the upper traps, letting you load them heavily with a stable path and no grip limitation from a bar.",
        commonMistakes = listOf(
            "Rolling the shoulders instead of shrugging straight up.",
            "Using a bouncy, short range.",
        ),
        tips = listOf(
            "Shrug straight up toward the ears and pause at the top.",
            "Let the traps stretch fully at the bottom.",
        ),
        problematicAreas = listOf("Neck"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Linear_3-Part_Start_Technique",
        overview = "A sprint-start drill breaking the acceleration start into parts. A technique drill for improving the first steps of a sprint.",
        commonMistakes = listOf(
            "Standing up too early instead of staying low.",
            "Over-striding on the first steps.",
        ),
        tips = listOf(
            "Stay low with a strong forward lean and drive the ground back.",
            "Practice each phase, then link them smoothly.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Linear_Acceleration_Wall_Drill",
        overview = "A wall drill drilling the sprint acceleration position and leg action. Teaches the forward-lean posture and powerful knee drive for faster starts.",
        commonMistakes = listOf(
            "Losing the straight line from head to heel.",
            "Letting the hips sag toward the wall.",
        ),
        tips = listOf(
            "Maintain a rigid forward lean and drive the knee up then the foot down/back.",
            "Keep the movements sharp and posture tall through the body.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Linear_Depth_Jump",
        overview = "A depth jump into a forward broad jump, training reactive horizontal power. An advanced plyometric — keep landings crisp and volume low.",
        commonMistakes = listOf(
            "Long, soft ground contacts instead of a fast rebound.",
            "Landing with the knees caving.",
        ),
        tips = listOf(
            "Drop, land, and immediately explode forward, minimizing ground time.",
            "Use a modest box height and keep reps low.",
        ),
        problematicAreas = listOf("Knees", "Ankles"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Log_Lift",
        overview = "A strongman log clean-and-press overhead. Builds huge full-body strength and overhead power with a thick, awkward implement. Technical and demanding.",
        commonMistakes = listOf(
            "Rounding the back cleaning the log to the shoulders.",
            "Pressing before bracing and setting the legs.",
        ),
        tips = listOf(
            "Lap the log on the thighs, roll it to the shoulders, then leg-drive it overhead.",
            "Brace the core hard and keep the ribs down at lockout.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders", "Wrists"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "London_Bridges",
        overview = "A challenging bodyweight back-and-grip exercise moving along/under a bar. Builds pulling strength, grip and shoulder control. Advanced.",
        commonMistakes = listOf(
            "Swinging out of control.",
            "Attempting it before the pulling and grip base is there.",
        ),
        tips = listOf(
            "Move deliberately, keeping the shoulders engaged and the core tight.",
            "Build strong pull-ups and grip first.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Looking_At_Ceiling",
        overview = "A gentle standing stretch leaning back and looking up to open the front of the body and neck. A light mobility/posture break.",
        commonMistakes = listOf(
            "Cranking the neck or lower back hard.",
            "Losing balance leaning back.",
        ),
        tips = listOf(
            "Ease back only to a comfortable range and support the lower back if needed.",
            "Breathe and keep the movement gentle.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Low_Cable_Crossover",
        overview = "A cable crossover from low pulleys, bringing the hands up and together to target the upper chest. Constant tension and a strong squeeze at the top.",
        commonMistakes = listOf(
            "Bending the elbows to press instead of arcing.",
            "Shrugging or leaning back to lift.",
        ),
        tips = listOf(
            "Keep a fixed slight elbow bend and sweep the hands up and together in front.",
            "Squeeze the upper chest at the top.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Low_Cable_Triceps_Extension",
        overview = "An overhead triceps extension from a low cable, stretching the long head under constant tension. A solid triceps builder.",
        commonMistakes = listOf(
            "Flaring the elbows wide.",
            "Overarching the back to press.",
        ),
        tips = listOf(
            "Keep the elbows in and pointed forward; extend fully behind the head.",
            "Brace the core and keep the ribs down.",
        ),
        problematicAreas = listOf("Elbows", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Low_Pulley_Row_To_Neck",
        overview = "A cable row pulled high toward the neck/face with high elbows, targeting the rear delts and upper back. A face-pull-style move for shoulder health.",
        commonMistakes = listOf(
            "Rowing low to the belly.",
            "Shrugging the traps up.",
        ),
        tips = listOf(
            "Pull toward the neck/face with the elbows high and wide.",
            "Squeeze the rear delts and upper back at the end.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Lower_Back_Curl",
        overview = "A gentle spinal-flexion movement/stretch curling the lower back. Used to mobilize and relieve the lower back rather than build strength.",
        commonMistakes = listOf(
            "Forcing the range aggressively.",
            "Holding the breath and tensing.",
        ),
        tips = listOf(
            "Round the lower back gently and control the movement.",
            "Keep it smooth and pain-free.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Lower_Back-SMR",
        overview = "Foam rolling around the lower back region. Use with care — roll the surrounding areas (glutes, upper back, lats) rather than grinding directly on the lumbar spine.",
        commonMistakes = listOf(
            "Rolling directly and hard on the lumbar spine.",
            "Extending the lower back over the roller under pressure.",
        ),
        tips = listOf(
            "Focus on the glutes and mid-back; keep the core lightly braced.",
            "Avoid the bony spine itself and keep pressure moderate.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Lunge_Pass_Through",
        overview = "A lunge while passing a kettlebell under the front leg. Combines single-leg strength with a core and coordination challenge.",
        commonMistakes = listOf(
            "Rounding the back to pass the bell.",
            "Letting the front knee cave.",
        ),
        tips = listOf(
            "Sink into the lunge and pass the bell under the front thigh, keeping the chest up.",
            "Move smoothly and keep the core braced.",
        ),
        problematicAreas = listOf("Knees", "Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Lunge_Sprint",
        overview = "An explosive alternating jumping-lunge/sprint drill for leg power and conditioning. High-tempo and demanding on the knees.",
        commonMistakes = listOf(
            "Landing with the knees caving or on stiff legs.",
            "Losing posture as fatigue sets in.",
        ),
        tips = listOf(
            "Drive the knees and land softly, absorbing into each lunge.",
            "Keep bursts short and crisp.",
        ),
        problematicAreas = listOf("Knees", "Ankles"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Lying_Bent_Leg_Groin",
        overview = "A partner-assisted or supported groin/adductor stretch performed lying down. A deep inner-thigh stretch for advanced flexibility work.",
        commonMistakes = listOf(
            "Forcing the range too aggressively.",
            "Tensing up instead of relaxing into it.",
        ),
        tips = listOf(
            "Ease into the stretch and hold, breathing steadily.",
            "Progress the range gradually over time.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Lying_Cable_Curl",
        overview = "A biceps curl performed lying on the floor with a low cable, keeping constant tension and removing body English. A strict, constant-tension curl.",
        commonMistakes = listOf(
            "Lifting the elbows off the floor.",
            "Using the shoulders to help.",
        ),
        tips = listOf(
            "Keep the upper arms down and curl only the forearms.",
            "Squeeze at the top and control the negative.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Lying_Cambered_Barbell_Row",
        overview = "A chest-supported row lying prone on a high bench with a cambered bar, allowing an extra-deep pull. Strictly builds the mid-back and lats.",
        commonMistakes = listOf(
            "Heaving off the bench with body English.",
            "Shrugging instead of driving the elbows back.",
        ),
        tips = listOf(
            "Keep the chest down and row the bar to the underside of the bench.",
            "Use the deep range and squeeze the back.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Lying_Close-Grip_Bar_Curl_On_High_Pulley",
        overview = "A biceps curl lying on the floor curling a bar from a high pulley overhead. A unique angle that keeps constant tension and peaks the biceps.",
        commonMistakes = listOf(
            "Moving the upper arms instead of just the forearms.",
            "Using momentum from the whole body.",
        ),
        tips = listOf(
            "Keep the upper arms fixed and curl the bar toward the forehead.",
            "Squeeze the biceps and control the return.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Lying_Close-Grip_Barbell_Triceps_Extension_Behind_The_Head",
        overview = "A lying triceps extension lowering the bar behind the head, maximizing the stretch on the long head of the triceps. Excellent for triceps size.",
        commonMistakes = listOf(
            "Flaring the elbows out.",
            "Moving the upper arms instead of just the forearms.",
        ),
        tips = listOf(
            "Angle the upper arms back and lower behind the head for a deep stretch.",
            "Keep the elbows fixed and extend fully.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Lying_Close-Grip_Barbell_Triceps_Press_To_Chin",
        overview = "A lying close-grip triceps press lowering the bar toward the chin (a JM-press style move). Builds the triceps with a pressing-extension hybrid.",
        commonMistakes = listOf(
            "Flaring the elbows out wide.",
            "Losing control near the face.",
        ),
        tips = listOf(
            "Keep the elbows forward and lower the bar toward the chin, then press.",
            "Use a manageable weight and a spotter.",
        ),
        problematicAreas = listOf("Elbows", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Lying_Crossover",
        overview = "A lying spinal-rotation stretch crossing one leg over the body. Opens the lower back, glutes and outer hip; a deeper mobility stretch.",
        commonMistakes = listOf(
            "Forcing the knee to the floor.",
            "Lifting the opposite shoulder off the ground.",
        ),
        tips = listOf(
            "Let the crossed leg fall gently and keep both shoulders down.",
            "Breathe into the stretch and hold.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Lying_Dumbbell_Tricep_Extension",
        overview = "A lying dumbbell triceps extension (skull crusher with dumbbells), allowing a neutral grip that's easy on the elbows. A great triceps builder.",
        commonMistakes = listOf(
            "Flaring the elbows out.",
            "Turning it into a press by moving the upper arms.",
        ),
        tips = listOf(
            "Keep the elbows fixed and pointed up; lower toward the ears or forehead.",
            "Extend fully and squeeze the triceps.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Lying_Face_Down_Plate_Neck_Resistance",
        overview = "A neck extension exercise lying face-down with a plate on the back of the head. Strengthens the back of the neck — valuable for contact athletes. Advanced.",
        commonMistakes = listOf(
            "Using too heavy a plate or jerky movement.",
            "Overextending the neck hard at the top.",
        ),
        tips = listOf(
            "Move through a small, controlled range with a light plate on a towel.",
            "Build load and range very gradually.",
        ),
        problematicAreas = listOf("Neck"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Lying_Face_Up_Plate_Neck_Resistance",
        overview = "A neck flexion exercise lying face-up with a plate on the forehead. Strengthens the front of the neck for stability and injury resilience. Advanced.",
        commonMistakes = listOf(
            "Using a plate too heavy for the small neck muscles.",
            "Yanking the chin up quickly.",
        ),
        tips = listOf(
            "Curl the chin toward the chest slowly with a light, padded plate.",
            "Keep the range small and controlled.",
        ),
        problematicAreas = listOf("Neck"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Lying_Glute",
        overview = "A deep lying glute stretch (figure-four pulled to the chest). Relieves tight glutes and hips; an advanced-range version of the standard glute stretch.",
        commonMistakes = listOf(
            "Yanking the leg in forcefully.",
            "Lifting the head and tensing the neck.",
        ),
        tips = listOf(
            "Draw the thigh toward the chest until you feel the glute stretch, and hold.",
            "Keep the crossed ankle flexed to protect the knee.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Lying_Hamstring",
        overview = "A deep supported hamstring stretch lying on the back with the leg raised (often with a strap). Improves hamstring flexibility with the back protected.",
        commonMistakes = listOf(
            "Bending the knee excessively to reach further.",
            "Lifting the hips and lower back off the floor.",
        ),
        tips = listOf(
            "Use a strap to draw the straight leg toward you until you feel a stretch.",
            "Keep the opposite leg down and hold.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Lying_High_Bench_Barbell_Curl",
        overview = "A barbell curl lying prone on a high bench, the arms hanging straight down. Removes all cheating for a very strict biceps contraction.",
        commonMistakes = listOf(
            "Lifting the chest off the bench to help.",
            "Swinging the bar up.",
        ),
        tips = listOf(
            "Let the arms hang and curl purely with the biceps.",
            "Squeeze at the top and lower slowly.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Lying_Leg_Curls",
        overview = "The prone machine leg curl, isolating the hamstrings through knee flexion. A staple for hamstring size and balancing quad-dominant training.",
        commonMistakes = listOf(
            "Lifting the hips off the pad to swing the weight.",
            "Using a partial range or momentum.",
        ),
        tips = listOf(
            "Keep the hips down and curl the heels toward the glutes, squeezing at the top.",
            "Control the negative and point/flex the toes to vary the feel.",
        ),
        problematicAreas = listOf("Hamstrings", "Knees"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Lying_Machine_Squat",
        overview = "A supine machine squat (similar to a leg press/hack squat), loading the quads and glutes with the back supported. A joint-stable way to train legs heavily.",
        commonMistakes = listOf(
            "Letting the lower back round at the bottom.",
            "Locking the knees hard at the top.",
        ),
        tips = listOf(
            "Lower to a comfortable depth without the hips tucking under.",
            "Keep a slight knee bend at the top and the whole foot planted.",
        ),
        problematicAreas = listOf("Knees", "Lower back"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Lying_One-Arm_Lateral_Raise",
        overview = "A single-arm lateral raise lying on your side, which loads the side delt strongly at the bottom and top. A strict, effective side-delt isolation.",
        commonMistakes = listOf(
            "Using momentum to swing the arm up.",
            "Rolling the torso to cheat.",
        ),
        tips = listOf(
            "Raise the dumbbell to about vertical, leading with control.",
            "Use light weight and pause at the top.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Lying_Prone_Quadriceps",
        overview = "A deep quad stretch lying face-down and pulling the heel to the glute. Stretches the quads and hip flexors; an advanced-range version.",
        commonMistakes = listOf(
            "Cranking the heel in and stressing the knee.",
            "Overarching the lower back.",
        ),
        tips = listOf(
            "Draw the heel toward the glute until you feel the quad stretch, keeping the hips down.",
            "Use a strap if you can't reach the foot, and keep the knee comfortable.",
        ),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Lying_Rear_Delt_Raise",
        overview = "A rear-delt raise lying face-down on a bench, strictly isolating the rear delts. Excellent for shoulder balance and posture.",
        commonMistakes = listOf(
            "Swinging or turning it into a row.",
            "Shrugging the traps to lift.",
        ),
        tips = listOf(
            "Raise the dumbbells out to the sides leading with the elbows.",
            "Use light weight and squeeze the rear delts at the top.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Lying_Supine_Dumbbell_Curl",
        overview = "A dumbbell curl lying face-up on a bench, the arms hanging down for a deep biceps stretch. A strict, stretch-focused biceps exercise.",
        commonMistakes = listOf(
            "Swinging the dumbbells up from the stretch.",
            "Lifting the elbows toward the ceiling.",
        ),
        tips = listOf(
            "Let the arms hang straight down and curl without moving the upper arms.",
            "Squeeze at the top and control the stretch.",
        ),
        problematicAreas = listOf("Elbows", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Lying_T-Bar_Row",
        overview = "A chest-supported T-bar row lying prone on an angled bench. Loads the mid-back heavily with zero lower-back strain — one of the best back-thickness exercises.",
        commonMistakes = listOf(
            "Heaving off the pad with the whole body.",
            "Shrugging instead of driving the elbows back.",
        ),
        tips = listOf(
            "Keep the chest on the pad and row the handles to your torso.",
            "Squeeze the shoulder blades and control the negative.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Lying_Triceps_Press",
        overview = "A lying triceps press/extension with an EZ bar, a classic triceps mass builder. The angled bar keeps the wrists comfortable.",
        commonMistakes = listOf(
            "Flaring the elbows and letting them drift back.",
            "Bouncing near the head.",
        ),
        tips = listOf(
            "Keep the elbows fixed and lower toward the forehead, then extend.",
            "Use a spotter with heavier weight.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Machine_Bench_Press",
        overview = "A seated machine bench press with a fixed path. Beginner-friendly and spotter-free, letting you build the chest and press near failure safely.",
        commonMistakes = listOf(
            "Setting the seat so the handles are too far back, straining the shoulders.",
            "Using a partial range or momentum.",
        ),
        tips = listOf(
            "Adjust the seat so the handles align with the mid-chest, and press through a full range.",
            "Keep the shoulder blades retracted and control the return.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Machine_Bicep_Curl",
        overview = "A machine biceps curl with a fixed path and pad support, isolating the biceps and making it easy to use strict form and drop sets.",
        commonMistakes = listOf(
            "Lifting the elbows off the pad.",
            "Using momentum to swing through reps.",
        ),
        tips = listOf(
            "Keep the upper arms on the pad and curl through a full range.",
            "Squeeze at the top and control the negative.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 3,
    ),
)
