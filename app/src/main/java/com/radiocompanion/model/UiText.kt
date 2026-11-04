package com.radiocompanion.model

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A user-visible message as a string resource + arguments, resolved where it is
 * shown. The ViewModel and the playback service create these; the screen turns
 * them into text with the activity's context, so they follow the in-app language
 * (English/Arabic) even on Android 10–12, where the application and service
 * contexts keep the system language.
 */
class UiText(@StringRes val id: Int, vararg val args: Any) {

    fun resolve(context: Context): String =
        context.getString(id, *args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())

    @Composable
    fun resolve(): String = resolve(LocalContext.current)
}
