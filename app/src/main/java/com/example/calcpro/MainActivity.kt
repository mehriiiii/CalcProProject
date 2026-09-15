package com.example.calcpro

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class HistoryItem(
    val id: Long = System.currentTimeMillis(),
    val expression: String,
    val result: String
)

object CalculatorEngine {
    private val numberFormat = DecimalFormat("#,###.############")

    fun isOperator(char: String): Boolean = char in listOf("+", "-", "*", "/")

    fun formatOperator(op: String): String = when (op) {
        "*" -> "×"
        "/" -> "÷"
        "-" -> "−"
        else -> op
    }

    fun formatNumber(numStr: String): String {
        if (numStr.isEmpty() || numStr == "Error") return numStr
        val parts = numStr.split(".")
        return try {
            val doubleVal = parts[0].replace(",", "").toDoubleOrNull() ?: return numStr
            val formattedInt = numberFormat.format(doubleVal)
            if (parts.size > 1) "$formattedInt.${parts[1]}" else formattedInt
        } catch (e: Exception) {
            numStr
        }
    }

    /**
     * Evaluates tokens with standard mathematical precedence (*, / before +, -)
     */
    fun evaluateTokens(tokens: List<String>): Double {
        if (tokens.isEmpty()) return 0.0

        val working = tokens.toMutableList()
        if (working.isNotEmpty() && isOperator(working.last())) {
            working.removeAt(working.size - 1)
        }
        if (working.isEmpty()) return 0.0

        // Phase 1: Multiplication and Division
        var i = 0
        while (i < working.size) {
            val token = working[i]
            if (token == "*" || token == "/") {
                val prev = working.getOrNull(i - 1)?.toDoubleOrNull() ?: 0.0
                val next = working.getOrNull(i + 1)?.toDoubleOrNull() ?: 0.0

                if (token == "/" && next == 0.0) {
                    throw ArithmeticException("Division by Zero")
                }

                val res = if (token == "*") prev * next else prev / next
                working.removeAt(i + 1)
                working.removeAt(i)
                working[i - 1] = res.toString()
                i = maxOf(0, i - 1)
            } else {
                i++
            }
        }

        // Phase 2: Addition and Subtraction
        i = 0
        while (i < working.size) {
            val token = working[i]
            if (token == "+" || token == "-") {
                val prev = working.getOrNull(i - 1)?.toDoubleOrNull() ?: 0.0
                val next = working.getOrNull(i + 1)?.toDoubleOrNull() ?: 0.0

                val res = if (token == "+") prev + next else prev - next
                working.removeAt(i + 1)
                working.removeAt(i)
                working[i - 1] = res.toString()
                i = maxOf(0, i - 1)
            } else {
                i++
            }
        }

        val finalVal = working.firstOrNull()?.toDoubleOrNull() ?: 0.0
        if (finalVal.isNaN() || finalVal.isInfinite()) {
            throw ArithmeticException("Invalid Expression")
        }

        // Clean precision glitches
        return Math.round(finalVal * 1e12) / 1e12
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalcProTheme {
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

@Composable
fun CalcProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B),
            primary = Color(0xFF3B82F6),
            secondary = Color(0xFF64748B),
            tertiary = Color(0xFFF59E0B),
            error = Color(0xFFEF4444),
            onBackground = Color(0xFFF8FAFC),
            onSurface = Color(0xFFF1F5F9)
        )
    } else {
        lightColorScheme(
            background = Color(0xFFF1F5F9),
            surface = Color(0xFFFFFFFF),
            primary = Color(0xFF2563EB),
            secondary = Color(0xFF94A3B8),
            tertiary = Color(0xFFD97706),
            error = Color(0xFFDC2626),
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF1E293B)
        )
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen() {
    var currentInput by remember { mutableStateOf("0") }
    var expressionTokens by remember { mutableStateOf(listOf<String>()) }
    var isEvaluated by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(listOf<HistoryItem>()) }
    var showHistorySheet by remember { mutableStateOf(false) }

    val view = LocalView.current

    fun triggerHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun handleNumber(num: String) {
        triggerHaptic()
        errorMessage = null
        if (isEvaluated) {
            currentInput = num
            expressionTokens = emptyList()
            isEvaluated = false
        } else {
            if (currentInput == "0") {
                currentInput = num
            } else if (currentInput == "-0") {
                currentInput = "-$num"
            } else if (currentInput.replace("-", "").length < 15) {
                currentInput += num
            }
        }
    }

    fun handleOperator(op: String) {
        triggerHaptic()
        errorMessage = null
        if (isEvaluated) {
            expressionTokens = listOf(currentInput, op)
            currentInput = "0"
            isEvaluated = false
        } else {
            if (currentInput.isNotEmpty() && currentInput != "-") {
                expressionTokens = expressionTokens + currentInput + op
                currentInput = "0"
            } else if (expressionTokens.isNotEmpty()) {
                val mutable = expressionTokens.toMutableList()
                if (CalculatorEngine.isOperator(mutable.last())) {
                    mutable[mutable.size - 1] = op
                    expressionTokens = mutable
                }
            }
        }
    }

    fun handleCalculate() {
        triggerHaptic()
        if (expressionTokens.isEmpty()) return

        val fullTokens = expressionTokens.toMutableList()
        if (currentInput.isNotEmpty()) {
            fullTokens.add(currentInput)
        } else if (fullTokens.isNotEmpty() && CalculatorEngine.isOperator(fullTokens.last())) {
            fullTokens.removeAt(fullTokens.size - 1)
        }

        try {
            val result = CalculatorEngine.evaluateTokens(fullTokens)
            val resultStr = if (result % 1.0 == 0.0) result.toLong().toString() else result.toString()

            val exprFormatted = fullTokens.joinToString("") { token ->
                if (CalculatorEngine.isOperator(token)) " ${CalculatorEngine.formatOperator(token)} "
                else CalculatorEngine.formatNumber(token)
            }

            history = listOf(HistoryItem(expression = exprFormatted, result = resultStr)) + history.take(19)
            expressionTokens = emptyList()
            currentInput = resultStr
            isEvaluated = true
        } catch (e: Exception) {
            errorMessage = e.message ?: "Error"
        }
    }

    fun handleClear() {
        triggerHaptic()
        currentInput = "0"
        expressionTokens = emptyList()
        isEvaluated = false
        errorMessage = null
    }

    fun handleBackspace() {
        triggerHaptic()
        if (isEvaluated) {
            handleClear()
            return
        }
        if (currentInput.length > 1) {
            currentInput = currentInput.dropLast(1)
            if (currentInput == "-") currentInput = "0"
        } else {
            currentInput = "0"
        }
    }

    fun handleToggleSign() {
        triggerHaptic()
        if (currentInput == "0") return
        currentInput = if (currentInput.startsWith("-")) {
            currentInput.drop(1)
        } else {
            "-$currentInput"
        }
    }

    fun handleTrig(function: String) {
        triggerHaptic()
        errorMessage = null
        val value = currentInput.toDoubleOrNull() ?: run {
            errorMessage = "Invalid Number"
            return
        }

        val radians = Math.toRadians(value)
        val result = when (function) {
            "sin" -> sin(radians)
            "cos" -> cos(radians)
            "tan" -> tan(radians).takeUnless { it.isNaN() || it.isInfinite() }
            "cot" -> {
                val sine = sin(radians)
                if (abs(sine) < 1e-12) null else cos(radians) / sine
            }
            else -> null
        }

        if (result == null || result.isNaN() || result.isInfinite()) {
            errorMessage = "Invalid Result"
            return
        }

        val cleaned = if (abs(result) < 1e-12) 0.0 else result
        currentInput = if (cleaned % 1.0 == 0.0) {
            cleaned.toLong().toString()
        } else {
            cleaned.toString()
        }
        expressionTokens = emptyList()
        isEvaluated = true
    }

    fun handlePercent() {
        triggerHaptic()
        try {
            val valDouble = currentInput.toDoubleOrNull() ?: return
            if (expressionTokens.size >= 2) {
                val prevNum = expressionTokens[0].toDoubleOrNull()
                if (prevNum != null) {
                    currentInput = ((prevNum * valDouble) / 100.0).toString()
                    return
                }
            }
            currentInput = (valDouble / 100.0).toString()
        } catch (e: Exception) {
            errorMessage = "Invalid Percent"
        }
    }

    // Live Result Preview calculation
    val livePreview: String? = remember(expressionTokens, currentInput, isEvaluated) {
        if (expressionTokens.size >= 2 && !isEvaluated) {
            try {
                val tempTokens = expressionTokens + currentInput
                val res = CalculatorEngine.evaluateTokens(tempTokens)
                val resStr = if (res % 1.0 == 0.0) res.toLong().toString() else res.toString()
                "= ${CalculatorEngine.formatNumber(resStr)}"
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBarContainer(
                onOpenHistory = { showHistorySheet = true },
                hasHistory = history.isNotEmpty()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            DisplaySection(
                expressionTokens = expressionTokens,
                currentInput = currentInput,
                livePreview = livePreview,
                errorMessage = errorMessage
            )

            KeypadSection(
                onNumber = ::handleNumber,
                onOperator = ::handleOperator,
                onCalculate = ::handleCalculate,
                onClear = ::handleClear,
                onBackspace = ::handleBackspace,
                onToggleSign = ::handleToggleSign,
                onPercent = ::handlePercent,
                onDecimal = { handleNumber(".") },
                onTrig = ::handleTrig
            )
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            HistorySheetContent(
                historyList = history,
                onClearHistory = { history = emptyList() },
                onSelectResult = { res ->
                    currentInput = res
                    expressionTokens = emptyList()
                    isEvaluated = true
                    showHistorySheet = false
                }
            )
        }
    }
}

@Composable
fun TopAppBarContainer(onOpenHistory: () -> Unit, hasHistory: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = "App Icon",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = "CalcPro",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        IconButton(onClick = onOpenHistory) {
            Box {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    tint = MaterialTheme.colorScheme.onBackground
                )
                if (hasHistory) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .align(Alignment.TopEnd)
                    )
                }
            }
        }
    }
}

