package com.example.lednotify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class ContactColorItem(
    val contactName: String,
    val color: Int,
    val speedMs: Int = 600
)

data class BlinkSpeedOption(
    val label: String,
    val speedMs: Int
)

object ContactColorManager {
    const val PREFS_NAME = "contact_colors"
    const val PREFS_SPEEDS = "contact_speeds"
    const val DEFAULT_BLINK_SPEED_MS = 600
    private const val KEY_INITIALIZED = "is_initialized"

    val SPEED_OPTIONS = listOf(
        BlinkSpeedOption("⚡ 150ms", 150),
        BlinkSpeedOption("⚡ 250ms", 250),
        BlinkSpeedOption("⚡ 400ms", 400),
        BlinkSpeedOption("💡 600ms", 600),
        BlinkSpeedOption("🌙 900ms", 900)
    )

    fun initializeDefaultsIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val speedPrefs = context.getSharedPreferences(PREFS_SPEEDS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                // Default entries requested: Mom = #FF69B4 (pink), Boss = #FF0000 (red)
                .putInt("Mom", 0xFFFF69B4.toInt())
                .putInt("Boss", 0xFFFF0000.toInt())
                .putBoolean(KEY_INITIALIZED, true)
                .apply()

            speedPrefs.edit()
                .putInt("Mom", 600)
                .putInt("Boss", 200) // Rapid blinking for Boss by default
                .apply()
        }
    }

    fun getColorForContact(context: Context, contactName: String?): Int? {
        if (contactName.isNullOrBlank()) return null
        initializeDefaultsIfNeeded(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val cleanName = contactName.trim()

        // 1. Exact match
        if (prefs.contains(cleanName)) {
            return prefs.getInt(cleanName, 0)
        }

        val allEntries = prefs.all

        // 2. Case-insensitive match
        for ((key, value) in allEntries) {
            if (key != KEY_INITIALIZED && value is Int && key.equals(cleanName, ignoreCase = true)) {
                return value
            }
        }

        // 3. Substring match fallback (e.g. "John" matches "John Doe")
        for ((key, value) in allEntries) {
            if (key != KEY_INITIALIZED && value is Int) {
                if (cleanName.contains(key, ignoreCase = true) || key.contains(cleanName, ignoreCase = true)) {
                    return value
                }
            }
        }

        return null
    }

    fun getSpeedForContact(context: Context, contactName: String?): Int {
        if (contactName.isNullOrBlank()) return DEFAULT_BLINK_SPEED_MS
        initializeDefaultsIfNeeded(context)
        val speedPrefs = context.getSharedPreferences(PREFS_SPEEDS, Context.MODE_PRIVATE)

        val cleanName = contactName.trim()

        if (speedPrefs.contains(cleanName)) {
            return speedPrefs.getInt(cleanName, DEFAULT_BLINK_SPEED_MS)
        }

        val allEntries = speedPrefs.all
        for ((key, value) in allEntries) {
            if (value is Int && key.equals(cleanName, ignoreCase = true)) {
                return value
            }
        }
        for ((key, value) in allEntries) {
            if (value is Int) {
                if (cleanName.contains(key, ignoreCase = true) || key.contains(cleanName, ignoreCase = true)) {
                    return value
                }
            }
        }

        return DEFAULT_BLINK_SPEED_MS
    }

    fun saveContactColor(context: Context, contactName: String, color: Int, speedMs: Int = DEFAULT_BLINK_SPEED_MS) {
        val cleanName = contactName.trim()
        if (cleanName.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val speedPrefs = context.getSharedPreferences(PREFS_SPEEDS, Context.MODE_PRIVATE)
        prefs.edit().putInt(cleanName, color).apply()
        speedPrefs.edit().putInt(cleanName, speedMs).apply()
    }

    fun deleteContactColor(context: Context, contactName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val speedPrefs = context.getSharedPreferences(PREFS_SPEEDS, Context.MODE_PRIVATE)
        prefs.edit().remove(contactName).apply()
        speedPrefs.edit().remove(contactName).apply()
    }

    fun getAllContactColors(context: Context): List<ContactColorItem> {
        initializeDefaultsIfNeeded(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val speedPrefs = context.getSharedPreferences(PREFS_SPEEDS, Context.MODE_PRIVATE)
        val list = mutableListOf<ContactColorItem>()
        for ((key, value) in prefs.all) {
            if (key != KEY_INITIALIZED && value is Int) {
                val speed = speedPrefs.getInt(key, DEFAULT_BLINK_SPEED_MS)
                list.add(ContactColorItem(key, value, speed))
            }
        }
        return list.sortedBy { it.contactName.lowercase() }
    }
}

class ContactColorActivity : ComponentActivity() {

    private lateinit var etContactName: EditText
    private lateinit var layoutContactColors: LinearLayout
    private lateinit var layoutBlinkSpeeds: LinearLayout
    private lateinit var btnSaveContact: Button
    private lateinit var tvEmptyContacts: TextView
    private lateinit var rvContactColors: RecyclerView
    private lateinit var adapter: ContactColorsAdapter

    private var selectedColor: Int = 0xFFFF69B4.toInt() // Pink default for contacts
    private var selectedSpeedMs: Int = ContactColorManager.DEFAULT_BLINK_SPEED_MS
    private val colorViews = mutableListOf<View>()
    private val speedViews = mutableListOf<TextView>()

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cursor = contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val contactName = it.getString(nameIndex)
                            if (!contactName.isNullOrBlank()) {
                                etContactName.setText(contactName)
                                Toast.makeText(this, "Selected contact: $contactName", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Could not read contact name", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pickContactLauncher.launch(null)
        } else {
            pickContactLauncher.launch(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ContactColorManager.initializeDefaultsIfNeeded(this)

        val density = resources.displayMetrics.density

        // Root container
        val rootLayout = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // Top Toolbar
        val topBar = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        val btnBack = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt())
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.WHITE)
            contentDescription = "Back"
            setOnClickListener { finish() }
        }

        val headerTextLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (12 * density).toInt()
            }
            orientation = LinearLayout.VERTICAL
        }

        val tvTitle = TextView(this).apply {
            text = "WhatsApp Contact Colors"
            setTextColor(Color.WHITE)
            textSize = 18f
            paint.isFakeBoldText = true
        }

        val tvSubtitle = TextView(this).apply {
            text = "Set custom LED colors & blink speeds per contact"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
        }

        headerTextLayout.addView(tvTitle)
        headerTextLayout.addView(tvSubtitle)
        topBar.addView(btnBack)
        topBar.addView(headerTextLayout)
        rootLayout.addView(topBar)

        // ScrollView Content
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        val contentLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (24 * density).toInt())
        }

        // Add/Update Contact Card
        val addCard = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setCardBackgroundColor(Color.parseColor("#1E1E1E"))
            radius = 12 * density
            cardElevation = 2 * density
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        val tvCardHeader = TextView(this).apply {
            text = "Add or Update Contact"
            setTextColor(Color.WHITE)
            textSize = 16f
            paint.isFakeBoldText = true
        }

        // Contact input row with EditText and "Pick Contact" button
        val inputRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * density).toInt()
            ).apply {
                topMargin = (12 * density).toInt()
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        etContactName = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setBackgroundColor(Color.parseColor("#2C2C2C"))
            hint = "Contact name (e.g. Mom, John)"
            setHintTextColor(Color.parseColor("#777777"))
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
        }

        val btnPickContact = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                marginStart = (8 * density).toInt()
            }
            setBackgroundColor(Color.parseColor("#00897B"))
            text = "Pick Contact"
            setTextColor(Color.WHITE)
            textSize = 13f
            paint.isFakeBoldText = true
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@ContactColorActivity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    pickContactLauncher.launch(null)
                } else {
                    requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            }
        }

        inputRow.addView(etContactName)
        inputRow.addView(btnPickContact)

        val tvHint = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * density).toInt()
            }
            text = "Tap 'Pick Contact' to choose from your phone contacts, or type the name/number manually."
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
        }

        // Color Picker Label
        val tvColorLabel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (14 * density).toInt()
            }
            text = "Choose Solid LED Color:"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 14f
            paint.isFakeBoldText = true
        }

        val horizontalScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).toInt()
            }
            isHorizontalScrollBarEnabled = false
        }

        layoutContactColors = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        horizontalScroll.addView(layoutContactColors)

        // Blink Speed Selection Label & Row
        val tvSpeedLabel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (16 * density).toInt()
            }
            text = "Blink Speed (Rapid Blinking):"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 14f
            paint.isFakeBoldText = true
        }

        val speedScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).toInt()
            }
            isHorizontalScrollBarEnabled = false
        }

        layoutBlinkSpeeds = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        speedScroll.addView(layoutBlinkSpeeds)

        val tvSpeedHelp = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * density).toInt()
            }
            text = "Rapid (150ms / 250ms) blinks ultra-fast for urgent messages. Normal is 600ms."
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
        }

        btnSaveContact = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (50 * density).toInt()
            ).apply {
                topMargin = (16 * density).toInt()
            }
            setBackgroundColor(Color.parseColor("#2E7D32"))
            text = "Save Contact Color & Speed"
            setTextColor(Color.WHITE)
            textSize = 15f
            paint.isFakeBoldText = true
        }

        cardContent.addView(tvCardHeader)
        cardContent.addView(inputRow)
        cardContent.addView(tvHint)
        cardContent.addView(tvColorLabel)
        cardContent.addView(horizontalScroll)
        cardContent.addView(tvSpeedLabel)
        cardContent.addView(speedScroll)
        cardContent.addView(tvSpeedHelp)
        cardContent.addView(btnSaveContact)
        addCard.addView(cardContent)
        contentLayout.addView(addCard)

        // Section Title: Saved Contact Colors
        val tvSavedHeader = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (20 * density).toInt()
                bottomMargin = (8 * density).toInt()
            }
            text = "Saved Contact Colors & Speeds"
            setTextColor(Color.WHITE)
            textSize = 16f
            paint.isFakeBoldText = true
        }
        contentLayout.addView(tvSavedHeader)

        tvEmptyContacts = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (16 * density).toInt()
            }
            gravity = Gravity.CENTER
            text = "No contact colors saved yet.\nAdd Mom, Boss, or any contact above!"
            setTextColor(Color.parseColor("#777777"))
            textSize = 14f
            visibility = View.GONE
        }
        contentLayout.addView(tvEmptyContacts)

        rvContactColors = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isNestedScrollingEnabled = false
        }
        contentLayout.addView(rvContactColors)

        scrollView.addView(contentLayout)
        rootLayout.addView(scrollView)
        setContentView(rootLayout)

        setupColorPalette()
        setupSpeedOptions()
        setupRecyclerView()
        setupSaveButton()

        loadContacts()
    }

    private fun setupColorPalette() {
        layoutContactColors.removeAllViews()
        colorViews.clear()

        val density = resources.displayMetrics.density
        val sizePx = (40 * density).toInt()
        val marginPx = (6 * density).toInt()

        for (color in AppColorManager.PALETTE_12) {
            val colorView = View(this).apply {
                val params = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                layoutParams = params
                background = createCircleDrawable(color, color == selectedColor)
                setOnClickListener {
                    selectedColor = color
                    refreshPaletteSelection()
                }
            }
            colorViews.add(colorView)
            layoutContactColors.addView(colorView)
        }
    }

    private fun setupSpeedOptions() {
        layoutBlinkSpeeds.removeAllViews()
        speedViews.clear()

        val density = resources.displayMetrics.density
        val marginPx = (4 * density).toInt()

        for (option in ContactColorManager.SPEED_OPTIONS) {
            val tvOption = TextView(this).apply {
                text = option.label
                textSize = 13f
                paint.isFakeBoldText = true
                gravity = Gravity.CENTER
                setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                layoutParams = params

                background = createSpeedOptionDrawable(option.speedMs == selectedSpeedMs)
                setTextColor(if (option.speedMs == selectedSpeedMs) Color.WHITE else Color.parseColor("#CCCCCC"))

                setOnClickListener {
                    selectedSpeedMs = option.speedMs
                    refreshSpeedSelection()
                }
            }
            speedViews.add(tvOption)
            layoutBlinkSpeeds.addView(tvOption)
        }
    }

    private fun refreshSpeedSelection() {
        for (i in ContactColorManager.SPEED_OPTIONS.indices) {
            val option = ContactColorManager.SPEED_OPTIONS[i]
            val view = speedViews[i]
            val isSelected = (option.speedMs == selectedSpeedMs)
            view.background = createSpeedOptionDrawable(isSelected)
            view.setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#CCCCCC"))
        }
    }

    private fun createSpeedOptionDrawable(isSelected: Boolean): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8 * density
            if (isSelected) {
                setColor(Color.parseColor("#00897B"))
                setStroke((2 * density).toInt(), Color.WHITE)
            } else {
                setColor(Color.parseColor("#2A2A2A"))
                setStroke((1 * density).toInt(), Color.parseColor("#444444"))
            }
        }
    }

    private fun refreshPaletteSelection() {
        for (i in AppColorManager.PALETTE_12.indices) {
            val color = AppColorManager.PALETTE_12[i]
            val view = colorViews[i]
            view.background = createCircleDrawable(color, color == selectedColor)
        }
    }

    private fun createCircleDrawable(color: Int, isSelected: Boolean): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (isSelected) {
                setStroke((3 * density).toInt(), Color.WHITE)
            } else {
                setStroke((1 * density).toInt(), Color.parseColor("#444444"))
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ContactColorsAdapter(
            onDeleteClick = { item ->
                ContactColorManager.deleteContactColor(this, item.contactName)
                Toast.makeText(this, "Removed ${item.contactName}", Toast.LENGTH_SHORT).show()
                loadContacts()
            },
            onItemClick = { item ->
                etContactName.setText(item.contactName)
                selectedColor = item.color
                selectedSpeedMs = item.speedMs
                refreshPaletteSelection()
                refreshSpeedSelection()
            }
        )
        rvContactColors.layoutManager = LinearLayoutManager(this)
        rvContactColors.adapter = adapter
    }

    private fun setupSaveButton() {
        btnSaveContact.setOnClickListener {
            val name = etContactName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a contact name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            ContactColorManager.saveContactColor(this, name, selectedColor, selectedSpeedMs)
            Toast.makeText(this, "Saved \"$name\" ($selectedSpeedMs ms)", Toast.LENGTH_SHORT).show()
            etContactName.text.clear()
            loadContacts()
        }
    }

    private fun loadContacts() {
        val list = ContactColorManager.getAllContactColors(this)
        if (list.isEmpty()) {
            tvEmptyContacts.visibility = View.VISIBLE
            rvContactColors.visibility = View.GONE
        } else {
            tvEmptyContacts.visibility = View.GONE
            rvContactColors.visibility = View.VISIBLE
            adapter.setItems(list)
        }
    }
}

