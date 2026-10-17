package com.music.bitchord.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.music.bitchord.R
import com.music.bitchord.playback.equalizer.EqualizerPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import org.json.JSONObject

@Serializable
data class KnownOutputDevice(
    val key: String,
    val label: String,
)

object EqualizerSettings {
    private const val PREF_FILE = "bitchord_equalizer_settings"
    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_PRESET_ID = "eq_preset_id"
    private const val KEY_BASS_BOOST = "eq_bass_boost"
    private const val KEY_VIRTUALIZER = "eq_virtualizer"
    private const val KEY_BAND_PREFIX = "eq_band_"
    private const val KEY_ACTIVE_DEVICE = "eq_active_device"
    private const val PROFILE_PREFIX = "eq_device_profile_"
    private const val KEY_KNOWN_DEVICES = "eq_known_devices"
    private const val KEY_DEVICE_PRESET_ASSIGNMENTS = "eq_device_preset_assignments"

    const val AUTO_PRESET_NONE = ""
    const val AUTO_PRESET_OFF = "off"
    const val AUTO_PRESET_GEQ_PREFIX = "geq:"
    const val AUTO_PRESET_PEQ_PREFIX = "peq:"

    private lateinit var prefs: SharedPreferences
    private var appContext: Context? = null
    private var deviceCallbackRegistered = false

    private val _enabled = MutableStateFlow(false)
    val enabled = _enabled.asStateFlow()

    private val _presetId = MutableStateFlow(0) // Default Flat (id = 0)
    val presetId = _presetId.asStateFlow()

    private val _bandLevels = MutableStateFlow<Map<Int, Short>>(emptyMap())
    val bandLevels = _bandLevels.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength = _bassBoostStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength = _virtualizerStrength.asStateFlow()
    private val _activeDeviceLabel = MutableStateFlow("This device")
    val activeDeviceLabel = _activeDeviceLabel.asStateFlow()
    private var activeDeviceKey = "default"
    private val _activeDeviceKeyFlow = MutableStateFlow("default")
    val activeDeviceKeyFlow = _activeDeviceKeyFlow.asStateFlow()

    private val _knownDevices = MutableStateFlow<List<KnownOutputDevice>>(emptyList())
    val knownDevices = _knownDevices.asStateFlow()

