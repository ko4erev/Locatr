package android.mobdev.com.locatr

import android.net.Uri


class GalleryItem {

    var mCaption: String? = null
    var mId: String? = null
    var mUrl: String? = null
    var mOwner: String? = null

    fun getPhotoPageUri(): Uri {
        return Uri.parse("https://www.flickr.com/photos/")
            .buildUpon()
            .appendPath(mOwner)
            .appendPath(mId)
            .build()
    }

    override fun toString(): String {
        return mCaption ?: ""
    }
}