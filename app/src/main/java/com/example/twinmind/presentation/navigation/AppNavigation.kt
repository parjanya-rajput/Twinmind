package com.example.twinmind.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.twinmind.presentation.dashboard.DashboardScreen
import com.example.twinmind.presentation.dashboard.DashboardViewModel
import com.example.twinmind.presentation.recording.RecordingScreen
import com.example.twinmind.presentation.recording.RecordingViewModel
import com.example.twinmind.presentation.summary.SummaryScreen
import com.example.twinmind.presentation.summary.SummaryViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "dashboard") {

        // 1. Dashboard Screen
        composable("dashboard") {
            val viewModel: DashboardViewModel = hiltViewModel()
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToRecording = { navController.navigate("recording") },
                onNavigateToSummary = { meetingId ->
                    navController.navigate("summary/$meetingId")
                }
            )
        }

        // 2. Recording Screen
        composable("recording") {
            val viewModel: RecordingViewModel = hiltViewModel()
            RecordingScreen(
                viewModel = viewModel,
                onNavigateToSummary = { meetingId ->
                    // Pop recording screen and navigate to summary
                    navController.navigate("summary/$meetingId") {
                        popUpTo("dashboard") { inclusive = false }
                    }
                }
            )
        }

        // 3. Summary Screen
        composable(
            route = "summary/{meetingId}",
            arguments = listOf(navArgument("meetingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: return@composable
            val viewModel: SummaryViewModel = hiltViewModel()

            SummaryScreen(
                viewModel = viewModel,
                meetingId = meetingId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}