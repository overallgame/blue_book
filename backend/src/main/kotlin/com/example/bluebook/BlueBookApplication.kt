package com.example.bluebook

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class BlueBookApplication

fun main(args: Array<String>) {
    runApplication<BlueBookApplication>(*args)
}
