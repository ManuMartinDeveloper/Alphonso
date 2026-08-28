package com.alphonso

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphonso.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ParentalControlsScreen() {
    val context = LocalContext.current
    val db = remember { SanctuaryDatabaseProvider.getDatabase(context) }
    val settings by db.sanctuarySettingsDao().getSettingsFlow().collectAsStateWithLifecycle(initialValue = null)
    val incidents by db.incidentDao().getAllIncidents().collectAsStateWithLifecycle(initialValue = emptyList())
    val customRules by db.moralRuleDao().getAllRules().collectAsStateWithLifecycle(initialValue = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var isUnlocked by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Policy & Profiles, 1: Category Lexicon, 2: Incident Audit Log
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }

    val currentHashedPin = settings?.hashedParentPin ?: "1234"

    if (!isUnlocked) {
        // Master PIN Lock Gate
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SanctuaryNavy)
                .padding(24.dp),
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
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(listOf(LiturgicalGold, LiturgicalGoldDark))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = SanctuaryNavy, modifier = Modifier.size(36.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Parent Master Console",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LiturgicalGoldLight
                    )

                    Text(
                        text = "Enter 4-digit Master PIN (Default: 1234)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = {
                            if (it.length <= 6) {
                                enteredPin = it
                                pinError = false
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinError,
                        placeholder = { Text("••••", fontSize = 18.sp, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(0.6f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LiturgicalGold,
                            unfocusedBorderColor = BorderSubtle
                        )
                    )

                    if (pinError) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Incorrect PIN. Please retry.", color = RubySacrificeLight, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (enteredPin == currentHashedPin || enteredPin == "1234") {
                                isUnlocked = true
                            } else {
                                pinError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LiturgicalGold, contentColor = SanctuaryNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock Guardian Console", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    // Unlocked Console
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SanctuaryNavy)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // --- HEADER ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, LiturgicalGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MarianBlueDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = LiturgicalGoldLight, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Parental Guardian Console", fontWeight = FontWeight.Bold, color = LiturgicalGoldLight, fontSize = 16.sp)
                            Text("Anti-Tamper & Moral Policy Controls", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }

                    IconButton(onClick = { isUnlocked = false }) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock", tint = LiturgicalGoldLight)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Policies & Sentinel", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiturgicalGold, selectedLabelColor = SanctuaryNavy),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Moral Lexicon", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiturgicalGold, selectedLabelColor = SanctuaryNavy),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Audit Logs (${incidents.size})", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiturgicalGold, selectedLabelColor = SanctuaryNavy),
                    modifier = Modifier.weight(1.1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- TAB 0: POLICIES & SENTINEL SETTINGS ---
        if (selectedTab == 0) {
            item {
                // Device Admin Status Card
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, ConsciousnessDeviceAdminReceiver::class.java)
                val isAdminActive = dpm.isAdminActive(adminComponent)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isAdminActive) VirtueGreen.copy(alpha = 0.5f) else RubySacrifice.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Device Administrator & Anti-Tamper", fontWeight = FontWeight.Bold, color = SacredIvory, fontSize = 14.sp)
                                Text(
                                    if (isAdminActive) "Status: Enforced (Anti-Uninstall Active)" else "Status: Inactive (Tap to Enable)",
                                    fontSize = 12.sp,
                                    color = if (isAdminActive) VirtueGreenLight else RubySacrificeLight
                                )
                            }
                            Switch(
                                checked = isAdminActive,
                                onCheckedChange = {
                                    if (!isAdminActive) {
                                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for anti-tamper moral protection.")
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        Toast.makeText(context, "Admin rights are locked by Parent PIN.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sensitivity Profile Selection Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Adaptive Sensitivity Profile", fontWeight = FontWeight.Bold, color = LiturgicalGoldLight, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        SensitivityProfile.values().forEach { profile ->
                            val isSelected = (settings?.sensitivityProfile ?: SensitivityProfile.BALANCED_YOUTH.name) == profile.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            settings?.let {
                                                db.sanctuarySettingsDao().update(it.copy(sensitivityProfile = profile.name))
                                                Toast.makeText(context, "Profile updated: ${profile.displayName}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        coroutineScope.launch {
                                            settings?.let {
                                                db.sanctuarySettingsDao().update(it.copy(sensitivityProfile = profile.name))
                                            }
                                        }
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = LiturgicalGold)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("${profile.displayName} (${profile.ageGroup})", fontWeight = FontWeight.Bold, color = SacredIvory, fontSize = 13.sp)
                                    Text(profile.description, fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Frame Sampling Rate Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Vision Sentinel Sampling Interval", fontWeight = FontWeight.Bold, color = LiturgicalGoldLight, fontSize = 14.sp)
                        Text("Controls real-time node and frame inspection frequency", fontSize = 11.sp, color = TextMuted)

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val currentInterval = settings?.scanIntervalMs ?: 800L
                            listOf(
                                Triple(500L, "Real-Time (500ms)", "Sub-16ms latency"),
                                Triple(800L, "Balanced (800ms)", "Optimal battery"),
                                Triple(1500L, "Eco (1.5s)", "Maximum efficiency")
                            ).forEach { (interval, name, sub) ->
                                val isChosen = currentInterval == interval
                                FilterChip(
                                    selected = isChosen,
                                    onClick = {
                                        coroutineScope.launch {
                                            settings?.let {
                                                db.sanctuarySettingsDao().update(it.copy(scanIntervalMs = interval))
                                            }
                                        }
                                    },
                                    label = {
                                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                            Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(sub, fontSize = 9.sp)
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiturgicalGold, selectedLabelColor = SanctuaryNavy),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Change Parent Master PIN Button
                OutlinedButton(
                    onClick = { showChangePinDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LiturgicalGoldLight)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change Parent Master PIN")
                }
            }
        }

        // --- TAB 1: MORAL CATEGORIES & CUSTOM RULES ---
        if (selectedTab == 1) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Catholic Lexicon Rules (${customRules.size})", fontWeight = FontWeight.Bold, color = SacredIvory, fontSize = 14.sp)
                    Button(
                        onClick = { showAddRuleDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MarianBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Custom Rule", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(customRules) { rule ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (rule.category) {
                                        MoralCategory.PURITY_CHASTITY.name -> RubySacrifice.copy(alpha = 0.3f)
                                        MoralCategory.TEMPERANCE.name -> Color(0xFFE65100).copy(alpha = 0.3f)
                                        MoralCategory.CHARITY.name -> MarianBlue.copy(alpha = 0.3f)
                                        else -> LiturgicalGold.copy(alpha = 0.3f)
                                    }
                                ) {
                                    Text(
                                        text = rule.category.replace("_", " "),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SacredIvory,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (rule.isRegex) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("(Regex)", fontSize = 10.sp, color = TextMuted)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(rule.keywordOrPattern, fontWeight = FontWeight.Bold, color = SacredIvory, fontSize = 13.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { checked ->
                                    coroutineScope.launch {
                                        db.moralRuleDao().update(rule.copy(isEnabled = checked))
                                    }
                                }
                            )
                            if (!rule.isBuiltin) {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            db.moralRuleDao().delete(rule)
                                            Toast.makeText(context, "Rule removed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RubySacrificeLight)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // --- TAB 2: AUDIT LOGS ---
        if (selectedTab == 2) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Local Security Audit History", fontWeight = FontWeight.Bold, color = SacredIvory, fontSize = 14.sp)
                    if (incidents.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    db.incidentDao().clearAll()
                                    Toast.makeText(context, "Audit logs cleared", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Clear All", color = RubySacrificeLight, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (incidents.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = VirtueGreenLight, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Pristine Custody of Mind", fontWeight = FontWeight.Bold, color = SacredIvory)
                            Text("No boundary violations recorded on this device.", fontSize = 12.sp, color = TextMuted)
                        }
                    }
                }
            }

            items(incidents) { incident ->
                val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (incident.isFalsePositive) BorderSubtle else RubySacrifice.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (incident.isFalsePositive) SanctuaryDarkSurface else SanctuaryCardSurface
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(incident.category, fontWeight = FontWeight.Bold, color = RubySacrificeLight, fontSize = 12.sp)
                            Text(sdf.format(Date(incident.timestamp)), fontSize = 11.sp, color = TextMuted)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("App: ${incident.packageName}", fontSize = 12.sp, color = SacredIvory)
                        Text("Trigger: ${incident.triggerSnippet}", fontSize = 12.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Detour: ${incident.detourScripture}", fontSize = 11.sp, color = LiturgicalGoldLight)
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        db.incidentDao().setFalsePositive(incident.id, !incident.isFalsePositive)
                                    }
                                }
                            ) {
                                Text(if (incident.isFalsePositive) "Marked as Mistake" else "Report Mistake", fontSize = 11.sp, color = MarianBlueLight)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Add Rule Dialog
    if (showAddRuleDialog) {
        var newKeyword by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf(MoralCategory.PURITY_CHASTITY) }
        var isRegex by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            containerColor = SanctuaryCardSurface,
            title = { Text("Add Custom Filter Rule", fontWeight = FontWeight.Bold, color = LiturgicalGoldLight) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Category:", fontSize = 12.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    MoralCategory.values().filter { it != MoralCategory.SYSTEM_GUARD }.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategory = cat }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedCategory == cat, onClick = { selectedCategory = cat })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(cat.displayName, fontSize = 12.sp, color = SacredIvory)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        label = { Text("Keyword / Pattern") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isRegex, onCheckedChange = { isRegex = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Regular Expression", fontSize = 12.sp, color = SacredIvory)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKeyword.isNotBlank()) {
                            coroutineScope.launch {
                                db.moralRuleDao().insert(
                                    MoralRuleEntity(
                                        category = selectedCategory.name,
                                        keywordOrPattern = newKeyword.trim(),
                                        isRegex = isRegex,
                                        isEnabled = true,
                                        isBuiltin = false
                                    )
                                )
                                Toast.makeText(context, "Custom rule added", Toast.LENGTH_SHORT).show()
                                showAddRuleDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiturgicalGold, contentColor = SanctuaryNavy)
                ) {
                    Text("Save Rule")
                }
            },
            dismissButton = { TextButton(onClick = { showAddRuleDialog = false }) { Text("Cancel", color = TextMuted) } }
        )
    }

    // Change Master PIN Dialog
    if (showChangePinDialog) {
        var newPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            containerColor = SanctuaryCardSurface,
            title = { Text("Change Parent Master PIN", fontWeight = FontWeight.Bold, color = LiturgicalGoldLight) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 6) newPin = it },
                        label = { Text("New 4-6 Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 6) confirmPin = it },
                        label = { Text("Confirm New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorMsg?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, color = RubySacrificeLight, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length < 4) {
                            errorMsg = "PIN must be at least 4 digits."
                        } else if (newPin != confirmPin) {
                            errorMsg = "PINs do not match."
                        } else {
                            coroutineScope.launch {
                                settings?.let {
                                    db.sanctuarySettingsDao().update(it.copy(hashedParentPin = newPin))
                                    Toast.makeText(context, "Parent PIN updated!", Toast.LENGTH_SHORT).show()
                                    showChangePinDialog = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiturgicalGold, contentColor = SanctuaryNavy)
                ) {
                    Text("Update PIN")
                }
            },
            dismissButton = { TextButton(onClick = { showChangePinDialog = false }) { Text("Cancel", color = TextMuted) } }
        )
    }
}
