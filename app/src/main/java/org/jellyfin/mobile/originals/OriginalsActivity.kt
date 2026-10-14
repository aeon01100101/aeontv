package org.jellyfin.mobile.originals

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.archive.ArchivePlayerActivity
import org.jellyfin.mobile.sphere.SphereActivity
import org.jellyfin.mobile.ui.utils.AppTheme

/**
 * "Originally from AeonTV": our own archive.org uploads that contain a
 * playable .mp4 or .sphere, plus the slot for our own Live TV
 * (our own m3u & guide, coming later).
 */
class OriginalsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val composeView = ComposeView(this)
        setContentView(composeView)
        composeView.setContent {
            AppTheme {
                OriginalsScreen(onClose = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, OriginalsActivity::class.java))
        }
    }
}

private sealed interface OriginalsState {
    data object Loading : OriginalsState
    data class Ready(val entries: List<OriginalEntry>) : OriginalsState
    data class Failed(val message: String) : OriginalsState
}

@Composable
private fun OriginalsScreen(onClose: () -> Unit) {
    var state by remember { mutableStateOf<OriginalsState>(OriginalsState.Loading) }
    var selected by remember { mutableStateOf<OriginalEntry?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    val context = LocalContext.current
    LaunchedEffect(reloadKey) {
        state = OriginalsState.Loading
        state = try {
            val entries = OriginalsApi.fetchOriginals(context.applicationContext)
            // Download & cache all posters up front so the grid is instant later
            OriginalThumbnails.prefetch(context.applicationContext, entries.map { it.posterUrl })
            OriginalsState.Ready(entries)
        } catch (e: Exception) {
            OriginalsState.Failed(e.message ?: "Unknown error")
        }
    }

    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        val entry = selected
        if (entry != null) {
            OriginalDetail(entry = entry, onBack = { selected = null })
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text("Originally from AeonTV", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                        Text("From the AeonTV archive", style = MaterialTheme.typography.caption)
                    }
                }

                // Our own Live TV lives here — it arrives when we make our own m3u & guide.
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    backgroundColor = MaterialTheme.colors.surface,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Live TV", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = Color(0xFF00C8F0))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Our own live channels — coming later, when we make our own playlist & guide.",
                            style = MaterialTheme.typography.body2,
                        )
                    }
                }

                Text(
                    "From the archive",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )

                when (val s = state) {
                    OriginalsState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is OriginalsState.Failed -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        ) {
                            Text("Couldn't load the AeonTV archive.", style = MaterialTheme.typography.h6)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(s.message, style = MaterialTheme.typography.caption)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { reloadKey++ }) { Text("Retry") }
                        }
                    }
                    is OriginalsState.Ready -> {
                        if (s.entries.isEmpty()) {
                            Text(
                                "Nothing here yet. Upload an .mp4 or a .sphere to the AeonTV archive and it will show up here.",
                                style = MaterialTheme.typography.body2,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(s.entries, key = { it.item.identifier }) { e ->
                                    OriginalCard(entry = e) { selected = e }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OriginalCard(entry: OriginalEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        backgroundColor = MaterialTheme.colors.surface,
    ) {
        Column {
            OriginalThumbnail(
                url = entry.posterUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    entry.item.title,
                    style = MaterialTheme.typography.subtitle2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val kinds = buildString {
                    if (entry.files.any { !it.isSphere }) append("MP4")
                    if (entry.files.any { it.isSphere }) {
                        if (isNotEmpty()) append(" · ")
                        append(".sphere")
                    }
                }
                Text(kinds, style = MaterialTheme.typography.caption, color = Color(0xFF00C8F0))
            }
        }
    }
}

@Composable
private fun OriginalThumbnail(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageLoader = remember { OriginalThumbnails.get(context) }
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        val result = runCatching { imageLoader.execute(OriginalThumbnails.request(context, url)) }.getOrNull()
        bitmap = (result?.drawable as? BitmapDrawable)?.bitmap
    }
    Box(modifier = modifier.background(Color.DarkGray)) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun OriginalDetail(entry: OriginalEntry, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val item = entry.item
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Details", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            OriginalThumbnail(
                url = entry.posterUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(item.title, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
            val meta = listOfNotNull(item.year, item.creator).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.subtitle1, color = Color.LightGray)
            }
            val description = remember(item.description) {
                item.description?.let {
                    runCatching { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }.getOrNull()
                }
            }
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(description, style = MaterialTheme.typography.body2, maxLines = 12, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Watch", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            for (file in entry.files) {
                Button(
                    onClick = {
                        if (file.isSphere) {
                            scope.launch {
                                try {
                                    val local = OriginalsApi.downloadSphere(context, item.identifier, file.name)
                                    SphereActivity.start(context, Uri.fromFile(local))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Couldn't download the .sphere file", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            ArchivePlayerActivity.start(context, file.streamUrl(item.identifier), item.title)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(
                        if (file.isSphere) "Open ${file.name}" else "Play ${file.name} (${file.sizeLabel})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
