@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
@file:OptIn(DelicateCoroutinesApi::class)
@file:Suppress("DeferredResultUnused")

import kotlinx.coroutines.*
import java.net.InetAddress
import kotlin.time.Duration
import kotlin.time.measureTime

runBlocking {
    val listResult = CompletableDeferred<Map<String, Deferred<Pair<Duration, Boolean>>>>()
    val deepListResult = CompletableDeferred<Map<String, Deferred<Pair<Duration, Boolean>>>>()
    val definedResult = CompletableDeferred<Set<String>>()
    launch(Dispatchers.Default) {
        val list = listResult.await()
        println("Starting checking list: ${list.size}")
        var completed: Int
        val size = list.size
        do {
            completed = list.count { it.value.isCompleted }
            println("Completed: $completed from $size")
            delay(1_00)
        } while (completed < size)
        println("Done!")
        val defined = list.filter { it.value.await().second }.map { it.key to it.value.await().first }.toMap()
        println("Defined ${defined.size}: $defined")

        definedResult.complete(defined.keys)

        val deepList = deepListResult.await()
        println("Starting deep checking list: ${deepList.size}")
        var completedDeep: Int
        val sizeDeep = deepList.size
        do {
            completedDeep = deepList.count { it.value.isCompleted }
            println("Completed deep list: $completedDeep from $sizeDeep")
            delay(1_00)
        } while (completedDeep < sizeDeep)
        println("Done!")
        val definedDeep = deepList.filter { it.value.await().second }.map { it.key to it.value.await().first }.toMap()
        println("Defined deep ${definedDeep.size}: $definedDeep")
    }
    launch(newFixedThreadPoolContext(16384, "PING")) {
        println("Creating list")
        val list =
            buildMap {
                for (a in 0..255) {
                    for (b in 0..255) {
                        val ip = "192.168.$a.$b"
                        val result =
                            async {
                                val result: Boolean
                                measureTime {
                                    result = InetAddress.getByName(ip).isReachable(5000)
                                } to result
                            }
                        put(ip, result)
                    }
                }
            }
        listResult.complete(list)
    }.join()
    launch(newFixedThreadPoolContext(256, "DEEP-PING")) {
        val ips = definedResult.await()
        println("Creating deep list")
        val deepList = ips.associateWith { ip ->
            val result = async {
                val result: Boolean
                measureTime {
                    result = ping(ip)
                } to result
            }
            result
        }
        deepListResult.complete(deepList)
    }
}

fun ping(host: String): Boolean = runCatching {
    val process = ProcessBuilder("ping", "-c 1", "-W 1", host).start()
    process.waitFor()
    process.exitValue() == 0
}.getOrElse { false }