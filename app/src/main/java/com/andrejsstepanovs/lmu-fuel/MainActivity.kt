package com.andrejsstepanovs.lmufuel

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

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
                // Determine Surface color explicitly to ensure dark mode look
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

    // Mutable State for Inputs
    // Initializing from Prefs immediately (Requirements 3.2 State Persistence)
    var durationInput by remember { mutableStateOf(prefs.getString(KEY_DURATION, "") ?: "") }
    var lapTimeInput by remember { mutableStateOf(prefs.getString(KEY_LAP_TIME, "") ?: "") }
    var fuelConsInput by remember { mutableStateOf(prefs.getString(KEY_FUEL_CONS, "") ?: "") }
    var veConsInput by remember { mutableStateOf(prefs.getString(KEY_VE_CONS, "") ?: "") }

    // Save to Prefs whenever inputs change (Auto-save)
    LaunchedEffect(durationInput, lapTimeInput, fuelConsInput, veConsInput) {
        prefs.edit().apply {
            putString(KEY_DURATION, durationInput)
            putString(KEY_LAP_TIME, lapTimeInput)
            putString(KEY_FUEL_CONS, fuelConsInput)
            putString(KEY_VE_CONS, veConsInput)
            apply()
        }
    }

    // --- Calculation Logic (Requirements 2.2) ---
    val durationMin = durationInput.toDoubleOrNull() ?: 0.0
    
    // Parse Lap Time (Requirements 3.2 Lap Time Parsing)
    val lapTimeSec: Double = try {
        if (lapTimeInput.contains(":")) {
            val parts = lapTimeInput.split(":")
            if (parts.size == 2) {
                val min = parts[0].toDoubleOrNull() ?: 0.0
                val sec = parts[1].toDoubleOrNull() ?: 0.0
                (min * 60) + sec
            } else 0.0
        } else {
            lapTimeInput.toDoubleOrNull() ?: 0.0
        }
    } catch (e: Exception) { 0.0 }

    val fuelPerLap = fuelConsInput.toDoubleOrNull() ?: 0.0
    val vePerLap = veConsInput.toDoubleOrNull() ?: 0.0

    // Logic A: Total Laps (Round Up, No +1 buffer)
    val totalLaps: Int = if (lapTimeSec > 0 && durationMin > 0) {
        ceil((durationMin * 60) / lapTimeSec).toInt()
    } else {
        0
    }

    // Logic B: Fuel Ratio (Setup Value)
    // Formula: Fuel Consumption / VE Consumption (Standard LMU Logic)
    val fuelRatioStr: String = if (vePerLap > 0) {
        "%.3f".format(fuelPerLap / vePerLap)
    } else {
        "---"
    }

    // Logic C: Totals
    val totalFuelNeeded = totalLaps * fuelPerLap
    val totalVeNeeded = totalLaps * vePerLap

    // Logic 2.4: Pit Stop Warning
    val isPitStopRequired = totalVeNeeded > 100.0

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
            InputRow(
                label = "Duration (min)",
                value = durationInput,
                onValueChange = { durationInput = it },
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                focusManager = focusManager
            )
            InputRow(
                label = "Lap Time (mm:ss or s)",
                value = lapTimeInput,
                onValueChange = { lapTimeInput = it }, // Allow text for colon
                keyboardType = KeyboardType.Text, 
                imeAction = ImeAction.Next,
                focusManager = focusManager
            )
            InputRow(
                label = "Fuel Cons. (L/lap)",
                value = fuelConsInput,
                onValueChange = { fuelConsInput = it },
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
                focusManager = focusManager
            )
            InputRow(
                label = "VE Cons. (%/lap)",
                value = veConsInput,
                onValueChange = { veConsInput = it },
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done, // Close keyboard on last item
                focusManager = focusManager
            )
        }

        // Reset Button
        Button(
            onClick = {
                durationInput = ""
                lapTimeInput = ""
                fuelConsInput = ""
                veConsInput = ""
            },
            modifier = Modifier.align(Alignment.End),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Reset Inputs", color = Color.Black)
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

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
                // Left Column: VE
                Column(horizontalAlignment = Alignment.Start) {
                    Text(text = "TOTAL VE %", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = if (vePerLap > 0) "%.1f%%".format(totalVeNeeded) else "---",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        // Color code Orange/Red if > 100%
                        color = if (isPitStopRequired) Color(0xFFFF5252) else Color(0xFF4CAF50)
                    )
                }

                // Right Column: Fuel & Laps
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "TOTAL FUEL", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = if (fuelPerLap > 0) "%.1f L".format(totalFuelNeeded) else "---",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "LAPS REQUIRED", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = "$totalLaps",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Warning Label (Non-intrusive)
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
fun InputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() }
        )
    )
}