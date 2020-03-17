package org.noise_planet.qrtoneapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.edit
import androidx.preference.PreferenceManager

import kotlinx.android.synthetic.main.activity_main.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this /* Activity context */)
        val pseudonym = sharedPreferences.getString("pseudonym", "")
        if(pseudonym!!.isNotEmpty()) {
            val editText = findViewById<EditText>(R.id.username)
            editText.setText(pseudonym)
        }

        btnLogin.setOnClickListener {

            if (username.text.isNotEmpty()) {
                val user = username.text.toString()
                App.user = user
                val editor = sharedPreferences.edit()
                editor.putString("pseudonym", user)
                editor.apply()
                startActivity(Intent(this@MainActivity, ChatActivity::class.java))
            } else {
                val r = Random(3141597)
                App.user = byteArrayOf(r.nextInt('A'.toInt(),'Z'.toInt()).toByte()).toString(Charsets.UTF_8)
                startActivity(Intent(this@MainActivity, ChatActivity::class.java))
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
}
