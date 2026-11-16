package com.example.hogarscan.shared.data.http

import java.time.Instant

internal object UtcTimestamp {
    private val PATTERN = Regex(
        "[0-9]{4}-[0-9]{2}-[0-9]{2}T(?:[01][0-9]|2[0-3]):" +
                "[0-5][0-9]:[0-5][0-9](?:\\.[0-9]{1,3})?Z"
    )

    fun parseMillis(value: String): Long {
        require(PATTERN.matches(value)) {
            "La fecha no tiene el formato UTC esperado."
        }

        return Instant.parse(value).toEpochMilli()
    }
}