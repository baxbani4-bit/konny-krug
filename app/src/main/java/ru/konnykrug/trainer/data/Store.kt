package ru.konnykrug.trainer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Локальное хранилище на SharedPreferences (JSON).
 * Намеренно простое: без Room и kapt, чтобы проект собирался быстро и без лишних зависимостей.
 * Данные переживают перезапуск приложения, а изменения помечаются dirty=true и уезжают на сервер.
 */
class Store(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("konny_krug", Context.MODE_PRIVATE)

    // ---------- сессия ----------

    var myTrainerId: String?
        get() = prefs.getString("my_trainer_id", null)
        set(v) {
            prefs.edit().putString("my_trainer_id", v).apply()
        }

    var serverUrl: String
        get() = prefs.getString("server_url", null) ?: ru.konnykrug.trainer.BuildConfig.DEFAULT_SERVER
        set(v) {
            prefs.edit().putString("server_url", v).apply()
        }

    var lastSync: Long
        get() = prefs.getLong("last_sync", 0L)
        set(v) {
            prefs.edit().putLong("last_sync", v).apply()
        }

    val isOnboarded: Boolean get() = myTrainerId != null

    // ---------- тренеры ----------

    fun trainers(): MutableList<Trainer> =
        readArray("trainers").toJsonObjects().map { Trainer.fromJson(it) }.toMutableList()

    fun saveTrainers(list: List<Trainer>) = writeArray("trainers", list.map { it.toJson() })

    fun me(): Trainer? {
        val id = myTrainerId ?: return null
        return trainers().firstOrNull { it.id == id }
    }

    fun upsertTrainer(t: Trainer) {
        val list = trainers()
        val idx = list.indexOfFirst { it.id == t.id }
        if (idx >= 0) list[idx] = t else list.add(t)
        saveTrainers(list)
    }

    // ---------- клиенты ----------

    fun clients(): MutableList<Client> =
        readArray("clients").toJsonObjects().map { Client.fromJson(it) }.toMutableList()

    fun saveClients(list: List<Client>) = writeArray("clients", list.map { it.localJson() })

    fun upsertClient(c: Client) {
        val list = clients()
        val idx = list.indexOfFirst { it.id == c.id }
        if (idx >= 0) list[idx] = c else list.add(c)
        saveClients(list)
    }

    // ---------- абонементы ----------

    fun subscriptions(): MutableList<Subscription> =
        readArray("subscriptions").toJsonObjects().map { Subscription.fromJson(it) }.toMutableList()

    fun saveSubscriptions(list: List<Subscription>) =
        writeArray("subscriptions", list.map { it.localJson() })

    fun upsertSubscription(s: Subscription) {
        val list = subscriptions()
        val idx = list.indexOfFirst { it.id == s.id }
        if (idx >= 0) list[idx] = s else list.add(s)
        saveSubscriptions(list)
    }

    // ---------- записи ----------

    fun lessons(): MutableList<Lesson> =
        readArray("lessons").toJsonObjects().map { Lesson.fromJson(it) }.toMutableList()

    fun saveLessons(list: List<Lesson>) = writeArray("lessons", list.map { it.localJson() })

    fun upsertLesson(l: Lesson) {
        val list = lessons()
        val idx = list.indexOfFirst { it.id == l.id }
        if (idx >= 0) list[idx] = l else list.add(l)
        saveLessons(list)
    }

    // ---------- служебное ----------

    fun replaceAll(
        trainers: List<Trainer>,
        clients: List<Client>,
        subscriptions: List<Subscription>,
        lessons: List<Lesson>
    ) {
        saveTrainers(trainers)
        saveClients(clients)
        saveSubscriptions(subscriptions)
        saveLessons(lessons)
    }

    private fun readArray(key: String): JSONArray = try {
        JSONArray(prefs.getString(key, "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    private fun writeArray(key: String, items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
