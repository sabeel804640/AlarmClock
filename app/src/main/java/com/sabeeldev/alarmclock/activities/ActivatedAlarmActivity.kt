package com.sabeeldev.alarmclock.activities

import androidx.lifecycle.ViewModelProviders
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import com.sabeeldev.alarmclock.database.Alarm
import com.sabeeldev.alarmclock.R
import com.sabeeldev.alarmclock.Utils
import com.sabeeldev.alarmclock.Utils.getFormattedTime
import com.sabeeldev.alarmclock.viewModel.AlarmViewModel
import com.sabeeldev.alarmclock.database.Injection
import java.util.*
import java.util.concurrent.TimeUnit

class ActivatedAlarmActivity: AppCompatActivity(), SensorEventListener {

    private lateinit var currentAlarm: Alarm
    private val disposable = CompositeDisposable()
    private lateinit var viewModel: AlarmViewModel
    private val mediaPlayer = MediaPlayer()
    private lateinit var vibrator: Vibrator
    private lateinit var sensorManager: SensorManager
    private var lastShakeTime: Long = System.currentTimeMillis()
    private var mediaPlayerPosition = 0

    //Prefs
    private var secondsToHoldButton: Int = 0
    private var amountOfShakeTimes = 0
    private var volume = 0f
    private var delayAlarmTime = 0
    private var playSoundInSilent = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        wakeUpDevice()
        setupPreferences()
        setupViewModel()

        val alarmId = getCurrentAlarmId()

