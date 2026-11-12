package com.fossscout.core.matching

import com.fossscout.core.inventory.InstalledApp

/** Android protection level, reduced to the categories shown to users. */
enum class PermissionCategory {
    DANGEROUS,
    SPECIAL,
    SIGNATURE,
    NORMAL,
    OTHER,
}

/** How an alternative relates to the installed app it is compared with. */
enum class PermissionRelationship {
    SAME_APP,
    VARIANT,
    DIFFERENT_APP,
    UNKNOWN,
}

/** One permission with a readable name and, when known, a short explanation. */
data class PermissionDetail(
    val name: String,
    val displayName: String,
    val category: PermissionCategory,
    val explanation: String? = null,
)

/** One side of the comparison. */
data class PermissionSet(
    val label: String,
    val packageName: String,
    val versionName: String? = null,
    val storeName: String? = null,
    val permissions: List<PermissionDetail> = emptyList(),
    val permissionsKnown: Boolean = true,
)

/** Where one permission appears in the comparison. */
enum class PermissionDiffStatus {
    INSTALLED_ONLY,
    ALTERNATIVE_ONLY,
    SHARED,
}

/** Permission names that differ, grouped by where they appear. */
data class PermissionDiff(
    val installedOnly: List<PermissionDetail> = emptyList(),
    val alternativeOnly: List<PermissionDetail> = emptyList(),
    val shared: List<PermissionDetail> = emptyList(),
)

/**
 * A local, evidence-first permission comparison. [diff] is null when the store
 * index does not provide a permission list for the alternative; that is unknown,
 * not an empty permission set.
 */
data class PermissionComparison(
    val installed: PermissionSet,
    val alternative: PermissionSet,
    val relationship: PermissionRelationship,
    val diff: PermissionDiff? = null,
    val alternativeSourceUrl: String? = null,
    val tracker: TrackerReport? = null,
    val trackerScanInProgress: Boolean = false,
)

/** Store-side input for a comparison. */
data class PermissionAlternative(
    val label: String,
    val packageName: String,
    val permissions: List<String> = emptyList(),
    val permissionsKnown: Boolean = permissions.isNotEmpty(),
    val versionName: String? = null,
    val storeName: String,
    val sourceUrl: String? = null,
    val relationship: PermissionRelationship,
)

/** Builds the permission diff from the installed app and one store result. */
fun comparePermissions(
    installed: InstalledApp,
    alternative: PermissionAlternative,
    tracker: TrackerReport? = null,
    trackerScanInProgress: Boolean = false,
): PermissionComparison {
    val installedDetails = permissionDetails(installed.requestedPermissions)
    val alternativeDetails = permissionDetails(alternative.permissions)
    val diff = if (alternative.permissionsKnown) {
        val installedByName = installedDetails.associateBy(PermissionDetail::name)
        val alternativeByName = alternativeDetails.associateBy(PermissionDetail::name)
        PermissionDiff(
            installedOnly = installedDetails
                .filterNot { it.name in alternativeByName }
                .sortedWith(PERMISSION_ORDER),
            alternativeOnly = alternativeDetails
                .filterNot { it.name in installedByName }
                .sortedWith(PERMISSION_ORDER),
            shared = alternativeDetails
                .filter { it.name in installedByName }
                .sortedWith(PERMISSION_ORDER),
        )
    } else {
        null
    }

    return PermissionComparison(
        installed = PermissionSet(
            label = installed.label,
            packageName = installed.packageName,
            versionName = installed.versionName,
            permissions = installedDetails,
            permissionsKnown = true,
        ),
        alternative = PermissionSet(
            label = alternative.label,
            packageName = alternative.packageName,
            versionName = alternative.versionName,
            storeName = alternative.storeName,
            permissions = alternativeDetails,
            permissionsKnown = alternative.permissionsKnown,
        ),
        relationship = alternative.relationship,
        diff = diff,
        alternativeSourceUrl = alternative.sourceUrl,
        tracker = tracker,
        trackerScanInProgress = trackerScanInProgress,
    )
}

