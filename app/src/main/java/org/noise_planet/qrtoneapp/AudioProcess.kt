package org.noise_planet.qrtoneapp

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.util.Log
import org.noise_planet.qrtone.Configuration
import org.noise_planet.qrtone.QRTone
import java.beans.PropertyChangeSupport
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class AudioProcess constructor(
    private val recording: AtomicBoolean,
    snr: Double
) :
    Runnable {
    private var bufferSize = 0
    private var encoding = 0
    var rate = 0
    private var audioChannel = 0
    /**
     * @return Listener manager
     */
    val listeners = PropertyChangeSupport(this)
    private var processingThread = ProcessingThread(recording, listeners, snr)

    enum class STATE {
        WAITING, PROCESSING, WAITING_END_PROCESSING, CLOSED
    }

    private var currentState = STATE.WAITING

    fun getCurrentState(): STATE {
        return currentState
    }

    private fun setCurrentState(state: STATE) {
        val oldState = currentState
        currentState = state
        listeners.firePropertyChange(
            PROP_STATE_CHANGED,
            oldState,
            currentState
        )
    }

    private fun createAudioRecord(): AudioRecord? { // Source:
//  section 5.3 of the Android 4.0 Compatibility Definition
// https://source.android.com/compatibility/4.0/android-4.0-cdd.pdf
// Using VOICE_RECOGNITION
// Noise reduction processing, if present, is disabled.
// Except for 5.0+ where android.media.audiofx.NoiseSuppressor could be use to cancel such processing
// Automatic gain control, if present, is disabled.
        return if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                rate, audioChannel,
                encoding, bufferSize
            )
        } else {
            null
        }
    }

    override fun run() {
        try {
            setCurrentState(STATE.PROCESSING)
            val audioRecord = createAudioRecord()
            if (recording.get() && audioRecord != null) {
                try {
                    try {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                    } catch (ex: IllegalArgumentException) { // Ignore
                    } catch (ex: SecurityException) {
                    }
                    Thread(processingThread).start()
                    audioRecord.startRecording()

                    if(encoding == AudioFormat.ENCODING_PCM_16BIT) {
                        while (recording.get()) {
                            var buffer = ShortArray(bufferSize)
                            val read = audioRecord.read(buffer, 0, buffer.size)
                            if (read < buffer.size) {
                                buffer = buffer.copyOfRange(0, read)
                            }
                            processingThread.addSample(buffer)
                        }
                    } else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        while (recording.get()) {
                            var buffer = FloatArray(bufferSize)
                            val read = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                            if (read < buffer.size) {
                                buffer = buffer.copyOfRange(0, read)
                            }
                            processingThread.addSample(buffer)
                        }
                    }
                    setCurrentState(STATE.WAITING_END_PROCESSING)
                    while (processingThread.isProcessing()) {
                        Thread.sleep(10)
                    }
                } catch (ex: Exception) {
                    Log.e("tag_record", "Error while recording", ex)
                } finally {
                    if (audioRecord.state != AudioRecord.STATE_UNINITIALIZED) {
                        audioRecord.stop()
                        audioRecord.release()
                    }
                }
            }
        } finally {
            setCurrentState(STATE.CLOSED)
        }
    }

    class ProcessingThread(val recording : AtomicBoolean, val listeners : PropertyChangeSupport, val snr: Double) : Runnable {
        private val bufferToProcess: Queue<FloatArray> =
            ConcurrentLinkedQueue()
        private val processing = AtomicBoolean(false)
        private var sampleRate = 44100.0
        /**
         * Add Signed Short sound samples
         * @param sample
         */
        fun addSample(sample: ShortArray) {
            val fSamples = FloatArray(sample.size);
            for(i in sample.indices) {
                fSamples[i] = sample[i].div(Short.MAX_VALUE.toFloat());
            }
            bufferToProcess.add(fSamples)
        }

        fun addSample(sample: FloatArray) {
            bufferToProcess.add(sample)
        }

        fun init(sampleRate : Double) {
            this.sampleRate = sampleRate
        }

        fun isProcessing() : Boolean {
            return false;
        }

        fun processMessage(qrTone : QRTone) {
            val payload = qrTone.payload
            listeners.firePropertyChange(PROP_MESSAGE_RECEIVED, null, payload)
        }

        override fun run() {
            val qrTone = QRTone(Configuration(
                sampleRate,
                Configuration.DEFAULT_AUDIBLE_FIRST_FREQUENCY,
                0,
                Configuration.MULT_SEMITONE,
                Configuration.DEFAULT_WORD_TIME,
                snr,
                Configuration.DEFAULT_GATE_TIME,
                Configuration.DEFAULT_WORD_SILENCE_TIME
            ))
            while (recording.get()) {
                while (!bufferToProcess.isEmpty() && recording.get()) {
                    processing.set(true)
                    val buffer = bufferToProcess.poll()
                    if (buffer != null) {
                        if (buffer.size <= qrTone.maximumWindowLength) { // Good buffer size, use it
                            if(qrTone.pushSamples(buffer)) {
                                processMessage(qrTone)
                            }
                        } else {
                            // Buffer is too large for the window
                            // Split the buffer in multiple parts
                            var cursor = 0
                            while (cursor < buffer.size) {
                                val sampleLen = Math.min(
                                    qrTone.maximumWindowLength,
                                    buffer.size - cursor
                                )
                                val samples =
                                    Arrays.copyOfRange(buffer, cursor, cursor + sampleLen)
                                cursor += samples.size
                                if(qrTone.pushSamples(samples)) {
                                    // Got a message
                                    processMessage(qrTone)
                                }
                            }
                        }
                    }
                }
                Thread.sleep(15)
            }
            processing.set(false)
        }
    }

    companion object {
        const val PROP_STATE_CHANGED = "PROP_STATE_CHANGED"
        const val PROP_MESSAGE_RECEIVED = "PROP_MESSAGE_RECEIVED"
    }

    /**
     * Constructor
     * @param recording Recording state
     * @param canceled Canceled state
     * @param customLeqProcessing Custom receiver of sound signals
     */
    init {
        val mSampleRates =
            intArrayOf(48000, 44100, 22050, 16000)
        val encodings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intArrayOf(
                AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_16BIT
            )
        } else {
            intArrayOf(
                AudioFormat.ENCODING_PCM_16BIT
            )
        }
        val audioChannels = shortArrayOf(
            AudioFormat.CHANNEL_IN_MONO.toShort()
        )
        tryLoop@ for (tryRate in mSampleRates) {
            for (tryEncoding in encodings) {
                for (tryAudioChannel in audioChannels) {
                    val tryBufferSize = AudioRecord.getMinBufferSize(
                        tryRate,
                        tryAudioChannel.toInt(), tryEncoding
                    )
                    var bufferSampleSize = Short.SIZE_BYTES;
                    if(tryEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        bufferSampleSize = 4;
                    }
                    // Take a higher buffer size in order to get a smooth recording under load
                    // avoiding Buffer overflow error on AudioRecord side.
                    if (tryBufferSize != AudioRecord.ERROR_BAD_VALUE) {
                        bufferSize = max(
                            tryBufferSize,
                            bufferSampleSize * 2048
                        )
                        encoding = tryEncoding
                        audioChannel = tryAudioChannel.toInt()
                        rate = tryRate
                        break@tryLoop
                    }
                }
            }
        }
        processingThread.init(rate.toDouble())
    }
}

