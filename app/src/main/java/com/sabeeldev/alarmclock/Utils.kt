package com.sabeeldev.alarmclock

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import androidx.core.app.NotificationCompat
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.sabeeldev.alarmclock.activities.MainActivity
import com.sabeeldev.alarmclock.database.Alarm
import com.sabeeldev.alarmclock.fragments.AlarmSetupFragment
import com.sabeeldev.alarmclock.receivers.AlarmReceiver
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

object Utils {

    private const val ALARM_NOTIFICATION_ID = 100
    private const val NOTIFICATION_REQUEST_CODE = 101
    var ADAPTER_CHECKER: String = "main"

    //also gets called when user presses delay button
    fun setSingleAlarm(alarm: Alarm, context: Context) {
        val alarmManager =
            context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = setupAlarmIntent(alarm.aid * 10, context)
        val alarmDate = getAlarmDateFromAlarm(alarm)
        alarmManager.set(AlarmManager.RTC_WAKEUP, alarmDate.timeInMillis, alarmIntent)
    }

    fun startAlarmSetupFragment(
        activity: AppCompatActivity,
        fragment: Fragment,
        arguments: Bundle?,
        name: String
    ) {
        if (arguments != null) fragment.arguments = arguments
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.empty_fragment_container, fragment)
            .addToBackStack(name)
            .commit()
    }

    fun getDate(): String {
        return SimpleDateFormat("E, dd MMM yyyy").format(Date())
    }

    fun setAlarmWithDays(alarm: Alarm, context: Context) {
        val alarmManager =
            context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager

        //sets several alarms
        alarm.days.forEach {
            if (it.value) {
                val alarmIntent =
                    setupAlarmIntent(alarm.aid * 10 + alarm.daysStringToCalendar[it.key]!!, context)
                val alarmDate = getAlarmDateFromAlarm(alarm, it.key)
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    alarmDate.timeInMillis,
                    AlarmManager.INTERVAL_DAY * 7,
                    alarmIntent
                )

                displayAlarmLogs(alarmDate)
            }
        }
    }

    private fun displayAlarmLogs(alarmDate: Calendar) {
        Log.d("alarmTime", DateFormat.getTimeInstance(DateFormat.FULL).format(alarmDate.time))
        Log.d("alarmDate", DateFormat.getDateInstance(DateFormat.FULL).format(alarmDate.time))
    }

    //stops alarms with given id
    fun stopAlarm(id: Long, alarmManager: AlarmManager, context: Context) {
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, AlarmReceiver::class.java),
            0
        )
        alarmManager.cancel(alarmIntent)
    }

    fun getPassedSeconds(): Int {
        val calendar = Calendar.getInstance()
        val isDaylightSavingActive = TimeZone.getDefault().inDaylightTime(Date())
        var offset = calendar.timeZone.rawOffset
        if (isDaylightSavingActive) {
            offset += TimeZone.getDefault().dstSavings
        }
        return ((calendar.timeInMillis + offset) / 1000).toInt()
    }

    fun Context.getFormattedTime(
        passedSeconds: Int,
        showSeconds: Boolean,
        makeAmPmSmaller: Boolean
    ): SpannableString {
        val hours = (passedSeconds / 3600) % 24
        val minutes = (passedSeconds / 60) % 60
        val seconds = passedSeconds % 60
        val formattedTime = formatTo12HourFormat(showSeconds, hours, minutes, seconds)
        val spannableTime = SpannableString(formattedTime)
        val amPmMultiplier = if (makeAmPmSmaller) 0.3f else 1f
        spannableTime.setSpan(
            RelativeSizeSpan(amPmMultiplier),
            spannableTime.length - 3,
            spannableTime.length,
            0
        )
        return spannableTime

    }

    fun Context.formatTo12HourFormat(
        showSeconds: Boolean,
        hours: Int,
        minutes: Int,
        seconds: Int
    ): String {
        val appendable = getString(if (hours >= 12) R.string.p_m else R.string.a_m)
        val newHours = if (hours == 0 || hours == 12) 12 else hours % 12
        return "${formatTime(showSeconds, false, newHours, minutes, seconds)} $appendable"
    }

    fun formatTime(
        showSeconds: Boolean,
        use24HourFormat: Boolean,
        hours: Int,
        minutes: Int,
        seconds: Int
    ): String {
        val hoursFormat = if (use24HourFormat) "%02d" else "%01d"
        var format = "$hoursFormat:%02d"

        return if (showSeconds) {
            format += ":%02d"
            String.format(format, hours, minutes, seconds)
        } else {
            String.format(format, hours, minutes)
        }
    }

    //creates notification for alarm with time and on click event
    fun updateAlarmNotification(allAlarms: List<Alarm>, context: Context) {

        val alarm = getEarliestAlarm(allAlarms)
        if (alarm == null) {
            dismissAlarmNotification(context)
            return
        }

        val notification = setupNotification(context, alarm)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT > 26) {
            val notificationChannel =
                NotificationChannel("100", "Alarm", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(notificationChannel)
        }
        notificationManager.notify(ALARM_NOTIFICATION_ID, notification.build())
    }

    private fun setupNotification(context: Context, alarm: Alarm): NotificationCompat.Builder {
        val notification = NotificationCompat.Builder(context, "100")
        notification.setSmallIcon(R.drawable.baseline_alarm_white_18)
        notification.setOnlyAlertOnce(true)
        if (alarm.name.isBlank()) {
            notification.setContentTitle(context.resources.getString(R.string.notification_default_title))
        } else {
            notification.setContentTitle(alarm.name)
        }

        if (alarm.isSingleAlarm()) {
            notification.setContentText(getAlarmTimeDescription(alarm.hours, alarm.minutes))
        } else {
            val day = getAlarmDayForNotification(alarm)
            notification.setContentText(
                day + ", " + getAlarmTimeDescription(
                    alarm.hours,
                    alarm.minutes
                )
            )
        }

        notification.priority = NotificationCompat.PRIORITY_DEFAULT
        notification.setOngoing(true)

        setupNotificationIntent(context, notification)

        return notification
    }

    private fun getAlarmDayForNotification(alarm: Alarm): String? {
        var day = ""

        alarm.days.forEach {
            if (it.value) {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_WEEK, alarm.daysStringToCalendar[it.key]!!)
                day = calendar.getDisplayName(
                    Calendar.DAY_OF_WEEK,
                    Calendar.LONG,
                    Locale.getDefault()
                )
            }
        }
        return if (day == "") null else day
    }

    //returns time string for recycler view and notification
    fun getAlarmTimeDescription(hours: Int, minutes: Int): String {

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hours)
        calendar.set(Calendar.MINUTE, minutes)
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.time)
    }

    private fun dismissAlarmNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ALARM_NOTIFICATION_ID)
    }

    private fun getEarliestAlarm(allAlarms: List<Alarm>): Alarm? {
        //TODO: still should refactor probably
        var earliestAlarm: Alarm? = null
        var earliestDate = Calendar.getInstance()
        earliestDate.timeInMillis = Long.MAX_VALUE

        for (alarm in allAlarms) {
            if (alarm.isOn) {
                alarm.days.forEach {
                    if (it.value) {
                        val alarmDate = getAlarmDateFromAlarm(alarm, it.key)
                        if (alarmDate.before(earliestDate)) {
                            earliestDate = alarmDate
                            earliestAlarm = alarm
                        }
                    }
                }

                if (alarm.isSingleAlarm()) {
                    val alarmDate = getAlarmDateFromAlarm(alarm)
                    if (alarmDate.before(earliestDate)) {
                        earliestDate = alarmDate
                        earliestAlarm = alarm
                    }
                }
            }
        }

        setDaysInEarliestAlarm(earliestAlarm, earliestDate[Calendar.DAY_OF_WEEK])

        return earliestAlarm
    }

    fun getAlarmDateFromAlarm(alarm: Alarm): Calendar {
        val alarmDate = Calendar.getInstance()
        alarmDate.set(Calendar.HOUR_OF_DAY, alarm.hours)
        alarmDate.set(Calendar.MINUTE, alarm.minutes)
        alarmDate.set(Calendar.SECOND, 0)
        alarmDate.set(Calendar.MILLISECOND, 0)
        if (alarmDate.before(Calendar.getInstance())) {
            alarmDate.add(Calendar.DAY_OF_YEAR, 1)
        }

        return alarmDate
    }

    fun getAlarmDateFromAlarm(alarm: Alarm, dayOfWeek: String): Calendar {
        val alarmDate = Calendar.getInstance()
        alarmDate.set(Calendar.HOUR_OF_DAY, alarm.hours)
        alarmDate.set(Calendar.MINUTE, alarm.minutes)
        alarmDate.set(Calendar.SECOND, 0)
        alarmDate.set(Calendar.MILLISECOND, 0)
        alarmDate.set(Calendar.DAY_OF_WEEK, alarm.daysStringToCalendar[dayOfWeek]!!)
        if (alarmDate.before(Calendar.getInstance())) {
            alarmDate.add(Calendar.WEEK_OF_MONTH, 1)
        }

        return alarmDate
    }

    private fun setDaysInEarliestAlarm(earliestAlarm: Alarm?, alarmDay: Int) {
        if (earliestAlarm == null || earliestAlarm.isSingleAlarm()) return

        for (day in earliestAlarm.days) {
            if (earliestAlarm.daysStringToCalendar[day.key] == alarmDay) {
                day.setValue(true)
            } else {
                day.setValue(false)
            }
        }
    }

    private fun setupAlarmIntent(id: Long, context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        intent.putExtra("alarmId", id)
        intent.action = "com.sabeeldev.alarmclock"
        return PendingIntent.getBroadcast(context, id.toInt(), intent, 0)
    }

    private fun setupNotificationIntent(
        context: Context,
        notification: NotificationCompat.Builder
    ) {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        notification.setContentIntent(pendingIntent)
    }
}
