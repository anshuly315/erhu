package org.simpmusic.radio.data

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads what a Shoutcast/Icecast station says is playing ("Artist - Title"). The stream is asked to
 * interleave metadata, the first block is read and the connection is closed again, so a check costs
 * about one metadata interval (typically 16 KB) and is safe to repeat every half minute.
 */
object IcyReader {
    suspend fun nowPlaying(streamUrl: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(streamUrl).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 6000
                    c.readTimeout = 8000
                    c.setRequestProperty("Icy-MetaData", "1")
                    c.setRequestProperty("User-Agent", "SimpMusic-Radio/1.0")
                    val interval = c.getHeaderField("icy-metaint")?.toIntOrNull() ?: return@runCatching null
                    if (interval <= 0) return@runCatching null
                    val input = c.inputStream
                    skipFully(input, interval)
                    val length = input.read() * 16
                    if (length <= 0) return@runCatching null
                    val buf = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val n = input.read(buf, read, length - read)
                        if (n < 0) break
                        read += n
                    }
                    val text = String(buf, 0, read, Charsets.UTF_8).trim { it == '\u0000' || it.isWhitespace() }
                    Regex("StreamTitle='(.*?)';").find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                } finally {
                    c.disconnect()
                }
            }.getOrNull()
        }

    private fun skipFully(
        input: InputStream,
        count: Int,
    ) {
        var left = count.toLong()
        val scratch = ByteArray(4096)
        while (left > 0) {
            val n = input.read(scratch, 0, minOf(scratch.size.toLong(), left).toInt())
            if (n < 0) throw java.io.EOFException()
            left -= n
        }
    }
}
