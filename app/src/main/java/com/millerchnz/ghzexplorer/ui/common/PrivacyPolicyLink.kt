package com.millerchnz.ghzexplorer.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

object ExternalLinks {
    val privacyPolicy: Uri = Uri.parse("https://chaosferret.net/24ghzexplorer_privacy.html")
}

fun Context.openExternalLink(uri: Uri) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

/** Opens the published privacy policy. Play requires this to be reachable inside the app. */
@Composable
fun PrivacyPolicyLink(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = { context.openExternalLink(ExternalLinks.privacyPolicy) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text("Privacy policy")
    }
}
