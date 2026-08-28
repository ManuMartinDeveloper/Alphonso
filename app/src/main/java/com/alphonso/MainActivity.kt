package com.alphonso

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.alphonso.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Run heavy Policy Enforcement in Background Thread
        lifecycleScope.launch(Dispatchers.IO) {
            PolicyManager.enforcePolicies(applicationContext)
        }

        // 2. Start the Sentinel Watchdog
        try {
            startForegroundService(Intent(this, AppMonitorService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Render Sanctuary Main App with Pin-Protected Controls (No Login required)
        setContent {
            AlphonsoTheme {
                SanctuaryAppRoot()
            }
        }
    }
}

@Composable
fun SanctuaryAppRoot() {
    val context = LocalContext.current
    val db = remember { SanctuaryDatabaseProvider.getDatabase(context) }
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    // 0: Guardian Home, 1: Kids Faith Hub, 2: Spiritual Hub, 3: Parental Controls

    var showVirtueShieldDemo by remember { mutableStateOf<IncidentEntity?>(null) }
    var showHolyLockscreen by remember { mutableStateOf(false) }
    var showArchitectureModal by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = SanctuaryNavy,
        bottomBar = {
            NavigationBar(
                containerColor = SanctuaryDarkSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.border(1.dp, BorderSubtle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            if (selectedTab == 0) Icons.Default.Shield else Icons.Outlined.Shield,
                            contentDescription = "Guardian"
                        )
                    },
                    label = { Text("Guardian", fontSize = 11.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SanctuaryNavy,
                        indicatorColor = LiturgicalGold,
                        selectedTextColor = LiturgicalGold,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            if (selectedTab == 1) Icons.Default.Spa else Icons.Outlined.Spa,
                            contentDescription = "Faith Hub"
                        )
                    },
                    label = { Text("Faith Hub", fontSize = 11.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SanctuaryNavy,
                        indicatorColor = MarianBlueLight,
                        selectedTextColor = MarianBlueLight,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            if (selectedTab == 2) Icons.Default.AutoAwesome else Icons.Outlined.AutoAwesome,
                            contentDescription = "Spiritual"
                        )
                    },
                    label = { Text("Spiritual", fontSize = 11.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SanctuaryNavy,
                        indicatorColor = LiturgicalGoldLight,
                        selectedTextColor = LiturgicalGoldLight,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            if (selectedTab == 3) Icons.Default.AdminPanelSettings else Icons.Outlined.AdminPanelSettings,
                            contentDescription = "Parents"
                        )
                    },
                    label = { Text("Parents", fontSize = 11.sp, fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SanctuaryNavy,
                        indicatorColor = RubySacrificeLight,
                        selectedTextColor = RubySacrificeLight,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> GuardianHomeScreen(
                    onTriggerTestViolation = { incident ->
                        showVirtueShieldDemo = incident
                    },
                    onOpenHolyLockscreen = { showHolyLockscreen = true },
                    onOpenArchitecture = { showArchitectureModal = true }
                )
                1 -> ChildrenFaithHubScreen()
                2 -> SpiritualHubScreen()
                3 -> ParentalControlsScreen()
            }
        }
    }

    // Virtue Shield Interceptor Dialog
    if (showVirtueShieldDemo != null) {
        VirtueShieldDetourDialog(
            incident = showVirtueShieldDemo,
            onDismiss = { showVirtueShieldDemo = null },
            onPrayerCompleted = {
                coroutineScope.launch {
                    db.virtueGardenDao().addWaterPoints(25, System.currentTimeMillis())
                    Toast.makeText(context, "+25 Virtue Points! May St. Michael protect you.", Toast.LENGTH_LONG).show()
                }
                showVirtueShieldDemo = null
            }
        )
    }

    // Holy Lockscreen & Double Tap Simulation
    if (showHolyLockscreen) {
        HolyLockscreenDialog(onDismiss = { showHolyLockscreen = false })
    }

    // Architecture & Zero-Cloud Privacy Dialog
    if (showArchitectureModal) {
        ArchitectureExplainerDialog(onDismiss = { showArchitectureModal = false })
    }
}

@Composable
fun GuardianHomeScreen(
    onTriggerTestViolation: (IncidentEntity) -> Unit,
    onOpenHolyLockscreen: () -> Unit,
    onOpenArchitecture: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { SanctuaryDatabaseProvider.getDatabase(context) }
    val settings by db.sanctuarySettingsDao().getSettingsFlow().collectAsStateWithLifecycle(initialValue = null)
    val incidentsCount by db.incidentDao().getCount().collectAsStateWithLifecycle(initialValue = 0)
    val garden by db.virtueGardenDao().getGarden().collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()

    var simulatedInputText by remember { mutableStateOf("") }
    var lastEvaluationResult by remember { mutableStateOf<MoralEvaluationResult?>(null) }
    var isEvaluating by remember { mutableStateOf(false) }

    val currentLevel = garden?.virtueLevel ?: 1
    val currentPoints = garden?.totalVirtuePoints ?: 45
    val moralScore = (currentLevel * 18 + currentPoints / 5).coerceIn(40, 100)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SanctuaryNavy)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- HERO STATUS CARD ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, LiturgicalGold.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(VirtueGreenLight)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "SENTINEL ACTIVE",
                                color = VirtueGreenLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = "Edge AI sub-16ms",
                            color = LiturgicalGoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Circular Moral Score Meter
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(MarianBlueDark, SanctuaryNavy)
                                )
                            )
                            .border(3.dp, Brush.sweepGradient(listOf(LiturgicalGold, MarianBlueLight, LiturgicalGold)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$moralScore%",
                                color = LiturgicalGoldLight,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Moral Score",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Sanctuary Conscience Guard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SacredIvory
                    )

                    Text(
                        text = "Custody of the eyes (Matthew 5:8) & Always-On Neural Shielding",
                        style = MaterialTheme.typography.bodySmall,
                        color = SacredParchment.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenHolyLockscreen,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MarianBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.LockClock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Holy Screen", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onOpenArchitecture,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LiturgicalGoldLight),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(LiturgicalGold, LiturgicalGoldDark))),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edge OCR", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- QUICK STATS TILES ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = LiturgicalGold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Incidents", color = TextMuted, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("$incidentsCount Logged", color = SacredIvory, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Zero Cloud Sync", color = VirtueGreenLight, fontSize = 10.sp)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Spa, contentDescription = null, tint = MarianBlueLight, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Soul Garden", color = TextMuted, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Level ${garden?.virtueLevel ?: 1}", color = SacredIvory, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${garden?.totalVirtuePoints ?: 45} Virtue Pts", color = LiturgicalGoldLight, fontSize = 10.sp)
                    }
                }
            }
        }

        // --- LIVE CONSCIENCE ENGINE TEST BENCH ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MarianBlueLight.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Conscience Engine Inspector",
                            color = LiturgicalGoldLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        Text(
                            text = "Sub-16ms Edge OCR",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Test real-time moral text evaluation or trigger instant Virtue Shield detour overlay with Catholic scriptures & prayers.",
                        color = SacredParchment.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = simulatedInputText,
                        onValueChange = { simulatedInputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. adult content, casino gambling, violence, or pure prayer...", color = TextMuted, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LiturgicalGold,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = SacredIvory,
                            unfocusedTextColor = SacredIvory
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (simulatedInputText.isBlank()) {
                                    Toast.makeText(context, "Please enter test text", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val result = MoralConscienceEngine.evaluateText(simulatedInputText)
                                lastEvaluationResult = result

                                if (result.isViolation) {
                                    val incident = IncidentEntity(
                                        category = result.category?.name ?: MoralCategory.PURITY_CHASTITY.name,
                                        packageName = "com.android.chrome",
                                        appName = "Web Browser Test",
                                        triggerSnippet = simulatedInputText,
                                        confidenceScore = result.confidence,
                                        actionTaken = "Virtue Shield Overlay",
                                        detourScripture = result.detourScripture.reference
                                    )
                                    coroutineScope.launch {
                                        db.incidentDao().insert(incident)
                                    }
                                    onTriggerTestViolation(incident)
                                } else {
                                    Toast.makeText(context, "Passed: Noble & Pure (${result.latencyMs}ms)", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MarianBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Text", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val demoIncident = IncidentEntity(
                                    category = MoralCategory.PURITY_CHASTITY.name,
                                    packageName = "com.sample.unsafeapp",
                                    appName = "Unsafe Browser Tab",
                                    triggerSnippet = "Exposed content detected on viewport",
                                    confidenceScore = 0.98f,
                                    actionTaken = "Virtue Shield Interceptor",
                                    detourScripture = "Matthew 5:8"
                                )
                                coroutineScope.launch {
                                    db.incidentDao().insert(demoIncident)
                                }
                                onTriggerTestViolation(demoIncident)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = RubySacrifice),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.GppBad, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simulate Intercept", fontSize = 13.sp)
                        }
                    }

                    if (lastEvaluationResult != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val res = lastEvaluationResult!!
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SanctuaryDarkSurface, RoundedCornerShape(10.dp))
                                .border(1.dp, if (res.isViolation) RubySacrificeLight.copy(alpha = 0.5f) else VirtueGreenLight.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        if (res.isViolation) "VIOLATION DETECTED" else "NO VIOLATION (PURE)",
                                        color = if (res.isViolation) RubySacrificeLight else VirtueGreenLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text("Latency: ${res.latencyMs} ms", color = TextMuted, fontSize = 11.sp)
                                }
                                if (res.matchedTerm != null) {
                                    Text("Matched Keyword: ${res.matchedTerm}", color = LiturgicalGoldLight, fontSize = 11.sp)
                                }
                                Text("Scripture: ${res.detourScripture.reference} - \"${res.detourScripture.text}\"", color = SacredParchment, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                }
            }
        }

        // --- SYSTEM PERMISSIONS & SETTINGS QUICK ACTIONS ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Device Protection Policies",
                        color = SacredIvory,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccessibilityNew, contentDescription = null, tint = LiturgicalGold, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Accessibility Sentinel", color = SacredIvory, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Must be ENABLED for real-time viewport analysis", color = TextMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }

                    Divider(color = BorderSubtle, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                                val adminComponent = ComponentName(context, ConsciousnessDeviceAdminReceiver::class.java)
                                if (!dpm.isAdminActive(adminComponent)) {
                                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Sanctuary Device Admin safeguards uninstall prevention and device integrity.")
                                    }
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(context, "Device Admin is active & guarding system.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MarianBlueLight, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Device Admin Policy", color = SacredIvory, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Prevents unauthorized uninstallation & tampering", color = TextMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun HolyLockscreenDialog(onDismiss: () -> Unit) {
    val verses = listOf(
        MoralConscienceEngine.SCRIPTURE_MATTHEW_5_8,
        MoralConscienceEngine.SCRIPTURE_PHILIPPIANS_4_8,
        MoralConscienceEngine.SCRIPTURE_1COR_6_19,
        MoralConscienceEngine.SCRIPTURE_EPHESIANS_6_11,
        MoralConscienceEngine.SCRIPTURE_PSALM_23
    )
    val quotes = listOf(
        "\"The Eucharist is my highway to Heaven.\" — Blessed Carlo Acutis",
        "\"Death rather than sin! Jesus and Mary shall be my best friends.\" — St. Dominic Savio",
        "\"My vocation is Love! Miss no single opportunity of making a small sacrifice.\" — St. Thérèse",
        "\"Who is like unto God?\" — St. Michael the Archangel"
    )

    var currentVerseIndex by remember { mutableIntStateOf(0) }
    val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    val currentTime = remember { timeFormat.format(Date()) }
    val currentDate = remember { dateFormat.format(Date()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF03070C),
                            SanctuaryNavy,
                            Color(0xFF0D1824)
                        )
                    )
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            onDismiss()
                        },
                        onTap = {
                            currentVerseIndex = (currentVerseIndex + 1) % verses.size
                        }
                    )
                }
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Liturgical Date & Time
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentDate.uppercase(),
                        color = LiturgicalGoldLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = currentTime,
                        color = SacredIvory,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-1).sp
                    )
                }

                // Center Monstrance & Sacred Verse
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(LiturgicalGold.copy(alpha = 0.3f), Color.Transparent)
                                )
                            )
                            .border(1.dp, LiturgicalGold.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Monstrance",
                            tint = LiturgicalGoldLight,
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    val activeVerse = verses[currentVerseIndex]

                    Text(
                        text = "“${activeVerse.text}”",
                        color = SacredIvory,
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "— ${activeVerse.reference}",
                        color = LiturgicalGoldLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = quotes[currentVerseIndex % quotes.size],
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Bottom Hint
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Tap to rotate Scripture • Double-tap to wake device",
                        color = SacredParchment.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SacredIvory),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(LiturgicalGold, LiturgicalGoldLight))),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Exit Holy Lockscreen", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ArchitectureExplainerDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SanctuaryNavy.copy(alpha = 0.95f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, LiturgicalGold.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = LiturgicalGold, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Edge AI Architecture",
                                color = LiturgicalGoldLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = SacredIvory)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val features = listOf(
                        Triple(Icons.Default.Speed, "Sub-16ms Edge OCR", "Real-time on-device viewport scanning without sending user data over network."),
                        Triple(Icons.Default.VpnLock, "Zero-Cloud Privacy Guarantee", "Images and text never leave the device. Stored locally in encrypted Room database."),
                        Triple(Icons.Default.Church, "Catholic Conscience Heuristics", "Scripture & Catechism based classification for purity, temperance, charity, and truth."),
                        Triple(Icons.Default.Security, "Device Admin Sentinel", "System-level uninstallation block and anti-tamper safeguards with parent PIN gate.")
                    )

                    features.forEach { (icon, title, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MarianBlueDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = LiturgicalGoldLight, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(title, color = SacredIvory, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(desc, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MarianBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Understood", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
