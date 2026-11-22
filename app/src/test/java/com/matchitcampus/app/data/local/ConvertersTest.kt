package com.matchitcampus.app.data.local

import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.domain.model.SyncState
import com.matchitcampus.app.domain.model.ZoneType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** JVM tests for [Converters]. They run without a device, because the converters use no Android APIs. */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun floatArray_roundTrip_keepsEveryValueExactly() {
        val original = floatArrayOf(0f, 1f, -1f, 0.123456f, Float.MAX_VALUE, Float.MIN_VALUE, -0.5f)

        val restored = converters.bytesToFloatArray(converters.floatArrayToBytes(original))

        // Delta 0: a binary copy must not lose any precision (JSON text could).
        assertArrayEquals(original, restored, 0f)
    }

    @Test
    fun floatArray_roundTrip_realisticEmbeddingSize() {
        val original = FloatArray(100) { i -> (i - 50) / 37f }

        val restored = converters.bytesToFloatArray(converters.floatArrayToBytes(original))

        assertArrayEquals(original, restored, 0f)
    }

    @Test
    fun floatArray_usesFourBytesPerFloat() {
        val bytes = converters.floatArrayToBytes(FloatArray(100))!!

        assertEquals(400, bytes.size)
    }

    @Test
    fun floatArray_isLittleEndian() {
        // 1.0f is 0x3F800000. Little-endian stores the least significant byte first.
        val bytes = converters.floatArrayToBytes(floatArrayOf(1f))!!

        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F), bytes)
    }

    @Test
    fun floatArray_emptyArray_roundTripsToEmpty() {
        val restored = converters.bytesToFloatArray(converters.floatArrayToBytes(FloatArray(0)))

        assertEquals(0, restored!!.size)
    }

    @Test
    fun floatArray_null_staysNull() {
        assertNull(converters.floatArrayToBytes(null))
        assertNull(converters.bytesToFloatArray(null))
    }

    @Test
    fun enums_areStoredByName_andRoundTrip() {
        assertEquals("FOUND", converters.itemTypeToString(ItemType.FOUND))
        assertEquals("WALLETS_CARDS", converters.categoryToString(Category.WALLETS_CARDS))

        ItemType.entries.forEach { assertEquals(it, converters.stringToItemType(converters.itemTypeToString(it))) }
        ItemStatus.entries.forEach { assertEquals(it, converters.stringToItemStatus(converters.itemStatusToString(it))) }
        Category.entries.forEach { assertEquals(it, converters.stringToCategory(converters.categoryToString(it))) }
        ClaimStatus.entries.forEach { assertEquals(it, converters.stringToClaimStatus(converters.claimStatusToString(it))) }
        SyncState.entries.forEach { assertEquals(it, converters.stringToSyncState(converters.syncStateToString(it))) }
        ZoneType.entries.forEach { assertEquals(it, converters.stringToZoneType(converters.zoneTypeToString(it))) }
    }
}
