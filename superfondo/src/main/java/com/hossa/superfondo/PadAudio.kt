package com.hossa.superfondo

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Audio mono en 16 bits. */
class Pcm(val data: ShortArray, val sampleRate: Int) {
    val seconds: Float get() = data.size.toFloat() / sampleRate
}

/** Decodifica cualquier audio que Android entienda (MP3, M4A, AAC, OGG, WAV, FLAC...) a PCM mono. */
object AudioDecoder {
    fun decode(ctx: Context, uri: Uri, maxSeconds: Int = 480): Pcm {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { track = i; format = f; break }
        }
        if (track < 0 || format == null) { ex.release(); throw IllegalArgumentException("Sin pista de audio") }
        ex.selectTrack(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        var sr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var isFloat = false
        var out = ShortArray(sr * 60)
        var n = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val ob = codec.getOutputBuffer(outIdx)!!
                    ob.position(info.offset)
                    ob.limit(info.offset + info.size)
                    ob.order(ByteOrder.nativeOrder())
                    val chs = max(1, ch)
                    if (isFloat) {
                        val fb = ob.asFloatBuffer()
                        val frames = fb.remaining() / chs
                        if (n + frames > out.size) out = out.copyOf(max(out.size * 2, n + frames))
                        for (f in 0 until frames) {
                            var s = 0f
                            for (c in 0 until chs) s += fb.get()
                            out[n++] = (s / chs * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                        }
                    } else {
                        val sb = ob.asShortBuffer()
                        val frames = sb.remaining() / chs
                        if (n + frames > out.size) out = out.copyOf(max(out.size * 2, n + frames))
                        for (f in 0 until frames) {
                            var s = 0
                            for (c in 0 until chs) s += sb.get()
                            out[n++] = (s / chs).toShort()
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (n > sr * maxSeconds) outputDone = true
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
            }
        } finally {
            try { codec.stop() } catch (e: Exception) { }
            codec.release()
            ex.release()
        }
        if (n < sr / 2) throw IllegalArgumentException("Audio demasiado corto")
        return Pcm(out.copyOf(n), sr)
    }
}

/** Un corte de la canción: inicio y largo en muestras. */
class Slice(val start: Int, val length: Int)

object Slicer {
    fun peaks(p: Pcm, count: Int): FloatArray {
        val out = FloatArray(count)
        val step = max(1, p.data.size / count)
        for (i in 0 until count) {
            var m = 0
            var j = i * step
            val end = min(p.data.size, (i + 1) * step)
            while (j < end) { val a = abs(p.data[j].toInt()); if (a > m) m = a; j += 16 }
            out[i] = m / 32768f
        }
        return out
    }

    fun ritmo(p: Pcm, startSec: Float, lenSec: Float): List<Slice> {
        val start = (startSec * p.sampleRate).toInt().coerceIn(0, p.data.size - 1)
        val total = min((lenSec * p.sampleRate).toInt(), p.data.size - start)
        val each = total / 16
        return (0 until 16).map { Slice(start + it * each, each) }
    }

    fun golpes(p: Pcm, count: Int = 16): List<Slice> {
        val hop = 512
        val frames = p.data.size / hop
        val e = FloatArray(frames)
        for (i in 0 until frames) {
            var s = 0.0
            for (j in i * hop until i * hop + hop) { val v = p.data[j] / 32768.0; s += v * v }
            e[i] = sqrt(s / hop).toFloat()
        }
        val flux = FloatArray(frames)
        for (i in 1 until frames) flux[i] = max(0f, e[i] - e[i - 1])
        val w = 8
        val cand = ArrayList<Pair<Float, Float>>() // tiempo, fuerza
        for (i in w until frames - w) {
            var isMax = flux[i] > 0f
            if (isMax) for (k in -w..w) if (flux[i + k] > flux[i]) { isMax = false; break }
            if (isMax) cand.add(Pair(i * hop.toFloat() / p.sampleRate, flux[i]))
        }
        cand.sortByDescending { it.second }
        var gap = max(0.6f, p.seconds / 40f)
        var picked: List<Float>
        while (true) {
            val list = ArrayList<Float>()
            for ((t, _) in cand) {
                if (list.all { abs(it - t) > gap }) list.add(t)
                if (list.size >= count) break
            }
            picked = list
            if (list.size >= count || gap < 0.08f) break
            gap /= 2f
        }
        val times = picked.sorted().map { max(0f, it - 0.01f) }
        return times.mapIndexed { i, t ->
            val next = times.getOrNull(i + 1) ?: p.seconds
            val len = min(1.4f, next - t)
            Slice((t * p.sampleRate).toInt(), max(1, (len * p.sampleRate).toInt()))
        }
    }

