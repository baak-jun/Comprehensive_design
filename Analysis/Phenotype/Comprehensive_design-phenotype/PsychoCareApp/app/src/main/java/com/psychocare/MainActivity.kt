package com.psychocare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.psychocare.ui.screens.HomeScreen
import com.psychocare.ui.screens.PhenotypeScreen
import com.psychocare.ui.screens.ProfileScreen
import com.psychocare.viewmodel.MainViewModel
// Gemma 상담 기능 임시 비활성화 — 다른 기능 검증 후 복원 예정
// import com.psychocare.ui.screens.ChatScreen
// import androidx.compose.material.icons.filled.Chat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PsychoCareApp()
            }
        }
    }
}

@Composable
fun PsychoCareApp() {
    val viewModel: MainViewModel = viewModel()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon    = { Icon(Icons.Default.Home, "홈") },
                    label   = { Text("홈") },
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon    = { Icon(Icons.Default.InsertChart, "패턴") },
                    label   = { Text("패턴") },
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon    = { Icon(Icons.Default.Person, "프로필") },
                    label   = { Text("프로필") },
                    selected = selectedTab == 2,
                    onClick  = { selectedTab = 2 }
                )
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> HomeScreen(
                    viewModel = viewModel,
                    modifier  = Modifier.padding(paddingValues)
                 )
            1 -> PhenotypeScreen(
                    viewModel = viewModel,
                    modifier  = Modifier.padding(paddingValues)
                 )
            2 -> ProfileScreen(
                    viewModel = viewModel,
                    modifier  = Modifier.padding(paddingValues)
                 )
        }
    }
}
