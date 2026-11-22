package com.example.hogarscan.shared.data.http

class ApiError internal constructor(
    val statusCode: Int,
    val code: String?,
    val message: String,
    val currentSummaryGeneration: Int?,
    val retryAfterSeconds: Long?
)