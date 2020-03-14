package org.noise_planet.qrtoneapp

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    fun fromMillisToTimeString(millis: Long) : String {
        val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return format.format(millis)
    }
}