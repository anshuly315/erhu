package org.simpmusic.radio.local

import android.content.Context
import com.maxrave.domain.mediaservice.automix.TransitionOverrides
import org.json.JSONObject

/** Saves Transition Studio's overrides on the phone and loads them back at app start. */
object TransitionStore {
    private const val PREFS = "transition_studio"

    fun install(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        TransitionOverrides.load(read(prefs.getString("pairs", null)), read(prefs.getString("collections", null)))
        TransitionOverrides.onChanged = {
            prefs.edit()
                .putString("pairs", write(TransitionOverrides.snapshotPairs()))
                .putString("collections", write(TransitionOverrides.snapshotCollections()))
                .apply()
        }
    }

    private fun read(json: String?): Map<String, Int> =
        runCatching {
            val o = JSONObject(json ?: return emptyMap())
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())

    private fun write(map: Map<String, Int>): String = JSONObject(map).toString()
}
