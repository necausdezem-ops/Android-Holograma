package com.stark.holoos

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private lateinit var voiceLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var contactsPermissionLauncher: ActivityResultLauncher<String>
    private var pendingContactQuery: String? = null
    private lateinit var homeRoleLauncher: ActivityResultLauncher<Intent>
    private var pendingWebPermission: PermissionRequest? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(1, 4, 9)
        window.navigationBarColor = android.graphics.Color.rgb(1, 4, 9)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

        voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val spoken = if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            } else ""
            val quoted = JSONObject.quote(spoken)
            if (::webView.isInitialized) {
                webView.post { webView.evaluateJavascript("window.holoNativeVoiceResult && window.holoNativeVoiceResult($quoted);", null) }
            }
        }

        homeRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Toast.makeText(this, if (result.resultCode == Activity.RESULT_OK) "Launcher padrão atualizado." else "Seleção de launcher cancelada.", Toast.LENGTH_SHORT).show()
        }

        contactsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val query = pendingContactQuery
            pendingContactQuery = null
            if (query != null) {
                if (granted) HoloBridge().findContactsByName(query) else {
                    webView.evaluateJavascript("window.holoContactResults && window.holoContactResults([], 'permission_denied');", null)
                    Toast.makeText(this, "Permissão de contatos negada.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingWebPermission
            pendingWebPermission = null
            if (request == null) return@registerForActivityResult
            if (granted && !isFinishing) {
                val allowed = request.resources.filter { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }.toTypedArray()
                if (allowed.isNotEmpty()) request.grant(allowed) else request.deny()
            } else {
                request.deny()
                Toast.makeText(this, "Permita a câmera para ativar o modo AR.", Toast.LENGTH_LONG).show()
            }
        }

        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale("pt", "BR")
                tts?.setSpeechRate(1.06f)
                tts?.setPitch(0.9f)
            }
        }

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(1, 4, 9))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            addJavascriptInterface(HoloBridge(), "HoloBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                    assetLoader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.host == "appassets.androidplatform.net") return false
                    openExternal(uri.toString())
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        val wantsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                        if (!wantsCamera) {
                            request.deny()
                            return@runOnUiThread
                        }
                        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                        } else {
                            pendingWebPermission?.deny()
                            pendingWebPermission = request
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest) {
                    if (pendingWebPermission === request) pendingWebPermission = null
                }
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        setContentView(webView)
        webView.loadUrl("https://appassets.androidplatform.net/assets/Holo.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized) {
                    webView.evaluateJavascript("if(typeof homeAll==='function')homeAll();", null)
                } else finish()
            }
        })
    }

    private fun openExternal(raw: String) {
        try {
            val uri = Uri.parse(raw)
            val intent = if (uri.scheme.equals("tel", true)) Intent(Intent.ACTION_DIAL, uri) else Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Não há aplicativo disponível para abrir esse link.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível abrir esse link.", Toast.LENGTH_SHORT).show()
        }
    }


    private inner class HoloBridge {
        @JavascriptInterface
        fun sendHologram(ip: String, pin: String, type: String, title: String, payload: String) {
            val host = ip.trim()
            val code = pin.trim()
            if (!host.matches(Regex("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$")) || code.length !in 4..8) {
                Toast.makeText(this@MainActivity, "Informe o IP local do PC e o PIN de 4 a 8 dígitos.", Toast.LENGTH_LONG).show()
                return
            }
            Thread {
                var message = "Falha ao transmitir. Verifique a rede e o PIN."
                try {
                    val connection = java.net.URL("http://$host:47821/holo").openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 3500
                    connection.readTimeout = 5000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val body = JSONObject().put("pin", code).put("type", type.take(20)).put("title", title.take(120)).put("payload", payload.take(12000)).toString()
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val statusCode = connection.responseCode
                    message = if (statusCode in 200..299) "Holograma transmitido ao PC." else if (statusCode == 401) "PIN incorreto. Confira o código no HoloBridge do PC." else "O PC recusou a transmissão (HTTP $statusCode)."
                    connection.disconnect()
                } catch (_: Exception) {
                    message = "Não foi possível conectar. Confirme IP, Wi-Fi e se o HoloBridge está aberto."
                }
                runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show() }
            }.start()
        }


        @JavascriptInterface
        fun startVoice() {
            runOnUiThread {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale um comando para o J.A.R.V.I.S.")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                try {
                    voiceLauncher.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    if (::webView.isInitialized) webView.evaluateJavascript("window.holoNativeVoiceResult && window.holoNativeVoiceResult('');", null)
                    Toast.makeText(this@MainActivity, "Reconhecimento de voz indisponível neste aparelho.", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun speak(message: String) {
            runOnUiThread {
                if (ttsReady) {
                    tts?.speak(message.take(1500), TextToSpeech.QUEUE_FLUSH, null, "holoos-" + System.nanoTime().toString())
                    webView.postDelayed({
                        webView.evaluateJavascript("window.holoNativeSpeechDone && window.holoNativeSpeechDone();", null)
                    }, (message.length * 75L).coerceIn(1200L, 12000L))
                } else {
                    webView.evaluateJavascript("window.holoNativeSpeechDone && window.holoNativeSpeechDone();", null)
                }
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            runOnUiThread {
                val uri = Uri.parse(url)
                if (uri.scheme in listOf("https", "http", "tel", "mailto", "geo")) openExternal(url)
                else Toast.makeText(this@MainActivity, "Link bloqueado por segurança.", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun requestHomeRole() {
            runOnUiThread {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val manager = getSystemService(RoleManager::class.java)
                        if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                            if (!manager.isRoleHeld(RoleManager.ROLE_HOME)) {
                                homeRoleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                            } else {
                                Toast.makeText(this@MainActivity, "STARK HoloOS já é o launcher padrão.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            startActivity(Intent("android.settings.HOME_SETTINGS"))
                        }
                    } else {
                        startActivity(Intent("android.settings.HOME_SETTINGS"))
                    }
                } catch (_: Exception) {
                    try { startActivity(Intent("android.settings.HOME_SETTINGS")) } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Abra Configurações > Apps padrão > App de início.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        @JavascriptInterface
        fun openAppByName(query: String) {
            runOnUiThread {
                val q = query.trim().lowercase(Locale.ROOT)
                if (q.isBlank()) return@runOnUiThread
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val matches = packageManager.queryIntentActivities(intent, 0)
                val match = matches.firstOrNull {
                    it.loadLabel(packageManager).toString().lowercase(Locale.ROOT) == q
                } ?: matches.firstOrNull {
                    it.loadLabel(packageManager).toString().lowercase(Locale.ROOT).contains(q)
                }
                val targetPackage = match?.activityInfo?.packageName
                val launch = targetPackage?.let { packageManager.getLaunchIntentForPackage(it) }
                if (launch != null) startActivity(launch)
                else Toast.makeText(this@MainActivity, "Não encontrei o aplicativo: $query", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun dialNumber(number: String) {
            runOnUiThread {
                val digits = number.filter { it.isDigit() || it == '+' }
                if (digits.count { it.isDigit() } < 7) {
                    Toast.makeText(this@MainActivity, "Número de telefone inválido.", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))) }
                catch (_: Exception) { Toast.makeText(this@MainActivity, "Não foi possível abrir o discador.", Toast.LENGTH_SHORT).show() }
            }
        }

        @JavascriptInterface
        fun findContactsByName(query: String) {
            runOnUiThread {
                val q = query.trim()
                if (q.isBlank()) {
                    webView.evaluateJavascript("window.holoContactResults && window.holoContactResults([], 'empty');", null)
                    return@runOnUiThread
                }
                if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    pendingContactQuery = q
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                } else sendContactResults(q)
            }
        }

        private fun sendContactResults(query: String) {
            val results = JSONArray()
            try {
                val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$query%"),
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
                )?.use { cursor ->
                    val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (cursor.moveToNext() && results.length() < 30) {
                        val name = cursor.getString(nameCol) ?: continue
                        val number = cursor.getString(numberCol) ?: continue
                        if (number.isNotBlank()) results.put(JSONObject().put("name", name).put("number", number))
                    }
                }
                val payload = JSONObject.quote(results.toString())
                webView.evaluateJavascript("window.holoContactResults && window.holoContactResults(JSON.parse($payload), 'ok');", null)
            } catch (_: Exception) {
                webView.evaluateJavascript("window.holoContactResults && window.holoContactResults([], 'error');", null)
            }
        }

        @JavascriptInterface
        fun sendWhatsAppMessage(number: String, message: String) {
            runOnUiThread {
                val digits = number.filter { it.isDigit() }
                if (digits.length < 10 || message.isBlank()) {
                    Toast.makeText(this@MainActivity, "Informe o número com DDI e a mensagem.", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                try {
                    val uri = Uri.parse("https://wa.me/$digits?text=" + Uri.encode(message))
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Não foi possível abrir o WhatsApp.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun getInstalledApps(): String {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { info ->
                    val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                    if (appInfo.packageName == packageName) return@mapNotNull null
                    val iconData = try {
                        val drawable = info.loadIcon(packageManager)
                        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        drawable.setBounds(0, 0, 96, 96)
                        drawable.draw(canvas)
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
                        bitmap.recycle()
                        Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    } catch (_: Exception) { "" }
                    JSONObject().apply {
                        put("label", info.loadLabel(packageManager).toString())
                        put("packageName", appInfo.packageName)
                        put("iconData", iconData)
                    }
                }
                .distinctBy { it.optString("packageName") }
                .sortedBy { it.optString("label").lowercase(Locale.ROOT) }
            return JSONArray(apps).toString()
        }

        @JavascriptInterface
        fun launchApp(packageName: String) {
            runOnUiThread {
                try {
                    if (packageName == this@MainActivity.packageName) return@runOnUiThread
                    val launch = packageManager.getLaunchIntentForPackage(packageName)
                    if (launch != null) startActivity(launch)
                    else Toast.makeText(this@MainActivity, "Esse aplicativo não tem um atalho de inicialização.", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Não foi possível abrir esse aplicativo.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun getGeminiApiKey(): String {
            return getSharedPreferences("holo_private_settings", Context.MODE_PRIVATE)
                .getString("gemini_api_key", "").orEmpty()
        }

        @JavascriptInterface
        fun setGeminiApiKey(key: String) {
            getSharedPreferences("holo_private_settings", Context.MODE_PRIVATE)
                .edit().putString("gemini_api_key", key.trim()).apply()
        }

        @JavascriptInterface
        fun batteryPercent(): Int {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) return (level * 100 / scale).coerceIn(0, 100)
            val manager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }

        @JavascriptInterface
        fun batteryCharging(): Boolean {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }

        @JavascriptInterface
        fun vibrate(pattern: String) {
            try {
                val values = JSONArray(pattern)
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (values.length() > 1) {
                    val timings = LongArray(values.length()) { values.optLong(it, 0L).coerceIn(0L, 1000L) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                    else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(timings, -1)
                    }
                } else {
                    val duration = if (values.length() == 1) values.optLong(0, 0L) else pattern.toLongOrNull() ?: 0L
                    if (duration > 0) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(duration.coerceAtMost(1000L), VibrationEffect.DEFAULT_AMPLITUDE))
                        else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(duration.coerceAtMost(1000L))
                        }
                    }
                }
            } catch (_: Exception) {
                val duration = pattern.toLongOrNull() ?: return
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(duration.coerceAtMost(1000L), VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    override fun onDestroy() {
        pendingWebPermission?.deny()
        pendingWebPermission = null
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.removeJavascriptInterface("HoloBridge")
            webView.destroy()
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
