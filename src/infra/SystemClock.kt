package com.pms.dental.infra

import com.pms.dental.domain.service.Clock
import java.time.Instant

/** Real wall-clock time source. */
class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
