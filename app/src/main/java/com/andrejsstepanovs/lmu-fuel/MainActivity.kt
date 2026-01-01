package com.andrejsstepanovs.lmufuel

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

// Keys for SharedPreferences
const val PREF_NAME = "LMUFuelPrefs"
const val KEY_DURATION = "duration"
const val KEY_LAP_TIME = "lap_time"
const val KEY_FUEL_CONS = "fuel_cons"
const val KEY_VE_CONS = "ve_cons"
const val KEY_LAPS_ADJ = "laps_adj" // New key for lap adjustment

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Constraint: Keep screen ON
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Optimization: Initialize SharedPreferences and read values BEFORE UI composition
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val initialDuration = prefs.getString(KEY_DURATION, "") ?: ""
        val initialLapTime = prefs.getString(KEY_LAP_TIME, "") ?: ""
        val initialFuel = prefs.getString(KEY_FUEL_CONS, "") ?: ""
        val initialVe = prefs.getString(KEY_VE_CONS, "") ?: ""
        // Default adjustment is 0.0
        val initialLapsAdj = prefs.getFloat(KEY_LAPS_ADJ, 0.0f).toDouble()

        setContent {
            // FIX: Force fontScale to 1.0f. 
            // This treats all 'sp' as 'dp', preventing system font size settings 
            // from breaking the dashboard layout (The "Dashboard Approach").
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = LocalDensity.current.density,
                    fontScale = 1.0f
                )
            ) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Color(0xFFBB86FC),
                        secondary = Color(0xFF03DAC6),
                        background = Color(0xFF121212),
                        surface = Color(0xFF1E1E1E),
                        onSurface = Color.White
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        CalculatorScreen(
                            initDuration = initialDuration,
                            initLapTime = initialLapTime,
                            initFuel = initialFuel,
                            initVe = initialVe,
                            initLapsAdj = initialLapsAdj,
                            onSave = { duration, lap, fuel, ve, adj ->
                                prefs.edit().apply {
                                    putString(KEY_DURATION, duration)
                                    putString(KEY_LAP_TIME, lap)
                                    putString(KEY_FUEL_CONS, fuel)
                                    putString(KEY_VE_CONS, ve)
                                    putFloat(KEY_LAPS_ADJ, adj.toFloat())
                                    apply()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorScreen(
    initDuration: String,
    initLapTime: String,
    initFuel: String,
    initVe: String,
    initLapsAdj: Double,
    onSave: (String, String, String, String, Double) -> Unit
) {
    val focusManager = LocalFocusManager.current

    // Helper to create initial TextFieldValue from String
    fun tfv(text: String) = TextFieldValue(text = text)

    // Mutable State for Inputs
    var durationInput by remember { mutableStateOf(tfv(initDuration)) }
    var lapTimeInput by remember { mutableStateOf(tfv(initLapTime)) }
    var fuelConsInput by remember { mutableStateOf(tfv(initFuel)) }
    var veConsInput by remember { mutableStateOf(tfv(initVe)) }
    
    // New State for Laps Adjustment
    var lapsAdjustment by remember { mutableDoubleStateOf(initLapsAdj) }

    // Optimization: Debounced Save
    LaunchedEffect(durationInput.text, lapTimeInput.text, fuelConsInput.text, veConsInput.text, lapsAdjustment) {
        delay(500)
        onSave(durationInput.text, lapTimeInput.text, fuelConsInput.text, veConsInput.text, lapsAdjustment)
    }

    // --- Calculation Logic ---
    val durationMin = durationInput.text.toDoubleOrNull() ?: 0.0
    val lapTimeSec = lapTimeInput.text.toDoubleOrNull() ?: 0.0
    val fuelPerLap = fuelConsInput.text.toDoubleOrNull() ?: 0.0
    val vePerLap = veConsInput.text.toDoubleOrNull() ?: 0.0

    // Logic A: Base Laps (The integer calculation from duration)
    val baseLaps: Int = if (lapTimeSec > 0 && durationMin > 0) {
        ceil((durationMin * 60) / lapTimeSec).toInt()
    } else {
        0
    }

    // Logic B: Final Laps (Base + User Adjustment)
    // We assume you can't have negative total laps, but negative adjustment is fine
    val finalLaps = (baseLaps + lapsAdjustment).coerceAtLeast(0.0)

    // Logic C: Fuel Ratio (Setup parameter, technically independent of lap count)
    val fuelRatioStr: String = if (vePerLap > 0) {
        "%.2f".format(Locale.US, fuelPerLap / vePerLap)
    } else {
        "---"
    }

    // Logic D: Totals (Now derived from finalLaps)
    val totalFuelNeeded = finalLaps * fuelPerLap
    val totalVeNeeded = finalLaps * vePerLap
    val totalTimeSeconds = finalLaps * lapTimeSec

    // Logic E: Pit Stop Warning
    val isPitStopRequired = totalVeNeeded > 100.0

    // --- Helpers for Input Steppers ---
    fun updateText(newValue: String, updateState: (TextFieldValue) -> Unit) {
        updateState(TextFieldValue(text = newValue, selection = TextRange(newValue.length)))
    }

    // --- UI Layout ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "LMU Fuel & VE v1.1",
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Normal
            )
        }

        // Input Section
        InputCard {
            // Duration
            StepperInputRow(
                label = { Text("Duration (min)", fontSize = 12.sp) },
                value = durationInput,
                onValueChange = { durationInput = it },
                onIncrement = {
                    val current = durationInput.text.toDoubleOrNull() ?: 0.0
                    updateText("%.0f".format(Locale.US, current + 1), { durationInput = it })
                },
                onDecrement = {
                    val current = durationInput.text.toDoubleOrNull() ?: 0.0
                    updateText("%.0f".format(Locale.US, (current - 1).coerceAtLeast(0.0)), { durationInput = it })
                },
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                focusManager = focusManager
            )
            
            // Lap Time
            val lapTimeLabel = buildAnnotatedString {
                append("Lap Time (sec)")
                if (lapTimeSec > 0) {
                    append(" • ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)) {
                        append(formatLapTimeForLabel(lapTimeSec))
                    }
                }
            }
            StepperInputRow(
                label = { Text(text = lapTimeLabel, fontSize = 12.sp) },
                value = lapTimeInput,
                onValueChange = { lapTimeInput = it },
                onIncrement = {
                    val current = lapTimeInput.text.toDoubleOrNull() ?: 0.0
                    updateText("%.1f".format(Locale.US, current + 1.0), { lapTimeInput = it })
                },
                onDecrement = {
                    val current = lapTimeInput.text.toDoubleOrNull() ?: 0.0
                    updateText("%.1f".format(Locale.US, (current - 1.0).coerceAtLeast(0.0)), { lapTimeInput = it })
                },
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
                focusManager = focusManager
            )

            // Fuel
            StepperInputRow(
                label = { Text("Fuel Cons. (L/lap)", fontSize = 12.sp) },
                value = fuelConsInput,
                onValueChange = { fuelConsInput = it },
                onIncrement = {
                    val current = fuelConsInput.text.toDoubleOrNull() ?: 0.0
                    val next = (floor(current * 10 + 0.05) + 1) / 10.0
                    updateText("%.1f".format(Locale.US, next), { fuelConsInput = it })
                },
                onDecrement = {
                    val current = fuelConsInput.text.toDoubleOrNull() ?: 0.0
                    val prev = (ceil(current * 10 - 0.05) - 1) / 10.0
                    updateText("%.1f".format(Locale.US, prev.coerceAtLeast(0.0)), { fuelConsInput = it })
                },
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
                focusManager = focusManager
            )

            // VE
            StepperInputRow(
                label = { Text("VE Cons. (%/lap)", fontSize = 12.sp) },
                value = veConsInput,
                onValueChange = { veConsInput = it },
                onIncrement = {
                    val current = veConsInput.text.toDoubleOrNull() ?: 0.0
                    val next = (floor(current * 10 + 0.05) + 1) / 10.0
                    updateText("%.1f".format(Locale.US, next), { veConsInput = it })
                },
                onDecrement = {
                    val current = veConsInput.text.toDoubleOrNull() ?: 0.0
                    val prev = (ceil(current * 10 - 0.05) - 1) / 10.0
                    updateText("%.1f".format(Locale.US, prev.coerceAtLeast(0.0)), { veConsInput = it })
                },
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
                focusManager = focusManager
            )
        }

        Divider(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

        // Output Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "FUEL RATIO", fontSize = 14.sp, color = Color.Gray)
            Text(
                text = fuelRatioStr,
                fontSize = 60.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Column
                Column(horizontalAlignment = Alignment.Start) {
                    Text(text = "NEED VIRTUAL ENERGY", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = if (vePerLap > 0) "%.1f%%".format(Locale.US, totalVeNeeded) else "---",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPitStopRequired) Color(0xFFFF5252) else Color(0xFF4CAF50)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "DRIVE TIME", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = formatSecondsToTime(totalTimeSeconds),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Right Column: Fuel & Laps with Adjustment
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "NEED FUEL", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = if (fuelPerLap > 0) "%.1f L".format(Locale.US, totalFuelNeeded) else "---",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "LAPS", fontSize = 12.sp, color = Color.Gray)
                    
                    // Laps Display with Adjustment
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$baseLaps",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (lapsAdjustment != 0.0) {
                            Text(
                                text = if (lapsAdjustment > 0) " + $lapsAdjustment" else " - ${kotlin.math.abs(lapsAdjustment)}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (lapsAdjustment < 0) Color(0xFFFF5252) else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp) // Align slightly with base number
                            )
                        } else {
                            // Empty spacer to reserve height if needed, or just show nothing
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Laps Adjustment Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                         RepeatingIconButton(
                            onClick = { lapsAdjustment -= 0.5 },
                            modifier = Modifier.size(32.dp) // Smaller than main input buttons
                        ) {
                             // Minus visual
                             Box(
                                 modifier = Modifier.size(16.dp),
                                 contentAlignment = Alignment.Center
                             ) {
                                 Box(
                                     modifier = Modifier
                                         .width(10.dp)
                                         .height(2.dp)
                                         .background(LocalContentColor.current)
                                 )
                             }
                        }

                        RepeatingIconButton(
                            onClick = { lapsAdjustment += 0.5 },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Laps", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isPitStopRequired) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFCF6679)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "PIT STOP REQUIRED",
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.CenterHorizontally),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// --- Utils & Components ---

fun formatSecondsToTime(totalSeconds: Double): String {
    if (totalSeconds <= 0) return "---"
    val h = (totalSeconds / 3600).toInt()
    val m = ((totalSeconds % 3600) / 60).toInt()
    val s = (totalSeconds % 60).toInt()
    return if (h > 0) {
        "%d:%02d:%02d".format(Locale.US, h, m, s)
    } else {
        "%02d:%02d".format(Locale.US, m, s)
    }
}

fun formatLapTimeForLabel(seconds: Double): String {
    val m = (seconds / 60).toInt()
    val s = seconds % 60
    return "%d:%04.1f".format(Locale.US, m, s)
}

@Composable
fun InputCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
fun StepperInputRow(
    label: @Composable () -> Unit,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        RepeatingIconButton(onClick = onDecrement) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .background(LocalContentColor.current)
                )
            }
        }

        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                // Fix: Allow comma as decimal separator by replacing it with dot immediately
                val sanitized = newValue.copy(text = newValue.text.replace(',', '.'))
                onValueChange(sanitized)
            },
            label = label,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            ),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        val text = value.text
                        if (text.isNotEmpty()) {
                            onValueChange(value.copy(selection = TextRange(0, text.length)))
                        }
                    }
                },
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { focusManager.clearFocus() }
            )
        )

        RepeatingIconButton(onClick = onIncrement) {
            Icon(Icons.Default.Add, contentDescription = "Increase")
        }
    }
}

@Composable
fun RepeatingIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = contentColorFor(containerColor)

    Surface(
        modifier = modifier
            .size(40.dp) // Default size, overridden by modifier if provided
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val job = scope.launch {
                            currentOnClick()
                            delay(500)
                            while (isActive) {
                                currentOnClick()
                                delay(100)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                    }
                )
            },
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}