package ir.zabtyar

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/**
 * Writes mono 16-bit PCM data to a standard WAV file.
 */
class WavWriter(
    private val file: File,
    private val sampleRate: Int = 16_000,
    private val channels: Short = 1,
    private val bitsPerSample: Short = 16
) : Closeable {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0L

    init {
        raf.setLength(0)
        raf.write(ByteArray(44))
    }

    @Synchronized
    fun write(data: ByteArray, length: Int) {
        if (length <= 0) return
        raf.write(data, 0, length)
        dataBytes += length
    }

    override fun close() {
        synchronized(this) {
            raf.seek(0)
            raf.writeBytes("RIFF")
            writeIntLE((36 + dataBytes).coerceAtMost(0xFFFFFFFFL))
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            writeIntLE(16)
            writeShortLE(1)
            writeShortLE(channels.toInt())
            writeIntLE(sampleRate.toLong())
            val byteRate = sampleRate.toLong() * channels * bitsPerSample / 8
            writeIntLE(byteRate)
            writeShortLE((channels * bitsPerSample / 8).toInt())
            writeShortLE(bitsPerSample.toInt())
            raf.writeBytes("data")
            writeIntLE(dataBytes.coerceAtMost(0xFFFFFFFFL))
            raf.fd.sync()
            raf.close()
        }
    }

    private fun writeIntLE(value: Long) {
        raf.write((value and 0xff).toInt())
        raf.write(((value shr 8) and 0xff).toInt())
        raf.write(((value shr 16) and 0xff).toInt())
        raf.write(((value shr 24) and 0xff).toInt())
    }

    private fun writeShortLE(value: Int) {
        raf.write(value and 0xff)
        raf.write((value shr 8) and 0xff)
    }
}
