package com.majiang.counter.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 主界面：对局（记牌 + 分析）、底部「相机 / 分析」与设置（画面区域标定）。
 */
@Composable
fun MainScreen(
    username: String,
    isAdmin: Boolean,
    onLogout: () -> Unit,
    onAddUser: () -> Unit,
    vm: GameViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var camPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            camPermission = granted
            if (granted) vm.setCameraActive(true)
        },
    )

    // 从系统设置返回后同步权限状态，避免仍按旧值判断。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                camPermission =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraActive by vm.cameraActive.collectAsStateWithLifecycle()
    val busy by vm.analysisBusy.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.SportsEsports, contentDescription = null) },
                    label = { Text(PlayerStrings.TAB_PLAY) },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        tab = 0
                        if (!camPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            vm.setCameraActive(!cameraActive)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (cameraActive) Icons.Filled.Videocam else Icons.Outlined.Videocam,
                            contentDescription = PlayerStrings.NAV_CAMERA_CD,
                        )
                    },
                    label = { Text(PlayerStrings.TAB_NAV_CAMERA) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor =
                            if (cameraActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    ),
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        tab = 0
                        vm.startAnalysis()
                    },
                    enabled = cameraActive && !busy,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Insights,
                            contentDescription = PlayerStrings.NAV_ANALYSIS_CD,
                        )
                    },
                    label = { Text(PlayerStrings.TAB_NAV_ANALYSIS) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(PlayerStrings.TAB_SETTINGS) },
                )
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner)) {
            when (tab) {
                0 -> PlayTab(vm, hasCameraPermission = camPermission)
                else -> SettingsTab(
                    vm = vm,
                    username = username,
                    isAdmin = isAdmin,
                    onLogout = onLogout,
                    onAddUser = onAddUser,
                )
            }
        }
    }
}
