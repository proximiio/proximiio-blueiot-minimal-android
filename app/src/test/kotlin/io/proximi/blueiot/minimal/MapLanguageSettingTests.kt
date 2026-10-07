//
//  MapLanguageSettingTests.kt
//  BlueiotMinimalTests
//
//  The "Map language" choice, the `MapOptions.language` it sets, and the place titles the
//  app shows in that language. Automatic stores an empty value and must set `null`, so
//  the map uses `ProximiioLanguage.preferred(context)`. The app's own titles must use the
//  map's language and fall back to the default title, so the search and the map labels
//  agree.
//
package io.proximi.blueiot.minimal

import android.content.Context
import android.content.SharedPreferences
import io.proximi.map.live.MapOptions
import io.proximi.sdk.core.model.JsonValue
import io.proximi.sdk.core.model.ProximiioFeature
import io.proximi.sdk.core.model.ProximiioLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
// SDK 35, not 36: Robolectric's API 36 image requires Java 21 and this project builds on
// 17.
@Config(sdk = [35])
class MapLanguageSettingTests {
    private lateinit var context: Context

    /** A file of its own, so a test never reads or writes the app's real store. */
    private lateinit var store: SharedPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        store = context.getSharedPreferences("MapLanguageSettingTests.${UUID.randomUUID()}", Context.MODE_PRIVATE)
    }

    // Setting

    @Test
    fun unsetIsAutomatic() {
        assertNull(MapLanguageSetting.language(store))
    }

    @Test
    fun emptyValueIsAutomatic() {
        MapLanguageSetting.save("", store)
        assertNull(MapLanguageSetting.language(store))
        MapLanguageSetting.save("  ", store)
        assertNull(MapLanguageSetting.language(store))
    }

    @Test
    fun chosenLanguageSetsMapLanguage() {
        MapLanguageSetting.save("ar", store)
        val language = MapLanguageSetting.language(store)
        assertEquals("ar", language)
        assertEquals("ar", MapOptions().copy(language = language).resolvedLanguage(context))
    }

    /** Automatic leaves `language` `null`; the map then draws in the app language. */
    @Test
    fun automaticResolvesToAppLanguage() {
        val options = MapOptions().copy(language = null)
        assertNull(options.language)
        val resolved = options.resolvedLanguage(context)
        assertEquals(ProximiioLanguage.preferred(context), resolved)
        assertTrue(MapLanguageSetting.summary(options, resolved).endsWith("(automatic)"))
    }

    /**
     * iOS `testSettingsBundleOffersAutomaticEnglishArabic`: the choice defaults to
     * Automatic and offers Automatic, English and Arabic.
     */
    @Test
    fun sheetOffersAutomaticEnglishArabic() {
        assertEquals("mapLanguage", MapLanguageSetting.KEY)
        assertEquals(listOf("Automatic", "English", "Arabic"), MapLanguageSetting.choices.map { it.title })
        assertEquals(listOf("", "en", "ar"), MapLanguageSetting.choices.map { it.value })
    }

    // App titles

    private fun poi(
        id: String,
        title: String,
        translations: Map<String, String>?,
    ): ProximiioFeature {
        val properties =
            mutableMapOf<String, JsonValue>(
                "type" to JsonValue.String("poi"),
                "title" to JsonValue.String(title),
                "level" to JsonValue.Number(0.0),
            )
        if (translations != null) {
            properties["title_i18n"] = JsonValue.Object(translations.mapValues { JsonValue.String(it.value) })
        }
        return ProximiioFeature(
            id = id,
            geometry =
                ProximiioFeature.Geometry(
                    type = "Point",
                    coordinates = JsonValue.Array(listOf(JsonValue.Number(0.0), JsonValue.Number(0.0))),
                ),
            properties = JsonValue.Object(properties),
        )
    }

    @Test
    fun placeTitleUsesLanguage() {
        val places =
            VenuePoi.all(
                listOf(poi("cafe", title = "Cafe", translations = mapOf("en" to "Cafe", "ar" to "مقهى"))),
                language = "ar",
            )
        assertEquals(listOf("مقهى"), places.map { it.title })
    }

    /** A place without a translation in the language shows its `title`. */
    @Test
    fun placeTitleFallsBackToDefaultTitle() {
        val places =
            VenuePoi.all(
                listOf(
                    poi("cafe", title = "Cafe", translations = mapOf("en" to "Cafe")),
                    poi("shop", title = "Shop", translations = null),
                ),
                language = "ar",
            )
        assertEquals(listOf("Cafe", "Shop"), places.map { it.title })
    }

    @Test
    fun placeTitleInEnglish() {
        val places =
            VenuePoi.all(
                listOf(poi("cafe", title = "Café", translations = mapOf("en" to "Cafe", "ar" to "مقهى"))),
                language = "en",
            )
        assertEquals(listOf("Cafe"), places.map { it.title })
    }
}
