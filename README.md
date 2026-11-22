# PS Media Downloader Pro · Android (Kotlin)

Aplicación Android beta para Pepe Simancas Capa. Código principal **100 % Kotlin**, sin actividad Java duplicada.

## Estado del proyecto
- Kotlin 2.0.21 + Android Gradle Plugin 8.7.3
- Java 17 usado **solo como JDK de compilación** (Kotlin compila a bytecode de JVM)
- Interfaz de prueba Android con descarga MP4 y extracción MP3 mediante yt-dlp-android y FFmpeg
- Barra de progreso, cancelación y guardado en Descargas/PS Media Downloader
- Compatibilidad real con plataformas y dispositivos **pendiente de validación**
- Marca tipográfica provisional. El archivo PNG oficial aún no está incorporado como recurso Android.

## Compilar APK de prueba
En GitHub → Acciones → Compilar APK Android, abre la última ejecución.
Si termina correctamente, en Resumen → Artefactos estará **PS-Media-Downloader-Android**.
Dentro del ZIP estará `app-debug.apk`.

El paso SDK Android falló en una ejecución anterior. Se ajustó el flujo para usar el SDK preinstalado, pero no se ha verificado todavía el resultado de esta nueva compilación.

Solo para contenido propio o descargas autorizadas. Ninguna opción evita restricciones de acceso o DRM.
