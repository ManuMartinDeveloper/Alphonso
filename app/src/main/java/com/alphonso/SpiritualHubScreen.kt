package com.alphonso

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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphonso.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class PrayerItem(
    val title: String,
    val subtitle: String,
    val category: String,
    val iconName: String,
    val prayerContent: String
)

@Composable
fun SpiritualHubScreen() {
    val context = LocalContext.current
    val db = remember { SanctuaryDatabaseProvider.getDatabase(context) }
    val examens by db.dailyExamenDao().getAllExamens().collectAsStateWithLifecycle(initialValue = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Prayer Sanctuary, 1: Daily Examen, 2: Conscience Counselor
    var selectedPrayer by remember { mutableStateOf<PrayerItem?>(null) }
    var showRosaryCounterDialog by remember { mutableStateOf(false) }
    var showNewExamenDialog by remember { mutableStateOf(false) }

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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(listOf(MarianBlueLight, MarianBlueDark))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelfImprovement,
                            contentDescription = "Spiritual Hub",
                            tint = SacredIvory,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Spiritual Sanctuary",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = LiturgicalGoldLight
                        )
                        Text(
                            text = "Holy Prayers, Daily Examen & Catholic Discernment",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub Navigation Tab Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Catholic Prayers", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiturgicalGold, selectedLabelColor = SanctuaryNavy),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Daily Examen", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiturgicalGold, selectedLabelColor = SanctuaryNavy),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Conscience Guide", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiturgicalGold, selectedLabelColor = SanctuaryNavy),
                    modifier = Modifier.weight(1.1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- TAB 0: CATHOLIC PRAYERS ---
        if (selectedTab == 0) {
            item {
                // Featured Rosary Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, LiturgicalGold.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = LiturgicalGoldLight)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("The Holy Rosary", fontWeight = FontWeight.Bold, color = LiturgicalGoldLight, fontSize = 16.sp)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = LiturgicalGold.copy(alpha = 0.2f)
                            ) {
                                Text("50 Beads", fontSize = 10.sp, color = LiturgicalGoldLight, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Meditation on the Mysteries of Christ with Our Blessed Mother. Includes Joyful, Luminous, Sorrowful, and Glorious mysteries.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SacredParchment
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showRosaryCounterDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = LiturgicalGold, contentColor = SanctuaryNavy),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Interactive Rosary Bead Counter", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Catholic Treasury of Prayers", fontWeight = FontWeight.Bold, color = SacredIvory, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))
            }

            val prayerList = listOf(
                PrayerItem(
                    title = "St. Michael Archangel Battle Prayer",
                    subtitle = "Spiritual warfare protection against demonic temptation",
                    category = "Protection",
                    iconName = "Shield",
                    prayerContent = "Saint Michael the Archangel, defend us in battle. Be our protection against the wickedness and snares of the devil. May God rebuke him, we humbly pray; and do thou, O Prince of the Heavenly Host, by the power of God, cast into hell Satan and all the evil spirits who prowl about the world seeking the ruin of souls. Amen."
                ),
                PrayerItem(
                    title = "The Angelus",
                    subtitle = "Traditional 6 AM / 12 PM / 6 PM incarnation prayer",
                    category = "Devotion",
                    iconName = "WbSunny",
                    prayerContent = "V. The Angel of the Lord declared unto Mary.\nR. And she conceived of the Holy Spirit.\n(Hail Mary...)\nV. Behold the handmaid of the Lord.\nR. Be it done unto me according to thy word.\n(Hail Mary...)\nV. And the Word was made flesh.\nR. And dwelt among us.\n(Hail Mary...)\nV. Pray for us, O Holy Mother of God.\nR. That we may be made worthy of the promises of Christ.\nLet us pray: Pour forth, we beseech Thee, O Lord, Thy grace into our hearts... Amen."
                ),
                PrayerItem(
                    title = "Chaplet of Divine Mercy",
                    subtitle = "3:00 PM Hour of Great Mercy for the whole world",
                    category = "Mercy",
                    iconName = "Favorite",
                    prayerContent = "Eternal Father, I offer You the Body and Blood, Soul and Divinity of Your dearly beloved Son, Our Lord Jesus Christ, in atonement for our sins and those of the whole world.\n\nFor the sake of His sorrowful Passion, have mercy on us and on the whole world.\n\nHoly God, Holy Mighty One, Holy Immortal One, have mercy on us and on the whole world."
                ),
                PrayerItem(
                    title = "The Memorare",
                    subtitle = "Powerful intercession of Our Lady in times of urgent need",
                    category = "Marian",
                    iconName = "Star",
                    prayerContent = "Remember, O most gracious Virgin Mary, that never was it known that anyone who fled to thy protection, implored thy help, or sought thine intercession was left unaided.\n\nInspired by this confidence, I fly unto thee, O Virgin of virgins, my mother; to thee do I come, before thee I stand, sinful and sorrowful. O Mother of the Word Incarnate, despise not my petitions, but in thy mercy hear and answer me. Amen."
                ),
                PrayerItem(
                    title = "Litany of Humility",
                    subtitle = "Cure for digital vanity, pride, and social media comparison",
                    category = "Virtue",
                    iconName = "SelfImprovement",
                    prayerContent = "O Jesus, meek and humble of heart, hear me.\nFrom the desire of being esteemed, deliver me, Jesus.\nFrom the desire of being loved, deliver me, Jesus.\nFrom the desire of being extolled, deliver me, Jesus.\nFrom the desire of being praised, deliver me, Jesus.\nFrom the desire of being preferred to others, deliver me, Jesus.\nFrom the fear of being humiliated, deliver me, Jesus.\nThat others may be loved more than I, Jesus, grant me the grace to desire it.\nThat others may be esteemed more than I, Jesus, grant me the grace to desire it. Amen."
                ),
                PrayerItem(
                    title = "Act of Contrition",
                    subtitle = "Sorrow for sin and firm resolution of amendment",
                    category = "Penance",
                    iconName = "CheckCircle",
                    prayerContent = "O my God, I am heartily sorry for having offended Thee, and I detest all my sins because of Thy just punishments, but most of all because they offend Thee, my God, Who art all-good and deserving of all my love. I firmly resolve, with the help of Thy grace, to sin no more and to avoid the near occasions of sin. Amen."
                )
            )

            items(prayerList) { prayer ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPrayer = prayer }
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MarianBlueDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = LiturgicalGoldLight, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prayer.title, fontWeight = FontWeight.Bold, color = SacredIvory, fontSize = 14.sp)
                            Text(prayer.subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // --- TAB 1: DAILY EXAMEN JOURNAL ---
        if (selectedTab == 1) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, LiturgicalGold.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Nightlight, contentDescription = null, tint = LiturgicalGoldLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ignatian & Catholic Nightly Examen", fontWeight = FontWeight.Bold, color = LiturgicalGoldLight, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Review your day in the light of Christ. Reflect on your digital purity, moments of gratitude, and resolve to walk in holy virtue tomorrow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SacredParchment,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { showNewExamenDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MarianBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Begin Tonight's Examen Reflection", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text("Past Examen Reflections", fontWeight = FontWeight.Bold, color = SacredIvory, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                if (examens.isEmpty()) {
                    Text(
                        "No past reflections yet. Take 3 minutes tonight to review your day with the Lord.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            items(examens) { examen ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SanctuaryDarkSurface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(examen.dateString, fontWeight = FontWeight.Bold, color = LiturgicalGoldLight, fontSize = 13.sp)
                            Text("Peace: ${"⭐".repeat(examen.peaceRating)}", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Gratitude: ${examen.gratitudeNote}", style = MaterialTheme.typography.bodySmall, color = SacredIvory)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Grace: ${examen.momentsOfGrace}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Resolution: ${examen.resolutionTomorrow}", style = MaterialTheme.typography.bodySmall, color = MarianBlueLight, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // --- TAB 2: CONSCIENCE COUNSELOR ---
        if (selectedTab == 2) {
            item {
                ConscienceCounselorSection()
            }
        }
    }

    // Detail Dialogs
    selectedPrayer?.let { prayer ->
        AlertDialog(
            onDismissRequest = { selectedPrayer = null },
            containerColor = SanctuaryCardSurface,
            title = {
                Text(prayer.title, fontWeight = FontWeight.Bold, color = LiturgicalGoldLight)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = prayer.prayerContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SacredIvory,
                        lineHeight = 22.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            db.virtueGardenDao().addWaterPoints(10, System.currentTimeMillis())
                            Toast.makeText(context, "Amen! +10 Virtue Points", Toast.LENGTH_SHORT).show()
                        }
                        selectedPrayer = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiturgicalGold, contentColor = SanctuaryNavy)
                ) {
                    Text("Amen (+10 Virtue)")
                }
            }
        )
    }

    if (showRosaryCounterDialog) {
        RosaryCounterFullDialog(onDismiss = { showRosaryCounterDialog = false })
    }

    if (showNewExamenDialog) {
        NewExamenDialog(
            onDismiss = { showNewExamenDialog = false },
            onSave = { gratitude, grace, challenge, resolution, rating ->
                coroutineScope.launch {
                    val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                    db.dailyExamenDao().insert(
                        DailyExamenEntity(
                            dateString = sdf.format(Date()),
                            gratitudeNote = gratitude,
                            momentsOfGrace = grace,
                            challengesFaced = challenge,
                            resolutionTomorrow = resolution,
                            peaceRating = rating
                        )
                    )
                    db.virtueGardenDao().addWaterPoints(30, System.currentTimeMillis())
                    Toast.makeText(context, "Examen recorded! +30 Virtue Points", Toast.LENGTH_SHORT).show()
                    showNewExamenDialog = false
                }
            }
        )
    }
}

