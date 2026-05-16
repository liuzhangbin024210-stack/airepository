package com.majiang.counter.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.majiang.counter.domain.Seat
import com.majiang.counter.profile.NormRect
import com.majiang.counter.profile.VisualRoiKeys
import com.majiang.counter.ui.util.seatLabel

/** 画面区域标定与牌墙剩张识别开关（无相机预览）。 */
@Composable
fun SettingsTab(
    vm: GameViewModel,
    username: String,
    isAdmin: Boolean,
    onLogout: () -> Unit,
    onAddUser: () -> Unit,
) {
    val authVm: AuthViewModel = hiltViewModel()
    var showChangePassword by remember { mutableStateOf(false) }
    val pack by vm.roiPack.collectAsStateWithLifecycle()
    val rois by vm.riverRois.collectAsStateWithLifecycle()
    val hudOcrEnabled by vm.hudOcrEnabled.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf<CalibTarget>(CalibTarget.River(Seat.EAST)) }

    val rect: NormRect = when (val t = target) {
        is CalibTarget.River ->
            rois[t.seat] ?: GameViewModel.defaultRiverRois()[t.seat]!!
        is CalibTarget.Extra ->
            pack.extras[t.key] ?: NormRect(0.05f, 0.05f, 0.95f, 0.95f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(PlayerStrings.SETTINGS_TITLE, style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(PlayerStrings.AUTH_LOGGED_IN_AS, style = MaterialTheme.typography.labelLarge)
                Text(username, style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(onClick = onLogout) {
                Text(PlayerStrings.AUTH_LOGOUT)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    authVm.resetFormFeedback()
                    showChangePassword = true
                },
            ) {
                Text(PlayerStrings.AUTH_CHANGE_PASSWORD)
            }
            if (isAdmin) {
                OutlinedButton(onClick = onAddUser) {
                    Text(PlayerStrings.AUTH_ADD_USER_ENTRY)
                }
            }
        }

        if (showChangePassword) {
            ChangePasswordDialog(
                username = username,
                authVm = authVm,
                onDismiss = { showChangePassword = false },
            )
        }

        Text(
            PlayerStrings.SETTINGS_CALIB_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(PlayerStrings.SETTINGS_AUTO_WALL, style = MaterialTheme.typography.titleSmall)
                Text(
                    PlayerStrings.SETTINGS_AUTO_WALL_DESC,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = hudOcrEnabled,
                onCheckedChange = { vm.setHudOcrEnabled(it) },
            )
        }

        Text(PlayerStrings.SETTINGS_RIVER_ZONES, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Seat.entries.forEach { s ->
                FilterChip(
                    selected = target == CalibTarget.River(s),
                    onClick = { target = CalibTarget.River(s) },
                    label = { Text("舍牌·${PlayerStrings.seatShort(s)}") },
                )
            }
        }

        Text(PlayerStrings.SETTINGS_EXTRA_ZONES, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VisualRoiKeys.extraCalibrationOrder().forEach { (key, label) ->
                FilterChip(
                    selected = (target as? CalibTarget.Extra)?.key == key,
                    onClick = { target = CalibTarget.Extra(key, label) },
                    label = { Text(label) },
                )
            }
        }

        Text(
            when (val t = target) {
                is CalibTarget.River -> "正在调整：${seatLabel(t.seat)} 舍牌区"
                is CalibTarget.Extra -> "正在调整：${t.label}"
            },
            style = MaterialTheme.typography.titleSmall,
        )

        NormRectSlider(PlayerStrings.EDGE_LEFT, rect.left) {
            applyRect(vm, target, rect.copy(left = it))
        }
        NormRectSlider(PlayerStrings.EDGE_TOP, rect.top) {
            applyRect(vm, target, rect.copy(top = it))
        }
        NormRectSlider(PlayerStrings.EDGE_RIGHT, rect.right) {
            applyRect(vm, target, rect.copy(right = it))
        }
        NormRectSlider(PlayerStrings.EDGE_BOTTOM, rect.bottom) {
            applyRect(vm, target, rect.copy(bottom = it))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    when (val t = target) {
                        is CalibTarget.River -> vm.resetRiverRoiToDefault(t.seat)
                        is CalibTarget.Extra -> vm.resetExtraRoiToDefault(t.key)
                    }
                },
            ) {
                Text(PlayerStrings.RESET_CURRENT)
            }
            OutlinedButton(onClick = { vm.resetRiverRoisToDefault() }) {
                Text(PlayerStrings.RESET_ALL_RIVERS)
            }
        }
        OutlinedButton(onClick = { vm.resetAllCalibrationToProfileDefaults() }) {
            Text(PlayerStrings.RESET_ALL)
        }
    }
}

@Composable
private fun ChangePasswordDialog(
    username: String,
    authVm: AuthViewModel,
    onDismiss: () -> Unit,
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val formState by authVm.formState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        authVm.resetFormFeedback()
    }

    AlertDialog(
        onDismissRequest = { if (!formState.busy) onDismiss() },
        title = { Text(PlayerStrings.AUTH_CHANGE_PASSWORD) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text(PlayerStrings.AUTH_OLD_PASSWORD) },
                    singleLine = true,
                    enabled = !formState.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(PlayerStrings.AUTH_NEW_PASSWORD) },
                    singleLine = true,
                    enabled = !formState.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(PlayerStrings.AUTH_CONFIRM_NEW_PASSWORD) },
                    singleLine = true,
                    enabled = !formState.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (formState.errorMessage != null) {
                    Text(
                        formState.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    authVm.changePassword(
                        username = username,
                        oldPassword = oldPassword,
                        newPassword = newPassword,
                        confirmPassword = confirmPassword,
                        onSuccess = { onDismiss() },
                    )
                },
                enabled = !formState.busy,
            ) {
                Text(PlayerStrings.AUTH_CHANGE_PASSWORD_CONFIRM)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !formState.busy) {
                Text(PlayerStrings.AUTH_CANCEL)
            }
        },
    )
}

private sealed class CalibTarget {
    data class River(val seat: Seat) : CalibTarget()
    data class Extra(val key: String, val label: String) : CalibTarget()
}

private fun applyRect(vm: GameViewModel, target: CalibTarget, updated: NormRect) {
    when (target) {
        is CalibTarget.River -> vm.updateRiverRoi(target.seat, updated)
        is CalibTarget.Extra -> vm.updateExtraRoi(target.key, updated)
    }
}

@Composable
private fun NormRectSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column {
        Text("$label ${"%.2f".format(value)}")
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
    }
}
