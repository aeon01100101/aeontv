package org.jellyfin.mobile.ui.screens.connect

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.R
import org.jellyfin.mobile.archive.ArchiveActivity
import org.jellyfin.mobile.livetv.LiveTvActivity
import org.jellyfin.mobile.ui.utils.CenterRow

@Composable
fun ConnectScreen(
    mainViewModel: MainViewModel,
    showExternalConnectionError: Boolean,
) {
    Surface(color = MaterialTheme.colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            LogoHeader()
            ServerSelection(
                showExternalConnectionError = showExternalConnectionError,
                onConnected = { hostname ->
                    mainViewModel.switchServer(hostname)
                },
            )
            ArchiveModeEntry()
            LiveTvEntry()
        }
    }
}

@Composable
private fun LiveTvEntry() {
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(28.dp))
    Divider()
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Live TV",
        style = MaterialTheme.typography.h6,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Watch live channels — AeonTV grabs the newest channel list & guide from archive.org every time you open it.",
        style = MaterialTheme.typography.body2,
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(
        onClick = {
            context.startActivity(Intent(context, LiveTvActivity::class.java))
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Watch Live TV")
    }
}

@Composable
private fun ArchiveModeEntry() {
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(28.dp))
    Divider()
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "No server? Watch free classics",
        style = MaterialTheme.typography.h6,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Archive Mode streams movies & TV straight from archive.org — no AeonTV server needed.",
        style = MaterialTheme.typography.body2,
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(
        onClick = {
            context.startActivity(Intent(context, ArchiveActivity::class.java))
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Open Archive Mode")
    }
}

@Stable
@Composable
fun LogoHeader() {
    CenterRow(
        modifier = Modifier.padding(vertical = 25.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            modifier = Modifier
                .height(72.dp),
            contentDescription = null,
        )
    }
}
