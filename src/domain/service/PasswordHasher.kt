package com.pms.dental.domain.service

/** Hashes and verifies passwords. The real implementation uses bcrypt; tests substitute a fake. */
interface PasswordHasher {
    fun hash(rawPassword: String): String
    fun verify(rawPassword: String, hash: String): Boolean
}
