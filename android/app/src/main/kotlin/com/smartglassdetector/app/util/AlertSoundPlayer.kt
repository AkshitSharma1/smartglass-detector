package com.smartglassdetector.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

class AlertSoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    @Synchronized
    fun playLooping(soundUri: String): Boolean = play(soundUri, looping = true)

    @Synchronized
    fun preview(soundUri: String): Boolean = play(soundUri, looping = false)

    @Synchronized
    fun stop() {
        val activePlayer = player ?: return
        player = null
        try {
            activePlayer.stop()
        } catch (_: IllegalStateException) {
            // The player had already completed or failed.
        } finally {
            activePlayer.reset()
            activePlayer.release()
        }
    }

    private fun play(soundUri: String, looping: Boolean): Boolean {
        stop()
        if (soundUri.isEmpty()) {
            return false
        }

        val newPlayer = MediaPlayer()
        return try {
            newPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            newPlayer.setDataSource(appContext, Uri.parse(soundUri))
            newPlayer.isLooping = looping
            newPlayer.setOnCompletionListener { completedPlayer ->
                synchronized(this) {
                    if (player === completedPlayer) {
                        player = null
                    }
                    completedPlayer.reset()
                    completedPlayer.release()
                }
            }
            newPlayer.setOnErrorListener { failedPlayer, _, _ ->
                synchronized(this) {
                    if (player === failedPlayer) {
                        player = null
                    }
                    failedPlayer.reset()
                    failedPlayer.release()
                }
                true
            }
            newPlayer.prepare()
            player = newPlayer
            newPlayer.start()
            true
        } catch (_: Exception) {
            try {
                newPlayer.reset()
                newPlayer.release()
            } catch (_: Exception) {
                // Ignore secondary cleanup failures.
            }
            false
        }
    }
}
