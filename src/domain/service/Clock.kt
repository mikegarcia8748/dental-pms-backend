package com.pms.dental.domain.service

import java.time.Instant

/** Injected time source so use cases stay pure and tests can pin "now". */
fun interface Clock {
    fun now(): Instant
}
