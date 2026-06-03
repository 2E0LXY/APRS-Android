package uk.aprsnet.client.aprs

import androidx.compose.ui.graphics.Color
import uk.aprsnet.client.model.StationType

/**
 * Maps APRS symbol codes / station types to a marker colour and short glyph.
 * A lightweight native stand-in for the website's symbol sprite sheet -
 * enough to distinguish station kinds at a glance on the map.
 */
object Symbols {

    data class MarkerStyle(val color: Color, val glyph: String)

    private val HAM      = MarkerStyle(Color(0xFF3B82F6), "R")
    private val WEATHER  = MarkerStyle(Color(0xFF22C55E), "W")
    private val GLIDER   = MarkerStyle(Color(0xFFF59E0B), "G")
    private val OBJECT   = MarkerStyle(Color(0xFFE2E8F0), "O")
    private val SHIP     = MarkerStyle(Color(0xFF06B6D4), "S")
    private val LORA     = MarkerStyle(Color(0xFFA855F7), "L")
    private val MMDVM    = MarkerStyle(Color(0xFFEC4899), "M")
    private val OTHER    = MarkerStyle(Color(0xFF94A3B8), "?")

    fun styleFor(type: StationType): MarkerStyle = when (type) {
        StationType.HAM     -> HAM
        StationType.WEATHER -> WEATHER
        StationType.GLIDER  -> GLIDER
        StationType.OBJECT  -> OBJECT
        StationType.SHIP    -> SHIP
        StationType.LORA    -> LORA
        StationType.MMDVM   -> MMDVM
        StationType.OTHER   -> OTHER
    }

    /** Rough description of a symbol code for the station detail sheet. */
    fun describe(table: Char, code: Char): String = when (code) {
        '_' -> "Weather station"
        '>' -> "Car"
        'k' -> "Truck"
        'b' -> "Bicycle"
        '<' -> "Motorcycle"
        'O' -> "Balloon"
        '\'' -> "Aircraft / glider"
        'Y' -> "Yacht"
        's' -> "Ship / boat"
        '-' -> "House / QTH"
        'r' -> "Repeater"
        'I' -> "TCP/IP station"
        '#' -> "Digipeater"
        else -> "Station ($table$code)"
    }
}