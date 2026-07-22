package com.pms.dental.infra

import at.favre.lib.crypto.bcrypt.BCrypt
import com.pms.dental.domain.service.PasswordHasher

/** bcrypt-backed [PasswordHasher]. Cost 12 is a sensible default for interactive logins. */
class BcryptPasswordHasher(private val cost: Int = 12) : PasswordHasher {

    override fun hash(rawPassword: String): String =
        BCrypt.withDefaults().hashToString(cost, rawPassword.toCharArray())

    override fun verify(rawPassword: String, hash: String): Boolean =
        BCrypt.verifyer().verify(rawPassword.toCharArray(), hash).verified
}
