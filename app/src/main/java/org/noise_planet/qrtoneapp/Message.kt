package org.noise_planet.qrtoneapp

import java.io.*
import java.util.*

data class Message(var user:String,
                   var message:String,
                   var time:Long) {

    enum class ATTRIBUTE {
        USERNAME, MESSAGE
    }

    @Throws(IOException::class)
    fun craftMessage(): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val writer = DataOutputStream(byteArrayOutputStream)
        // Username
        writer.writeByte(ATTRIBUTE.USERNAME.ordinal)
        val userNameBytes = user.toByteArray()
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

    companion object {
        fun fromBytes(data : ByteArray) : Message {
            val bis = ByteArrayInputStream(data)
            val reader = DataInputStream(bis)
            var userName  = ""
            var message  = ""
            try {
                while (userName.isEmpty() || message.isEmpty()) {
                    val messageId = reader.readByte()
                    when(messageId.toInt()) {
                        ATTRIBUTE.USERNAME.ordinal -> {
                            val userNameBytes = ByteArray(reader.readByte().toInt())
                            reader.read(userNameBytes)
                            userName = userNameBytes.toString(Charsets.UTF_8)
                        }
                        ATTRIBUTE.MESSAGE.ordinal -> {
                            val messageNameBytes = ByteArray(reader.readByte().toInt())
                            reader.read(messageNameBytes)
                            message = messageNameBytes.toString(Charsets.UTF_8)
                        }
                    }
                }
            } catch (ex : IOException) {
                // Ignore
            }
            return Message(userName, message, Calendar.getInstance().timeInMillis)
        }
    }
}