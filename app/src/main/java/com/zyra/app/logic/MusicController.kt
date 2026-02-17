package com.zyra.app.logic

import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal Music Controller
 * Controls any music app using Android MediaSession
 * Works with: Spotify, YouTube Music, Apple Music, etc.
 */
object MusicController {
    
    /**
     * Play next track
     */
    suspend fun nextTrack(context: Context): Boolean {
        return sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    }
    
    /**
     * Play previous track
     */
    suspend fun previousTrack(context: Context): Boolean {
        return sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }
    
    /**
     * Pause playback
     */
    suspend fun pause(context: Context): Boolean {
        return sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
    }
    
    /**
     * Resume playback
     */
    suspend fun play(context: Context): Boolean {
        return sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY)
    }
    
    /**
     * Toggle play/pause
     */
    suspend fun playPause(context: Context): Boolean {
        return sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }
    
    /**
     * Get currently playing track info
     */
    suspend fun getCurrentTrack(context: Context): TrackInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val controllers = mediaSessionManager.getActiveSessions(null)
                
                if (controllers.isNotEmpty()) {
                    val controller = controllers[0]
                    val metadata = controller.metadata
                    
                    if (metadata != null) {
                        TrackInfo(
                            title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown",
                            artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown",
                            album = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM) ?: "Unknown"
                        )
                    } else null
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Check if music is currently playing
     */
    suspend fun isPlaying(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val controllers = mediaSessionManager.getActiveSessions(null)
                
                if (controllers.isNotEmpty()) {
                    val controller = controllers[0]
                    val state = controller.playbackState
                    state?.state == android.media.session.PlaybackState.STATE_PLAYING
                } else false
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Send media key event to active music app
     */
    private suspend fun sendMediaKeyEvent(context: Context, keyCode: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                
                // Send key down event
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                audioManager.dispatchMediaKeyEvent(downEvent)
                
                // Send key up event
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                audioManager.dispatchMediaKeyEvent(upEvent)
                
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    data class TrackInfo(
        val title: String,
        val artist: String,
        val album: String
    )
}