    private val _devicePresetAssignments = MutableStateFlow<Map<String, String>>(emptyMap())
    val devicePresetAssignments = _devicePresetAssignments.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            appContext?.let(::refreshConnectedDevices)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            appContext?.let(::refreshConnectedDevices)
        }
    }

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        prefs = appCtx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        activeDeviceKey = prefs.getString(KEY_ACTIVE_DEVICE, "default") ?: "default"
        _activeDeviceKeyFlow.value = activeDeviceKey
        readAll()
        registerDeviceCallbackIfNeeded(appCtx)
        refreshConnectedDevices(appCtx)
    }

    private fun registerDeviceCallbackIfNeeded(context: Context) {
        if (deviceCallbackRegistered) return
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            manager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
            deviceCallbackRegistered = true
        }
    }

    private fun readAll() {
        if (!this::prefs.isInitialized) return
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
        val pId = prefs.getInt(KEY_PRESET_ID, 0)
        _presetId.value = pId
        _bassBoostStrength.value = prefs.getInt(KEY_BASS_BOOST, 0)
        _virtualizerStrength.value = prefs.getInt(KEY_VIRTUALIZER, 0)

        val levels = mutableMapOf<Int, Short>()
        for (i in 0 until 5) {
            val defaultLevel = EqualizerPreset.getPresetById(pId)?.bandLevels?.getOrNull(i) ?: 0
            val saved = prefs.getInt("$KEY_BAND_PREFIX$i", defaultLevel.toInt()).toShort()
            levels[i] = saved
        }
        _bandLevels.value = levels
        _knownDevices.value = readKnownDevices()
        _devicePresetAssignments.value = readDevicePresetAssignments()
        if (activeDeviceKey != "default") loadProfile(activeDeviceKey)
        val initialAssigned = _devicePresetAssignments.value[activeDeviceKey]
        if (!initialAssigned.isNullOrBlank()) {
            applyAssignedPreset(initialAssigned)
        }
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        persistCurrentProfile()
    }

    fun selectPreset(id: Int) {
        _presetId.value = id
        val preset = EqualizerPreset.getPresetById(id)
        if (preset != null) {
            val newLevels = mutableMapOf<Int, Short>()
            preset.bandLevels.forEachIndexed { index, level ->
                newLevels[index] = level
            }
            _bandLevels.value = newLevels
            persistCurrentProfile()
        } else {
            persistCurrentProfile()
        }
    }

    fun setBandLevel(band: Int, level: Short) {
        val current = _bandLevels.value.toMutableMap()
        current[band] = level
        _bandLevels.value = current
        _presetId.value = EqualizerPreset.CUSTOM_PRESET_ID

        persistCurrentProfile()
    }

    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _bassBoostStrength.value = clamped
        persistCurrentProfile()
    }

    fun setVirtualizerStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _virtualizerStrength.value = clamped
        persistCurrentProfile()
    }

    fun reset() {
        selectPreset(0) // Flat
        setBassBoostStrength(0)
        setVirtualizerStrength(0)
    }

    /** Assigns or clears an automatic EQ preset for a specific output device key. */
    fun setDevicePresetAssignment(deviceKey: String, presetSpec: String?) {
        if (!this::prefs.isInitialized) return
        val key = deviceKey.takeIf(::isValidDeviceKey) ?: return
        val current = _devicePresetAssignments.value.toMutableMap()
        val normalized = presetSpec?.trim().orEmpty()
        if (normalized.isEmpty()) {
            current.remove(key)
        } else {
            current[key] = normalized
        }
        _devicePresetAssignments.value = current
        persistDevicePresetAssignments(current)
        if (key == activeDeviceKey && normalized.isNotEmpty()) {
            applyAssignedPreset(normalized)
        }
    }

    private fun applyAssignedPreset(presetSpec: String) {
        when {
            presetSpec == AUTO_PRESET_OFF -> {
                setEnabled(false)
                AppSettings.setParametricEqEnabled(false)
            }
            presetSpec.startsWith(AUTO_PRESET_GEQ_PREFIX) -> {
                val id = presetSpec.removePrefix(AUTO_PRESET_GEQ_PREFIX).toIntOrNull() ?: return
                if (EqualizerPreset.getPresetById(id) != null) {
                    _enabled.value = true
                    selectPreset(id)
                }
            }
            presetSpec.startsWith(AUTO_PRESET_PEQ_PREFIX) -> {
                val peqId = presetSpec.removePrefix(AUTO_PRESET_PEQ_PREFIX)
                if (peqId.isNotBlank()) {
                    AppSettings.selectParametricEqPreset(peqId)
                }
            }
        }
    }

    /** Scans currently connected audio output devices and updates the active route & known devices list. */
    fun refreshConnectedDevices(context: Context) {
        if (!this::prefs.isInitialized) return
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val mediaTypes = buildSet {
            add(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_USB_DEVICE)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            add(AudioDeviceInfo.TYPE_USB_ACCESSORY)
            add(AudioDeviceInfo.TYPE_LINE_ANALOG)
            add(AudioDeviceInfo.TYPE_LINE_DIGITAL)
            add(AudioDeviceInfo.TYPE_HDMI)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }
        for (device in outputs) {
            if (!device.isSink || device.type !in mediaTypes) continue
            val key = deviceKeyFor(device)
            val label = deviceLabelFor(context, device)
            recordKnownDevice(key, label)
        }
        val routed = resolveActiveOutputDevice(manager, outputs)
        val activeKey = routed?.let(::deviceKeyFor) ?: "default"
        val activeLabel = routed?.let { deviceLabelFor(context, it) }
            ?: context.getString(R.string.equalizer_this_device)
        activateOutputProfile(activeKey, activeLabel)
    }

    fun deviceKeyFor(device: AudioDeviceInfo): String =
        "out-${(device.type.toString() + ":" + device.productName.toString().lowercase()).hashCode().toUInt().toString(16)}"

    fun deviceLabelFor(context: Context, device: AudioDeviceInfo): String {
        val typeLabel = context.getString(outputTypeLabelRes(device.type))
        val productName = device.productName?.toString()?.trim().orEmpty()
        return if (productName.isNotBlank() && !productName.equals(Build.MODEL, ignoreCase = true) &&
            device.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        ) {
            productName
        } else {
            typeLabel
        }
    }

    private fun outputTypeLabelRes(type: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        ) return R.string.equalizer_bluetooth_output

        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> R.string.equalizer_bluetooth_output
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> R.string.equalizer_wired_output
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> R.string.equalizer_usb_output
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> R.string.equalizer_speaker_output
            else -> R.string.equalizer_other_output
        }
    }

    private fun resolveActiveOutputDevice(
        manager: AudioManager,
        outputs: Array<AudioDeviceInfo>,
    ): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            runCatching { manager.getAudioDevicesForAttributes(attributes).firstOrNull() }
                .getOrNull()?.let { return it }
        }
        val preferredTypes = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_USB_DEVICE)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }
        return outputs.firstOrNull { it.type in preferredTypes }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?: outputs.firstOrNull()
    }

    private fun recordKnownDevice(key: String, label: String) {
        if (!isValidDeviceKey(key)) return
        val cleanLabel = label.trim().ifBlank { "This device" }
        val current = _knownDevices.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.key == key }
        if (existingIdx >= 0) {
            if (current[existingIdx].label == cleanLabel) return
            current[existingIdx] = KnownOutputDevice(key, cleanLabel)
        } else {
            current.add(KnownOutputDevice(key, cleanLabel))
        }
        _knownDevices.value = current
        persistKnownDevices(current)
    }

    /** Persist and activate an EQ profile for the routed output device. */
    fun activateOutputProfile(deviceKey: String, label: String) {
        if (!this::prefs.isInitialized) return
        val key = deviceKey.takeIf(::isValidDeviceKey) ?: "default"
        val cleanLabel = label.ifBlank { "This device" }
        _activeDeviceLabel.value = cleanLabel
        recordKnownDevice(key, cleanLabel)
        if (activeDeviceKey == key) {
            _activeDeviceKeyFlow.value = key
            return
        }
        persistCurrentProfile()
        activeDeviceKey = key
        _activeDeviceKeyFlow.value = key
        prefs.edit().putString(KEY_ACTIVE_DEVICE, key).apply()
        if (!loadProfile(key)) persistCurrentProfile()
        val assignedPreset = _devicePresetAssignments.value[key]
        if (!assignedPreset.isNullOrBlank()) {
            applyAssignedPreset(assignedPreset)
        }
    }

    private fun readKnownDevices(): List<KnownOutputDevice> {
        val raw = prefs.getString(KEY_KNOWN_DEVICES, null) ?: return emptyList()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val list = mutableListOf<KnownOutputDevice>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (!isValidDeviceKey(k)) continue
            val v = json.optString(k, "").trim()
            if (v.isNotEmpty()) {
                list.add(KnownOutputDevice(k, v))
            }
        }
        return list
    }

    private fun persistKnownDevices(devices: List<KnownOutputDevice>) {
        if (!this::prefs.isInitialized) return
        val json = JSONObject()
        devices.forEach { json.put(it.key, it.label) }
        prefs.edit().putString(KEY_KNOWN_DEVICES, json.toString()).apply()
    }

    private fun readDevicePresetAssignments(): Map<String, String> {
        val raw = prefs.getString(KEY_DEVICE_PRESET_ASSIGNMENTS, null) ?: return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (!isValidDeviceKey(k)) continue
            val v = json.optString(k, "").trim()
            if (v.isNotEmpty()) {
                map[k] = v
            }
        }
        return map
    }

    private fun persistDevicePresetAssignments(assignments: Map<String, String>) {
        if (!this::prefs.isInitialized) return
        val json = JSONObject()
        assignments.forEach { (k, v) -> if (isValidDeviceKey(k) && v.isNotBlank()) json.put(k, v) }
        prefs.edit().putString(KEY_DEVICE_PRESET_ASSIGNMENTS, json.toString()).apply()
    }

    private fun loadProfile(key: String): Boolean {
        if (!this::prefs.isInitialized) return false
        val raw = prefs.getString(PROFILE_PREFIX + key, null) ?: return false
        val profile = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        _enabled.value = profile.optBoolean("enabled", false)
        _presetId.value = profile.optInt("preset", 0)
        _bassBoostStrength.value = profile.optInt("bass", 0).coerceIn(0, 1000)
        _virtualizerStrength.value = profile.optInt("virtualizer", 0).coerceIn(0, 1000)
        val bandsJson = profile.optJSONObject("bands")
        _bandLevels.value = (0 until 5).associateWith { index ->
            bandsJson?.optInt(index.toString(), 0)?.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())?.toShort() ?: 0
        }
        return true
    }

    private fun persistCurrentProfile() {
        if (!this::prefs.isInitialized) return
        val levels = _bandLevels.value
        val profileJson = JSONObject().apply {
            put("enabled", _enabled.value)
            put("preset", _presetId.value)
            put("bass", _bassBoostStrength.value)
            put("virtualizer", _virtualizerStrength.value)
            put("bands", JSONObject().apply { levels.forEach { (band, level) -> put(band.toString(), level.toInt()) } })
        }
        prefs.edit()
            // Keep the original keys as the migration source for profiles on
            // a newly attached output and for older versions of the app.
            .putBoolean(KEY_ENABLED, _enabled.value)
            .putInt(KEY_PRESET_ID, _presetId.value)
            .putInt(KEY_BASS_BOOST, _bassBoostStrength.value)
            .putInt(KEY_VIRTUALIZER, _virtualizerStrength.value)
            .also { editor -> levels.forEach { (band, level) -> editor.putInt("$KEY_BAND_PREFIX$band", level.toInt()) } }
            .putString(KEY_ACTIVE_DEVICE, activeDeviceKey)
            .putString(PROFILE_PREFIX + activeDeviceKey, profileJson.toString())
            .apply()
    }

    fun exportBackup(): EqualizerBackup = EqualizerBackup(
        enabled = _enabled.value,
        presetId = _presetId.value,
        bandLevels = _bandLevels.value,
        bassBoostStrength = _bassBoostStrength.value,
        virtualizerStrength = _virtualizerStrength.value,
        activeDeviceKey = activeDeviceKey,
        deviceProfiles = readProfilesForBackup() + (activeDeviceKey to currentProfileSnapshot()),
        knownDevices = _knownDevices.value,
        devicePresetAssignments = _devicePresetAssignments.value,
    )

    fun importBackup(backup: EqualizerBackup?) {
        if (backup == null) return
        if (!this::prefs.isInitialized) return

        val importedKey = backup.activeDeviceKey.takeIf(::isValidDeviceKey) ?: "default"
        val profiles = backup.deviceProfiles
            .filterKeys(::isValidDeviceKey)
            .mapValues { (_, profile) -> profile.sanitized() }

        activeDeviceKey = importedKey
        _activeDeviceKeyFlow.value = importedKey
        _enabled.value = backup.enabled
        _presetId.value = backup.presetId
        _bassBoostStrength.value = backup.bassBoostStrength.coerceIn(0, 1000)
        _virtualizerStrength.value = backup.virtualizerStrength.coerceIn(0, 1000)
        _bandLevels.value = backup.bandLevels
            .filterKeys { it in 0 until 5 }
            .ifEmpty {
                (0 until 5).associateWith { index ->
                    EqualizerPreset.getPresetById(backup.presetId)?.bandLevels?.getOrNull(index) ?: 0.toShort()
                }
            }
        if (backup.knownDevices.isNotEmpty()) {
            val mergedKnown = (_knownDevices.value + backup.knownDevices.filter { isValidDeviceKey(it.key) })
                .distinctBy { it.key }
            _knownDevices.value = mergedKnown
            persistKnownDevices(mergedKnown)
        }
        if (backup.devicePresetAssignments.isNotEmpty()) {
            val mergedAssignments = _devicePresetAssignments.value +
                backup.devicePresetAssignments.filterKeys(::isValidDeviceKey)
            _devicePresetAssignments.value = mergedAssignments
            persistDevicePresetAssignments(mergedAssignments)
        }

        prefs.edit().apply {
            putString(KEY_ACTIVE_DEVICE, importedKey)
            // New backups carry the complete device map. Replace it only when
            // one is present, so a v1-v3 backup (which had no profiles) does
            // not silently erase profiles on the device receiving the restore.
            if (profiles.isNotEmpty()) {
                prefs.all.keys.filter { it.startsWith(PROFILE_PREFIX) }.forEach(::remove)
                profiles.forEach { (key, profile) ->
                    putString(PROFILE_PREFIX + key, profile.toJson().toString())
                }
            }
        }.apply()
        persistCurrentProfile()
    }

    private fun readProfilesForBackup(): Map<String, EqualizerDeviceProfile> =
        prefs.all.entries
            .asSequence()
            .filter { (key, _) -> key.startsWith(PROFILE_PREFIX) }
            .mapNotNull { (storedKey, storedValue) ->
                val key = storedKey.removePrefix(PROFILE_PREFIX)
                if (!isValidDeviceKey(key)) return@mapNotNull null
                val json = (storedValue as? String)?.let { runCatching { JSONObject(it) }.getOrNull() }
                    ?: return@mapNotNull null
                key to json.toDeviceProfile()
            }
            .toMap()

    private fun currentProfileSnapshot() = EqualizerDeviceProfile(
        enabled = _enabled.value,
        presetId = _presetId.value,
        bandLevels = _bandLevels.value,
        bassBoostStrength = _bassBoostStrength.value,
        virtualizerStrength = _virtualizerStrength.value,
    )

    private fun JSONObject.toDeviceProfile() = EqualizerDeviceProfile(
        enabled = optBoolean("enabled", false),
        presetId = optInt("preset", 0),
        bandLevels = (0 until 5).associateWith { index ->
            optJSONObject("bands")?.optInt(index.toString(), 0)
                ?.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())?.toShort() ?: 0
        },
        bassBoostStrength = optInt("bass", 0).coerceIn(0, 1000),
        virtualizerStrength = optInt("virtualizer", 0).coerceIn(0, 1000),
    )

    private fun EqualizerDeviceProfile.sanitized() = copy(
        bandLevels = bandLevels.filterKeys { it in 0 until 5 },
        bassBoostStrength = bassBoostStrength.coerceIn(0, 1000),
        virtualizerStrength = virtualizerStrength.coerceIn(0, 1000),
    )

    private fun EqualizerDeviceProfile.toJson() = JSONObject().apply {
        put("enabled", enabled)
        put("preset", presetId)
        put("bass", bassBoostStrength)
        put("virtualizer", virtualizerStrength)
        put("bands", JSONObject().apply {
            bandLevels.forEach { (band, level) ->
                if (band in 0 until 5) put(band.toString(), level.toInt())
            }
        })
    }

    private fun isValidDeviceKey(key: String): Boolean =
        key == "default" || key.matches(Regex("[A-Za-z0-9_-]{1,64}"))
}

@Serializable
data class EqualizerBackup(
    val enabled: Boolean = false,
    val presetId: Int = 0,
    val bandLevels: Map<Int, Short> = emptyMap(),
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val activeDeviceKey: String = "default",
    val deviceProfiles: Map<String, EqualizerDeviceProfile> = emptyMap(),
    val knownDevices: List<KnownOutputDevice> = emptyList(),
    val devicePresetAssignments: Map<String, String> = emptyMap(),
)

@Serializable
data class EqualizerDeviceProfile(
    val enabled: Boolean = false,
    val presetId: Int = 0,
    val bandLevels: Map<Int, Short> = emptyMap(),
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
)
