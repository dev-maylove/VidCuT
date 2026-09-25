package id.jsn.vidcut.dubbing

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.delay

/** Runtime guardrails for long-running on-device separation. */
object TigerRuntimePolicy {
    private const val MIN_FREE_MEMORY = 192L * 1024L * 1024L

    data class Snapshot(val freeBytes: Long, val lowMemory: Boolean, val thermalStatus: Int)

    fun snapshot(context: Context): Snapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.currentThermalStatus
        } else PowerManager.THERMAL_STATUS_NONE
        return Snapshot(info.availMem, info.lowMemory, thermal)
    }

    suspend fun beforeInference(context: Context) {
        val s = snapshot(context)
        if (s.lowMemory || s.freeBytes < MIN_FREE_MEMORY) {
            throw IllegalStateException("Memori bebas terlalu rendah untuk AI separation (${s.freeBytes / 1024 / 1024} MB). Tutup aplikasi lain lalu coba lagi.")
        }
        if (Build.VERSION.SDK_INT >= 29) {
            when (s.thermalStatus) {
                PowerManager.THERMAL_STATUS_SEVERE,
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN ->
                    throw IllegalStateException("Perangkat terlalu panas. Hentikan proses dan biarkan perangkat mendingin.")
                PowerManager.THERMAL_STATUS_MODERATE -> delay(750)
            }
        }
    }
}
