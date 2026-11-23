package com.nanobot.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nanobot.client.resources.Res
import com.nanobot.client.resources.copied
import com.nanobot.client.resources.copy
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private data class CodeLanguage(
    val keywords: Set<String> = emptySet(),
    val lineComment: String? = null,
    val hashComment: Boolean = false,
    val dashComment: Boolean = false,
    val blockComment: Boolean = false,
)

@Composable
fun RichCodeBlock(
    code: String,
    language: String?,
    style: TextStyle,
) {
    val normalized = language?.trim()?.lowercase().orEmpty()
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_200)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    normalized.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(
                            if (copied) Res.string.copied else Res.string.copy,
                        ),
                        modifier = Modifier.size(16.dp),
                        tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            )
            SelectionContainer {
                Text(
                    text = highlightCode(code, normalized),
                    style = style.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = style.fontSize * 1.45f,
                    ),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                    softWrap = false,
                )
            }
        }
    }
}

private val KotlinKeywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
    "in", "interface", "is", "null", "object", "package", "return", "super", "this",
    "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
    "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally",
    "get", "import", "init", "param", "property", "receiver", "set", "setparam",
    "where", "actual", "abstract", "annotation", "companion", "const", "crossinline",
    "data", "enum", "expect", "external", "final", "infix", "inline", "inner",
    "internal", "lateinit", "noinline", "open", "operator", "out", "override",
    "private", "protected", "public", "reified", "sealed", "suspend", "tailrec",
    "vararg",
)

private val PythonKeywords = setOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
    "del", "elif", "else", "except", "False", "finally", "for", "from", "global",
    "if", "import", "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass",
    "raise", "return", "True", "try", "while", "with", "yield", "match", "case",
)

private val JsKeywords = setOf(
    "as", "async", "await", "break", "case", "catch", "class", "const", "continue",
    "debugger", "default", "delete", "do", "else", "export", "extends", "false",
    "finally", "for", "from", "function", "get", "if", "import", "in", "instanceof",
    "let", "new", "null", "of", "return", "set", "static", "super", "switch", "this",
    "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "with",
    "yield", "type", "interface", "enum", "implements", "private", "protected",
    "public", "readonly", "declare", "namespace", "abstract", "keyof", "infer",
)

private val JavaKeywords = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
    "final", "finally", "float", "for", "goto", "if", "implements", "import",
    "instanceof", "int", "interface", "long", "native", "new", "package", "private",
    "protected", "public", "return", "short", "static", "strictfp", "super", "switch",
    "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile",
    "while", "true", "false", "null", "var", "record", "sealed", "permits", "yield",
)

private val ShellKeywords = setOf(
    "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
    "function", "in", "select", "until", "time", "coproc", "return", "exit", "export",
    "local", "readonly", "set", "unset", "shift", "source", "alias", "unalias", "cd",
    "echo", "printf", "read", "test", "true", "false", "sudo", "apt", "dpkg", "git",
    "docker", "kubectl", "curl", "wget", "grep", "sed", "awk", "find", "xargs",
)

private val SqlKeywords = setOf(
    "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
    "create", "table", "drop", "alter", "index", "join", "left", "right", "inner",
    "outer", "on", "as", "and", "or", "not", "null", "is", "in", "like", "between",
    "group", "by", "order", "having", "limit", "offset", "union", "all", "distinct",
    "case", "when", "then", "else", "end", "with", "primary", "key", "foreign",
    "references", "default", "count", "sum", "avg", "min", "max",
)

private fun languageSpec(language: String): CodeLanguage = when {
    language in setOf("kt", "kotlin", "kts") -> CodeLanguage(KotlinKeywords, "//", blockComment = true)
    language in setOf("py", "python") -> CodeLanguage(PythonKeywords, "#", hashComment = true)
    language in setOf("js", "javascript", "jsx", "ts", "typescript", "tsx", "json5") ->
        CodeLanguage(JsKeywords, "//", blockComment = true)
    language in setOf("java") -> CodeLanguage(JavaKeywords, "//", blockComment = true)
    language in setOf("sh", "shell", "bash", "zsh", "fish", "console") ->
        CodeLanguage(ShellKeywords, "#", hashComment = true)
    language in setOf("sql", "postgres", "sqlite") ->
        CodeLanguage(SqlKeywords, "--", dashComment = true, blockComment = true)
    language in setOf("json") -> CodeLanguage(lineComment = null)
    else -> CodeLanguage()
}

