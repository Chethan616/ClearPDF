package com.chethan616.clearpdf.notes

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile

private fun le16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

private fun le32(b: ByteArray, at: Int): Int = le16(b, at) or (le16(b, at + 2) shl 16)

/**
 * A voice note as other apps expect one: the recording (16-bit PCM in a WAV file, as the recorder
 * writes it) encoded as AAC in an MPEG-4 file (.m4a) by the phone's own encoder. A minute of
 * speech comes to about half a megabyte where the WAV is five.
 *
 * False when [wav] is not such a file or the phone can't encode it: the caller then shares the
 * recording as it is. Blocking; call it off the main thread.
 */
fun wavToM4a(wav: File, out: File): Boolean = runCatching {
    RandomAccessFile(wav, "r").use { f ->
        val head = ByteArray(12)
        f.readFully(head)
        if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF" || String(head, 8, 4, Charsets.US_ASCII) != "WAVE") return false
        var rate = 0
        var channels = 0
        var bits = 0
        var dataAt = -1L
        var dataLen = 0L
        val chunk = ByteArray(8)
        while (f.filePointer + 8 <= f.length()) {
            f.readFully(chunk)
            val id = String(chunk, 0, 4, Charsets.US_ASCII)
            val size = le32(chunk, 4).toLong() and 0xFFFFFFFFL
            when (id) {
                "fmt " -> {
                    if (size < 16) return false
                    val b = ByteArray(16)
                    f.readFully(b)
                    // 1: plain PCM
                    if (le16(b, 0) != 1) return false
                    channels = le16(b, 2)
                    rate = le32(b, 4)
                    bits = le16(b, 14)
                    f.seek(f.filePointer + size - 16 + (size and 1))
                }
                "data" -> {
                    dataAt = f.filePointer
                    // a recording cut short states more than it holds
                    dataLen = minOf(size, f.length() - dataAt)
                    break
                }
                else -> f.seek(f.filePointer + size + (size and 1))
            }
        }
        if (dataAt < 0 || dataLen <= 0 || bits != 16 || channels !in 1..2 || rate !in 8000..96000) return false

        val frame = channels * 2
        val bytesPerSecond = rate.toLong() * frame
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, if (channels == 1) 72_000 else 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer? = null
        var started = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val info = MediaCodec.BufferInfo()
            val buffer = ByteArray(8 * 1024)
            var track = -1
            var read = 0L
            var inputDone = false
            var outputDone = false
            // a stuck encoder must not hang the export
            var idle = 0
            f.seek(dataAt)
            while (!outputDone && idle < 2000) {
                idle++
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        idle = 0
                        val input = codec.getInputBuffer(i) ?: return false
                        input.clear()
                        // whole frames only
                        val want = (minOf(minOf(buffer.size, input.remaining()).toLong(), dataLen - read).toInt() / frame) * frame
                        val n = if (want > 0) f.read(buffer, 0, want) else -1
                        val time = read * 1_000_000L / bytesPerSecond
                        if (n <= 0) {
                            codec.queueInputBuffer(i, 0, 0, time, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            input.put(buffer, 0, n)
                            codec.queueInputBuffer(i, 0, n, time, 0)
                            read += n
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    idle = 0
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                } else if (o >= 0) {
                    idle = 0
                    val output = codec.getOutputBuffer(o)
                    // the codec's own setup data goes into the track's format, not the samples
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (output != null && info.size > 0 && started) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        muxer.writeSampleData(track, output, info)
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    codec.releaseOutputBuffer(o, false)
                }
            }
            started && outputDone
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }
}.getOrDefault(false).also { ok -> if (!ok) out.delete() }
