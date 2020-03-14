package org.noise_planet.qrtoneapp

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_chat.*
import org.noise_planet.qrtone.Configuration
import org.noise_planet.qrtone.QRTone
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class ChatActivity : AppCompatActivity() {
    private lateinit var adapter: MessageAdapter
    private var audioTrack: AudioTrack? = null
    var canceled = AtomicBoolean(false)

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
                onNewData(message.user, message.message, message.time)
                playMessage(craftMessage(App.user, txtMessage.text.toString()))
                resetInput(false)
            } else {
                Toast.makeText(applicationContext,"Message should not be empty", Toast.LENGTH_SHORT).show()
            }
        }
    }

    enum class ATTRIBUTE {
        USERNAME, MESSAGE
    }

    @Throws(IOException::class)
    fun craftMessage(userName: String, message: String): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val writer = DataOutputStream(byteArrayOutputStream)
        // Username
        writer.writeByte(ATTRIBUTE.USERNAME.ordinal)
        val userNameBytes = userName.toByteArray()
        writer.writeByte(userNameBytes.size)
        writer.write(userNameBytes)
        // message
        writer.writeByte(ATTRIBUTE.MESSAGE.ordinal)
        val messageBytes = message.toByteArray()
        writer.writeByte(messageBytes.size)
        writer.write(messageBytes)
        writer.flush()
        return byteArrayOutputStream.toByteArray()
    }

    private fun getAudioOutput(): Int {
        return AudioManager.STREAM_RING
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
        canceled.set(false)
        newTrack.play()
        val toneFeed = ToneFeed(newTrack, payload, canceled)
        Thread(toneFeed).start()
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
        canceled.set(true)
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
        private val audioTrack: AudioTrack, private val payload: ByteArray,  private val canceled: AtomicBoolean
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
            while (!canceled.get() && cursor < samples) {
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
        val BUFFER_SIZE = 1024
    }
}