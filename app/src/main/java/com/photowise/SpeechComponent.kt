package com.photowise

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber


class SpeechComponent(
    private val context: Context,
    delegate: BridgeDelegate<HotwireDestination>
) : BridgeComponent<HotwireDestination>("speech", delegate) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    data class SpeechMessageData(
        val action: String,
        val language: String? = null,
        val maxResults: Int? = null,
        val prompt: String? = null
    )

    data class SpeechResult(
        val transcript: String,
        val confidence: Float
    )

    override fun onReceive(message: Message) {

        try {
            val data = parseMessageData(message.jsonData)
            if (data == null) {
                sendErrorResponse(message, "Invalid message data format")
                return
            }

            when (data.action) {
                "startSpeechRecognition" -> startSpeechRecognition(message, data)
                "stopSpeechRecognition" -> stopSpeechRecognition(message)
                "isSpeechRecognitionAvailable" -> checkSpeechRecognitionAvailability(message)
                else -> sendErrorResponse(message, "Unknown action: ${data.action}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling speech message")
            sendErrorResponse(message, "Failed to handle speech message: ${e.message}")
        }
    }

    private fun parseMessageData(jsonData: String): SpeechMessageData? {
        return try {
            val json = JSONObject(jsonData)
            if (!json.has("action")) {
                return null
            }

            SpeechMessageData(
                action = json.getString("action"),
                language = if (json.has("language")) json.getString("language") else null,
                maxResults = if (json.has("maxResults")) json.getInt("maxResults") else null,
                prompt = if (json.has("prompt")) json.getString("prompt") else null
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing message data")
            null
        }
    }

    private fun startSpeechRecognition(originalMessage: Message, data: SpeechMessageData) {
        if (!hasPermissions()) {
            sendErrorResponse(originalMessage, "Speech recognition permissions not granted")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            sendErrorResponse(originalMessage, "Speech recognition not available")
            return
        }

        if (isListening) {
            stopSpeechRecognition(originalMessage)
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener(originalMessage))

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, data.language ?: "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, data.maxResults ?: 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                data.prompt?.let { putExtra(RecognizerIntent.EXTRA_PROMPT, it) }
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            sendEventResponse(originalMessage, "speechRecognitionStarted")
        } catch (e: Exception) {
            Timber.e(e, "Error starting speech recognition")
            sendErrorResponse(originalMessage, "Failed to start speech recognition: ${e.message}")
        }
    }

    private fun stopSpeechRecognition(originalMessage: Message) {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            sendEventResponse(originalMessage, "speechRecognitionStopped")
        }
    }

    private fun checkSpeechRecognitionAvailability(originalMessage: Message) {
        val isAvailable = hasPermissions() && SpeechRecognizer.isRecognitionAvailable(context)
        val jsonData = JSONObject().apply {
            put("event", "speechRecognitionAvailability")
            put("available", isAvailable)
        }.toString()
        replyWith(originalMessage.replacing(jsonData = jsonData))
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun createRecognitionListener(originalMessage: Message): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                sendEventResponse(originalMessage, "readyForSpeech")
            }

            override fun onBeginningOfSpeech() {
                sendEventResponse(originalMessage, "beginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                val jsonData = JSONObject().apply {
                    put("event", "rmsChanged")
                    put("rmsdB", rmsdB)
                }.toString()
                replyWith(originalMessage.replacing(jsonData = jsonData))
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Not typically used
            }

            override fun onEndOfSpeech() {
                sendEventResponse(originalMessage, "endOfSpeech")
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Unknown error"
                }
                isListening = false

                val jsonData = JSONObject().apply {
                    put("event", "error")
                    put("error", errorMessage)
                    put("errorCode", error)
                }.toString()
                replyWith(originalMessage.replacing(jsonData = jsonData))
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                val resultsArray = JSONArray()
                matches?.forEachIndexed { index, match ->
                    val conf = confidence?.getOrNull(index) ?: 0.0f
                    val result = JSONObject().apply {
                        put("transcript", match)
                        put("confidence", conf)
                    }
                    resultsArray.put(result)
                }

                isListening = false

                val jsonData = JSONObject().apply {
                    put("event", "results")
                    put("results", resultsArray)
                }.toString()
                replyWith(originalMessage.replacing(jsonData = jsonData))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                val resultsArray = JSONArray()
                matches?.forEach { match ->
                    val result = JSONObject().apply {
                        put("transcript", match)
                        put("confidence", 0.0f)
                    }
                    resultsArray.put(result)
                }

                val jsonData = JSONObject().apply {
                    put("event", "partialResults")
                    put("results", resultsArray)
                }.toString()
                replyWith(originalMessage.replacing(jsonData = jsonData))
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Not typically used
            }
        }
    }

    private fun sendEventResponse(originalMessage: Message, event: String) {
        val jsonData = JSONObject().apply {
            put("event", event)
        }.toString()
        replyWith(originalMessage.replacing(jsonData = jsonData))
    }

    private fun sendErrorResponse(originalMessage: Message, errorMessage: String) {
        val jsonData = JSONObject().apply {
            put("event", "error")
            put("error", errorMessage)
        }.toString()
        replyWith(originalMessage.replacing(jsonData = jsonData))
    }

    override fun onStop() {
        super.onStop()
        cleanup()
    }

    fun cleanup() {
        try {
            if (isListening) {
                speechRecognizer?.stopListening()
                isListening = false
            }
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up speech recognizer")
        }
    }
}