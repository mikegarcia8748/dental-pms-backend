package com.pms.dental.domain.model

/**
 * The two personas the system authenticates. [SYSADMIN] owns data and configuration;
 * [DENTIST] performs all clinical operations. Operational endpoints for [SYSADMIN] are a
 * later phase — this slice only needs both roles to exist and to sign in.
 */
enum class Role { SYSADMIN, DENTIST }
