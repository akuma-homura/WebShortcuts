package io.github.homura251.webshortcuts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.homura251.webshortcuts.shortcuts.ChromeTabLauncher
import io.github.homura251.webshortcuts.ui.WebShortcutsApp
import io.github.homura251.webshortcuts.ui.theme.ThemePreferences
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::importImage)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WebShortcutsApplication).container
        setContent {
            val themePreferences = remember { ThemePreferences(applicationContext) }
            val appearance by themePreferences.state.collectAsStateWithLifecycle()
            val darkTheme = appearance.themeMode.isDark(isSystemInDarkTheme())
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            WebShortcutsTheme(darkTheme = darkTheme, dynamicColor = appearance.dynamicColor, palette = appearance.palette) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.messages.collect { snackbar.showSnackbar(it) }
                    }
                }
                WebShortcutsApp(
                    state = state, iconResolver = container.iconResolver, snackbarHostState = snackbar,
                    onQueryChange = viewModel::setQuery, onAdd = { viewModel.add() }, onEdit = viewModel::edit,
                    onOpen = { if (!ChromeTabLauncher.open(this, it.url)) viewModel.notify("未找到可用浏览器，请先安装 Chrome 或其他浏览器") },
                    onPin = viewModel::pin, onUpdateShortcut = viewModel::updateShortcut,
                    onRefresh = viewModel::refresh, onRefreshAll = viewModel::refreshAll, onDelete = viewModel::delete,
                    onDismissEditor = viewModel::dismissEditor, onTitleChange = viewModel::setTitle,
                    onUrlChange = viewModel::setUrl, onUrlFocusLost = viewModel::onUrlFocusLost,
                    onModeChange = viewModel::setMode,
                    onPickImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRefetchIcon = viewModel::refetchIcon, onSave = viewModel::save,
                    onCenterCropChange = viewModel::setCenterCrop,
                    onCropApply = viewModel::applyCustomCrop,
                    onClearRefreshSummary = viewModel::clearRefreshSummary,
                    appearance = appearance,
                    onAppearanceChange = themePreferences::update,
                )
            }
        }
        if (savedInstanceState == null) acceptShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShare(intent)
    }

    private fun acceptShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(8192)
        val url = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).find(text)?.value
            ?.trimEnd('.', ',', '，', '。') ?: text.trim()
        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().take(80)
        lifecycleScope.launch {
            viewModel.state.first { !it.loading }
            viewModel.add(url, title)
        }
    }
}
