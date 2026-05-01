package com.paires.cleanride.booking

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
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
import java.net.HttpURLConnection
import java.net.URL

class WizardActivity : AppCompatActivity() {

    private var selectedDate: String = ""
    private var selectedSlot: Int = -1
    private var userId: Long = -1

    private var basePrice = 100
    private var vehicleSurcharge = 0

    private lateinit var tvTotalAmount: TextView
    private lateinit var btnConfirm: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_wizard)

        selectedDate = intent.getStringExtra("SELECTED_DATE") ?: ""
        selectedSlot = intent.getIntExtra("SELECTED_SLOT", -1)

        val sharedPref = getSharedPreferences("CleanRidePrefs", Context.MODE_PRIVATE)
        userId = sharedPref.getLong("userId", -1)

        if (userId == -1L) {
            Toast.makeText(this, "Please log in to book a slot.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val tvDateTime = findViewById<TextView>(R.id.tvDateTime)
        tvDateTime.text = "$selectedDate (Slot $selectedSlot)"

        tvTotalAmount = findViewById(R.id.tvTotalAmount)
        btnConfirm = findViewById(R.id.btnConfirm)

        val rgServiceType = findViewById<RadioGroup>(R.id.rgServiceType)
        val rgVehicleType = findViewById<RadioGroup>(R.id.rgVehicleType)

        rgServiceType.setOnCheckedChangeListener { _, checkedId ->
            basePrice = if (checkedId == R.id.rbSelfService) 100 else 150
            updateTotal()
        }

        rgVehicleType.setOnCheckedChangeListener { _, checkedId ->
            vehicleSurcharge = when (checkedId) {
                R.id.rb4Seater -> 0
                R.id.rb7Seater -> 100
                R.id.rbMaxSeater -> 200
                else -> 0
            }
            updateTotal()
        }

        btnConfirm.setOnClickListener {
            submitBooking()
        }
    }

    private fun updateTotal() {
        val total = basePrice + vehicleSurcharge
        tvTotalAmount.text = "₱$total.00"
    }

    private fun submitBooking() {
        val rgService = findViewById<RadioGroup>(R.id.rgServiceType)
        val rgVehicle = findViewById<RadioGroup>(R.id.rgVehicleType)

        val serviceType = if (rgService.checkedRadioButtonId == R.id.rbSelfService) "SELF_SERVICE" else "FULL_SERVICE"
        val vehicleType = when (rgVehicle.checkedRadioButtonId) {
            R.id.rb4Seater -> "4_SEATER"
            R.id.rb7Seater -> "7_SEATER"
            else -> "MAX_SEATER"
        }
        val totalPrice = basePrice + vehicleSurcharge

        btnConfirm.isEnabled = false
        btnConfirm.text = "Booking..."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val url = URL("http://10.0.2.2:8081/api/v1/bookings")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json; utf-8")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.doOutput = true

                    val jsonInput = JSONObject()
                    jsonInput.put("userId", userId)
                    jsonInput.put("bookingDate", selectedDate)
                    jsonInput.put("timeSlot", selectedSlot)
                    jsonInput.put("serviceType", serviceType)
                    jsonInput.put("vehicleType", vehicleType)
                    jsonInput.put("totalPrice", totalPrice)

                    connection.outputStream.use { os ->
                        val input = jsonInput.toString().toByteArray(Charsets.UTF_8)
                        os.write(input, 0, input.size)
                    }

                    if (connection.responseCode in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        null
                    }
                }

                if (responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val priorityNumber = jsonResponse.getString("priorityNumber")
                    showReceiptDialog(priorityNumber)
                } else {
                    Toast.makeText(this@WizardActivity, "Booking failed. Slot may be full.", Toast.LENGTH_LONG).show()
                    btnConfirm.isEnabled = true
                    btnConfirm.text = "Confirm Booking"
                }

            } catch (e: Exception) {
                Toast.makeText(this@WizardActivity, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                btnConfirm.isEnabled = true
                btnConfirm.text = "Confirm Booking"
            }
        }
    }

    private fun showReceiptDialog(priorityNumber: String) {
        AlertDialog.Builder(this)
            .setTitle("Booking Confirmed!")
            .setMessage("Your Priority Number is:\n\n$priorityNumber\n\nPlease arrive 10 minutes before your slot.")
            .setPositiveButton("OK") { _, _ ->
                finish() // Return to MainActivity
            }
            .setCancelable(false)
            .show()
    }
}
