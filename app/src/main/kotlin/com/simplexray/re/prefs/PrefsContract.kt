package com.simplexray.re.prefs

import android.net.Uri
import androidx.core.net.toUri
import android.provider.BaseColumns

object PrefsContract {
    const val AUTHORITY: String = "com.simplexray.re.prefsprovider"
    val BASE_CONTENT_URI: Uri = "content://$AUTHORITY".toUri()
    const val PATH_PREFS: String = "prefs"

    object PrefsEntry : BaseColumns {
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_PREFS).build()
        const val CONTENT_TYPE: String =
            "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_PREFS"
        const val CONTENT_ITEM_TYPE: String =
            "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_PREFS"
        const val COLUMN_PREF_KEY: String = "pref_key"
        const val COLUMN_PREF_VALUE: String = "pref_value"
        const val COLUMN_PREF_TYPE: String = "pref_type"
    }
}
