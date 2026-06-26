package com.swordfish.lemuroid.app.shared.game

import android.app.Activity
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GameLauncher(
    private val coresSelection: CoresSelection,
    private val gameLaunchTaskHandler: GameLaunchTaskHandler,
) {
    @OptIn(DelicateCoroutinesApi::class)
    fun launchGameAsync(
        activity: Activity,
        game: Game,
        loadSave: Boolean,
        leanback: Boolean,
        newWindow: Boolean = false,
    ): Boolean {
        val runningGameName = SystemProcessLock.getRunningGameName(activity.applicationContext, game.systemId)
        if (runningGameName != null) {
            activity.runOnUiThread {
                val dialogView = activity.layoutInflater.inflate(R.layout.dialog_system_already_running, null)
                val messageTextView = dialogView.findViewById<android.widget.TextView>(R.id.dialog_message)
                messageTextView.text = "DeltArch can only play one game per system at a time.\n\nPlease quit the ($runningGameName), or choose another game for a different system."
                
                val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                    .setView(dialogView)
                    .create()
                
                dialogView.findViewById<android.view.View>(R.id.btn_cancel).setOnClickListener {
                    dialog.dismiss()
                }
                
                dialogView.findViewById<android.view.View>(R.id.btn_quit).setOnClickListener {
                    dialog.dismiss()
                    val quitIntent = android.content.Intent("com.swordfish.lemuroid.action.QUIT_GAME").apply {
                        putExtra("systemId", game.systemId)
                        `package` = activity.packageName
                    }
                    activity.sendBroadcast(quitIntent)
                    
                    GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        kotlinx.coroutines.delay(500)
                        launchGameAsync(activity, game, loadSave, leanback, newWindow)
                    }
                }
                
                dialog.show()
            }
            return false
        }

        if (!newWindow && GameProcessLock.isHeldByAnotherProcess(activity.applicationContext)) {
            activity.displayToast(R.string.game_process_another_game_running)
            return false
        }

        GlobalScope.launch {
            val system = GameSystem.findById(game.systemId)
            val coreConfig = coresSelection.getCoreConfigForSystem(system)
            gameLaunchTaskHandler.handleGameStart(activity.applicationContext)
            BaseGameActivity.launchGame(activity, coreConfig, game, loadSave, leanback, newWindow)
        }

        return true
    }
}
