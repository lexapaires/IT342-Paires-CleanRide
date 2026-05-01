package com.paires.cleanride.booking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.paires.cleanride.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ProfileActivity : AppCompatActivity() {

    private var userId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        val sharedPref = getSharedPreferences("CleanRidePrefs", Context.MODE_PRIVATE)
        userId = sharedPref.getLong("userId", -1)

        populateProfileFromPrefs(sharedPref)
        setupNavigation()
        setupActions()
    }

    private fun populateProfileFromPrefs(sharedPref: android.content.SharedPreferences) {
        val firstName = sharedPref.getString("firstName", "") ?: ""
        val lastName = sharedPref.getString("lastName", "") ?: ""
        val username = sharedPref.getString("username", "N/A") ?: "N/A"
        val email = sharedPref.getString("email", "N/A") ?: "N/A"
        val fullName = "$firstName $lastName".trim()

        findViewById<TextView>(R.id.tvProfileName).text = fullName.ifBlank { "User" }
        findViewById<TextView>(R.id.tvFullName).text = fullName.ifBlank { "N/A" }
        findViewById<TextView>(R.id.tvUsername).text = username
        findViewById<TextView>(R.id.tvEmail).text = email

        // Avatar initials — first letter of first name
        val initial = if (firstName.isNotBlank()) firstName[0].uppercase() else "U"
        findViewById<TextView>(R.id.tvAvatar).text = initial
    }

    private fun setupNavigation() {
        findViewById<LinearLayout>(R.id.navCalendar).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
        findViewById<LinearLayout>(R.id.navGarage).setOnClickListener {
            startActivity(Intent(this, MyBookingsActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
        findViewById<LinearLayout>(R.id.navReviews).setOnClickListener {
            startActivity(Intent(this, MyReviewsActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
        // navProfile is active
    }

    private fun setupActions() {
        // Save Password
        val btnSave = findViewById<MaterialButton>(R.id.btnSavePassword)
        val etNewPassword = findViewById<android.widget.EditText>(R.id.etNewPassword)

        btnSave.setOnClickListener {
            val newPassword = etNewPassword.text.toString().trim()
            if (newPassword.isEmpty()) {
                Toast.makeText(this, "Please enter a new password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPassword.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.text = "Saving..."
            btnSave.isEnabled = false
            updatePassword(newPassword, btnSave, etNewPassword)
        }

        // Logout
        val btnLogout = findViewById<MaterialButton>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            // Clear all stored prefs
            val sharedPref = getSharedPreferences("CleanRidePrefs", Context.MODE_PRIVATE)
            sharedPref.edit().clear().apply()

            val intent = Intent(this, com.paires.cleanride.auth.LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun updatePassword(
        newPassword: String,
        btn: MaterialButton,
        etPassword: android.widget.EditText
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    val url = URL("http://10.0.2.2:8081/api/v1/auth/password")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "PUT"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true

                    val body = JSONObject().apply {
                        put("userId", userId)
                        put("newPassword", newPassword)
                    }.toString()

                    OutputStreamWriter(connection.outputStream).use { it.write(body) }

                    connection.responseCode in 200..299
                }

                if (success) {
                    Toast.makeText(this@ProfileActivity, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                    etPassword.text?.clear()
                } else {
                    Toast.makeText(this@ProfileActivity, "Failed to update password.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btn.text = "Save Password"
                btn.isEnabled = true
            }
        }
    }
}
