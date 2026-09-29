package com.optisuite.optiplay.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaButtonReceiver

/**
 * Botón play de auriculares/Bluetooth con la app cerrada. Solo arranca el servicio (vía el receptor
 * de Media3) si hay una cola guardada que reanudar; si no, Android podría cerrar la app por no
 * iniciar la reproducción a tiempo.
 */
class ResumeButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (QueueStore(context).load() == null) return
        MediaButtonReceiver().onReceive(context, intent)
    }
}
