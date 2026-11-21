package com.example.util

import com.example.shared.util.SharedPasswordHasher

object PasswordHasher {
    data class HashedPassword(
        val saltBase64: String,
        val hashBase64: String
    )

    fun hashPassword(password: String): HashedPassword {
        val h = SharedPasswordHasher.hashPassword(password)
        return HashedPassword(h.saltBase64, h.hashBase64)
    }

    fun verifyPassword(password: String, saltBase64: String, expectedHashBase64: String): Boolean =
        SharedPasswordHasher.verifyPassword(password, saltBase64, expectedHashBase64)

    fun generateTemporaryPassword(length: Int = 8): String =
        SharedPasswordHasher.generateTemporaryPassword(length)

    fun generateCode(prefix: String): String =
        SharedPasswordHasher.generateCode(prefix)

    fun generateBusinessCode(businessName: String): String =
        SharedPasswordHasher.generateBusinessCode(businessName)

    fun buildTicketId(businessCode: String, username: String): String =
        SharedPasswordHasher.buildTicketId(businessCode, username)

    fun validateStrongPassword(password: String): String? =
        SharedPasswordHasher.validateStrongPassword(password)
}