fun MatchCandidate.toPermissionAlternative(): PermissionAlternative = PermissionAlternative(
    label = displayName,
    packageName = packageName,
    permissions = permissions,
    permissionsKnown = permissions.isNotEmpty(),
    versionName = versionName,
    storeName = storeName,
    sourceUrl = sourceUrl,
    relationship = when (level) {
        MatchLevel.L1 -> PermissionRelationship.SAME_APP
        MatchLevel.L2 -> PermissionRelationship.VARIANT
        MatchLevel.L3 -> PermissionRelationship.DIFFERENT_APP
        MatchLevel.L0 -> PermissionRelationship.UNKNOWN
    },
)

fun AlternativeSuggestion.toPermissionAlternative(): PermissionAlternative = PermissionAlternative(
    label = displayName,
    packageName = packageName,
    permissions = permissions,
    permissionsKnown = permissions.isNotEmpty(),
    versionName = versionName,
    storeName = storeName,
    sourceUrl = sourceUrl,
    relationship = PermissionRelationship.DIFFERENT_APP,
)

internal fun permissionDetail(name: String): PermissionDetail {
    val normalized = name.trim()
    val known = KNOWN_PERMISSIONS[normalized]
    return PermissionDetail(
        name = normalized,
        displayName = known?.displayName ?: fallbackDisplayName(normalized),
        category = known?.category ?: PermissionCategory.OTHER,
        explanation = known?.explanation,
    )
}

private fun permissionDetails(names: List<String>): List<PermissionDetail> = names
    .asSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()
    .map(::permissionDetail)
    .sortedWith(PERMISSION_ORDER)
    .toList()

private fun fallbackDisplayName(permission: String): String {
    val leaf = permission.substringAfterLast('.').ifEmpty { permission }
    return leaf.split('_')
        .filter(String::isNotBlank)
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar(Char::uppercase)
        }
        .ifBlank { permission }
}

private val PERMISSION_ORDER = compareBy<PermissionDetail> { it.category.ordinal }
    .thenBy { it.displayName.lowercase() }
    .thenBy { it.name }

private data class KnownPermission(
    val displayName: String,
    val category: PermissionCategory,
    val explanation: String? = null,
)

private fun dangerous(displayName: String, explanation: String) = KnownPermission(
    displayName = displayName,
    category = PermissionCategory.DANGEROUS,
    explanation = explanation,
)

private fun special(displayName: String, explanation: String) = KnownPermission(
    displayName = displayName,
    category = PermissionCategory.SPECIAL,
    explanation = explanation,
)

private fun normal(displayName: String) = KnownPermission(
    displayName = displayName,
    category = PermissionCategory.NORMAL,
)

private fun signature(displayName: String, explanation: String) = KnownPermission(
    displayName = displayName,
    category = PermissionCategory.SIGNATURE,
    explanation = explanation,
)

