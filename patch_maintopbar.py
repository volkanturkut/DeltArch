import sys

with open(r'C:\Users\volka\Downloads\Lemuroid\lemuroid-app\src\main\java\com\swordfish\lemuroid\app\mobile\feature\main\MainTopBar.kt', 'r', encoding='utf8') as f:
    text = f.read()

# Add imports
imports = '''import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
'''
if 'import androidx.compose.material.icons.filled.Add' not in text:
    text = text.replace('import androidx.compose.material3.TopAppBar', imports + 'import androidx.compose.material3.TopAppBar')

# Update MainTopBar signature
text = text.replace(
    'fun MainTopBar(\n    currentRoute: MainRoute,\n    navController: NavHostController,\n    onHelpPressed: () -> Unit,\n    onUpdateQueryString: (String) -> Unit,\n    mainUIState: MainViewModel.UiState,\n)',
    'fun MainTopBar(\n    currentRoute: MainRoute,\n    navController: NavHostController,\n    onHelpPressed: () -> Unit,\n    onUpdateQueryString: (String) -> Unit,\n    mainUIState: MainViewModel.UiState,\n    onAddDirectoryClicked: () -> Unit = {},\n    dynamicTitle: String? = null\n)'
)

# Update LemuroidTopAppBar call
text = text.replace(
    'onUpdateQueryString = onUpdateQueryString,\n        )',
    'onUpdateQueryString = onUpdateQueryString,\n            onAddDirectoryClicked = onAddDirectoryClicked,\n            dynamicTitle = dynamicTitle\n        )'
)

# Update LemuroidTopAppBar signature
text = text.replace(
    'fun LemuroidTopAppBar(\n    route: MainRoute,\n    navController: NavController,\n    mainUIState: MainViewModel.UiState,\n    onHelpPressed: () -> Unit,\n    onUpdateQueryString: (String) -> Unit,\n)',
    'fun LemuroidTopAppBar(\n    route: MainRoute,\n    navController: NavController,\n    mainUIState: MainViewModel.UiState,\n    onHelpPressed: () -> Unit,\n    onUpdateQueryString: (String) -> Unit,\n    onAddDirectoryClicked: () -> Unit = {},\n    dynamicTitle: String? = null\n)'
)

# Update Title logic
text = text.replace(
    'stringResource(route.title)',
    'dynamicTitle ?: stringResource(route.title)'
)

# Update Actions signature
text = text.replace(
    'fun LemuroidTopBarActions(\n    route: MainRoute,\n    navController: NavController,\n    context: Context,\n    saveSyncEnabled: Boolean,\n    operationsInProgress: Boolean,\n    onHelpPressed: () -> Unit,\n)',
    'fun LemuroidTopBarActions(\n    route: MainRoute,\n    navController: NavController,\n    context: Context,\n    saveSyncEnabled: Boolean,\n    operationsInProgress: Boolean,\n    onHelpPressed: () -> Unit,\n    onAddDirectoryClicked: () -> Unit = {}\n)'
)

# Update Actions call
text = text.replace(
    'operationsInProgress = mainUIState.operationInProgress,\n            )',
    'operationsInProgress = mainUIState.operationInProgress,\n                onAddDirectoryClicked = onAddDirectoryClicked\n            )'
)

# Add plus icon
plus_icon = '''
        if (route == MainRoute.HOME) {
            IconButton(onClick = onAddDirectoryClicked) {
                Icon(Icons.Filled.Add, "Add Directory")
            }
        }
'''
if 'Icons.Filled.Add' not in text:
    text = text.replace(
        'if (route.showBottomNavigation) {',
        plus_icon + '        if (route.showBottomNavigation) {'
    )

with open(r'C:\Users\volka\Downloads\Lemuroid\lemuroid-app\src\main\java\com\swordfish\lemuroid\app\mobile\feature\main\MainTopBar.kt', 'w', encoding='utf8') as f:
    f.write(text)