    /** Recorta, aplica filtro y eco, normaliza y suaviza los bordes. */
    fun render(p: Pcm, s: Slice, cutoffHz: Float, echo: Float): ShortArray {
        val sr = p.sampleRate
        val tail = if (echo > 0.01f) (sr * 1.2f).toInt() else 0
        val len = min(s.length, p.data.size - s.start)
        val x = FloatArray(len + tail)
        for (i in 0 until len) x[i] = p.data[s.start + i] / 32768f

        if (cutoffHz < 18000f) {
            val w0 = 2.0 * PI * cutoffHz / sr
            val alpha = sin(w0) / (2 * 0.9)
            val cw = cos(w0)
            val a0 = 1 + alpha
            val b0 = ((1 - cw) / 2 / a0).toFloat(); val b1 = ((1 - cw) / a0).toFloat(); val b2 = b0
            val a1 = (-2 * cw / a0).toFloat(); val a2 = ((1 - alpha) / a0).toFloat()
            var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
            for (i in x.indices) {
                val xi = x[i]
                val y = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = xi; y2 = y1; y1 = y
                x[i] = y
            }
        }
        if (echo > 0.01f) {
            val d = (sr * 0.375f).toInt()
            val wet = echo * 0.7f
            val buf = x.copyOf()
            for (i in d until buf.size) buf[i] += buf[i - d] * 0.38f
            for (i in x.indices) x[i] = x[i] + (buf[i] - x[i]) * wet
        }
        var peak = 0f
        for (v in x) peak = max(peak, abs(v))
        val gain = if (peak > 0.0001f) min(4f, 0.92f / peak) else 1f
        val fadeIn = min(x.size / 4, (sr * 0.002f).toInt())
        val fadeOut = min(x.size / 4, (sr * 0.012f).toInt())
        val out = ShortArray(x.size)
        for (i in x.indices) {
            var g = gain
            if (i < fadeIn) g *= i.toFloat() / fadeIn
            val fromEnd = x.size - 1 - i
            if (fromEnd < fadeOut) g *= fromEnd.toFloat() / fadeOut
            out[i] = (x[i] * g * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
        }
        return out
    }

    /** Cambia la altura: factor 2 = una octava más aguda (y más corto). */
    fun pitch(src: ShortArray, factor: Float): ShortArray {
        val n = max(1, (src.size / factor).toInt())
        val out = ShortArray(n)
        for (i in 0 until n) {
            val pos = i * factor
            val a = pos.toInt().coerceAtMost(src.size - 1)
            val b = (a + 1).coerceAtMost(src.size - 1)
            val fr = pos - a
            out[i] = (src[a] * (1 - fr) + src[b] * fr).toInt().toShort()
        }
        return out
    }

    fun readWav(bytes: ByteArray): Pcm {
        val bb = java.nio.ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var sr = 44100
        var channels = 1
        var bits = 16
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = bb.getInt(pos + 4)
            if (id == "fmt ") {
                channels = bb.getShort(pos + 10).toInt()
                sr = bb.getInt(pos + 12)
                bits = bb.getShort(pos + 22).toInt()
            } else if (id == "data" && bits == 16) {
                val frames = min(size, bytes.size - pos - 8) / (2 * channels)
                val out = ShortArray(frames)
                var p = pos + 8
                for (i in 0 until frames) {
                    var s = 0
                    for (c in 0 until channels) { s += bb.getShort(p).toInt(); p += 2 }
                    out[i] = (s / channels).toShort()
                }
                return Pcm(out, sr)
            }
            pos += 8 + size + (size and 1)
        }
        throw IllegalArgumentException("WAV no válido")
    }

    fun writeWav(file: File, data: ShortArray, sr: Int) {
        DataOutputStream(FileOutputStream(file).buffered()).use { o ->
            fun le32(v: Int) { o.write(v and 0xff); o.write(v shr 8 and 0xff); o.write(v shr 16 and 0xff); o.write(v shr 24 and 0xff) }
            fun le16(v: Int) { o.write(v and 0xff); o.write(v shr 8 and 0xff) }
            val bytes = data.size * 2
            o.writeBytes("RIFF"); le32(36 + bytes); o.writeBytes("WAVE")
            o.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(sr); le32(sr * 2); le16(2); le16(16)
            o.writeBytes("data"); le32(bytes)
            for (s in data) le16(s.toInt())
        }
    }
}