private val KNOWN_PERMISSIONS: Map<String, KnownPermission> = mapOf(
    "android.permission.CAMERA" to dangerous(
        "Camera",
        "Take photos and record video while the app is in use.",
    ),
    "android.permission.RECORD_AUDIO" to dangerous(
        "Microphone",
        "Record audio while the app is in use.",
    ),
    "android.permission.READ_CONTACTS" to dangerous(
        "Read contacts",
        "Read names, phone numbers, and other contact details.",
    ),
    "android.permission.WRITE_CONTACTS" to dangerous(
        "Change contacts",
        "Add, change, or delete contact details.",
    ),
    "android.permission.GET_ACCOUNTS" to dangerous(
        "Accounts",
        "See accounts such as email or social accounts on the device.",
    ),
    "android.permission.READ_CALENDAR" to dangerous(
        "Read calendar",
        "Read calendar events and details.",
    ),
    "android.permission.WRITE_CALENDAR" to dangerous(
        "Change calendar",
        "Add, change, or delete calendar events.",
    ),
    "android.permission.ACCESS_FINE_LOCATION" to dangerous(
        "Precise location",
        "Read the device's precise location.",
    ),
    "android.permission.ACCESS_COARSE_LOCATION" to dangerous(
        "Approximate location",
        "Read the device's approximate location.",
    ),
    "android.permission.ACCESS_BACKGROUND_LOCATION" to dangerous(
        "Background location",
        "Read location while the app is not in use.",
    ),
    "android.permission.READ_PHONE_STATE" to dangerous(
        "Phone state",
        "Read phone and network state, including whether a call is active.",
    ),
    "android.permission.READ_PHONE_NUMBERS" to dangerous(
        "Phone numbers",
        "Read phone numbers associated with the device.",
    ),
    "android.permission.CALL_PHONE" to dangerous(
        "Make phone calls",
        "Start phone calls without asking for confirmation.",
    ),
    "android.permission.ANSWER_PHONE_CALLS" to dangerous(
        "Answer phone calls",
        "Answer incoming phone calls.",
    ),
    "android.permission.ACCEPT_HANDOVER" to dangerous(
        "Accept call handover",
        "Continue a phone call in this app without another confirmation.",
    ),
    "android.permission.READ_VOICEMAIL" to dangerous(
        "Read voicemail",
        "Read voicemail messages stored by the phone service.",
    ),
    "android.permission.WRITE_VOICEMAIL" to dangerous(
        "Change voicemail",
        "Add or change voicemail messages stored by the phone service.",
    ),
    "android.permission.READ_CALL_LOG" to dangerous(
        "Read call history",
        "Read incoming and outgoing call history.",
    ),
    "android.permission.WRITE_CALL_LOG" to dangerous(
        "Change call history",
        "Change the device's call history.",
    ),
    "android.permission.ADD_VOICEMAIL" to dangerous(
        "Add voicemail",
        "Add voicemail messages to the device.",
    ),
    "android.permission.USE_SIP" to dangerous(
        "SIP calls",
        "Make or receive SIP calls.",
    ),
    "android.permission.PROCESS_OUTGOING_CALLS" to dangerous(
        "Outgoing calls",
        "See and redirect outgoing phone calls.",
    ),
    "android.permission.BODY_SENSORS" to dangerous(
        "Body sensors",
        "Read body sensors such as heart-rate sensors.",
    ),
    "android.permission.BODY_SENSORS_BACKGROUND" to dangerous(
        "Background body sensors",
        "Read body sensors while the app is not in use.",
    ),
    "android.permission.SEND_SMS" to dangerous(
        "Send SMS",
        "Send text messages, which may cost money.",
    ),
    "android.permission.RECEIVE_SMS" to dangerous(
        "Receive SMS",
        "Read text messages received on the device.",
    ),
    "android.permission.READ_SMS" to dangerous(
        "Read SMS",
        "Read text messages stored on the device.",
    ),
    "android.permission.RECEIVE_WAP_PUSH" to dangerous(
        "Receive WAP push",
        "Receive WAP push messages.",
    ),
    "android.permission.RECEIVE_MMS" to dangerous(
        "Receive MMS",
        "Receive multimedia messages.",
    ),
    "android.permission.READ_EXTERNAL_STORAGE" to dangerous(
        "Read shared storage",
        "Read files in shared device storage.",
    ),
    "android.permission.WRITE_EXTERNAL_STORAGE" to dangerous(
        "Change shared storage",
        "Add, change, or delete files in shared device storage.",
    ),
    "android.permission.READ_MEDIA_IMAGES" to dangerous(
        "Read photos",
        "Read image files in shared device storage.",
    ),
    "android.permission.READ_MEDIA_VIDEO" to dangerous(
        "Read video",
        "Read video files in shared device storage.",
    ),
    "android.permission.READ_MEDIA_AUDIO" to dangerous(
        "Read audio",
        "Read audio files in shared device storage.",
    ),
    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" to dangerous(
        "Read selected photos and video",
        "Read only the photos and videos the user selected.",
    ),
    "android.permission.ACCESS_MEDIA_LOCATION" to dangerous(
        "Photo location",
        "Read location information stored in photos.",
    ),
    "android.permission.POST_NOTIFICATIONS" to dangerous(
        "Notifications",
        "Show notifications.",
    ),
    "android.permission.NEARBY_WIFI_DEVICES" to dangerous(
        "Nearby Wi-Fi devices",
        "Find and connect to nearby Wi-Fi devices.",
    ),
    "android.permission.BLUETOOTH_SCAN" to dangerous(
        "Find Bluetooth devices",
        "Scan for nearby Bluetooth devices.",
    ),
    "android.permission.BLUETOOTH_CONNECT" to dangerous(
        "Connect to Bluetooth devices",
        "Connect to paired Bluetooth devices.",
    ),
    "android.permission.BLUETOOTH_ADVERTISE" to dangerous(
        "Advertise over Bluetooth",
        "Make the device visible to nearby Bluetooth devices.",
    ),
    "android.permission.ACTIVITY_RECOGNITION" to dangerous(
        "Activity recognition",
        "Recognise physical activity such as walking or cycling.",
    ),
    "android.permission.UWB_RANGING" to dangerous(
        "Ultra-wideband ranging",
        "Measure distance to nearby ultra-wideband devices.",
    ),
    "android.permission.SYSTEM_ALERT_WINDOW" to special(
        "Display over other apps",
        "Draw over other apps. This is a special app access, not a normal permission.",
    ),
    "android.permission.MANAGE_EXTERNAL_STORAGE" to special(
        "All files access",
        "Read and change all files in shared storage. This is a special app access.",
    ),
    "android.permission.PACKAGE_USAGE_STATS" to special(
        "Usage access",
        "See which apps are used and when. This is a special app access.",
    ),
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to special(
        "Ignore battery optimisation",
        "Ask to run without normal battery restrictions.",
    ),
    "android.permission.REQUEST_INSTALL_PACKAGES" to special(
        "Install unknown apps",
        "Ask to install other apps. This is a special app access.",
    ),
    "android.permission.BIND_ACCESSIBILITY_SERVICE" to special(
        "Accessibility service",
        "Observe and control the screen through an accessibility service.",
    ),
    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to special(
        "Notification access",
        "Read notifications from other apps.",
    ),
    "android.permission.BIND_VPN_SERVICE" to special(
        "VPN service",
        "Route device network traffic through a VPN service.",
    ),
    "android.permission.BIND_DEVICE_ADMIN" to special(
        "Device administration",
        "Manage device policies as a device administrator.",
    ),
    "android.permission.SCHEDULE_EXACT_ALARM" to special(
        "Exact alarms",
        "Schedule alarms that fire at an exact time.",
    ),
    "android.permission.USE_EXACT_ALARM" to special(
        "Exact alarms",
        "Use exact alarms without a separate user grant.",
    ),
    "android.permission.ACCESS_NOTIFICATION_POLICY" to special(
        "Do not disturb access",
        "Change the device's do-not-disturb settings.",
    ),
    "android.permission.WRITE_SETTINGS" to special(
        "Change system settings",
        "Change some system settings. This is a special app access.",
    ),
    "android.permission.MANAGE_MEDIA" to special(
        "Manage media",
        "Manage media recommendations and playback.",
    ),
    "android.permission.MANAGE_OWN_CALLS" to special(
        "Manage own calls",
        "Route calls made by this app through the system phone app.",
    ),
    "android.permission.WRITE_SECURE_SETTINGS" to signature(
        "Write secure settings",
        "Change protected system settings. Usually reserved for system apps.",
    ),
    "android.permission.INSTALL_PACKAGES" to signature(
        "Install packages",
        "Install other apps. Usually reserved for system apps.",
    ),
    "android.permission.DELETE_PACKAGES" to signature(
        "Delete packages",
        "Uninstall other apps. Usually reserved for system apps.",
    ),
    "android.permission.READ_LOGS" to signature(
        "Read system logs",
        "Read system log data. Usually reserved for system apps.",
    ),
    "android.permission.DUMP" to signature(
        "Dump system state",
        "Read diagnostic dumps from system services.",
    ),
    "android.permission.CHANGE_CONFIGURATION" to signature(
        "Change configuration",
        "Change system configuration. Usually reserved for system apps.",
    ),
    "android.permission.REBOOT" to signature(
        "Reboot device",
        "Restart the device. Usually reserved for system apps.",
    ),
    "android.permission.SET_TIME" to signature(
        "Set time",
        "Set the system clock. Usually reserved for system apps.",
    ),
    "android.permission.SET_TIME_ZONE" to signature(
        "Set time zone",
        "Set the system time zone. Usually reserved for system apps.",
    ),
    "android.permission.MANAGE_USERS" to signature(
        "Manage users",
        "Create and manage device users. Usually reserved for system apps.",
    ),
    "android.permission.INTERACT_ACROSS_USERS" to signature(
        "Interact across users",
        "Access data for other device users. Usually reserved for system apps.",
    ),
    "android.permission.INTERACT_ACROSS_USERS_FULL" to signature(
        "Interact across all users",
        "Access data for all device users. Usually reserved for system apps.",
    ),
    "android.permission.INTERNET" to normal("Internet access"),
    "android.permission.ACCESS_NETWORK_STATE" to normal("Network state"),
    "android.permission.CHANGE_NETWORK_STATE" to normal("Change network state"),
    "android.permission.ACCESS_WIFI_STATE" to normal("Wi-Fi state"),
    "android.permission.CHANGE_WIFI_STATE" to normal("Change Wi-Fi state"),
    "android.permission.CHANGE_WIFI_MULTICAST_STATE" to normal("Wi-Fi multicast"),
    "android.permission.BLUETOOTH" to normal("Bluetooth"),
    "android.permission.BLUETOOTH_ADMIN" to normal("Bluetooth administration"),
    "android.permission.NFC" to normal("NFC"),
    "android.permission.VIBRATE" to normal("Vibration"),
    "android.permission.WAKE_LOCK" to normal("Keep awake"),
    "android.permission.RECEIVE_BOOT_COMPLETED" to normal("Start after reboot"),
    "android.permission.FOREGROUND_SERVICE" to normal("Foreground service"),
    "android.permission.FOREGROUND_SERVICE_DATA_SYNC" to normal("Foreground data sync"),
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" to normal(
        "Foreground media playback",
    ),
    "android.permission.USE_BIOMETRIC" to normal("Biometric authentication"),
    "android.permission.USE_FINGERPRINT" to normal("Fingerprint authentication"),
    "android.permission.MODIFY_AUDIO_SETTINGS" to normal("Change audio settings"),
    "android.permission.SET_WALLPAPER" to normal("Set wallpaper"),
    "android.permission.EXPAND_STATUS_BAR" to normal("Expand status bar"),
    "android.permission.DISABLE_KEYGUARD" to normal("Change keyguard"),
    "android.permission.READ_SYNC_SETTINGS" to normal("Read sync settings"),
    "android.permission.WRITE_SYNC_SETTINGS" to normal("Change sync settings"),
    "android.permission.AUTHENTICATE_ACCOUNTS" to normal("Authenticate accounts"),
    "android.permission.MANAGE_ACCOUNTS" to normal("Manage accounts"),
    "android.permission.TRANSMIT_IR" to normal("Infrared transmitter"),
    "android.permission.FLASHLIGHT" to normal("Flashlight"),
    "android.permission.HIGH_SAMPLING_RATE_SENSORS" to normal("High-rate sensors"),
    "android.permission.QUERY_ALL_PACKAGES" to normal("See installed apps"),
)
