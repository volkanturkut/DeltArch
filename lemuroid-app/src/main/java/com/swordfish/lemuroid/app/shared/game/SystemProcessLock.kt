package com.swordfish.lemuroid.app.shared.game

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

object SystemProcessLock {
    private val channels = mutableMapOf<String, FileChannel>()
    private val locks = mutableMapOf<String, FileLock>()

    fun acquire(appContext: Context, systemId: String, gameTitle: String) {
        val lockFile = File(appContext.filesDir, "system_$systemId.lock")
        try {
            val channel = RandomAccessFile(lockFile, "rw").channel
            val lock = channel.tryLock()
            if (lock != null) {
                channels[systemId] = channel
                locks[systemId] = lock
                
                appContext.getSharedPreferences("system_locks", Context.MODE_PRIVATE)
                    .edit()
                    .putString(systemId, gameTitle)
                    .apply()
            }
        } catch (e: OverlappingFileLockException) {
            // Already locked by this process
        } catch (e: Exception) {
            // Handle other IO exceptions
        }
    }

    fun release(systemId: String) {
        try {
            locks[systemId]?.release()
            channels[systemId]?.close()
        } catch (e: Exception) {
            // Ignore
        } finally {
            locks.remove(systemId)
            channels.remove(systemId)
        }
    }

    fun getRunningGameName(appContext: Context, systemId: String): String? {
        val lockFile = File(appContext.filesDir, "system_$systemId.lock")
        if (!lockFile.exists()) return null
        
        try {
            RandomAccessFile(lockFile, "rw").use { raf ->
                val channel = raf.channel
                val testLock = channel.tryLock()
                if (testLock != null) {
                    testLock.release()
                    return null // No lock held, so no game running
                } else {
                    return appContext.getSharedPreferences("system_locks", Context.MODE_PRIVATE)
                        .getString(systemId, null)
                }
            }
        } catch (e: OverlappingFileLockException) {
            return appContext.getSharedPreferences("system_locks", Context.MODE_PRIVATE)
                .getString(systemId, null)
        } catch (e: Exception) {
            return appContext.getSharedPreferences("system_locks", Context.MODE_PRIVATE)
                .getString(systemId, null)
        }
    }
}
