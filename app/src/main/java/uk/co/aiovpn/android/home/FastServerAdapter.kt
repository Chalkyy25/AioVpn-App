package uk.co.aiovpn.android.home

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import uk.co.aiovpn.android.R
import java.util.Locale

class FastServerAdapter(
    private var items: List<FastServerItem>,
    private val onMoveToSidebar: () -> Unit,
    private val onServerClick: (FastServerItem) -> Unit
) : RecyclerView.Adapter<FastServerAdapter.FastServerViewHolder>() {

    inner class FastServerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val serverFlag: ImageView = view.findViewById(R.id.serverFlag)
        val serverName: TextView = view.findViewById(R.id.serverName)
        val serverPing: TextView = view.findViewById(R.id.serverPing)
        val serverSubtitle: TextView = view.findViewById(R.id.serverSubtitle)
        val serverAction: TextView = view.findViewById(R.id.serverAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FastServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fast_server, parent, false)
        return FastServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: FastServerViewHolder, position: Int) {
        val item = items[position]

        holder.serverName.text = item.label
        holder.serverPing.text = item.pingText
        
        holder.serverSubtitle.text = when {
            item.isAllServers -> holder.itemView.context.getString(R.string.aio_title_servers)
            !item.cityName.isNullOrBlank() -> item.cityName
            else -> holder.itemView.context.getString(R.string.aio_fastest_subtitle)
        }

        holder.serverAction.text =
            if (item.isAllServers) holder.itemView.context.getString(R.string.aio_title_servers)
            else holder.itemView.context.getString(R.string.aio_quick_connect)

        holder.serverPing.setTextColor(
            if (item.isAllServers) 0xFFEAF1FF.toInt() else 0xFF57E389.toInt()
        )

        holder.serverFlag.setImageResource(
            if (item.isAllServers) R.drawable.ic_nav_servers
            else resolveFlagRes(holder.itemView, item.countryCode)
        )

        holder.itemView.alpha = 0.96f

        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate()
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .alpha(1f)
                    .setDuration(120)
                    .start()
            } else {
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(0.96f)
                    .setDuration(120)
                    .start()
            }
        }

        holder.itemView.setOnClickListener {
            onServerClick(item)
        }

        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && position == 0) {
                onMoveToSidebar()
                true
            } else {
                false
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun getItems(): List<FastServerItem> = items

    fun updateItems(newItems: List<FastServerItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    private fun resolveFlagRes(view: View, countryCode: String?): Int {
        val normalized = countryCode
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.length == 2 }
            ?: return R.drawable.ic_nav_servers

        val resId = view.resources.getIdentifier(
            "flag_$normalized",
            "drawable",
            view.context.packageName
        )

        return if (resId != 0) resId else R.drawable.ic_nav_servers
    }
}
