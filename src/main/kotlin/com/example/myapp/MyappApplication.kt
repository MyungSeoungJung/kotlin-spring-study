package com.example.myapp

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.LocalDateTime

@SpringBootApplication
class MyappApplication

fun main(args: Array<String>) {
	runApplication<MyappApplication>(*args)
}

@RestController
@RequestMapping("/test")
class TestController {

	@GetMapping
	fun test() : String {
		val durationArr = arrayOf<Long>(200, 100, 300, 200, 400)
		val ids = Array(5){""};

		val start = LocalDateTime.now()
		println(start.toString())
		for(i in 1..durationArr.size) {
			Thread.sleep(durationArr[i-1]) // long term process
			ids[i-1] = "${i.toLong()}: ${durationArr[i-1]}"
		}
		val end = LocalDateTime.now()
		println(end.toString())

		return """
			|All tasks are ended: ${ids.joinToString { it.toString() }}
			|Elapsed time: ${Duration.between(start, end).toMillis()}
			|Current Thread: ${Thread.currentThread().name}
		""".trimMargin()
	}

	@GetMapping("/coroutine")
	fun testCoroutine() : String {
		val durationArr = arrayOf<Long>(200, 100, 300, 200, 400)
		val ids = Array(5){""};

		val start = LocalDateTime.now()
		println(start.toString())

		runBlocking {
			for(i in 1..durationArr.size) {
				launch {
					delay(durationArr[i-1]) // long term process
					ids[i-1] = "${i.toLong()}: ${durationArr[i-1]}"
				}
			}
		}

		val end = LocalDateTime.now()
		println(end.toString())

		return """
			|All tasks are ended: ${ids.joinToString { it.toString() }}
			|Elapsed time: ${Duration.between(start, end).toMillis()}
			|Current Thread: ${Thread.currentThread().name}
		""".trimMargin()
	}
}