package app.roz.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink)
            if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = palette.muted, fontWeight = FontWeight.Medium)
        }
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, color: Color = palette.muted, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 12.dp, bottom = 6.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = color,
    )
}

@Composable
fun ItemCard(
    modifier: Modifier = Modifier,
    border: Color? = null,
    fill: Color = palette.card,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = fill,
        border = border?.let { BorderStroke(1.5.dp, it) },
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** The tick target at the start of a row: a 28dp ring inside a 44dp touch area. */
@Composable
fun CheckCircle(color: Color, checked: Boolean, description: String, round: Boolean = true, size: Dp = 28.dp, onClick: () -> Unit) {
    val shape = if (round) CircleShape else RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(if (checked) color else Color.Transparent)
                .border(2.dp, color, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.7f))
        }
    }
}

@Composable
fun KindChip(text: String, fill: Color, ink: Color) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(fill).padding(horizontal = 7.dp, vertical = 2.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = ink,
    )
}

/** Title over a small meta line, filling the middle of a row. */
@Composable
fun RowScope.TitleAndMeta(title: String, meta: @Composable RowScope.() -> Unit) {
    Column(Modifier.weight(1f).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = meta)
    }
}

@Composable
fun MetaText(text: String, color: Color = palette.muted) {
    Text(text, fontSize = 12.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun EmptyNote(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 8.dp), color = palette.muted, fontSize = 14.sp)
}

@Composable
fun Banner(text: String, action: String, fill: Color, ink: Color, onClick: () -> Unit) {
    ItemCard(fill = fill, modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(action, color = ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
