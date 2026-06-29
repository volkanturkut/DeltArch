package com.swordfish.lemuroid.app.mobile.feature.settings.skins

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.skins.SkinAssetLoader
import com.swordfish.lemuroid.app.shared.skins.SkinManager
import com.swordfish.lemuroid.app.shared.skins.SkinPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SkinSystemItem(
    val id: String,
    val name: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinsSettingsScreen(
    modifier: Modifier = Modifier,
    skinManager: SkinManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val systemsList = remember {
        listOf(
            SkinSystemItem("nes", "Nintendo Entertainment System"),
            SkinSystemItem("snes", "Super Nintendo Entertainment System"),
            SkinSystemItem("genesis", "Sega Genesis"),
            SkinSystemItem("gbc", "Game Boy Color / Game Boy"),
            SkinSystemItem("gba", "Game Boy Advance"),
            SkinSystemItem("n64", "Nintendo 64"),
            SkinSystemItem("nds", "Nintendo DS")
        )
    }

    var expandedSystemId by remember { mutableStateOf<String?>(null) }
    // Triggers list refresh
    var refreshCounter by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    skinManager.importSkin(uri)
                }
                if (result.isSuccess) {
                    val skin = result.getOrThrow()
                    skinManager.setSelectedSkin(skin.systemId, skin.id)
                    Toast.makeText(context, "Skin imported successfully!", Toast.LENGTH_SHORT).show()
                    refreshCounter++
                    expandedSystemId = skin.systemId
                } else {
                    Toast.makeText(context, "Failed to import skin: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_title_skins)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(systemsList) { system ->
                key(system.id, refreshCounter) {
                    val availableSkins by produceState<List<SkinPackage>>(emptyList(), system.id, refreshCounter) {
                        value = withContext(Dispatchers.IO) {
                            skinManager.getAvailableSkins(system.id)
                        }
                    }
                    val selectedSkin by produceState<SkinPackage?>(null, system.id, refreshCounter) {
                        value = withContext(Dispatchers.IO) {
                            skinManager.getSelectedSkin(system.id)
                        }
                    }

                    SkinSystemCard(
                        system = system,
                        availableSkins = availableSkins,
                        selectedSkin = selectedSkin,
                        isExpanded = expandedSystemId == system.id,
                        onExpandClick = {
                            expandedSystemId = if (expandedSystemId == system.id) null else system.id
                        },
                        onSkinSelect = { skin ->
                            skinManager.setSelectedSkin(system.id, skin.id)
                            refreshCounter++
                        },
                        onImportClick = {
                            filePickerLauncher.launch("*/*")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SkinSystemCard(
    system: SkinSystemItem,
    availableSkins: List<SkinPackage>,
    selectedSkin: SkinPackage?,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onSkinSelect: (SkinPackage) -> Unit,
    onImportClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpandClick)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = system.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        selectedSkin?.let {
                            Text(
                                text = "Active: ${it.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = "Available Skins",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        availableSkins.forEach { skin ->
                            SkinThumbnailCard(
                                skin = skin,
                                isSelected = selectedSkin?.id == skin.id,
                                onClick = { onSkinSelect(skin) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(180.dp)
                            )
                        }
                        if (availableSkins.size < 3) {
                            // Empty weights to fill row spacing
                            Spacer(modifier = Modifier.weight((3 - availableSkins.size).toFloat()))
                        }
                    }

                    Button(
                        onClick = onImportClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(text = "Import Skin (.deltaskin)")
                    }
                }
            }
        }
    }
}

@Composable
fun SkinThumbnailCard(
    skin: SkinPackage,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .run {
                if (isSelected) {
                    background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        )
                } else {
                    background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                }
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            SkinThumbnail(
                skinPackage = skin,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = skin.name,
                    maxLines = 1,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SkinThumbnail(
    skinPackage: SkinPackage,
    modifier: Modifier = Modifier
) {
    val bitmapState by produceState<android.graphics.Bitmap?>(null, skinPackage) {
        value = withContext(Dispatchers.IO) {
            val layout = skinPackage.info.representations["iphone"]?.standard?.portrait
                ?: skinPackage.info.representations["iphone"]?.edgeToEdge?.portrait
                ?: skinPackage.info.representations["iphone"]?.splitView?.portrait
            layout?.let { SkinAssetLoader.resolveAssetFile(skinPackage, it) }
                ?.let { SkinAssetLoader.loadBitmap(it) }
        }
    }

    if (bitmapState != null) {
        Image(
            bitmap = bitmapState!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier.background(Color.DarkGray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VideogameAsset,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
