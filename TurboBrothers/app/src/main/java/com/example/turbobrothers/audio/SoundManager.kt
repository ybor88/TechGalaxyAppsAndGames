package com.example.turbobrothers.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.annotation.RawRes
import com.example.turbobrothers.R

enum class Sfx { JUMP, COLLECT, POWERUP, HIT, GAME_OVER, BUTTON, NEW_RECORD }

/** Effetti sonori brevi (SoundPool) e musica di sottofondo per livello (MediaPlayer), tutti sintetizzati via codice. */
object SoundManager {
    private var appContext: Context? = null
    private var soundPool: SoundPool? = null
    private val sfxIds = mutableMapOf<Sfx, Int>()
    private var musicPlayer: MediaPlayer? = null
    private var currentMusicRes: Int? = null
    private var musicWasPlaying = false

    fun init(context: Context) {
        appContext = context.applicationContext
        if (soundPool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()
        soundPool = pool

        val app = context.applicationContext
        sfxIds[Sfx.JUMP] = pool.load(app, R.raw.sfx_jump, 1)
        sfxIds[Sfx.COLLECT] = pool.load(app, R.raw.sfx_collect, 1)
        sfxIds[Sfx.POWERUP] = pool.load(app, R.raw.sfx_powerup, 1)
        sfxIds[Sfx.HIT] = pool.load(app, R.raw.sfx_hit, 1)
        sfxIds[Sfx.GAME_OVER] = pool.load(app, R.raw.sfx_gameover, 1)
        sfxIds[Sfx.BUTTON] = pool.load(app, R.raw.sfx_button, 1)
        sfxIds[Sfx.NEW_RECORD] = pool.load(app, R.raw.sfx_newrecord, 1)
    }

    fun playSfx(sfx: Sfx) {
        val pool = soundPool ?: return
        val id = sfxIds[sfx] ?: return
        pool.play(id, 0.9f, 0.9f, 1, 0, 1f)
    }

    fun playMusic(@RawRes musicRes: Int) {
        if (currentMusicRes == musicRes && musicPlayer?.isPlaying == true) return
        val context = appContext ?: return
        stopMusic()
        val player = MediaPlayer.create(context, musicRes) ?: return
        player.isLooping = true
        player.setVolume(0.35f, 0.35f)
        player.start()
        musicPlayer = player
        currentMusicRes = musicRes
        musicWasPlaying = true
    }

    fun pauseMusic() {
        musicPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                musicWasPlaying = true
            }
        }
    }

    fun resumeMusic() {
        if (musicWasPlaying) {
            musicPlayer?.start()
        }
    }

    fun stopMusic() {
        musicPlayer?.release()
        musicPlayer = null
        currentMusicRes = null
    }

    fun release() {
        stopMusic()
        soundPool?.release()
        soundPool = null
        sfxIds.clear()
    }
}
