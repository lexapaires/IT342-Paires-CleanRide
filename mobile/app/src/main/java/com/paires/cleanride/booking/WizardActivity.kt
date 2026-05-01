package com.paires.cleanride.booking

import android.app.AlertDialog
import android.content.Context
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
import java.net.HttpURLConnection
import java.net.URL

class WizardActivity : AppCompatActivity() {

    private var selectedDate: String = ""
    private var selectedSlot: Int = -1
    private var userId: Long = -1

    private var serviceType = "SELF_SERVICE"
    private var vehicleType = "4_SEATER"

    private var basePrice = 250
    private var vehicleSurcharge = 100

    private lateinit var tvTotalAmount: TextView
    private lateinit var tvSur4: TextView
    private lateinit var tvSur7: TextView
    private lateinit var tvSurMax: TextView

    private lateinit var cardSelfService: LinearLayout
    private lateinit var cardFullService: LinearLayout
    private lateinit var card4Seater: LinearLayout
    private lateinit var card7Seater: LinearLayout
    private lateinit var cardMaxSeater: LinearLayout

    private lateinit var btnConfirm: MaterialButton
    private lateinit var btnCancel: MaterialButton

    // Pricing Matrix
    private val surcharges = mapOf(
        "SELF_SERVICE" to mapOf("4_SEATER" to 100, "7_SEATER" to 130, "GREATER_THAN_7_SEATER" to 160),
        "FULL_SERVICE" to mapOf("4_SEATER" to 130, "7_SEATER" to 160, "GREATER_THAN_7_SEATER" to 190)
    )

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
        tvSur4 = findViewById(R.id.tvSur4)
        tvSur7 = findViewById(R.id.tvSur7)
        tvSurMax = findViewById(R.id.tvSurMax)
        
        btnConfirm = findViewById(R.id.btnConfirm)
        btnCancel = findViewById(R.id.btnCancel)

        cardSelfService = findViewById(R.id.cardSelfService)
        cardFullService = findViewById(R.id.cardFullService)
        card4Seater = findViewById(R.id.card4Seater)
        card7Seater = findViewById(R.id.card7Seater)
        cardMaxSeater = findViewById(R.id.cardMaxSeater)

        setupClickListeners()
        updateUI() // Initial State
    }

    private fun setupClickListeners() {
        cardSelfService.setOnClickListener {
            serviceType = "SELF_SERVICE"
            basePrice = 250
            updateUI()
        }

        cardFullService.setOnClickListener {
            serviceType = "FULL_SERVICE"
            basePrice = 350
            updateUI()
        }

        card4Seater.setOnClickListener {
            vehicleType = "4_SEATER"
            updateUI()
        }

        card7Seater.setOnClickListener {
            vehicleType = "7_SEATER"
            updateUI()
        }

        cardMaxSeater.setOnClickListener {
            vehicleType = "GREATER_THAN_7_SEATER"
            updateUI()
        }

        btnConfirm.setOnClickListener {
            submitBooking()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun updateUI() {
        // Update Service Cards
        cardSelfService.setBackgroundResource(if (serviceType == "SELF_SERVICE") R.drawable.bg_wizard_card_selected else R.drawable.bg_wizard_card_default)
        cardFullService.setBackgroundResource(if (serviceType == "FULL_SERVICE") R.drawable.bg_wizard_card_selected else R.drawable.bg_wizard_card_default)

        // Update Vehicle Cards
        card4Seater.setBackgroundResource(if (vehicleType == "4_SEATER") R.drawable.bg_wizard_card_selected else R.drawable.bg_wizard_card_default)
        card7Seater.setBackgroundResource(if (vehicleType == "7_SEATER") R.drawable.bg_wizard_card_selected else R.drawable.bg_wizard_card_default)
        cardMaxSeater.setBackgroundResource(if (vehicleType == "GREATER_THAN_7_SEATER") R.drawable.bg_wizard_card_selected else R.drawable.bg_wizard_card_default)

        // Update Surcharge Texts
        val currentSurcharges = surcharges[serviceType]!!
        tvSur4.text = "+₱${currentSurcharges["4_SEATER"]}"
        tvSur7.text = "+₱${currentSurcharges["7_SEATER"]}"
        tvSurMax.text = "+₱${currentSurcharges["GREATER_THAN_7_SEATER"]}"

        // Update Total
        vehicleSurcharge = currentSurcharges[vehicleType]!!
        val total = basePrice + vehicleSurcharge
        tvTotalAmount.text = "₱$total"
    }

    private fun submitBooking() {
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
                    val priorityNumber = responseBody.trim()
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