@Composable
fun DisplaySection(
    expressionTokens: List<String>,
    currentInput: String,
    livePreview: String?,
    errorMessage: String?
) {
    val exprFormatted = remember(expressionTokens) {
        expressionTokens.joinToString("") { token ->
            if (CalculatorEngine.isOperator(token)) " ${CalculatorEngine.formatOperator(token)} "
            else CalculatorEngine.formatNumber(token)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Mode Badge & Error Message
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "STANDARD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.error
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = errorMessage ?: "",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Calculation Display Text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomEnd),
                horizontalAlignment = Alignment.End
            ) {
                // Secondary Expression Display
                Text(
                    text = exprFormatted,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Primary Input / Output Display
                val fontSize = when {
                    currentInput.length > 12 -> 28.sp
                    currentInput.length > 8 -> 36.sp
                    else -> 46.sp
                }

                Text(
                    text = CalculatorEngine.formatNumber(currentInput),
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Live Preview Result
                Text(
                    text = livePreview ?: "",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun KeypadSection(
    onNumber: (String) -> Unit,
    onOperator: (String) -> Unit,
    onCalculate: () -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onToggleSign: () -> Unit,
    onPercent: () -> Unit,
    onDecimal: () -> Unit,
    onTrig: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Trigonometry row
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalcButton("sin", Modifier.weight(1f), isAction = true, onClick = { onTrig("sin") })
            CalcButton("cos", Modifier.weight(1f), isAction = true, onClick = { onTrig("cos") })
            CalcButton("tan", Modifier.weight(1f), isAction = true, onClick = { onTrig("tan") })
            CalcButton("cot", Modifier.weight(1f), isAction = true, onClick = { onTrig("cot") })
        }

        // Row 1
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalcButton("AC", Modifier.weight(1f), isDanger = true, onClick = onClear)
            CalcButton("⌫", Modifier.weight(1f), isAction = true, onClick = onBackspace)
            CalcButton("%", Modifier.weight(1f), isAction = true, onClick = onPercent)
            CalcButton("÷", Modifier.weight(1f), isOperator = true, onClick = { onOperator("/") })
        }
        // Row 2
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalcButton("7", Modifier.weight(1f), onClick = { onNumber("7") })
            CalcButton("8", Modifier.weight(1f), onClick = { onNumber("8") })
            CalcButton("9", Modifier.weight(1f), onClick = { onNumber("9") })
            CalcButton("×", Modifier.weight(1f), isOperator = true, onClick = { onOperator("*") })
        }
        // Row 3
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalcButton("4", Modifier.weight(1f), onClick = { onNumber("4") })
            CalcButton("5", Modifier.weight(1f), onClick = { onNumber("5") })
            CalcButton("6", Modifier.weight(1f), onClick = { onNumber("6") })
            CalcButton("−", Modifier.weight(1f), isOperator = true, onClick = { onOperator("-") })
        }
        // Row 4
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalcButton("1", Modifier.weight(1f), onClick = { onNumber("1") })
            CalcButton("2", Modifier.weight(1f), onClick = { onNumber("2") })
            CalcButton("3", Modifier.weight(1f), onClick = { onNumber("3") })
            CalcButton("+", Modifier.weight(1f), isOperator = true, onClick = { onOperator("+") })
        }
        // Row 5
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalcButton("±", Modifier.weight(1f), isAction = true, onClick = onToggleSign)
            CalcButton("0", Modifier.weight(1f), onClick = { onNumber("0") })
            CalcButton(".", Modifier.weight(1f), onClick = onDecimal)
            CalcButton("=", Modifier.weight(1f), isEquals = true, onClick = onCalculate)
        }
    }
}

@Composable
fun CalcButton(
    label: String,
    modifier: Modifier = Modifier,
    isOperator: Boolean = false,
    isAction: Boolean = false,
    isDanger: Boolean = false,
    isEquals: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )

    val containerColor = when {
        isEquals -> MaterialTheme.colorScheme.primary
        isOperator -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        isDanger -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        isAction -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surface
    }

    val contentColor = when {
        isEquals -> Color.White
        isOperator -> MaterialTheme.colorScheme.primary
        isDanger -> MaterialTheme.colorScheme.error
        isAction -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .scale(scale)
            .height(68.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 22.sp,
            fontWeight = if (isOperator || isEquals) FontWeight.Bold else FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
fun HistorySheetContent(
    historyList: List<HistoryItem>,
    onClearHistory: () -> Unit,
    onSelectResult: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Calculation History",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (historyList.isNotEmpty()) {
                TextButton(onClick = onClearHistory) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No history yet",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(historyList, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectResult(item.result) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "${item.expression} =",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = CalculatorEngine.formatNumber(item.result),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}