package com.example.counseling

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var images by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }
    var message by remember { mutableStateOf("사진 권한을 허용하면 갤러리 이미지를 불러올 수 있습니다.") }
    var access by remember { mutableStateOf(galleryAccess(context)) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    fun loadImages() {
        scope.launch {
            access = galleryAccess(context)
            message = "갤러리 이미지를 불러오는 중..."
            images = queryGalleryImages(context)
            val accessLabel = if (access == GalleryAccess.Partial) "선택한 사진" else "전체 사진"
            message = if (images.isEmpty()) {
                "불러올 수 있는 이미지가 없습니다."
            } else {
                "$accessLabel 기준으로 이미지 ${images.size}장을 불러왔습니다."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            access = galleryAccess(context)
            if (access != GalleryAccess.None) {
                loadImages()
            } else {
                message = "사진 권한이 허용되지 않았습니다. 시스템 권한 창에서 전체 사진 접근을 허용해 주세요."
            }
        },
    )

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("사진 접근 권한") },
            text = {
                Text("전체 사진을 불러오거나, 선택한 사진 목록만 다시 조정할 수 있습니다. 권한을 줄이거나 완전히 회수하려면 권한 설정을 여세요.")
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showPermissionDialog = false
                            permissionLauncher.launch(fullGalleryPermissionsForDevice())
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("전체 권한 요청")
                    }
                    OutlinedButton(
                        onClick = {
                            showPermissionDialog = false
                            permissionLauncher.launch(partialGalleryPermissionsForDevice())
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("일부 액세스 변경")
                    }
                    OutlinedButton(
                        onClick = { showPermissionDialog = false },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("거부하거나 유지")
                    }
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        access = galleryAccess(context)
        if (access != GalleryAccess.None) loadImages()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    access = galleryAccess(context)
                    if (access == GalleryAccess.Full) loadImages() else showPermissionDialog = true
                },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    when (access) {
                        GalleryAccess.None -> "사진 권한"
                        GalleryAccess.Partial -> "이미지 추가"
                        GalleryAccess.Full -> "새로고침"
                    },
                )
            }
            OutlinedButton(
                onClick = { openAppSettings(context) },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (access == GalleryAccess.Full) "권한 줄이기" else "권한 설정")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(images, key = { it.uri.toString() }) { image ->
                GalleryTile(image)
            }
        }
    }
}

@Composable
fun GalleryTile(image: GalleryImage) {
    val context = LocalContext.current
    var bitmap by remember(image.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(image.uri) {
        bitmap = loadThumbnail(context, image.uri)
    }

    Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            val loaded = bitmap
            if (loaded == null) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            } else {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = image.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}
