package com.example.lednotify

import android.app.AlertDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfoItem(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
    var color: Int,
    var isEnabled: Boolean
)

class AppColorSettingsActivity : ComponentActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvAppCount: TextView
    private lateinit var tvEnabledSummary: TextView
    private lateinit var btnDisableAll: Button
    private lateinit var btnEnableAll: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var rvAppColors: RecyclerView
    private lateinit var adapter: AppColorsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_color_settings)

        btnBack = findViewById(R.id.btnBack)
        tvAppCount = findViewById(R.id.tvAppCount)
        tvEnabledSummary = findViewById(R.id.tvEnabledSummary)
        btnDisableAll = findViewById(R.id.btnDisableAll)
        btnEnableAll = findViewById(R.id.btnEnableAll)
        progressBar = findViewById(R.id.progressBar)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        rvAppColors = findViewById(R.id.rvAppColors)

        btnBack.setOnClickListener { finish() }

        adapter = AppColorsAdapter(
            onItemClick = { item, position ->
                showColorPickerDialog(item, position)
            },
            onToggleEnabled = { item, isEnabled ->
                AppColorManager.setAppEnabled(this, item.packageName, isEnabled)
                item.isEnabled = isEnabled
                updateSummary()
            }
        )
        rvAppColors.layoutManager = LinearLayoutManager(this)
        rvAppColors.adapter = adapter

        btnEnableAll.setOnClickListener {
            val allItems = adapter.getItems()
            for (item in allItems) {
                item.isEnabled = true
                AppColorManager.setAppEnabled(this, item.packageName, true)
            }
            adapter.notifyDataSetChanged()
            updateSummary()
        }

        btnDisableAll.setOnClickListener {
            val allItems = adapter.getItems()
            for (item in allItems) {
                item.isEnabled = false
                AppColorManager.setAppEnabled(this, item.packageName, false)
            }
            adapter.notifyDataSetChanged()
            updateSummary()
        }

        loadInstalledUserApps()
    }

    private fun updateSummary() {
        val allItems = adapter.getItems()
        val enabledCount = allItems.count { it.isEnabled }
        tvEnabledSummary.text = "$enabledCount of ${allItems.size} apps enabled for LED"
    }

    private fun loadInstalledUserApps() {
        progressBar.visibility = View.VISIBLE
        tvEmptyState.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            AppColorManager.initializeDefaultsIfNeeded(applicationContext)

            val pm = packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                pm.getInstalledApplications(0)
            }

            val userApps = packages.filter { app ->
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                // Exclude system apps and this app itself
                (!isSystem || isUpdatedSystem) && app.packageName != packageName
            }.map { app ->
                val label = app.loadLabel(pm).toString()
                val icon = app.loadIcon(pm)
                val color = AppColorManager.getColorForPackage(applicationContext, app.packageName)
                val isEnabled = AppColorManager.isAppEnabled(applicationContext, app.packageName)
                AppInfoItem(label, app.packageName, icon, color, isEnabled)
            }.sortedBy { it.appName.lowercase() }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                if (userApps.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    tvAppCount.text = "No user apps found"
                    tvEnabledSummary.text = "0 apps enabled"
                } else {
                    tvAppCount.text = "${userApps.size} apps found. Toggle to allow LED."
                    adapter.setItems(userApps)
                    updateSummary()
                }
            }
        }
    }

    private fun showColorPickerDialog(item: AppInfoItem, position: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null)
        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvDialogSubtitle = dialogView.findViewById<TextView>(R.id.tvDialogSubtitle)
        val gridColors = dialogView.findViewById<GridLayout>(R.id.gridColors)

        tvDialogTitle.text = item.appName
        tvDialogSubtitle.text = "Choose LED color for ${item.packageName}"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val density = resources.displayMetrics.density
        val sizePx = (46 * density).toInt()
        val marginPx = (6 * density).toInt()

        for (color in AppColorManager.PALETTE_12) {
            val colorView = View(this).apply {
                val params = GridLayout.LayoutParams().apply {
                    width = sizePx
                    height = sizePx
                    setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                layoutParams = params
                val isSelected = (color == item.color)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (isSelected) {
                        setStroke((3 * density).toInt(), Color.WHITE)
                    } else {
                        setStroke((1 * density).toInt(), Color.parseColor("#444444"))
                    }
                }
                setOnClickListener {
                    AppColorManager.setColorForPackage(this@AppColorSettingsActivity, item.packageName, color)
                    // If user selects color, make sure app is enabled
                    if (!item.isEnabled) {
                        item.isEnabled = true
                        AppColorManager.setAppEnabled(this@AppColorSettingsActivity, item.packageName, true)
                        updateSummary()
                    }
                    item.color = color
                    adapter.notifyItemChanged(position)
                    dialog.dismiss()
                }
            }
            gridColors.addView(colorView)
        }

        dialog.show()
    }
}

class AppColorsAdapter(
    private val onItemClick: (AppInfoItem, Int) -> Unit,
    private val onToggleEnabled: (AppInfoItem, Boolean) -> Unit
) : RecyclerView.Adapter<AppColorsAdapter.AppViewHolder>() {

    private val items = mutableListOf<AppInfoItem>()

    fun setItems(newItems: List<AppInfoItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItems(): List<AppInfoItem> = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_color, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardApp: CardView = itemView.findViewById(R.id.cardApp)
        private val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        private val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        private val tvAppPackage: TextView = itemView.findViewById(R.id.tvAppPackage)
        private val viewColorPreview: View = itemView.findViewById(R.id.viewColorPreview)
        private val switchAppEnabled: Switch = itemView.findViewById(R.id.switchAppEnabled)

        fun bind(item: AppInfoItem) {
            ivAppIcon.setImageDrawable(item.icon)
            tvAppName.text = item.appName
            tvAppPackage.text = item.packageName

            viewColorPreview.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(item.color)
            }

            // Temporarily clear listener to prevent firing on recycling
            switchAppEnabled.setOnCheckedChangeListener(null)
            switchAppEnabled.isChecked = item.isEnabled
            cardApp.alpha = if (item.isEnabled) 1.0f else 0.5f

            switchAppEnabled.setOnCheckedChangeListener { _, isChecked ->
                item.isEnabled = isChecked
                cardApp.alpha = if (isChecked) 1.0f else 0.5f
                onToggleEnabled(item, isChecked)
            }

            viewColorPreview.setOnClickListener {
                onItemClick(item, bindingAdapterPosition)
            }

            itemView.setOnClickListener {
                onItemClick(item, bindingAdapterPosition)
            }
        }
    }
}
