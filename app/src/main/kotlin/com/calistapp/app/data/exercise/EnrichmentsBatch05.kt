package com.calistapp.app.data.exercise

/** Hand-authored coaching overlays, batch 05 (Decline Crunch … Dumbbell Scaption). */
internal val enrichmentBatch05 = listOf(
    ExerciseEnrichment(
        id = "Decline_Crunch",
        overview = "A crunch on a decline bench, increasing the range and resistance on the upper abs. A step up in difficulty from a floor crunch.",
        commonMistakes = listOf(
            "Anchoring the feet and pulling with the hip flexors.",
            "Yanking on the neck to rise.",
        ),
        tips = listOf(
            "Curl the ribs toward the pelvis using the abs, not by hinging at the hips.",
            "Control the descent for constant tension.",
        ),
        problematicAreas = listOf("Neck", "Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Decline_Dumbbell_Bench_Press",
        overview = "A dumbbell press on a decline, targeting the lower chest with a big range and independent-arm work. Often easier on the shoulders than flat pressing.",
        commonMistakes = listOf(
            "Flaring the elbows to 90°.",
            "Letting the dumbbells drift and lose control at the bottom.",
        ),
        tips = listOf(
            "Lower with tucked elbows to the lower chest and press up over the shoulders.",
            "Have a partner hand you the dumbbells on a decline.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Decline_Dumbbell_Flyes",
        overview = "A dumbbell flye on a decline, stretching and isolating the lower chest. A good accessory for chest shape and a strong squeeze.",
        commonMistakes = listOf(
            "Bending the elbows into a press.",
            "Using too much weight and overstretching the shoulders.",
        ),
        tips = listOf(
            "Keep a fixed slight elbow bend and open the arms to a comfortable stretch.",
            "Squeeze the chest bringing the dumbbells together.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Decline_Dumbbell_Triceps_Extension",
        overview = "A lying triceps extension on a decline with dumbbells, keeping tension on the triceps and allowing a neutral grip that's easy on the elbows.",
        commonMistakes = listOf(
            "Flaring the elbows out.",
            "Moving the upper arms instead of just the forearms.",
        ),
        tips = listOf(
            "Fix the elbows pointing up and lower toward the ears/forehead.",
            "Extend fully and squeeze the triceps.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Decline_EZ_Bar_Triceps_Extension",
        overview = "A decline skull crusher with an EZ bar. The decline keeps constant tension on the triceps and the angled bar is friendlier to the wrists.",
        commonMistakes = listOf(
            "Letting the elbows flare and drift.",
            "Bouncing at the bottom near the head.",
        ),
        tips = listOf(
            "Keep the elbows fixed and lower under control, then extend fully.",
            "Use a spotter and a manageable weight.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Decline_Oblique_Crunch",
        overview = "A crunch with a twist on a decline bench, targeting the obliques with added range and resistance. A solid intermediate oblique exercise.",
        commonMistakes = listOf(
            "Pulling on the head to rotate.",
            "Using momentum instead of a controlled twist.",
        ),
        tips = listOf(
            "Crunch up and rotate the shoulder toward the opposite knee.",
            "Move slowly and feel the obliques work.",
        ),
        problematicAreas = listOf("Neck", "Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Decline_Push-Up",
        overview = "A push-up with the feet elevated, shifting more load onto the upper chest and shoulders. A great bodyweight progression from the standard push-up.",
        commonMistakes = listOf(
            "Letting the hips pike up.",
            "Elevating the feet so high it becomes a near-handstand press (unless intended).",
        ),
        tips = listOf(
            "Keep the body rigid and lower the chest under control.",
            "The higher the feet, the harder — progress gradually.",
        ),
        problematicAreas = listOf("Wrists", "Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Decline_Reverse_Crunch",
        overview = "A reverse crunch on a decline, curling the hips up against gravity. Loads the lower abs harder than the floor version while sparing the neck.",
        commonMistakes = listOf(
            "Swinging the legs for momentum.",
            "Arching the lower back at the bottom.",
        ),
        tips = listOf(
            "Curl the pelvis up toward the ribs and lower slowly.",
            "Grip the bench above your head for a stable anchor.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Decline_Smith_Press",
        overview = "A decline bench press in a Smith machine. The fixed bar path makes it stable and easy to overload the lower chest, though it removes the stabilizer demand.",
        commonMistakes = listOf(
            "Bouncing the bar off the chest.",
            "Relying on the machine and losing upper-back tightness.",
        ),
        tips = listOf(
            "Set the bench so the bar lands on the lower chest, and keep the elbows tucked.",
            "Control the tempo since the machine won't punish sloppiness immediately.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Deficit_Deadlift",
        overview = "A deadlift standing on a plate/block, increasing the range off the floor. Builds strength and speed off the ground and hammers the lower back and legs.",
        commonMistakes = listOf(
            "Rounding the lower back to reach the extra depth.",
            "Using a deficit so large your positions break down.",
        ),
        tips = listOf(
            "Start with a small deficit (1–2 inches) and keep a flat back.",
            "Brace hard — the longer range increases the demand off the floor.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Depth_Jump_Leap",
        overview = "A reactive plyometric: step off a box, land, and immediately leap. Trains the stretch-shortening cycle for explosive power. High-impact and advanced.",
        commonMistakes = listOf(
            "Spending too long on the ground (should be a quick rebound).",
            "Landing with knees caving or on stiff legs.",
        ),
        tips = listOf(
            "Minimize ground-contact time and land softly then explode.",
            "Use a modest box height and keep volume low; skip if you have knee issues.",
        ),
        problematicAreas = listOf("Knees", "Ankles"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Dip_Machine",
        overview = "A machine dip that trains the triceps and chest through a guided path with easy loading. A good option for beginners or for adding volume safely.",
        commonMistakes = listOf(
            "Going deeper than the shoulders comfortably allow.",
            "Using momentum instead of controlled reps.",
        ),
        tips = listOf(
            "Lower to a comfortable depth and press to lockout.",
            "Lean forward slightly for more chest, stay upright for more triceps.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Dips_-_Chest_Version",
        overview = "Parallel-bar dips with a forward lean to emphasize the chest. An excellent upper-body pressing builder — the 'squat of the upper body.'",
        commonMistakes = listOf(
            "Going too deep and overstretching the shoulder.",
            "Staying too upright when the goal is chest emphasis.",
        ),
        tips = listOf(
            "Lean the torso forward and let the elbows flare slightly to hit the chest.",
            "Lower to about upper-arms-parallel and press up under control.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Donkey_Calf_Raises",
        overview = "Calf raises bent forward at the hips (with weight on the lower back/hips), which puts the calves on a great stretch. An old-school favorite for calf growth.",
        commonMistakes = listOf(
            "Using a short, bouncy range.",
            "Rounding the lower back under the load.",
        ),
        tips = listOf(
            "Hinge at the hips with a flat back and drive through a full stretch-to-contraction range.",
            "Pause and squeeze at the top.",
        ),
        problematicAreas = listOf("Ankles", "Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Double_Kettlebell_Alternating_Hang_Clean",
        overview = "Cleaning two kettlebells from the hang, alternating arms. Builds hip power, grip and conditioning with a strong coordination demand.",
        commonMistakes = listOf(
            "Curling the bells with the arms instead of driving with the hips.",
            "Letting the bells crash onto the wrists.",
        ),
        tips = listOf(
            "Snap the hips and guide each bell into a soft rack.",
            "Keep the arms relaxed and the back flat on the hinge.",
        ),
        problematicAreas = listOf("Lower back", "Wrists"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Double_Kettlebell_Jerk",
        overview = "An overhead jerk with two kettlebells, using leg drive and a dip-under to lock heavy bells overhead. A powerful full-body strength-and-power builder.",
        commonMistakes = listOf(
            "Pressing with the arms instead of driving with the legs and dropping under.",
            "Losing the rack position or overarching the back.",
        ),
        tips = listOf(
            "Dip, drive, and punch under the bells into a locked-out overhead position.",
            "Master the rack and the push press before jerking.",
        ),
        problematicAreas = listOf("Shoulders", "Lower back", "Wrists"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Double_Kettlebell_Push_Press",
        overview = "An overhead press of two kettlebells using leg drive to start the bells moving. Lets you handle more load than a strict press and builds power.",
        commonMistakes = listOf(
            "Overarching the lower back on the drive.",
            "Turning the leg dip into a full squat.",
        ),
        tips = listOf(
            "Use a short, sharp dip-and-drive, then press to lockout.",
            "Keep the ribs down and glutes tight overhead.",
        ),
        problematicAreas = listOf("Shoulders", "Lower back"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Double_Kettlebell_Snatch",
        overview = "Snatching two kettlebells from between the legs to overhead in one motion. A brutal, technical power and conditioning lift. Expert-level.",
        commonMistakes = listOf(
            "Muscling with the arms instead of a violent hip snap.",
            "Banging the bells onto the wrists at the top.",
        ),
        tips = listOf(
            "Explode the hips and guide the bells around the hands into a soft overhead lockout.",
            "Build single-bell snatch technique first.",
        ),
        problematicAreas = listOf("Shoulders", "Wrists", "Lower back"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Double_Kettlebell_Windmill",
        overview = "A windmill holding a kettlebell overhead and another at the hip, training the obliques, hips and shoulder stability through a big range. Technical.",
        commonMistakes = listOf(
            "Taking the eyes off the overhead bell.",
            "Bending the supporting knee instead of hinging the hips.",
        ),
        tips = listOf(
            "Push the hip out and hinge, keeping the top arm locked and watched.",
            "Start light and earn the mobility before loading.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Double_Leg_Butt_Kick",
        overview = "A plyometric jump tucking both heels toward the glutes. A low-complexity power and warm-up drill for the legs.",
        commonMistakes = listOf(
            "Landing stiff-legged.",
            "Losing posture as you fatigue.",
        ),
        tips = listOf(
            "Jump and snap the heels up, landing softly.",
            "Keep reps crisp and controlled.",
        ),
        problematicAreas = listOf("Knees", "Ankles"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Downward_Facing_Balance",
        overview = "A single-leg balance/extension over a stability ball training the glutes, hamstrings and core stabilizers. A rehab/stability exercise more than a strength one.",
        commonMistakes = listOf(
            "Overarching the lower back to lift the leg higher.",
            "Rushing and losing balance on the ball.",
        ),
        tips = listOf(
            "Keep the hips level and extend the leg only to body height.",
            "Move slowly and brace the core throughout.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Drag_Curl",
        overview = "A barbell curl where you 'drag' the bar up the body by pulling the elbows back, biasing the outer/long head of the biceps. A strict variation for the biceps.",
        commonMistakes = listOf(
            "Letting the bar drift away from the torso.",
            "Swinging instead of dragging under control.",
        ),
        tips = listOf(
            "Pull the elbows back and up so the bar stays against the body.",
            "Use lighter weight than a standard curl — the leverage is harder.",
        ),
        problematicAreas = listOf("Elbows", "Wrists"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Drop_Push",
        overview = "A plyometric push-up dropping from elevated hands to the floor and catching explosively. Builds upper-body reactive power. Advanced and joint-demanding.",
        commonMistakes = listOf(
            "Catching on stiff, locked arms.",
            "Doing them fatigued, which wrecks the landing.",
        ),
        tips = listOf(
            "Absorb the drop by bending the elbows, then push back up.",
            "Master clapping push-ups first and keep reps low.",
        ),
        problematicAreas = listOf("Wrists", "Shoulders", "Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Alternate_Bicep_Curl",
        overview = "The classic alternating dumbbell curl. A simple, effective biceps builder that lets each arm work independently and rest between reps.",
        commonMistakes = listOf(
            "Swinging the torso to lift.",
            "Letting the elbows drift forward.",
        ),
        tips = listOf(
            "Curl one arm at a time, keeping the elbows pinned to the sides.",
            "Supinate (turn the palm up) and squeeze at the top.",
        ),
        problematicAreas = listOf("Elbows", "Wrists"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Bench_Press",
        overview = "The flat dumbbell bench press — a chest-building staple that allows a deeper stretch and more natural path than a barbell, while balancing each side.",
        commonMistakes = listOf(
            "Flaring the elbows straight out to 90°.",
            "Clashing the dumbbells at the top or bouncing at the bottom.",
        ),
        tips = listOf(
            "Retract the shoulder blades and lower with tucked elbows to a deep stretch.",
            "Press up and slightly together over the chest.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Bench_Press_with_Neutral_Grip",
        overview = "A dumbbell bench press with palms facing each other. The neutral grip is easier on the shoulders and adds triceps involvement — great for cranky shoulders.",
        commonMistakes = listOf(
            "Letting the dumbbells drift apart.",
            "Over-tucking or over-flaring the elbows.",
        ),
        tips = listOf(
            "Keep the palms facing in and elbows at a comfortable tuck.",
            "Press the dumbbells together lightly for extra chest/triceps tension.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Bicep_Curl",
        overview = "The standard two-arm dumbbell biceps curl. A fundamental arm builder that's simple to load and progress.",
        commonMistakes = listOf(
            "Swinging the body to heave the weights.",
            "Not fully straightening the arms at the bottom.",
        ),
        tips = listOf(
            "Keep the elbows pinned and curl with a full range.",
            "Squeeze at the top and lower slowly.",
        ),
        problematicAreas = listOf("Elbows", "Wrists"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Clean",
        overview = "An explosive clean with a dumbbell from the floor/hang to the shoulder. Builds hip power and coordination with less technical demand than a barbell clean.",
        commonMistakes = listOf(
            "Pulling early with the arm instead of exploding with the hips.",
            "Rounding the back on the pickup.",
        ),
        tips = listOf(
            "Snap the hips and pull under, catching the dumbbell softly at the shoulder.",
            "Keep a flat back and the dumbbell close.",
        ),
        problematicAreas = listOf("Lower back", "Wrists"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Floor_Press",
        overview = "A dumbbell press lying on the floor, limiting shoulder range for a triceps-and-lockout-focused press that's easy on the shoulders. Good for pressing around injuries.",
        commonMistakes = listOf(
            "Bouncing the elbows off the floor.",
            "Flaring the elbows wide.",
        ),
        tips = listOf(
            "Touch the elbows lightly to the floor, pause, then press.",
            "Keep the elbows tucked to protect the shoulders.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Flyes",
        overview = "The classic flat dumbbell flye, isolating the chest with a big stretch. A staple accessory for chest width and a strong squeeze.",
        commonMistakes = listOf(
            "Bending the elbows to press instead of arcing the arms.",
            "Going too heavy and overstretching the shoulders.",
        ),
        tips = listOf(
            "Keep a fixed slight elbow bend and open the arms to a comfortable stretch.",
            "Squeeze the chest bringing the dumbbells together over the chest.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Incline_Row",
        overview = "A chest-supported row lying face-down on an incline bench, hitting the mid-back and lats with zero lower-back strain. Excellent strict back work.",
        commonMistakes = listOf(
            "Jerking off the bench and using body English.",
            "Shrugging instead of driving the elbows back.",
        ),
        tips = listOf(
            "Keep the chest on the pad and row the dumbbells to the hips/ribs.",
            "Squeeze the shoulder blades and control the negative.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Incline_Shoulder_Raise",
        overview = "A protraction/serratus-focused raise on an incline with dumbbells. A niche accessory for shoulder-blade control and upper-chest tie-in.",
        commonMistakes = listOf(
            "Bending the elbows into a press.",
            "Using too much weight for the small range.",
        ),
        tips = listOf(
            "Keep the arms long and push the shoulders up/forward.",
            "Move slowly and lightly.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Lunges",
        overview = "Lunges holding dumbbells at the sides. A great single-leg builder that's simple to load and easier to balance than a barbell on the back.",
        commonMistakes = listOf(
            "Front knee caving in or the torso pitching forward.",
            "Too short a step, grinding the knee.",
        ),
        tips = listOf(
            "Take a full stride, sink both knees to ~90°, and drive through the front heel.",
            "Keep the torso tall and core braced.",
        ),
        problematicAreas = listOf("Knees"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Lying_One-Arm_Rear_Lateral_Raise",
        overview = "A strict single-arm rear-delt raise lying on your side, isolating one rear delt at a time. Great for shoulder balance and posture.",
        commonMistakes = listOf(
            "Using momentum to swing the dumbbell up.",
            "Rotating the torso to cheat.",
        ),
        tips = listOf(
            "Raise the dumbbell in a controlled arc leading with the elbow.",
            "Use light weight and pause at the top.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Lying_Pronation",
        overview = "A forearm-rotation exercise turning the palm down against a dumbbell's offset weight. Rehab/prehab work for the forearm and elbow (e.g., golfer's/tennis elbow).",
        commonMistakes = listOf(
            "Moving the whole arm instead of just rotating the forearm.",
            "Loading it too heavy.",
        ),
        tips = listOf(
            "Rest the forearm on a bench and rotate only at the wrist/forearm.",
            "Use light weight and high, controlled reps.",
        ),
        problematicAreas = listOf("Wrists", "Elbows"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Lying_Rear_Lateral_Raise",
        overview = "A rear-delt raise lying face-down on a bench, eliminating cheating to strictly isolate the rear delts. Excellent for balanced shoulders and posture.",
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
        id = "Dumbbell_Lying_Supination",
        overview = "A forearm-rotation exercise turning the palm up against a dumbbell's offset weight. Prehab for the forearm/elbow and builds the supinators used in curls.",
        commonMistakes = listOf(
            "Rotating the whole arm instead of just the forearm.",
            "Using too much weight.",
        ),
        tips = listOf(
            "Brace the forearm and rotate the palm from down to up slowly.",
            "Keep the reps light and controlled.",
        ),
        problematicAreas = listOf("Wrists", "Elbows"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_One-Arm_Shoulder_Press",
        overview = "A single-arm overhead dumbbell press. The unilateral load builds shoulder strength while heavily challenging core anti-lateral-flexion stability.",
        commonMistakes = listOf(
            "Leaning away from the pressing arm.",
            "Overarching the lower back at lockout.",
        ),
        tips = listOf(
            "Brace the core and glutes and keep the ribs down.",
            "Press straight up to a full lockout over the shoulder.",
        ),
        problematicAreas = listOf("Shoulders", "Lower back"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_One-Arm_Triceps_Extension",
        overview = "A seated/standing single-arm overhead triceps extension, stretching the long head of the triceps. Good for triceps size and ironing out imbalances.",
        commonMistakes = listOf(
            "Letting the elbow flare out to the side.",
            "Overarching the back to press overhead.",
        ),
        tips = listOf(
            "Keep the elbow pointed up and close to the head; lower for a full stretch.",
            "Extend fully and squeeze the triceps.",
        ),
        problematicAreas = listOf("Elbows", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_One-Arm_Upright_Row",
        overview = "A single-arm upright row with a dumbbell for the side delts and traps. Effective, but the upright-row pattern can irritate some shoulders — keep the range moderate.",
        commonMistakes = listOf(
            "Pulling the elbow too high, pinching the shoulder.",
            "Swinging the body to lift.",
        ),
        tips = listOf(
            "Row the elbow to about shoulder height, no higher.",
            "If you feel any pinching, switch to lateral raises.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Prone_Incline_Curl",
        overview = "A biceps curl lying face-down on an incline bench, which fixes the upper arms and forces very strict curling. Great for a strong contraction with no cheating.",
        commonMistakes = listOf(
            "Lifting the chest off the pad to help.",
            "Swinging the dumbbells up.",
        ),
        tips = listOf(
            "Let the arms hang straight down and curl purely with the biceps.",
            "Squeeze at the top and lower slowly.",
        ),
        problematicAreas = listOf("Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Raise",
        overview = "A front raise with dumbbells for the front delts. A simple shoulder accessory; keep it light and strict to avoid swinging.",
        commonMistakes = listOf(
            "Swinging the weights up with the lower back.",
            "Raising far above shoulder height with heavy weight.",
        ),
        tips = listOf(
            "Raise to about shoulder height with a slight elbow bend.",
            "Control the descent and avoid momentum.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Rear_Lunge",
        overview = "A reverse lunge holding dumbbells, stepping back into the lunge. Easier on the knees than forward lunges and great for glutes and single-leg strength.",
        commonMistakes = listOf(
            "Letting the front knee cave or drift far past the toes.",
            "Leaning the torso too far forward.",
        ),
        tips = listOf(
            "Step straight back and lower until both knees make ~90°.",
            "Drive through the front heel to return, keeping the torso tall.",
        ),
        problematicAreas = listOf("Knees"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Dumbbell_Scaption",
        overview = "Raising dumbbells in the scapular plane (~30° forward of straight-side). A shoulder-friendly raise that builds the delts and supports rotator-cuff health.",
        commonMistakes = listOf(
            "Raising straight out to the side or straight in front instead of in between.",
            "Shrugging the traps or going too heavy.",
        ),
        tips = listOf(
            "Raise the dumbbells in a 'Y' about 30° in front, thumbs up.",
            "Use light weight and stop around shoulder height.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
)
