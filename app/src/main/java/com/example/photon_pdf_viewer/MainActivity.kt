package com.example.photon_pdf_viewer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.photon_pdf_viewer.ui.theme.PhotonPDFViewerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val intentUri = intent?.data

        setContent {
            PhotonPDFViewerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PdfViewerScreen(initialUri = intentUri)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(initialUri: Uri? = null) {
    val context = LocalContext.current
    var pdfUri by remember { mutableStateOf(initialUri) }
    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            pdfUri = it
        }
    }

    LaunchedEffect(pdfUri) {
        pdfUri?.let { uri ->
            isLoading = true
            errorMessage = null
            try {
                pdfPages = withContext(Dispatchers.IO) {
                    renderPdfPages(context.contentResolver.openFileDescriptor(uri, "r")!!)
                }
            } catch (e: Exception) {
                errorMessage = "PDF yüklenirken hata oluştu: ${e.message}"
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photon PDF Viewer") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_upload),
                            contentDescription = "PDF Aç"
                        )
                    }
                    IconButton(onClick = { scale = (scale + 0.25f).coerceAtMost(3f) }) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_zoom),
                            contentDescription = "Yakınlaştır"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                errorMessage != null -> {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                pdfPages.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_gallery),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "PDF dosyası seçmek için yukarıdaki butona tıklayın",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(pdfPages) { page ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale
                                    ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Image(
                                    bitmap = page.asImageBitmap(),
                                    contentDescription = "PDF Sayfası",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun renderPdfPages(fileDescriptor: ParcelFileDescriptor): List<Bitmap> {
    val pdfRenderer = PdfRenderer(fileDescriptor)
    val pages = mutableListOf<Bitmap>()

    for (i in 0 until pdfRenderer.pageCount) {
        val page = pdfRenderer.openPage(i)
        val bitmap = Bitmap.createBitmap(
            page.width * 2,
            page.height * 2,
            Bitmap.Config.ARGB_8888
        )
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        pages.add(bitmap)
        page.close()
    }

    pdfRenderer.close()
    fileDescriptor.close()

    return pages
}
