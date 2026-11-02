package com.dzaurov.tv.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.dzaurov.tv.data.Photo
import com.dzaurov.tv.data.Photos
import com.dzaurov.tv.ui.MainViewModel
import com.dzaurov.tv.ui.components.FullWidthNote
import com.dzaurov.tv.ui.components.PageTitle
import com.dzaurov.tv.ui.components.focusRing
import com.dzaurov.tv.ui.components.safeRequest
import com.dzaurov.tv.ui.components.tvClickable
import com.dzaurov.tv.ui.theme.Dz
import com.dzaurov.tv.ui.theme.dzStyle

private val imagePermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

/** Pick a photo from Downloads or a USB stick to use as the background. */
@Composable
fun PhotosScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val importing by vm.importing.collectAsStateWithLifecycle()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, imagePermission) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(imagePermission)
    }
    val photos by produceState<List<Photo>?>(null, granted) {
        value = if (granted) Photos.list(context) else null
    }
    val first = remember { FocusRequester() }

    Column(Modifier.fillMaxSize().padding(top = 78.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageTitle("Choose a photo", if (importing) "Preparing your background…" else "From Downloads or a USB stick · 16:9 photos look best")
        val list = photos
        when {
            !granted && asked -> FullWidthNote("Dzaurov TV needs permission to see your photos. Press Back, then try again and choose Allow.")
            !granted -> FullWidthNote("Asking for permission to see your photos…")
            list == null -> FullWidthNote("Looking for photos…")
            list.isEmpty() -> FullWidthNote(
                "No photos found. Copy a JPG into the Download folder or onto a USB stick, plug it in, wait a minute and open this page again."
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 14.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(list, key = { _, p -> p.uri.toString() }) { i, photo ->
                    PhotoTile(
                        photo = photo,
                        onClick = { vm.importPhoto(context, photo) },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
    LaunchedEffect(photos) {
        if (!photos.isNullOrEmpty()) {
            withFrameNanos { }
            first.safeRequest()
        }
    }
    LaunchedEffect(Unit) {
        vm.focusRestore.collect { first.safeRequest() }
    }
}

@Composable
private fun PhotoTile(photo: Photo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(null, photo.uri) { value = Photos.thumbnail(context, photo.uri) }
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (focused) 1.08f else 1f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "photo")
    val ring = animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "photoRing")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier
                .size(160.dp, 90.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .onFocusChanged { focused = it.isFocused }
                .tvClickable(onClick = onClick)
                .drawBehind { focusRing(ring.value, 10.dp.toPx()) }
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1F28)),
            contentAlignment = Alignment.Center,
        ) {
            thumb?.let {
                Image(it, contentDescription = photo.name, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            if (photo.folder.isNotEmpty()) "${photo.folder} · ${photo.name}" else photo.name,
            style = dzStyle(10.sp, color = Dz.Ink2),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(160.dp),
        )
    }
}
