package com.pms.dental.domain.model

/**
 * The kind of consent being acknowledged. `TREATMENT` is the PDA page-2 informed consent;
 * `DATA_PRIVACY` supports the RA 10173 processing basis; `RADIOGRAPH`/`EXTRACTION` are
 * available for per-procedure consents captured later.
 */
enum class ConsentType { TREATMENT, RADIOGRAPH, EXTRACTION, DATA_PRIVACY }
