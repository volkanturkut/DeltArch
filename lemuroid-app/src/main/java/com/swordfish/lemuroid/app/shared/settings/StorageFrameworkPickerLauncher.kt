package com.swordfish.lemuroid.app.shared.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.utils.android.displayErrorDialog
import com.swordfish.lemuroid.lib.android.RetrogradeActivity
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StorageFrameworkPickerLauncher : RetrogradeActivity() {
    @Inject
    lateinit var directoriesManager: DirectoriesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/*",
                    "text/*",
                    "application/octet-stream"
                ))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            try {
                startActivityForResult(intent, REQUEST_CODE_PICK_FILES)
            } catch (e: Exception) {
                val message = getString(
                    com.swordfish.lemuroid.R.string.dialog_saf_not_found,
                    directoriesManager.getInternalRomsDirectory()
                )
                displayErrorDialog(message, getString(com.swordfish.lemuroid.R.string.ok)) { finish() }
            }
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        resultData: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, resultData)

        if (requestCode == REQUEST_CODE_PICK_FILES && resultCode == Activity.RESULT_OK) {
            val uris = mutableListOf<Uri>()

            // Support multi-select
            if (resultData?.clipData != null) {
                val clipData = resultData.clipData!!
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } else if (resultData?.data != null) {
                uris.add(resultData.data!!)
            }

            if (uris.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val romsDir = directoriesManager.getInternalRomsDirectory()
                    for (uri in uris) {
                        try {
                            var fileName: String? = null
                            var isZip = false
                            if (uri.scheme == "content") {
                                try {
                                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                        if (cursor.moveToFirst()) {
                                            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                            if (index != -1) {
                                                fileName = cursor.getString(index)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {}
                            }
                            if (fileName == null) {
                                fileName = uri.path
                                val cut = fileName?.lastIndexOf('/')
                                if (cut != null && cut != -1) {
                                    fileName = fileName?.substring(cut + 1)
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
                            
                            fileName = fileName ?: "imported_game_${System.currentTimeMillis()}"
                            if (isZip && !fileName!!.endsWith(".zip", ignoreCase = true)) {
                                fileName += ".zip"
                            } else if (!fileName!!.contains(".")) {
                                val mimeType = contentResolver.getType(uri)
                                if (mimeType != null) {
                                    val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                                    if (ext != null) {
                                        fileName = "$fileName.$ext"
                                    }
                                }
                            }
                            if (!fileName!!.contains(".")) {
                                fileName += ".rom"
                            }

                            val destFile = java.io.File(romsDir, fileName)
                            contentResolver.openInputStream(uri)?.use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
                    withContext(Dispatchers.Main) {
                        finish()
                    }
                }
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    companion object {
        private const val REQUEST_CODE_PICK_FILES = 1

        fun pickFolder(context: Context) {
            context.startActivity(Intent(context, StorageFrameworkPickerLauncher::class.java))
        }
    }
}
