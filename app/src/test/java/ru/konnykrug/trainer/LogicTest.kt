package ru.konnykrug.trainer

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.konnykrug.trainer.data.Client
import ru.konnykrug.trainer.data.Kind
import ru.konnykrug.trainer.data.Lesson
import ru.konnykrug.trainer.data.Status
import ru.konnykrug.trainer.data.Subscription
import ru.konnykrug.trainer.data.Trainer

/** Проверки логики, не требующие Android-устройства. */
class LogicTest {

    @Test
    fun lesson_roundTripJson() {
        val l = Lesson(
            date = "2026-11-21",
            time = "14:30",
            clientId = "c1",
            clientName = "Анна",
            trainerId = "t1",
            trainerName = "Ирина",
            kind = Kind.SUBSCRIPTION,
            subscriptionId = "s1",
            status = Status.PLANNED,
            updatedAt = 42L
        )
        val restored = Lesson.fromJson(l.toJson())
        assertEquals(l.id, restored.id)
        assertEquals("2026-11-21", restored.date)
        assertEquals("14:30", restored.time)
        assertEquals(Kind.SUBSCRIPTION, restored.kind)
        assertEquals("s1", restored.subscriptionId)
        assertEquals(42L, restored.updatedAt)
    }

    @Test
    fun single_lesson_keeps_null_subscription() {
        val l = Lesson(kind = Kind.SINGLE, price = 2500)
        assertEquals("null", l.toJson().get("subscriptionId").toString())
        assertEquals(null, Lesson.fromJson(l.toJson()).subscriptionId)
    }

    @Test
    fun subscription_left_never_negative() {
        val s = Subscription(total = 10, used = 12)
        assertEquals(0, s.left)
        assertEquals(false, s.isActive)
        assertEquals(0, Subscription(total = 10, used = 3).copy(total = 0, used = 0).left)
        assertEquals(7, Subscription(total = 10, used = 3).left)
    }

    @Test
    fun deleted_records_are_not_active() {
        assertEquals(false, Subscription(total = 10, used = 0, deleted = true).isActive)
        assertEquals("#40916C", Trainer(name = "Ира").color)
    }

    @Test
    fun dates_convert_both_ways() {
        assertEquals("21.11.2026", Dates.toHuman("2026-11-21"))
        assertEquals("2026-11-21", Dates.toIso("21.11.2026"))
        assertEquals("2026-11-02", Dates.toIso("2.11.2026"))
        assertEquals("2026-11-22", Dates.shift("2026-11-21", 1))
        assertEquals("2026-11-20", Dates.shift("2026-11-21", -1))
    }

    @Test
    fun money_formatting() {
        assertEquals("", 0L.toMoney())
        assertEquals("900 ₽", 900L.toMoney())
        assertEquals("2 500 ₽", 2500L.toMoney())
        assertEquals("12 000 ₽", 12000L.toMoney())
    }

    @Test
    fun client_survives_json() {
        val c = Client(name = "Анна", phone = "+7 900", notes = "боится рысить", updatedAt = 7L)
        val r = Client.fromJson(c.localJson())
        assertEquals("Анна", r.name)
        assertEquals(true, r.dirty)
        assertEquals(7L, r.updatedAt)
    }
}
