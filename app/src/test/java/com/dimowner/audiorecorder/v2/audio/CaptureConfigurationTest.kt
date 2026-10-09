package com.dimowner.audiorecorder.v2.audio

import android.media.projection.MediaProjection
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class CaptureConfigurationTest {
    @Test fun `missing system consent is rejected rather than recording the microphone`() {
        val result = CaptureConfiguration.resolveInput(AudioSource.SYSTEM_AUDIO, InputPreprocessingPolicy.SYSTEM_DEFAULT,
            PcmGainMode.OFF, true, null)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("consent"))
    }
    @Test fun `unsupported system capture is rejected rather than changing source`() {
        assertTrue(CaptureConfiguration.resolveInput(AudioSource.SYSTEM_AUDIO, InputPreprocessingPolicy.SYSTEM_DEFAULT,
            PcmGainMode.OFF, false, null).isFailure)
    }
    @Test fun `microphone choices reach the recorder unchanged`() {
        val input = CaptureConfiguration.resolveInput(AudioSource.VOICE_RECOGNITION, InputPreprocessingPolicy.AGC_ONLY,
            PcmGainMode.AUTO_LEVEL, false, null).getOrThrow()
        assertEquals(AudioInput.Mic(AudioSource.VOICE_RECOGNITION.value, InputPreprocessingPolicy.AGC_ONLY, PcmGainMode.AUTO_LEVEL), input)
    }
    @Test fun `valid system capture keeps its projection`() {
        val projection = mockk<MediaProjection>()
        assertEquals(AudioInput.SystemPlayback(projection), CaptureConfiguration.resolveInput(AudioSource.SYSTEM_AUDIO,
            InputPreprocessingPolicy.AGC_ONLY, PcmGainMode.AUTO_LEVEL, true, projection).getOrThrow())
    }
    @Test fun `3GP rejects system audio instead of substituting M4A`() {
        assertNotNull(CaptureConfiguration.configurationProblem(RecordingFormat.ThreeGp, 16000, 1,
            AudioInput.SystemPlayback(mockk())))
    }
    @Test fun `MediaRecorder refuses every non-default processing policy`() {
        InputPreprocessingPolicy.entries.filter { it != InputPreprocessingPolicy.SYSTEM_DEFAULT }.forEach {
            assertNotNull(CaptureConfiguration.mediaRecorderProblem(AudioInput.Mic(AudioSource.MIC.value, it)))
        }
    }
    @Test fun `MediaRecorder refuses every enabled software gain`() {
        PcmGainMode.entries.filter { it != PcmGainMode.OFF }.forEach {
            assertNotNull(CaptureConfiguration.mediaRecorderProblem(AudioInput.Mic(AudioSource.MIC.value, gain = it)))
        }
    }
    @Test fun `MediaRecorder accepts system-managed microphone processing with gain off`() {
        assertNull(CaptureConfiguration.mediaRecorderProblem(AudioInput.Mic(AudioSource.VOICE_RECOGNITION.value)))
    }
    @Test fun `3GP cannot silently replace unsupported rate or stereo`() {
        assertNotNull(CaptureConfiguration.configurationProblem(RecordingFormat.ThreeGp, 44100, 1, AudioInput.Mic(1)))
        assertNotNull(CaptureConfiguration.configurationProblem(RecordingFormat.ThreeGp, 16000, 2, AudioInput.Mic(1)))
    }
    @Test fun `AudioRecord formats accept the selected microphone processing`() {
        val input = AudioInput.Mic(AudioSource.VOICE_RECOGNITION.value, InputPreprocessingPolicy.AGC_ONLY, PcmGainMode.AUTO_LEVEL)
        assertNull(CaptureConfiguration.configurationProblem(RecordingFormat.Wav, 16000, 1, input))
        assertNull(CaptureConfiguration.configurationProblem(RecordingFormat.M4a, 16000, 1, input))
    }
    @Test fun `AAC rejects an unattainable selected bitrate instead of lowering it`() {
        assertFalse(CaptureConfiguration.supportsAacBitrate(288000, 8000, 1, 960000))
        assertFalse(CaptureConfiguration.supportsAacBitrate(192000, 48000, 2, 96000))
        assertFalse(CaptureConfiguration.supportsAacBitrate(0, 48000, 2, 960000))
        assertTrue(CaptureConfiguration.supportsAacBitrate(192000, 48000, 2, 960000))
        assertTrue(CaptureConfiguration.supportsAacBitrate(48000, 8000, 1, 960000))
    }
}
