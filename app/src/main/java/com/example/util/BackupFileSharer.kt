package com.example.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Comparte la copia como ARCHIVO .json. Antes se mandaba como texto (EXTRA_TEXT) y con
 * historiales grandes podia pasar el limite de Android para enviar datos (cerca de 1 MB)
 * y cerrar la app.
 */
object BackupFileSharer {

    /** Carpeta dentro de cacheDir. Debe coincidir con res/xml/file_paths.xml. */
    private const val SHARE_DIR = "copias"

    /** Nombre sugerido para una copia: habitflow_copia_2026-09-30.json */
    fun backupFileName(today: String = DateUtils.getTodayDateString()): String = "habitflow_copia_$today.json"

    /** Nombre sugerido para la exportacion de registros: habitflow_registros_2026-09-30.csv */
    fun csvFileName(today: String = DateUtils.getTodayDateString()): String = "habitflow_registros_$today.csv"

    /** Escribe la copia en cache y borra la del envio anterior. Llamar fuera del hilo principal. */
    fun writeShareFile(context: Context, json: String, fileName: String = backupFileName()): File {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, fileName).apply { writeText(json, Charsets.UTF_8) }
    }

    /** Menu de compartir de Android con el archivo adjunto y permiso de lectura para la app elegida. */
    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Copia de HabitFlow")
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Enviar copia")
    }
}
