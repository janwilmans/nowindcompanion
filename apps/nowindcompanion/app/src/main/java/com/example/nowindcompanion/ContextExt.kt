package com.example.nowindcompanion
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

fun Context.hasNowindPermissions(): Boolean {
//    return ContextCompat.checkSelfPermission(
//        this,
//        android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
//    )
    return true
}