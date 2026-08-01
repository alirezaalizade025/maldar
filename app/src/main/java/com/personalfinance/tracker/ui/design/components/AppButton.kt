package com.personalfinance.tracker.ui.design.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    leadingIcon: (@Composable RowScope.() -> Unit)? = null
) {
    val content: @Composable RowScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.invoke(this)
            if (leadingIcon != null) Spacer(Modifier.width(8.dp))
            Text(text)
        }
    }
    val sizedModifier = modifier.defaultMinSize(minHeight = 48.dp)
    if (style == AppButtonStyle.PRIMARY) {
        Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
            contentPadding = ButtonDefaults.ContentPadding,
            content = content
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
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
