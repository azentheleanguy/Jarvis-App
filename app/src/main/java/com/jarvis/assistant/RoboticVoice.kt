package com.jarvis.assistant

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Simple ring-modulation effect applied to a 16-bit PCM WAV file,
 * giving TTS output an electronic / robotic timbre. This is a DSP
 * effect, not a clone of any specific character's voice.
 */
object RoboticVoice {

    fun applyRingMod(inputFile: File, outputFile: File, carrierHz: Double = 38.0, mixAmount: Double = 0.55) {
        val raf = RandomAccessFile(inputFile, "r")
        val bytes = ByteArray(raf.length().toInt())
        raf.readFully(bytes)
        raf.close()

        val bitsPerSample = readShortLE(bytes, 34)
        val sampleRate = readIntLE(bytes, 24)
        val dataOffset = findDataChunk(bytes)

        if (dataOffset == -1 || bitsPerSample != 16) {
            outputFile.writeBytes(bytes)
            return
        }

        val out = bytes.copyOf()
        var sampleIndex = 0
        var i = dataOffset
        while (i + 1 < bytes.size) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt()
            val sample = (hi shl 8) or lo
            val t = sampleIndex.toDouble() / sampleRate
            val carrier = cos(2.0 * PI * carrierHz * t)
            val modulated = sample * (1.0 - mixAmount) + (sample * carrier) * mixAmount
            val clamped = modulated.roundToInt().coerceIn(-32768, 32767)
            out[i] = (clamped and 0xFF).toByte()
            out[i + 1] = ((clamped shr 8) and 0xFF).toByte()
            i += 2
            sampleIndex++
        }
        outputFile.writeBytes(out)
    }

    private fun readIntLE(b: ByteArray, offset: Int): Int {
        return (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readShortLE(b: ByteArray, offset: Int): Int {
        return (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun findDataChunk(b: ByteArray): Int {
        var i = 12
        while (i + 8 <= b.size) {
            val id = String(b, i, 4, Charsets.US_ASCII)
            val size = readIntLE(b, i + 4)
            if (id == "data") return i + 8
            i += 8 + size
        }
        return -1
    }
}
