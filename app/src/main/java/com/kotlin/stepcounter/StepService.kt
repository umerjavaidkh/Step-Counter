/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kotlin.stepcounter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.text.format.DateUtils

import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

import java.util.*

/**
 * The background service of the step counter app. It binds the sensors and counts in the background.
 * The [MainActivity] starts the service and connects to it to receive updates.
 *
 *
 * Created by tiefensuche on 06.11.16.
 */
internal class StepService : Service() {
    private lateinit var sharedPreferences: SharedPreferences
    // steps at the current day
    private var mTodaysSteps: Int = 0
    // steps reported from sensor
    private var mCurrentSteps: Int = 0
    // steps reported from sensor STEP_COUNTER in previous event
    private var mLastSteps = -1
    // current date of counting
    private var mCurrentDate: Long = 0
    private lateinit var simpleStepDetector: StepDetector
    private lateinit var mListener: SensorEventListener
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mBuilder: NotificationCompat.Builder


    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d(TAG, "Creating MotionService")

        startService()

        sharedPreferences = getSharedPreferences("packageName",0)

        // get last saved date
        mCurrentDate = sharedPreferences.getLong(KEY_DATE, System.currentTimeMillis())
        // get last steps
        mTodaysSteps = sharedPreferences.getInt(KEY_STEPS, 0)

        val manager = packageManager

        // connect sensor
        val mSensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: throw IllegalStateException("could not get sensor service")
        var mStepSensor: Sensor? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && manager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)) {
            // androids built in step counter
            Log.d(TAG, "using sensor step counter")


            mStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            mListener = object : SensorEventListener {

                override fun onSensorChanged(event: SensorEvent) {
                    handleEvent(event.values[0].toInt())
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    // no-op
                }
            }
        } else if (manager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
            // fallback sensor
            Log.d(TAG, "using fallback sensor accelerometer")
            mStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            simpleStepDetector = StepDetector(object : StepDetector.StepListener {
                override fun step(timeNs: Long) {
                    handleEvent(mCurrentSteps + 1)
                }
            })
            mListener = object : SensorEventListener {

                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        simpleStepDetector.updateAccel(
                                event.timestamp, event.values[0], event.values[1], event.values[2])
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    // no-op
                }
            }
        }

        if (mStepSensor != null) {
            mSensorManager.registerListener(mListener, mStepSensor,
                    SensorManager.SENSOR_DELAY_FASTEST)
        } else {
            Toast.makeText(this, getString(R.string.no_sensor), Toast.LENGTH_LONG).show()
        }
    }

    private fun handleEvent(value: Int) {

        mCurrentSteps = value
        if (mLastSteps == -1) {
            mLastSteps = value
        }
        mTodaysSteps += value - mLastSteps
        mLastSteps = value
        handleEvent()
    }

    private fun handleEvent() {

        if (!DateUtils.isToday(mCurrentDate)) {
            // Start counting for the new day
            mTodaysSteps = 0
            mCurrentDate = Util.calendar.timeInMillis
            sharedPreferences.edit().putLong(KEY_DATE, mCurrentDate).apply()

            // Add record for the day to the database
            Database.getInstance(this).addEntry(mCurrentDate, mTodaysSteps)
        }

        sharedPreferences.edit().putInt(KEY_STEPS, mTodaysSteps).apply()

        sendUpdate()
    }

    private fun sendUpdate() {
        mBuilder.setContentText(String.format(Locale.getDefault(), getString(R.string.steps_format), Util.stepsToMeters(mTodaysSteps), mTodaysSteps))
        mNotificationManager.notify(FOREGROUND_ID, mBuilder.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Received start id $startId: $intent")


        return START_STICKY
    }

    private fun startService() {
        mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: throw IllegalStateException("could not get notification service")
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        // Notification channels are only supported on Android O+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        mBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentIntent(pendingIntent)
        mBuilder.setContentText(String.format(Locale.getDefault(), getString(R.string.steps_format), Util.stepsToMeters(mTodaysSteps), mTodaysSteps))

        startForeground(FOREGROUND_ID, mBuilder.build())
    }

    /**
     * Creates Notification Channel. This is required in Android O+ to display notifications.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        if (mNotificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val notificationChannel = NotificationChannel(CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_NONE)

            notificationChannel.description = getString(R.string.steps)

            mNotificationManager.createNotificationChannel(notificationChannel)
        }
    }

    companion object {

        private val TAG = StepService::class.java.simpleName
        internal const val KEY_STEPS = "STEPS"
        internal const val KEY_DATE = "DATE"
        private const val FOREGROUND_ID = 3838
        private const val CHANNEL_ID = "com.kotlin.stepcounter.CHANNEL_ID"
    }
}
