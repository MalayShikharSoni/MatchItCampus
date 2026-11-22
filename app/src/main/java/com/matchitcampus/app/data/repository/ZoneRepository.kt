package com.matchitcampus.app.data.repository

import android.util.Log
import com.matchitcampus.app.data.local.dao.CampusZoneDao
import com.matchitcampus.app.data.local.mapper.toDomain
import com.matchitcampus.app.data.local.mapper.toEntity
import com.matchitcampus.app.data.local.seed.ZoneSeedParser
import com.matchitcampus.app.domain.model.CampusZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException

private const val TAG = "MatchIt/ZoneRepository"

/**
 * The campus zones. They're local only: they're never synced, because every phone seeds the
 * same list from the bundled JSON.
 *
 * @param readSeedJson returns the text of `campus_zones.json`. It's passed in as a function
 *   instead of reading `context.assets` here, so a test can supply its own JSON.
 */
class ZoneRepository(
    private val zoneDao: CampusZoneDao,
    private val readSeedJson: () -> String,
) {

    fun observeZones(): Flow<List<CampusZone>> =
        zoneDao.observeAll().map { zones -> zones.map { it.toDomain() } }

    suspend fun getZones(): List<CampusZone> = zoneDao.getAll().map { it.toDomain() }

    /**
     * Fills the zone table from the JSON file the first time the app runs.
     *
     * It uses a "count == 0" check rather than a `RoomDatabase.Callback.onCreate`, because
     * the callback only gets a raw SQLite connection (no DAOs), and if the seed failed there,
     * it would never run again. With the count check, a failed seed is simply retried on the
     * next launch. Running it twice at once is harmless too, because upsert replaces rows by id.
     *
     * Once seeded, the table is never overwritten from the file again, so zones corrected with
     * the debug Zone Capture tool are kept.
     *
     * A broken seed file is logged and the app carries on without zones, instead of crashing.
     *
     * @return true if zones were inserted, false if the table already had zones or seeding failed.
     */
    suspend fun seedIfEmpty(): Boolean {
        if (zoneDao.count() > 0) return false
        val zones = try {
            // Reading an asset is blocking file I/O, so it runs on the IO dispatcher.
            withContext(Dispatchers.IO) { ZoneSeedParser.parse(readSeedJson()) }
        } catch (e: IOException) {
            Log.e(TAG, "Couldn't read the zone seed file", e)
            return false
        } catch (e: SerializationException) {
            Log.e(TAG, "Zone seed file is not valid JSON", e)
            return false
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Zone seed file has an invalid value", e)
            return false
        }
        zoneDao.upsertAll(zones.map { it.toEntity() })
        Log.i(TAG, "Seeded ${zones.size} campus zones")
        return true
    }
}
