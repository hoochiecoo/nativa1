import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CameraX Texture',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: const CameraScreen(),
    );
  }
}

class CameraScreen extends StatefulWidget {
  const CameraScreen({super.key});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  // Канал для команд (start/stop)
  static const MethodChannel _methodChannel = MethodChannel('com.example.camera/methods');
  // Канал для получения данных анализа (например, результаты ML)
  static const EventChannel _eventChannel = EventChannel('com.example.camera/events');

  int? _textureId;
  String _analysisResult = "Waiting for data...";
  String _error = "";

  @override
  void initState() {
    super.initState();
    _initializeCamera();
    _subscribeToAnalysis();
  }

  Future<void> _initializeCamera() async {
    try {
      // Вызываем нативный метод startCamera, который вернет ID текстуры
      final int textureId = await _methodChannel.invokeMethod('startCamera');
      setState(() {
        _textureId = textureId;
      });
    } on PlatformException catch (e) {
      setState(() {
        _error = "Failed to open camera: ${e.message}";
      });
    }
  }

  void _subscribeToAnalysis() {
    _eventChannel.receiveBroadcastStream().listen((event) {
      // Обновляем UI данными из нативного анализатора
      setState(() {
        _analysisResult = event.toString();
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("CameraX + Texture")),
      body: Column(
        children: [
          // Область камеры
          Expanded(
            child: Container(
              color: Colors.black,
              child: Center(
                child: _textureId == null
                    ? (_error.isNotEmpty 
                        ? Text(_error, style: const TextStyle(color: Colors.red)) 
                        : const CircularProgressIndicator())
                    : AspectRatio(
                        aspectRatio: 3.0 / 4.0, // Соотношение сторон по умолчанию
                        child: Texture(textureId: _textureId!),
                      ),
              ),
            ),
          ),
          // Панель данных
          Container(
            padding: const EdgeInsets.all(20),
            color: Colors.white,
            width: double.infinity,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text("Analysis Data (from Native):", 
                  style: TextStyle(fontWeight: FontWeight.bold)),
                const SizedBox(height: 5),
                Text(_analysisResult, 
                  style: const TextStyle(fontSize: 18, color: Colors.blue)),
              ],
            ),
          )
        ],
      ),
    );
  }
}
