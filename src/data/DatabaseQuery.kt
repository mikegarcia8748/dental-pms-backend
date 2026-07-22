package com.pms.dental.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/** Runs a block inside a suspended Exposed transaction, off the caller's (possibly event-loop) thread. */
suspend fun <T> dbQuery(block: suspend () -> T): T =
    withContext(Dispatchers.IO) {
        suspendTransaction { block() }
    }
