package com.calistapp.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.calistapp.app.ui.common.AmbientHost
import com.calistapp.app.ui.common.FloatingNavBar
import com.calistapp.app.ui.common.NavItem
import com.calistapp.app.ui.dashboard.DashboardScreen
import com.calistapp.app.ui.detail.SessionDetailScreen
import com.calistapp.app.ui.exercises.ExerciseDetailScreen
import com.calistapp.app.ui.exercises.ExerciseEditScreen
import com.calistapp.app.ui.exercises.ExerciseGalleryScreen
import com.calistapp.app.ui.history.HistoryScreen
import com.calistapp.app.ui.navigation.Routes
import com.calistapp.app.ui.planner.SavedWorkoutDetailScreen
import com.calistapp.app.ui.planner.WorkoutPlannerScreen
import com.calistapp.app.ui.profile.ProfileScreen
import com.calistapp.app.ui.schedule.ScheduleScreen
import com.calistapp.app.ui.session.ActiveSessionScreen
import com.calistapp.app.ui.session.SessionSetupScreen
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.Sky

private val bottomItems = listOf(
    NavItem(Routes.DASHBOARD, "Home", Icons.Filled.Home),
    NavItem(Routes.EXERCISES, "Exercises", Icons.Filled.FitnessCenter),
    NavItem(Routes.HISTORY, "History", Icons.Filled.History),
    NavItem(Routes.PROFILE, "Settings", Icons.Filled.Settings),
)

/**
 * Each destination carries its own ambient hue, so moving between tabs feels like moving somewhere
 * rather than swapping content in a static frame.
 */
private fun tintFor(route: String?): Color = when (route) {
    // The library gets a warm gold; stats/history keep a cool blue as a deliberate "data" counterpoint
    // to the orange; everything else (home, session, profile) glows in the signature Flame.
    Routes.EXERCISES, Routes.EXERCISE_DETAIL, Routes.EXERCISE_EDIT -> Amber
    Routes.HISTORY, Routes.DETAIL -> Sky
    else -> Flame
}

/** Room for the floating nav bar so content never slides underneath it. */
private val NavBarInset = 104.dp

/**
 * Where the primary action goes.
 *
 * A workout now begins by building one, so the planner *is* the start of a session and the action
 * button leads straight there — it used to land on a staging screen whose only real job was to offer
 * a "Build a workout" button, which put the app's core activity three taps away. With a session
 * already running there is nothing to build, so it reopens that instead.
 */
private fun startDestination(sessionRunning: Boolean): String =
    if (sessionRunning) Routes.active() else Routes.PLANNER

