package com.alphonso

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphonso.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ChildrenFaithHubScreen() {
    val context = LocalContext.current
    val db = remember { SanctuaryDatabaseProvider.getDatabase(context) }
    val quests by db.faithQuestDao().getAllQuests().collectAsStateWithLifecycle(initialValue = emptyList())
    val garden by db.virtueGardenDao().getGarden().collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()

    var selectedSection by remember { mutableIntStateOf(0) } // 0: Virtue Garden & Quests, 1: Saint Heroes, 2: Family Consecration
    var selectedSaintDetail by remember { mutableStateOf<SaintProfile?>(null) }
    var showConsecrationDialog by remember { mutableStateOf(false) }

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
                                Brush.radialGradient(listOf(LiturgicalGold, LiturgicalGoldDark))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "Children of Light",
                            tint = SanctuaryNavy,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Children of Light",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = LiturgicalGoldLight
                        )
                        Text(
                            text = "Cultivate the Garden of Virtue in Your Soul",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-Navigation Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    label = { Text("Virtue Garden & Quests", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Park, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LiturgicalGold,
                        selectedLabelColor = SanctuaryNavy
                    ),
                    modifier = Modifier.weight(1.3f)
                )
                FilterChip(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    label = { Text("Saint Heroes", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LiturgicalGold,
                        selectedLabelColor = SanctuaryNavy
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedSection == 2,
                    onClick = { selectedSection = 2 },
                    label = { Text("Consecration", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LiturgicalGold,
                        selectedLabelColor = SanctuaryNavy
                    ),
                    modifier = Modifier.weight(1.1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- SECTION 0: VIRTUE GARDEN & DAILY FAITH QUESTS ---
        if (selectedSection == 0) {
            item {
                VirtueGardenCard(
                    garden = garden,
                    onWaterGarden = {
                        coroutineScope.launch {
                            val currentPts = garden?.totalVirtuePoints ?: 0
                            val newPts = currentPts + 15
                            val newLevel = when {
                                newPts >= 500 -> 4
                                newPts >= 250 -> 3
                                newPts >= 100 -> 2
                                else -> 1
                            }
                            val stageName = when (newLevel) {
                                4 -> "Golden Tree of Divine Light"
                                3 -> "Blossom of Purity"
                                2 -> "Sprout of Charity"
                                else -> "Seedling of Grace"
                            }
                            db.virtueGardenDao().addWaterPoints(15, System.currentTimeMillis())
                            db.virtueGardenDao().updateLevel(newLevel, stageName)
                            Toast.makeText(context, "🌹 Soul Watered with Hail Mary! (+15 Virtue)", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Faith Quests",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SacredIvory
                    )
                    Text(
                        text = "${quests.count { it.isCompleted }} / ${quests.size} Complete",
                        style = MaterialTheme.typography.labelMedium,
                        color = LiturgicalGoldLight,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            items(quests) { quest ->
                FaithQuestItem(
                    quest = quest,
                    onToggleComplete = {
                        coroutineScope.launch {
                            val newStatus = !quest.isCompleted
                            db.faithQuestDao().setQuestCompleted(quest.id, newStatus, System.currentTimeMillis())
                            if (newStatus) {
                                db.virtueGardenDao().addWaterPoints(quest.virtuePoints, System.currentTimeMillis())
                                Toast.makeText(context, "✨ Quest Complete: +${quest.virtuePoints} Virtue Points!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // --- SECTION 1: YOUTH SAINT HEROES ---
        if (selectedSection == 1) {
            item {
                Text(
                    text = "Youth Saint Role Models",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LiturgicalGoldLight,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Heroes who guarded their hearts and conquered the world through Christ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            val allSaints = listOf(
                MoralConscienceEngine.SAINT_CARLO_ACUTIS,
                MoralConscienceEngine.SAINT_DOMINIC_SAVIO,
                MoralConscienceEngine.SAINT_THERESE,
                MoralConscienceEngine.SAINT_TARCISIUS,
                MoralConscienceEngine.SAINT_MARIA_GORETTI,
                MoralConscienceEngine.SAINT_MICHAEL
            )

            items(allSaints) { saint ->
                SaintHeroCard(
                    saint = saint,
                    onClick = { selectedSaintDetail = saint }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // --- SECTION 2: FAMILY CONSECRATION & BLESSINGS ---
        if (selectedSection == 2) {
            item {
                FamilyConsecrationCard(
                    onOpenConsecration = { showConsecrationDialog = true }
                )
            }
        }
    }

    // Detail Dialogs
    selectedSaintDetail?.let { saint ->
        SaintDetailDialog(saint = saint, onDismiss = { selectedSaintDetail = null })
    }

    if (showConsecrationDialog) {
        FamilyConsecrationDialog(onDismiss = { showConsecrationDialog = false })
    }
}

@Composable
fun VirtueGardenCard(
    garden: VirtueGardenEntity?,
    onWaterGarden: () -> Unit
) {
    val level = garden?.virtueLevel ?: 1
    val points = garden?.totalVirtuePoints ?: 0
    val waterCount = garden?.waterCount ?: 0
    val stageName = garden?.stageName ?: "Seedling of Grace"

    val nextLevelTarget = when (level) {
        1 -> 100
        2 -> 250
        3 -> 500
        else -> 1000
    }
    val progress = (points.toFloat() / nextLevelTarget).coerceIn(0f, 1f)

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
                Column {
                    Text(
                        text = "THE SOUL'S VIRTUE GARDEN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LiturgicalGoldLight,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Level $level • $stageName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SacredIvory
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LiturgicalGold.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LiturgicalGold.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "✨ $points Pts",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = LiturgicalGoldLight,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Visual Tree / Plant Stage
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                when (level) {
                                    4 -> Color(0xFFFFD54F)
                                    3 -> Color(0xFFF48FB1)
                                    2 -> Color(0xFF81C784)
                                    else -> Color(0xFFA5D6A7)
                                }.copy(alpha = 0.3f),
                                SanctuaryDarkSurface
                            )
                        )
                    )
                    .border(1.dp, LiturgicalGold.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (level) {
                        4 -> "🌳✨"
                        3 -> "🌸"
                        2 -> "🌿"
                        else -> "🌱"
                    },
                    fontSize = 46.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when (level) {
                    4 -> "Golden Tree of Divine Light — Filled with the Holy Spirit!"
                    3 -> "Blossom of Purity — Pure in heart, radiating Christ's joy."
                    2 -> "Sprout of Charity — Growing in love and obedience."
                    else -> "Seedling of Grace — Rooted in prayer and morning offering."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SacredParchment,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Bar to next stage
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Growth to Next Stage", fontSize = 11.sp, color = TextMuted)
                    Text("$points / $nextLevelTarget pts", fontSize = 11.sp, color = LiturgicalGoldLight, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = LiturgicalGold,
                    trackColor = SanctuaryNavy
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Water with Hail Mary Button
            Button(
                onClick = onWaterGarden,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MarianBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color(0xFF64B5F6))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Water with Hail Mary (+15 Virtue Pts)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FaithQuestItem(
    quest: FaithQuestEntity,
    onToggleComplete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleComplete() }
            .border(
                1.dp,
                if (quest.isCompleted) LiturgicalGold.copy(alpha = 0.6f) else BorderSubtle,
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (quest.isCompleted) SanctuaryDarkSurface else SanctuaryCardSurface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (quest.isCompleted) LiturgicalGold else SanctuaryNavy
                    )
            ) {
                Icon(
                    imageVector = if (quest.isCompleted) Icons.Default.Check else Icons.Default.Circle,
                    contentDescription = "Complete",
                    tint = if (quest.isCompleted) SanctuaryNavy else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = quest.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (quest.isCompleted) LiturgicalGoldLight else SacredIvory
                    )
                    if (quest.streakDays > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🔥 ${quest.streakDays}d",
                            fontSize = 11.sp,
                            color = Color(0xFFFFB74D),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = quest.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SanctuaryNavy,
                border = androidx.compose.foundation.BorderStroke(1.dp, LiturgicalGold.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "+${quest.virtuePoints}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiturgicalGoldLight,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SaintHeroCard(
    saint: SaintProfile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, MarianBlue.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MarianBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = SacredIvory,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = saint.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LiturgicalGoldLight
                    )
                    Text(
                        text = saint.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Read More",
                    tint = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = saint.motto,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = SacredParchment,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun SaintDetailDialog(saint: SaintProfile, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SanctuaryCardSurface,
        title = {
            Column {
                Text(saint.name, fontWeight = FontWeight.Bold, color = LiturgicalGoldLight)
                Text(saint.title, fontSize = 12.sp, color = TextMuted)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SanctuaryDarkSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = saint.motto,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = SacredIvory,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Spiritual Biography", fontWeight = FontWeight.Bold, color = MarianBlueLight, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(saint.bio, style = MaterialTheme.typography.bodySmall, color = SacredParchment, lineHeight = 20.sp)

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Feast Day: ${saint.feastDay}", fontSize = 12.sp, color = LiturgicalGoldLight, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Patron of: ${saint.patronOf}", fontSize = 12.sp, color = TextMuted)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = LiturgicalGold, contentColor = SanctuaryNavy)
            ) {
                Text("St. ${saint.name.replace("Blessed ", "").replace("Saint ", "")}, Pray for Us!")
            }
        }
    )
}

@Composable
fun FamilyConsecrationCard(onOpenConsecration: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, LiturgicalGold.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = RubySacrificeLight,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Family Digital Consecration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LiturgicalGoldLight
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Consecrate this device and all family media to the Sacred Heart of Jesus and the Immaculate Heart of Mary. Invite Jesus Christ to be the true King and Guardian of your home's digital doorway.",
                style = MaterialTheme.typography.bodySmall,
                color = SacredParchment,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenConsecration,
                colors = ButtonDefaults.buttonColors(containerColor = LiturgicalGold, contentColor = SanctuaryNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Read Consecration Prayer & Blessing", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FamilyConsecrationDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SanctuaryCardSurface,
        title = {
            Text("Family Device Consecration", fontWeight = FontWeight.Bold, color = LiturgicalGoldLight)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Most Sacred Heart of Jesus and Immaculate Heart of Mary,\n\nWe humbly consecrate this device, our eyes, our minds, and our home to Your divine protection.\n\nGrant us the grace to use technology only for truth, learning, holy connection, and the building up of Your Kingdom. Shield our children from every snare of impurity, deception, and distraction.\n\nSaint Michael the Archangel, Saint Joseph, and our Guardian Angels, guard this screen and repel all evil from our presence.\n\nAmen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SacredIvory,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(colors = CardDefaults.cardColors(containerColor = SanctuaryDarkSurface), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("✝️ Daily Parental Blessing Ritual", fontWeight = FontWeight.Bold, color = MarianBlueLight, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Each morning or before sleep, trace the Sign of the Cross on your child's forehead with your thumb and say:\n\"May the Lord bless you and keep you; may His face shine upon you and protect your eyes and heart in Jesus' name. Amen.\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = LiturgicalGold, contentColor = SanctuaryNavy)
            ) {
                Text("Consecrate Device & Amen")
            }
        }
    )
}
