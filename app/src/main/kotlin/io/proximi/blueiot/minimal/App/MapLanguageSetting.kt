//
//  MapLanguageSetting.kt
//  BlueiotMinimal
//
//  The "Map language" choice. It is in the sheet that a long press on the map opens, in
//  debug and release builds. It is for testing the venue's translated titles; visitors
//  keep Automatic.
//
//  The choice sets `MapOptions.language`: the language of the POI labels and floor
//  names on the map. The app's own place titles (search, the search bar, new visit
//  stops) use the same language through `VenuePoi.all(features, language)`. A title
//  without a translation in that language shows the default title.
//
//  Automatic, the default, stores an empty value and sets `language` to `null`. The map
//  then uses `ProximiioLanguage.preferred(context)`, the app's display language. The app
//  declares English only (`res/xml/locales_config.xml`), so Automatic is `"en"`.
//
//  `VenueMapScreen` reads the choice when it creates the map session, and assigns
//  `ProximiioMapSession.options` when it changes, so the change applies at once. A visit
//  already in progress keeps the stop titles it was planned with.
//
package io.proximi.blueiot.minimal

import android.content.SharedPreferences
import androidx.core.content.edit
import io.proximi.map.live.MapOptions

object MapLanguageSetting {
    /** The key in the app's preferences file (`WristbandStore.preferences`), as on iOS. */
    const val KEY = "mapLanguage"

    /** One entry in the choice: the stored value and its title. */
    data class Choice(
        val value: String,
        val title: String,
    )

    /** The choices, in the iOS order. The empty value is Automatic. */
    val choices: List<Choice> =
        listOf(
            Choice(value = "", title = "Automatic"),
            Choice(value = "en", title = "English"),
            Choice(value = "ar", title = "Arabic"),
        )

    /**
     * The `MapOptions.language` for the stored value: the language code, or `null` for
     * Automatic. An empty or blank value is Automatic.
     */
    fun language(store: SharedPreferences): String? =
        store
            .getString(KEY, null)
            ?.trim()
            ?.ifEmpty { null }

    /** Stores [value], a `Choice.value`. The empty value is Automatic. */
    fun save(
        value: String,
        store: SharedPreferences,
    ) {
        store.edit { putString(KEY, value) }
    }

    /**
     * The diagnostics log line for [options]: the language the map draws in, and whether
     * it is the Automatic choice. [resolvedLanguage] is `options.resolvedLanguage(context)`,
     * the language the session draws in.
     */
    fun summary(
        options: MapOptions,
        resolvedLanguage: String,
    ): String = "map language: $resolvedLanguage" + if (options.language == null) " (automatic)" else ""
}
