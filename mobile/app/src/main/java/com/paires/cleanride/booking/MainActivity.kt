package com.paires.cleanride.booking

import com.paires.cleanride.R
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var rvDates: RecyclerView
    private lateinit var rvTimeSlots: RecyclerView
    private lateinit var btnNext: MaterialButton
    private lateinit var tvMonthYear: TextView

    private val dateAdapter = DateAdapter { dateItem ->
        selectedDate = dateItem.fullDateString
        tvMonthYear.text = dateItem.monthYearString
        selectedSlot = null
        btnNext.isEnabled = false
        fetchAvailableSlots(selectedDate)
    }

    private val slotAdapter = TimeSlotAdapter { slot ->
        selectedSlot = slot.id
        btnNext.isEnabled = true
    }

    private var selectedDate: String = ""
    private var selectedSlot: Int? = null

    private val slotTimes = mapOf(
        1 to "08:00 AM", 2 to "09:30 AM", 3 to "11:00 AM",
        4 to "12:30 PM", 5 to "02:00 PM", 6 to "03:30 PM",
        7 to "05:00 PM", 8 to "06:30 PM", 9 to "08:00 PM"
    )

    private val slotLabels = mapOf(
        1 to "Morning Shift Start", 2 to "Mid-morning Rush", 3 to "Pre-lunch Service",
        4 to "Lunch Peak", 5 to "Post-lunch Session", 6 to "Late Afternoon",
        7 to "Early Evening", 8 to "Commuter Rush", 9 to "Closing Slot"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val mainView = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        tvMonthYear = findViewById(R.id.tvMonthYear)
        rvDates = findViewById(R.id.rvDates)
        rvTimeSlots = findViewById(R.id.rvTimeSlots)
        btnNext = findViewById(R.id.btnNext)

        rvDates.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvDates.adapter = dateAdapter

        rvTimeSlots.layoutManager = LinearLayoutManager(this)
        rvTimeSlots.adapter = slotAdapter

        val navGarage = findViewById<LinearLayout>(R.id.navGarage)
        navGarage.setOnClickListener {
            val intent = android.content.Intent(this, MyBookingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        setupDates()

        btnNext.setOnClickListener {
            if (selectedSlot != null) {
                val intent = android.content.Intent(this, WizardActivity::class.java)
                intent.putExtra("SELECTED_DATE", selectedDate)
                intent.putExtra("SELECTED_SLOT", selectedSlot)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please select a slot", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDates() {
        val dates = mutableListOf<DateItem>()
        val calendar = Calendar.getInstance()
        val sdfFull = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfMonthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val sdfDayName = SimpleDateFormat("EEE", Locale.getDefault())
        val sdfDayNum = SimpleDateFormat("dd", Locale.getDefault())

        for (i in 0 until 30) {
            val date = calendar.time
            dates.add(
                DateItem(
                    fullDateString = sdfFull.format(date),
                    monthYearString = sdfMonthYear.format(date),
                    dayName = sdfDayName.format(date),
                    dayNumber = sdfDayNum.format(date)
                )
            )
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        dateAdapter.submitList(dates)
        
        selectedDate = dates[0].fullDateString
        tvMonthYear.text = dates[0].monthYearString
        fetchAvailableSlots(selectedDate)
    }

    private fun fetchAvailableSlots(date: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val slotsData = withContext(Dispatchers.IO) {
                    val url = URL("http://10.0.2.2:8081/api/v1/bookings/daily?date=$date")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/json")

                    if (connection.responseCode in 200..299) {
                        val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                        JSONArray(responseString)
                    } else {
                        null
                    }
                }

                if (slotsData != null) {
                    val newSlots = mutableListOf<SlotItem>()
                    val sdf = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault())
                    val now = Date()
                    
                    for (i in 1..9) {
                        var occupied = 0
                        for (j in 0 until slotsData.length()) {
                            val obj = slotsData.getJSONObject(j)
                            if (obj.getInt("timeSlot") == i) {
                                occupied = obj.getInt("occupiedCount")
                                break
                            }
                        }
                        
                        val timeStr = slotTimes[i] ?: "08:00 AM"
                        val dateObj = try { sdf.parse("$date $timeStr") } catch(e:Exception){ null }
                        val isPast = dateObj?.before(now) ?: false
                        
                        newSlots.add(SlotItem(
                            id = i, 
                            time = timeStr, 
                            label = slotLabels[i] ?: "", 
                            occupiedCount = occupied, 
                            isPast = isPast
                        ))
                    }
                    slotAdapter.submitList(newSlots)
                } else {
                    Toast.makeText(this@MainActivity, "Failed to load slots", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// Data Classes
data class DateItem(val fullDateString: String, val monthYearString: String, val dayName: String, val dayNumber: String)
data class SlotItem(val id: Int, val time: String, val label: String, val occupiedCount: Int, val isPast: Boolean)

// Adapters
class DateAdapter(private val onDateSelected: (DateItem) -> Unit) : RecyclerView.Adapter<DateAdapter.ViewHolder>() {
    private var items = listOf<DateItem>()
    private var selectedPos = 0

    fun submitList(newItems: List<DateItem>) {
        items = newItems
        selectedPos = 0
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_date, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvDayName.text = item.dayName
        holder.tvDayNum.text = item.dayNumber

        val isSelected = position == selectedPos
        holder.container.isSelected = isSelected

        if (isSelected) {
            holder.tvDayName.setTextColor(Color.WHITE)
            holder.tvDayNum.setTextColor(Color.WHITE)
        } else {
            holder.tvDayName.setTextColor(Color.parseColor("#64748b"))
            holder.tvDayNum.setTextColor(Color.parseColor("#0f172a"))
        }

        holder.itemView.setOnClickListener {
            if (selectedPos != position) {
                val oldPos = selectedPos
                selectedPos = position
                notifyItemChanged(oldPos)
                notifyItemChanged(selectedPos)
                onDateSelected(item)
            }
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.llDateContainer)
        val tvDayName: TextView = view.findViewById(R.id.tvDayName)
        val tvDayNum: TextView = view.findViewById(R.id.tvDayNumber)
    }
}

class TimeSlotAdapter(private val onSlotSelected: (SlotItem) -> Unit) : RecyclerView.Adapter<TimeSlotAdapter.ViewHolder>() {
    private var items = listOf<SlotItem>()
    private var selectedPos = -1

    fun submitList(newItems: List<SlotItem>) {
        items = newItems
        selectedPos = -1
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_time_slot, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTimeRange.text = item.time
        holder.tvSlotLabel.text = item.label
        
        val baysAvailable = 5 - item.occupiedCount
        val isFull = baysAvailable <= 0
        val isUnavailable = isFull || item.isPast
        
        holder.container.isEnabled = !isUnavailable
        val isSelected = (position == selectedPos)
        
        var badgeText = "OPEN"
        var badgeBgColor = "#dcfce7"
        var badgeTextColor = "#16a34a"
        
        if (item.isPast) {
            badgeText = "PASSED"
            badgeBgColor = "#f1f5f9"
            badgeTextColor = "#94a3b8"
        } else if (item.occupiedCount >= 5) {
            badgeText = "FULL"
            badgeBgColor = "#fee2e2"
            badgeTextColor = "#dc2626"
        } else if (item.occupiedCount >= 4) {
            badgeText = "FINAL"
            badgeBgColor = "#f1f5f9"
            badgeTextColor = "#94a3b8"
        } else if (item.occupiedCount >= 2) {
            badgeText = "BUSY"
            badgeBgColor = "#fef08a"
            badgeTextColor = "#ca8a04"
        }
        
        holder.tvBadge.text = badgeText
        holder.tvBadge.setTextColor(Color.parseColor(badgeTextColor))
        holder.tvBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor(badgeBgColor))
        
        if (item.isPast) {
            holder.tvAvailability.text = "Unavailable"
            holder.tvAvailability.setTextColor(Color.parseColor("#94a3b8"))
            holder.tvOccupancyLabel.setTextColor(Color.parseColor("#cbd5e1"))
            holder.tvTimeRange.setTextColor(Color.parseColor("#94a3b8"))
            holder.container.alpha = 0.6f
        } else {
            holder.tvAvailability.text = "${baysAvailable}/5 Available"
            holder.tvAvailability.setTextColor(Color.parseColor("#0f172a"))
            holder.tvOccupancyLabel.setTextColor(Color.parseColor("#64748b"))
            holder.tvTimeRange.setTextColor(Color.parseColor("#0f172a"))
            holder.container.alpha = 1.0f
        }
        
        for (i in 0..4) {
            val segment = holder.segments[i]
            if (i < item.occupiedCount) {
                if (isUnavailable) {
                    segment.setBackgroundResource(R.drawable.bg_segment_locked)
                } else {
                    segment.setBackgroundResource(R.drawable.bg_segment_filled)
                }
            } else {
                segment.setBackgroundResource(R.drawable.bg_segment)
            }
        }
        
        if (isSelected && !isUnavailable) {
            // we can simulate an active border if we want, or just let Android handle StateListAnimator
            holder.container.setBackgroundResource(R.drawable.bg_slot_item)
            holder.container.elevation = 8f
        } else {
            holder.container.setBackgroundResource(R.drawable.bg_slot_item)
            holder.container.elevation = 0f
        }

        holder.itemView.setOnClickListener {
            if (!isUnavailable) {
                val oldPos = selectedPos
                selectedPos = position
                notifyItemChanged(oldPos)
                notifyItemChanged(selectedPos)
                onSlotSelected(item)
            }
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.clSlotContainer)
        val tvTimeRange: TextView = view.findViewById(R.id.tvTimeRange)
        val tvBadge: TextView = view.findViewById(R.id.tvBadge)
        val tvSlotLabel: TextView = view.findViewById(R.id.tvSlotLabel)
        val tvOccupancyLabel: TextView = view.findViewById(R.id.tvOccupancyLabel)
        val tvAvailability: TextView = view.findViewById(R.id.tvAvailability)
        val segments = listOf<View>(
            view.findViewById(R.id.segment1),
            view.findViewById(R.id.segment2),
            view.findViewById(R.id.segment3),
            view.findViewById(R.id.segment4),
            view.findViewById(R.id.segment5)
        )
    }
}
