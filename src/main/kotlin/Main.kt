import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.future.await

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime
import io.ktor.client.engine.okhttp.*
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers

typealias ResponseString = String

val client = HttpClient(OkHttp)

suspend fun requestKtorClient(url: URL): ResponseString {
    val response = client.request(url)
    return response.bodyAsText()
}

//val client = HttpClient.newBuilder().build()

//suspend fun requestJdkClient(url: URL): ResponseString {
//    val request = HttpRequest.newBuilder(url.toURI()).build()
//    client.sendAsync(request, BodyHandlers.ofString()).await().body()
//}

// wait for delay or error to be signaled first
suspend fun delayOrFail(channel: Channel<Unit>, delayBy: Duration) = channelFlow {
    launch { send(delayTask(delayBy)) }
    launch { send(failedTask(channel)) }
}.first()

//Another way using select
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun delayOrFailAlt(failedTask: Channel<Unit>, delayBy: Duration) {
    select {
        failedTask.onReceive {}
        onTimeout(delayBy) {}
    }
}

suspend fun <T> failedTask(failed: Channel<T>) {
    failed.receive()
}

suspend fun delayTask(delayBy: Duration) {
    delay(delayBy)
}

suspend fun happyEyeBalls(tasks: List<URL>, delayedBy: Duration): Flow<ResponseString> = channelFlow {
    val failedTask = Channel<Unit>(Channel.CONFLATED)  // 1 element

    // start the first task immediately
    launch {
        try {
            send(requestKtorClient(tasks.first()))
        } catch (e: Exception) {
            failedTask.trySend(Unit) // it failed, send message to channel
        }
    }
    // start remaining tasks only after either waiting for delay or failed channel element is received
    tasks.drop(1).forEach { task ->
        delayOrFail(failedTask, delayedBy) // wait for delay or failed signal to continue next task
        launch {
            try {
                send(requestKtorClient(task))
            } catch (e: Exception) {
                failedTask.trySend(Unit) // task failed, send message to channel - next task will start
            }
        }
    }
}

val task1 = URL("http://localhost/delay/1")
val task2 = URL("http://localhost/delay/3")
val task3 = URL("http://localhost/delay/5")
val task4 = URL("http://localhost/delay/7")
val task5 = URL("http://localhost/delay/9")

val delayBy = 800.milliseconds // delay next request

// coroutineScope() uses multi-threaded Dispatchers.Default - could use WithContext(Dispatchers.IO)
suspend fun main(): Unit = coroutineScope {

    val tasks = listOf(task1, task2, task3, task4, task5).shuffled()
    println(tasks.joinToString("\n"))
    val t = measureTime {
        // the first task to succeed - the others are cancelled
        val winner = happyEyeBalls(tasks, delayBy).first()
        println(winner) // will be delay/1
    }

    println("...in ${t.inWholeSeconds} second/s") // will be at least 1 second plus (delayBy * index of task1)
    client.close() //ktor client
}
