package com.openchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openchat.app.ui.theme.LocalChatColors
import kotlinx.coroutines.delay

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Code(val language: String?, val code: String) : MarkdownBlock
    data class Bullets(val items: List<String>) : MarkdownBlock
    data class Numbered(val items: List<String>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

private val headingRegex = Regex("^(#{1,6})\\s+(.+)$")
private val bulletRegex = Regex("^[-*+]\\s+(.+)$")
private val orderedRegex = Regex("^(\\d+)[.)]\\s+(.+)$")
private val inlineRegex = Regex(
    """`([^`\n]+)`|\*\*([^*\n]+)\*\*|\*([^*\n]+)\*|~~([^~\n]+)~~|\[([^\]]+)\]\((https?://[^\s)]+)\)|(https?://[^\s)]+)"""
)

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeTextColor = MaterialTheme.colorScheme.onSurface
    val inlineCodeBackground = LocalChatColors.current.inlineCodeBackground

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    InlineMarkdownText(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        linkColor = linkColor,
                        codeTextColor = codeTextColor,
                        inlineCodeBackground = inlineCodeBackground
                    )
                }

                is MarkdownBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> 23.sp
                        2 -> 20.sp
                        3 -> 18.sp
                        else -> 16.sp
                    }
                    val topPadding = if (block.level == 1) 8.dp else 4.dp
                    InlineMarkdownText(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = size,
                            lineHeight = (size.value * 1.3f).sp
                        ),
                        linkColor = linkColor,
                        codeTextColor = codeTextColor,
                        inlineCodeBackground = inlineCodeBackground,
                        modifier = Modifier.padding(top = topPadding)
                    )
                }

                is MarkdownBlock.Code -> {
                    CodeBlock(language = block.language, code = block.code)
                }

                is MarkdownBlock.Bullets -> {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        block.items.forEach { item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 3.dp, end = 9.dp)
                                )
                                InlineMarkdownText(
                                    text = item,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    linkColor = linkColor,
                                    codeTextColor = codeTextColor,
                                    inlineCodeBackground = inlineCodeBackground,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.Numbered -> {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 1.dp, end = 9.dp)
                                )
                                InlineMarkdownText(
                                    text = item,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    linkColor = linkColor,
                                    codeTextColor = codeTextColor,
                                    inlineCodeBackground = inlineCodeBackground,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        InlineMarkdownText(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = LocalChatColors.current.subtleText
                            ),
                            linkColor = linkColor,
                            codeTextColor = codeTextColor,
                            inlineCodeBackground = inlineCodeBackground,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }

                MarkdownBlock.Divider -> {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun InlineMarkdownText(
    text: String,
    style: TextStyle,
    linkColor: Color,
    codeTextColor: Color,
    inlineCodeBackground: Color,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, linkColor, codeTextColor, inlineCodeBackground) {
        buildInlineMarkdown(
            text = text,
            linkColor = linkColor,
            codeTextColor = codeTextColor,
            inlineCodeBackground = inlineCodeBackground
        )
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style,
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()
                ?.let { annotation ->
                    runCatching { uriHandler.openUri(annotation.item) }
                }
        }
    )
}

@Composable
private fun CodeBlock(language: String?, code: String) {
    val colors = LocalChatColors.current
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.codeBackground,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.codeHeader)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language?.ifBlank { "code" } ?: "code",
                    modifier = Modifier.weight(1f),
                    color = colors.codeText.copy(alpha = 0.66f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                        contentDescription = if (copied) "Copied" else "Copy code",
                        modifier = Modifier.size(16.dp),
                        tint = colors.codeText.copy(alpha = 0.8f)
                    )
                }
            }
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = code.ifEmpty { " " },
                        color = colors.codeText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

private fun parseMarkdown(source: String): List<MarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            index++
            continue
        }

        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim().takeIf { it.isNotBlank() }
            val codeLines = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                codeLines.add(lines[index])
                index++
            }
            if (index < lines.size) index++
            blocks.add(MarkdownBlock.Code(language, codeLines.joinToString("\n")))
            continue
        }

        val heading = headingRegex.matchEntire(trimmed)
        if (heading != null) {
            blocks.add(
                MarkdownBlock.Heading(
                    level = heading.groupValues[1].length,
                    text = heading.groupValues[2].trim()
                )
            )
            index++
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MarkdownBlock.Divider)
            index++
            continue
        }

        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                quoteLines.add(
                    lines[index].trimStart().removePrefix(">").trimStart()
                )
                index++
            }
            blocks.add(MarkdownBlock.Quote(quoteLines.joinToString("\n")))
            continue
        }

        val firstBullet = bulletRegex.matchEntire(trimmed)
        if (firstBullet != null) {
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val match = bulletRegex.matchEntire(lines[index].trim()) ?: break
                items.add(match.groupValues[1].trim())
                index++
            }
            blocks.add(MarkdownBlock.Bullets(items))
            continue
        }

        val firstOrdered = orderedRegex.matchEntire(trimmed)
        if (firstOrdered != null) {
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val match = orderedRegex.matchEntire(lines[index].trim()) ?: break
                items.add(match.groupValues[2].trim())
                index++
            }
            blocks.add(MarkdownBlock.Numbered(items))
            continue
        }

        val paragraphLines = mutableListOf<String>()
        while (index < lines.size) {
            val current = lines[index]
            if (current.isBlank() || isBlockStart(current)) break
            paragraphLines.add(current.trim())
            index++
        }
        if (paragraphLines.isEmpty()) {
            paragraphLines.add(trimmed)
            index++
        }
        blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString("\n")))
    }
    return blocks
}

private fun isBlockStart(line: String): Boolean {
    val trimmed = line.trimStart()
    return trimmed.startsWith("```") ||
        trimmed.startsWith(">") ||
        headingRegex.matches(trimmed) ||
        bulletRegex.matches(trimmed) ||
        orderedRegex.matches(trimmed) ||
        trimmed == "---" || trimmed == "***" || trimmed == "___"
}

private fun buildInlineMarkdown(
    text: String,
    linkColor: Color,
    codeTextColor: Color,
    inlineCodeBackground: Color
): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    for (match in inlineRegex.findAll(text)) {
        if (match.range.first > lastIndex) {
            append(text.substring(lastIndex, match.range.first))
        }
        val groups = match.groups
        when {
            groups[1] != null -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = inlineCodeBackground,
                        color = codeTextColor,
                        fontSize = 14.sp
                    )
                ) {
                    append(groups[1]?.value.orEmpty())
                }
            }

            groups[2] != null -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(groups[2]?.value.orEmpty())
                }
            }

            groups[3] != null -> {
                withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(groups[3]?.value.orEmpty())
                }
            }

            groups[4] != null -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(groups[4]?.value.orEmpty())
                }
            }

            groups[5] != null && groups[6] != null -> {
                val label = groups[5]?.value.orEmpty()
                val url = groups[6]?.value.orEmpty()
                val start = length
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(label)
                }
                addStringAnnotation("URL", url, start, length)
            }

            groups[7] != null -> {
                val url = groups[7]?.value.orEmpty()
                val start = length
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(url)
                }
                addStringAnnotation("URL", url, start, length)
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}
