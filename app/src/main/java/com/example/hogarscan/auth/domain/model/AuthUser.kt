package com.example.hogarscan.auth.domain.model

data class AuthUser(
    val id: String,
    val email: String,
    val createdAtMillis: Long
)