package com.plane.tracker.domain

/**
 * Classifies flights into RARE or UNCOMMON tiers.
 * Direct port of the web app's classifyFlight() logic.
 */
object ClassifyFlight {

    private val MIL_PREFIXES = setOf(
        "RRR", "RFR", "GAF", "IAM", "BAF", "NAF", "FAF", "SVF", "DAF",
        "NOR", "HUF", "PLF", "RCH", "AIO"
    )

    private val MIL_CALLSIGN_STARTS = listOf(
        "DUKE", "ASCOT", "REACH", "EVAC", "NAVY", "CASA", "CANFO",
        "JAKE", "TOPCAT", "VIPER", "RAFR", "TALLY", "CHAOS", "DEMON", "MOOSE"
    )

    private val MIL_TYPES = setOf(
        "C17", "C130", "C30J", "C30H", "A400", "A40M",
        "KC10", "KC46", "KC35", "E3CF", "E3TF", "E6B", "E8",
        "EUFI", "F16", "F15", "F18", "F35", "F22",
        "HAWK", "P8", "U2", "GLHK", "RC35", "MQ9",
        "V22", "C5M", "C2A", "T38", "T6", "C12"
    )

    private val RARE_TYPES = setOf(
        "A124", "A225", "BLCF", "BLNG", "CONC", "B52", "B1", "B2", "A10"
    )

    private val HELI_TYPES = setOf(
        "H125", "H130", "H135", "H145", "H155", "H160", "H175", "H215", "H225",
        "EC20", "EC25", "EC30", "EC35", "EC45", "EC55", "EC75",
        "S76", "S92", "S70",
        "AW09", "AW10", "AW13", "AW16", "AW18",
        "B06", "B07", "B22", "B47", "B06T",
        "R22", "R44", "R66",
        "BK17", "B412", "B429", "B505",
        "AS50", "AS55", "AS65",
        "A109", "A119", "A139", "A149", "A169", "A189"
    )

    /**
     * Classify a flight. Returns null for ordinary flights.
     * @param seenAirlines set of previously seen airline names (for first-sighting check)
     */
    fun classify(
        callsign: String,
        type: String,
        altFt: Int,
        airlineName: String?,
        seenAirlines: Set<String>
    ): Classification? {
        val cs = callsign.uppercase().trim()
        val tp = type.uppercase().trim()

        // Strip digits to get prefix
        val prefix3 = cs.replace(Regex("[0-9]"), "").take(3)

        // RARE: military prefix
        if (prefix3 in MIL_PREFIXES) return Classification("MILITARY", Tier.RARE)

        // RARE: military callsign start
        for (s in MIL_CALLSIGN_STARTS) {
            if (cs.startsWith(s)) return Classification("MILITARY", Tier.RARE)
        }

        // RARE: military type
        if (tp in MIL_TYPES) return Classification("MILITARY", Tier.RARE)

        // RARE: rare civilian type
        if (tp in RARE_TYPES) return Classification("RARE TYPE", Tier.RARE)

        // RARE: extreme altitude
        if (altFt > 50_000) return Classification("HIGH ALT", Tier.RARE)

        // UNCOMMON: helicopter
        if (tp in HELI_TYPES) return Classification("HELICOPTER", Tier.UNCOMMON)

        // UNCOMMON: first sighting
        if (!airlineName.isNullOrBlank() && seenAirlines.isNotEmpty()) {
            if (airlineName !in seenAirlines) {
                return Classification("FIRST SIGHTING", Tier.UNCOMMON)
            }
        }

        return null
    }
}
