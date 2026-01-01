package com.andrejsstepanovs.lmufuel

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Constraint: Keep screen ON
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Optimization: Initialize SharedPreferences and read values BEFORE UI composition
        // This prevents disk reads from blocking the first frame draw (fixing skipped frames)
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val initialDuration = prefs.getString(KEY_DURATION, "") ?: ""
        val initialLapTime = prefs.getString(KEY_LAP_TIME, "") ?: ""
        val initialFuel = prefs.getString(KEY_FUEL_CONS, "") ?: ""
        val initialVe = prefs.getString(KEY_VE_CONS, "") ?: ""

        setContent {
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
                        onSave = { duration, lap, fuel, ve ->
                            prefs.edit().apply {
                                putString(KEY_DURATION, duration)
                                putString(KEY_LAP_TIME, lap)
                                putString(KEY_FUEL_CONS, fuel)
                                putString(KEY_VE_CONS, ve)
                                apply()
                            }
                        }
                    )
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
    onSave: (String, String, String, String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    // Helper to create initial TextFieldValue from String
    fun tfv(text: String) = TextFieldValue(text = text)

    // Mutable State for Inputs initialized with hoisted values
    var durationInput by remember { mutableStateOf(tfv(initDuration)) }
    var lapTimeInput by remember { mutableStateOf(tfv(initLapTime)) }
    var fuelConsInput by remember { mutableStateOf(tfv(initFuel)) }
    var veConsInput by remember { mutableStateOf(tfv(initVe)) }

    // Optimization: Debounced Save
    // We wait for 500ms of inactivity before hitting SharedPreferences.
    // This dramatically reduces main thread work during rapid typing/stepper usage.
    LaunchedEffect(durationInput.text, lapTimeInput.text, fuelConsInput.text, veConsInput.text) {
        delay(500)
        onSave(durationInput.text, lapTimeInput.text, fuelConsInput.text, veConsInput.text)
    }

    // --- Calculation Logic (Runs immediately, not debounced) ---
    val durationMin = durationInput.text.toDoubleOrNull() ?: 0.0

    // Parse Lap Time (Simplified to Raw Seconds)
    val lapTimeSec = lapTimeInput.text.toDoubleOrNull() ?: 0.0

    val fuelPerLap = fuelConsInput.text.toDoubleOrNull() ?: 0.0
    val vePerLap = veConsInput.text.toDoubleOrNull() ?: 0.0

    // Logic A: Total Laps
    val totalLaps: Int = if (lapTimeSec > 0 && durationMin > 0) {
        ceil((durationMin * 60) / lapTimeSec).toInt()
    } else {
        0
    }

    // Logic B: Fuel Ratio
    val fuelRatioStr: String = if (vePerLap > 0) {
        // Updated to 2 decimal places per requirement
        "%.2f".format(Locale.US, fuelPerLap / vePerLap)
    } else {
        "---"
    }

    // Logic C: Totals
    val totalFuelNeeded = totalLaps * fuelPerLap
    val totalVeNeeded = totalLaps * vePerLap
    val totalTimeSeconds = totalLaps * lapTimeSec

    // Logic 2.4: Pit Stop Warning
    val isPitStopRequired = totalVeNeeded > 100.0

    // --- Helpers for Steppers ---
    fun updateText(newValue: String, updateState: (TextFieldValue) -> Unit) {
        updateState(TextFieldValue(text = newValue, selection = TextRange(newValue.length)))
    }

    // --- UI Layout ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Clear focus and hide keyboard when tapping outside input fields
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header (Refined: Small, Grey, Right-Aligned)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp), // Reduced bottom padding
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "LMU Fuel & VE v0.1.1",
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Normal
            )
        }

        // Input Section
        InputCard {
            // Duration Stepper (Steps of 1 min)
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

            // Lap Time Stepper (Steps of 1 sec)
            // Generate dynamic label for mm:ss with BOLD formatting
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
                keyboardType = KeyboardType.Decimal, // Decimal for dot support
                imeAction = ImeAction.Next,
                focusManager = focusManager
            )

            // Fuel Stepper (Steps of 0.1, Snap to Grid)
            StepperInputRow(
                label = { Text("Fuel Cons. (L/lap)", fontSize = 12.sp) },
                value = fuelConsInput,
                onValueChange = { fuelConsInput = it },
                onIncrement = {
                    val current = fuelConsInput.text.toDoubleOrNull() ?: 0.0
                    // Snap logic: (floor(23.5 + 0.05) + 1) / 10 -> 2.4
                    val next = (floor(current * 10 + 0.05) + 1) / 10.0
                    updateText("%.1f".format(Locale.US, next), { fuelConsInput = it })
                },
                onDecrement = {
                    val current = fuelConsInput.text.toDoubleOrNull() ?: 0.0
                    // Snap logic: (ceil(23.5 - 0.05) - 1) / 10 -> 2.3
                    val prev = (ceil(current * 10 - 0.05) - 1) / 10.0
                    updateText("%.1f".format(Locale.US, prev.coerceAtLeast(0.0)), { fuelConsInput = it })
                },
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
                focusManager = focusManager
            )

            // VE Stepper (Steps of 0.1, Snap to Grid)
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

        // Divider with reduced top padding (1/2 size)
        Divider(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

        // Output Section (High Visibility)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fuel Ratio (Largest)
            Text(text = "FUEL RATIO", fontSize = 14.sp, color = Color.Gray)
            Text(
                text = fuelRatioStr,
                fontSize = 60.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Grid for other stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Column: VE & Total Time
                Column(horizontalAlignment = Alignment.Start) {
                    Text(text = "TOTAL VE %", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = if (vePerLap > 0) "%.1f%%".format(Locale.US, totalVeNeeded) else "---",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPitStopRequired) Color(0xFFFF5252) else Color(0xFF4CAF50)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "TOTAL TIME", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = formatSecondsToTime(totalTimeSeconds),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Right Column: Fuel & Laps
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "TOTAL FUEL", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = if (fuelPerLap > 0) "%.1f L".format(Locale.US, totalFuelNeeded) else "---",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "LAPS REQUIRED", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = "$totalLaps",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Warning Label
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
        // Decrement Button
        RepeatingIconButton(
            onClick = onDecrement
        ) {
            // Visual minus using Box to ensure perfect vertical centering matching the Add Icon
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

        // Text Field
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            ),
            singleLine = true,
            // Constrain height to make padding tighter (since contentPadding param is unavailable)
            modifier = Modifier
                .weight(1f)
                //.height(64.dp)
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

        // Increment Button
        RepeatingIconButton(
            onClick = onIncrement
        ) {
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

    // Use Surface directly instead of FilledIconButton to avoid internal click conflict
    // Matching FilledIconButton styles manually
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = contentColorFor(containerColor)

    Surface(
        modifier = modifier
            .size(40.dp) // Standard icon button size
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val job = scope.launch {
                            currentOnClick() // Fire once immediately
                            delay(500) // Initial delay before repeating
                            while (isActive) {
                                currentOnClick()
                                delay(100) // Repeat interval
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