package com.example.lednotify

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
            onDeleteClick = { item, position ->
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
    private val onDeleteClick: (ContactColorItem, Int) -> Unit,
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
                onDeleteClick(item, bindingAdapterPosition)
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
