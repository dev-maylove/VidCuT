package id.jsn.vidcut.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VidCutDarkColors = darkColorScheme(
    primary = Color(0xFF66FFB3),
    secondary = Color(0xFF67D9FF),
    background = Color(0xFF080A10),
    surface = Color(0xFF11141C)
)

@Composable
fun ReUploadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VidCutDarkColors,
        content = content
    )
}
