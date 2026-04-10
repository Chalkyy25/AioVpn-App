package uk.co.aiovpn.android.login

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import uk.co.aiovpn.android.R
import uk.co.aiovpn.android.api.WgServerDto
import java.util.Locale

data class ServerUiItem(
    val server: WgServerDto,
    val pingText: String = "... ms"
)

class ServerAdapter(
    private var items: List<ServerUiItem>,
    private val onMoveToSidebar: () -> Unit,
    private val onServerClick: (WgServerDto) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    inner class ServerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val serverFlag: ImageView = view.findViewById(R.id.serverFlag)
        val serverName: TextView = view.findViewById(R.id.serverName)
        val serverLocation: TextView = view.findViewById(R.id.serverLocation)
        val serverPing: TextView = view.findViewById(R.id.serverPing)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server_full, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val item = items[position]
        val server = item.server

        holder.serverName.text = when {
            !server.label.isNullOrBlank() -> server.label
            !server.country_code.isNullOrBlank() -> {
                Locale("", server.country_code).displayCountry.ifBlank { server.name }
            }
            else -> server.name
        }

        holder.serverLocation.text = server.city.orEmpty()
        holder.serverPing.text = item.pingText
        holder.serverFlag.setImageResource(resolveFlagRes(holder.itemView, server.country_code))

        holder.itemView.alpha = 0.96f

        holder.itemView.setOnClickListener {
            onServerClick(server)
        }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate()
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .alpha(1f)
                    .setDuration(120)
                    .start()
            } else {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(0.96f)
                    .setDuration(120)
                    .start()
            }
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

    fun updateItems(newItems: List<ServerUiItem>) {
        items = newItems
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
