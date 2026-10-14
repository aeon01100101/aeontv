package org.jellyfin.mobile.livetv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.archive.ArchivePlayerActivity
import org.jellyfin.mobile.ui.utils.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live TV: downloads our own livetv.m3u + guide.xml from archive.org every
 * time it opens (see [LiveTvApi]), lists the channels with what's on now,
 * and plays the picked channel in the fullscreen player.
 */
class LiveTvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val composeView = ComposeView(this)
        setContentView(composeView)
        composeView.setContent {
            AppTheme {
                LiveTvScreen(onClose = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, LiveTvActivity::class.java))
        }
    }
}

private sealed interface LiveTvState {
    data object Loading : LiveTvState
    data class Ready(val data: LiveTvData) : LiveTvState
    data class Failed(val message: String) : LiveTvState
}

private val TILE_COLORS = listOf(
    Color(0xFF1565C0), Color(0xFF6A1B9A), Color(0xFF00695C), Color(0xFFAD1457),
    Color(0xFF4527A0), Color(0xFF00838F), Color(0xFF4E342E), Color(0xFF37474F),
    Color(0xFF827717), Color(0xFFBF360C),
)

@Composable
private fun LiveTvScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<LiveTvState>(LiveTvState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(reloadKey) {
        state = LiveTvState.Loading
        state = try {
            LiveTvState.Ready(LiveTvApi.load(context.applicationContext))
        } catch (e: Exception) {
            LiveTvState.Failed(e.message ?: "Unknown error")
        }
    }

    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Live TV",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { reloadKey++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh channels")
                }
            }

            when (val s = state) {
                LiveTvState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Downloading channel list…")
                        }
                    }
                }
                is LiveTvState.Failed -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text("Couldn't download the Live TV files.", style = MaterialTheme.typography.h6)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "The playlist and guide live on archive.org (aeontv-livetv). " +
                                    "Check your connection and try again.",
                                style = MaterialTheme.typography.body2,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(s.message, style = MaterialTheme.typography.caption)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { reloadKey++ }) { Text("Retry") }
                        }
                    }
                }
                is LiveTvState.Ready -> {
                    val data = s.data
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search channels") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    if (data.fromCache) {
                        Text(
                            "Offline — showing the saved channel list",
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    val filtered = data.channels.filter {
                        query.isBlank() || it.name.contains(query, ignoreCase = true)
                    }
                    val grouped = filtered.groupBy { it.group }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        for ((group, channels) in grouped) {
                            item(key = "header-$group") {
                                Text(
                                    text = group,
                                    style = MaterialTheme.typography.subtitle1,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00C8F0),
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(channels, key = { it.id }) { channel ->
                                ChannelRow(channel = channel, data = data) {
                                    ArchivePlayerActivity.start(context, channel.url, channel.name)
                                }
                            }
                        }
                        item(key = "footer") {
                            Text(
                                "Channels & guide: archive.org/details/aeontv-livetv",
                                style = MaterialTheme.typography.caption,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: LiveChannel, data: LiveTvData, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val current = data.currentProgram(channel.id, now)
    val tileColor = TILE_COLORS[(channel.name.hashCode() and 0x7fffffff) % TILE_COLORS.size]
    val initials = channel.name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tileColor),
        ) {
            Text(initials, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.name,
                style = MaterialTheme.typography.subtitle1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (current != null) {
                Text(
                    current.title,
                    style = MaterialTheme.typography.body2,
                    color = Color(0xFF00C8F0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val fraction = if (current.stopMs > current.startMs) {
                    ((now - current.startMs).toFloat() / (current.stopMs - current.startMs)).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "until " + SimpleDateFormat("h:mm a", Locale.US).format(Date(current.stopMs)),
                    style = MaterialTheme.typography.caption,
                )
            } else {
                Text(
                    "LIVE",
                    style = MaterialTheme.typography.body2,
                    color = Color(0xFF00C8F0),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${channel.name}")
    }
}
