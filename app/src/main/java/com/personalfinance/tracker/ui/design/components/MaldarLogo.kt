package com.personalfinance.tracker.ui.design.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.R
import com.personalfinance.tracker.ui.design.MaldarDesignTheme

/** The approved Maldar brand mark, with its aspect ratio preserved. */
@Composable
fun MaldarLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Image(
        painter = painterResource(R.drawable.maldar_logo),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Preview(showBackground = true, locale = "fa")
@Composable
private fun MaldarLogoLightPreview() = MaldarDesignTheme {
    Box(Modifier.padding(16.dp)) {
        MaldarLogo(Modifier.size(width = 72.dp, height = 60.dp))
    }
}

@Preview(showBackground = true, locale = "fa", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MaldarLogoDarkPreview() = MaldarDesignTheme(darkTheme = true) {
    Box(Modifier.padding(16.dp)) {
        MaldarLogo(Modifier.size(width = 72.dp, height = 60.dp))
    }
}
