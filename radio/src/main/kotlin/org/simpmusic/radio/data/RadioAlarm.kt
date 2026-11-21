package org.simpmusic.radio.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Wake-up alarm that starts a radio station. Uses the system alarm clock API, which needs no special
 * permission and survives Doze. One alarm at a time, optionally repeating every day.
 */
object RadioAlarm {
    /** Set once at app start so the receiver can reach the player when the app was not running. */
    @Volatile
    var handlerProvider: (() -> MediaPlayerHandler)? = null

    data class Alarm(val hour: Int, val minute: Int, val daily: Boolean, val station: RadioStation)

    private const val PREFS = "radio_alarm"
    private const val KEY = "alarm"
    const val ACTION = "org.simpmusic.radio.ALARM"
    private const val CHANNEL = "radio_alarm"

    fun current(context: Context): Alarm? =
        runCatching {
            val o = JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null)
            Alarm(o.getInt("h"), o.getInt("m"), o.optBoolean("daily"), RadioStation.fromJson(o.getJSONObject("station")))
        }.getOrNull()

    fun set(
        context: Context,
        alarm: Alarm,
    ) {
        val o = JSONObject().put("h", alarm.hour).put("m", alarm.minute).put("daily", alarm.daily).put("station", alarm.station.toJson())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
        schedule(context, alarm)
    }

    fun cancel(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context))
    }

    /** Re-arms the saved alarm, e.g. after it fired with "every day" on. */
    fun reschedule(context: Context) {
        current(context)?.let { schedule(context, it) }
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            4711,
            Intent(context, RadioAlarmReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun schedule(
        context: Context,
        alarm: Alarm,
    ) {
        val cal =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(context, 4712, context.packageManager.getLaunchIntentForPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis, show), pending(context))
    }

    internal fun fire(context: Context) {
        val alarm = current(context) ?: return
        if (!alarm.daily) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        } else {
            schedule(context, alarm)
        }
        val handler = runCatching { handlerProvider?.invoke() }.getOrNull()
        if (handler != null) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            scope.launch {
                runCatching { RadioPlayer(context, handler, RadioBrowserApi(), RadioStore(context)).play(listOf(alarm.station), 0, "Alarm") }
                    .onFailure { notify(context, alarm) }
            }
        } else {
            notify(context, alarm)
        }
    }

    private fun notify(
        context: Context,
        alarm: Alarm,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Radio alarm", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, 4713, context.packageManager.getLaunchIntentForPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            4711,
            android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Radio alarm")
                .setContentText("Tap to play ${alarm.station.name}")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}

class RadioAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == RadioAlarm.ACTION) RadioAlarm.fire(context.applicationContext)
    }
}
