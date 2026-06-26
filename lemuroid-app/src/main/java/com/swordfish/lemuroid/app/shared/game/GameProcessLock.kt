package com.swordfish.lemuroid.app.shared.game

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import dagger.android.support.DaggerApplication
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

object GameProcessLock {
    private val locks = mutableMapOf<Int, FileLock>()
    private val channels = mutableMapOf<Int, FileChannel>()

    fun acquire(appContext: Context) {
        val processIndex = getProcessIndex(appContext)
        if (locks.containsKey(processIndex)) return
        
        try {
            val lockFile = File(appContext.filesDir, "game_process_$processIndex.lock")
            val channel = RandomAccessFile(lockFile, "rw").channel
            val lock = channel.tryLock()
            if (lock != null) {
                locks[processIndex] = lock
                channels[processIndex] = channel
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun isProcessLocked(appContext: Context, processIndex: Int): Boolean {
        val lockFile = File(appContext.filesDir, "game_process_$processIndex.lock")
        if (!lockFile.exists()) return false
        
        try {
            RandomAccessFile(lockFile, "rw").channel.use { ch ->
                val testLock = ch.tryLock()
                return if (testLock != null) {
                    testLock.release()
                    false
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            return true
        }
    }

    fun isHeldByAnotherProcess(appContext: Context): Boolean {
        return isProcessLocked(appContext, 1)
    }

    private fun getProcessIndex(context: Context): Int {
        val processName = retrieveProcessName(context) ?: return 1
        return when {
            processName.endsWith(":game2") -> 2
            processName.endsWith(":game3") -> 3
            else -> 1
        }
    }

    private fun retrieveProcessName(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return DaggerApplication.getProcessName()
        }
        val currentPID = Process.myPid()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.runningAppProcesses
            ?.firstOrNull { it.pid == currentPID }
            ?.processName
    }
}
