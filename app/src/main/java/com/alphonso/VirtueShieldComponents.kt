package com.alphonso

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alphonso.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun VirtueShieldDetourDialog(
    incident: IncidentEntity?,
    onDismiss: () -> Unit,
    onPrayerCompleted: () -> Unit = {}
) {
    if (incident == null) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Scripture & Saint, 1 = Battle Prayers, 2 = Rosary Decade
    var rosaryBeadCount by remember { mutableIntStateOf(0) }
    var prayerOffered by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { /* Require active acknowledgment */ },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SanctuaryNavy,
                            Color(0xFF0F1B29),
                            SanctuaryDarkSurface
                        )
                    )
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .border(1.5.dp, LiturgicalGold.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SanctuaryCardSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with Shield & Monstrance Icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(LiturgicalGold, LiturgicalGoldDark)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Virtue Shield",
                            tint = SanctuaryNavy,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "VIRTUE SHIELD INTERCEPT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = LiturgicalGoldLight,
                        letterSpacing = 1.5.sp
                    )

                    Text(
                        text = "Protected Custody of Mind & Heart",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Breach Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RubySacrifice.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RubySacrificeLight.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = RubySacrificeLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${incident.category}: Guarded trigger detected",
                                style = MaterialTheme.typography.labelMedium,
                                color = RubySacrificeLight,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tab Row: Scripture Detour, Battle Prayer, Rosary Bead
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = SanctuaryDarkSurface,
                        contentColor = LiturgicalGoldLight,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = LiturgicalGold
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Scripture", fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Saint Prayer", fontSize = 12.sp) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Rosary Decade", fontSize = 12.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tab Content
                    when (selectedTab) {
                        0 -> ScriptureAndSaintTab(incident)
                        1 -> BattlePrayersTab(incident)
                        2 -> RosaryDecadeTab(
                            beadCount = rosaryBeadCount,
                            onIncrement = {
                                if (rosaryBeadCount < 10) rosaryBeadCount++
                                if (rosaryBeadCount == 10) prayerOffered = true
                            }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f, fill = false))
                    Spacer(modifier = Modifier.height(20.dp))

                    // Bottom Action Button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val db = SanctuaryDatabaseProvider.getDatabase(context)
                                // Add virtue water points for reciting prayer detour
                                db.virtueGardenDao().addWaterPoints(20, System.currentTimeMillis())
                            }
                            onPrayerCompleted()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LiturgicalGold,
                            contentColor = SanctuaryNavy
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Amen • Return with Pure Heart (+20 Virtue)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptureAndSaintTab(incident: IncidentEntity) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Scripture Box
        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryDarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = LiturgicalGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Holy Scripture: Matthew 5:8",
                        fontWeight = FontWeight.Bold,
                        color = LiturgicalGoldLight,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\"Blessed are the pure in heart, for they shall see God.\"",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = SacredIvory,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Philippians 4:8: \"Whatever is true, noble, right, pure, lovely, and admirable—think on these things.\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Saint Hero Inspiration Box
        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryDarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MarianBlueLight,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Saint Hero: St. Dominic Savio",
                        fontWeight = FontWeight.Bold,
                        color = MarianBlueLight,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\"Death rather than sin! Jesus and Mary shall be my best friends.\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SacredIvory
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Patron of youth and purity. When tempted, St. Dominic immediately made the Sign of the Cross and offered his eyes to Christ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun BattlePrayersTab(incident: IncidentEntity) {
    Column {
        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryDarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "⚔️ Saint Michael Archangel Battle Prayer",
                    fontWeight = FontWeight.Bold,
                    color = LiturgicalGoldLight,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Saint Michael the Archangel, defend us in battle. Be our protection against the wickedness and snares of the devil. May God rebuke him, we humbly pray; and do thou, O Prince of the Heavenly Host, by the power of God, cast into hell Satan and all the evil spirits who prowl about the world seeking the ruin of souls. Amen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SacredIvory,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = SanctuaryDarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🌹 Hail Mary for Custody of the Eyes",
                    fontWeight = FontWeight.Bold,
                    color = MarianBlueLight,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hail Mary, full of grace, the Lord is with thee. Blessed art thou among women, and blessed is the fruit of thy womb, Jesus. Holy Mary, Mother of God, pray for us sinners, now and at the hour of our death. Amen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SacredIvory,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun RosaryDecadeTab(
    beadCount: Int,
    onIncrement: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Interactive Rosary Decade for Chastity",
            fontWeight = FontWeight.Bold,
            color = LiturgicalGoldLight,
            fontSize = 14.sp
        )
        Text(
            text = "Tap the bead to pray each Hail Mary ($beadCount / 10)",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Visual Beads Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 1..10) {
                val isDone = i <= beadCount
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDone) LiturgicalGold else SanctuaryDarkSurface
                        )
                        .border(
                            1.dp,
                            if (isDone) LiturgicalGoldLight else BorderSubtle,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = SanctuaryNavy,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Text(
                            text = "$i",
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onIncrement,
            enabled = beadCount < 10,
            colors = ButtonDefaults.buttonColors(
                containerColor = MarianBlue,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.TouchApp, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (beadCount < 10) "Pray Hail Mary #${beadCount + 1}" else "Decade Complete! 🕊️")
        }
    }
}
