package com.matchitcampus.app.data.local

import androidx.room.TypeConverter
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import com.matchitcampus.app.domain.model.ZoneType
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Room TypeConverters: they turn types SQLite can't store into types it can, and back.
 * Registered on [AppDatabase] with `@TypeConverters`.
 *
 * **Enums ↔ String.** Each enum is stored as its constant name, e.g. `"FOUND"`. Room 2.3+
 * could do this automatically, but writing the converters out makes the stored format
 * explicit, and readable in App Inspection, instead of hidden in generated code. Names are
 * used rather than ordinals, because adding a constant in the middle of an enum would shift
 * every ordinal and silently corrupt existing rows.
 *
 * **FloatArray ↔ ByteArray (BLOB).** An embedding is a few hundred floats. As a BLOB it takes
 * exactly 4 bytes per float, and converting is a single memory copy. The same data as JSON
 * text (`[0.0123456, -0.98…]`) is about three times bigger, and has to be formatted and
 * parsed digit by digit every time. That slows matching, which reads every candidate's
 * embedding. The byte order is fixed to little-endian, so the format doesn't depend on the
 * platform.
 *
 * Only plain Java/Kotlin is used here (no Android classes), so the converters are tested on
 * the JVM in `ConvertersTest`.
 */
class Converters {

    @TypeConverter
    fun floatArrayToBytes(value: FloatArray?): ByteArray? {
        if (value == null) return null
        val buffer = ByteBuffer.allocate(value.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(value)
        return buffer.array()
    }

    @TypeConverter
    fun bytesToFloatArray(value: ByteArray?): FloatArray? {
        if (value == null) return null
        val floats = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val result = FloatArray(floats.remaining())
        floats.get(result)
        return result
    }

    @TypeConverter fun itemTypeToString(value: ItemType): String = value.name
    @TypeConverter fun stringToItemType(value: String): ItemType = ItemType.valueOf(value)

    @TypeConverter fun itemStatusToString(value: ItemStatus): String = value.name
    @TypeConverter fun stringToItemStatus(value: String): ItemStatus = ItemStatus.valueOf(value)

    @TypeConverter fun categoryToString(value: Category): String = value.name
    @TypeConverter fun stringToCategory(value: String): Category = Category.valueOf(value)

    @TypeConverter fun claimStatusToString(value: ClaimStatus): String = value.name
    @TypeConverter fun stringToClaimStatus(value: String): ClaimStatus = ClaimStatus.valueOf(value)

    @TypeConverter fun syncStateToString(value: SyncState): String = value.name
    @TypeConverter fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter fun zoneTypeToString(value: ZoneType): String = value.name
    @TypeConverter fun stringToZoneType(value: String): ZoneType = ZoneType.valueOf(value)
}
