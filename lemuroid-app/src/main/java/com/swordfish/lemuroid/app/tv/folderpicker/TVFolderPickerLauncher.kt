@file:Suppress("all")

package com.swordfish.lemuroid.app.tv.folderpicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.ImmersiveActivity
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

@Suppress("DEPRECATION")
class TVFolderPickerLauncher : ImmersiveActivity() {
    private val tvFolderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val sharedPreferences = SharedPreferencesHelper.getLegacySharedPreferences(this)
                val preferenceKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder)

                val currentValue: String? = sharedPreferences.getString(preferenceKey, null)
                val newValue = result.data?.extras?.getString(TVFolderPickerActivity.RESULT_DIRECTORY_PATH)

                if (newValue.toString() != currentValue) {
                    sharedPreferences.edit().apply {
                        this.putString(preferenceKey, newValue.toString())
                        this.commit()
                    }
                }

                startLibraryIndexWork()
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            tvFolderPickerLauncher.launch(Intent(this, TVFolderPickerActivity::class.java))
        }
    }



    private fun startLibraryIndexWork() {
        LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
    }

    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 1

        fun pickFolder(context: Context) {
            context.startActivity(Intent(context, TVFolderPickerLauncher::class.java))
        }
    }
}
