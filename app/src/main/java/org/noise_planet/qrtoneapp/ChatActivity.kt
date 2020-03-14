package org.noise_planet.qrtoneapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_chat.*
import org.noise_planet.qrtone.Configuration
import org.noise_planet.qrtone.QRTone
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class ChatActivity : AppCompatActivity(), PropertyChangeListener {
    private lateinit var adapter: MessageAdapter
    private var audioTrack: AudioTrack? = null
    var listening = AtomicBoolean(true)
    val PERMISSION_RECORD_AUDIO = 1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        messageList.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter(this)
        messageList.adapter = adapter

        btnSend.setOnClickListener {
            if(txtMessage.text.isNotEmpty()) {
                val message = Message(
                        App.user,
                        txtMessage.text.toString(),
                        Calendar.getInstance().timeInMillis
                )
                playMessage(message.craftMessage())
                resetInput(false)
            } else {
                Toast.makeText(applicationContext,"Message should not be empty", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun initAudioProcess() {
        listening.set(true)
        var audioProcess = AudioProcess(listening)
        audioProcess.listeners.addPropertyChangeListener(this)
        // Start listening messages
        Thread(audioProcess).start()
    }

    private fun getAudioOutput(): Int {
        return AudioManager.STREAM_MUSIC
    }

    private fun playMessage(payload: ByteArray) {
        val oldTrack = audioTrack
        if (oldTrack != null) {
            oldTrack.pause()
            oldTrack.flush()
            oldTrack.release()
        }
        val newTrack =  AudioTrack(
            getAudioOutput(), 44100, AudioFormat
                .CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE * (java.lang.Short
                .SIZE / java.lang.Byte.SIZE), AudioTrack.MODE_STREAM
        )
        audioTrack = newTrack
        newTrack.play()
        val toneFeed = ToneFeed(newTrack, payload, listening)
        Thread(toneFeed).start()
    }


    override fun onPostResume() {
        super.onPostResume()
        if(checkAndAskPermissions()) {
            initAudioProcess()
        }
    }

    /**
     * If necessary request user to acquire permisions for critical ressources (gps and microphone)
     * @return True if service can be bind immediately. Otherwise the bind should be done using the
     * @see .onRequestPermissionsResult
     */
    protected fun checkAndAskPermissions(): Boolean {
        if (ContextCompat.checkSelfPermission( this, Manifest.permission.RECORD_AUDIO  )
            != PackageManager.PERMISSION_GRANTED ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.RECORD_AUDIO
                )
            ) { // After the user
                // sees the explanation, try again to request the permission.
                Toast.makeText(
                    this,
                    R.string.permission_explain_audio_record, Toast.LENGTH_LONG
                ).show()
            }
            // Request the permission.
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),
                PERMISSION_RECORD_AUDIO
            )
            return false
        }
        return true
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_RECORD_AUDIO -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    initAudioProcess()
                } else { // permission denied
                    // Ask again
                    checkAndAskPermissions()
                }
            }
        }
    }

    private fun resetInput(hideKeyboard: Boolean) {
        // Clean text box
        txtMessage.text.clear()

        if(hideKeyboard) {
            // Hide keyboard
            val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputManager.hideSoftInputFromWindow(currentFocus!!.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        }
    }

    override fun onPause() {
        super.onPause()
        listening.set(false)
    }

    override fun propertyChange(evt: PropertyChangeEvent?) {
        if(evt?.propertyName == AudioProcess.PROP_MESSAGE_RECEIVED) {
            val bytes = evt.newValue
            if(bytes is ByteArray) {
                val message = Message.fromBytes(bytes)
                onNewData(message.user, message.message, message.time)
            }
        }
    }

    private fun onNewData(user: String, message: String, time: Long) {

            val message = Message(
                user,
                message,
                time
            )

            runOnUiThread {
                adapter.addMessage(message)
                // scroll the RecyclerView to the last added element
                messageList.scrollToPosition(adapter.itemCount - 1)
            }
    }

    private class ToneFeed(
        private val audioTrack: AudioTrack, private val payload: ByteArray,  private val activated: AtomicBoolean
    ) : Runnable {


        @Throws(IOException::class)
        fun doubleToShort(signal: FloatArray) : ShortArray {
            val shortSignal = ShortArray(signal.size)
            for (i in signal.indices) {
                shortSignal[i] = Math.min(Short.MAX_VALUE.toInt(), Math.max(Short.MIN_VALUE.toInt(), signal[i].toInt())).toShort()
            }
            return shortSignal
        }

        override fun run() {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (ex: IllegalArgumentException) {
                // Ignore
            } catch (ex: SecurityException) {
            }

            val qrTone = QRTone(Configuration.getAudible(audioTrack.sampleRate.toDouble()))
            val samples = qrTone.setPayload(payload)
            var cursor = 0
            while (activated.get() && cursor < samples) {
                val windowLength = Math.min(samples - cursor, BUFFER_SIZE)
                val fSamples = FloatArray(windowLength)
                qrTone.getSamples(fSamples, cursor, Short.MAX_VALUE / 2.0)
                val buffer = doubleToShort(fSamples)
                try {
                    audioTrack.write(buffer, 0, buffer.size)
                } catch (ex: IllegalStateException) {
                    return
                }
                cursor += buffer.size
            }
            try {
                audioTrack.stop()
            } catch (ex: IllegalStateException) {
                // AudioTrack has been unloaded
            }
        }
    }

    companion object{
        const val BUFFER_SIZE = 1024
    }
}