package com.example.lednotify

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.Toast
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lednotify.databinding.FragmentAppsBinding
import com.example.lednotify.databinding.ItemAppColorBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    // 12 Dialog Colors for Apps & Contacts
    private val appDialogColors = listOf(
        0xFF00E676.toInt(), // Green
        0xFFFF1744.toInt(), // Red
        0xFF2979FF.toInt(), // Blue
        0xFFFFD600.toInt(), // Amber / Yellow
        0xFFFF007F.toInt(), // Pink / Magenta
        0xFF00E5FF.toInt(), // Cyan
        0xFFFF9100.toInt(), // Orange
        0xFFFFFFFF.toInt(), // White
        0xFF7C4DFF.toInt(), // Purple
        0xFF00B0FF.toInt(), // Light Blue
        0xFF1DE9B6.toInt(), // Teal
        0xFFFF5252.toInt()  // Coral
    )

    // Apps Tab state
    private val allAppsList = mutableListOf<AppInfoItem>()
    private val filteredAppsList = mutableListOf<AppInfoItem>()
    private lateinit var appsAdapter: AppsAdapter

    // Contacts Tab state
    private var selectedContactColor: Int = 0xFF00E676.toInt()
    private var selectedContactSpeed: Int = 600
    private lateinit var contactColorsAdapter: ContactColorsPickerAdapter
    private lateinit var savedContactsAdapter: SavedContactsAdapter
    private val savedContactsList = mutableListOf<ContactColorItem>()

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri != null) {
            val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
            val cursor = requireContext().contentResolver.query(contactUri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        val name = it.getString(nameIdx)
                        if (!name.isNullOrBlank()) {
                            binding.etContactName.setText(name)
                        }
                    }
                }
            }
        }
    }

    private val contactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickContactLauncher.launch(null)
        } else {
            Toast.makeText(requireContext(), "Contacts permission needed to pick contact", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabSwitching()
        setupAppsTab()
        setupContactsTab()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupTabSwitching() {
        binding.tabLayoutApps.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 1) {
                    binding.layoutAppsTab.visibility = View.GONE
                    binding.layoutContactsTab.visibility = View.VISIBLE
                    refreshSavedContacts()
                } else {
                    binding.layoutAppsTab.visibility = View.VISIBLE
                    binding.layoutContactsTab.visibility = View.GONE
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    // ==========================================
    // APPS TAB IMPLEMENTATION
    // ==========================================
    private fun setupAppsTab() {
        appsAdapter = AppsAdapter(filteredAppsList,
            onItemClick = { item ->
                showAppColorDialog(item)
            },
            onToggle = { item, isEnabled ->
                val ctx = requireContext()
                AppColorManager.setAppEnabled(ctx, item.packageName, isEnabled)
                item.isEnabled = isEnabled
                updateAppsSummary()
            }
        )

        binding.rvAppsList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAppsList.adapter = appsAdapter

        binding.btnEnableAllApps.setOnClickListener {
            val ctx = requireContext()
            for (app in allAppsList) {
                app.isEnabled = true
                AppColorManager.setAppEnabled(ctx, app.packageName, true)
            }
            appsAdapter.notifyDataSetChanged()
            updateAppsSummary()
        }

        binding.btnDisableAllApps.setOnClickListener {
            val ctx = requireContext()
            for (app in allAppsList) {
                app.isEnabled = false
                AppColorManager.setAppEnabled(ctx, app.packageName, false)
            }
            appsAdapter.notifyDataSetChanged()
            updateAppsSummary()
        }

        binding.etSearchApp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString().trim())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        binding.progressBarApps.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ctx = requireContext().applicationContext
            AppColorManager.initializeDefaultsIfNeeded(ctx)

            val pm = ctx.packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                pm.getInstalledApplications(0)
            }

            val defaultColor = AppColorManager.getDefaultColor(ctx)
            val items = mutableListOf<AppInfoItem>()

            for (appInfo in packages) {
                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null && appInfo.packageName != ctx.packageName) {
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    val color = AppColorManager.getColorForPackage(ctx, appInfo.packageName)
                    val isEnabled = AppColorManager.isAppEnabled(ctx, appInfo.packageName)
                    items.add(AppInfoItem(appName, appInfo.packageName, icon, color, isEnabled))
                }
            }

            items.sortBy { it.appName.lowercase() }

            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    binding.progressBarApps.visibility = View.GONE
                    allAppsList.clear()
                    allAppsList.addAll(items)
                    filterApps(binding.etSearchApp.text.toString().trim())
                }
            }
        }
    }

    private fun filterApps(query: String) {
        filteredAppsList.clear()
        if (query.isEmpty()) {
            filteredAppsList.addAll(allAppsList)
        } else {
            for (app in allAppsList) {
                if (app.appName.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)) {
                    filteredAppsList.add(app)
                }
            }
        }
        appsAdapter.notifyDataSetChanged()
        updateAppsSummary()
    }

    private fun updateAppsSummary() {
        val total = allAppsList.size
        val enabled = allAppsList.count { it.isEnabled }
        binding.tvAppCountSummary.text = "$enabled of $total apps enabled for LED"
    }

    private fun showAppColorDialog(item: AppInfoItem) {
        val ctx = requireContext()
        val density = resources.displayMetrics.density

        val grid = GridLayout(ctx).apply {
            columnCount = 4
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        var dialog: AlertDialog? = null

        for (color in appDialogColors) {
            val colorView = View(ctx).apply {
                val sizePx = (48 * density).toInt()
                layoutParams = GridLayout.LayoutParams().apply {
                    width = sizePx
                    height = sizePx
                    setMargins((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (color == item.color) {
                        setStroke((3 * density).toInt(), Color.WHITE)
                    }
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    item.color = color
                    AppColorManager.setColorForPackage(ctx, item.packageName, color)
                    appsAdapter.notifyDataSetChanged()
                    dialog?.dismiss()
                    Toast.makeText(ctx, "Updated LED color for ${item.appName}", Toast.LENGTH_SHORT).show()
                }
            }
            grid.addView(colorView)
        }

        dialog = AlertDialog.Builder(ctx)
            .setTitle("Pick LED Color for ${item.appName}")
            .setView(grid)
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class AppsAdapter(
        private val list: List<AppInfoItem>,
        private val onItemClick: (AppInfoItem) -> Unit,
        private val onToggle: (AppInfoItem, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

        inner class AppViewHolder(val b: ItemAppColorBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val b = ItemAppColorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return AppViewHolder(b)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val item = list[position]
            val b = holder.b

            b.ivAppIcon.setImageDrawable(item.icon)
            b.tvAppName.text = item.appName
            b.tvAppPackage.text = item.packageName

            b.viewColorPreview.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(item.color)
            }

            b.switchAppEnabled.setOnCheckedChangeListener(null)
            b.switchAppEnabled.isChecked = item.isEnabled
            b.switchAppEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item, isChecked)
            }

            b.viewColorPreview.setOnClickListener {
                onItemClick(item)
            }

            holder.itemView.setOnClickListener {
                onItemClick(item)
            }
        }

        override fun getItemCount(): Int = list.size
    }

    // ==========================================
    // CONTACTS TAB IMPLEMENTATION (WhatsApp)
    // ==========================================
    private fun setupContactsTab() {
        binding.btnPickContactPhone.setOnClickListener {
            val ctx = requireContext()
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                pickContactLauncher.launch(null)
            } else {
                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }

        // Color circles for contact
        contactColorsAdapter = ContactColorsPickerAdapter(appDialogColors, selectedContactColor) { color ->
            selectedContactColor = color
        }
        binding.rvContactColorPicker.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvContactColorPicker.adapter = contactColorsAdapter

        // Speed chips for contact
        binding.chipGroupContactSpeed.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedContactSpeed = when {
                checkedIds.contains(R.id.chipSpeed150) -> 150
                checkedIds.contains(R.id.chipSpeed250) -> 250
                checkedIds.contains(R.id.chipSpeed400) -> 400
                checkedIds.contains(R.id.chipSpeed900) -> 900
                else -> 600
            }
        }

        // Save Contact Button
        binding.btnSaveContactColor.setOnClickListener {
            val name = binding.etContactName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a contact name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ctx = requireContext()
            ContactColorManager.saveContactColor(ctx, name, selectedContactColor, selectedContactSpeed)

            binding.etContactName.setText("")
            Toast.makeText(ctx, "Saved LED for $name", Toast.LENGTH_SHORT).show()
            refreshSavedContacts()
        }

        // Saved Contacts Recycler
        savedContactsAdapter = SavedContactsAdapter(savedContactsList) { item ->
            val ctx = requireContext()
            AlertDialog.Builder(ctx)
                .setTitle("Delete Contact Mapping?")
                .setMessage("Remove custom LED color for ${item.contactName}?")
                .setPositiveButton("Delete") { _, _ ->
                    ContactColorManager.deleteContactColor(ctx, item.contactName)
                    refreshSavedContacts()
                    Toast.makeText(ctx, "Removed ${item.contactName}", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.rvSavedContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSavedContacts.adapter = savedContactsAdapter

        refreshSavedContacts()
    }

    private fun refreshSavedContacts() {
        val ctx = context ?: return
        savedContactsList.clear()
        savedContactsList.addAll(ContactColorManager.getAllContactColors(ctx))
        savedContactsAdapter.notifyDataSetChanged()
    }

    inner class ContactColorsPickerAdapter(
        private val colors: List<Int>,
        private var selected: Int,
        private val onSelect: (Int) -> Unit
    ) : RecyclerView.Adapter<ContactColorsPickerAdapter.ColorViewHolder>() {

        inner class ColorViewHolder(
            val root: FrameLayout,
            val ringView: View,
            val dotView: View
        ) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val density = parent.context.resources.displayMetrics.density
            val root = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    (48 * density).toInt(),
                    (48 * density).toInt()
                ).apply {
                    marginEnd = (8 * density).toInt()
                }
            }
            val ring = View(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt()).apply {
                    gravity = Gravity.CENTER
                }
            }
            val dot = View(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams((32 * density).toInt(), (32 * density).toInt()).apply {
                    gravity = Gravity.CENTER
                }
            }
            root.addView(ring)
            root.addView(dot)
            return ColorViewHolder(root, ring, dot)
        }

        override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
            val color = colors[position]
            val density = holder.itemView.resources.displayMetrics.density

            holder.dotView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }

            if (color == selected) {
                holder.ringView.visibility = View.VISIBLE
                holder.ringView.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke((3 * density).toInt(), Color.WHITE)
                }
            } else {
                holder.ringView.visibility = View.GONE
            }

            holder.root.setOnClickListener {
                selected = color
                notifyDataSetChanged()
                onSelect(color)
            }
        }

        override fun getItemCount(): Int = colors.size
    }

    inner class SavedContactsAdapter(
        private val list: List<ContactColorItem>,
        private val onDeleteClick: (ContactColorItem) -> Unit
    ) : RecyclerView.Adapter<SavedContactsAdapter.ContactViewHolder>() {

        inner class ContactViewHolder(
            view: View,
            val dotView: View,
            val nameView: TextView,
            val speedView: TextView,
            val deleteBtn: ImageButton
        ) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
            val density = parent.context.resources.displayMetrics.density
            val card = androidx.cardview.widget.CardView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, (4 * density).toInt(), 0, (4 * density).toInt())
                }
                setCardBackgroundColor(Color.parseColor("#121212"))
                radius = 16 * density
                cardElevation = 0f
            }

            val row = LinearLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
            }

            val dot = View(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams((32 * density).toInt(), (32 * density).toInt())
            }

            val textLayout = LinearLayout(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (14 * density).toInt()
                }
                orientation = LinearLayout.VERTICAL
            }

            val tvName = TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                textSize = 15f
                paint.isFakeBoldText = true
            }

            val tvSpeed = TextView(parent.context).apply {
                setTextColor(Color.parseColor("#80CBC4"))
                textSize = 12f
            }

            textLayout.addView(tvName)
            textLayout.addView(tvSpeed)

            val btnDelete = ImageButton(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt())
                setBackgroundColor(Color.TRANSPARENT)
                setImageResource(R.drawable.ic_delete)
                setColorFilter(Color.parseColor("#FF5252"))
                contentDescription = "Delete Contact"
            }

            row.addView(dot)
            row.addView(textLayout)
            row.addView(btnDelete)
            card.addView(row)

            return ContactViewHolder(card, dot, tvName, tvSpeed, btnDelete)
        }

        override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
            val item = list[position]

            holder.nameView.text = item.contactName
            holder.speedView.text = "Blink: ${item.speedMs}ms"

            holder.dotView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(item.color)
            }

            holder.deleteBtn.setOnClickListener {
                onDeleteClick(item)
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
