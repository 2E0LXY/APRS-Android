package uk.aprsnet.client.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// APRS Net dark palette - v2.3 visual refresh
// Deep navy base, elevated panels with a subtle glass feel, vivid accents.
// ============================================================================

// -- Backgrounds: three elevation tiers --------------------------------------
val BgDeep      = Color(0xFF0A0F1C)   // page background, lowest tier
val BgBase      = Color(0xFF0F172A)   // legacy alias - kept for older refs
val BgPanel     = Color(0xFF131A2C)   // card surface
val BgPanelHi   = Color(0xFF1A2540)   // raised element inside a card
val BgHeader    = Color(0xFF152033)   // top bar - darker so the bright title pops
val BorderCol   = Color(0xFF263147)   // subtle border / divider

// -- Text --------------------------------------------------------------------
val TextBase    = Color(0xFFE2E8F0)   // primary text (now used for the TopBar title)
val TextHi      = Color(0xFFF1F5F9)   // emphasised text
val TextDim     = Color(0xFF94A3B8)   // secondary / preview text (slightly brighter
                                       // than before so previews are readable)
val TextMute    = Color(0xFF64748B)   // tertiary / timestamp text

// -- Accents -----------------------------------------------------------------
val Accent      = Color(0xFF38BDF8)   // cyan - app accent (TopBar title, links)
val AccentBlue  = Color(0xFF2563EB)   // saturated blue - unread badge fill
val AccentLime  = Color(0xFF84CC16)   // lime - ACK / success-progress
val AccentAmber = Color(0xFFF59E0B)   // amber - gliders, warnings
val AccentPurple= Color(0xFFA855F7)   // purple - LoRa
val AccentCyan  = Color(0xFF06B6D4)   // cyan-saturated - ships
val AccentRose  = Color(0xFFEC4899)   // magenta - errors-prime, "delete" swipe bg

// -- Status colours ----------------------------------------------------------
val Ok          = Color(0xFF4ADE80)
val Err         = Color(0xFFF87171)
val Warn        = AccentAmber

// -- Bubbles -----------------------------------------------------------------
val BubbleMine  = Color(0xFF1E40AF)   // outgoing - deep blue, gradient applied in code
val BubbleAcked = Color(0xFF15803D)   // ACKed outgoing - keeps green for clarity
val BubbleThem  = Color(0xFF1F2A44)   // incoming - elevated panel