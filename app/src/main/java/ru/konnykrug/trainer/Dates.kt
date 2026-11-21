package ru.konnykrug.trainer

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Разбор и вывод дат в формате приложения: ISO (2026-11-21) <-> «21.11.2026». */
object Dates {

    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val human: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("ru"))
    private val long: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
    private val dowShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EE", Locale("ru"))

    fun today(): String = LocalDate.now().toString()

    fun parse(s: String): LocalDate? = try {
        LocalDate.parse(s, iso)
    } catch (e: Exception) {
        null
    }

    fun toHuman(isoDate: String): String = parse(isoDate)?.format(human) ?: isoDate

    fun toIso(humanDate: String): String {
        val parts = humanDate.split(".")
        return if (parts.size == 3) {
            "%s-%s-%s".format(parts[2], parts[1].padStart(2, '0'), parts[0].padStart(2, '0'))
        } else {
            today()
        }
    }

    fun longLabel(isoDate: String): String {
        val d = parse(isoDate) ?: return isoDate
        val base = d.format(long)
        val t = LocalDate.now()
        return when (d) {
            t -> "Сегодня, $base"
            t.plusDays(1) -> "Завтра, $base"
            t.minusDays(1) -> "Вчера, $base"
            else -> base
        }
    }

    fun dow(isoDate: String): String = parse(isoDate)?.format(dowShort)?.capitalize(Locale("ru")) ?: ""

    fun dayNumber(isoDate: String): String = parse(isoDate)?.dayOfMonth?.toString() ?: ""

    fun week(): List<String> {
        val start = LocalDate.now().minusDays(3)
        return (0L until 14L).map { start.plusDays(it).toString() }
    }

    fun shift(isoDate: String, days: Long): String {
        val d = parse(isoDate) ?: LocalDate.now()
        return d.plusDays(days).toString()
    }
}

fun Long.toMoney(): String {
    if (this <= 0) return ""
    val s = toString()
    val sb = StringBuilder()
    s.reversed().forEachIndexed { i, c ->
        if (i > 0 && i % 3 == 0) sb.append(' ')
        sb.append(c)
    }
    return sb.reverse().toString() + " ₽"
}
