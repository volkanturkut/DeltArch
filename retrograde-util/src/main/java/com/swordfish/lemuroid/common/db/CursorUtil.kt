package com.swordfish.lemuroid.common.db

import android.database.Cursor

@Suppress("unused")
fun Cursor.asSequence(): Sequence<Cursor> = generateSequence { if (moveToNext()) this else null }
