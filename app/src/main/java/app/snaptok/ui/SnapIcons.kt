package app.snaptok.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Small, consistent outline set; avoids shipping a catalogue of unused icons. */
object SnapIcons {
    private fun icon(name: String, data: String, filled: Boolean = false) = ImageVector.Builder(name,24.dp,24.dp,24f,24f)
        .addPath(PathParser().parsePathString(data).toNodes(), fill = if (filled) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    val AddCircleOutline = icon("Save", "M12 3 A9 9 0 1 1 11.99 3 M12 8 V16 M8 12 H16")
    val VideoLibrary = icon("Library", "M5 6 H21 V21 H5 Z M2 3 H18 M10 10 L16 13.5 L10 17 Z")
    val Tune = icon("Settings", "M4 6 H20 M4 12 H20 M4 18 H20 M8 3 V9 M16 9 V15 M10 15 V21")
    val CloudDownload = icon("Cloud", "M6 17 A5 5 0 0 1 5 7 A7 7 0 0 1 18 7 A5 5 0 0 1 19 17 M12 10 V21 M8 17 L12 21 L16 17")
    val ContentPaste = icon("Paste", "M8 5 H5 V21 H19 V5 H16 M8 3 H16 V7 H8 Z M8 11 H16 M8 15 H14")
    val HighQuality = icon("Quality", "M3 5 H21 V19 H3 Z M7 9 V15 M11 9 V15 M7 12 H11 M15 9 H18 V15 H15 Z")
    val FolderOpen = icon("Folder", "M3 7 V4 H10 L12 7 H21 V10 M3 10 H22 L19 20 H2 Z")
    val Download = icon("Download", "M12 3 V16 M7 11 L12 16 L17 11 M4 18 V21 H20 V18")
    val ArrowForward = icon("Forward", "M4 12 H20 M14 6 L20 12 L14 18")
    val Search = icon("Search", "M10 3 A7 7 0 1 1 9.99 3 M15 15 L21 21")
    val Close = icon("Close", "M6 6 L18 18 M18 6 L6 18")
    val ArrowDropDown = icon("Expand", "M7 10 L12 15 L17 10")
    val ViewList = icon("List", "M4 5 H7 V8 H4 Z M10 6 H20 M4 11 H7 V14 H4 Z M10 12 H20 M4 17 H7 V20 H4 Z M10 18 H20")
    val GridView = icon("Grid", "M3 3 H10 V10 H3 Z M14 3 H21 V10 H14 Z M3 14 H10 V21 H3 Z M14 14 H21 V21 H14 Z")
    private const val heart = "M12 21 L3.5 12.5 C-2 6 6 0 12 7 C18 0 26 6 20.5 12.5 Z"
    val Favorite = icon("Favourite",heart,true)
    val FavoriteBorder = icon("Favourite",heart)
    val PlayArrow = icon("Play", "M7 4 L20 12 L7 20 Z",true)
    val MoreVert = icon("More", "M12 4 V5 M12 11 V12 M12 18 V19")
    val MusicNote = icon("Audio", "M9 18 V5 L20 3 V16 M9 17 C2 14 2 23 9 20 M20 15 C13 12 13 21 20 18 M9 9 L20 7")
    val VideoFile = icon("Video", "M4 3 H15 L20 8 V21 H4 Z M15 3 V8 H20 M9 11 L16 15 L9 19 Z")
}
