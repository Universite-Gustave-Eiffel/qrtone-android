package org.noise_planet.qrtoneapp

import android.os.Bundle

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_chat.*
import java.util.*

private const val TAG = "ChatActivity"

class ChatActivity : AppCompatActivity() {
    private lateinit var adapter: MessageAdapter

    private val pusherAppKey = "PUSHER_APP_KEY"
    private val pusherAppCluster = "PUSHER_APP_CLUSTER"

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
                // TODO push message
            } else {
                Toast.makeText(applicationContext,"Message should not be empty", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetInput() {
        // Clean text box
        txtMessage.text.clear()

        // Hide keyboard
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(currentFocus!!.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    private fun onNewData( user: String, message: String, time: Long) {

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
}