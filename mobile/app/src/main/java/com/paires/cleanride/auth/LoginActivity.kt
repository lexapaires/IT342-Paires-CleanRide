package com.paires.cleanride.auth

import com.paires.cleanride.R
import android.content.Intent
import com.paires.cleanride.booking.MainActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Handles user authentication and navigation to registration.
 * Follows standard Android Activity lifecycle and naming conventions.
 */
class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Fill the screen with your bg_main gradient
        setContentView(R.layout.login)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        val etEmail = findViewById<TextInputEditText>(R.id.email)
        val etPassword = findViewById<TextInputEditText>(R.id.password)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val btnRegister = findViewById<MaterialButton>(R.id.btnRegister)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (validateInput(email, password)) {
                btnLogin.isEnabled = false
                btnLogin.text = "Logging in..."
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    try {
                        val responseSuccess = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val url = java.net.URL("http://10.0.2.2:8081/api/v1/auth/login")
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.requestMethod = "POST"
                            connection.setRequestProperty("Content-Type", "application/json; utf-8")
                            connection.setRequestProperty("Accept", "application/json")
                            connection.doOutput = true

                            // Create JSON object utilizing native Android org.json
                            val jsonInput = org.json.JSONObject()
                            jsonInput.put("email", email)
                            jsonInput.put("password", password)

                            connection.outputStream.use { os ->
                                val input = jsonInput.toString().toByteArray(Charsets.UTF_8)
                                os.write(input, 0, input.size)
                            }

                            if (connection.responseCode in 200..299) {
                                val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                                if (responseString == "ADMIN_LOGIN_SUCCESS") {
                                    -1L
                                } else {
                                    val jsonResponse = org.json.JSONObject(responseString)
                                    jsonResponse.getLong("id")
                                }
                            } else {
                                null
                            }
                        }
                        if (responseSuccess != null) {
                            val sharedPref = getSharedPreferences("CleanRidePrefs", android.content.Context.MODE_PRIVATE)
                            with (sharedPref.edit()) {
                                putLong("userId", responseSuccess)
                                apply()
                            }
                            // Fetch and cache profile info for the Profile screen
                            try {
                                val profileUrl = java.net.URL("http://10.0.2.2:8081/api/v1/auth/user/$responseSuccess")
                                val profileConn = profileUrl.openConnection() as java.net.HttpURLConnection
                                profileConn.requestMethod = "GET"
                                if (profileConn.responseCode in 200..299) {
                                    val profileStr = profileConn.inputStream.bufferedReader().use { it.readText() }
                                    val profileJson = org.json.JSONObject(profileStr)
                                    with (sharedPref.edit()) {
                                        putString("firstName", profileJson.optString("firstName", ""))
                                        putString("lastName", profileJson.optString("lastName", ""))
                                        putString("username", profileJson.optString("username", ""))
                                        putString("email", profileJson.optString("email", ""))
                                        apply()
                                    }
                                }
                            } catch (e: Exception) { /* non-critical, profile screen will show N/A */ }
                            Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                            navigateToMain()
                        } else {
                            // Can be 401 Unauthorized
                            Toast.makeText(this@LoginActivity, "Invalid credentials", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@LoginActivity, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Log In"
                    }
                }
            }
        }

        btnRegister.setOnClickListener {
            // Navigate to RegisterActivity
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun validateInput(email: String, pass: String): Boolean {
        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Close LoginActivity so user can't "back" into it
    }
}