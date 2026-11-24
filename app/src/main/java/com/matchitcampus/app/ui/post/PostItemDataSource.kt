package com.matchitcampus.app.ui.post

import android.net.Uri
import com.matchitcampus.app.data.repository.AuthRepository
import com.matchitcampus.app.data.repository.ItemRepository
import com.matchitcampus.app.data.repository.PhotoRepository
import com.matchitcampus.app.data.repository.ZoneRepository
import com.matchitcampus.app.domain.model.CampusZone
import com.matchitcampus.app.domain.model.Item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Who is posting: stored on the item as `ownerId` and `ownerName`. */
data class Author(val uid: String, val name: String)

/**
 * What the Post screen needs from the data layer. An interface for the same reason as
 * `FeedDataSource`: `PostItemViewModel` can be unit-tested with a fake.
 */
interface PostItemDataSource {
    suspend fun loadItem(id: String): Item?
    fun observeZones(): Flow<List<CampusZone>>

    /** The signed-in user, or null if nobody is signed in. */
    suspend fun currentAuthor(): Author?

    /** Compresses the picked image into a draft file. @return its path. */
    suspend fun createPhotoDraft(uri: Uri): String

    /**
     * Deletes a draft that won't be used. Not `suspend`: it's called from `onCleared`, after
     * the ViewModel's own scope is already cancelled, so the real version runs it in the app scope.
     */
    fun discardPhotoDraft(path: String)

    /** Makes a draft the item's photo. @return the photo's final path. */
    suspend fun commitPhotoDraft(draftPath: String, itemId: String): String

    suspend fun removePhoto(itemId: String)

    /**
     * Saves the item (Room first; sync uploads it). With [uploadPhoto], the item's photo is
     * queued for upload too, after the item is saved.
     */
    suspend fun saveItem(item: Item, uploadPhoto: Boolean)
}

/** The real [PostItemDataSource], over the repositories. */
class RepositoryPostItemDataSource(
    private val itemRepository: ItemRepository,
    private val zoneRepository: ZoneRepository,
    private val authRepository: AuthRepository,
    private val photoRepository: PhotoRepository,
    private val appScope: CoroutineScope,
) : PostItemDataSource {

    override suspend fun loadItem(id: String): Item? = itemRepository.observeItem(id).first()

    override fun observeZones(): Flow<List<CampusZone>> = zoneRepository.observeZones()

    override suspend fun currentAuthor(): Author? {
        val user = authRepository.authState.value ?: return null
        val profile = authRepository.observeProfile(user.uid).first()
        return Author(uid = user.uid, name = profile?.name?.takeIf { it.isNotBlank() } ?: user.email)
    }

    override suspend fun createPhotoDraft(uri: Uri): String = photoRepository.createDraft(uri)

    override fun discardPhotoDraft(path: String) {
        appScope.launch { photoRepository.discardDraft(path) }
    }

    override suspend fun commitPhotoDraft(draftPath: String, itemId: String): String =
        photoRepository.commitDraft(draftPath, itemId)

    override suspend fun removePhoto(itemId: String) = photoRepository.removeLocalPhoto(itemId)

    override suspend fun saveItem(item: Item, uploadPhoto: Boolean) {
        itemRepository.saveItem(item)
        if (uploadPhoto) photoRepository.queueUpload(item.id)
    }
}
