package com.paires.cleanride.booking

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.paires.cleanride.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.UUID

class MyReviewsActivity : AppCompatActivity() {

    private lateinit var rvReviews: RecyclerView
    private lateinit var tvEmpty: TextView
    private val reviewAdapter = ReviewsAdapter { bookingId -> showWriteReviewDialog(bookingId) }
    private var userId: Long = -1

    // Currently selected photo URI for the review dialog
    private var selectedPhotoUri: Uri? = null
    private var photoPickerImageView: ImageView? = null

    // Photo picker launcher
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedPhotoUri = it
            photoPickerImageView?.setImageURI(it)
            photoPickerImageView?.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_reviews)

        rvReviews = findViewById(R.id.rvReviews)
        tvEmpty = findViewById(R.id.tvEmpty)

        rvReviews.layoutManager = LinearLayoutManager(this)
        rvReviews.adapter = reviewAdapter

        val sharedPref = getSharedPreferences("CleanRidePrefs", Context.MODE_PRIVATE)
        userId = sharedPref.getLong("userId", -1)

        setupNavigation()
        fetchData()
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
        // navReviews is active
    }

    private fun fetchData() {
        if (userId == -1L) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "Please log in."
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
                        val s = connection.inputStream.bufferedReader().use { it.readText() }
                        if (s.isNotBlank()) JSONArray(s) else JSONArray()
                    } else null
                }

                val reviewsData = withContext(Dispatchers.IO) {
                    val url = URL("http://10.0.2.2:8081/api/v1/reviews/user/$userId")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/json")
                    if (connection.responseCode in 200..299) {
                        val s = connection.inputStream.bufferedReader().use { it.readText() }
                        if (s.isNotBlank()) JSONArray(s) else JSONArray()
                    } else null
                }

                if (bookingsData != null && reviewsData != null) {
                    val reviewMap = mutableMapOf<Long, ReviewData>()
                    for (i in 0 until reviewsData.length()) {
                        val obj = reviewsData.getJSONObject(i)
                        val bId = obj.getLong("bookingId")
                        val rating = obj.getInt("rating")
                        val feedback = obj.getString("feedback")
                        val photoUrl = if (obj.has("photoUrl") && !obj.isNull("photoUrl")) obj.getString("photoUrl") else null
                        reviewMap[bId] = ReviewData(rating, feedback, photoUrl)
                    }

                    val pending = mutableListOf<ReviewItem>()
                    val reviewed = mutableListOf<ReviewItem>()

                    for (i in 0 until bookingsData.length()) {
                        val obj = bookingsData.getJSONObject(i)
                        val status = if (obj.has("status") && !obj.isNull("status")) obj.getString("status") else "UNKNOWN"

                        if (status.uppercase() == "CANCELLED" || status.uppercase() == "CANCELED") continue

                        val bookingId = obj.getLong("id")
                        val date = if (obj.has("bookingDate") && !obj.isNull("bookingDate")) obj.getString("bookingDate") else continue
                        val timeSlot = if (obj.has("timeSlot") && !obj.isNull("timeSlot")) obj.getInt("timeSlot") else 1
                        val service = if (obj.has("serviceType") && !obj.isNull("serviceType")) obj.getString("serviceType") else "N/A"
                        val bayId = if (obj.has("bayId") && !obj.isNull("bayId")) obj.getInt("bayId") else 1

                        val isEffectivelyCompleted = if (status.uppercase() == "COMPLETED") {
                            true
                        } else {
                            try {
                                val parts = date.split("-")
                                val year = parts[0].toInt()
                                val month = parts[1].toInt() - 1
                                val day = parts[2].toInt()
                                val (slotHour, slotMinute) = getSlotTime(timeSlot)
                                val slotStart = Calendar.getInstance()
                                slotStart.set(year, month, day, slotHour, slotMinute, 0)
                                slotStart.set(Calendar.MILLISECOND, 0)
                                val slotEnd = slotStart.clone() as Calendar
                                slotEnd.add(Calendar.MINUTE, 90)
                                Calendar.getInstance().after(slotEnd)
                            } catch (e: Exception) {
                                false
                            }
                        }

                        if (!isEffectivelyCompleted) continue

                        val existingReview = reviewMap[bookingId]
                        val item = ReviewItem(
                            bookingId = bookingId,
                            date = date,
                            time = mapTimeSlot(timeSlot),
                            serviceType = service.replace("_", " "),
                            bayId = bayId,
                            isReviewed = existingReview != null,
                            rating = existingReview?.rating ?: 0,
                            feedback = existingReview?.feedback ?: "",
                            photoUrl = existingReview?.photoUrl
                        )

                        if (existingReview != null) reviewed.add(item) else pending.add(item)
                    }

                    // Build the flat list for adapter with section headers
                    val allItems = mutableListOf<ReviewListItem>()

                    if (pending.isNotEmpty()) {
                        allItems.add(ReviewListItem.Header("Pending Review"))
                        pending.sortedByDescending { it.date }.forEach { allItems.add(ReviewListItem.Card(it)) }
                    }

                    if (reviewed.isNotEmpty()) {
                        allItems.add(ReviewListItem.Header("Already Reviewed"))
                        reviewed.sortedByDescending { it.date }.forEach { allItems.add(ReviewListItem.Card(it)) }
                    }

                    if (allItems.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                    } else {
                        tvEmpty.visibility = View.GONE
                        reviewAdapter.submitList(allItems)
                    }

                } else {
                    Toast.makeText(this@MyReviewsActivity, "Failed to load data", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "Error: ${e.message}"
            }
        }
    }

    private fun showWriteReviewDialog(bookingId: Long) {
        selectedPhotoUri = null

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_write_review, null)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)
        val etFeedback = dialogView.findViewById<EditText>(R.id.etFeedback)
        val btnChoosePhoto = dialogView.findViewById<MaterialButton>(R.id.btnChoosePhoto)
        val ivPhotoPreview = dialogView.findViewById<ImageView>(R.id.ivPhotoPreview)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelReview)
        val btnSubmit = dialogView.findViewById<MaterialButton>(R.id.btnSubmitReview)

        // Keep reference so the photo picker result can update this imageview
        photoPickerImageView = ivPhotoPreview

        btnChoosePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnCancel.setOnClickListener {
            selectedPhotoUri = null
            photoPickerImageView = null
            dialog.dismiss()
        }

        btnSubmit.setOnClickListener {
            val rating = ratingBar.rating.toInt()
            val feedback = etFeedback.text.toString().trim()

            if (rating == 0) {
                Toast.makeText(this, "Please select a star rating.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (feedback.isEmpty()) {
                Toast.makeText(this, "Please enter your feedback.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSubmit.text = "Submitting..."
            btnSubmit.isEnabled = false

            submitReview(bookingId, rating, feedback, selectedPhotoUri, dialog)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun submitReview(bookingId: Long, rating: Int, feedback: String, photoUri: Uri?, dialog: AlertDialog) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val boundary = "Boundary-" + UUID.randomUUID().toString()
                val success = withContext(Dispatchers.IO) {
                    val url = URL("http://10.0.2.2:8081/api/v1/reviews/upload")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    connection.doOutput = true

                    val outputStream = DataOutputStream(connection.outputStream)

                    fun writeField(name: String, value: String) {
                        outputStream.writeBytes("--$boundary\r\n")
                        outputStream.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                        val bytes = value.toByteArray(Charsets.UTF_8)
                        outputStream.write(bytes, 0, bytes.size)
                        outputStream.writeBytes("\r\n")
                    }

                    writeField("bookingId", bookingId.toString())
                    writeField("rating", rating.toString())
                    writeField("feedback", feedback)

                    // Attach photo if selected
                    if (photoUri != null) {
                        val mimeType = contentResolver.getType(photoUri) ?: "image/jpeg"
                        val extension = when (mimeType) {
                            "image/png" -> "png"
                            "image/webp" -> "webp"
                            else -> "jpg"
                        }
                        val filename = "photo_${System.currentTimeMillis()}.$extension"

                        outputStream.writeBytes("--$boundary\r\n")
                        outputStream.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"$filename\"\r\n")
                        outputStream.writeBytes("Content-Type: $mimeType\r\n\r\n")

                        contentResolver.openInputStream(photoUri)?.use { inputStream ->
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                        }
                        outputStream.writeBytes("\r\n")
                    }

                    outputStream.writeBytes("--$boundary--\r\n")
                    outputStream.flush()
                    outputStream.close()

                    connection.responseCode in 200..299
                }

                if (success) {
                    dialog.dismiss()
                    selectedPhotoUri = null
                    photoPickerImageView = null
                    Toast.makeText(this@MyReviewsActivity, "Review submitted!", Toast.LENGTH_SHORT).show()
                    fetchData()
                } else {
                    Toast.makeText(this@MyReviewsActivity, "Failed to submit review.", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MyReviewsActivity, "Network Error: ${e.message}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }

    private fun getSlotTime(slot: Int): Pair<Int, Int> {
        return when (slot) {
            1 -> Pair(8, 0); 2 -> Pair(9, 30); 3 -> Pair(11, 0)
            4 -> Pair(12, 30); 5 -> Pair(14, 0); 6 -> Pair(15, 30)
            7 -> Pair(17, 0); 8 -> Pair(18, 30); 9 -> Pair(20, 0)
            else -> Pair(8, 0)
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

// ─── Data Models ───────────────────────────────────────────────────────────────

data class ReviewData(val rating: Int, val feedback: String, val photoUrl: String?)

data class ReviewItem(
    val bookingId: Long,
    val date: String,
    val time: String,
    val serviceType: String,
    val bayId: Int,
    val isReviewed: Boolean,
    val rating: Int,
    val feedback: String,
    val photoUrl: String?
)

sealed class ReviewListItem {
    data class Header(val title: String) : ReviewListItem()
    data class Card(val item: ReviewItem) : ReviewListItem()
}

// ─── Adapter ──────────────────────────────────────────────────────────────────

class ReviewsAdapter(private val onWriteReviewClick: (Long) -> Unit) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_CARD = 1
    }

    private var items = listOf<ReviewListItem>()

    fun submitList(newItems: List<ReviewListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ReviewListItem.Header -> TYPE_HEADER
        is ReviewListItem.Card -> TYPE_CARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_section_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_review, parent, false)
            CardViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val listItem = items[position]) {
            is ReviewListItem.Header -> (holder as HeaderViewHolder).bind(listItem.title)
            is ReviewListItem.Card -> (holder as CardViewHolder).bind(listItem.item, onWriteReviewClick)
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
        val tvServiceType: TextView = view.findViewById(R.id.tvServiceType)
        val tvBay: TextView = view.findViewById(R.id.tvBay)
        val btnWriteReview: MaterialButton = view.findViewById(R.id.btnWriteReview)
        val containerReviewed: LinearLayout = view.findViewById(R.id.containerReviewed)
        val tvStars: TextView = view.findViewById(R.id.tvStars)
        val tvFeedback: TextView = view.findViewById(R.id.tvFeedback)

        fun bind(item: ReviewItem, onWriteReviewClick: (Long) -> Unit) {
            tvDate.text = item.date
            tvTime.text = item.time
            tvServiceType.text = item.serviceType
            tvBay.text = "Bay ${item.bayId}"

            if (item.isReviewed) {
                btnWriteReview.visibility = View.GONE
                containerReviewed.visibility = View.VISIBLE
                var starsStr = ""
                for (i in 1..5) starsStr += if (i <= item.rating) "★" else "☆"
                tvStars.text = starsStr
                tvFeedback.text = "\"${item.feedback}\""
            } else {
                containerReviewed.visibility = View.GONE
                btnWriteReview.visibility = View.VISIBLE
                btnWriteReview.setOnClickListener { onWriteReviewClick(item.bookingId) }
            }
        }
    }
}
