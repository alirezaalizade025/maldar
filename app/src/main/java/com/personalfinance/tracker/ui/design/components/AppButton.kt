package com.personalfinance.tracker.ui.design.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.ui.design.MaldarDesignTheme

enum class AppButtonStyle { PRIMARY, OUTLINED }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppButtonStyle = AppButtonStyle.PRIMARY,
    loading: Boolean = false,
    loadingDescription: String = "در حال ذخیره…",
    leadingIcon: (@Composable RowScope.() -> Unit)? = null
) {
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = androidx.compose.material3.LocalContentColor.current
            )
            Spacer(Modifier.width(8.dp))
            Text(loadingDescription)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.invoke(this)
                if (leadingIcon != null) Spacer(Modifier.width(8.dp))
                Text(text)
            }
        }
    }
    val sizedModifier = modifier
        .defaultMinSize(minHeight = 48.dp)
        .semantics {
            if (loading) {
                stateDescription = loadingDescription
                liveRegion = LiveRegionMode.Polite
            }
        }
    if (style == AppButtonStyle.PRIMARY) {
        Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled && !loading,
            shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
            contentPadding = ButtonDefaults.ContentPadding,
            content = content
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled && !loading,
            shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
            contentPadding = ButtonDefaults.ContentPadding,
            content = content
        )
    }
}

@Preview(showBackground = true, locale = "fa")
@Composable
private fun AppButtonPreview() = MaldarDesignTheme { AppButton("ذخیره", {}) }

@Preview(showBackground = true, locale = "fa", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppButtonDarkPreview() = MaldarDesignTheme(true) {
    AppButton("ویرایش", {}, style = AppButtonStyle.OUTLINED)
}
