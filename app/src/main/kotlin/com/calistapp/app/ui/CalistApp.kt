package com.calistapp.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import com.calistapp.app.ui.planner.WorkoutPlannerScreen
import com.calistapp.app.ui.profile.ProfileScreen
import com.calistapp.app.ui.session.ActiveSessionScreen
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Emerald
import com.calistapp.app.ui.theme.Sky
import com.calistapp.app.ui.theme.Violet

private val bottomItems = listOf(
    NavItem(Routes.DASHBOARD, "Home", Icons.Filled.Home),
    NavItem(Routes.EXERCISES, "Exercises", Icons.Filled.FitnessCenter),
    NavItem(Routes.HISTORY, "History", Icons.Filled.History),
    NavItem(Routes.PROFILE, "Profile", Icons.Filled.Person),
)

/**
 * Each destination carries its own ambient hue, so moving between tabs feels like moving somewhere
 * rather than swapping content in a static frame.
 */
private fun tintFor(route: String?): Color = when (route) {
    Routes.EXERCISES, Routes.EXERCISE_DETAIL, Routes.EXERCISE_EDIT -> Violet
    Routes.HISTORY, Routes.DETAIL -> Sky
    Routes.PROFILE -> Amber
    else -> Emerald
}

/** Room for the floating nav bar so content never slides underneath it. */
private val NavBarInset = 104.dp

@Composable
fun CalistApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in Routes.bottomBarRoutes

    AmbientHost(routeTint = tintFor(currentRoute)) {
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier
                .statusBarsPadding()
                .padding(bottom = if (showBar) NavBarInset else 0.dp),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onStartWorkout = { navController.navigate(Routes.active()) },
                    onOpenSession = { id -> navController.navigate(Routes.detail(id)) },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
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
                HistoryScreen(onOpenSession = { id -> navController.navigate(Routes.detail(id)) })
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
                )
            }
            composable(Routes.PLANNER) {
                WorkoutPlannerScreen(
                    // Starting from the planner hands the plan straight to the live session.
                    onStarted = {
                        navController.navigate(Routes.active()) {
                            popUpTo(Routes.PLANNER) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onOpenExercise = { id -> navController.navigate(Routes.exerciseDetail(id)) },
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
                onAction = { navController.navigate(Routes.active()) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
    }
}
