package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged

@Composable
fun DeltArchBottomSearchBar(
    modifier: Modifier = Modifier,
    searchQuery: String,
    onUpdateQueryString: (String) -> Unit,
    onSearchFocused: () -> Unit = {},
    onSearchUnfocused: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    androidx.compose.foundation.layout.Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        val isFocused = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

        Surface(
            modifier = Modifier.weight(1f).fillMaxSize(),
            shape = RoundedCornerShape(100),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onUpdateQueryString,
                modifier = Modifier.fillMaxSize().onFocusChanged {
                    isFocused.value = it.isFocused
                    if (it.isFocused) {
                        onSearchFocused()
                    } else {
                        onSearchUnfocused()
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardActions =
                    KeyboardActions(
                        onDone = { focusManager.clearFocus(true) },
                    ),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = isFocused.value || searchQuery.isNotEmpty()
        ) {
            androidx.compose.material3.IconButton(
                onClick = { 
                    onUpdateQueryString("")
                    focusManager.clearFocus(true)
                },
                modifier = Modifier.padding(start = 8.dp).size(56.dp)
            ) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.CircleShape, 
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Default.Close, 
                        contentDescription = "Clear",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