@Composable
fun RosaryCounterFullDialog(onDismiss: () -> Unit) {
    var decade by remember { mutableIntStateOf(1) } // 1 to 5
    var bead by remember { mutableIntStateOf(0) } // 0 to 10
    val mysteryNames = listOf(
        "Joyful Mysteries (The Annunciation, Visitation, Nativity, Presentation, Finding in Temple)",
        "Luminous Mysteries (Baptism in Jordan, Wedding at Cana, Proclamation of Kingdom, Transfiguration, Eucharist)",
        "Sorrowful Mysteries (Agony in Garden, Scourging, Crowning with Thorns, Carrying Cross, Crucifixion)",
        "Glorious Mysteries (Resurrection, Ascension, Descent of Holy Spirit, Assumption, Coronation of Mary)"
    )
    var selectedMysteryIndex by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SanctuaryCardSurface,
        title = {
            Column {
                Text("Holy Rosary Bead Counter", fontWeight = FontWeight.Bold, color = LiturgicalGoldLight)
                Text("Decade $decade of 5 • Bead $bead / 10", fontSize = 12.sp, color = TextMuted)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Card(colors = CardDefaults.cardColors(containerColor = SanctuaryDarkSurface), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = mysteryNames[selectedMysteryIndex],
                        style = MaterialTheme.typography.bodySmall,
                        color = SacredParchment,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 10 Beads Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 1..10) {
                        val isDone = i <= bead
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isDone) LiturgicalGold else SanctuaryNavy)
                                .border(1.dp, if (isDone) LiturgicalGoldLight else BorderSubtle, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$i",
                                fontSize = 9.sp,
                                color = if (isDone) SanctuaryNavy else TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "🌹 \"Hail Mary, full of grace, the Lord is with thee. Blessed art thou among women, and blessed is the fruit of thy womb, Jesus. Holy Mary, Mother of God, pray for us sinners, now and at the hour of our death. Amen.\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = SacredIvory,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (bead < 10) {
                        bead++
                    } else if (decade < 5) {
                        decade++
                        bead = 1
                    } else {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = LiturgicalGold, contentColor = SanctuaryNavy)
            ) {
                Text(if (bead == 10 && decade == 5) "Finish Rosary" else "Next Bead (Hail Mary)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) }
        }
    )
}

