package two

import delayOrFail
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

typealias ResponseString = String

val client = HttpClient(OkHttp) {
    install(Logging)
}

suspend fun requestKtorClient(url: URL): ResponseString {
    println("request $url")
    val response = client.request(url)
    return response.bodyAsText()
}

val task1 = suspend { requestKtorClient(URL("http://localhost/delay/1")) }
val task2 = suspend { requestKtorClient(URL("http://localhost/delay/3")) }
val task3 = suspend { requestKtorClient(URL("http://localhost/delay/5")) }
val task4 = suspend { requestKtorClient(URL("http://localhost/delay/7")) }
val task5 = suspend { requestKtorClient(URL("http://localhost/delay/9")) }

val delayBy = 800.milliseconds // delay next request

// coroutineScope() uses multi-threaded Dispatchers.Default - could use WithContext(Dispatchers.IO)
@OptIn(ExperimentalStdlibApi::class)
suspend fun main(): Unit = coroutineScope {

    val tasks = listOf(
        task1,
        task2,
        task3,
        task4,
        task5
    ).shuffled()

    val t = measureTime {
        val winner = happyEyeBalls(tasks, 800.milliseconds).first()
        println(winner)
    }

    println(t.inWholeSeconds)

    client.close() //ktor client
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun happyEyeBalls(tasks: List<suspend() -> ResponseString>, delayedBy: Duration): Flow<ResponseString> {
    val failedTask = Channel<Unit>(1)
    return when (tasks.size) {
        0 -> error("no tasks")
        1 -> tasks.first().asFlow().catch { failedTask.trySend(Unit) }
        else -> {
            merge(
                tasks.first().asFlow().catch { failedTask.trySend(Unit) },
                flow { emit(delayOrFail(failedTask, delayedBy)) }.flatMapMerge {
                    happyEyeBalls(tasks.drop(1), delayedBy) // delayOrFail flatmap with recursive tasks
                }
            )
        }
    }
}
