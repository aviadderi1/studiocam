package com.aviad.studiocam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.aviad.studiocam.audio.AudioDeviceManager
import com.aviad.studiocam.audio.NativeAudioEngine
import com.aviad.studiocam.ui.ChannelState
import com.aviad.studiocam.ui.ChannelStrip
import com.aviad.studiocam.ui.theme.RecRed
import com.aviad.studiocam.ui.theme.StudioCamTheme
import com.aviad.studiocam.ui.theme.TextSecondary
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudioCamTheme {
                Surface(color = Color.Black) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasAudio by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasCamera = result[Manifest.permission.CAMERA] ?: hasCamera
        hasAudio = result[Manifest.permission.RECORD_AUDIO] ?: hasAudio
    }

    LaunchedEffect(Unit) {
        if (!hasCamera || !hasAudio) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    if (hasCamera && hasAudio) {
        RecordingScreen()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("נדרשת הרשאה למצלמה ולמיקרופון", color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val audioDeviceManager = remember { AudioDeviceManager(context) }
    val inputOptions = remember { mutableStateListOf<com.aviad.studiocam.audio.InputOption>() }
    LaunchedEffect(Unit) { inputOptions.addAll(audioDeviceManager.listInputOptions()) }

    val channelA = remember { ChannelState(initialName = "מיקרופון", icon = "mic") }
    val channelB = remember { ChannelState(initialName = "גיטרה", icon = "guitar") }

    // Native audio engine: starts once an input is selected, handles capture, DSP
    // (reverb/delay), live monitoring to the headphone output, and level metering.
    var engineStarted by remember { mutableStateOf(false) }
    LaunchedEffect(channelA.selectedInput, channelB.selectedInput) {
        val deviceId = (channelA.selectedInput ?: channelB.selectedInput)?.deviceInfo?.id
        if (deviceId != null && !engineStarted) {
            engineStarted = NativeAudioEngine.start(deviceId)
        }
    }

    // Push current channel params to the native engine and pull live levels back,
    // a few times a second - cheap enough and avoids juggling many separate keys.
    LaunchedEffect(engineStarted) {
        while (engineStarted) {
            NativeAudioEngine.setChannelParams(
                channel = 0,
                physicalIndex = channelA.selectedInput?.channelIndex ?: 0,
                volume = channelA.volume / 100f,
                pan = channelA.pan / 50f,
                reverbOn = channelA.reverb.enabled,
                reverbMix = channelA.reverb.mix / 100f,
                reverbSize = channelA.reverb.param2 / 100f,
                delayOn = channelA.delay.enabled,
                delayMix = channelA.delay.mix / 100f,
                delayFeedback = channelA.delay.param2 / 100f
            )
            NativeAudioEngine.setChannelParams(
                channel = 1,
                physicalIndex = channelB.selectedInput?.channelIndex ?: 1,
                volume = channelB.volume / 100f,
                pan = channelB.pan / 50f,
                reverbOn = channelB.reverb.enabled,
                reverbMix = channelB.reverb.mix / 100f,
                reverbSize = channelB.reverb.param2 / 100f,
                delayOn = channelB.delay.enabled,
                delayMix = channelB.delay.mix / 100f,
                delayFeedback = channelB.delay.param2 / 100f
            )
            channelA.level = NativeAudioEngine.getLevel(0)
            channelB.level = NativeAudioEngine.getLevel(1)
            kotlinx.coroutines.delay(60)
        }
    }

    DisposableEffect(Unit) {
        onDispose { NativeAudioEngine.stop() }
    }

    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewRef by remember { mutableStateOf<Preview?>(null) }

    fun bindCamera() {
        val cameraProvider = cameraProviderRef ?: return
        val preview = previewRef ?: return
        val capture = videoCapture ?: return
        val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
    }

    fun startRecording() {
        val capture = videoCapture ?: return
        val name = "StudioCam_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/StudioCam")
            }
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        // Video still records silently for now - the processed channel audio is
        // captured in parallel to a WAV file by the native engine. Muxing the two
        // together into one file is the next build.
        activeRecording = capture.output
            .prepareRecording(context, outputOptions)
            .start(ContextCompat.getMainExecutor(context)) { }

        if (engineStarted) {
            val wavPath = context.cacheDir.absolutePath + "/studiocam_audio_${System.currentTimeMillis()}.wav"
            NativeAudioEngine.startFileRecording(wavPath)
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
        if (engineStarted) {
            NativeAudioEngine.stopFileRecording()
        }
    }

    LaunchedEffect(isRecording) {
        elapsedSeconds = 0
        if (isRecording) {
            startRecording()
        } else {
            stopRecording()
        }
        while (isRecording) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds++
        }
    }

    val sheetState = rememberModalBottomSheetState()
    var showMixer by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val recorder = Recorder.Builder().build()
                    val capture = VideoCapture.withOutput(recorder)
                    videoCapture = capture
                    cameraProviderRef = cameraProvider
                    previewRef = preview
                    val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        capture
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Top overlay: rec indicator + timer
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRecording) {
                    Box(Modifier.size(6.dp).background(RecRed, CircleShape))
                    Spacer(Modifier.width(6.dp))
                }
                Text(formatTime(elapsedSeconds), color = Color.White, fontSize = 11.sp)
            }
            Text("4K · 30", color = TextSecondary, fontSize = 10.sp)
        }

        // Camera flip button - top right corner
        IconButton(
            onClick = {
                useFrontCamera = !useFrontCamera
                bindCamera()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 44.dp, end = 12.dp)
                .size(36.dp)
        ) {
            Icon(Icons.Default.Cameraswitch, contentDescription = "החלף מצלמה", tint = Color.White)
        }

        // Bottom controls: mixer toggle + record button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                IconButton(
                    onClick = { showMixer = true },
                    modifier = Modifier.size(36.dp).background(Color.Transparent, CircleShape)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "פתח מיקסר אודיו", tint = Color.White)
                }

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color.Transparent, CircleShape)
                        .then(Modifier)
                        .clickableRecord { isRecording = !isRecording },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(RecRed, if (isRecording) androidx.compose.foundation.shape.RoundedCornerShape(8.dp) else CircleShape)
                    )
                }

                Spacer(Modifier.size(36.dp))
            }
        }
    }

    if (showMixer) {
        var selectedTab by remember { mutableStateOf(0) }
        ModalBottomSheet(onDismissRequest = { showMixer = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("AUDIO MIXER", color = TextSecondary, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
                }
                Spacer(Modifier.height(10.dp))
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(channelA.name) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(channelB.name) })
                }
                Spacer(Modifier.height(12.dp))
                if (selectedTab == 0) {
                    ChannelStrip(state = channelA, availableInputs = inputOptions)
                } else {
                    ChannelStrip(state = channelB, availableInputs = inputOptions)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

@Composable
private fun Modifier.clickableRecord(onClick: () -> Unit): Modifier {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.then(
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    )
}
