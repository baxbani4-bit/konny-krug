package ru.konnykrug.trainer

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import ru.konnykrug.trainer.data.Client
import ru.konnykrug.trainer.data.Kind
import ru.konnykrug.trainer.data.Lesson
import ru.konnykrug.trainer.data.Status
import ru.konnykrug.trainer.data.Store
import ru.konnykrug.trainer.data.Subscription
import ru.konnykrug.trainer.data.Sync
import ru.konnykrug.trainer.data.SyncResult
import ru.konnykrug.trainer.data.Trainer
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private val sync: Sync by lazy { Sync(store) }

    private var selectedDate = LocalDate.now().toString()
    private var tab = 0

    /** Последний показанный диалог — используется в автотестах интерфейса. */
    internal var lastDialog: AlertDialog? = null

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var syncState: TextView
    private lateinit var dateLabel: TextView
    private lateinit var weekStrip: LinearLayout
    private lateinit var lessonsAdapter: LessonAdapter
    private lateinit var clientsAdapter: ClientAdapter
    private lateinit var subsAdapter: SubAdapter
    private lateinit var emptyLessons: TextView
    private lateinit var fab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = Store(this)

        title = findViewById(R.id.title)
        subtitle = findViewById(R.id.subtitle)
        syncState = findViewById(R.id.syncState)
        dateLabel = findViewById(R.id.dateLabel)
        weekStrip = findViewById(R.id.weekStrip)
        emptyLessons = findViewById(R.id.emptyLessons)
        fab = findViewById(R.id.fab)

        lessonsAdapter = LessonAdapter(onOpen = { openLessonDialog(it) }, onDone = { toggleDone(it) })
        clientsAdapter = ClientAdapter(onOpen = { openClientDialog(it) })
        subsAdapter = SubAdapter(onOpen = { openSubDialog(it) })

        findViewById<RecyclerView>(R.id.listLessons).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = lessonsAdapter
        }
        findViewById<RecyclerView>(R.id.listClients).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = clientsAdapter
        }
        findViewById<RecyclerView>(R.id.listSubs).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = subsAdapter
        }

        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            selectedDate = Dates.shift(selectedDate, -1)
            refreshAll()
        }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            selectedDate = Dates.shift(selectedDate, 1)
            refreshAll()
        }
        findViewById<Button>(R.id.btnToday).setOnClickListener {
            selectedDate = Dates.today()
            refreshAll()
        }
        findViewById<ImageButton>(R.id.btnSync).setOnClickListener { doSync(manual = true) }
        findViewById<ImageButton>(R.id.btnMe).setOnClickListener { openMeDialog() }

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            tab = when (item.itemId) {
                R.id.tab_clients -> 1
                R.id.tab_subs -> 2
                else -> 0
            }
            refreshAll()
            true
        }

        fab.setOnClickListener {
            when (tab) {
                0 -> openLessonDialog(null)
                1 -> openClientDialog(null)
                else -> openSubDialog(null)
            }
        }

        if (!store.isOnboarded) {
            openMeDialog(firstRun = true)
        } else {
            refreshAll()
            doSync(manual = false)
        }
    }

    override fun onResume() {
        super.onResume()
        if (store.isOnboarded) {
            refreshAll()
            doSync(manual = false)
        }
    }

    // ---------------- обновление экранов ----------------

    private fun refreshAll() {
        refreshHeader()
        refreshScreens()
        refreshWeekStrip()
        refreshList()
    }

    private fun refreshHeader() {
        val me = store.me()
        subtitle.text = if (me == null) "Запись клиентов на прогулки" else "Тренер: ${me.name}"
        syncState.text = when {
            !store.isOnboarded -> "Профиль не настроен — нажмите на иконку справа сверху"
            store.lastSync == 0L -> "Ещё не синхронизировано · ${store.serverUrl}"
            else -> {
                val fmt = SimpleDateFormat("HH:mm", Locale("ru"))
                "Синхронизировано: ${fmt.format(Date(store.lastSync))} · ${store.serverUrl}"
            }
        }
    }

    private fun refreshScreens() {
        findViewById<View>(R.id.screenSchedule).visibility = if (tab == 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.screenClients).visibility = if (tab == 1) View.VISIBLE else View.GONE
        findViewById<View>(R.id.screenSubs).visibility = if (tab == 2) View.VISIBLE else View.GONE
        dateLabel.text = Dates.longLabel(selectedDate)
        findViewById<View>(R.id.btnPrev).visibility = if (tab == 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnNext).visibility = if (tab == 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnToday).visibility = if (tab == 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.weekStrip).visibility = if (tab == 0) View.VISIBLE else View.GONE
    }

    private fun refreshWeekStrip() {
        val days = Dates.week()
        val lessons = store.lessons().filter { !it.deleted }
        weekStrip.removeAllViews()
        val inflater = LayoutInflater.from(this)
        days.forEach { iso ->
            val v = inflater.inflate(R.layout.item_day, weekStrip, false)
            v.findViewById<TextView>(R.id.dow).text = Dates.dow(iso)
            v.findViewById<TextView>(R.id.dayNum).text = Dates.dayNumber(iso)
            val isSelected = iso == selectedDate
            val num = v.findViewById<TextView>(R.id.dayNum)
            if (isSelected) {
                num.background = ContextCompat.getDrawable(this, R.drawable.dot)
                num.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2D6A4F"))
                num.setTextColor(Color.WHITE)
            } else {
                num.background = null
                num.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
            val count = lessons.count { it.date == iso }
            val marker = v.findViewById<View>(R.id.marker)
            marker.visibility = if (count > 0) View.VISIBLE else View.INVISIBLE
            val density = resources.displayMetrics.density
            marker.layoutParams = marker.layoutParams.apply {
                width = ((if (count > 1) 16 else 6) * density).toInt()
                height = (6 * density).toInt()
            }
            v.setOnClickListener {
                selectedDate = iso
                refreshAll()
            }
            weekStrip.addView(v)
        }
    }

    private fun refreshList() {
        when (tab) {
            0 -> refreshLessons()
            1 -> refreshClients()
            else -> refreshSubs()
        }
    }

    private fun refreshLessons() {
        val clients = store.clients().filter { !it.deleted }.associateBy { it.id }
        val subs = store.subscriptions().filter { !it.deleted }.associateBy { it.id }
        val trainers = store.trainers().associateBy { it.id }

        val rows = store.lessons()
            .filter { !it.deleted && it.date == selectedDate }
            .sortedBy { it.time }
            .map { l ->
                val sub = l.subscriptionId?.let { subs[it] }
                LessonRow(
                    lesson = l,
                    trainerText = "Тренер: ${trainers[l.trainerId]?.name ?: l.trainerName.ifBlank { "—" }}",
                    trainerColor = trainers[l.trainerId]?.color ?: "#40916C",
                    subText = if (sub != null) "Абонемент · осталось ${sub.left}" else "Абонемент",
                    priceText = l.price.toLong().toMoney(),
                    statusText = when (l.status) {
                        Status.DONE -> "Выполнено"
                        else -> "Отменено"
                    }
                )
            }
        lessonsAdapter.items = rows
        emptyLessons.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        // подсказка об именах клиентов, чтобы не было «пустых» строк
        if (clients.isEmpty() && rows.isEmpty() && tab == 0) {
            emptyLessons.text = "Записей нет.\nСначала добавьте клиента во вкладке «Клиенты», затем нажмите «+»."
        }
    }

    private fun refreshClients() {
        val lessons = store.lessons().filter { !it.deleted }
        val subs = store.subscriptions().filter { !it.deleted }
        val rows = store.clients()
            .filter { !it.deleted }
            .sortedBy { it.name.lowercase() }
            .map { c ->
                val activeSubs = subs.filter { it.clientId == c.id && it.left > 0 }
                val upcoming = lessons.filter { it.clientId == c.id && it.status == Status.PLANNED && it.date >= Dates.today() }
                val parts = mutableListOf<String>()
                if (activeSubs.isNotEmpty()) {
                    parts.add("абонемент: " + plural(activeSubs.sumOf { it.left }, "занятие", "занятия", "занятий"))
                }
                val visits = lessons.count { it.clientId == c.id && it.status == Status.DONE }
                parts.add("визитов: $visits")
                if (upcoming.isNotEmpty()) parts.add("запланировано: ${upcoming.size}")
                ClientRow(c, parts.joinToString(" · "))
            }
        clientsAdapter.items = rows
    }

    private fun refreshSubs() {
        val clients = store.clients().associateBy { it.id }
        val rows = store.subscriptions()
            .filter { !it.deleted }
            .sortedByDescending { it.updatedAt }
            .map { s ->
                SubRow(
                    sub = s,
                    clientName = clients[s.clientId]?.name ?: "Клиент удалён",
                    details = "Начало: ${Dates.toHuman(s.startDate)}" +
                        (if (s.price > 0) " · ${s.price.toLong().toMoney()}" else "") +
                        " · использовано ${s.used}"
                )
            }
        subsAdapter.items = rows
    }

    // ---------------- синхронизация ----------------

    private fun doSync(manual: Boolean) {
        if (!store.isOnboarded) return
        if (manual) Toast.makeText(this, "Синхронизация…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            when (val r = sync.sync()) {
                is SyncResult.Ok -> {
                    refreshAll()
                    if (manual) {
                        Toast.makeText(
                            this@MainActivity,
                            "Готово: отправлено ${r.pushed}, получено ${r.pulled}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                is SyncResult.Error -> {
                    refreshHeader()
                    if (manual) {
                        Toast.makeText(this@MainActivity, "Нет связи с сервером: ${r.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // ---------------- диалог: запись ----------------

    private fun openLessonDialog(existing: Lesson?) {
        val clients = store.clients().filter { !it.deleted }.sortedBy { it.name.lowercase() }
        if (clients.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Сначала добавьте клиента")
                .setMessage("Запись создаётся на клиента. Добавьте его во вкладке «Клиенты».")
                .setPositiveButton("Добавить клиента") { _, _ ->
                    tab = 1
                    findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.tab_clients
                    openClientDialog(null)
                }
                .setNegativeButton("Отмена", null)
                .show()
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_lesson, null)
        val spClient = view.findViewById<Spinner>(R.id.spClient)
        val spTrainer = view.findViewById<Spinner>(R.id.spTrainer)
        val spSub = view.findViewById<Spinner>(R.id.spSub)
        val etDate = view.findViewById<TextView>(R.id.etDate)
        val etTime = view.findViewById<TextView>(R.id.etTime)
        val etDuration = view.findViewById<EditText>(R.id.etDuration)
        val etPrice = view.findViewById<EditText>(R.id.etPrice)
        val etNotes = view.findViewById<EditText>(R.id.etNotes)
        val rgKind = view.findViewById<RadioGroup>(R.id.rgKind)
        val rbSub = view.findViewById<RadioButton>(R.id.rbSub)
        val rbSingle = view.findViewById<RadioButton>(R.id.rbSingle)
        val labelSub = view.findViewById<View>(R.id.labelSub)
        val labelPrice = view.findViewById<View>(R.id.labelPrice)

        val trainers = store.trainers().sortedBy { it.name.lowercase() }
        val me = store.me()

        val clientNames = clients.map { it.name.ifBlank { "Без имени" } }
        spClient.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, clientNames)
        spTrainer.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            trainers.map { if (it.id == me?.id) "${it.name} (я)" else it.name }
        )

        var selectedClientId = existing?.clientId ?: clients.first().id
        var selectedTrainerId = existing?.trainerId ?: (me?.id ?: trainers.firstOrNull()?.id ?: "")
        var selectedSubId: String? = existing?.subscriptionId

        fun loadSubs() {
            val list = store.subscriptions()
                .filter { !it.deleted && it.clientId == selectedClientId && it.left > 0 }
            val names = if (list.isEmpty()) listOf("Нет активного абонемента") else
                list.map { "осталось ${it.left} из ${it.total} · ${Dates.toHuman(it.startDate)}" }
            spSub.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
            selectedSubId = when {
                list.isEmpty() -> null
                existing?.subscriptionId != null && list.any { it.id == existing.subscriptionId } -> existing.subscriptionId
                else -> list.first().id
            }
            if (list.isEmpty()) {
                rbSingle.isChecked = true
            }
        }

        loadSubs()

        val idxClient = clients.indexOfFirst { it.id == selectedClientId }
        if (idxClient >= 0) spClient.setSelection(idxClient) else selectedClientId = clients.first().id
        val idxTrainer = trainers.indexOfFirst { it.id == selectedTrainerId }
        if (idxTrainer >= 0) spTrainer.setSelection(idxTrainer)

        spClient.onItemSelectedListener = OnItemSelected { position ->
                selectedClientId = clients[position].id
                loadSubs()
            }
        spTrainer.onItemSelectedListener = OnItemSelected { position ->
                selectedTrainerId = trainers[position].id
            }
        spSub.onItemSelectedListener = OnItemSelected { position ->
                val list = store.subscriptions().filter { !it.deleted && it.clientId == selectedClientId && it.left > 0 }
                selectedSubId = list.getOrNull(position)?.id
            }

        etDate.text = Dates.toHuman(existing?.date ?: selectedDate)
        etDate.setOnClickListener { pickDate(etDate) }
        etTime.text = existing?.time ?: "10:00"
        etTime.setOnClickListener { pickTime(etTime) }
        etDuration.setText((existing?.durationMin ?: 60).toString())
        etPrice.setText((existing?.price ?: 2500).toString())
        etNotes.setText(existing?.notes ?: "")

        fun applyKindVisibility() {
            val bySub = rbSub.isChecked
            labelSub.visibility = if (bySub) View.VISIBLE else View.GONE
            spSub.visibility = if (bySub) View.VISIBLE else View.GONE
            labelPrice.visibility = if (bySub) View.GONE else View.VISIBLE
            etPrice.visibility = if (bySub) View.GONE else View.VISIBLE
        }
        if (existing != null) {
            if (existing.kind == Kind.SUBSCRIPTION) rbSub.isChecked = true else rbSingle.isChecked = true
        }
        rgKind.setOnCheckedChangeListener { _, _ -> applyKindVisibility() }
        applyKindVisibility()

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Новая запись" else "Запись")
            .setView(view)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Удалить", null)

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = clients.firstOrNull { it.id == selectedClientId }?.name ?: "Клиент"
                val trainerName = trainers.firstOrNull { it.id == selectedTrainerId }?.name ?: ""
                val dateIso = Dates.toIso(etDate.text.toString())
                val time = etTime.text.toString()
                if (dateIso.isBlank() || time.isBlank()) {
                    Toast.makeText(this, "Укажите дату и время", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val isSub = rbSub.isChecked
                val lesson = Lesson(
                    id = existing?.id ?: ru.konnykrug.trainer.data.newId(),
                    date = dateIso,
                    time = time,
                    durationMin = etDuration.text.toString().toIntOrNull() ?: 60,
                    clientId = selectedClientId,
                    clientName = name,
                    trainerId = selectedTrainerId,
                    trainerName = trainerName,
                    kind = if (isSub) Kind.SUBSCRIPTION else Kind.SINGLE,
                    subscriptionId = if (isSub) selectedSubId else null,
                    price = if (isSub) 0 else (etPrice.text.toString().toIntOrNull() ?: 0),
                    notes = etNotes.text.toString().trim(),
                    status = existing?.status ?: Status.PLANNED,
                    updatedAt = System.currentTimeMillis(),
                    dirty = true
                )
                store.upsertLesson(lesson)
                selectedDate = dateIso
                refreshAll()
                doSync(manual = false)
                dialog.dismiss()
            }

            val neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            if (existing == null) {
                neutral.visibility = View.GONE
            } else {
                neutral.setTextColor(Color.parseColor("#C1443B"))
                neutral.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Удалить запись?")
                        .setMessage("Запись исчезнет у всех тренеров после синхронизации.")
                        .setPositiveButton("Удалить") { _, _ ->
                            val list = store.lessons()
                            val idx = list.indexOfFirst { it.id == existing.id }
                            if (idx >= 0) {
                                list[idx] = list[idx].copy(
                                    deleted = true,
                                    dirty = true,
                                    updatedAt = System.currentTimeMillis()
                                )
                                store.saveLessons(list)
                            }
                            refreshAll()
                            doSync(manual = false)
                            dialog.dismiss()
                        }
                        .setNegativeButton("Отмена", null)
                        .show()
                }
            }
        }
        activeDialog(dialog)
        dialog.show()
    }

    /** Запоминаем диалог до показа, чтобы автотесты могли его найти. */
    private fun activeDialog(d: AlertDialog) {
        lastDialog = d
    }

    private fun toggleDone(lesson: Lesson) {
        val list = store.lessons()
        val idx = list.indexOfFirst { it.id == lesson.id }
        if (idx < 0) return
        val current = list[idx]
        val nextStatus = when (current.status) {
            Status.PLANNED -> Status.DONE
            Status.DONE -> Status.PLANNED
            else -> Status.PLANNED
        }

        // абонемент: списываем/возвращаем занятие при изменении статуса
        current.subscriptionId?.let { subId ->
            val subs = store.subscriptions()
            val sIdx = subs.indexOfFirst { it.id == subId }
            if (sIdx >= 0) {
                val s = subs[sIdx]
                val delta = when {
                    current.status != Status.DONE && nextStatus == Status.DONE -> 1
                    current.status == Status.DONE && nextStatus != Status.DONE -> -1
                    else -> 0
                }
                if (delta != 0) {
                    subs[sIdx] = s.copy(
                        used = (s.used + delta).coerceIn(0, s.total),
                        updatedAt = System.currentTimeMillis(),
                        dirty = true
                    )
                    store.saveSubscriptions(subs)
                }
            }
        }

        list[idx] = current.copy(status = nextStatus, dirty = true, updatedAt = System.currentTimeMillis())
        store.saveLessons(list)
        refreshAll()
        doSync(manual = false)
    }

    // ---------------- диалог: клиент ----------------

    private fun openClientDialog(existing: Client?) {
        val view = layoutInflater.inflate(R.layout.dialog_client, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etPhone = view.findViewById<EditText>(R.id.etPhone)
        val etNotes = view.findViewById<EditText>(R.id.etNotes)

        etName.setText(existing?.name ?: "")
        etPhone.setText(existing?.phone ?: "")
        etNotes.setText(existing?.notes ?: "")

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Новый клиент" else "Клиент")
            .setView(view)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Удалить", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Введите имя", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val client = Client(
                    id = existing?.id ?: ru.konnykrug.trainer.data.newId(),
                    name = name,
                    phone = etPhone.text.toString().trim(),
                    notes = etNotes.text.toString().trim(),
                    updatedAt = System.currentTimeMillis(),
                    deleted = false,
                    dirty = true
                )
                store.upsertClient(client)
                refreshAll()
                doSync(manual = false)
                dialog.dismiss()
            }
            val neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            if (existing == null) {
                neutral.visibility = View.GONE
            } else {
                neutral.setTextColor(Color.parseColor("#C1443B"))
                neutral.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Удалить клиента?")
                        .setPositiveButton("Удалить") { _, _ ->
                            val list = store.clients()
                            val idx = list.indexOfFirst { it.id == existing.id }
                            if (idx >= 0) {
                                list[idx] = list[idx].copy(
                                    deleted = true,
                                    dirty = true,
                                    updatedAt = System.currentTimeMillis()
                                )
                                store.saveClients(list)
                            }
                            refreshAll()
                            doSync(manual = false)
                            dialog.dismiss()
                        }
                        .setNegativeButton("Отмена", null)
                        .show()
                }
            }
        }
        activeDialog(dialog)
        dialog.show()
    }

    // ---------------- диалог: абонемент ----------------

    private fun openSubDialog(existing: Subscription?) {
        val clients = store.clients().filter { !it.deleted }.sortedBy { it.name.lowercase() }
        if (clients.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Сначала добавьте клиента")
                .setMessage("Абонемент оформляется на клиента.")
                .setPositiveButton("ОК", null)
                .show()
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_sub, null)
        val spClient = view.findViewById<Spinner>(R.id.spClient)
        val etTotal = view.findViewById<EditText>(R.id.etTotal)
        val etUsed = view.findViewById<EditText>(R.id.etUsed)
        val etPrice = view.findViewById<EditText>(R.id.etPrice)
        val etStart = view.findViewById<TextView>(R.id.etStart)
        val etNotes = view.findViewById<EditText>(R.id.etNotes)

        spClient.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            clients.map { it.name.ifBlank { "Без имени" } }
        )
        var selectedClientId = existing?.clientId ?: clients.first().id
        val idx = clients.indexOfFirst { it.id == selectedClientId }
        if (idx >= 0) spClient.setSelection(idx) else selectedClientId = clients.first().id
        spClient.onItemSelectedListener = OnItemSelected { position ->
                selectedClientId = clients[position].id
            }

        etTotal.setText((existing?.total ?: 10).toString())
        etUsed.setText((existing?.used ?: 0).toString())
        etPrice.setText((existing?.price ?: 12000).toString())
        etStart.text = Dates.toHuman(existing?.startDate ?: Dates.today())
        etStart.setOnClickListener { pickDate(etStart) }
        etNotes.setText(existing?.notes ?: "")

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Новый абонемент" else "Абонемент")
            .setView(view)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Удалить", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val total = etTotal.text.toString().toIntOrNull() ?: 10
                val used = (etUsed.text.toString().toIntOrNull() ?: 0).coerceIn(0, total)
                val sub = Subscription(
                    id = existing?.id ?: ru.konnykrug.trainer.data.newId(),
                    clientId = selectedClientId,
                    total = total,
                    used = used,
                    price = etPrice.text.toString().toIntOrNull() ?: 0,
                    startDate = Dates.toIso(etStart.text.toString()),
                    notes = etNotes.text.toString().trim(),
                    updatedAt = System.currentTimeMillis(),
                    deleted = false,
                    dirty = true
                )
                store.upsertSubscription(sub)
                refreshAll()
                doSync(manual = false)
                dialog.dismiss()
            }
            val neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            if (existing == null) {
                neutral.visibility = View.GONE
            } else {
                neutral.setTextColor(Color.parseColor("#C1443B"))
                neutral.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Удалить абонемент?")
                        .setPositiveButton("Удалить") { _, _ ->
                            val list = store.subscriptions()
                            val i = list.indexOfFirst { it.id == existing.id }
                            if (i >= 0) {
                                list[i] = list[i].copy(
                                    deleted = true,
                                    dirty = true,
                                    updatedAt = System.currentTimeMillis()
                                )
                                store.saveSubscriptions(list)
                            }
                            refreshAll()
                            doSync(manual = false)
                            dialog.dismiss()
                        }
                        .setNegativeButton("Отмена", null)
                        .show()
                }
            }
        }
        activeDialog(dialog)
        dialog.show()
    }

    // ---------------- профиль тренера ----------------

    private fun openMeDialog(firstRun: Boolean = false) {
        val view = layoutInflater.inflate(R.layout.dialog_me, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etPhone = view.findViewById<EditText>(R.id.etPhone)
        val etServer = view.findViewById<EditText>(R.id.etServer)
        val btnTest = view.findViewById<Button>(R.id.btnTest)
        val testResult = view.findViewById<TextView>(R.id.testResult)

        val me = store.me()
        etName.setText(me?.name ?: "")
        etPhone.setText(me?.phone ?: "")
        etServer.setText(store.serverUrl)

        if (firstRun) {
            testResult.text = "Первый запуск: укажите имя и адрес общего сервера клуба."
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Профиль тренера")
            .setView(view)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton(if (firstRun) null else "Отмена", null)

        val dialog = builder.create()
        dialog.setCancelable(!firstRun)
        dialog.setOnShowListener {
            btnTest.setOnClickListener {
                store.serverUrl = etServer.text.toString().trim()
                testResult.text = "Проверяем…"
                lifecycleScope.launch {
                    val ok = sync.ping()
                    testResult.text = if (ok) "Связь есть ✅" else "Сервер недоступен ❌ Проверьте адрес и что сервер запущен"
                }
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Введите имя", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                store.serverUrl = etServer.text.toString().trim()
                val existing = store.me()
                val trainer = Trainer(
                    id = existing?.id ?: ru.konnykrug.trainer.data.newId(),
                    name = name,
                    phone = etPhone.text.toString().trim(),
                    color = existing?.color ?: "#40916C",
                    isMe = true,
                    updatedAt = System.currentTimeMillis()
                )
                store.myTrainerId = trainer.id
                store.upsertTrainer(trainer)
                refreshAll()
                doSync(manual = true)
                dialog.dismiss()
            }
        }
        activeDialog(dialog)
        dialog.show()
    }

    // ---------------- дата и время ----------------

    private fun pickDate(target: TextView) {
        val current = Dates.parse(Dates.toIso(target.text.toString())) ?: LocalDate.now()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                target.text = "%02d.%02d.%04d".format(d, m + 1, y)
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        ).show()
    }

    private fun pickTime(target: TextView) {
        val parts = target.text.toString().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 10
        val min = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(this, { _, hh, mm -> target.text = "%02d:%02d".format(hh, mm) }, h, min, true).show()
    }
}

/**
 * Упрощённый слушатель выбора в Spinner: `spinner.onItemSelectedListener = OnItemSelected { pos -> ... }`.
 * Сделан функцией, а не объектом-наследником, чтобы не тянуть лишние классы.
 */
fun OnItemSelected(block: (Int) -> Unit): android.widget.AdapterView.OnItemSelectedListener =
    object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) =
            block(position)

        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }
