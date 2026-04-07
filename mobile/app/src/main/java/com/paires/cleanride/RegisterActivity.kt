package com.paires.cleanride

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.register)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        val etFirstName = findViewById<TextInputEditText>(R.id.etFirstName)
        val etLastName = findViewById<TextInputEditText>(R.id.etLastName)
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etBirthDate = findViewById<TextInputEditText>(R.id.etBirthDate)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnCreate = findViewById<MaterialButton>(R.id.btnCreateAccount)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)

        btnCreate.setOnClickListener {
            val fName = etFirstName.text.toString().trim()
            val lName = etLastName.text.toString().trim()
            val userN = etUsername.text.toString().trim()
            val birthD = etBirthDate.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (fName.isEmpty() || lName.isEmpty() || email.isEmpty() || pass.isEmpty() || userN.isEmpty() || birthD.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            } else {
                btnCreate.isEnabled = false
                btnCreate.text = "Creating..."
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    try {
                        var errorString = "Failed to connect"
                        val responseSuccess = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val url = java.net.URL("http://10.0.2.2:8081/api/v1/auth/register")
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.requestMethod = "POST"
                            connection.setRequestProperty("Content-Type", "application/json; utf-8")
                            connection.setRequestProperty("Accept", "application/json")
                            connection.doOutput = true

                            // Map the fields using native org.json.JSONObject
                            val jsonInput = org.json.JSONObject()
                            jsonInput.put("firstName", fName)
                            jsonInput.put("lastName", lName)
                            jsonInput.put("username", userN)
                            jsonInput.put("email", email)
                            jsonInput.put("password", pass)
                            jsonInput.put("birthDate", birthD)

                            connection.outputStream.use { os ->
                                val input = jsonInput.toString().toByteArray(Charsets.UTF_8)
                                os.write(input, 0, input.size)
                            }
                            
                            val code = connection.responseCode
                            if (code !in 200..299) {
                                // Extract error safely if possible
                                connection.errorStream?.bufferedReader()?.use { errorString = it.readText() }
                            }
                            code in 200..299
                        }
                        if (responseSuccess) {
                            Toast.makeText(this@RegisterActivity, "Welcome, $fName! Account created.", Toast.LENGTH_LONG).show()
                            // Navigate to Login after success
                            val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@RegisterActivity, errorString, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@RegisterActivity, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        btnCreate.isEnabled = true
                        btnCreate.text = "Create Account"
                    }
                }
            }
        }

        btnLogin.setOnClickListener {
            // Navigate back to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}