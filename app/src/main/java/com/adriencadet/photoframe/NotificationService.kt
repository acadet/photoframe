package com.adriencadet.photoframe

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat

class NotificationService {

    companion object {
        const val CHANNEL_ID = "PhotoFrame"
        const val CHANNEL_NAME = "PhotoFrame"

        const val NOTIFICATION_ID = 1
    }

    fun start(context: Context) {
        createNotificationChannel(context)
        createNotification(context)
    }

    fun stop(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = CHANNEL_NAME
            val descriptionText = ""
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = context. getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Slideshow")
            .setContentText("Tap to resume")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
}