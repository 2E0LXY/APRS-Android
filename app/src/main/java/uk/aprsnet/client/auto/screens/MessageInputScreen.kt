package uk.aprsnet.client.auto.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import uk.aprsnet.client.auto.AutoDataBridge

/**
 * Free-text input for APRS messages using SearchTemplate.
 * SearchTemplate renders a voice/keyboard input bar — the user can dictate or type.
 * onSearchSubmitted fires when the user presses Enter or completes voice input.
 */
class MessageInputScreen(
    carContext: CarContext,
    private val toCallsign: String
) : Screen(carContext) {

    private var currentText = ""

    override fun onGetTemplate(): Template {
        return SearchTemplate.Builder(
            object : SearchTemplate.SearchCallback {
                override fun onSearchTextChanged(searchText: String) {
                    currentText = searchText
                }

                override fun onSearchSubmitted(searchText: String) {
                    val trimmed = searchText.trim()
                    if (trimmed.isNotBlank() && trimmed.length <= 67) { // APRS message max
                        AutoDataBridge.onSendMessage?.invoke(toCallsign, trimmed)
                        screenManager.pop() // MessageInputScreen
                        screenManager.pop() // MessageComposeScreen
                    } else if (trimmed.length > 67) {
                        // APRS limit exceeded — surface warning via invalidate (no toast in CAL)
                        currentText = trimmed.take(67)
                        invalidate()
                    }
                }
            }
        )
            .setHeaderAction(Action.BACK)
            .setSearchHint("To $toCallsign (67 chars max)…")
            .setShowKeyboardByDefault(false)  // prefer voice; user can switch
            .build()
    }
}
