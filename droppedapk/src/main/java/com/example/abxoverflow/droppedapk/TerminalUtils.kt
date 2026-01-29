package com.example.abxoverflow.droppedapk

/**
 * Truncate UTF-8 string to at most `capBytes` bytes by keeping the tail portion and
 * prefixing a small truncation notice. Ensures we don't split multibyte UTF-8 sequences.
 */
fun truncatedOutputForSave(rawOutput: String, capBytes: Int): String {
    val rawBytes = rawOutput.toByteArray(Charsets.UTF_8)
    if (rawBytes.size <= capBytes) return rawOutput

    val notice = "[...output truncated: showing last ${capBytes} bytes]\n"
    val noticeBytes = notice.toByteArray(Charsets.UTF_8)

    var start = rawBytes.size - capBytes
    // Advance start while it's a UTF-8 continuation byte (10xxxxxx)
    while (start < rawBytes.size && (rawBytes[start].toInt() and 0xC0) == 0x80) {
        start++
    }
    if (start >= rawBytes.size) {
        // Fallback: take the last capBytes bytes
        start = rawBytes.size - capBytes
        if (start < 0) start = 0
    }

    val tailBytes = rawBytes.copyOfRange(start, rawBytes.size)
    val combined = ByteArray(noticeBytes.size + tailBytes.size)
    System.arraycopy(noticeBytes, 0, combined, 0, noticeBytes.size)
    System.arraycopy(tailBytes, 0, combined, noticeBytes.size, tailBytes.size)
    return String(combined, Charsets.UTF_8)
}
