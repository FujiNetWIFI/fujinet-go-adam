package online.fujinet.go.adam

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import online.fujinet.go.adam.core.EmulatorNative
import kotlin.concurrent.thread

/**
 * Streams ADAMEm's PSG output to an AudioTrack. A feeder thread repeatedly asks
 * the native layer (which invokes ADAMEm's `soundData` generator) for the next
 * block of mono 44100 Hz signed-16 samples and writes them out.
 */
class AudioOutput {
    private companion object {
        const val SAMPLE_RATE = 44100
        const val BLOCK_FRAMES = 1024
        const val TAG = "FujiAdamAudio"
    }

    @Volatile private var running = false
    private var feeder: Thread? = null
    private var track: AudioTrack? = null

    fun start() {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(BLOCK_FRAMES * 2 * 4)

        track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            minBuf,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        ).also { it.play() }

        running = true
        feeder = thread(name = "adam-audio") {
            val buffer = ShortArray(BLOCK_FRAMES)
            while (running) {
                try {
                    val n = EmulatorNative.nativeRenderAudio(buffer)
                    if (n > 0) {
                        track?.write(buffer, 0, n, AudioTrack.WRITE_BLOCKING)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "audio feeder error", t)
                    break
                }
            }
        }
    }

    fun stop() {
        running = false
        feeder?.join(500)
        feeder = null
        track?.run { stop(); release() }
        track = null
    }
}
