package com.sabeeldev.alarmclock.service

import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import com.sabeeldev.alarmclock.Utils
import com.sabeeldev.alarmclock.database.AlarmDatabase

class BootService: JobIntentService() {

    private val JOB_ID = 100

    override fun onHandleWork(intent: Intent) {

        AlarmDatabase.getInstance(this).alarmDao().getAllAlarms().observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe { alarmList ->
                    alarmList.forEach {
                        if (it.isOn) {
                            if (it.isSingleAlarm()) {
                                Utils.setSingleAlarm(it, this)
                            } else {
                                Utils.setAlarmWithDays(it, this)
                            }
                        }

                        Utils.updateAlarmNotification(alarmList, this)
                    }
        }
    }

    fun enqueueWork(context: Context, work: Intent) {
        enqueueWork(context, BootService::class.java, JOB_ID, work)
    }
}