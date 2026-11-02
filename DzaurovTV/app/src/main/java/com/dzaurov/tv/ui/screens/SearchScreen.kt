package com.dzaurov.tv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.dzaurov.tv.ui.MainViewModel
import com.dzaurov.tv.ui.MenuSource
import com.dzaurov.tv.ui.components.DzIcons
import com.dzaurov.tv.ui.components.focusRing
import com.dzaurov.tv.ui.components.glass
import com.dzaurov.tv.ui.components.safeRequest
import com.dzaurov.tv.ui.components.tvClickable
import com.dzaurov.tv.ui.theme.Dz
import com.dzaurov.tv.ui.theme.dzStyle

/**
 * The search box is a normal focusable tile until you press OK; only then the on-screen
 * keyboard opens. That way moving through the page never pops the keyboard up by accident.
 */
@Composable
fun SearchScreen(vm: MainViewModel, perf: Boolean) {
    val context = LocalContext.current
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val starred = remember(prefs.favorites) { prefs.favorites.toSet() }
    val keyboard = LocalSoftwareKeyboardController.current

    val boxFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var editing by remember { mutableStateOf(false) }
    var boxFocused by remember { mutableStateOf(false) }
    val ring = animateFloatAsState(if (boxFocused || editing) 1f else 0f, tween(160), label = "searchRing")

    fun finishTyping() {
        editing = false
        keyboard?.hide()
        if (results.isNotEmpty()) firstResult.safeRequest() else boxFocus.safeRequest()
    }

    Column(Modifier.fillMaxSize().padding(top = 78.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .padding(start = 44.dp)
                .width(560.dp)
                .height(48.dp)
                .focusRequester(boxFocus)
                .onFocusChanged { boxFocused = it.isFocused }
                .tvClickable(enabled = !editing, onClick = { editing = true })
                .glass(24.dp)
                .drawBehind { focusRing(ring.value, size.height / 2f) }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(DzIcons.Search, contentDescription = null, tint = Dz.Ink, modifier = Modifier.size(18.dp))
                Box(Modifier.fillMaxWidth()) {
                    if (query.isEmpty() && !editing) {
                        Text("Search apps — press OK to type", style = dzStyle(16.sp, color = Dz.Ink3))
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        singleLine = true,
                        textStyle = dzStyle(18.sp, FontWeight.Medium),
                        cursorBrush = SolidColor(Dz.Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { finishTyping() }, onDone = { finishTyping() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFocus)
                            .focusProperties { canFocus = editing }
                            .onFocusChanged { if (!it.isFocused && editing) editing = false },
                    )
                }
            }
        }
        AppGrid(
            vm = vm,
            apps = results,
            perf = perf,
            starred = starred,
            emptyText = "No apps match “$query”.",
            onClick = { vm.open(context, it) },
            onLongClick = { vm.showMenu(it, MenuSource.Grid) },
            state = gridState,
            firstFocus = firstResult,
            takeFocusOnStart = false,
        )
    }

    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }
            fieldFocus.safeRequest()
            keyboard?.show()
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        boxFocus.safeRequest()
    }
    LaunchedEffect(Unit) {
        vm.focusRestore.collect { boxFocus.safeRequest() }
    }
}
