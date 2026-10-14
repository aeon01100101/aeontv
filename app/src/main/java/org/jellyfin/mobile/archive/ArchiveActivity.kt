package org.jellyfin.mobile.archive

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.Html
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jellyfin.mobile.ui.utils.AppTheme

/**
 * Archive Mode: browse, search and stream free movies & TV from archive.org.
 * No AeonTV server connection needed — playback streams straight from archive.org.
 */
class ArchiveActivity : ComponentActivity() {
    private val openSphereLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            org.jellyfin.mobile.sphere.SphereActivity.start(this, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val composeView = ComposeView(this)
        setContentView(composeView)
        composeView.setContent {
            AppTheme {
                ArchiveScreen(
                    onClose = { finish() },
                    onOpenSphere = { openSphereLauncher.launch(arrayOf("*/*")) },
                    onOpenLiveTv = { org.jellyfin.mobile.livetv.LiveTvActivity.start(this) },
                )
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ArchiveActivity::class.java))
        }
    }
}

private sealed interface ArchiveQuery {
    data class Category(val index: Int) : ArchiveQuery
    data class Search(val text: String) : ArchiveQuery
}

@Composable
private fun ArchiveScreen(onClose: () -> Unit, onOpenSphere: () -> Unit, onOpenLiveTv: () -> Unit) {
    var selectedItem by remember { mutableStateOf<ArchiveItem?>(null) }

    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        val item = selectedItem
        if (item != null) {
            ArchiveDetail(
                item = item,
                onBack = { selectedItem = null },
            )
        } else {
            ArchiveBrowse(
                onClose = onClose,
                onItemClick = { selectedItem = it },
                onOpenSphere = onOpenSphere,
                onOpenLiveTv = onOpenLiveTv,
            )
        }
    }
}

@Composable
private fun ArchiveBrowse(onClose: () -> Unit, onItemClick: (ArchiveItem) -> Unit, onOpenSphere: () -> Unit, onOpenLiveTv: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchText by remember { mutableStateOf("") }
    var query by remember { mutableStateOf<ArchiveQuery>(ArchiveQuery.Category(0)) }
    val items = remember { mutableStateListOf<ArchiveItem>() }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }

    suspend fun loadPage(pageToLoad: Int) {
        loading = true
        if (pageToLoad == 1) loadFailed = false
        val result = when (val q = query) {
            is ArchiveQuery.Category -> ArchiveApi.browse(ArchiveApi.CATEGORIES[q.index].second, pageToLoad)
            is ArchiveQuery.Search -> ArchiveApi.search(q.text, pageToLoad)
        }
        if (pageToLoad == 1) {
            items.clear()
            if (result.isEmpty()) loadFailed = true
        }
        items.addAll(result)
        endReached = result.size < ArchiveApi.PAGE_SIZE
        page = pageToLoad
        loading = false
    }

    LaunchedEffect(query) {
        page = 1
        endReached = false
        loadPage(1)
    }

    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, query) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collectLatest { lastVisible ->
                if (lastVisible >= items.size - 8 && !loading && !endReached && items.isNotEmpty()) {
                    loadPage(page + 1)
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text("Other media", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                Text(
                    "Free movies & TV from archive.org",
                    style = MaterialTheme.typography.caption,
                )
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search movies & TV…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchText.isNotBlank()) {
                        query = ArchiveQuery.Search(searchText.trim())
                    }
                },
            ),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                "Have a .sphere file?",
                style = MaterialTheme.typography.body2,
                color = Color.LightGray,
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onOpenSphere) {
                Text("Open .sphere")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                "Live TV — 98 free channels",
                style = MaterialTheme.typography.body2,
                color = Color.LightGray,
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onOpenLiveTv) {
                Text("Watch")
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(ArchiveApi.CATEGORIES) { index, (name, _) ->
                val selected = (query as? ArchiveQuery.Category)?.index == index
                Surface(
                    color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.clickable { query = ArchiveQuery.Category(index) },
                ) {
                    Text(
                        text = name,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = if (selected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
                        style = MaterialTheme.typography.body2,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            loading && items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (loadFailed) "Couldn't load titles. Check your connection." else "No titles found.",
                        style = MaterialTheme.typography.body1,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { scope.launch { loadPage(1) } }) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items, key = { it.identifier }) { archiveItem ->
                        ArchiveCard(item = archiveItem, onClick = { onItemClick(archiveItem) })
                    }
                    if (loading) {
                        item {
                            Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveThumbnail(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoader.Builder(context).build() }
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        val result = runCatching { imageLoader.execute(request) }.getOrNull()
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
private fun ArchiveCard(item: ArchiveItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        elevation = 4.dp,
    ) {
        Column {
            ArchiveThumbnail(
                url = item.thumbnailUrl,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!item.year.isNullOrBlank()) {
                    Text(
                        text = item.year,
                        style = MaterialTheme.typography.caption,
                        color = Color.LightGray,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveDetail(item: ArchiveItem, onBack: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<ArchiveFile>?>(null) }

    LaunchedEffect(item.identifier) {
        files = ArchiveApi.loadFiles(item.identifier)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Details", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        }

        ArchiveThumbnail(
            url = item.thumbnailUrl,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.title, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
            val meta = listOfNotNull(item.year, item.creator).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.subtitle1, color = Color.LightGray)
            }
            val description = remember(item.description) {
                item.description?.let { raw ->
                    @Suppress("DEPRECATION")
                    Html.fromHtml(raw).toString().trim()
                }
            }
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(description, style = MaterialTheme.typography.body2, maxLines = 12, overflow = TextOverflow.Ellipsis)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Watch", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            when (val loaded = files) {
                null -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    if (loaded.isEmpty()) {
                        Text(
                            "No playable video files for this title.",
                            style = MaterialTheme.typography.body2,
                        )
                    } else {
                        loaded.forEachIndexed { index, file ->
                            ArchiveFileRow(
                                file = file,
                                recommended = index == 0,
                                onClick = {
                                    ArchivePlayerActivity.start(
                                        context = context,
                                        streamUrl = file.streamUrl(item.identifier),
                                        title = item.title,
                                    )
                                },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveFileRow(file: ArchiveFile, recommended: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = 2.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colors.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.format.ifBlank { file.name },
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    file.lengthLabel.takeIf { it.isNotBlank() },
                    file.sizeLabel.takeIf { it.isNotBlank() },
                    if (recommended) "Recommended" else null,
                ).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.caption, color = Color.LightGray)
            }
        }
    }
}
