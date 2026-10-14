package ir.cheghadr.core.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shares exports (CSV / JSON) through the native Android share sheet
 * (blueprint §15). Files are written to a private cache directory and served
 * read-only through the app's FileProvider — nothing is uploaded anywhere.
 */
@Singleton
class ExportSharer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Writes [content] to a cached file and shares it as a document.
     * Returns false when file sharing is unavailable (the caller may then
     * fall back to [shareText]).
     */
    fun shareFile(fileName: String, mime: String, content: String, chooserTitle: String): Boolean {
        return runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(content, Charsets.UTF_8)
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    /** Plain-text share (fallback / simple summaries). */
    fun shareText(text: String, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