class ContactColorsAdapter(
    private val onDeleteClick: (ContactColorItem) -> Unit,
    private val onItemClick: (ContactColorItem) -> Unit
) : RecyclerView.Adapter<ContactColorsAdapter.ContactViewHolder>() {

    private val items = mutableListOf<ContactColorItem>()

    fun setItems(newItems: List<ContactColorItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val density = parent.context.resources.displayMetrics.density

        val card = CardView(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (6 * density).toInt(), 0, (6 * density).toInt())
            }
            setCardBackgroundColor(Color.parseColor("#1E1E1E"))
            radius = 12 * density
            cardElevation = 2 * density
        }

        val row = LinearLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
        }

        val colorDot = View(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams((34 * density).toInt(), (34 * density).toInt())
        }

        val nameText = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (14 * density).toInt()
            }
            setTextColor(Color.WHITE)
            textSize = 16f
            paint.isFakeBoldText = true
        }

        val speedBadge = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (10 * density).toInt()
            }
            textSize = 12f
            paint.isFakeBoldText = true
            setTextColor(Color.parseColor("#80CBC4"))
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())

            val badgeDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6 * density
                setColor(Color.parseColor("#263238"))
                setStroke((1 * density).toInt(), Color.parseColor("#00897B"))
            }
            background = badgeDrawable
        }

        val deleteBtn = ImageButton(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt())
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(android.R.drawable.ic_menu_delete)
            setColorFilter(Color.parseColor("#FF5252"))
            contentDescription = "Delete Contact"
        }

        row.addView(colorDot)
        row.addView(nameText)
        row.addView(speedBadge)
        row.addView(deleteBtn)
        card.addView(row)

        return ContactViewHolder(card, colorDot, nameText, speedBadge, deleteBtn)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class ContactViewHolder(
        itemView: View,
        private val viewContactColor: View,
        private val tvContactName: TextView,
        private val tvSpeedBadge: TextView,
        private val btnDeleteContact: ImageButton
    ) : RecyclerView.ViewHolder(itemView) {

        fun bind(item: ContactColorItem) {
            tvContactName.text = item.contactName

            val speedLabel = if (item.speedMs <= 250) {
                "⚡ ${item.speedMs}ms"
            } else {
                "${item.speedMs}ms"
            }
            tvSpeedBadge.text = speedLabel

            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(item.color)
            }
            viewContactColor.background = drawable

            btnDeleteContact.setOnClickListener {
                onDeleteClick(item)
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
