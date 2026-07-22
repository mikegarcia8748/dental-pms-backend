package com.pms.dental.domain.service

import java.util.UUID

/** Injected identifier source so use cases that mint entities stay deterministic under test. */
fun interface IdGenerator {
    fun newId(): UUID
}
