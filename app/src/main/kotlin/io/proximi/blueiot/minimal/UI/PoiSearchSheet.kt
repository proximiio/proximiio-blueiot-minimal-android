//
//  PoiSearchSheet.kt
//  BlueiotMinimal
//
//  The venue's place search. One sheet serves both the single-destination search and
//  the multi-select visit planner; the row accessory and the way the sheet closes are
//  parameters. The picked places are the only output.
//
//  Product-specific search features, such as categories, favourites or amenity icons,
//  belong in this file rather than in the SDK.
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
 * @param allowsMultiple pick several places instead of one. The tap order is the order
 *   they are walked.
 * @param adds add to a running visit rather than plan a new one. Changes the wording
 *   only.
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
                        // `MapLevelFormat` is the map library's own level rendering, the
                        // one its floor picker uses, so "1" and "1.5" read the same in
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

/** Tapping a picked place again removes it, and the numbers close up. */
private fun toggle(
    picked: MutableList<VenuePoi>,
    poi: VenuePoi,
) {
    val stop = picked.indexOf(poi)
    if (stop >= 0) picked.removeAt(stop) else picked.add(poi)
}
