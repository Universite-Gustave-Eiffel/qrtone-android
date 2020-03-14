package org.noise_planet.qrtoneapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


private const val VIEW_TYPE_OUTGOING_MESSAGE = 1
private const val VIEW_TYPE_INCOMING_MESSAGE = 2

class MessageAdapter (val context: Context) : RecyclerView.Adapter<MessageViewHolder>() {
    private val messages: ArrayList<Message> = ArrayList()

    fun addMessage(message: Message){
        messages.add(message)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages.get(position)

        return if (App.user == message.user) {
            VIEW_TYPE_OUTGOING_MESSAGE
        } else {
            VIEW_TYPE_INCOMING_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return if(viewType == VIEW_TYPE_OUTGOING_MESSAGE) {
            OutgoingMessageViewHolder(LayoutInflater.from(context).inflate(R.layout.outgoing_message_item, parent, false))
        } else {
            IncomingMessageViewHolder(LayoutInflater.from(context).inflate(R.layout.incoming_message_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages.get(position)

        holder?.bind(message)
    }

    inner class OutgoingMessageViewHolder (view: View) : MessageViewHolder(view) {
        private var messageText: TextView = view.findViewById(R.id.message)
        private var timeText: TextView = view.findViewById(R.id.messageTime)

        override fun bind(message: Message) {
            messageText.text = message.message
            timeText.text = DateUtils.fromMillisToTimeString(message.time)
        }
    }

    inner class IncomingMessageViewHolder (view: View) : MessageViewHolder(view) {
        private var messageText: TextView = view.findViewById(R.id.message)
        private var userText: TextView = view.findViewById(R.id.username)
        private var timeText: TextView = view.findViewById(R.id.messageTime)

        override fun bind(message: Message) {
            messageText.text = message.message
            userText.text = message.user
            timeText.text = DateUtils.fromMillisToTimeString(message.time)
        }
    }
}

open class MessageViewHolder (view: View) : RecyclerView.ViewHolder(view) {
    open fun bind(message:Message) {}
}
