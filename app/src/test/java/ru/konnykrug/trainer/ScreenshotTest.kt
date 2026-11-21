package ru.konnykrug.trainer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Снимает скриншоты экранов в PNG — чтобы проверить внешний вид без эмулятора. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    private fun idle() = org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun capture() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val today = Dates.today()
        val tomorrow = Dates.shift(today, 1)
        ctx.getSharedPreferences("konny_krug", Context.MODE_PRIVATE).edit()
            .putString("my_trainer_id", "t1")
            .putString("server_url", "http://192.168.1.10:8080")
            .putLong("last_sync", System.currentTimeMillis())
            .putString("trainers", """[{"id":"t1","name":"Ирина","color":"#2D6A4F","updatedAt":1},
                                       {"id":"t2","name":"Павел","color":"#8B5E34","updatedAt":1}]""")
            .putString("clients", """[{"id":"c1","name":"Анна Петрова","phone":"+7 900 111-22-33","updatedAt":1},
                                      {"id":"c2","name":"Игорь Смирнов","phone":"+7 903 222-33-44","updatedAt":1},
                                      {"id":"c3","name":"Мария Ким","phone":"+7 916 555-77-88","updatedAt":1},
                                      {"id":"c4","name":"Ольга Ли","phone":"+7 985 333-11-22","updatedAt":1}]""")
            .putString("subscriptions", """[{"id":"s1","clientId":"c1","total":10,"used":3,"price":12000,"startDate":"2026-11-01","updatedAt":1},
                                            {"id":"s2","clientId":"c3","total":5,"used":4,"price":7000,"startDate":"2026-11-10","updatedAt":1}]""")
            .putString("lessons", """[
              {"id":"l1","date":"$today","time":"10:00","durationMin":60,"clientId":"c1","clientName":"Анна Петрова",
               "trainerId":"t1","trainerName":"Ирина","kind":"SUBSCRIPTION","subscriptionId":"s1","price":0,
               "notes":"Звёздочка, лесной маршрут","status":"PLANNED","updatedAt":1},
              {"id":"l2","date":"$today","time":"12:00","durationMin":90,"clientId":"c2","clientName":"Игорь Смирнов",
               "trainerId":"t2","trainerName":"Павел","kind":"SINGLE","price":2500,
               "notes":"","status":"DONE","updatedAt":1},
              {"id":"l3","date":"$today","time":"15:30","durationMin":60,"clientId":"c3","clientName":"Мария Ким",
               "trainerId":"t1","trainerName":"Ирина","kind":"SUBSCRIPTION","subscriptionId":"s2","price":0,
               "notes":"Поле, шагом","status":"PLANNED","updatedAt":1},
              {"id":"l4","date":"$tomorrow","time":"11:00","durationMin":60,"clientId":"c4","clientName":"Ольга Ли",
               "trainerId":"t2","trainerName":"Павел","kind":"SINGLE","price":3000,"status":"PLANNED","updatedAt":1}
            ]""")
            .apply()

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()
        val out = File("build/screenshots").apply { mkdirs() }
        shot(activity.window.decorView, File(out, "1-schedule.png"))

        activity.findViewById<View>(R.id.tab_clients).performClick(); idle()
        shot(activity.window.decorView, File(out, "2-clients.png"))

        activity.findViewById<View>(R.id.tab_subs).performClick(); idle()
        shot(activity.window.decorView, File(out, "3-subs.png"))

        // диалог новой записи
        activity.findViewById<View>(R.id.tab_schedule).performClick(); idle()
        activity.findViewById<View>(R.id.fab).performClick(); idle()
        activity.lastDialog?.window?.decorView?.let { shot(it, File(out, "4-new-lesson.png")) }
    }

    private fun shot(view: View, file: File) {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        view.draw(canvas)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SHOT ${file.absolutePath} ${view.width}x${view.height}")
    }
}
