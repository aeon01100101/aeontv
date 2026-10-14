package org.jellyfin.mobile.sphere

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.archive.ArchivePlayerActivity
import org.jellyfin.mobile.ui.utils.AppTheme
import java.io.File
import java.util.zip.ZipFile

/**
 * Opens a .sphere file (Albish video format, see SPHERE_FORMAT.md):
 * - Script form: an Albish manifest whose `video` is a stream URL.
 * - Package form: a ZIP with `sphere.alb` + the video inside; the video is
 *   extracted to the app cache and played locally.
 * Shows the manifest's title/poster/description/chapters, then plays.
 */
class SphereActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceUri = intent?.data ?: intent?.getParcelableExtra<Uri>(EXTRA_URI)
        val composeView = ComposeView(this)
        setContentView(composeView)
        composeView.setContent {
            AppTheme {
                SphereScreen(sourceUri = sourceUri, onClose = { finish() })
            }
        }
    }

    companion object {
        private const val EXTRA_URI = "sphere_uri"

        fun start(context: Context, uri: Uri) {
            context.startActivity(
                Intent(context, SphereActivity::class.java).apply {
                    putExtra(EXTRA_URI, uri)
                },
            )
        }
    }
}

private sealed interface SphereState {
    data object Loading : SphereState
    data class Error(val message: String) : SphereState
    data class Ready(
        val manifest: SphereManifest,
        val playTarget: String,
        val posterData: Any?,
    ) : SphereState
}

@Composable
private fun SphereScreen(sourceUri: Uri?, onClose: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<SphereState>(SphereState.Loading) }

    LaunchedEffect(sourceUri) {
        state = if (sourceUri == null) {
            SphereState.Error("No .sphere file was given.")
        } else {
            try {
                resolveSphere(context, sourceUri)
            } catch (e: Exception) {
                SphereState.Error(e.message ?: "Couldn't read this .sphere file.")
            }
        }
    }

    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is SphereState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is SphereState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Not a playable .sphere", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(current.message, style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onClose) { Text("Back") }
                }
            }
            is SphereState.Ready -> {
                SphereDetail(state = current, onClose = onClose)
            }
        }
    }
}

/**
 * Copy the source into the cache, sniff the form, parse the manifest and work
 * out the playable target (stream URL for scripts, extracted file for packages).
 */
private suspend fun resolveSphere(context: Context, uri: Uri): SphereState = withContext(Dispatchers.IO) {
    val cacheDir = File(context.cacheDir, "sphere").apply { mkdirs() }
    val localCopy = File(cacheDir, "opened-${System.currentTimeMillis()}.sphere")
    context.contentResolver.openInputStream(uri)?.use { input ->
        localCopy.outputStream().use { output -> input.copyTo(output) }
    } ?: throw IllegalStateException("Couldn't open the file.")

    val isPackage = localCopy.inputStream().use { stream ->
        val magic = ByteArray(2)
        stream.read(magic) == 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
    }

    if (!isPackage) {
        val manifest = SphereManifestParser.parse(localCopy.readText(Charsets.UTF_8))
        if (!manifest.isPlayable) throw IllegalStateException("The manifest needs a literal title and video.")
        val video = manifest.video!!
        if (!video.startsWith("http://") && !video.startsWith("https://")) {
            throw IllegalStateException("A Sphere Script's video must be an http(s) stream URL.")
        }
        return@withContext SphereState.Ready(
            manifest = manifest,
            playTarget = video,
            posterData = manifest.poster,
        )
    }

    ZipFile(localCopy).use { zip ->
        val manifestEntry = zip.getEntry("sphere.alb")
            ?: throw IllegalStateException("This package has no sphere.alb at its root.")
        val manifestText = zip.getInputStream(manifestEntry).readBytes().toString(Charsets.UTF_8)
        val manifest = SphereManifestParser.parse(manifestText)
        if (!manifest.isPlayable) throw IllegalStateException("The manifest needs a literal title and video.")
        val videoName = manifest.video!!.substringAfterLast('/')
        val videoEntry = zip.getEntry(manifest.video)
            ?: throw IllegalStateException("The video '${manifest.video}' is not inside the package.")
        val videoFile = File(cacheDir, "${System.currentTimeMillis()}-$videoName")
        zip.getInputStream(videoEntry).use { input ->
            videoFile.outputStream().use { output -> input.copyTo(output) }
        }
        val posterFile = manifest.poster?.let { posterName ->
            zip.getEntry(posterName)?.let { posterEntry ->
                val file = File(cacheDir, "${System.currentTimeMillis()}-${posterName.substringAfterLast('/')}")
                zip.getInputStream(posterEntry).use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file
            }
        }
        SphereState.Ready(
            manifest = manifest,
            playTarget = Uri.fromFile(videoFile).toString(),
            posterData = posterFile ?: manifest.poster,
        )
    }
}

@Composable
private fun SphereDetail(state: SphereState.Ready, onClose: () -> Unit) {
    val context = LocalContext.current
    val manifest = state.manifest

    fun play(startSeconds: Long = 0) {
        ArchivePlayerActivity.start(
            context = context,
            streamUrl = state.playTarget,
            title = manifest.title ?: "Sphere",
            startPositionMs = startSeconds * 1000,
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(".sphere", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        }

        if (state.posterData != null) {
            SpherePoster(
                data = state.posterData,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(manifest.title ?: "", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
            val meta = listOfNotNull(manifest.year, manifest.creator).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.subtitle1, color = Color.LightGray)
            }
            if (!manifest.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(manifest.description, style = MaterialTheme.typography.body2)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { play() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play")
            }

            if (manifest.chapters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text("Chapters", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                manifest.chapters.forEach { chapter ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { play(chapter.atSeconds) },
                        elevation = 2.dp,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play from chapter", tint = MaterialTheme.colors.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(chapter.name, style = MaterialTheme.typography.body1, fontWeight = FontWeight.SemiBold)
                                Text(formatTimestamp(chapter.atSeconds), style = MaterialTheme.typography.caption, color = Color.LightGray)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun formatTimestamp(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@Composable
private fun SpherePoster(data: Any, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoader.Builder(context).build() }
    var bitmap by remember(data) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(data) {
        val request = ImageRequest.Builder(context)
            .data(data)
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
