package org.jellyfin.mobile.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.R
import org.jellyfin.mobile.archive.ArchiveActivity
import org.jellyfin.mobile.archive.ArchiveShortcut
import org.jellyfin.mobile.livetv.LiveTvShortcut
import org.jellyfin.mobile.originals.OriginalsActivity
import org.jellyfin.mobile.ui.utils.AppTheme

/**
 * AeonTV home: two doors, no servers.
 *  - "Originally from AeonTV": our own archive uploads (.mp4 / .sphere) + our Live TV.
 *  - "Other media": Archive Mode (free movies & TV from archive.org) + Live TV channels.
 */
class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Long-press launcher shortcuts keep working from the new home
        ArchiveShortcut.publish(this)
        LiveTvShortcut.publish(this)
        val composeView = ComposeView(this)
        setContentView(composeView)
        composeView.setContent {
            AppTheme {
                HomeScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Spacer(modifier = Modifier.height(36.dp))
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.height(84.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("AeonTV", style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(40.dp))

            HomeCard(
                title = "Originally from AeonTV",
                subtitle = "Our own uploads from the archive — .mp4 & .sphere — plus our Live TV",
                onClick = { OriginalsActivity.start(context) },
            )
            Spacer(modifier = Modifier.height(16.dp))
            HomeCard(
                title = "Other media",
                subtitle = "Free movies & TV from archive.org, plus Live TV channels",
                onClick = { ArchiveActivity.start(context) },
            )
        }
    }
}

@Composable
private fun HomeCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        backgroundColor = MaterialTheme.colors.surface,
        elevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = Color(0xFF00C8F0))
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.body2)
        }
    }
}
