package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.swordfish.lemuroid.metadata.libretrodb.db.LibretroDBManager
import com.swordfish.lemuroid.metadata.libretrodb.db.entity.LibretroRom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDatabaseSearchDialog(
    initialQuery: String = "",
    onDismissRequest: () -> Unit,
    onGameSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var searchResults by remember { mutableStateOf<List<LibretroRom>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val performSearch = { query: String ->
        coroutineScope.launch {
            if (query.length > 2) {
                isSearching = true
                val dbManager = LibretroDBManager(context)
                val results = withContext(Dispatchers.IO) {
                    dbManager.dbInstance.gameDao().findByName("%$query%") ?: emptyList()
                }.take(100)
                
                val filteredResults = withContext(Dispatchers.IO) {
                    results.map { rom ->
                        async {
                            val systemDbName = rom.system ?: ""
                            val rawName = rom.name ?: ""
                            
                            val THUMB_REPLACE = Regex("[&*/:`<>?\\\\|]")
                            val thumbGameName = rawName.replace(THUMB_REPLACE, "_")
                            
                            var systemName = systemDbName
                            val gameSystem = runCatching { com.swordfish.lemuroid.lib.library.GameSystem.findById(systemDbName) }.getOrNull()
                            if (gameSystem != null) {
                                systemName = gameSystem.libretroFullName
                                if (systemDbName == com.swordfish.lemuroid.lib.library.SystemID.GB.dbname) {
                                    systemName = "Nintendo - Game Boy"
                                }
                                if (gameSystem.id == com.swordfish.lemuroid.lib.library.SystemID.MAME2003PLUS) {
                                    systemName = "MAME"
                                }
                            }
                            
                            val encodedSystemName = android.net.Uri.encode(systemName)
                            val encodedName = android.net.Uri.encode(thumbGameName)
                            val boxArtUrl = "https://thumbnails.libretro.com/$encodedSystemName/Named_Boxarts/$encodedName.png"
                            
                            val exists = try {
                                val connection = java.net.URL(boxArtUrl).openConnection() as java.net.HttpURLConnection
                                connection.requestMethod = "HEAD"
                                connection.connectTimeout = 3000
                                connection.readTimeout = 3000
                                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                                connection.responseCode in 200..299
                            } catch (e: Exception) {
                                false
                            }
                            
                            if (exists) rom else null
                        }
                    }.awaitAll().filterNotNull()
                }

                searchResults = filteredResults
                isSearching = false
            } else {
                searchResults = emptyList()
            }
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty()) {
            performSearch(initialQuery)
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar with Close button and Title
                TopAppBar(
                    title = {
                        Text(
                            text = "Game Database",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .padding(8.dp)
                                .background(androidx.compose.ui.graphics.Color.DarkGray, RoundedCornerShape(50))
                                .size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = androidx.compose.ui.graphics.Color.White)
                        }
                    },
                    actions = {
                        // Invisible placeholder for symmetry
                        Spacer(modifier = Modifier.width(52.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                // Search Bar
                val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        performSearch(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.DarkGray,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.DarkGray,
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = androidx.compose.ui.graphics.Color.Gray)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { 
                                    searchQuery = ""
                                    searchResults = emptyList()
                                },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(50))
                                    .size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                // Results List
                if (isSearching) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (searchQuery.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Game Database", color = androidx.compose.ui.graphics.Color.Gray, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "To search the database, type the name of a game in the search bar.",
                            color = androidx.compose.ui.graphics.Color.Gray,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(searchResults) { rom ->
                            val systemDbName = rom.system ?: ""
                            val rawName = rom.name ?: ""
                            
                            val THUMB_REPLACE = Regex("[&*/:`<>?\\\\|]")
                            val thumbGameName = rawName.replace(THUMB_REPLACE, "_")
                            
                            var systemName = systemDbName
                            val gameSystem = runCatching { com.swordfish.lemuroid.lib.library.GameSystem.findById(systemDbName) }.getOrNull()
                            if (gameSystem != null) {
                                systemName = gameSystem.libretroFullName
                                if (systemDbName == com.swordfish.lemuroid.lib.library.SystemID.GB.dbname) {
                                    systemName = "Nintendo - Game Boy"
                                }
                                if (gameSystem.id == com.swordfish.lemuroid.lib.library.SystemID.MAME2003PLUS) {
                                    systemName = "MAME"
                                }
                            }
                            
                            val encodedSystemName = android.net.Uri.encode(systemName)
                            val encodedName = android.net.Uri.encode(thumbGameName)
                            val boxArtUrl = "https://thumbnails.libretro.com/$encodedSystemName/Named_Boxarts/$encodedName.png"

                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onGameSelected(boxArtUrl) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = boxArtUrl,
                                        contentDescription = rom.name,
                                        modifier = Modifier
                                            .size(60.dp, 80.dp)
                                            .padding(end = 16.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        val cleanName = (rom.name ?: "Unknown").replace(Regex("\\s*\\([^)]*\\)|\\s*\\[[^\\]]*\\]"), "").trim()
                                        Text(
                                            text = cleanName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 76.dp),
                                    color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
