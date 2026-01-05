package com.inkflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class InkFlowApplication

fun main(args: Array<String>) {
    runApplication<InkFlowApplication>(*args)
}
