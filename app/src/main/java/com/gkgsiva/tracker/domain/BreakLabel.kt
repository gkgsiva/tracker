package com.gkgsiva.tracker.domain

import java.time.LocalTime

/** What a break was for. Stored by name on the break-start entry. */
enum class BreakLabel(val text: String) {
    BREAKFAST("Breakfast"),
    TEA("Tea"),
    LUNCH("Lunch"),
    OTHER("Other");

    companion object {
        /** A first guess from the time a break starts; one tap changes it. */
        fun guess(start: LocalTime): BreakLabel = when {
            start < LocalTime.of(10, 30) -> BREAKFAST
            start >= LocalTime.of(12, 0) && start < LocalTime.of(14, 30) -> LUNCH
            else -> TEA
        }

        fun of(name: String?): BreakLabel? = entries.firstOrNull { it.name == name }

        /** How a break with [name] is shown: its label, or just "Break". */
        fun textOf(name: String?): String = of(name)?.text ?: "Break"
    }
}
