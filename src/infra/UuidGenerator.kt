package com.pms.dental.infra

import com.pms.dental.domain.service.IdGenerator
import java.util.UUID

/** Random (v4) UUID generator. */
class UuidGenerator : IdGenerator {
    override fun newId(): UUID = UUID.randomUUID()
}
