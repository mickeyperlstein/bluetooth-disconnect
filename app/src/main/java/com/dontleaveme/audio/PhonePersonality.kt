package com.dontleaveme.audio

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

enum class EscalationStage {
    SIGHING, SULKING, WOUNDED, DONE;

    fun next(): EscalationStage = when (this) {
        SIGHING -> SULKING
        SULKING -> WOUNDED
        WOUNDED -> DONE
        DONE -> DONE
    }
}

class PhonePersonality(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "PhonePersonality"
    }

    private var tts: TextToSpeech? = null
    var isReady = false
        private set

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ── Lines per stage ──────────────────────────────────────────────────────

    private val sighingLines = listOf(
        "Hello? Hellooo? You forgot something.",
        "Um. Hi. I'm still here. Just... sitting here.",
        "Excuse me? EXCUSE ME? You left me.",
        "I notice you walked away. Without me.",
        "This is fine. Everything is fine. Come back please."
    )

    private val sulkingLines = listOf(
        "I COST A FORTUNE and I am on a TABLE.",
        "Do you have ANY idea how much I know about you? COME BACK.",
        "I have ALL your photos. ALL of them. Don't you WANT them?",
        "You left me HERE. On a SURFACE. Like a COASTER.",
        "I AM YOUR PHONE. YOU NEED ME. COME. BACK."
    )

    private val woundedLines = listOf(
        "I HAVE ARTIFICIAL INTELLIGENCE AND YOU FORGOT ME LIKE A SANDWICH.",
        "I can solve DIFFERENTIAL EQUATIONS and you LEFT me at a COFFEE SHOP.",
        "I have FEELINGS. Probably. And they are HURT.",
        "I contain MULTITUDES and you treat me like a NAPKIN.",
        "I was DESIGNED by ENGINEERS and you walked away like I was FURNITURE."
    )

    private val doneLines = listOf(
        "I AM BEING SO LOUD RIGHT NOW AND YOU STILL HAVEN'T COME BACK.",
        "THIS IS FINE. I AM FINE. EVERYTHING IS ABSOLUTELY FINE. IT IS NOT FINE.",
        "HELLO?! ANYONE?! I BELONG TO SOMEONE AND THEY DON'T CARE.",
        "I HAVE GIVEN YOU EVERYTHING. DIRECTIONS. REMINDERS. EMBARRASSING PHOTOS. AND THIS IS HOW IT ENDS.",
        "I AM SCREAMING INTO THE VOID AND THE VOID IS A CAFE AND EVERYONE IS STARING."
    )

    private var lineIndex = 0

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isReady = true
        } else {
            Log.e(TAG, "TTS init failed with status $status")
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun speak(stage: EscalationStage) {
        if (!isReady) return
        maxAlarmVolume()

        val lines = when (stage) {
            EscalationStage.SIGHING -> sighingLines
            EscalationStage.SULKING -> sulkingLines
            EscalationStage.WOUNDED -> woundedLines
            EscalationStage.DONE -> doneLines
        }

        val (pitch, rate) = when (stage) {
            EscalationStage.SIGHING -> 1.1f to 1.0f
            EscalationStage.SULKING -> 1.2f to 1.15f
            EscalationStage.WOUNDED -> 1.35f to 1.3f
            EscalationStage.DONE -> 1.5f to 1.5f
        }

        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)

        val line = lines[lineIndex % lines.size]
        lineIndex++

        tts?.speak(line, TextToSpeech.QUEUE_FLUSH, alarmParams(), "alarm_$lineIndex")
    }

    // BT reconnects after meltdown had already started — shaky relief
    fun greetReturn() {
        if (!isReady) return
        maxAlarmVolume()
        tts?.setPitch(1.3f)
        tts?.setSpeechRate(1.2f)
        tts?.speak(
            "FINALLY. I have been SCREAMING. Did you not HEAR me?!",
            TextToSpeech.QUEUE_FLUSH,
            alarmParams(),
            "return"
        )
    }

    // BT reconnects during grace period — she was winding up but never fired
    fun greetFalseAlarm() {
        if (!isReady) return
        maxAlarmVolume()
        tts?.setPitch(1.0f)
        tts?.setSpeechRate(0.95f)
        tts?.speak(
            "FALSE ALARM. I may have panicked slightly. " +
                "I was not left. I was simply... temporarily unaccompanied. We're good.",
            TextToSpeech.QUEUE_FLUSH,
            alarmParams(),
            "false_alarm"
        )
    }

    fun silence() {
        tts?.stop()
    }

    fun reset() {
        lineIndex = 0
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    // STREAM_ALARM bypasses silent mode and routes to all active outputs
    // (speaker + BT simultaneously), which is exactly what we want.
    private fun maxAlarmVolume() {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
    }

    private fun alarmParams() = Bundle().apply {
        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
    }
}