@Composable
fun NewExamenDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int) -> Unit
) {
    var gratitude by remember { mutableStateOf("") }
    var grace by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf("") }
    var resolution by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SanctuaryCardSurface,
        title = {
            Text("Nightly Examen with Christ", fontWeight = FontWeight.Bold, color = LiturgicalGoldLight)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("1. Gratitude: What gifts did God give you today?", fontSize = 12.sp, color = MarianBlueLight, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = gratitude,
                    onValueChange = { gratitude = it },
                    placeholder = { Text("e.g., A peaceful family dinner, clear focus at school...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("2. Light & Grace: Where did you feel the Holy Spirit?", fontSize = 12.sp, color = MarianBlueLight, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = grace,
                    onValueChange = { grace = it },
                    placeholder = { Text("e.g., Chose to put the phone away during prayer...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("3. Resolution for Tomorrow:", fontSize = 12.sp, color = MarianBlueLight, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = resolution,
                    onValueChange = { resolution = it },
                    placeholder = { Text("e.g., Recite morning offering before touching the screen...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        gratitude.ifBlank { "Grateful for life and grace today." },
                        grace.ifBlank { "Felt God's peace during quiet prayer." },
                        challenge.ifBlank { "Resisted screen distractions." },
                        resolution.ifBlank { "Offer the morning to Jesus." },
                        rating
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = LiturgicalGold, contentColor = SanctuaryNavy)
            ) {
                Text("Save Examen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
fun ConscienceCounselorSection() {
    var userQuery by remember { mutableStateOf("") }
    var counselorResponse by remember { mutableStateOf<String?>(null) }
    var isThinking by remember { mutableStateOf(false) }

    Column {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, LiturgicalGold.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = LiturgicalGoldLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Catholic Conscience Counselor", fontWeight = FontWeight.Bold, color = LiturgicalGoldLight, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Ask any moral discernment question about digital habits, entertainment choices, online friendships, or spiritual growth according to Catholic moral theology and Catechism principles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SacredParchment,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = userQuery,
                    onValueChange = { userQuery = it },
                    placeholder = { Text("e.g. How do I resist compulsive phone scrolling? Is this game wholesome?", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        isThinking = true
                        counselorResponse = generateCatholicCounsel(userQuery)
                        isThinking = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiturgicalGold, contentColor = SanctuaryNavy),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Discern in Christ", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        counselorResponse?.let { response ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MarianBlueLight.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SanctuaryDarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MarianBlueLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Spiritual Counsel & Catechism Wisdom", fontWeight = FontWeight.Bold, color = MarianBlueLight, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = response,
                        style = MaterialTheme.typography.bodySmall,
                        color = SacredIvory,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

fun generateCatholicCounsel(query: String): String {
    val lower = query.lowercase()
    return when {
        lower.contains("scroll") || lower.contains("phone") || lower.contains("addict") || lower.contains("time") ->
            "🕊️ Principle of Digital Temperance (CCC 1809):\n\"Temperance is the moral virtue that moderates the attraction of pleasures and provides balance in the use of created goods.\"\n\nPractical Counsel:\n1. Consecrate your screen time each morning with the Morning Offering.\n2. Implement a 1-hour digital fast before bed to foster silent recollection with God.\n3. Remember the motto of Blessed Carlo Acutis: 'All people are born as originals, but many die as photocopies' when enslaved by algorithmic feeds."

        lower.contains("pure") || lower.contains("lust") || lower.contains("look") || lower.contains("eye") || lower.contains("tempt") ->
            "🌹 Custody of the Eyes (Matthew 5:8, CCC 2517-2533):\n\"Blessed are the pure in heart, for they shall see God.\"\n\nPractical Counsel:\n1. The moment an unwholesome image or suggestion appears, instantly invoke the Holy Name of Jesus and make the internal Sign of the Cross.\n2. Ask Saint Dominic Savio and Our Lady of Purity for angelic custody of your gaze.\n3. Remember your body is a consecrated Temple of the Holy Spirit (1 Cor 6:19)."

        lower.contains("friend") || lower.contains("bully") || lower.contains("hate") || lower.contains("speech") ->
            "✝️ Christian Charity & Pure Speech (Ephesians 4:29):\n\"Do not let any unwholesome talk come out of your mouths, but only what is helpful for building others up.\"\n\nPractical Counsel:\n1. Never send a comment online that you would not speak in the bodily presence of Christ.\n2. Pray the Litany of Humility to detach from online approval, likes, and worldly praise."

        else ->
            "✨ Catholic Discernment Rule (Philippians 4:8):\n\"Whatever is true, whatever is noble, whatever is right, whatever is pure, whatever is lovely... think about such things.\"\n\nAsk yourself three questions:\n1. Does this draw my heart closer to the peace of Christ?\n2. Would I feel comfortable viewing or doing this with my Guardian Angel beside me?\n3. Does this build up my mind in genuine virtue and wisdom?\n\nIf in doubt, choose the higher path of holy silence and prayer."
    }
}
