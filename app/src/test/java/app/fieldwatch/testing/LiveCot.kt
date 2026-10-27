package app.fieldwatch.testing

import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.CotEvent
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.TakDefaults
import app.fieldwatch.domain.TakInstall
import app.fieldwatch.domain.TakPublish
import app.fieldwatch.domain.WatchTarget

/** Live TAK events for a device, built the way TakPublisher builds them (markers → face → render), one per line. */
object LiveCot {
    /** A valid install tag, so heard-here uids are phone-scoped. */
    const val TAG = "AB23"

    /** TAK on, sending every signed device (a finder with no attention note is skipped by default). */
    val settings = AppSettings(takEnabled = true, takAllSignatures = true, tagLocation = true)

    fun events(device: Sighting, fleets: List<Fleet>, watchlist: List<WatchTarget>): String {
        check(TakInstall.clean(TAG) == TAG)
        val phone = TakInstall.callsign(settings.takCallsign, TAG) // as TakPublisher names this phone
        return TakPublish.markers(device, settings, fleets, watchlist, TAG).joinToString("") { m ->
            val face = CotEvent.face(
                device = m.device, fleets = fleets, watchlist = watchlist, advertised = m.advertised,
                tag = TAG, pilot = m.pilot, viaCallsign = TakInstall.viaLine(m.uid, TAG, phone),
            )
            CotEvent.render(m.uid, face, m.lat, m.lon, Golden.NOW, TakDefaults.STALE_MS) + "\n"
        }
    }
}
