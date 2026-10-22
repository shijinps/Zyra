package com.zyra.app.logic.wakeword

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.util.concurrent.atomic.AtomicBoolean

class SherpaWakeWordEngine(private val context: Context) : WakeWordEngine {
    companion object {
        private const val TAG = "SherpaWakeWord"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SIZE = 1600 // 100ms at 16kHz
        private const val ENERGY_THRESHOLD = 0.003 // VAD energy gate to save battery
    }

    override val name: String = "Sherpa-ONNX (Offline KWS)"

    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var gainControl: AutomaticGainControl? = null

    private val _isRunning = AtomicBoolean(false)
    override val isRunning: Boolean
        get() = _isRunning.get() && spotter != null

    private var currentListener: WakeWordListener? = null

    override fun initialize(listener: WakeWordListener): Result<Unit> {
        this.currentListener = listener
        return try {
            val modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "sherpa-onnx-kws/encoder.int8.onnx",
                    decoder = "sherpa-onnx-kws/decoder.onnx",
                    joiner = "sherpa-onnx-kws/joiner.int8.onnx"
                ),
                tokens = "sherpa-onnx-kws/tokens.txt",
                numThreads = 2,
                provider = "cpu",
                modelType = "zipformer2"
            )

            val featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = 80
            )

            val spotterConfig = KeywordSpotterConfig(
                featConfig = featConfig,
                modelConfig = modelConfig,
                keywordsFile = "sherpa-onnx-kws/keywords.txt"
            )

            Log.d(TAG, "Initializing Sherpa-ONNX KeywordSpotter from assets...")
            spotter = KeywordSpotter(assetManager = context.assets, config = spotterConfig)
            stream = spotter?.createStream()

            Log.d(TAG, "Sherpa-ONNX KeywordSpotter initialized successfully (100% offline).")
            Result.success(Unit)
        } catch (e: Throwable) {
            spotter = null
            stream = null
            _isRunning.set(false)
            Log.e(TAG, "Failed to initialize Sherpa-ONNX: ${e.message}", e)
            listener.onError(e, isFatal = true, readableMessage = "Sherpa-ONNX init failed: ${e.message}")
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        if (_isRunning.get()) {
            Log.d(TAG, "Already listening, ignoring start()")
            return
        }

        val kws = spotter
        if (kws == null) {
            Log.e(TAG, "Cannot start: spotter not initialized")
            return
        }

        try {
            val minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufSize, CHUNK_SIZE * 2)

            // VOICE_RECOGNITION enables hardware tuning for speech
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord failed to initialize")
            }

            audioRecord = record
            val sessionId = record.audioSessionId

            // Hardware DSP filters
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }

            record.startRecording()
            _isRunning.set(true)

            recordingThread = Thread({
                processAudioLoop(record, kws)
            }, "SherpaWakeWord-AudioLoop").apply {
                priority = Thread.NORM_PRIORITY + 1
                start()
            }

            Log.d(TAG, "Sherpa-ONNX wake word listening started.")
        } catch (e: Throwable) {
            _isRunning.set(false)
            stop()
            Log.e(TAG, "Failed to start audio recording: ${e.message}", e)
            currentListener?.onError(e, isFatal = false, readableMessage = "Microphone error: ${e.message}")
        }
    }

    private fun processAudioLoop(record: AudioRecord, kws: KeywordSpotter) {
        val shortBuffer = ShortArray(CHUNK_SIZE)
        val floatBuffer = FloatArray(CHUNK_SIZE)
        val s = stream ?: kws.createStream().also { stream = it }

        while (_isRunning.get()) {
            val readCount = record.read(shortBuffer, 0, CHUNK_SIZE)
            if (readCount <= 0) continue

            // 1. Calculate RMS energy & normalize samples to [-1.0, 1.0]
            var sumSq = 0.0
            for (i in 0 until readCount) {
                val sample = shortBuffer[i] / 32768.0f
                floatBuffer[i] = sample
                sumSq += sample * sample
            }
            val rms = Math.sqrt(sumSq / readCount)

            // 2. Battery saver: skip inference if silent or background hum below threshold
            if (rms < ENERGY_THRESHOLD) {
                continue
            }

            // 3. Human speech detected -> feed to Sherpa-ONNX KeywordSpotter
            s.acceptWaveform(floatBuffer, SAMPLE_RATE)

            while (kws.isReady(s)) {
                kws.decode(s)
                val result = kws.getResult(s)
                if (result.keyword.isNotEmpty()) {
                    Log.d(TAG, "Sherpa-ONNX matched keyword: [${result.keyword}]")
                    kws.reset(s)
                    currentListener?.onWakeWordDetected(0)
                }
            }
        }
    }

    override fun stop() {
        _isRunning.set(false)
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
            audioRecord = null

            noiseSuppressor?.release()
            noiseSuppressor = null
            echoCanceler?.release()
            echoCanceler = null
            gainControl?.release()
            gainControl = null

            recordingThread?.interrupt()
            recordingThread = null

            stream?.let {
                spotter?.reset(it)
            }

            Log.d(TAG, "Sherpa-ONNX stopped.")
        } catch (e: Throwable) {
            Log.e(TAG, "Error stopping Sherpa-ONNX: ${e.message}", e)
        }
    }

    override fun destroy() {
        stop()
        try {
            stream?.release()
            stream = null
            spotter?.release()
            spotter = null
            Log.d(TAG, "Sherpa-ONNX destroyed.")
        } catch (e: Throwable) {
            Log.e(TAG, "Error destroying Sherpa-ONNX: ${e.message}", e)
        }
    }
}
