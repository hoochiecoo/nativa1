package com.example.camera_starter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Surface
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel
import io.flutter.view.TextureRegistry
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random // Просто для примера анализа

class MainActivity: FlutterActivity() {
    private val METHOD_CHANNEL = "com.example.camera/methods"
    private val EVENT_CHANNEL = "com.example.camera/events"

    private lateinit var cameraExecutor: ExecutorService
    private var textureEntry: TextureRegistry.SurfaceTextureEntry? = null
    
    // Ссылка на поток событий для отправки данных во Flutter
    private var eventSink: EventChannel.EventSink? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // 1. Настраиваем MethodChannel (Входящие команды от Flutter)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "startCamera") {
                if (allPermissionsGranted()) {
                    val textureId = startCamera(flutterEngine)
                    result.success(textureId)
                } else {
                    ActivityCompat.requestPermissions(
                        this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                    )
                    // Для простоты примера мы пробуем сразу, но в реальном app 
                    // нужно ждать onRequestPermissionsResult
                    result.error("PERMISSION_DENIED", "Camera permission needed", null)
                }
            } else {
                result.notImplemented()
            }
        }

        // 2. Настраиваем EventChannel (Исходящие данные во Flutter)
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                }
                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            }
        )

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera(flutterEngine: FlutterEngine): Long {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        // 1. Создаем SurfaceTextureEntry. Это "мост" для видео.
        // Flutter дает нам ID, мы рендерим в Surface, Flutter показывает виджет Texture(ID).
        textureEntry = flutterEngine.renderer.createSurfaceTexture()
        val surfaceTexture = textureEntry!!.surfaceTexture()
        // Устанавливаем размер буфера (важно для корректного отображения)
        surfaceTexture.setDefaultBufferSize(640, 480) 

        val textureId = textureEntry!!.id()

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 2. Настраиваем Preview (отображение)
            val preview = Preview.Builder().build()
            
            // Связываем CameraX Preview с Flutter SurfaceTexture
            preview.setSurfaceProvider { request ->
                val surface = Surface(surfaceTexture)
                request.provideSurface(surface, cameraExecutor) {
                    // Cleanup если поверхность закрылась
                    // surface.release() (управляется Flutter, аккуратно)
                }
            }

            // 3. Настраиваем ImageAnalysis (покадровая обработка)
            // Это работает параллельно с Preview
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        // === ВАШ КОД ОБРАБОТКИ КАДРА ЗДЕСЬ ===
                        processImageFrame(imageProxy)
                    }
                }

            // Выбираем камеру (задняя)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                // Привязываем жизненный цикл и use cases
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch(exc: Exception) {
                // Log error
            }

        }, ContextCompat.getMainExecutor(this))

        return textureId
    }

    // Симуляция тяжелой работы или ML анализа
    private fun processImageFrame(image: ImageProxy) {
        // Доступ к байтам: val buffer = image.planes[0].buffer
        
        // Пример: Вычисляем "среднюю яркость" (просто рандом для демо)
        // В реальном проекте тут будет OpenCV или ML Kit
        val fakeBrightness = Random.nextInt(0, 100)
        val timestamp = System.currentTimeMillis()

        // Отправляем результат в Main Thread, чтобы передать во Flutter
        runOnUiThread {
            eventSink?.success("Frame processed: ${timestamp} | Light: ${fakeBrightness}%")
        }

        // ОБЯЗАТЕЛЬНО закрыть кадр, иначе поток встанет!
        image.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // --- Permissions Helpers ---
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
