package com.swordfish.lemuroid.app.shared.skins

import android.content.Context
import android.net.Uri
import com.swordfish.lemuroid.app.shared.skins.models.SkinInfo
import com.swordfish.lemuroid.app.shared.skins.models.LayoutInfo
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class SkinPackage(
    val id: String, // Directory name, e.g. "default", "com.custom.skin"
    val systemId: String,
    val name: String,
    val info: SkinInfo,
    val dir: File
)

class SkinManager(private val context: Context) {
    private val skinsDir = File(context.filesDir, "skins")
    private val json = Json { ignoreUnknownKeys = true }
    private val defaultsCopiedPreference = "pref_default_skins_copied"
    
    companion object {
        private val cachedSelectedSkins = mutableMapOf<String, SkinPackage?>()
    }

    init {
        skinsDir.mkdirs()
    }

    fun getSystemDirName(systemId: String): String {
        val lower = systemId.lowercase()
        return when (lower) {
            "gb", "gbc" -> "gbc"
            "gen", "genesis", "md" -> "genesis"
            "nds", "ds" -> "nds"
            else -> lower
        }
    }

    @Synchronized
    fun copyDefaultSkinsFromAssets() {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)

        try {
            val assetsList = context.assets.list("skins") ?: return
            if (prefs.getBoolean(defaultsCopiedPreference, false) && defaultsExist(assetsList)) return

            for (assetFileName in assetsList) {
                if (assetFileName.endsWith(".deltaskin")) {
                    val prefix = assetFileName.substringBefore(".deltaskin")
                    val systemDirName = getSystemDirName(prefix)
                    val targetDir = File(skinsDir, "$systemDirName/default")
                    if (!File(targetDir, "info.json").exists()) {
                        context.assets.open("skins/$assetFileName").use { input ->
                            ZipInputStream(input).use { zip ->
                                unzip(zip, targetDir)
                            }
                        }
                    }
                }
            }
            prefs.edit().putBoolean(defaultsCopiedPreference, true).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun defaultsExist(assetFileNames: Array<String>): Boolean {
        return assetFileNames
            .filter { it.endsWith(".deltaskin") }
            .all { assetFileName ->
                val systemDirName = getSystemDirName(assetFileName.substringBefore(".deltaskin"))
                File(skinsDir, "$systemDirName/default/info.json").exists()
            }
    }

    fun getAvailableSkins(systemId: String): List<SkinPackage> {
        copyDefaultSkinsFromAssets() // Ensure defaults are copied
        val systemDirName = getSystemDirName(systemId)
        val systemSkinsDir = File(skinsDir, systemDirName)
        if (!systemSkinsDir.exists()) return emptyList()

        val list = mutableListOf<SkinPackage>()
        systemSkinsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val infoJsonFile = File(dir, "info.json")
                if (infoJsonFile.exists()) {
                    try {
                        val skinInfo = json.decodeFromString<SkinInfo>(infoJsonFile.readText())
                        list.add(SkinPackage(dir.name, systemDirName, skinInfo.name, skinInfo, dir))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return list
    }

    fun getSelectedSkin(systemId: String): SkinPackage? {
        val systemDirName = getSystemDirName(systemId)
        val cacheKey = "selected_$systemDirName"
        cachedSelectedSkins[cacheKey]?.let { return it }

        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        val selectedId = prefs.getString("pref_skin_$systemDirName", "default")
        
        val skinDir = File(skinsDir, "$systemDirName/$selectedId")
        val infoJsonFile = File(skinDir, "info.json")
        
        val skin = if (infoJsonFile.exists()) {
            try {
                val skinInfo = json.decodeFromString<SkinInfo>(infoJsonFile.readText())
                SkinPackage(skinDir.name, systemDirName, skinInfo.name, skinInfo, skinDir)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        } ?: getAvailableSkins(systemId).firstOrNull { it.id == "default" } ?: getAvailableSkins(systemId).firstOrNull()

        cachedSelectedSkins[cacheKey] = skin
        return skin
    }

    fun setSelectedSkin(systemId: String, skinId: String) {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        prefs.edit().putString("pref_skin_${getSystemDirName(systemId)}", skinId).apply()
    }

    fun importSkin(uri: Uri): Result<SkinPackage> {
        return try {
            val tempDir = File(context.cacheDir, "temp_skin_import_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    unzip(zip, tempDir)
                }
            }

            val infoJsonFile = File(tempDir, "info.json")
            if (!infoJsonFile.exists()) {
                tempDir.deleteRecursively()
                return Result.failure(Exception("Invalid skin: info.json missing"))
            }

            val skinInfo = json.decodeFromString<SkinInfo>(infoJsonFile.readText())
            val systemDirName = mapGameTypeToSystemDir(skinInfo.gameTypeIdentifier)
            val skinId = sanitizeSkinId(skinInfo.identifier.ifEmpty { tempDir.name })
            val finalTargetDir = File(skinsDir, "$systemDirName/$skinId")

            if (finalTargetDir.exists()) {
                finalTargetDir.deleteRecursively()
            }
            tempDir.renameTo(finalTargetDir)

            // Double check rename worked, fallback to copy if rename across filesystems fails
            if (!File(finalTargetDir, "info.json").exists()) {
                tempDir.copyRecursively(finalTargetDir, overwrite = true)
                tempDir.deleteRecursively()
            }

            Result.success(SkinPackage(skinId, systemDirName, skinInfo.name, skinInfo, finalTargetDir))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapGameTypeToSystemDir(gameType: String): String {
        val clean = gameType.substringAfterLast(".").lowercase()
        return when (clean) {
            "nes" -> "nes"
            "snes" -> "snes"
            "gb", "gbc" -> "gbc"
            "gba" -> "gba"
            "n64" -> "n64"
            "ds" -> "nds"
            "genesis", "md", "megadrive" -> "genesis"
            else -> clean
        }
    }

    private fun unzip(zipInputStream: ZipInputStream, targetDir: File) {
        targetDir.mkdirs()
        val canonicalTarget = targetDir.canonicalFile
        var entry = zipInputStream.nextEntry
        while (entry != null) {
            val file = safeResolveZipEntry(canonicalTarget, entry.name)
            if (entry.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { output ->
                    zipInputStream.copyTo(output)
                }
            }
            zipInputStream.closeEntry()
            entry = zipInputStream.nextEntry
        }
    }

    private fun safeResolveZipEntry(targetDir: File, entryName: String): File {
        val normalizedName = entryName.replace('\\', '/')
        require(!normalizedName.startsWith("/") && !normalizedName.contains('\u0000')) {
            "Invalid skin archive entry: $entryName"
        }

        val file = File(targetDir, normalizedName).canonicalFile
        val targetPath = targetDir.path + File.separator
        require(file.path == targetDir.path || file.path.startsWith(targetPath)) {
            "Invalid skin archive entry: $entryName"
        }
        return file
    }

    private fun sanitizeSkinId(id: String): String {
        return id.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "imported" }
    }

    fun getLayoutForSkin(skin: SkinPackage, isLandscape: Boolean, isTablet: Boolean): LayoutInfo? {
        val representations = skin.info.representations
        val deviceKey = if (isTablet) "ipad" else "iphone"
        val rep = representations[deviceKey] ?: representations["iphone"] ?: return null

        val configs = listOfNotNull(rep.edgeToEdge, rep.standard, rep.splitView)
        for (config in configs) {
            val layout = if (isLandscape) config.landscape else config.portrait
            if (layout != null) return layout
        }
        return null
    }
}
