package ru.konnykrug.trainer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class SyncResult {
    data class Ok(val pulled: Int, val pushed: Int, val at: Long) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

/**
 * Обмен данными с общим сервером клуба.
 *
 * Схема простая и устойчивая к офлайн-работе:
 *  1) push — отправляем всё, что изменено локально (dirty);
 *  2) pull — забираем с сервера всё, что изменилось после нашей прошлой синхронизации;
 *  3) слияние по времени изменения (last-write-wins по updatedAt).
 *
 * Записи всех тренеров лежат в одном «круге» (room), поэтому каждый видит расписание коллеги.
 */
class Sync(private val store: Store) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun sync(room: String = "club"): SyncResult = withContext(Dispatchers.IO) {
        try {
            val me = store.me() ?: return@withContext SyncResult.Error("Сначала укажите профиль тренера")

            val clients = store.clients()
            val subs = store.subscriptions()
            val lessons = store.lessons()
            val trainers = store.trainers()

            val dirtyClients = clients.filter { it.dirty }
            val dirtySubs = subs.filter { it.dirty }
            val dirtyLessons = lessons.filter { it.dirty }
            val dirtyTrainers = trainers.filter { it.id == me.id }

            val pushed = push(room, me.id, dirtyTrainers, dirtyClients, dirtySubs, dirtyLessons)
            if (pushed != null) return@withContext SyncResult.Error(pushed)

            // после успешного push снимаем флаг «грязный»
            store.saveClients(clients.map { if (it.dirty) it.copy(dirty = false) else it })
            store.saveSubscriptions(subs.map { if (it.dirty) it.copy(dirty = false) else it })
            store.saveLessons(lessons.map { if (it.dirty) it.copy(dirty = false) else it })

            val since = store.lastSync
            val pullResp = pull(room, since) ?: return@withContext SyncResult.Error("Сервер не ответил")

            val serverTime = pullResp.optLong("serverTime", System.currentTimeMillis())

            val remoteTrainers = pullResp.optJSONArray("trainers").toJsonObjects().map { Trainer.fromJson(it) }
            val remoteClients = pullResp.optJSONArray("clients").toJsonObjects().map { Client.fromJson(it) }
            val remoteSubs = pullResp.optJSONArray("subscriptions").toJsonObjects().map { Subscription.fromJson(it) }
            val remoteLessons = pullResp.optJSONArray("lessons").toJsonObjects().map { Lesson.fromJson(it) }

            val mergedCount = applyRemote(remoteTrainers, remoteClients, remoteSubs, remoteLessons)

            store.lastSync = serverTime
            SyncResult.Ok(
                pulled = mergedCount,
                pushed = dirtyClients.size + dirtySubs.size + dirtyLessons.size,
                at = serverTime
            )
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Не удалось синхронизироваться")
        }
    }

    /** Возвращает null при успехе или текст ошибки. */
    private fun push(
        room: String,
        trainerId: String,
        trainers: List<Trainer>,
        clients: List<Client>,
        subs: List<Subscription>,
        lessons: List<Lesson>
    ): String? {
        val body = JSONObject()
            .put("room", room)
            .put("trainerId", trainerId)
            .put("trainers", JSONArray().apply { trainers.forEach { put(it.toJson()) } })
            .put("clients", JSONArray().apply { clients.forEach { put(it.toJson().put("dirty", false)) } })
            .put("subscriptions", JSONArray().apply { subs.forEach { put(it.toJson().put("dirty", false)) } })
            .put("lessons", JSONArray().apply { lessons.forEach { put(it.toJson().put("dirty", false)) } })

        val url = buildUrl("/api/push")
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(json))
            .build()
        return http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) "Сервер вернул ${resp.code}" else null
        }
    }

    private fun pull(room: String, since: Long): JSONObject? {
        val url = buildUrl("/api/pull?room=$room&since=$since")
        val req = Request.Builder().url(url).get().build()
        return http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else JSONObject(resp.body?.string() ?: "{}")
        }
    }

    private fun applyRemote(
        remoteTrainers: List<Trainer>,
        remoteClients: List<Client>,
        remoteSubs: List<Subscription>,
        remoteLessons: List<Lesson>
    ): Int {
        var changed = 0

        // тренеры
        val trainers = store.trainers()
        remoteTrainers.forEach { r ->
            val idx = trainers.indexOfFirst { it.id == r.id }
            if (idx < 0) {
                trainers.add(r)
                changed++
            } else if (r.updatedAt >= trainers[idx].updatedAt) {
                val isMe = trainers[idx].isMe
                trainers[idx] = r.copy(isMe = isMe)
                changed++
            }
        }
        store.saveTrainers(trainers)

        // клиенты
        val clients = store.clients()
        remoteClients.forEach { r ->
            val idx = clients.indexOfFirst { it.id == r.id }
            if (idx < 0) {
                clients.add(r)
                changed++
            } else if (r.updatedAt > clients[idx].updatedAt) {
                clients[idx] = r.copy(dirty = false)
                changed++
            }
        }
        store.saveClients(clients)

        // абонементы
        val subs = store.subscriptions()
        remoteSubs.forEach { r ->
            val idx = subs.indexOfFirst { it.id == r.id }
            if (idx < 0) {
                subs.add(r)
                changed++
            } else if (r.updatedAt > subs[idx].updatedAt) {
                subs[idx] = r.copy(dirty = false)
                changed++
            }
        }
        store.saveSubscriptions(subs)

        // записи
        val lessons = store.lessons()
        remoteLessons.forEach { r ->
            val idx = lessons.indexOfFirst { it.id == r.id }
            if (idx < 0) {
                lessons.add(r)
                changed++
            } else if (r.updatedAt > lessons[idx].updatedAt) {
                lessons[idx] = r.copy(dirty = false)
                changed++
            }
        }
        store.saveLessons(lessons)

        return changed
    }

    private fun buildUrl(path: String): String {
        val base = store.serverUrl.trim().trimEnd('/')
        return base + path
    }

    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(buildUrl("/api/health")).get().build()
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
