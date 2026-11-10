package trashpanda.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Paths

/**
 * "Never touch" — the user's own exclusion list (DESIGN user-friendliness pass): any
 * path typed here is invisible to every scan, in every scope, forever. Persisted in
 * settings.json next to the theme. Filenames, folder paths and globs-free literals
 * only; matching is whole-component prefix (see Exclusions.kt).
 */
@Composable
fun ExclusionsScreen(
    exclusions: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.bg)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Never touch", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text)
                Text(
                    "Paths listed here are skipped by every scan — Trashpanda will not list, count or move anything " +
                        "under them. Example: D:\\My Games",
                    fontSize = 13.sp,
                    color = Tokens.textDim,
                )
            }
            TextButton(onClick = onBack) { Text("Home", color = Tokens.textDim, fontSize = 13.sp) }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).testTag("exclusion-input"),
                singleLine = true,
                placeholder = { Text("type a folder or file path…", fontSize = 13.sp, color = Tokens.textDim) },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Tokens.primary,
                    unfocusedBorderColor = Tokens.border,
                    cursorColor = Tokens.primary,
                    focusedTextColor = Tokens.text,
                    unfocusedTextColor = Tokens.text,
                ),
            )
            Button(
                onClick = {
                    val candidate = draft.trim()
                    val normal = runCatching { Paths.get(candidate).normalize().toString() }.getOrDefault(candidate)
                    if (normal.isNotEmpty() && exclusions.none { exclusionKey(it) == exclusionKey(normal) }) {
                        onAdd(normal)
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Tokens.primary,
                    contentColor = Tokens.primaryOn,
                    disabledContainerColor = Tokens.border,
                ),
            ) {
                Text("Add", fontWeight = FontWeight.SemiBold)
            }
        }

        if (exclusions.isEmpty()) {
            Text(
                "Nothing excluded yet — Trashpanda's built-in ban list (git, wallets, cookies, Windows core…) " +
                    "is always on top of whatever you add here.",
                fontSize = 13.sp,
                color = Tokens.textDim,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(exclusions, key = { it }) { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Tokens.surface)
                            .border(1.dp, Tokens.border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            entry,
                            fontSize = 13.sp,
                            color = Tokens.text,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onRemove(entry) }, modifier = Modifier.testTag("remove: $entry")) {
                            Text("remove", color = Tokens.riskReview, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
