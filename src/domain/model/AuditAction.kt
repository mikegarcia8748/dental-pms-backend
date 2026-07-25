package com.pms.dental.domain.model

/** The kind of change recorded in the audit log. No hard deletes — deactivation is an action. */
enum class AuditAction { CREATE, UPDATE, DEACTIVATE }