@Composable
private fun highlightCode(code: String, language: String): AnnotatedString {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = CodePalette(
        keyword = MaterialTheme.colorScheme.primary,
        string = if (dark) Color(0xFF86EFAC) else Color(0xFF15803D),
        number = if (dark) Color(0xFFFDBA74) else Color(0xFFB45309),
        comment = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        function = if (dark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
        punctuation = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        plain = MaterialTheme.colorScheme.onSurface,
    )
    val spec = languageSpec(language)
    return buildAnnotatedString {
        var i = 0
        while (i < code.length) {
            val ch = code[i]
            when {
                spec.blockComment && i + 1 < code.length && code[i] == '/' && code[i + 1] == '*' -> {
                    val end = code.indexOf("*/", i + 2).let { if (it < 0) code.length else it + 2 }
                    withStyle(SpanStyle(color = palette.comment)) { append(code.substring(i, end)) }
                    i = end
                }
                spec.lineComment != null && code.startsWith(spec.lineComment, i) -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    withStyle(SpanStyle(color = palette.comment)) { append(code.substring(i, end)) }
                    i = end
                }
                spec.hashComment && ch == '#' -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    withStyle(SpanStyle(color = palette.comment)) { append(code.substring(i, end)) }
                    i = end
                }
                ch == '"' || ch == '\'' || ch == '`' -> {
                    val end = stringEnd(code, i)
                    val text = code.substring(i, end)
                    val isJsonKey = language == "json" && nextNonSpace(code, end) == ':'
                    withStyle(SpanStyle(color = if (isJsonKey) palette.keyword else palette.string)) {
                        append(text)
                    }
                    i = end
                }
                ch.isDigit() || (ch == '.' && i + 1 < code.length && code[i + 1].isDigit()) -> {
                    val end = numberEnd(code, i)
                    withStyle(SpanStyle(color = palette.number)) { append(code.substring(i, end)) }
                    i = end
                }
                ch.isLetter() || ch == '_' -> {
                    val end = identifierEnd(code, i)
                    val word = code.substring(i, end)
                    val color = when {
                        word in spec.keywords -> palette.keyword
                        word in setOf("true", "false", "null", "None", "True", "False") -> palette.number
                        nextNonSpace(code, end) == '(' -> palette.function
                        else -> palette.plain
                    }
                    withStyle(SpanStyle(color = color)) { append(word) }
                    i = end
                }
                else -> {
                    withStyle(SpanStyle(color = palette.punctuation)) { append(ch) }
                    i++
                }
            }
        }
    }
}

private data class CodePalette(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
    val punctuation: Color,
    val plain: Color,
)

private fun String.startsWith(prefix: String, start: Int): Boolean =
    start + prefix.length <= length && regionMatches(start, prefix, 0, prefix.length)

private fun stringEnd(text: String, start: Int): Int {
    val quote = text[start]
    var i = start + 1
    while (i < text.length) {
        if (text[i] == '\\') {
            i += 2
            continue
        }
        if (text[i] == quote) return i + 1
        i++
    }
    return text.length
}

private fun numberEnd(text: String, start: Int): Int {
    var i = start
    while (i < text.length && (text[i].isLetterOrDigit() || text[i] in "._+-")) i++
    return i
}

private fun identifierEnd(text: String, start: Int): Int {
    var i = start + 1
    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
    return i
}

private fun nextNonSpace(text: String, start: Int): Char? {
    var i = start
    while (i < text.length && text[i].isWhitespace()) i++
    return text.getOrNull(i)
}
