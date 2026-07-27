package com.calistapp.app.data.exercise

/** Hand-authored coaching overlays, batch 02 (Barbell Curl … Bicycling). */
internal val enrichmentBatch02 = listOf(
    ExerciseEnrichment(
        id = "Barbell_Curl",
        overview = "The classic standing barbell biceps curl — lets you load the biceps heavier than dumbbells, making it a staple for building arm size and strength.",
        commonMistakes = listOf(
            "Swinging the torso and using the back to heave the bar.",
            "Letting the elbows drift forward instead of staying at the sides.",
            "Not fully straightening the arms at the bottom.",
        ),
        tips = listOf(
            "Keep the upper arms pinned and curl only the forearms.",
            "Lower under control for 2–3 seconds each rep.",
        ),
        problematicAreas = listOf("Elbows", "Wrists"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Barbell_Curls_Lying_Against_An_Incline",
        overview = "A barbell curl performed lying face-up on an incline, minimizing body English so the biceps do all the work. Great for strict, cheat-free curling.",
        commonMistakes = listOf(
            "Lifting the hips or shoulders off the bench to help.",
            "Bouncing at the bottom instead of a controlled stretch.",
        ),
        tips = listOf(
            "Let gravity hold you in place and curl purely with the arms.",
            "Use lighter weight than a standing curl — it's much stricter.",
        ),
        problematicAreas = listOf("Elbows", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Barbell_Deadlift",
        overview = "The king of posterior-chain lifts. Trains nearly the whole body — back, glutes, hamstrings, grip — and builds raw total-body strength like nothing else.",
        commonMistakes = listOf(
            "Rounding the lower back off the floor.",
            "Letting the hips shoot up first, turning it into a stiff-leg pull.",
            "Jerking the bar instead of pushing the floor away.",
        ),
        tips = listOf(
            "Set the back flat, take slack out of the bar, then drive with the legs and hips together.",
            "Keep the bar dragging against your legs the whole way up.",
            "Brace your core hard before every rep.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Barbell_Full_Squat",
        overview = "A deep barbell back squat below parallel. The premier lower-body strength and mass builder, training the quads, glutes and whole trunk.",
        commonMistakes = listOf(
            "Letting the knees cave inward.",
            "Rounding the lower back ('buttwink') at the bottom.",
            "Rising with the hips first and dumping the torso forward.",
        ),
        tips = listOf(
            "Brace hard, break at the hips and knees together, and drive the knees out.",
            "Only go as deep as you can keep a neutral spine — mobility improves over time.",
        ),
        problematicAreas = listOf("Knees", "Lower back"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Barbell_Glute_Bridge",
        overview = "A floor-based hip extension with a loaded barbell over the hips. A powerful, back-friendly glute builder and a simpler alternative to the hip thrust.",
        commonMistakes = listOf(
            "Overarching the lower back instead of finishing with the glutes.",
            "Pushing through the toes rather than the heels.",
        ),
        tips = listOf(
            "Tuck the ribs and posteriorly tilt the pelvis as you lock out.",
            "Pause and squeeze hard at the top of each rep. Use a pad for comfort.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Barbell_Guillotine_Bench_Press",
        overview = "A bench press variation lowering the bar to the neck to emphasize the upper chest. Effective but risky for the shoulders — only for experienced lifters with light loads.",
        commonMistakes = listOf(
            "Using heavy weight — the neck position is very unforgiving.",
            "Flaring the elbows to 90° and overstretching the shoulder.",
        ),
        tips = listOf(
            "Go light, controlled, and always with a spotter.",
            "If your shoulders feel any pinch, switch to a standard or incline press.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Barbell_Hack_Squat",
        overview = "A squat holding the barbell behind the legs, shifting emphasis onto the quads. An old-school quad builder, though the bar path can be awkward.",
        commonMistakes = listOf(
            "Letting the bar drift away from the legs.",
            "Rounding the back to reach the bar at the bottom.",
        ),
        tips = listOf(
            "Keep the bar close, dragging up the backs of the legs.",
            "Elevate the heels slightly to stay upright and hit the quads.",
        ),
        problematicAreas = listOf("Knees", "Lower back"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Barbell_Hip_Thrust",
        overview = "Shoulders on a bench, barbell over the hips, driving into a full hip extension. The single best exercise for building strong, powerful glutes.",
        commonMistakes = listOf(
            "Hyperextending the lower back instead of finishing with the glutes.",
            "Not reaching full hip extension at the top.",
            "Letting the chin and ribs flare up.",
        ),
        tips = listOf(
            "Keep the chin tucked and ribs down; finish by squeezing the glutes level with the torso.",
            "Drive through the heels and pause at lockout.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Barbell_Incline_Bench_Press_-_Medium_Grip",
        overview = "An incline barbell press targeting the upper chest and front delts. A key movement for building a fuller, more balanced chest.",
        commonMistakes = listOf(
            "Setting the bench too steep so it becomes a shoulder press.",
            "Flaring the elbows and bouncing the bar.",
        ),
        tips = listOf(
            "Use a moderate incline (~30°) and lower to the upper chest.",
            "Keep the shoulder blades retracted and elbows tucked.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows", "Wrists"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Barbell_Incline_Shoulder_Raise",
        overview = "A protraction/shrug-style movement on an incline for the serratus and upper chest/shoulders. A niche accessory for shoulder-blade control.",
        commonMistakes = listOf(
            "Bending the elbows and turning it into a press.",
            "Using too much weight and losing the small range.",
        ),
        tips = listOf(
            "Keep arms straight and push the shoulders up/forward.",
            "Move slowly — it's a small, precise movement.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Barbell_Lunge",
        overview = "A loaded lunge with a barbell on the back. Builds single-leg strength, balance and glute/quad size while exposing left-right differences.",
        commonMistakes = listOf(
            "Letting the front knee cave or shoot far past the toes.",
            "Leaning the torso too far forward.",
            "Taking too short a step and overloading the knee.",
        ),
        tips = listOf(
            "Step out enough that both knees make ~90°, and drive through the front heel.",
            "Keep the torso tall and core braced for balance.",
        ),
        problematicAreas = listOf("Knees"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Barbell_Rear_Delt_Row",
        overview = "A high-elbow barbell row targeting the rear delts and upper back. Excellent for posture and balancing heavy pressing.",
        commonMistakes = listOf(
            "Rowing to the belly (that hits lats, not rear delts).",
            "Using momentum and standing up out of the hinge.",
        ),
        tips = listOf(
            "Row the bar toward the chest/upper ribs with the elbows high and wide.",
            "Squeeze the shoulder blades and rear delts at the top.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Barbell_Rollout_from_Bench",
        overview = "An anti-extension core rollout using a barbell rolled out over a bench. Builds strong, stable abs; a stepping stone toward floor rollouts.",
        commonMistakes = listOf(
            "Sagging the lower back into extension.",
            "Rolling out past the point you can control.",
        ),
        tips = listOf(
            "Keep the pelvis tucked and ribs down throughout.",
            "Extend only as far as you can pull back without the hips dropping.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Barbell_Seated_Calf_Raise",
        overview = "A seated calf raise with a barbell across the knees. The bent-knee position biases the soleus, complementing standing calf work.",
        commonMistakes = listOf(
            "Using a tiny, bouncy range of motion.",
            "Not pausing at the top or stretching at the bottom.",
        ),
        tips = listOf(
            "Get a full stretch at the bottom and a hard squeeze at the top.",
            "Use a pad and control the tempo.",
        ),
        problematicAreas = listOf("Ankles"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Barbell_Shoulder_Press",
        overview = "The seated or standing barbell overhead press — a primary builder of shoulder and triceps strength and a benchmark of upper-body pressing power.",
        commonMistakes = listOf(
            "Overarching the lower back to press the bar.",
            "Pressing the bar forward instead of straight up over the shoulders.",
        ),
        tips = listOf(
            "Brace the core and glutes; move the head back to let the bar pass, then under it.",
            "Lock out with the bar over the mid-foot and ears.",
        ),
        problematicAreas = listOf("Shoulders", "Lower back"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Barbell_Shrug",
        overview = "A barbell shrug to build the upper traps. Simple and effective for neck/trap thickness when done with a full range and no bounce.",
        commonMistakes = listOf(
            "Rolling the shoulders (unnecessary and hard on the joint).",
            "Using momentum and barely moving the bar.",
        ),
        tips = listOf(
            "Shrug straight up toward the ears and pause at the top.",
            "Let the traps stretch fully at the bottom.",
        ),
        problematicAreas = listOf("Neck", "Wrists"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Barbell_Shrug_Behind_The_Back",
        overview = "A shrug with the barbell behind the body, which keeps the shoulders back and can better target the traps while encouraging good posture.",
        commonMistakes = listOf(
            "Leaning back to swing the bar up.",
            "Grinding the bar against the glutes instead of shrugging cleanly.",
        ),
        tips = listOf(
            "Stand tall, shrug straight up, and pause briefly at the top.",
            "Use straps if grip limits you, since the traps can handle a lot.",
        ),
        problematicAreas = listOf("Shoulders", "Wrists"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Barbell_Side_Bend",
        overview = "A standing lateral flexion with a barbell to train the obliques. Effective but be conservative with load to protect the spine.",
        commonMistakes = listOf(
            "Twisting or leaning forward instead of bending straight to the side.",
            "Using heavy weight that compresses the spine.",
        ),
        tips = listOf(
            "Bend directly sideways and return by contracting the opposite obliques.",
            "Keep the reps controlled and the weight moderate.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Barbell_Side_Split_Squat",
        overview = "A wide-stance lateral (Cossack-style) split squat with a barbell. Builds single-leg strength plus adductor mobility and lateral power.",
        commonMistakes = listOf(
            "Letting the bent knee cave inward.",
            "Rounding the back to sink deeper.",
            "Lifting the heel of the bent leg.",
        ),
        tips = listOf(
            "Sit into one hip while keeping the other leg straight, chest up.",
            "Start bodyweight to earn the mobility before loading.",
        ),
        problematicAreas = listOf("Knees", "Hips"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Barbell_Squat",
        overview = "The barbell back squat — the foundational lower-body strength lift. Builds the quads, glutes and whole trunk, and carries over to almost everything.",
        commonMistakes = listOf(
            "Knees caving in on the way up.",
            "Good-morning-ing: hips rising first and the chest dropping.",
            "Rounding the lower back at depth.",
        ),
        tips = listOf(
            "Brace, sit down-and-back, drive the knees out, and keep the bar over mid-foot.",
            "Squat to at least parallel with a neutral spine.",
        ),
        problematicAreas = listOf("Knees", "Lower back"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Barbell_Squat_To_A_Bench",
        overview = "A box/bench squat that teaches sitting back and hitting a consistent depth, and builds power out of the hole. Common in powerlifting.",
        commonMistakes = listOf(
            "Crashing down and relaxing on the bench.",
            "Rocking backward to stand instead of staying tight.",
        ),
        tips = listOf(
            "Sit back to the bench under control, stay braced, then drive up without bouncing.",
            "Set the box at your target depth.",
        ),
        problematicAreas = listOf("Lower back", "Knees"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Barbell_Step_Ups",
        overview = "A loaded step-up onto a box, building single-leg strength, balance and glute/quad size with a functional pattern.",
        commonMistakes = listOf(
            "Pushing off the trailing foot instead of driving with the top leg.",
            "Using a box so high the form breaks down.",
        ),
        tips = listOf(
            "Place the whole foot on the box and drive through that heel to stand.",
            "Control the way down instead of dropping.",
        ),
        problematicAreas = listOf("Knees"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Barbell_Walking_Lunge",
        overview = "Continuous loaded lunges walking forward. A demanding single-leg builder that also challenges balance and conditioning.",
        commonMistakes = listOf(
            "Front knee caving or the torso pitching forward.",
            "Short steps that grind the knee.",
        ),
        tips = listOf(
            "Take a full stride, sink both knees to ~90°, and drive through the front heel into the next step.",
            "Keep the core tight and eyes forward for balance.",
        ),
        problematicAreas = listOf("Knees"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Battling_Ropes",
        overview = "Whipping heavy ropes for high-intensity conditioning. Torches the shoulders, arms and grip while spiking heart rate — a great finisher or interval tool.",
        commonMistakes = listOf(
            "Using only the arms instead of the whole body.",
            "Standing stiff and upright instead of in an athletic stance.",
        ),
        tips = listOf(
            "Stay in a quarter-squat and drive waves from the hips and legs.",
            "Work in short, hard intervals (e.g. 20–30s) for conditioning.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Bear_Crawl_Sled_Drags",
        overview = "Crawling forward while dragging a sled — a brutal full-body conditioning and core-stability drill that's easy on the joints.",
        commonMistakes = listOf(
            "Letting the hips pike up high.",
            "Moving same-side hand and foot, losing coordination.",
        ),
        tips = listOf(
            "Keep the hips low and back flat, moving opposite hand and foot.",
            "Short, controlled steps beat lunging forward.",
        ),
        problematicAreas = listOf("Wrists", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Behind_Head_Chest_Stretch",
        overview = "An aggressive chest and shoulder stretch with the arms taken behind the head/back. Opens the chest but is easy to overdo — advanced only.",
        commonMistakes = listOf(
            "Forcing the arms back too far, straining the shoulder.",
            "Holding your breath and tensing.",
        ),
        tips = listOf(
            "Ease in gradually and stop at a firm but comfortable stretch.",
            "Never bounce or push into pain.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 1,
    ),
    ExerciseEnrichment(
        id = "Bench_Jump",
        overview = "Jumping onto or over a bench for explosive lower-body power and conditioning. A plyometric drill — quality landings matter more than reps.",
        commonMistakes = listOf(
            "Landing stiff-legged or with the knees caving.",
            "Using a height you can't land softly on.",
        ),
        tips = listOf(
            "Land softly on the whole foot, absorbing into a slight squat.",
            "Step down between reps to save the joints.",
        ),
        problematicAreas = listOf("Knees", "Ankles"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Bench_Press_-_Powerlifting",
        overview = "The competition-style bench press with a leg drive, arch and paused rep. Maximizes pressing strength and stability for a bigger bench.",
        commonMistakes = listOf(
            "Losing the upper-back tightness and arch mid-rep.",
            "Bouncing off the chest instead of a controlled pause.",
            "Feet dancing instead of driving into the floor.",
        ),
        tips = listOf(
            "Pin the shoulder blades, set an arch, and use leg drive into a stable base.",
            "Pause the bar on the chest, then press explosively.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows", "Wrists"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Bench_Press_-_With_Bands",
        overview = "A bench press with bands adding resistance toward lockout. Trains explosive pressing and strengthens the top half — an accommodating-resistance tool.",
        commonMistakes = listOf(
            "Slowing down near the top instead of accelerating against the bands.",
            "Poor band setup causing uneven tension.",
        ),
        tips = listOf(
            "Press with maximum speed to beat the increasing band tension.",
            "Anchor the bands evenly and keep normal bench technique.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Bench_Press_with_Chains",
        overview = "Bench pressing with chains that add weight as you lock out. Builds explosive power and lockout strength — an advanced accommodating-resistance method.",
        commonMistakes = listOf(
            "Decelerating at the top instead of driving through the added chain weight.",
            "Hanging the chains so they don't deload on the chest.",
        ),
        tips = listOf(
            "Set the chains to fully unload on your chest and re-load as you press up.",
            "Focus on bar speed off the chest.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Bench_Sprint",
        overview = "A fast stepping/driving plyometric on a bench for leg power and conditioning. Simple, high-tempo athletic work.",
        commonMistakes = listOf(
            "Losing posture and hunching as fatigue sets in.",
            "Barely tapping the bench instead of driving.",
        ),
        tips = listOf(
            "Drive the knee up hard and keep a tall, athletic posture.",
            "Work in short, intense bursts.",
        ),
        problematicAreas = listOf("Knees", "Ankles"),
        efficiency = 2,
    ),
    ExerciseEnrichment(
        id = "Bent_Over_Barbell_Row",
        overview = "The bent-over barbell row — a heavy horizontal pull that builds a thick, strong back (lats, mid-back, rear delts) and carries over to deadlifts and pulls.",
        commonMistakes = listOf(
            "Standing up too tall so it becomes a shrug/upright row.",
            "Rounding the lower back under load.",
            "Heaving with momentum instead of rowing.",
        ),
        tips = listOf(
            "Hinge to ~45° or lower with a flat back, and row to the lower ribs/belly.",
            "Brace hard and keep the bar path tight to the body.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 5,
    ),
    ExerciseEnrichment(
        id = "Bent_Over_Dumbbell_Rear_Delt_Raise_With_Head_On_Bench",
        overview = "A rear-delt raise with the forehead resting on a bench to eliminate cheating. Isolates the rear delts strictly for posture and shoulder balance.",
        commonMistakes = listOf(
            "Swinging the weights up with momentum.",
            "Turning it into a row by pulling the elbows back and in.",
        ),
        tips = listOf(
            "Raise the dumbbells out to the sides leading with the elbows.",
            "Use light weight and a deliberate squeeze at the top.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Bent_Over_Low-Pulley_Side_Lateral",
        overview = "A cable rear-delt lateral from a low pulley, giving constant tension across the range. A quality isolation for the often-neglected rear delts.",
        commonMistakes = listOf(
            "Standing up and using the whole body to move the cable.",
            "Bending the elbow excessively into a row.",
        ),
        tips = listOf(
            "Stay hinged over and raise the handle out to the side with a fixed elbow.",
            "Keep the tension smooth and controlled both ways.",
        ),
        problematicAreas = listOf("Shoulders", "Lower back"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Bent_Over_One-Arm_Long_Bar_Row",
        overview = "A landmine/long-bar row done one arm at a time for a big range and strong mid-back contraction. Lets you row heavy through a long stroke.",
        commonMistakes = listOf(
            "Twisting the torso to yank the bar up.",
            "Losing the flat-back hinge under load.",
        ),
        tips = listOf(
            "Keep the hips square and row the handle to the hip.",
            "Pull with the back and pause at the top.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Bent_Over_Two-Arm_Long_Bar_Row",
        overview = "A T-bar/landmine row using both arms for heavy, back-thickening pulling. Great for loading the mid-back with a supported, joint-friendly path.",
        commonMistakes = listOf(
            "Standing up out of the hinge as you row.",
            "Rounding the lower back.",
        ),
        tips = listOf(
            "Hinge with a flat back and row the bar to your torso, squeezing the blades.",
            "Control the negative rather than dropping it.",
        ),
        problematicAreas = listOf("Lower back"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Bent_Over_Two-Dumbbell_Row",
        overview = "A bent-over row with two dumbbells, allowing a natural range and each side to work independently. A versatile back-builder.",
        commonMistakes = listOf(
            "Rowing with a rounded back.",
            "Shrugging or using momentum to lift the dumbbells.",
        ),
        tips = listOf(
            "Hinge to a flat back and row the dumbbells to the hips/lower ribs.",
            "Keep the elbows tucked and squeeze the back at the top.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Bent_Over_Two-Dumbbell_Row_With_Palms_In",
        overview = "A neutral-grip (palms-in) two-dumbbell row that emphasizes the lats and is comfortable on the shoulders and elbows.",
        commonMistakes = listOf(
            "Flaring the elbows wide, shifting work off the lats.",
            "Rounding the back or using body English.",
        ),
        tips = listOf(
            "Keep the palms facing each other and drive the elbows back along the ribs.",
            "Hold a flat-back hinge and squeeze at the top.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 4,
    ),
    ExerciseEnrichment(
        id = "Bent_Press",
        overview = "A classic kettlebell/barbell feat: pressing a weight overhead by bending the body under it. Highly technical, building whole-body strength and mobility. Expert-level.",
        commonMistakes = listOf(
            "Pressing with the arm instead of bending under the fixed weight.",
            "Losing sight of the overhead load.",
            "Rushing a lift that demands precise technique.",
        ),
        tips = listOf(
            "Keep the arm locked and 'bend away' from the weight, then stand up under it.",
            "Learn it light — it's a skill lift, not a grind.",
        ),
        problematicAreas = listOf("Lower back", "Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Bent-Arm_Barbell_Pullover",
        overview = "A barbell pullover done with bent arms, stretching and working the lats, chest and serratus. An old-school builder for rib-cage expansion and lat development.",
        commonMistakes = listOf(
            "Going too heavy and stressing the shoulders at the stretch.",
            "Flaring the elbows wide at the bottom.",
        ),
        tips = listOf(
            "Lower the bar behind the head under control for a deep stretch, then pull back over.",
            "Keep the elbows moderately tucked and use manageable weight.",
        ),
        problematicAreas = listOf("Shoulders", "Elbows"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Bent-Arm_Dumbbell_Pullover",
        overview = "A dumbbell pullover across a bench, stretching the chest and lats. Good for upper-body mobility and adding a different stimulus to chest/back work.",
        commonMistakes = listOf(
            "Using so much weight the shoulders get overstretched.",
            "Bending and straightening the arms to press instead of arcing over.",
        ),
        tips = listOf(
            "Keep a fixed slight elbow bend and arc the dumbbell overhead for a big stretch.",
            "Move slowly and keep the hips down.",
        ),
        problematicAreas = listOf("Shoulders"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Bent-Knee_Hip_Raise",
        overview = "A reverse-crunch-style hip raise curling the knees toward the chest. A beginner-friendly lower-ab exercise that spares the neck.",
        commonMistakes = listOf(
            "Swinging the legs and using momentum.",
            "Just lifting the knees without curling the pelvis up.",
        ),
        tips = listOf(
            "Tilt the pelvis and lift the hips slightly off the floor.",
            "Lower slowly without arching the lower back.",
        ),
        problematicAreas = listOf("Lower back", "Neck"),
        efficiency = 3,
    ),
    ExerciseEnrichment(
        id = "Bicycling",
        overview = "Outdoor cycling for cardiovascular fitness and leg endurance. Low-impact and scalable, great for conditioning and active recovery.",
        commonMistakes = listOf(
            "A saddle height too low, cramping the knees.",
            "Only ever riding at one easy pace.",
        ),
        tips = listOf(
            "Set the saddle so the knee is nearly straight at the bottom of the stroke.",
            "Mix steady rides with intervals to build fitness.",
        ),
        problematicAreas = listOf("Knees"),
        efficiency = 2,
    ),
)
