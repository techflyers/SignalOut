package com.signalout.android.ui.media

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.signalout.android.R
import com.signalout.android.features.file.FileUtils
import com.signalout.android.model.SignaloutFilePacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dialog for handling received file messages in modern chat style
 */
@Composable
fun FileViewerDialog(
    packet: SignaloutFilePacket,
    onDismiss: () -> Unit,
    onSaveToDevice: (ByteArray, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // File received header
                Text(
                    text = stringResource(R.string.file_viewer_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // File info
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.file_viewer_name, packet.fileName),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.file_viewer_size, FileUtils.formatFileSize(packet.fileSize)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.file_viewer_type, packet.mimeType),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Open button - opens file with system viewer
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                openFileWithViewer(context, packet)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(stringResource(R.string.file_viewer_open_save))
                    }

                    // Save button - saves to Downloads
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val saved = saveFileToDownloads(context, packet)
                                if (saved) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Saved to Downloads: ${packet.fileName}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Save")
                    }
                }

                // Dismiss button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        stringResource(R.string.close_with_emoji),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
} 

/**
 * Opens a file with the appropriate system viewer using FileProvider
 */
private fun openFileWithViewer(context: Context, packet: SignaloutFilePacket) {
    try {
        // Save to temp file first
        val tempFile = File.createTempFile(
            "signalout_", 
            ".${packet.fileName.substringAfterLast(".", "tmp")}", 
            context.cacheDir
        )
        tempFile.writeBytes(packet.content)
        tempFile.deleteOnExit()

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, packet.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app found to open this file type", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Saves a file to the Downloads directory using MediaStore (API 29+) or direct file write (pre-29)
 */
private suspend fun saveFileToDownloads(context: Context, packet: SignaloutFilePacket): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+: Use MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, packet.fileName)
                    put(MediaStore.Downloads.MIME_TYPE, packet.mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SignalOut")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return@withContext false

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(packet.content)
                }

                // Mark as complete
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                true
            } else {
                // Pre-API 29: Direct file write to Downloads
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val signaloutDir = File(downloadsDir, "SignalOut")
                signaloutDir.mkdirs()

                val outputFile = File(signaloutDir, packet.fileName)
                outputFile.writeBytes(packet.content)
                true
            }
        } catch (e: Exception) {
            android.util.Log.e("FileViewer", "Failed to save file: ${e.message}", e)
            false
        }
    }
}
