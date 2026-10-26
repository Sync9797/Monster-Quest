package com.monsterquest.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

/**
 * Hands off to the phone's map app for walking directions. Google Maps gets a
 * walking route straight away; any other map app opens on the spot.
 */
object Navigation {
    private const val GOOGLE_MAPS = "com.google.android.apps.maps"

    fun walkTo(context: Context, lat: Double, lon: Double, label: String) {
        val point = String.format(Locale.US, "%.6f,%.6f", lat, lon)
        val google = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$point&mode=w"))
            .setPackage(GOOGLE_MAPS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$point(${Uri.encode(label)})"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$point&travelmode=walking"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (intent in listOf(google, geo, web)) {
            try {
                context.startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next option.
            }
        }
        Toast.makeText(context, "No map app found for directions.", Toast.LENGTH_SHORT).show()
    }
}
