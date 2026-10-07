package com.dimowner.audiorecorder.v2.app.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.data.model.BluetoothCaptureRoute
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode

/** Technical labels intentionally mirror the experiment matrix rather than hiding combinations. */
@Composable
internal fun BluetoothExperimentSettings(state: SettingsState, onAction: (SettingsScreenAction) -> Unit) {
    val context = LocalContext.current
    var showDiagnostics by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.bluetooth_experiment_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.bluetooth_experiment_help), style = MaterialTheme.typography.bodySmall)
        ExperimentSelector(stringResource(R.string.bluetooth_experiment_route), state.bluetoothCaptureRoute,
            BluetoothCaptureRoute.entries, state.isRecordingSettingEditable) { value ->
            onAction(SettingsScreenAction.SetExperiment.Route(value))
            if (value == BluetoothCaptureRoute.HFP_VOICE_RECOGNITION && Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        ExperimentSelector(stringResource(R.string.bluetooth_experiment_mode), state.bluetoothAudioMode,
            BluetoothAudioMode.entries, state.isRecordingSettingEditable) { onAction(SettingsScreenAction.SetExperiment.Mode(it)) }
        ExperimentSelector(stringResource(R.string.bluetooth_experiment_preprocessing), state.inputPreprocessingPolicy,
            InputPreprocessingPolicy.entries, state.isRecordingSettingEditable) { onAction(SettingsScreenAction.SetExperiment.Preprocessing(it)) }
        ExperimentSelector(stringResource(R.string.bluetooth_experiment_gain), state.pcmGainMode,
            PcmGainMode.entries, state.isRecordingSettingEditable) { onAction(SettingsScreenAction.SetExperiment.Gain(it)) }
        if (permissionDenied) Text(stringResource(R.string.bluetooth_experiment_permission_denied), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { showDiagnostics = !showDiagnostics }) { Text(stringResource(R.string.bluetooth_experiment_diagnostics)) }
        if (showDiagnostics) SelectionContainer { Text(state.captureDiagnostics, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun <T : Enum<T>> ExperimentSelector(title: String, selected: T, values: List<T>, enabled: Boolean, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled) { expanded = true }.padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(selected.name, style = MaterialTheme.typography.bodyMedium)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value -> DropdownMenuItem(text = { Text(value.name) }, enabled = enabled, onClick = {
                expanded = false
                onSelect(value)
            }) }
        }
    }
}
