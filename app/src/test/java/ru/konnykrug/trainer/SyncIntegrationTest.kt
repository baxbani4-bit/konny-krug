package ru.konnykrug.trainer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.konnykrug.trainer.data.Client
import ru.konnykrug.trainer.data.Kind
import ru.konnykrug.trainer.data.Lesson
import ru.konnykrug.trainer.data.Status
import ru.konnykrug.trainer.data.Store
import ru.konnykrug.trainer.data.Subscription
import ru.konnykrug.trainer.data.Sync
import ru.konnykrug.trainer.data.SyncResult
import ru.konnykrug.trainer.data.Trainer
import java.net.HttpURLConnection
import java.net.URL

/**
 * Проверка обмена между двумя «телефонами» через настоящий сервер.
 *
 * Требуется запущенный сервер:  cd server && PORT=8089 node server.js
 * Если сервера нет — тест пропускается (в CI без сервера это нормально).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncIntegrationTest {

    private val server = "http://127.0.0.1:8089"
    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("konny_krug", Context.MODE_PRIVATE).edit().clear().apply()
        assumeTrue("сервер не запущен на $server", serverAlive())
    }

    private fun serverAlive(): Boolean = try {
        val c = URL("$server/api/health").openConnection() as HttpURLConnection
        c.connectTimeout = 700
        c.readTimeout = 700
        val ok = c.responseCode == 200
        c.disconnect()
        ok
    } catch (e: Exception) {
        false
    }

    /** Имитация второго телефона: чистое локальное хранилище с другим тренером. */
    private fun phone(trainerId: String, name: String): Store {
        ctx.getSharedPreferences("konny_krug", Context.MODE_PRIVATE).edit().clear().apply()
        val store = Store(ctx)
        store.serverUrl = server
        store.myTrainerId = trainerId
        store.upsertTrainer(Trainer(id = trainerId, name = name, isMe = true, updatedAt = System.currentTimeMillis()))
        return store
    }

    @Test
    fun records_of_two_trainers_converge() = runBlocking {
        // --- телефон Ирины: записывает клиента на абонемент
        val irina = phone("t-irina", "Ирина")
        val client = Client(id = "c-1", name = "Анна Петрова", phone = "+7 900 111-22-33", updatedAt = System.currentTimeMillis())
        irina.upsertClient(client)
        val sub = Subscription(
            id = "s-1", clientId = "c-1", total = 10, used = 3, price = 12000,
            startDate = Dates.today(), updatedAt = System.currentTimeMillis()
        )
        irina.upsertSubscription(sub)
        val lesson = Lesson(
            id = "l-1", date = Dates.today(), time = "10:00", clientId = "c-1", clientName = client.name,
            trainerId = "t-irina", trainerName = "Ирина", kind = Kind.SUBSCRIPTION, subscriptionId = "s-1",
            status = Status.PLANNED, updatedAt = System.currentTimeMillis()
        )
        irina.upsertLesson(lesson)

        val pushResult = Sync(irina).sync()
        assertTrue("push должен пройти: $pushResult", pushResult is SyncResult.Ok)

        // --- телефон Павла: чистое приложение, только профиль
        val pavel = phone("t-pavel", "Павел")
        assertEquals(0, pavel.lessons().size)

        val pull = Sync(pavel).sync()
        assertTrue("pull должен пройти: $pull", pull is SyncResult.Ok)

        // Павел видит запись коллеги и его клиента/абонемент
        assertEquals(1, pavel.lessons().size)
        val seen = pavel.lessons().first()
        assertEquals("Анна Петрова", seen.clientName)
        assertEquals("t-irina", seen.trainerId)
        assertEquals("Ирина", seen.trainerName)
        assertEquals(1, pavel.clients().size)
        assertEquals(7, pavel.subscriptions().first().left) // остаток абонемента доехал
        assertEquals(2, pavel.trainers().size)              // оба тренера в справочнике

        // --- Павел отмечает занятие выполненным и списывает его с абонемента
        val l = pavel.lessons().first()
        pavel.upsertLesson(l.copy(status = Status.DONE, updatedAt = System.currentTimeMillis() + 1000))
        val s = pavel.subscriptions().first()
        pavel.upsertSubscription(s.copy(used = s.used + 1, updatedAt = System.currentTimeMillis() + 1000))
        assertTrue(Sync(pavel).sync() is SyncResult.Ok)

        // --- Ирина получает обратно списание
        assertTrue(Sync(irina).sync() is SyncResult.Ok)
        assertEquals(Status.DONE, irina.lessons().first().status)
        assertEquals(4, irina.subscriptions().first().used)
        assertEquals(6, irina.subscriptions().first().left)
        assertEquals(2, irina.trainers().size)
    }
}
