package uk.aprsnet.client.auto

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entry point for Android Auto / AAOS.
 * Declared in AndroidManifest with category MESSAGING.
 * The OS binds this service when the head unit connects.
 */
class AprsCarAppService : CarAppService() {

    /**
     * ALLOW_ALL is acceptable for side-loaded / development builds.
     * For Play Store release, restrict to OEM-signed hosts via
     *   HostValidator.Builder(applicationContext).addAllowedHosts(...).build()
     */
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = AprsCarSession()
}
