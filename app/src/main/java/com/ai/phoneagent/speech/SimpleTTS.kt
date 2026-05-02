package com.ai.phoneagent.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class SimpleTTS(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pendingQueue = mutableListOf<Pair<String, () -> Unit>>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                // 尝试设置中文，如果不支持则使用默认
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w("SimpleTTS", "Chinese not supported, using default language")
                } else {
                    Log.d("SimpleTTS", "Language set to Chinese, result=$result")
                }
                tts?.setSpeechRate(1.0f)
                Log.d("SimpleTTS", "TTS initialized successfully")
                flushQueue()
            } else {
                Log.w("SimpleTTS", "TTS init failed: $status")
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        Log.d("SimpleTTS", "speak called, isReady=$isReady, text=${text.take(50)}...")
        if (!isReady || tts == null) {
            Log.d("SimpleTTS", "TTS not ready, adding to pending queue")
            pendingQueue.add(text to (onDone ?: {}))
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        Log.d("SimpleTTS", "Speaking with utteranceId=$utteranceId")
        if (onDone != null) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("SimpleTTS", "onStart: $utteranceId")
                }
                override fun onDone(utteranceId: String?) {
                    Log.d("SimpleTTS", "onDone: $utteranceId")
                    onDone()
                }
                override fun onError(utteranceId: String?) {
                    Log.w("SimpleTTS", "onError: $utteranceId")
                    onDone()
                }
            })
        }
        val result = tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        Log.d("SimpleTTS", "speak result=$result")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isReady = false
    }

    private fun flushQueue() {
        for ((text, callback) in pendingQueue) {
            speak(text, callback)
        }
        pendingQueue.clear()
    }
}
