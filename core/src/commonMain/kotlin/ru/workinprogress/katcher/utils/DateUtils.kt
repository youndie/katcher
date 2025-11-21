package ru.workinprogress.katcher.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number

fun LocalDateTime.human(): String {
    val yyyy = year.toString().padStart(4, '0')
    val monthNumber = month.number.toString().padStart(2, '0')
    val dd = day.toString().padStart(2, '0')
    val hh = hour.toString().padStart(2, '0')
    val mm = minute.toString().padStart(2, '0')

    return "$yyyy-$monthNumber-$dd $hh:$mm"
}
