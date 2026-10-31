package com.example.lednotify

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class ContactColorItem(
    val contactName: String,
    val color: Int
)

object ContactColorManager {
    const val PREFS_NAME = "contact_colors"
    private const val KEY_INITIALIZED = "is_initialized"

    fun initializeDefaultsIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                // Default entries requested: Mom = #FF69B4 (pink), Boss = #FF0000 (red)
                .putInt("Mom", 0xFFFF69B4.toInt())
                .putInt("Boss", 0xFFFF0000.toInt())
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
        }
    }

    fun getColorForContact(context: Context, contactName: String?): Int? {
        if (contactName.isNullOrBlank()) return null
        initializeDefaultsIfNeeded(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val cleanName = contactName.trim()

        // Exact match first
        if (prefs.contains(cleanName)) {
            return prefs.getInt(cleanName, 0)
        }

        // Case-insensitive fallback match
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (key != KEY_INITIALIZED && value is Int && key.equals(cleanName, ignoreCase = true)) {
                return value
            }
        }

        return null
    }

    fun saveContactColor(context: Context, contactName: String, color: Int) {
        val cleanName = contactName.trim()
        if (cleanName.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(cleanName, color).apply()
    }

    fun deleteContactColor(context: Context, contactName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(contactName).apply()
    }

    fun getAllContactColors(context: Context): List<ContactColorItem> {
        initializeDefaultsIfNeeded(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = mutableListOf<ContactColorItem>()
        for ((key, value) in prefs.all) {
            if (key != KEY_INITIALIZED && value is Int) {
                list.add(ContactColorItem(key, value))
            }
        }
        return list.sortedBy { it.contactName.lowercase() }
    }
}

class ContactColorActivity : ComponentActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etContactName: EditText
    private lateinit var layoutContactColors: LinearLayout
    private lateinit var btnSaveContact: Button
    private lateinit var tvEmptyContacts: TextView
    private lateinit var rvContactColors: RecyclerView
    private lateinit var adapter: ContactColorsAdapter

    private var selectedColor: Int = 0xFFFF69B4.toInt() // Pink default for contacts
    private val colorViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_color)

        ContactColorManager.initializeDefaultsIfNeeded(this)

        btnBack = findViewById(R.id.btnBack)
        etContactName = findViewById(R.id.etContactName)
        layoutContactColors = findViewById(R.id.layoutContactColors)
        btnSaveContact = findViewById(R.id.btnSaveContact)
        tvEmptyContacts = findViewById(R.id.tvEmptyContacts)
        rvContactColors = findViewById(R.id.rvContactColors)

        btnBack.setOnClickListener { finish() }

        setupColorPalette()
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
                refreshPaletteSelection()
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

            ContactColorManager.saveContactColor(this, name, selectedColor)
            Toast.makeText(this, "Saved color for \"$name\"", Toast.LENGTH_SHORT).show()
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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact_color, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val viewContactColor: View = itemView.findViewById(R.id.viewContactColor)
        private val tvContactName: TextView = itemView.findViewById(R.id.tvContactName)
        private val btnDeleteContact: ImageButton = itemView.findViewById(R.id.btnDeleteContact)

        fun bind(item: ContactColorItem) {
            tvContactName.text = item.contactName
            viewContactColor.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(item.color)
            }

            btnDeleteContact.setOnClickListener {
                onDeleteClick(item)
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
