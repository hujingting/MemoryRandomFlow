package com.example.photoreviewer.viewmodel

import android.app.PendingIntent

sealed class DeletionRequest {
    data class RequiresPendingIntent(val intent: PendingIntent) : DeletionRequest()
}