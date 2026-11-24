package com.matchitcampus.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The DataStore file. `preferencesDataStore` makes sure only one instance exists for this
 * file name in the process, which DataStore requires.
 */
private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

/**
 * Small sync settings kept in DataStore Preferences.
 *
 * Why DataStore and not Room: this is one number, not a table. Why DataStore and not
 * SharedPreferences: DataStore is asynchronous (reads are a Flow, writes are `suspend`),
 * so it never blocks the main thread.
 */
class SyncPreferences(context: Context) {

    private val dataStore = context.applicationContext.syncDataStore

    /**
     * The newest server `updatedAt` (epoch millis) of any item we've copied into Room.
     * The next sync only asks Firestore for items changed after it. 0 means "never synced".
     */
    val lastItemSyncMillis: Flow<Long> = dataStore.data.map { it[LAST_ITEM_SYNC] ?: 0L }

    /** Moves the sync point forward. Never moves it back, so an out-of-order page can't undo progress. */
    suspend fun advanceLastItemSync(millis: Long) {
        dataStore.edit { prefs ->
            val current = prefs[LAST_ITEM_SYNC] ?: 0L
            if (millis > current) prefs[LAST_ITEM_SYNC] = millis
        }
    }

    /**
     * Ids of items whose photo still has to be uploaded (see `PhotoRepository`). A small set of
     * ids fits DataStore well, and it survives the app being closed while offline.
     */
    val pendingPhotoUploads: Flow<Set<String>> = dataStore.data.map { it[PENDING_PHOTOS] ?: emptySet() }

    suspend fun addPendingPhoto(itemId: String) {
        dataStore.edit { prefs -> prefs[PENDING_PHOTOS] = (prefs[PENDING_PHOTOS] ?: emptySet()) + itemId }
    }

    suspend fun removePendingPhoto(itemId: String) {
        dataStore.edit { prefs -> prefs[PENDING_PHOTOS] = (prefs[PENDING_PHOTOS] ?: emptySet()) - itemId }
    }

    private companion object {
        val LAST_ITEM_SYNC = longPreferencesKey("last_item_sync_millis")
        val PENDING_PHOTOS = stringSetPreferencesKey("pending_photo_uploads")
    }
}
