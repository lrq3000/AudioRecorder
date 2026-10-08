package com.dimowner.audiorecorder.v2.app.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.data.model.BluetoothCaptureRoute
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.BluetoothVoiceEnhancement
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode
import com.dimowner.audiorecorder.v2.app.components.DISABLED_ALPHA

/** Presets keep the common setup compact; Custom retains all independent experiment controls. */
@Composable
internal fun BluetoothExperimentSettings(state: SettingsState, onAction: (SettingsScreenAction) -> Unit) {
    val context = LocalContext.current
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    // Entering Custom reveals its options immediately; collapsing them never changes capture.
    var customExpanded by rememberSaveable(state.bluetoothVoiceEnhancement) { mutableStateOf(true) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
    }
    fun requestHfpPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        ExperimentSelector(stringResource(R.string.bluetooth_voice_enhancement_title), state.bluetoothVoiceEnhancement,
            BluetoothVoiceEnhancement.entries, state.isRecordingSettingEditable, label = { mode ->
                stringResource(when (mode) {
                    BluetoothVoiceEnhancement.DISABLED -> R.string.bluetooth_voice_enhancement_disabled
                    BluetoothVoiceEnhancement.HFP_PRESET -> R.string.bluetooth_voice_enhancement_hfp
                    BluetoothVoiceEnhancement.CUSTOM -> R.string.bluetooth_voice_enhancement_custom
                })
            }) { value ->
            onAction(SettingsScreenAction.SetExperiment.Enhancement(value))
            if (value == BluetoothVoiceEnhancement.HFP_PRESET) requestHfpPermissionIfNeeded()
        }
        Text(stringResource(when (state.bluetoothVoiceEnhancement) {
            BluetoothVoiceEnhancement.DISABLED -> R.string.bluetooth_voice_enhancement_disabled_summary
            BluetoothVoiceEnhancement.HFP_PRESET -> R.string.bluetooth_voice_enhancement_hfp_summary
            BluetoothVoiceEnhancement.CUSTOM -> R.string.bluetooth_voice_enhancement_custom_summary
        }), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        Text(stringResource(R.string.bluetooth_experiment_help), style = MaterialTheme.typography.bodySmall)
        if (state.bluetoothVoiceEnhancement == BluetoothVoiceEnhancement.CUSTOM) {
            val collapseAction = stringResource(if (customExpanded) R.string.bluetooth_experiment_collapse else R.string.bluetooth_experiment_expand)
            val expansionState = stringResource(if (customExpanded) R.string.bluetooth_experiment_expanded else R.string.bluetooth_experiment_collapsed)
            Row(Modifier.fillMaxWidth()
                .semantics { stateDescription = expansionState }
                .clickable(role = Role.Button, onClickLabel = collapseAction) { customExpanded = !customExpanded }
                .padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bluetooth_experiment_title), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null,
                    modifier = Modifier.size(24.dp).rotate(if (customExpanded) 180f else 0f))
            }
            if (customExpanded) {
                ExperimentSelector(stringResource(R.string.bluetooth_experiment_route), state.bluetoothCaptureRoute,
                    BluetoothCaptureRoute.entries, state.isRecordingSettingEditable) { value ->
                    onAction(SettingsScreenAction.SetExperiment.Route(value))
                    if (value == BluetoothCaptureRoute.HFP_VOICE_RECOGNITION) requestHfpPermissionIfNeeded()
                }
                ExperimentSelector(stringResource(R.string.bluetooth_experiment_mode), state.bluetoothAudioMode,
                    BluetoothAudioMode.entries, state.isRecordingSettingEditable) { onAction(SettingsScreenAction.SetExperiment.Mode(it)) }
                ExperimentSelector(stringResource(R.string.bluetooth_experiment_preprocessing), state.inputPreprocessingPolicy,
                    InputPreprocessingPolicy.entries, state.isRecordingSettingEditable) { onAction(SettingsScreenAction.SetExperiment.Preprocessing(it)) }
                ExperimentSelector(stringResource(R.string.bluetooth_experiment_gain), state.pcmGainMode,
                    PcmGainMode.entries, state.isRecordingSettingEditable) { onAction(SettingsScreenAction.SetExperiment.Gain(it)) }
            }
        }
        if (permissionDenied && state.bluetoothCaptureRoute == BluetoothCaptureRoute.HFP_VOICE_RECOGNITION) {
            Text(stringResource(R.string.bluetooth_experiment_permission_denied), color = MaterialTheme.colorScheme.error)
        }
        // Diagnostics stay accessible with Disabled/HFP selected and with Custom collapsed.
        TextButton(onClick = { showDiagnostics = !showDiagnostics }) { Text(stringResource(R.string.bluetooth_experiment_diagnostics)) }
        if (showDiagnostics) SelectionContainer { Text(state.captureDiagnostics, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun <T : Enum<T>> ExperimentSelector(
    title: String,
    selected: T,
    values: List<T>,
    enabled: Boolean,
    label: @Composable (T) -> String = { it.name },
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
            .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(label(selected), style = MaterialTheme.typography.bodyLarge)
            }
            // Match the existing audio-source/name-format dropdown affordance, including a
            // full-row touch target rather than making the small arrow the only way to open it.
            Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null,
                modifier = Modifier.size(24.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value -> DropdownMenuItem(text = { Text(label(value)) }, enabled = enabled, onClick = {
                expanded = false
                onSelect(value)
            }) }
        }
    }
}
