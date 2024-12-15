package com.example.journaldownloader

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var inputJournalId: EditText
    private lateinit var downloadButton: Button
    private lateinit var viewButton: Button
    private lateinit var deleteButton: Button
    private val client = OkHttpClient()
    private lateinit var journalDir: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Проверка разрешений
        checkPermissions()

        // Инициализация UI элементов
        inputJournalId = findViewById(R.id.inputJournalId)
        downloadButton = findViewById(R.id.downloadButton)
        viewButton = findViewById(R.id.viewButton)
        deleteButton = findViewById(R.id.deleteButton)

        // Создание директории для хранения файлов
        journalDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Journals")
        if (!journalDir.exists()) journalDir.mkdirs()

        // Слушатели кнопок
        downloadButton.setOnClickListener { downloadJournal() }
        viewButton.setOnClickListener { viewJournal() }
        deleteButton.setOnClickListener { deleteJournal() }

        // Показываем инструкцию после завершения создания активности
        val rootView = findViewById<View>(android.R.id.content)
        rootView.post {
            showPopupInstructions()
        }
    }

    private fun checkPermissions() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Разрешение на запись отклонено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadJournal() {
        val id = inputJournalId.text.toString()
        if (id.isEmpty()) {
            Toast.makeText(this, "Введите ID журнала", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "http://ntv.ifmo.ru/file/journal/$id.pdf"
        val file = File(journalDir, "$id.pdf")

        Thread {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.header("Content-Type") == "application/pdf") {
                        response.body?.let { body ->
                            FileOutputStream(file).use { outputStream ->
                                outputStream.write(body.bytes())
                            }
                            runOnUiThread {
                                Toast.makeText(this, "Файл загружен в ${file.absolutePath}", Toast.LENGTH_LONG).show()
                                viewButton.isEnabled = true
                                deleteButton.isEnabled = true
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun viewJournal() {
        val id = inputJournalId.text.toString()
        val file = File(journalDir, "$id.pdf")

        if (!file.exists()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Проверяем, может ли какой-либо Intent обработать этот запрос
        val resolveInfo = packageManager.queryIntentActivities(intent, 0)
        if (resolveInfo.isEmpty()) {
            Toast.makeText(this, "Нет приложения для открытия PDF", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка открытия файла: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("PDF_VIEW", "Ошибка: ${e.message}")
        }
    }


    private fun deleteJournal() {
        val id = inputJournalId.text.toString()
        val file = File(journalDir, "$id.pdf")
        if (file.exists() && file.delete()) {
            Toast.makeText(this, "Файл удален", Toast.LENGTH_SHORT).show()
            viewButton.isEnabled = false
            deleteButton.isEnabled = false

            // Логирование удаления файла
            Log.d("PDF_DELETE", "Файл успешно удалён: ${file.absolutePath}")
        } else {
            Toast.makeText(this, "Ошибка при удалении файла", Toast.LENGTH_SHORT).show()
            Log.e("PDF_DELETE", "Ошибка при удалении файла: ${file.absolutePath}")
        }
    }

    private fun showPopupInstructions() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val firstLaunch = prefs.getBoolean("firstLaunch", true)

        if (!firstLaunch) return

        // Создание PopupWindow
        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.popup_instructions, null)

        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        // Настройка содержимого PopupWindow
        val dontShowAgainCheckbox = popupView.findViewById<CheckBox>(R.id.dontShowAgainCheckbox)
        val okButton = popupView.findViewById<Button>(R.id.okButton)

        okButton.setOnClickListener {
            if (dontShowAgainCheckbox.isChecked) {
                val editor = prefs.edit()
                editor.putBoolean("firstLaunch", false)
                editor.apply()
            }
            popupWindow.dismiss()
        }

        // Показываем PopupWindow
        val rootView = findViewById<View>(android.R.id.content)
        popupWindow.elevation = 10f
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.color.transparent))
        popupWindow.showAtLocation(rootView, android.view.Gravity.CENTER, 0, 0)
    }

}