package com.pms.dental.domain.model

/**
 * Which part of the PDA intake a question belongs to. One configurable questionnaire covers
 * both the medical history and the dental history via this discriminator.
 */
enum class IntakeSection { MEDICAL, DENTAL }
