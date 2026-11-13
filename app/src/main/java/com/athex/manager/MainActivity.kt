package com.athex.manager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

private val Purple = Color(0xFF8B5CF6)
private val Panel = Color(0xFF1D1B2B)
private val Ink = Color(0xFF11111B)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var speechCallback: ((String) -> Unit)? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startListening()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { AthexApp({ speak(it) }, { callback ->
            speechCallback = callback
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) tts?.language = Locale.US
        }
    }
    private fun speak(s: String) { tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "athex_tts") }
    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speechCallback?.invoke("Is device par voice recognition available nahi hai.")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "ATHEX ko command dein")
        }
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { speechCallback?.invoke("Voice input nahi mil paya. Dobara try karein.") }
            override fun onResults(results: Bundle?) {
                speechCallback?.invoke(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer?.startListening(intent)
    }
    override fun onDestroy() { recognizer?.destroy(); tts?.shutdown(); super.onDestroy() }
}

data class Expense(val title: String, val amount: Double, val date: String)
data class TaskItem(val title: String, val done: Boolean = false)
data class ReminderItem(val title: String, val time: String)

@Composable
fun AthexApp(speak: (String) -> Unit, listen: (((String) -> Unit)) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("athex_data", 0) }
    var tab by remember { mutableIntStateOf(0) }
    var expenses by remember { mutableStateOf(readExpenses(prefs.getString("expenses", "[]")!!)) }
    var tasks by remember { mutableStateOf(readTasks(prefs.getString("tasks", "[]")!!)) }
    var reminders by remember { mutableStateOf(readReminders(prefs.getString("reminders", "[]")!!)) }
    var expenseName by remember { mutableStateOf("") }; var expenseAmount by remember { mutableStateOf("") }
    var taskName by remember { mutableStateOf("") }; var reminderName by remember { mutableStateOf("") }
    var reminderTime by remember { mutableStateOf("08:00") }
    var assistantReply by remember { mutableStateOf("Namaste! Main ATHEX hoon. Aaj kya manage karna hai?") }
    var typedCommand by remember { mutableStateOf("") }

    fun saveExpenses() {
        val a = JSONArray(); expenses.forEach { a.put(JSONObject().put("title", it.title).put("amount", it.amount).put("date", it.date)) }
        prefs.edit().putString("expenses", a.toString()).apply()
    }
    fun saveTasks() {
        val a = JSONArray(); tasks.forEach { a.put(JSONObject().put("title", it.title).put("done", it.done)) }
        prefs.edit().putString("tasks", a.toString()).apply()
    }
    fun saveReminders() {
        val a = JSONArray(); reminders.forEach { a.put(JSONObject().put("title", it.title).put("time", it.time)) }
        prefs.edit().putString("reminders", a.toString()).apply()
    }
    fun handleCommand(command: String) {
        val c = command.lowercase(Locale.ROOT)
        assistantReply = when {
            c.contains("total") || c.contains("balance") || c.contains("kitna kharch") ->
                "Tumhara recorded expense total ₹${"%.2f".format(expenses.sumOf { it.amount })} hai."
            c.contains("task") || c.contains("kaam") -> "Tumhare ${tasks.count { !it.done }} pending tasks hain."
            c.contains("reminder") || c.contains("yaad") -> "Tumhare ${reminders.size} saved reminders hain. Reminders tab mein unhe dekho."
            c.contains("namaste") || c.contains("hello") || c == "hi" ->
                "Namaste! Main ATHEX hoon. Main expenses, tasks aur reminders manage kar sakta hoon."
            else -> "Maine suna: $command. Abhi main basic offline commands support karta hoon."
        }
        speak(assistantReply)
    }
    fun scheduleReminder(title: String, time: String): Boolean {
        val parts = time.split(":")
        if (parts.size != 2) return false
        val hour = parts[0].toIntOrNull() ?: return false
        val minute = parts[1].toIntOrNull() ?: return false
        if (hour !in 0..23 || minute !in 0..59) return false
        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour); set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val delay = (target.timeInMillis - now.timeInMillis).coerceAtLeast(1000L)
        val data = Data.Builder().putString("title", title).build()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(data).setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueue(request)
        reminders = listOf(ReminderItem(title, time)) + reminders
        saveReminders()
        return true
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Purple, background = Ink, surface = Panel, onBackground = Color.White, onSurface = Color.White)) {
        Scaffold(containerColor = Ink, bottomBar = {
            NavigationBar(containerColor = Panel) {
                val labels = listOf("Home", "Assistant", "Finance", "Tasks", "Reminders")
                val icons = listOf(Icons.Default.Home, Icons.Default.Mic, Icons.Default.AccountBalanceWallet, Icons.Default.CheckCircle, Icons.Default.Alarm)
                labels.forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], label) }, label = { Text(label) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ATHEX", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text("YOUR PERSONAL MANAGER", color = Purple, style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(Icons.Default.AutoAwesome, null, tint = Purple, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(20.dp))
                when (tab) {
                    0 -> {
                        Text("Good day 👋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Let's organise your day.", color = Color.LightGray)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryCard("Expenses", "₹${"%.0f".format(expenses.sumOf { it.amount })}", Modifier.weight(1f))
                            SummaryCard("Pending tasks", "${tasks.count { !it.done }}", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        SummaryCard("Reminders saved", "${reminders.size}", Modifier.fillMaxWidth())
                        Spacer(Modifier.height(14.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("ATHEX says", color = Purple, fontWeight = FontWeight.Bold)
                                Text(assistantReply)
                                Button(onClick = { tab = 1 }) { Icon(Icons.Default.Mic, null); Text("  Talk to ATHEX") }
                            }
                        }
                    }
                    1 -> {
                        Text("Voice Assistant", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.GraphicEq, null, tint = Purple, modifier = Modifier.size(64.dp))
                                Text("Hello! I'm ATHEX", fontWeight = FontWeight.Bold)
                                Text(assistantReply, color = Color.LightGray)
                                Button(onClick = { listen { handleCommand(it) } }) { Icon(Icons.Default.Mic, null); Text("  Speak now") }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(typedCommand, { typedCommand = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Type a command") })
                        Button(onClick = { handleCommand(typedCommand); typedCommand = "" }, modifier = Modifier.fillMaxWidth()) { Text("Send") }
                        Text("Try: total expense kitna hai, mere kitne tasks hain, reminders batao", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                    2 -> {
                        Text("My Finance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Manual expense tracker • data stays on this device", color = Color.LightGray)
                        OutlinedTextField(expenseName, { expenseName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Expense detail") })
                        OutlinedTextField(expenseAmount, { expenseAmount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Amount ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        Button(onClick = {
                            val value = expenseAmount.toDoubleOrNull() ?: 0.0
                            if (expenseName.isNotBlank() && value > 0) {
                                expenses = listOf(Expense(expenseName, value, SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())) ) + expenses
                                saveExpenses(); expenseName = ""; expenseAmount = ""
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Add expense") }
                        Text("Total: ₹${"%.2f".format(expenses.sumOf { it.amount })}", fontWeight = FontWeight.Bold)
                        LazyColumn { items(expenses) { e -> DataRow(e.title, e.date, "₹${"%.2f".format(e.amount)}") } }
                    }
                    3 -> {
                        Text("My Tasks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(taskName, { taskName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New task") })
                        Button(onClick = { if (taskName.isNotBlank()) { tasks = listOf(TaskItem(taskName)) + tasks; saveTasks(); taskName = "" } }, modifier = Modifier.fillMaxWidth()) { Text("Add task") }
                        LazyColumn { items(tasks.indices.toList()) { i ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(tasks[i].done, { checked -> tasks = tasks.toMutableList().also { it[i] = it[i].copy(done = checked) }; saveTasks() })
                                Text(tasks[i].title, Modifier.weight(1f))
                                IconButton(onClick = { tasks = tasks.toMutableList().also { it.removeAt(i) }; saveTasks() }) { Icon(Icons.Default.Delete, null) }
                            }
                        } }
                    }
                    else -> {
                        Text("Smart Reminders", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("One-time notification reminders", color = Color.LightGray)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(reminderName, { reminderName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Reminder (e.g. Call college)") })
                        OutlinedTextField(reminderTime, { reminderTime = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Time 24-hour format (HH:mm)") })
                        Button(onClick = {
                            if (reminderName.isNotBlank() && scheduleReminder(reminderName, reminderTime)) reminderName = ""
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Alarm, null); Text("  Schedule reminder") }
                        Text("Past time automatically schedules for tomorrow. Android may delay notifications due to battery optimisation.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        LazyColumn { items(reminders) { r -> DataRow(r.title, "Scheduled at ${r.time}", "•") } }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) { Text(label, color = Color.LightGray); Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
    }
}
@Composable
fun DataRow(title: String, subtitle: String, trailing: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.labelSmall) }
        Text(trailing, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider(color = Color.DarkGray)
}
fun readExpenses(raw: String): List<Expense> = try {
    val a = JSONArray(raw); (0 until a.length()).map { val o = a.getJSONObject(it); Expense(o.getString("title"), o.getDouble("amount"), o.getString("date")) }
} catch (_: Exception) { emptyList() }
fun readTasks(raw: String): List<TaskItem> = try {
    val a = JSONArray(raw); (0 until a.length()).map { val o = a.getJSONObject(it); TaskItem(o.getString("title"), o.optBoolean("done")) }
} catch (_: Exception) { emptyList() }
fun readReminders(raw: String): List<ReminderItem> = try {
    val a = JSONArray(raw); (0 until a.length()).map { val o = a.getJSONObject(it); ReminderItem(o.getString("title"), o.getString("time")) }
} catch (_: Exception) { emptyList() }
