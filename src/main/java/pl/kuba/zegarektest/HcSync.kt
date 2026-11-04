package pl.kuba.zegarektest

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.function.Consumer

/** Zapis danych z zegarka do Health Connect (stamtąd czyta je Samsung Health). */
object HcSync {

    private val PERMS = setOf(
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(BloodPressureRecord::class),
        HealthPermission.getWritePermission(OxygenSaturationRecord::class),
        HealthPermission.getWritePermission(BodyTemperatureRecord::class)
    )

    private val main = Handler(Looper.getMainLooper())

    @JvmStatic
    fun available(ctx: Context): Boolean =
        HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

    @JvmStatic
    fun permissionIntent(ctx: Context): Intent =
        PermissionController.createRequestPermissionResultContract().createIntent(ctx, PERMS)

    @JvmStatic
    fun checkGranted(ctx: Context, cb: Consumer<Boolean>) {
        if (!available(ctx)) { cb.accept(false); return }
        CoroutineScope(Dispatchers.IO).launch {
            val ok = try {
                HealthConnectClient.getOrCreate(ctx).permissionController
                    .getGrantedPermissions().containsAll(PERMS)
            } catch (e: Exception) { false }
            main.post { cb.accept(ok) }
        }
    }

    /** Wysyła dzisiejszy i wczorajszy dzień. */
    @JvmStatic
    fun syncRecent(ctx: Context, cb: Consumer<String>?) {
        val app = ctx.applicationContext
        if (!available(app)) { cb?.let { main.post { it.accept("Health Connect niedostępny na tym telefonie") } }; return }
        CoroutineScope(Dispatchers.IO).launch {
            val msg = try {
                val client = HealthConnectClient.getOrCreate(app)
                if (!client.permissionController.getGrantedPermissions().containsAll(PERMS)) {
                    "Brak zgody – stuknij „Połącz z Health Connect”"
                } else {
                    val today = LocalDate.now()
                    var n = 0
                    for (d in listOf(today.minusDays(1), today)) {
                        val recs = buildDay(app, d.toString())
                        for (chunk in recs.chunked(200)) {
                            client.insertRecords(chunk)
                            n += chunk.size
                        }
                    }
                    BleService.appendLog(app, "Health Connect: zapisano $n wpisów")
                    "Wysłano do Health Connect: $n wpisów"
                }
            } catch (e: Exception) {
                BleService.appendLog(app, "Health Connect błąd: ${e.message}")
                "Błąd Health Connect: ${e.message}"
            }
            cb?.let { main.post { it.accept(msg) } }
        }
    }

    private fun meta(id: String) = Metadata(clientRecordId = id, clientRecordVersion = 1L)

    private fun buildDay(ctx: Context, day: String): List<Record> {
        val out = ArrayList<Record>()
        val o = synchronized(HistStore::class.java) { JSONObject(HistStore.load(ctx, day).toString()) }
        val recs = o.optJSONObject("r") ?: return out
        val zone = ZoneId.systemDefault()
        val keys = recs.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val r = recs.optJSONObject(k) ?: continue
            val start = try {
                LocalDateTime.parse(day + "T" + k).atZone(zone)
            } catch (e: Exception) { continue }
            val end = start.plusMinutes(5)
            if (end.toInstant().isAfter(java.time.Instant.now())) continue
            val t = start.toInstant()
            val off = start.offset
            val id = "$day-$k"
            val st = r.optInt("st", 0)
            if (st > 0) out.add(StepsRecord(t, off, end.toInstant(), end.offset, st.toLong(), meta("st-$id")))
            val hr = r.optInt("hr", 0)
            if (hr in 30..230) out.add(
                HeartRateRecord(t, off, end.toInstant(), end.offset,
                    listOf(HeartRateRecord.Sample(t, hr.toLong())), meta("hr-$id"))
            )
            val sys = r.optInt("sys", 0)
            val dia = r.optInt("dia", 0)
            if (sys in 60..250 && dia in 30..180 && sys > dia) out.add(
                BloodPressureRecord(
                    time = t, zoneOffset = off,
                    systolic = Pressure.millimetersOfMercury(sys.toDouble()),
                    diastolic = Pressure.millimetersOfMercury(dia.toDouble()),
                    metadata = meta("bp-$id")
                )
            )
            val spo2 = r.optInt("spo2", 0)
            if (spo2 in 70..100) out.add(
                OxygenSaturationRecord(time = t, zoneOffset = off,
                    percentage = Percentage(spo2.toDouble()), metadata = meta("o2-$id"))
            )
            val temp = r.optDouble("temp", 0.0)
            if (temp in 30.0..43.0) out.add(
                BodyTemperatureRecord(time = t, zoneOffset = off,
                    temperature = Temperature.celsius(temp), metadata = meta("tp-$id"))
            )
        }
        return out
    }
}
