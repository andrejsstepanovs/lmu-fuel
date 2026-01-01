package com.andrejsstepanovs.lmufuel

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Constraint: Keep screen ON
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
                    CalculatorScreen()
                }
            }
        }
    }
}

// Keys for SharedPreferences
const val PREF_NAME = "LMUFuelPrefs"
const val KEY_DURATION = "duration"
const val KEY_LAP_TIME = "lap_time"
const val KEY_FUEL_CONS = "fuel_cons"
const val KEY_VE_CONS = "ve_cons"

@Composable
fun CalculatorScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val prefs: SharedPreferences = remember {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // Helper to create initial TextFieldValue from String
    fun initialTfv(key: String) = TextFieldValue(text = prefs.getString(key, "") ?: "")

    // Mutable State for Inputs (using TextFieldValue to control selection)
    var durationInput by remember { mutableStateOf(initialTfv(KEY_DURATION)) }
    var lapTimeInput by remember { mutableStateOf(initialTfv(KEY_LAP_TIME)) }
    var fuelConsInput by remember { mutableStateOf(initialTfv(KEY_FUEL_CONS)) }
    var veConsInput by remember { mutableStateOf(initialTfv(KEY_VE_CONS)) }

    // Save to Prefs whenever text changes
    LaunchedEffect(durationInput.text, lapTimeInput.text, fuelConsInput.text, veConsInput.text) {
        prefs.edit().apply {
            putString(KEY_DURATION, durationInput.text)
            putString(KEY_LAP_TIME, lapTimeInput.text)
            putString(KEY_FUEL_CONS, fuelConsInput.text)
            putString(KEY_VE_CONS, veConsInput.text)
            apply()
        }
    }

    // --- Calculation Logic ---
    val durationMin = durationInput.text.toDoubleOrNull() ?: 0.0
    
    // Parse Lap Time
    val isLapTimeFormatted = lapTimeInput.text.contains(":")
    val lapTimeSec: Double = try {
        if (isLapTimeFormatted) {
            val parts = lapTimeInput.text.split(":")
            if (parts.size == 2) {
                val min = parts[0].toDoubleOrNull() ?: 0.0
                val sec = parts[1].toDoubleOrNull() ?: 0.0
                (min * 60) + sec
            } else 0.0
        } else {
            lapTimeInput.text.toDoubleOrNull() ?: 0.0
        }
    } catch (e: Exception) { 0.0 }

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
        "%.3f".format(Locale.US, fuelPerLap / vePerLap)
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
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "LMU Fuel & VE",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Input Section
        InputCard {
            // Duration Stepper (Steps of 1 min)
            StepperInputRow(
                label = "Duration (min)",
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
            StepperInputRow(
                label = "Lap Time (mm:ss or s)",
                value = lapTimeInput,
                onValueChange = { lapTimeInput = it },
                onIncrement = {
                    updateText(adjustLapTime(lapTimeInput.text, 1.0), { lapTimeInput = it })
                },
                onDecrement = {
                    updateText(adjustLapTime(lapTimeInput.text, -1.0), { lapTimeInput = it })
                },
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                focusManager = focusManager
            )

            // Fuel Stepper (Steps of 0.1)
            StepperInputRow(
                label = "Fuel Cons. (L/lap)",
                value = fuelConsInput,
                onValueChange = { fuelConsInput = it },
                onIncrement = {
                    val current = fuelConsInput.text.toDoubleOrNull() ?: 0.0
                    updateText("%.1f".format(Locale.US, current + 0.1), { fuelConsInput = it })
                },
                onDecrement = {
                    val current = fuelConsInput.text.toDoubleOrNull() ?: 0.0
                    updateText("%.1f".format(Locale.US, (current - 0.1).coerceAtLeast(0.0)), { fuelConsInput = it })
                },
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
                focusManager = focusManager
            )

            // VE Stepper (Steps of 0.1)
            StepperInputRow(
                label = "VE Cons. (%/lap)",
                value = veConsInput,
                onValueChange = { veConsInput = it },
                onIncrement = {
                    val current = veConsInput.text.toDoubleOrNull() ?: 0.0
                    updateText("%.1f".format(Locale.US, current + 0.1), { veConsInput = it })
                },
                onDecrement = {
                    val current = veConsInput.text.toDoubleOrNull() ?: 0.0
                    updateText("%.1f".format(Locale.US, (current - 0.1).coerceAtLeast(0.0)), { veConsInput = it })
                },
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
                focusManager = focusManager
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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

// Logic to adjust lap time (+/- seconds) while preserving format
fun adjustLapTime(current: String, deltaSeconds: Double): String {
    val isFormatted = current.contains(":")
    var totalSeconds = 0.0
    
    if (isFormatted) {
        val parts = current.split(":")
        if (parts.size == 2) {
            val min = parts[0].toDoubleOrNull() ?: 0.0
            val sec = parts[1].toDoubleOrNull() ?: 0.0
            totalSeconds = (min * 60) + sec
        }
    } else {
        totalSeconds = current.toDoubleOrNull() ?: 0.0
    }
    
    val newTotal = (totalSeconds + deltaSeconds).coerceAtLeast(0.0)
    
    return if (isFormatted) {
        val min = (newTotal / 60).toInt()
        val sec = newTotal % 60
        // Use Locale.US to ensure dot separator
        "%d:%04.1f".format(Locale.US, min, sec) 
    } else {
        "%.1f".format(Locale.US, newTotal)
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

@Composable
fun InputCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun StepperInputRow(
    label: String,
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
        FilledIconButton(
            onClick = onDecrement,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Using system default icon, if custom icons aren't available could use text "-"
            // Using Material Icons (Requires compose material dependency for extended icons, using basic here)
            // If Icons.Default.Remove isn't available, we use a manual shape or Text
            // Note: Icons.Default.Remove might need 'androidx.compose.material:material-icons-extended'
            // We'll use a simple Text "-" to be safe with minimal dependencies or a ArrowLeft/Right as proxy if needed,
            // but usually Icons.Default.Add is core. Remove is sometimes core.
            // Let's use Text for safety in this minimal setup or KeyboardArrowLeft as "Decrease" visual metaphor if "-" fails.
            // Actually, let's use a Text("-") to be universally safe.
            Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        // Text Field
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontSize = 12.sp) }, // Smaller label to fit
            singleLine = true,
            modifier = Modifier
                .weight(1f)
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
        FilledIconButton(
            onClick = onIncrement,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase")
        }
    }
}