        disposable.add(viewModel.getAlarm(alarmId)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { alarm ->
                    this.currentAlarm = alarm
                    ringAlarm()
                    setupCorrectTurnOffDialog(alarm)
                    updateAlarmStatusAndNotification()
                })
    }

    override fun onStop() {
        super.onStop()
        if (this.isFinishing) {
            Log.d("mediaPlayerPause", "onStop")
            mediaPlayer.stop()
            mediaPlayer.release()
            vibrator.cancel()
            disposable.clear()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState?.putInt("shakeTimes", amountOfShakeTimes)
        outState?.putInt("mediaPlayerPosition", mediaPlayer.currentPosition)
        Log.d("mediaPlayerPause", "saveInstanceState")
        if (isChangingConfigurations) {
            mediaPlayer.pause()
        }
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        if (savedInstanceState != null) {
            amountOfShakeTimes = savedInstanceState.getInt("shakeTimes")
            mediaPlayerPosition = savedInstanceState.getInt("mediaPlayerPosition")
        }
        super.onRestoreInstanceState(savedInstanceState)
    }

    private fun setupViewModel() {
        val viewModelFactory = Injection.provideViewModelFactory(this)
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(AlarmViewModel::class.java)
    }

    private fun setupCorrectTurnOffDialog(alarm: Alarm) {
        val turnOffMode = getTurnOffModeFromAlarm(alarm)
        when (turnOffMode) {
            alarm.TURN_OFF_MODE_BUTTON_PRESS -> {
                setupPressButtonDialog()
            }

            alarm.TURN_OFF_MODE_BUTTON_HOLD -> {
                setupHoldButtonDialog()
            }

            alarm.TURN_OFF_MODE_SHAKE_DEVICE -> {
                setupShakeDeviceDialog()
            }
        }
    }

    private fun getCurrentAlarmId(): Long {
        return intent.extras!!.getLong("alarmId") / 10
    }

    private fun getTurnOffModeFromAlarm(alarm: Alarm): String {
        var turnOffMode = ""
        alarm.turnOffMode.forEach {
            if (it.value)
                turnOffMode = it.key
        }

        return turnOffMode
    }

    private fun setupPressButtonDialog() {
        setContentView(R.layout.activity_activated_alarm_press)
        setupLabelTextForDialog()
        setupDelayButton()
        setupTurnOffButton()
    }

    private fun setupHoldButtonDialog() {
        setContentView(R.layout.activity_activated_alarm_hold)
        setupLabelTextForDialog()
        setupTooltipForHoldButton()
        setupOnHoldTurnOffButton()
    }

    private fun setupShakeDeviceDialog() {
        setContentView(R.layout.activity_activated_alarm_shake)
        setupLabelTextForDialog()
        setupAccelerometer()
        setupDelayButton()
    }

    private fun wakeUpDevice() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        secondsToHoldButton = prefs.getInt(resources.getString(R.string.pref_alarm_seconds_hold_button_key), resources.getString(
            R.string.pref_alarm_seconds_hold_button_default
        ).toInt())
        amountOfShakeTimes = prefs.getInt(resources.getString(R.string.pref_alarm_shake_times_number_key), resources.getString(
            R.string.pref_alarm_shake_times_number_default
        ).toInt())
        volume = prefs.getInt(resources.getString(R.string.pref_alarm_volume_value_key), 75).toFloat() / 100
        delayAlarmTime = prefs.getString(resources.getString(R.string.pref_alarm_delay_time_key), resources.getString(
            R.string.pref_alarm_delay_time_default
        ))!!
            .toInt()
        playSoundInSilent = prefs.getBoolean(resources.getString(R.string.pref_alarm_volume_in_silent_key), true)
    }

    private fun setupTooltipForHoldButton() {
        findViewById<TextView>(R.id.activated_alarm_hold_button_tooltip)?.apply {
            text = resources.getQuantityString(R.plurals.activated_alarm_hold_button_tooltip, secondsToHoldButton, secondsToHoldButton)
        }
    }

    private fun setupLabelTextForDialog() {
        if (currentAlarm.name.isNotBlank()) {
            findViewById<TextView>(R.id.activated_alarm_name_label)?.apply {
                text = currentAlarm.name
                visibility = TextView.VISIBLE
            }
        }

        findViewById<TextView>(R.id.activated_alarm_time_label)?.apply {
           val time=(currentAlarm.hours*3600)+(currentAlarm.minutes*60)
            Utils.getAlarmTimeDescription(currentAlarm.hours, currentAlarm.minutes)
            text=getFormattedTime(time,false,true)
        }
    }

    private fun setupAccelerometer() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            changeTextInShakeDialog()
        }
    }

    private fun updateAlarmStatusAndNotification() {
        if (currentAlarm.isSingleAlarm()) {
            disposable.add(viewModel.updateAlarmStatus(currentAlarm.aid, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        updateAlarmNotification()
                    })
        } else {
            updateAlarmNotification()
        }
    }

    private fun updateAlarmNotification() {
        disposable.add(viewModel.getAlarmList()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { allAlarms ->
                    Utils.updateAlarmNotification(allAlarms, this@ActivatedAlarmActivity)
                })
    }

    private fun setupMediaPlayer() {
        if (Build.VERSION.SDK_INT < 21) {
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM)
        } else {
            val attributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            mediaPlayer.setAudioAttributes(attributes)
        }

        Log.d("mediaPlayerPause", "setup")
        mediaPlayer.isLooping = true
        mediaPlayer.setVolume(volume, volume)
        mediaPlayer.setDataSource(this, Uri.parse(currentAlarm.ringtone))
        mediaPlayer.prepareAsync()
    }

    private fun ringAlarm() {
        if (currentAlarm.vibrate) {
            vibrate()
        }

        if (shouldPlaySound())
            setupMediaPlayer()
            mediaPlayer.setOnPreparedListener {
                Log.d("mediaPlayerPause", "onPrepared")
                mediaPlayer.seekTo(mediaPlayerPosition)
                mediaPlayer.start()
            }
    }

    private fun shouldPlaySound(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return playSoundInSilent || audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    private fun vibrate() {
        val pattern = longArrayOf(0, 1000, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            vibrator.vibrate(pattern, 0)
        }
        Log.d("vibration", "vibrated")
    }

    private fun setupDelayButton() {
        findViewById<Button>(R.id.activated_alarm_delay_button).apply {
            setOnClickListener { _ ->
                this.isEnabled = false
                delayAlarm()
            }
        }
    }

    private fun delayAlarm() {
        addDelayTimeToAlarm(currentAlarm, delayAlarmTime)
        Utils.setSingleAlarm(currentAlarm, this@ActivatedAlarmActivity)
        Toast.makeText(this@ActivatedAlarmActivity, resources.getQuantityString(R.plurals.activated_alarm_delay_toast, delayAlarmTime, delayAlarmTime), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun addDelayTimeToAlarm(alarm: Alarm, delayTime: Int) {
        val currentTime = Calendar.getInstance()
        currentTime.add(Calendar.MINUTE, delayTime)

        alarm.hours = currentTime.get(Calendar.HOUR_OF_DAY)
        alarm.minutes = currentTime.get(Calendar.MINUTE)
    }

    private fun setupTurnOffButton() {
        findViewById<Button>(R.id.activated_alarm_turn_off_button).apply {
            setOnClickListener {
                turnOffAlarm()
            }
        }
    }

    private fun setupOnHoldTurnOffButton() {
        //without it notification will be always shown
        var isTurnedOff = false

        findViewById<Button>(R.id.activated_alarm_delay_button).apply {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Completable.timer(secondsToHoldButton.toLong(), TimeUnit.SECONDS, AndroidSchedulers.mainThread()).doOnComplete {
                            isTurnedOff = true
                            turnOffAlarm()
                        }.subscribe()
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!isTurnedOff) {
                            delayAlarm()
                        }
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
        }
    }

    private fun turnOffAlarm() {
        Toast.makeText(this@ActivatedAlarmActivity, getString(R.string.activated_alarm_turn_off_toast), Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    override fun onSensorChanged(event: SensorEvent?) {
        val minTimeBetweenShakes: Long = 500
        val shakeThreshold = 3.25f

        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER &&
                System.currentTimeMillis() - lastShakeTime > minTimeBetweenShakes) {
            val x = event.values[0].toDouble()
            val y = event.values[1].toDouble()
            val z = event.values[2].toDouble()

            val acceleration = Math.sqrt(Math.pow(x, 2.0) +
                    Math.pow(y, 2.0) +
                    Math.pow(z, 2.0)) - SensorManager.GRAVITY_EARTH

            if (acceleration > shakeThreshold) {
                onRegisteredShake()
            }
        }
    }

    private fun onRegisteredShake() {
        lastShakeTime = System.currentTimeMillis()

        amountOfShakeTimes--
        changeTextInShakeDialog()

        if (amountOfShakeTimes == 0) {
            sensorManager.unregisterListener(this)
            finish()
        }
    }

    private fun changeTextInShakeDialog() {
        findViewById<TextView>(R.id.activated_alarm_shake_text).apply {
            text = resources.getQuantityString(R.plurals.activated_alarm_shake_device_text, amountOfShakeTimes, amountOfShakeTimes)
        }
    }
}