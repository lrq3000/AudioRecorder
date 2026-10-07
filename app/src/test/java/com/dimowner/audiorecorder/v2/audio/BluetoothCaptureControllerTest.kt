package com.dimowner.audiorecorder.v2.audio

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Looper
import com.dimowner.audiorecorder.v2.data.model.BluetoothAudioMode
import com.dimowner.audiorecorder.v2.data.model.BluetoothCaptureRoute
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class BluetoothCaptureControllerTest {
    private val manager = mockk<AudioManager>(relaxed = true)
    private val bluetooth = mockk<BluetoothManager>()
    private val adapter = mockk<BluetoothAdapter>(relaxed = true)
    private val proxy = mockk<BluetoothHeadset>(relaxed = true)
    private val headset = mockk<BluetoothDevice>(relaxed = true)
    private val input = mockk<AudioDeviceInfo>(relaxed = true)
    private val listener = slot<BluetoothProfile.ServiceListener>()
    private lateinit var context: Context
    private lateinit var controller: BluetoothCaptureController

    @Before fun setUp() {
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? = when (name) {
                Context.AUDIO_SERVICE -> manager
                Context.BLUETOOTH_SERVICE -> bluetooth
                else -> super.getSystemService(name)
            }
        }
        every { manager.mode } returns AudioManager.MODE_NORMAL
        every { bluetooth.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.getProfileProxy(any(), capture(listener), BluetoothProfile.HEADSET) } returns true
        every { proxy.connectedDevices } returns listOf(headset)
        every { proxy.startVoiceRecognition(headset) } returns true
        every { headset.name } returns "Test headset"
        every { headset.address } returns "00:11:22:33:44:55"
        every { input.address } returns "00:11:22:33:44:55"
        controller = BluetoothCaptureController(context, CaptureDiagnostics()) {}
    }

    @Test fun `SCO request does not become ready after a blind delay`() {
        controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.IN_COMMUNICATION, input)
        verify { manager.startBluetoothSco() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(BluetoothRoutePhase.CONNECTING, controller.state.value.phase)
        context.sendBroadcast(Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED).putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_CONNECTED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(BluetoothRoutePhase.READY, controller.state.value.phase)
        controller.stop()
        verify { manager.stopBluetoothSco() }
    }

    @Test fun `legacy normal mode is selected and timeout is explicit`() {
        controller.start(BluetoothCaptureRoute.STANDARD_SCO, BluetoothAudioMode.NORMAL, input)
        verify(exactly = 0) { manager.mode = AudioManager.MODE_IN_COMMUNICATION }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
        assertEquals(BluetoothRoutePhase.FAILED, controller.state.value.phase)
        assertTrue(controller.state.value.message.contains("Timed out"))
        verify { manager.stopBluetoothSco() }
    }

    @Test fun `HFP requests voice recognition and readiness follows audio state`() {
        controller.start(BluetoothCaptureRoute.HFP_VOICE_RECOGNITION, BluetoothAudioMode.IN_COMMUNICATION, input)
        listener.captured.onServiceConnected(BluetoothProfile.HEADSET, proxy)
        verify { proxy.startVoiceRecognition(headset) }
        verify(exactly = 0) { manager.startBluetoothSco() }
        assertEquals(BluetoothRoutePhase.CONNECTING, controller.state.value.phase)
        // A profile query can confirm the same real connection state as its broadcast.
        every { proxy.isAudioConnected(headset) } returns true
        controller.stop()
        verify { proxy.stopVoiceRecognition(headset) }
        verify { adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy) }
    }

    @Test fun `HFP rejection never silently falls back to SCO`() {
        every { proxy.startVoiceRecognition(headset) } returns false
        controller.start(BluetoothCaptureRoute.HFP_VOICE_RECOGNITION, BluetoothAudioMode.NORMAL, input)
        listener.captured.onServiceConnected(BluetoothProfile.HEADSET, proxy)
        assertEquals(BluetoothRoutePhase.FAILED, controller.state.value.phase)
        assertTrue(controller.state.value.message.contains("returned false"))
        verify(exactly = 0) { manager.startBluetoothSco() }
        verify { adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy) }
    }

    @Test fun `missing HFP headset is explicit and profile is closed`() {
        every { proxy.connectedDevices } returns emptyList()
        controller.start(BluetoothCaptureRoute.HFP_VOICE_RECOGNITION, BluetoothAudioMode.NORMAL, input)
        listener.captured.onServiceConnected(BluetoothProfile.HEADSET, proxy)
        assertTrue(controller.state.value.message.contains("No connected HFP"))
        verify(exactly = 0) { proxy.startVoiceRecognition(any()) }
        verify { adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy) }
    }

    @Test fun `late profile callback after cancellation cannot enable microphone`() {
        controller.start(BluetoothCaptureRoute.HFP_VOICE_RECOGNITION, BluetoothAudioMode.NORMAL, input)
        controller.stop()
        listener.captured.onServiceConnected(BluetoothProfile.HEADSET, proxy)
        verify(exactly = 0) { proxy.startVoiceRecognition(any()) }
        verify { adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy) }
    }

    @Test fun `HFP never substitutes a different connected headset`() {
        every { input.address } returns "00:99:88:77:66:55"
        controller.start(BluetoothCaptureRoute.HFP_VOICE_RECOGNITION, BluetoothAudioMode.NORMAL, input)
        listener.captured.onServiceConnected(BluetoothProfile.HEADSET, proxy)
        assertEquals(BluetoothRoutePhase.FAILED, controller.state.value.phase)
        assertTrue(controller.state.value.message.contains("no device fallback"))
        verify(exactly = 0) { proxy.startVoiceRecognition(any()) }
        verify { adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy) }
    }
}
