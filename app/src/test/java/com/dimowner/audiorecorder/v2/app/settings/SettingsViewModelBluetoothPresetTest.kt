package com.dimowner.audiorecorder.v2.app.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.util.TestARApplication
import com.dimowner.audiorecorder.v2.audio.AudioRecorderDelegate
import com.dimowner.audiorecorder.v2.data.PrefsV2Impl
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.BluetoothCaptureRoute
import com.dimowner.audiorecorder.v2.data.model.BluetoothVoiceEnhancement
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Use actual preferences: these tests cover the UI/persistence boundary, not a mocked preset. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class SettingsViewModelBluetoothPresetTest {
    private lateinit var context: Context
    private lateinit var prefs: PrefsV2Impl
    private val recorder = mockk<AudioRecorderDelegate>(relaxed = true)

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = PrefsV2Impl(context)
        prefs.fullPreferenceReset()
        every { recorder.provideAudioRecorder().isRecording } returns false
    }

    private fun createViewModel() = SettingsViewModel(
        prefs = prefs, recordsDataSource = mockk(relaxed = true), fileDataSource = mockk(relaxed = true),
        audioPlayer = mockk(relaxed = true), audioRecorderDelegate = recorder, analyticsTracker = mockk(relaxed = true),
        mainDispatcher = Dispatchers.Unconfined, ioDispatcher = Dispatchers.Unconfined, context = context,
    )

    @Test fun `screen loads the persisted HFP selection and values`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val state = createViewModel().state.value
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, state.bluetoothVoiceEnhancement)
        assertEquals(hfpValues, values(state))
    }

    @Test fun `selecting HFP updates the source and every experimental control together`() {
        val vm = createViewModel()
        vm.onAction(SettingsScreenAction.SetExperiment.Enhancement(BluetoothVoiceEnhancement.HFP_PRESET))
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, vm.state.value.bluetoothVoiceEnhancement)
        assertEquals(hfpValues, values(vm.state.value))
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, PrefsV2Impl(context).bluetoothVoiceEnhancement)
    }

    @Test fun `selecting Custom keeps preset values available for editing`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        vm.onAction(SettingsScreenAction.SetExperiment.Enhancement(BluetoothVoiceEnhancement.CUSTOM))
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, vm.state.value.bluetoothVoiceEnhancement)
        assertEquals(hfpValues, values(vm.state.value))
    }

    @Test fun `selecting Disabled refreshes all controls while keeping the chosen format`() {
        prefs.settingRecordingFormat = RecordingFormat.Wav
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        vm.onAction(SettingsScreenAction.SetExperiment.Enhancement(BluetoothVoiceEnhancement.DISABLED))
        assertEquals(BluetoothVoiceEnhancement.DISABLED, vm.state.value.bluetoothVoiceEnhancement)
        assertEquals(defaultValues, values(vm.state.value))
        assertEquals(RecordingFormat.Wav, prefs.settingRecordingFormat)
    }

    @Test fun `editing the separately displayed audio source changes the preset label to Custom`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        vm.setAudioSource(AudioSource.MIC)
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, vm.state.value.bluetoothVoiceEnhancement)
        assertEquals(AudioSource.MIC, vm.state.value.selectedAudioSource)
        assertEquals(PcmGainMode.AUTO_LEVEL, vm.state.value.pcmGainMode)
    }

    @Test fun `editing an experimental choice changes the preset label to Custom`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        vm.onAction(SettingsScreenAction.SetExperiment.Gain(PcmGainMode.DB_PLUS_6))
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, vm.state.value.bluetoothVoiceEnhancement)
        assertEquals(PcmGainMode.DB_PLUS_6, vm.state.value.pcmGainMode)
    }

    @Test fun `recording reset displays Disabled and default values`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.CUSTOM
        val vm = createViewModel()
        vm.resetRecordingSettings()
        assertEquals(BluetoothVoiceEnhancement.DISABLED, vm.state.value.bluetoothVoiceEnhancement)
        assertEquals(defaultValues, values(vm.state.value))
    }

    @Test fun `preset selection cannot change a recording in progress`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.CUSTOM
        prefs.pcmGainMode = PcmGainMode.DB_PLUS_12
        val vm = createViewModel()
        val original = values(vm.state.value)
        every { recorder.provideAudioRecorder().isRecording } returns true
        vm.onAction(SettingsScreenAction.SetExperiment.Enhancement(BluetoothVoiceEnhancement.HFP_PRESET))
        assertEquals(original, values(vm.state.value))
        assertEquals(BluetoothVoiceEnhancement.CUSTOM, prefs.bluetoothVoiceEnhancement)
        assertEquals(PcmGainMode.DB_PLUS_12, prefs.pcmGainMode)
    }

    @Test fun `source selection cannot change a preset after recording starts outside Settings`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        every { recorder.provideAudioRecorder().isRecording } returns true
        vm.setAudioSource(AudioSource.MIC)
        assertEquals(hfpValues, values(vm.state.value))
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, prefs.bluetoothVoiceEnhancement)
    }

    @Test fun `reset cannot clear a preset after recording starts outside Settings`() {
        prefs.bluetoothVoiceEnhancement = BluetoothVoiceEnhancement.HFP_PRESET
        val vm = createViewModel()
        every { recorder.provideAudioRecorder().isRecording } returns true
        vm.resetRecordingSettings()
        assertEquals(hfpValues, values(vm.state.value))
        assertEquals(BluetoothVoiceEnhancement.HFP_PRESET, prefs.bluetoothVoiceEnhancement)
    }

    private fun values(state: SettingsState): List<Any> = listOf(state.selectedAudioSource,
        state.bluetoothCaptureRoute, state.bluetoothAudioMode, state.inputPreprocessingPolicy, state.pcmGainMode)

    companion object {
        private val hfpValues = listOf(AudioSource.VOICE_RECOGNITION, BluetoothCaptureRoute.HFP_VOICE_RECOGNITION,
            BluetoothAudioMode.NORMAL, InputPreprocessingPolicy.AGC_ONLY, PcmGainMode.AUTO_LEVEL)
        private val defaultValues = listOf(AudioSource.DEFAULT, BluetoothCaptureRoute.STANDARD_SCO,
            BluetoothAudioMode.IN_COMMUNICATION, InputPreprocessingPolicy.SYSTEM_DEFAULT, PcmGainMode.OFF)
    }
}
