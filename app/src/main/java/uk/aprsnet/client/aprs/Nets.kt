package uk.aprsnet.client.aprs

/**
 * Well-known APRS nets that the app can check into with one tap.
 * Each entry pre-fills the compose bar; the user appends their own message.
 *
 * ansrvrGroup is non-null when the destination is ANSRVR and an unjoin
 * command (U <group>) should be offered after the initial ACK arrives.
 */
data class AprsNet(
    val name: String,
    val schedule: String,
    val destination: String,
    val bodyPrefix: String,     // pre-filled text; cursor placed at end
    val ansrvrGroup: String? = null  // e.g. "HOTG" -> unjoin = "U HOTG"
)

val KNOWN_NETS = listOf(
    AprsNet(
        name        = "APRS Thursday (HOTG)",
        schedule    = "Every Thursday 00:00–23:59 UTC",
        destination = "ANSRVR",
        bodyPrefix  = "CQ HOTG ",
        ansrvrGroup = "HOTG"
    ),
    AprsNet(
        name        = "APRSPH Thursday",
        schedule    = "Every Thursday 00:00–23:59 UTC",
        destination = "APRSPH",
        bodyPrefix  = "HOTG "
    ),
    AprsNet(
        name        = "Hamfinity Sunday",
        schedule    = "Every Sunday 00:00–23:59 UTC",
        destination = "9M4GKS",
        bodyPrefix  = "CQ Hamfinity "
    ),
    AprsNet(
        name        = "ANSRVR CQ",
        schedule    = "Any time",
        destination = "ANSRVR",
        bodyPrefix  = "CQ ",
        ansrvrGroup = null      // group name varies; unjoin handled manually
    )
)
