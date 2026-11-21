package ru.konnykrug.trainer.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Тип записи: по абонементу или разовое занятие. */
object Kind {
    const val SUBSCRIPTION = "SUBSCRIPTION"
    const val SINGLE = "SINGLE"
}

/** Статус занятия. */
object Status {
    const val PLANNED = "PLANNED"
    const val DONE = "DONE"
    const val CANCELLED = "CANCELLED"
}

fun newId(): String = UUID.randomUUID().toString()

/**
 * Тренер. Одна запись = один телефон. Записи всех тренеров синхронизируются,
 * поэтому каждый видит расписание коллеги.
 */
data class Trainer(
    val id: String = newId(),
    var name: String = "",
    var phone: String = "",
    var color: String = "#40916C",
    /** true только на устройстве самого тренера — используется для иконки «это я». */
    var isMe: Boolean = false,
    var updatedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("phone", phone)
        .put("color", color)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject) = Trainer(
            id = o.getString("id"),
            name = o.optString("name"),
            phone = o.optString("phone"),
            color = o.optString("color", "#40916C"),
            updatedAt = o.optLong("updatedAt")
        )
    }
}

/** Клиент конного клуба. */
data class Client(
    val id: String = newId(),
    var name: String = "",
    var phone: String = "",
    var notes: String = "",
    var updatedAt: Long = 0L,
    var deleted: Boolean = false,
    var dirty: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("phone", phone)
        .put("notes", notes)
        .put("updatedAt", updatedAt)
        .put("deleted", deleted)

    fun localJson(): JSONObject = toJson().put("dirty", dirty)

    companion object {
        fun fromJson(o: JSONObject) = Client(
            id = o.getString("id"),
            name = o.optString("name"),
            phone = o.optString("phone"),
            notes = o.optString("notes"),
            updatedAt = o.optLong("updatedAt"),
            deleted = o.optBoolean("deleted"),
            dirty = o.optBoolean("dirty", false)
        )
    }
}

/** Абонемент: пакет занятий клиента (например, 10 прогулок). */
data class Subscription(
    val id: String = newId(),
    var clientId: String = "",
    var total: Int = 10,
    var used: Int = 0,
    var price: Int = 0,
    /** Дата начала в формате ISO: 2026-11-21 */
    var startDate: String = "",
    var notes: String = "",
    var updatedAt: Long = 0L,
    var deleted: Boolean = false,
    var dirty: Boolean = true
) {
    val left: Int get() = (total - used).coerceAtLeast(0)
    val isActive: Boolean get() = !deleted && left > 0

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("clientId", clientId)
        .put("total", total)
        .put("used", used)
        .put("price", price)
        .put("startDate", startDate)
        .put("notes", notes)
        .put("updatedAt", updatedAt)
        .put("deleted", deleted)

    fun localJson(): JSONObject = toJson().put("dirty", dirty)

    companion object {
        fun fromJson(o: JSONObject) = Subscription(
            id = o.getString("id"),
            clientId = o.optString("clientId"),
            total = o.optInt("total", 10),
            used = o.optInt("used", 0),
            price = o.optInt("price", 0),
            startDate = o.optString("startDate"),
            notes = o.optString("notes"),
            updatedAt = o.optLong("updatedAt"),
            deleted = o.optBoolean("deleted"),
            dirty = o.optBoolean("dirty", false)
        )
    }
}

/** Запись клиента на прогулку. */
data class Lesson(
    val id: String = newId(),
    /** ISO-дата: 2026-11-21 */
    var date: String = "",
    /** Время начала: 14:30 */
    var time: String = "10:00",
    var durationMin: Int = 60,
    var clientId: String = "",
    var clientName: String = "",
    var trainerId: String = "",
    var trainerName: String = "",
    var kind: String = Kind.SINGLE,
    var subscriptionId: String? = null,
    var price: Int = 0,
    var notes: String = "",
    var status: String = Status.PLANNED,
    var updatedAt: Long = 0L,
    var deleted: Boolean = false,
    var dirty: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("date", date)
        .put("time", time)
        .put("durationMin", durationMin)
        .put("clientId", clientId)
        .put("clientName", clientName)
        .put("trainerId", trainerId)
        .put("trainerName", trainerName)
        .put("kind", kind)
        .put("subscriptionId", subscriptionId ?: JSONObject.NULL)
        .put("price", price)
        .put("notes", notes)
        .put("status", status)
        .put("updatedAt", updatedAt)
        .put("deleted", deleted)

    fun localJson(): JSONObject = toJson().put("dirty", dirty)

    companion object {
        fun fromJson(o: JSONObject) = Lesson(
            id = o.getString("id"),
            date = o.optString("date"),
            time = o.optString("time", "10:00"),
            durationMin = o.optInt("durationMin", 60),
            clientId = o.optString("clientId"),
            clientName = o.optString("clientName"),
            trainerId = o.optString("trainerId"),
            trainerName = o.optString("trainerName"),
            kind = o.optString("kind", Kind.SINGLE),
            subscriptionId = if (o.isNull("subscriptionId")) null else o.optString("subscriptionId"),
            price = o.optInt("price"),
            notes = o.optString("notes"),
            status = o.optString("status", Status.PLANNED),
            updatedAt = o.optLong("updatedAt"),
            deleted = o.optBoolean("deleted"),
            dirty = o.optBoolean("dirty", false)
        )
    }
}

fun JSONArray?.toJsonObjects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
