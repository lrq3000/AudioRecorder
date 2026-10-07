package com.dimowner.audiorecorder.v2.audio

import android.media.AudioRecord
import com.dimowner.audiorecorder.v2.data.model.AudioSource
import com.dimowner.audiorecorder.v2.data.model.PcmGainMode

/** Frozen configuration and owned effects for one capture, shared by WAV and direct AAC. */
internal class CaptureProcessingSession(
    recorder: AudioRecord,
    input: AudioInput,
    sampleRate: Int,
    channelCount: Int,
    backend: String,
    private val diagnostics: CaptureDiagnostics,
) : AutoCloseable, PcmProcessor {
    private val mic = input as? AudioInput.Mic
    private val effects = mic?.let { InputAudioEffects(recorder.audioSessionId, it.preprocessing) }
    private val processor = PcmProcessor.create(mic?.gain ?: PcmGainMode.OFF, sampleRate, channelCount)
    private val summary = buildString {
        appendLine("Backend: $backend / AudioRecord")
        appendLine(describeInput(input))
        appendLine("Requested: $sampleRate Hz, $channelCount channel(s)")
        appendLine("AudioRecord: session=${recorder.audioSessionId}, ${recorder.sampleRate} Hz, ${recorder.channelCount} channel(s)")
        effects?.status?.forEach { appendLine(it) }
        if (mic == null) appendLine("Microphone effects and software gain not applied to system playback.")
        append("Java effect states do not prove that headset/OEM processing is bypassed.")
    }

    init { diagnostics.session(summary) }

    fun started(recorder: AudioRecord) {
        val routed = runCatching { recorder.routedDevice }.getOrNull()
        diagnostics.session(summary + "\nObserved input: " +
            (routed?.let { "${it.productName} (type=${it.type}, id=${it.id})" } ?: "not yet reported by Android"))
    }

    override fun processInPlace(buffer: ByteArray, bytesRead: Int) = processor.processInPlace(buffer, bytesRead)
    override fun close() { effects?.close() }

    companion object {
        fun describeInput(input: AudioInput): String = when (input) {
            is AudioInput.Mic -> "Source: ${AudioSource.fromValue(input.audioSource)}\n" +
                "Preprocessing: ${input.preprocessing}; software gain: ${input.gain}" +
                if (input.audioSource == AudioSource.UNPROCESSED.value) "\nUNPROCESSED may fall back on this device." else ""
            is AudioInput.SystemPlayback -> "Source: SYSTEM_AUDIO"
        }
    }
}
