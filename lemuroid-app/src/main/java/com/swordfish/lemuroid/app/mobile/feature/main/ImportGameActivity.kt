package com.swordfish.lemuroid.app.mobile.feature.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class ImportGameActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uri = intent.data
        if (intent.action == Intent.ACTION_VIEW && uri != null) {
            importRom(uri)
        } else {
            finish()
        }
    }

    companion object {
        private val importMutex = kotlinx.coroutines.sync.Mutex()
    }

    private fun importRom(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fileName = getFileName(uri)
                
                val ext = fileName.substringAfterLast('.', "").lowercase(java.util.Locale.US)
                val supported = com.swordfish.lemuroid.lib.library.GameSystem.getSupportedExtensions()
                if (!supported.contains(ext) && ext != "zip") {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImportGameActivity, "Not a supported game file", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }
                
                val directoriesManager = DirectoriesManager(applicationContext)
                val romsDir = directoriesManager.getInternalRomsDirectory()
                
                importMutex.withLock {
                    var destFile = File(romsDir, fileName)
                    if (destFile.exists()) {
                        val nameWithoutExtension = fileName.substringBeforeLast('.', fileName)
                        val extension = if (fileName.contains('.')) "." + fileName.substringAfterLast('.') else ""
                        var i = 1
                        while (destFile.exists()) {
                            destFile = File(romsDir, "$nameWithoutExtension ($i)$extension")
                            i++
                        }
                    }
                    
                    contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
                }
                
                withContext(Dispatchers.Main) {
                    val mainIntent = Intent(this@ImportGameActivity, MainActivity::class.java)
                    mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(mainIntent)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        var isZip = false
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        
        try {
            contentResolver.openInputStream(uri)?.use {
                val header = ByteArray(4)
                if (it.read(header, 0, 4) == 4) {
                    if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) {
                        isZip = true
                    }
                }
            }
        } catch (e: Exception) {}
        
        var finalName = result ?: "imported_game_${System.currentTimeMillis()}"
        
        if (isZip && !finalName.endsWith(".zip", ignoreCase = true)) {
            finalName += ".zip"
        } else if (!finalName.contains(".")) {
            val mimeType = contentResolver.getType(uri)
            if (mimeType != null) {
                val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                if (ext != null) {
                    finalName = "$finalName.$ext"
                }
            }
        }
        
        if (!finalName.contains(".")) {
            finalName += ".rom"
        }
        return finalName
    }
}
