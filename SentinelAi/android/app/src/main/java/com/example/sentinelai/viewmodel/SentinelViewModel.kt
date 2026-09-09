package com.example.sentinelai.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sentinelai.core.CloudLookup
import com.example.sentinelai.core.CloudResult
import com.example.sentinelai.core.Database
import com.example.sentinelai.core.DetectionResult
import com.example.sentinelai.core.DeviceSecurity
import com.example.sentinelai.core.PrivacyAudit
import com.example.sentinelai.core.AppAuditEntry
import com.example.sentinelai.core.Quarantine
import com.example.sentinelai.core.QuarantineRecord
import com.example.sentinelai.core.RealtimeEvent
import com.example.sentinelai.core.RealtimeMonitorService
import com.example.sentinelai.core.ScannableFile
import com.example.sentinelai.core.Scanner
import com.example.sentinelai.core.Signatures
import com.example.sentinelai.core.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashboardState(
    val filesScannedToday: Int = 0,
    val threatsToday: Int = 0,
    val quarantineCount: Int = 0,
    val signatureVersion: String = Signatures.VERSION,
    val realtimeActive: Boolean = false
)

data class ScanState(
    val isScanning: Boolean = false,
    val filesScanned: Int = 0,
    val results: List<DetectionResult> = emptyList(),
    val statusText: String = ""
)

data class CloudState(
    val apiKey: String = "",
    val cloudEnabled: Boolean = false,
    val configured: Boolean = false,
    val testResult: String = ""
)

class SentinelViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Database(application)
    private val quarantine = Quarantine(application, db)
    val settings = SettingsStore(application)

    private val _dashboard = MutableStateFlow(DashboardState())
    val dashboard: StateFlow<DashboardState> = _dashboard.asStateFlow()

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // Tracks whether the current scanState.results came from a SAF tree
    // (needs that Uri to locate the document again) or the local protected
    // folder (needs only the absolute path) — read by the UI to route the
    // "Metti in quarantena" action to the matching quarantine method.
    private val _lastScanTreeUri = MutableStateFlow<Uri?>(null)
    val lastScanTreeUri: StateFlow<Uri?> = _lastScanTreeUri.asStateFlow()

    private val _quarantineItems = MutableStateFlow<List<QuarantineRecord>>(emptyList())
    val quarantineItems: StateFlow<List<QuarantineRecord>> = _quarantineItems.asStateFlow()

    private val _privacyApps = MutableStateFlow<List<AppAuditEntry>>(emptyList())
    val privacyApps: StateFlow<List<AppAuditEntry>> = _privacyApps.asStateFlow()

    private val _screenLockStatus = MutableStateFlow("verifica…")
    val screenLockStatus: StateFlow<String> = _screenLockStatus.asStateFlow()

    private val _playProtectStatus = MutableStateFlow("verifica…")
    val playProtectStatus: StateFlow<String> = _playProtectStatus.asStateFlow()

    private val _cloudState = MutableStateFlow(CloudState())
    val cloudState: StateFlow<CloudState> = _cloudState.asStateFlow()

    private val _realtimeFeed = MutableStateFlow<List<String>>(emptyList())
    val realtimeFeed: StateFlow<List<String>> = _realtimeFeed.asStateFlow()

    init {
        refreshDashboard()
        viewModelScope.launch {
            settings.vtApiKey.collect { key ->
                _cloudState.value = _cloudState.value.copy(apiKey = key)
            }
        }
        viewModelScope.launch {
            settings.cloudLookupEnabled.collect { enabled ->
                _cloudState.value = _cloudState.value.copy(cloudEnabled = enabled)
            }
        }
        viewModelScope.launch {
            RealtimeMonitorService.events.collect { event ->
                when (event) {
                    is RealtimeEvent.FileScanned -> {
                        val name = event.result.path.substringAfterLast('/')
                        appendFeed("$name — ${verdictLabel(event.result.verdict)} (${event.result.riskScore}/100)")
                    }
                    is RealtimeEvent.ThreatBlocked -> {
                        val name = event.result.path.substringAfterLast('/')
                        appendFeed("🛑 Bloccato e messo in quarantena: $name")
                        refreshDashboard()
                        refreshQuarantine()
                    }
                }
            }
        }
    }

    private fun appendFeed(line: String) {
        _realtimeFeed.value = (listOf(line) + _realtimeFeed.value).take(100)
    }

    fun verdictLabel(verdict: String): String = when (verdict) {
        "malicious" -> "Minaccia rilevata"
        "suspicious" -> "Sospetto"
        else -> "Pulito"
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) {
                Triple(db.filesScannedToday(), db.threatsToday(), db.quarantineCount())
            }
            _dashboard.value = _dashboard.value.copy(
                filesScannedToday = stats.first,
                threatsToday = stats.second,
                quarantineCount = stats.third,
                realtimeActive = RealtimeMonitorService.isRunning
            )
        }
    }

    // ---------------- Scan ----------------

    fun scanTree(treeUri: Uri) {
        viewModelScope.launch {
            _lastScanTreeUri.value = treeUri
            _scanState.value = ScanState(isScanning = true, statusText = "Scansione in corso…")
            val context = getApplication<Application>()
            val root = DocumentFile.fromTreeUri(context, treeUri)
            val files = mutableListOf<DocumentFile>()
            collectFiles(root, files)

            var scanned = 0
            val results = mutableListOf<DetectionResult>()
            for (doc in files) {
                val scannable = ScannableFile(
                    displayPath = doc.name ?: "sconosciuto",
                    name = doc.name ?: "sconosciuto",
                    size = doc.length(),
                    opener = { context.contentResolver.openInputStream(doc.uri)!! }
                )
                val result = withContext(Dispatchers.IO) { Scanner.scanFile(scannable) }
                scanned++
                if (result.error.isEmpty()) {
                    if (result.isFlagged) {
                        db.addDetection(result, source = "manual")
                        results.add(result)
                    }
                }
                _scanState.value = _scanState.value.copy(
                    filesScanned = scanned,
                    statusText = "File analizzati: $scanned",
                    results = results.toList()
                )
            }
            db.recordScan(scanned)
            _scanState.value = _scanState.value.copy(
                isScanning = false,
                statusText = "Scansione completata: $scanned file analizzati, ${results.size} minacce rilevate"
            )
            refreshDashboard()
        }
    }

    private fun collectFiles(dir: DocumentFile?, out: MutableList<DocumentFile>) {
        if (dir == null) return
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                collectFiles(child, out)
            } else if (child.isFile) {
                out.add(child)
            }
        }
    }

    /**
     * "Scansione rapida" needs a folder it can scan immediately, with no
     * SAF picker round-trip. Android's scoped storage means the only
     * folder a normal app can read without that prompt is its own sandbox,
     * so this scans the same protected folder shown on the Realtime screen
     * rather than silently doing nothing.
     */
    fun quickScanProtectedFolder() {
        viewModelScope.launch {
            _lastScanTreeUri.value = null
            _scanState.value = ScanState(isScanning = true, statusText = "Scansione rapida in corso…")
            val dir = RealtimeMonitorService.protectedDir(getApplication())
            val files = dir.walkTopDown().filter { it.isFile }.toList()

            var scanned = 0
            val results = mutableListOf<DetectionResult>()
            for (file in files) {
                val scannable = ScannableFile(
                    displayPath = file.absolutePath,
                    name = file.name,
                    size = file.length(),
                    opener = { file.inputStream() }
                )
                val result = withContext(Dispatchers.IO) { Scanner.scanFile(scannable) }
                scanned++
                if (result.error.isEmpty() && result.isFlagged) {
                    db.addDetection(result, source = "manual")
                    results.add(result)
                }
                _scanState.value = _scanState.value.copy(
                    filesScanned = scanned,
                    statusText = "File analizzati: $scanned",
                    results = results.toList()
                )
            }
            db.recordScan(scanned)
            _scanState.value = _scanState.value.copy(
                isScanning = false,
                statusText = if (scanned == 0)
                    "Cartella protetta vuota. Aggiungi file da Tempo Reale, oppure usa \"Scegli cartella\" per scansionarne un'altra."
                else
                    "Scansione rapida completata: $scanned file analizzati, ${results.size} minacce rilevate"
            )
            refreshDashboard()
        }
    }

    fun quarantineScanResult(result: DetectionResult, treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val root = DocumentFile.fromTreeUri(context, treeUri)
            val doc = findDocument(root, result.path)
            if (doc != null) {
                try {
                    quarantine.quarantineFile(
                        originalPath = result.path,
                        threatName = result.threatName,
                        riskScore = result.riskScore,
                        opener = { context.contentResolver.openInputStream(doc.uri)!! },
                        deleteOriginal = { doc.delete() }
                    )
                } catch (_: Exception) {
                }
            }
            refreshDashboard()
            refreshQuarantine()
        }
    }

    /** Quarantine a result produced by quickScanProtectedFolder(), addressed
     * by absolute path rather than a SAF tree document. */
    fun quarantineLocalFile(result: DetectionResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = java.io.File(result.path)
            try {
                quarantine.quarantineFile(
                    originalPath = result.path,
                    threatName = result.threatName,
                    riskScore = result.riskScore,
                    opener = { file.inputStream() },
                    deleteOriginal = { file.delete() }
                )
            } catch (_: Exception) {
            }
            refreshDashboard()
            refreshQuarantine()
        }
    }

    private fun findDocument(dir: DocumentFile?, name: String): DocumentFile? {
        if (dir == null) return null
        for (child in dir.listFiles()) {
            if (child.isFile && child.name == name) return child
            if (child.isDirectory) {
                findDocument(child, name)?.let { return it }
            }
        }
        return null
    }

    // ---------------- Realtime ----------------

    fun startRealtime(threshold: Int) {
        val context = getApplication<Application>()
        val intent = Intent(context, RealtimeMonitorService::class.java)
            .putExtra(RealtimeMonitorService.EXTRA_THRESHOLD, threshold)
        context.startForegroundService(intent)
        viewModelScope.launch {
            settings.setRealtimeEnabled(true)
            refreshDashboard()
        }
    }

    fun stopRealtime() {
        val context = getApplication<Application>()
        context.stopService(Intent(context, RealtimeMonitorService::class.java))
        viewModelScope.launch {
            settings.setRealtimeEnabled(false)
            refreshDashboard()
        }
    }

    fun protectedDirPath(): String = RealtimeMonitorService.protectedDir(getApplication()).absolutePath

    // ---------------- Quarantine ----------------

    fun refreshQuarantine() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = db.listQuarantine()
            _quarantineItems.value = items
            refreshDashboard()
        }
    }

    fun restoreQuarantineItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                quarantine.restoreFile(id)
            } catch (_: Exception) {
            }
            refreshQuarantine()
        }
    }

    fun deleteQuarantineItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                quarantine.deletePermanently(id)
            } catch (_: Exception) {
            }
            refreshQuarantine()
        }
    }

    // ---------------- Privacy ----------------

    fun refreshPrivacy() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            _screenLockStatus.value = DeviceSecurity.screenLockStatus(context)
            _playProtectStatus.value = DeviceSecurity.playProtectStatus(context)
            val apps = withContext(Dispatchers.IO) { PrivacyAudit.auditInstalledApps(context) }
            _privacyApps.value = apps
        }
    }

    fun openAppDetails(packageName: String) {
        PrivacyAudit.openAppDetails(getApplication(), packageName)
    }

    // ---------------- Cloud ----------------

    fun saveApiKey(key: String) {
        viewModelScope.launch { settings.setVtApiKey(key) }
    }

    fun setCloudEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setCloudLookupEnabled(enabled) }
    }

    fun testCloudLookup(sha256: String) {
        viewModelScope.launch {
            val key = _cloudState.value.apiKey
            if (key.isBlank()) {
                _cloudState.value = _cloudState.value.copy(testResult = "Inserisci e salva una chiave API prima di continuare.")
                return@launch
            }
            _cloudState.value = _cloudState.value.copy(testResult = "Verifica in corso…")
            try {
                val result: CloudResult = CloudLookup.lookupHash(sha256, key)
                val text = if (!result.known) {
                    "Nessun risultato: file sconosciuto a VirusTotal."
                } else {
                    "Rilevamenti: ${result.malicious} motori malevoli, ${result.suspicious} sospetti, ${result.harmless} puliti."
                }
                _cloudState.value = _cloudState.value.copy(testResult = text)
            } catch (exc: Exception) {
                _cloudState.value = _cloudState.value.copy(testResult = "Errore: ${exc.message}")
            }
        }
    }

    // ---------------- Settings ----------------

    fun setAutoQuarantineThreshold(value: Int) {
        viewModelScope.launch { settings.setAutoQuarantineThreshold(value) }
    }

    suspend fun exportProfileJson(): String {
        val threshold = settings.autoQuarantineThreshold.first()
        val cloudEnabled = settings.cloudLookupEnabled.first()
        val json = org.json.JSONObject()
        json.put("app", "SentinelAI")
        json.put("profile_version", 1)
        val settingsJson = org.json.JSONObject()
        settingsJson.put("auto_quarantine_threshold", threshold)
        settingsJson.put("cloud_lookup_enabled", if (cloudEnabled) "1" else "0")
        json.put("settings", settingsJson)
        return json.toString(2)
    }

    fun importProfileJson(raw: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val json = org.json.JSONObject(raw)
                val settingsJson = json.getJSONObject("settings")
                if (settingsJson.has("auto_quarantine_threshold")) {
                    settings.setAutoQuarantineThreshold(settingsJson.getInt("auto_quarantine_threshold"))
                }
                if (settingsJson.has("cloud_lookup_enabled")) {
                    settings.setCloudLookupEnabled(settingsJson.getString("cloud_lookup_enabled") == "1")
                }
                onDone(true)
            } catch (exc: Exception) {
                onDone(false)
            }
        }
    }
}