@Composable
fun CalistApp(viewModel: AppViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in Routes.bottomBarRoutes
    val sessionRunning by viewModel.sessionRunning.collectAsStateWithLifecycle()

    // Reopening the app drops you back into a running workout. We act on every foreground (and on a
    // cold start once crash-recovery revives the session), but only from a top-level tab — so the
    // build→start flow and an intentional minimise (which stays in-app, firing no lifecycle event)
    // are never hijacked.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, sessionRunning) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (sessionRunning && navController.currentDestination?.route in Routes.bottomBarRoutes) {
                navController.navigate(Routes.active()) { launchSingleTop = true }
            }
        }
    }

    AmbientHost(routeTint = tintFor(currentRoute)) {
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            // A soft crossfade with a hair of scale, so moving between screens reads as depth rather
            // than a hard cut — the same "feels considered" the rest of the redesign is going for.
            enterTransition = { fadeIn(tween(220)) + scaleIn(initialScale = 0.985f, animationSpec = tween(220)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(150)) + scaleOut(targetScale = 0.985f, animationSpec = tween(150)) },
            modifier = Modifier
                .statusBarsPadding()
                .padding(bottom = if (showBar) NavBarInset else 0.dp),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onStartWorkout = { navController.navigate(startDestination(sessionRunning)) },
                    onOpenWorkout = { id -> navController.navigate(Routes.savedWorkout(id)) },
                    onOpenSession = { id -> navController.navigate(Routes.detail(id)) },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    onOpenSchedule = { navController.navigate(Routes.SCHEDULE) },
                )
            }
            composable(Routes.EXERCISES) {
                ExerciseGalleryScreen(
                    onOpenExercise = { id -> navController.navigate(Routes.exerciseDetail(id)) },
                    onAddExercise = { navController.navigate(Routes.exerciseEdit()) },
                )
            }
            composable(Routes.EXERCISE_DETAIL) {
                ExerciseDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { exerciseId -> navController.navigate(Routes.exerciseEdit(exerciseId)) },
                    onStartWorkout = { exerciseId ->
                        navController.navigate(Routes.active(exerciseId)) {
                            popUpTo(Routes.EXERCISES)
                        }
                    },
                    onOpenSession = { id -> navController.navigate(Routes.detail(id)) },
                )
            }
            composable(
                route = Routes.EXERCISE_EDIT,
                arguments = listOf(
                    navArgument(Routes.EXERCISE_EDIT_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                ExerciseEditScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(Routes.exerciseDetail(id)) {
                            popUpTo(Routes.EXERCISE_EDIT) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpenSession = { id -> navController.navigate(Routes.detail(id)) },
                    onStartWorkout = { navController.navigate(startDestination(sessionRunning)) },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen()
            }
            composable(
                route = Routes.ACTIVE,
                arguments = listOf(
                    navArgument(Routes.ACTIVE_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                ActiveSessionScreen(
                    onFinished = { id ->
                        navController.navigate(Routes.detail(id)) {
                            popUpTo(Routes.DASHBOARD)
                        }
                    },
                    onDiscarded = { navController.popBackStack() },
                    onBuildWorkout = { navController.navigate(Routes.PLANNER) },
                    // Minimise: leave the screen while the session keeps running in the background.
                    onCollapse = { navController.popBackStack() },
                    onOpenExercise = { id -> navController.navigate(Routes.exerciseDetail(id)) },
                )
            }
            composable(Routes.PLANNER) {
                WorkoutPlannerScreen(
                    // Building leads to the pre-flight setup screen, not straight into the session.
                    onStarted = { navController.navigate(Routes.SETUP) },
                    onBack = { navController.popBackStack() },
                    onOpenExercise = { id -> navController.navigate(Routes.exerciseDetail(id)) },
                    onOpenSavedWorkout = { id -> navController.navigate(Routes.savedWorkout(id)) },
                )
            }
            composable(Routes.SCHEDULE) {
                ScheduleScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlanner = { navController.navigate(Routes.PLANNER) },
                )
            }
            composable(Routes.SETUP) {
                SessionSetupScreen(
                    // Starting from setup hands the finished plan straight to the live session, and
                    // clears the planner (and any setup/detail step) off the back stack.
                    onStarted = {
                        navController.navigate(Routes.active()) {
                            popUpTo(Routes.PLANNER) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SAVED_WORKOUT) {
                SavedWorkoutDetailScreen(
                    // Start loads the plan into the draft (in the VM) then heads to the setup screen;
                    // Edit and Delete both drop back to the planner, which reflects the loaded draft.
                    onStart = { navController.navigate(Routes.SETUP) },
                    onEdit = { navController.popBackStack() },
                    onOpenSession = { id -> navController.navigate(Routes.detail(id)) },
                    onOpenExercise = { id -> navController.navigate(Routes.exerciseDetail(id)) },
                    onDeleted = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.DETAIL) {
                SessionDetailScreen(onBack = { navController.popBackStack() })
            }
        }

        if (showBar) {
            FloatingNavBar(
                items = bottomItems,
                currentRoute = currentRoute,
                onSelect = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onAction = { navController.navigate(startDestination(sessionRunning)) },
                actionDescription = if (sessionRunning) "Open running workout" else "Build a workout",
                actionIcon = if (sessionRunning) Icons.Filled.PlayArrow else Icons.Filled.Add,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
    }
}
