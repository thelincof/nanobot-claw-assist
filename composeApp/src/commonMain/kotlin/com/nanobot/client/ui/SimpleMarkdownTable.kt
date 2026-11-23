package com.nanobot.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.utils.buildMarkdownAnnotatedString
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * The renderer's table layout in 0.28 can collapse to an invisible zero-height
 * block on some Compose versions. This renderer is deliberately simple: every
 * row is a normal weighted Row, so a table always has a measurable size.
 */
@Composable
fun SimpleMarkdownTable(model: MarkdownComponentModel) {
    val header = model.node.findChildOfType(GFMElementTypes.HEADER)
    val rows = model.node.children.filter { it.type == GFMElementTypes.ROW }
    val headerCells = header?.children?.filter { it.type == GFMTokenTypes.CELL }.orEmpty()
    if (headerCells.isEmpty() && rows.isEmpty()) return

    val cellStyle = model.typography.text
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (headerCells.isNotEmpty()) {
                TableRow(
                    cells = headerCells,
                    content = model.content,
                    style = cellStyle.copy(fontWeight = FontWeight.SemiBold),
                    header = true,
                )
            }
            rows.forEach { row ->
                val cells = row.children.filter { it.type == GFMTokenTypes.CELL }
                if (cells.isNotEmpty()) {
                    TableRow(
                        cells = cells,
                        content = model.content,
                        style = cellStyle,
                        header = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<ASTNode>,
    content: String,
    style: TextStyle,
    header: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (header) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            ),
    ) {
        cells.forEach { cell ->
            Box(
                Modifier
                    .weight(1f)
                    .padding(1.dp)
                    .background(
                        if (header) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = content.buildMarkdownAnnotatedString(cell, style),
                    style = style,
                )
            }
        }
    }
}
