package com.paires.cleanride.booking

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.paires.cleanride.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class MyBookingsActivity : AppCompatActivity() {

    private lateinit var rvBookings: RecyclerView
    private lateinit var tvEmpty: TextView
    private val bookingAdapter = BookingListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_bookings)

        rvBookings = findViewById(R.id.rvBookings)
        tvEmpty = findViewById(R.id.tvEmpty)

        rvBookings.layoutManager = LinearLayoutManager(this)
        rvBookings.adapter = bookingAdapter

        setupNavigation()
        fetchBookings()
    }

    private fun setupNavigation() {
        val navCalendar = findViewById<LinearLayout>(R.id.navCalendar)
        navCalendar.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        val navReviews = findViewById<LinearLayout>(R.id.navReviews)
        navReviews.setOnClickListener {
            val intent = Intent(this, MyReviewsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        // Garage is active, Profile is dummy
    }

    private fun fetchBookings() {
        val sharedPref = getSharedPreferences("CleanRidePrefs", Context.MODE_PRIVATE)
        val userId = sharedPref.getLong("userId", -1)

        if (userId == -1L) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "Please log in to view bookings."
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val bookingsData = withContext(Dispatchers.IO) {
                    val url = URL("http://10.0.2.2:8081/api/v1/bookings/user/$userId")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/json")

                    if (connection.responseCode in 200..299) {
                        val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                        if (responseString.isNotBlank()) JSONArray(responseString) else JSONArray()
                    } else {
                        null
                    }
                }

                if (bookingsData != null) {
                    val ongoing = mutableListOf<BookingItem>()
                    val completed = mutableListOf<BookingItem>()
                    val cancelled = mutableListOf<BookingItem>()

                    for (i in 0 until bookingsData.length()) {
                        val obj = bookingsData.getJSONObject(i)

                        val date = if (obj.has("bookingDate") && !obj.isNull("bookingDate")) obj.getString("bookingDate") else "Unknown Date"
                        val timeSlot = if (obj.has("timeSlot") && !obj.isNull("timeSlot")) obj.getInt("timeSlot") else 1
                        val service = if (obj.has("serviceType") && !obj.isNull("serviceType")) obj.getString("serviceType") else "N/A"
                        val vehicle = if (obj.has("vehicleType") && !obj.isNull("vehicleType")) obj.getString("vehicleType") else "N/A"
                        val priority = if (obj.has("priorityNumber") && !obj.isNull("priorityNumber")) obj.getString("priorityNumber") else "N/A"
                        val total = if (obj.has("totalPrice") && !obj.isNull("totalPrice")) obj.getDouble("totalPrice") else 0.0
                        val status = if (obj.has("status") && !obj.isNull("status")) obj.getString("status") else "UNKNOWN"

                        val item = BookingItem(
                            date = date,
                            time = mapTimeSlot(timeSlot),
                            serviceType = service.replace("_", " "),
                            vehicleType = vehicle.replace("_", "-"),
                            priorityNumber = priority,
                            totalPrice = total,
                            status = status
                        )

                        when (status.uppercase()) {
                            "CONFIRMED" -> ongoing.add(item)
                            "COMPLETED" -> completed.add(item)
                            "CANCELLED", "CANCELED" -> cancelled.add(item)
                            else -> ongoing.add(item)
                        }
                    }

                    // Build flat list with section headers
                    val allItems = mutableListOf<BookingListItem>()

                    if (ongoing.isNotEmpty()) {
                        allItems.add(BookingListItem.Header("Ongoing"))
                        ongoing.sortedByDescending { it.date }.forEach { allItems.add(BookingListItem.Card(it)) }
                    }
                    if (completed.isNotEmpty()) {
                        allItems.add(BookingListItem.Header("Completed"))
                        completed.sortedByDescending { it.date }.forEach { allItems.add(BookingListItem.Card(it)) }
                    }
                    if (cancelled.isNotEmpty()) {
                        allItems.add(BookingListItem.Header("Cancelled"))
                        cancelled.sortedByDescending { it.date }.forEach { allItems.add(BookingListItem.Card(it)) }
                    }

                    if (allItems.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                    } else {
                        tvEmpty.visibility = View.GONE
                        bookingAdapter.submitList(allItems)
                    }
                } else {
                    Toast.makeText(this@MyBookingsActivity, "Failed to load bookings", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "Error: ${e.message}"
            }
        }
    }

    private fun mapTimeSlot(slot: Int): String {
        return when (slot) {
            1 -> "08:00 AM - 09:30 AM"; 2 -> "09:30 AM - 11:00 AM"; 3 -> "11:00 AM - 12:30 PM"
            4 -> "12:30 PM - 02:00 PM"; 5 -> "02:00 PM - 03:30 PM"; 6 -> "03:30 PM - 05:00 PM"
            7 -> "05:00 PM - 06:30 PM"; 8 -> "06:30 PM - 08:00 PM"; 9 -> "08:00 PM - 09:30 PM"
            else -> "Unknown Time"
        }
    }
}

// ─── Models ────────────────────────────────────────────────────────────────────

data class BookingItem(
    val date: String,
    val time: String,
    val serviceType: String,
    val vehicleType: String,
    val priorityNumber: String,
    val totalPrice: Double,
    val status: String
)

sealed class BookingListItem {
    data class Header(val title: String) : BookingListItem()
    data class Card(val item: BookingItem) : BookingListItem()
}

// ─── Adapter ──────────────────────────────────────────────────────────────────

class BookingListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_CARD = 1
    }

    private var items = listOf<BookingListItem>()

    fun submitList(newItems: List<BookingListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is BookingListItem.Header -> TYPE_HEADER
        is BookingListItem.Card -> TYPE_CARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_section_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_booking, parent, false)
            CardViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val listItem = items[position]) {
            is BookingListItem.Header -> (holder as HeaderViewHolder).bind(listItem.title)
            is BookingListItem.Card -> (holder as CardViewHolder).bind(listItem.item)
        }
    }

    override fun getItemCount() = items.size

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvHeader: TextView = view.findViewById(R.id.tvSectionHeader)
        fun bind(title: String) { tvHeader.text = title }
    }

    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvServiceType: TextView = view.findViewById(R.id.tvServiceType)
        val tvVehicleType: TextView = view.findViewById(R.id.tvVehicleType)
        val tvPriority: TextView = view.findViewById(R.id.tvPriority)
        val tvTotal: TextView = view.findViewById(R.id.tvTotal)

        fun bind(item: BookingItem) {
            tvDate.text = item.date
            tvTime.text = item.time
            tvServiceType.text = item.serviceType
            tvVehicleType.text = item.vehicleType
            tvPriority.text = item.priorityNumber
            tvTotal.text = "₱${item.totalPrice}0"
            tvStatus.text = item.status

            val (badgeBg, badgeText) = when (item.status.uppercase()) {
                "CONFIRMED" -> "#dcfce7" to "#16a34a"
                "CANCELLED", "CANCELED" -> "#fee2e2" to "#dc2626"
                "COMPLETED" -> "#f1f5f9" to "#94a3b8"
                else -> "#fef08a" to "#ca8a04"
            }

            tvStatus.setTextColor(Color.parseColor(badgeText))
            tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor(badgeBg))
        }
    }
}

// Keep old BookingAdapter as alias so nothing else breaks
typealias BookingAdapter = BookingListAdapter
