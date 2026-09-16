//
//  PoiSearchSheet.kt
//  BlueiotMinimal
//
//  Search the venue's places; pick one, or pick several. The pick is the only thing
//  that leaves here.
//
//  There is one search in this app and this is it. "Where to?" and "plan my
//  afternoon" are the same list, the same matching and the same rows — only the row's
//  accessory and the way the sheet closes differ, which is a parameter rather than a
//  second screen.
//
//  This is where a real product diverges first — categories, favourites, amenity
//  icons, "nearest toilet". All of it belongs in this file's place, and none of it
//  belongs in the SDK.
//
package io.proximi.blueiot.minimal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.proximi.map.core.MapLevelFormat

/**
 * @param allowsMultiple several places instead of one, in the order they are tapped,
 *   because that order is the order the visitor walks.
 * @param adds adding to a visit that is already running rather than planning a new
 *   one. Same list, same multi-select, the words that screen needs.
 */
@Composable
fun PoiSearchSheet(
    pois: List<VenuePoi>,
    allowsMultiple: Boolean = false,
    adds: Boolean = false,
    onPick: (List<VenuePoi>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val picked = remember { mutableStateListOf<VenuePoi>() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (allowsMultiple) (if (adds) "Add to your visit" else "Plan a visit") else "Where to?",
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search places") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (pois.isEmpty()) {
            Text("No places yet — the venue is still downloading.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
            items(VenuePoi.matching(query, pois), key = { it.id }) { poi ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!allowsMultiple) {
                                    onPick(listOf(poi))
                                    return@clickable
                                }
                                toggle(picked, poi)
                            }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(poi.title, style = MaterialTheme.typography.bodyLarge)
                        // `MapLevelFormat` is the map library's — the same rendering
                        // its own floor picker uses, so "1" and "1.5" read the same in
                        // both places.
                        Text(
                            "Level ${MapLevelFormat.trimmed(poi.level)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val stop = picked.indexOf(poi)
                    if (stop >= 0) {
                        Text("${stop + 1}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (allowsMultiple) {
            Button(
                onClick = { onPick(picked.toList()) },
                enabled = picked.isNotEmpty(),
                modifier = Modifier.align(Alignment.End),
            ) { Text(if (adds) "Add" else "Start") }
        }
    }
}

/** Tapping a picked place again takes it back out, and the numbers close up. */
private fun toggle(
    picked: MutableList<VenuePoi>,
    poi: VenuePoi,
) {
    val stop = picked.indexOf(poi)
    if (stop >= 0) picked.removeAt(stop) else picked.add(poi)
}
