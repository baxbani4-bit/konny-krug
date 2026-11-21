package ru.konnykrug.trainer

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.konnykrug.trainer.data.Client
import ru.konnykrug.trainer.data.Kind
import ru.konnykrug.trainer.data.Lesson
import ru.konnykrug.trainer.data.Status
import ru.konnykrug.trainer.data.Subscription

/** Строка расписания на выбранный день. */
data class LessonRow(
    val lesson: Lesson,
    val trainerText: String,
    val trainerColor: String,
    val subText: String,
    val priceText: String,
    val statusText: String
)

class LessonAdapter(
    private val onOpen: (Lesson) -> Unit,
    private val onDone: (Lesson) -> Unit
) : RecyclerView.Adapter<LessonAdapter.VH>() {

    var items: List<LessonRow> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val time: TextView = v.findViewById(R.id.time)
        val duration: TextView = v.findViewById(R.id.duration)
        val clientName: TextView = v.findViewById(R.id.clientName)
        val trainerName: TextView = v.findViewById(R.id.trainerName)
        val notes: TextView = v.findViewById(R.id.notes)
        val kindChip: TextView = v.findViewById(R.id.kindChip)
        val priceChip: TextView = v.findViewById(R.id.priceChip)
        val statusChip: TextView = v.findViewById(R.id.statusChip)
        val stripe: View = v.findViewById(R.id.stripe)
        val btnDone: ImageButton = v.findViewById(R.id.btnDone)
        val root: LinearLayout = v as LinearLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_lesson, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        val l = row.lesson
        holder.time.text = l.time
        holder.duration.text = "${l.durationMin} мин"
        holder.clientName.text = l.clientName.ifBlank { "Клиент" }
        holder.trainerName.text = row.trainerText
        holder.stripe.setBackgroundColor(runCatching { Color.parseColor(row.trainerColor) }.getOrDefault(Color.parseColor("#40916C")))

        if (l.notes.isBlank()) {
            holder.notes.visibility = View.GONE
        } else {
            holder.notes.visibility = View.VISIBLE
            holder.notes.text = l.notes
        }

        holder.kindChip.text = if (l.kind == Kind.SUBSCRIPTION) row.subText else "Разовая"
        holder.kindChip.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (l.kind == Kind.SUBSCRIPTION) "#B7E4C7" else "#F3EADA")
        )

        if (row.priceText.isBlank()) {
            holder.priceChip.visibility = View.GONE
        } else {
            holder.priceChip.visibility = View.VISIBLE
            holder.priceChip.text = row.priceText
        }

        if (l.status == Status.PLANNED) {
            holder.statusChip.visibility = View.GONE
            holder.btnDone.visibility = View.VISIBLE
            holder.btnDone.setImageResource(R.drawable.ic_check)
            holder.clientName.alpha = 1f
        } else {
            holder.statusChip.visibility = View.VISIBLE
            holder.statusChip.text = row.statusText
            holder.statusChip.backgroundTintList = ColorStateList.valueOf(
                Color.parseColor(if (l.status == Status.DONE) "#E9A23B" else "#E8D3D1")
            )
            holder.btnDone.visibility = if (l.status == Status.DONE) View.VISIBLE else View.GONE
            holder.btnDone.setImageResource(R.drawable.ic_refresh)
            holder.clientName.alpha = if (l.status == Status.CANCELLED) 0.5f else 1f
        }

        holder.root.setOnClickListener { onOpen(l) }
        holder.btnDone.setOnClickListener { onDone(l) }
    }
}

/** Строка списка клиентов. */
data class ClientRow(val client: Client, val extra: String)

class ClientAdapter(private val onOpen: (Client) -> Unit) : RecyclerView.Adapter<ClientAdapter.VH>() {

    var items: List<ClientRow> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: TextView = v.findViewById(R.id.avatar)
        val name: TextView = v.findViewById(R.id.name)
        val phone: TextView = v.findViewById(R.id.phone)
        val extra: TextView = v.findViewById(R.id.extra)
        val root: LinearLayout = v as LinearLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_client, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        val c = row.client
        holder.name.text = c.name.ifBlank { "Без имени" }
        holder.phone.text = c.phone.ifBlank { "телефон не указан" }
        holder.avatar.text = c.name.trim().take(1).uppercase().ifBlank { "?" }
        if (row.extra.isBlank()) {
            holder.extra.visibility = View.GONE
        } else {
            holder.extra.visibility = View.VISIBLE
            holder.extra.text = row.extra
        }
        holder.root.setOnClickListener { onOpen(c) }
    }
}

/** Строка списка абонементов. */
data class SubRow(val sub: Subscription, val clientName: String, val details: String)

class SubAdapter(private val onOpen: (Subscription) -> Unit) : RecyclerView.Adapter<SubAdapter.VH>() {

    var items: List<SubRow> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val clientName: TextView = v.findViewById(R.id.clientName)
        val left: TextView = v.findViewById(R.id.left)
        val progress: ProgressBar = v.findViewById(R.id.progress)
        val details: TextView = v.findViewById(R.id.details)
        val root: LinearLayout = v as LinearLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_sub, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        val s = row.sub
        holder.clientName.text = row.clientName
        holder.left.text = "${s.left} из ${s.total}"
        holder.progress.progress = if (s.total <= 0) 0 else (s.used * 100 / s.total)
        holder.details.text = row.details
        holder.root.setOnClickListener { onOpen(s) }
    }
}

/** Карточка дня в горизонтальной ленте. */
class DayHolder(view: View) : RecyclerView.ViewHolder(view) {
    val dow: TextView = view.findViewById(R.id.dow)
    val dayNum: TextView = view.findViewById(R.id.dayNum)
    val marker: View = view.findViewById(R.id.marker)
}
