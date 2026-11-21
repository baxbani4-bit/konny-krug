package ru.konnykrug.trainer

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Looper
import androidx.appcompat.app.AlertDialog

/**
 * Проверка, что приложение реально открывается: разметка инфлейтится,
 * экраны переключаются, записи попадают в список. Без эмулятора.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UiTest {

    /** Даём Robolectric прогнать отложенные задачи главного потока (открытие диалогов, перерисовку). */
    private fun idle() = org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

    private fun prefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("konny_krug", Context.MODE_PRIVATE)

    private fun seed() {
        prefs().edit()
            .putString("my_trainer_id", "t1")
            .putString("server_url", "http://127.0.0.1:1") // заведомо недоступен: приложение должно жить офлайн
            .putString(
                "trainers",
                """[{"id":"t1","name":"Ирина","color":"#40916C","updatedAt":1},
                   {"id":"t2","name":"Павел","color":"#8B5E34","updatedAt":1}]"""
            )
            .putString("clients", """[{"id":"c1","name":"Анна Петрова","phone":"+7 900 111-22-33","updatedAt":1}]""")
            .putString("subscriptions", """[{"id":"s1","clientId":"c1","total":10,"used":3,"price":12000,"startDate":"2026-11-01","updatedAt":1}]""")
            .putString(
                "lessons",
                """[{"id":"l1","date":"${Dates.today()}","time":"10:00","clientId":"c1","clientName":"Анна Петрова",
                     "trainerId":"t1","trainerName":"Ирина","kind":"SUBSCRIPTION","subscriptionId":"s1",
                     "price":0,"status":"PLANNED","updatedAt":1},
                    {"id":"l2","date":"${Dates.today()}","time":"12:00","clientId":"c1","clientName":"Анна Петрова",
                     "trainerId":"t2","trainerName":"Павел","kind":"SINGLE","price":2500,
                     "status":"PLANNED","updatedAt":1}]"""
            )
            .apply()
    }

    @Test
    fun activity_starts_and_shows_lessons_of_both_trainers() {
        seed()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()
        assertNotNull(activity.findViewById<View>(R.id.bottomNav))
        assertEquals("Тренер: Ирина", activity.findViewById<TextView>(R.id.subtitle).text.toString())

        val list = activity.findViewById<RecyclerView>(R.id.listLessons)
        assertEquals(2, list.adapter?.itemCount)

        // в расписании видно запись коллеги
        val holder = list.adapter!!.createViewHolder(list, 0)
        list.adapter!!.bindViewHolder(holder, 1)
        assertEquals("Тренер: Павел", holder.itemView.findViewById<TextView>(R.id.trainerName).text.toString())
        assertEquals("2 500 ₽", holder.itemView.findViewById<TextView>(R.id.priceChip).text.toString())
    }

    @Test
    fun tabs_switch_screens() {
        seed()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<View>(R.id.tab_clients).performClick()
        idle()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.screenClients).visibility)
        assertEquals(1, activity.findViewById<RecyclerView>(R.id.listClients).adapter?.itemCount)

        activity.findViewById<View>(R.id.tab_subs).performClick()
        idle()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.screenSubs).visibility)
        assertEquals(1, activity.findViewById<RecyclerView>(R.id.listSubs).adapter?.itemCount)
    }

    @Test
    fun day_navigation_moves_date() {
        seed()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()
        val label = activity.findViewById<TextView>(R.id.dateLabel)
        assertTrue(label.text.toString().startsWith("Сегодня"))

        activity.findViewById<View>(R.id.btnNext).performClick()
        idle()
        assertTrue(label.text.toString().startsWith("Завтра"))

        activity.findViewById<View>(R.id.btnToday).performClick()
        idle()
        assertTrue(label.text.toString().startsWith("Сегодня"))
    }

    @Test
    fun first_run_asks_for_trainer_profile() {
        prefs().edit().clear().apply()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()
        val dialog = activity.lastDialog
        assertTrue("диалог профиля должен открыться при первом запуске", dialog != null && dialog.isShowing)

        // заполняем имя и сохраняем — профиль запоминается
        dialog!!.findViewById<android.widget.EditText>(R.id.etName)!!.setText("Ирина")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        val store = ru.konnykrug.trainer.data.Store(activity)
        assertTrue(store.isOnboarded)
        assertEquals("Ирина", store.me()?.name)
    }

    @Test
    fun adding_lesson_from_dialog_appears_in_schedule() {
        seed()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<View>(R.id.fab).performClick()
        idle()
        val dialog = activity.lastDialog
        assertTrue(dialog != null && dialog.isShowing)

        dialog!!.findViewById<android.widget.EditText>(R.id.etDuration)!!.setText("90")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()

        val store = ru.konnykrug.trainer.data.Store(activity)
        val created = store.lessons().filter { it.date == Dates.today() }
        assertEquals(3, created.size)
        assertTrue(created.any { it.durationMin == 90 })
        assertEquals(3, activity.findViewById<RecyclerView>(R.id.listLessons).adapter?.itemCount)
    }
}
