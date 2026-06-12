package com.promptowy.noctra

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TabAdapter(
    private val tabs: List<Tab>,
    private val activeTabId: Int,
    private val profileManager: ProfileManager,
    private val onSwitch: (Int) -> Unit,
    private val onClose: (Int) -> Unit
) : RecyclerView.Adapter<TabAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.tabDot)
        val title: TextView = view.findViewById(R.id.tabTitle)
        val close: Button = view.findViewById(R.id.tabClose)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false))

    override fun getItemCount() = tabs.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val tab = tabs[pos]
        val isActive = tab.id == activeTabId
        val colorStr = profileManager.getColor(tab.profile)

        try { h.dot.setBackgroundColor(Color.parseColor(colorStr)) } catch (_: Exception) {}
        h.title.text = if (tab.isLoading) "· ${tab.title}" else tab.title
        h.title.setTextColor(h.itemView.context.getColor(if (isActive) R.color.fg else R.color.dim))
        h.itemView.setBackgroundResource(if (isActive) R.drawable.bg_tab_active else android.R.color.transparent)
        h.itemView.setOnClickListener { onSwitch(tab.id) }
        h.close.setOnClickListener { onClose(tab.id) }
    }
}
