package com.studytracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.studytracker.ui.components.*

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadStats()
    }

    when (val state = uiState) {
        is StatsUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is StatsUiState.Success -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = "Overview",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        title = "Today",
                        value = "${state.todayMinutes / 60}h ${state.todayMinutes % 60}m",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = "This Week",
                        value = "${state.weekMinutes / 60}h ${state.weekMinutes % 60}m",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        title = "This Month",
                        value = "${state.monthMinutes / 60}h ${state.monthMinutes % 60}m",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = "All Time",
                        value = "${state.allTimeMinutes / 60}h ${state.allTimeMinutes % 60}m",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                ProgressGoalBar(
                    currentMinutes = state.todayMinutes,
                    goalMinutes = state.dailyGoalMinutes
                )

                Spacer(modifier = Modifier.height(24.dp))

                StreakCard(
                    currentStreak = state.currentStreak,
                    longestStreak = state.longestStreak
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Last 7 Days",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (state.last7Days.isNotEmpty()) {
                    val chartEntryModel = entryModelOf(
                        *state.last7Days.mapIndexed { index, data ->
                            index.toFloat() to data.totalMinutes
                        }.toTypedArray()
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Chart(
                                chart = columnChart(),
                                model = chartEntryModel,
                                startAxis = rememberStartAxis(
                                    label = textComponent(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ),
                                bottomAxis = rememberBottomAxis(
                                    label = textComponent(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    valueFormatter = { value, _ ->
                                        state.last7Days.getOrNull(value.toInt())?.label ?: ""
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (state.subjectBreakdown.isNotEmpty()) {
                    Text(
                        text = "Time by Subject",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val segments = state.subjectBreakdown.map { data ->
                                DonutSegment(
                                    label = data.name,
                                    value = data.totalSeconds.toFloat(),
                                    color = try {
                                        Color(android.graphics.Color.parseColor(data.colorHex))
                                    } catch (e: Exception) {
                                        Color.Gray
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DonutChart(
                                    segments = segments,
                                    modifier = Modifier.size(140.dp),
                                    strokeWidth = 22.dp,
                                    totalLabel = "${state.allTimeMinutes / 60}h"
                                )
                                Spacer(modifier = Modifier.width(20.dp))
                                DonutLegend(
                                    segments = segments,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
