package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.graphics.Bitmap

data class SaveStateItem(
    val id: String,
    val title: String,
    val date: Long,
    val isAutoSave: Boolean,
    val bitmap: Bitmap?
)
