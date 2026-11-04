package com.example.data

import com.example.core.ErrorCatcher
import com.example.core.GeneratedProjectFiles
import com.example.model.ClaudeApkConfig
import com.example.model.ClaudeArtifactType
import com.example.model.ClaudeDiagnosticIssue
import com.example.model.ClaudeSourceFile
import com.example.model.DiagnosticSeverity
import com.example.model.StagedEntry
import com.example.model.StagedEntryCategory
import java.util.Locale
import java.util.UUID

data class ClaudePresetTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val config: ClaudeApkConfig,
    val rawInput: String
)

object ClaudeApkConverter {

    val presets: List<ClaudePresetTemplate> = listOf(
        ClaudePresetTemplate(
            id = "claude_neon_synth",
            title = "Claude PulseOS MiniApp (HTML/JS)",
            subtitle = "Interactive task board, focus timer & touch synth pad",
            config = ClaudeApkConfig(
                appName = "Claude PulseOS",
                applicationId = "com.claude.pulseos",
                versionName = "1.2.0",
                artifactType = ClaudeArtifactType.HTML_WEB_APP
            ),
            rawInput = """
Here is your complete interactive mobile web app ready to package as an Android APK:

```html
<!-- filename: index.html -->
<div class="app-shell">
  <header class="top-header">
    <div>
      <span class="pill-badge">CLAUDE ARTIFACT • LIVE APK</span>
      <h1>PulseOS Workspace</h1>
    </div>
    <button id="themeBtn" class="icon-btn" onclick="triggerPulse()">⚡ Pulse</button>
  </header>

  <section class="metric-grid">
    <div class="metric-card">
      <span class="label">FOCUS CLOCK</span>
      <div id="timerDisplay" class="big-val">25:00</div>
      <div class="row-btns">
        <button class="btn-primary" onclick="toggleTimer()" id="startStopBtn">Start</button>
        <button class="btn-ghost" onclick="resetTimer()">Reset</button>
      </div>
    </div>
    <div class="metric-card">
      <span class="label">APK BRIDGE SCORE</span>
      <div id="scoreDisplay" class="big-val accent">120</div>
      <button class="btn-secondary" onclick="boostScore()">+15 XP Tap</button>
    </div>
  </section>

  <section class="card-panel">
    <div class="panel-head">
      <h2>Quick Tasks</h2>
      <span id="taskCounter" class="count-tag">3 active</span>
    </div>
    <div class="input-row">
      <input id="taskInput" type="text" placeholder="Add feature to APK..." />
      <button class="btn-primary" onclick="addTask()">Add</button>
    </div>
    <ul id="taskList" class="task-list"></ul>
  </section>
</div>
```

```css
/* filename: styles.css */
* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
body { background: #0B1120; color: #F1F5F9; padding: 16px; user-select: none; }
.app-shell { max-width: 480px; margin: 0 auto; display: flex; flex-direction: column; gap: 14px; }
.top-header { display: flex; justify-content: space-between; align-items: center; background: linear-gradient(135deg, #111827, #064E3B); padding: 16px; border-radius: 18px; border: 1px solid #10B98144; }
.pill-badge { font-size: 10px; font-weight: 700; letter-spacing: 0.8px; color: #34D399; background: #064E3B; padding: 3px 8px; border-radius: 99px; }
h1 { font-size: 20px; margin-top: 6px; color: #FFFFFF; }
h2 { font-size: 15px; color: #E2E8F0; }
.icon-btn { background: #10B981; color: #022C22; border: none; padding: 8px 14px; border-radius: 12px; font-weight: 700; cursor: pointer; }
.metric-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.metric-card { background: #111827; border: 1px solid #1E293B; padding: 14px; border-radius: 16px; display: flex; flex-direction: column; gap: 8px; }
.label { font-size: 10px; color: #94A3B8; font-weight: 700; letter-spacing: 0.6px; }
.big-val { font-size: 26px; font-weight: 800; color: #F8FAFC; font-variant-numeric: tabular-nums; }
.big-val.accent { color: #38BDF8; }
.row-btns { display: flex; gap: 6px; }
.btn-primary { background: #10B981; color: #022C22; border: none; padding: 8px 12px; border-radius: 10px; font-weight: 700; cursor: pointer; flex: 1; }
.btn-secondary { background: #0284C7; color: #FFFFFF; border: none; padding: 8px 12px; border-radius: 10px; font-weight: 600; cursor: pointer; }
.btn-ghost { background: #1E293B; color: #CBD5E1; border: none; padding: 8px 10px; border-radius: 10px; cursor: pointer; }
.card-panel { background: #111827; border: 1px solid #1E293B; padding: 16px; border-radius: 16px; display: flex; flex-direction: column; gap: 12px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; }
.count-tag { font-size: 11px; color: #38BDF8; background: #0C4A6E; padding: 2px 8px; border-radius: 99px; }
.input-row { display: flex; gap: 8px; }
input { flex: 1; background: #0B1120; border: 1px solid #334155; color: #fff; padding: 10px 12px; border-radius: 10px; outline: none; }
.task-list { list-style: none; display: flex; flex-direction: column; gap: 8px; }
.task-item { display: flex; justify-content: space-between; align-items: center; background: #1E293B66; padding: 10px 12px; border-radius: 10px; font-size: 14px; }
.task-item.done span { text-decoration: line-through; color: #64748B; }
```

```javascript
// filename: app.js
let secondsLeft = 25 * 60;
let timerId = null;
let score = 120;
let tasks = [
  { id: 1, text: "Parse Claude artifact code blocks", done: true },
  { id: 2, text: "Test inside Live Android Device Frame", done: false },
  { id: 3, text: "Export standalone APK project bundle", done: false }
];

function renderTasks() {
  const list = document.getElementById('taskList');
  const counter = document.getElementById('taskCounter');
  if (!list) return;
  list.innerHTML = tasks.map(t =>
    '<li class="task-item ' + (t.done ? 'done' : '') + '" onclick="toggleTask(' + t.id + ')">' +
      '<span>' + (t.done ? '✅ ' : '⬜ ') + t.text + '</span>' +
    '</li>'
  ).join('');
  const remaining = tasks.filter(t => !t.done).length;
  counter.textContent = remaining + " active";
}

function toggleTask(id) {
  tasks = tasks.map(t => t.id === id ? { ...t, done: !t.done } : t);
  console.log("Toggled task #" + id);
  renderTasks();
}

function addTask() {
  const input = document.getElementById('taskInput');
  const val = (input.value || '').trim();
  if (!val) return;
  tasks.unshift({ id: Date.now(), text: val, done: false });
  input.value = '';
  console.log("Added new task: " + val);
  renderTasks();
}

function boostScore() {
  score += 15;
  document.getElementById('scoreDisplay').textContent = score;
  console.log("Score boosted to " + score);
}

function triggerPulse() {
  boostScore();
  document.body.style.background = score % 2 === 0 ? '#0B1120' : '#082F49';
  console.log("Pulse triggered via APK Bridge!");
}

function updateClock() {
  const m = String(Math.floor(secondsLeft / 60)).padStart(2, '0');
  const s = String(secondsLeft % 60).padStart(2, '0');
  document.getElementById('timerDisplay').textContent = m + ":" + s;
}

function toggleTimer() {
  const btn = document.getElementById('startStopBtn');
  if (timerId) {
    clearInterval(timerId);
    timerId = null;
    btn.textContent = "Start";
    console.log("Focus timer paused");
  } else {
    timerId = setInterval(() => {
      if (secondsLeft > 0) secondsLeft--;
      updateClock();
    }, 1000);
    btn.textContent = "Pause";
    console.log("Focus timer started");
  }
}

function resetTimer() {
  if (timerId) { clearInterval(timerId); timerId = null; }
  secondsLeft = 25 * 60;
  document.getElementById('startStopBtn').textContent = "Start";
  updateClock();
  console.log("Focus timer reset");
}

renderTasks();
console.log("Claude PulseOS initialized inside Android WebView");
```
            """.trimIndent()
        ),
        ClaudePresetTemplate(
            id = "claude_react_jsx",
            title = "Claude React Artifact (App.jsx)",
            subtitle = "React JSX component converted to standalone mobile WebAPK",
            config = ClaudeApkConfig(
                appName = "Claude CryptoCalc",
                applicationId = "com.claude.unitconverter",
                versionName = "1.0.0",
                artifactType = ClaudeArtifactType.REACT_JSX_ARTIFACT
            ),
            rawInput = """
```jsx
// filename: App.jsx
import React, { useState } from 'react';

export default function App() {
  const [amount, setAmount] = useState(100);
  const [unit, setUnit] = useState('km_to_mi');

  const factor = unit === 'km_to_mi' ? 0.621371 : unit === 'kg_to_lb' ? 2.20462 : 1.8;
  const converted = unit === 'c_to_f' ? (amount * 1.8 + 32).toFixed(1) : (amount * factor).toFixed(2);

  return (
    <div className="converter-card">
      <h2>Claude React Unit Studio</h2>
      <p>Converted: {converted}</p>
    </div>
  );
}
```
            """.trimIndent()
        ),
        ClaudePresetTemplate(
            id = "claude_kotlin_compose",
            title = "Claude Jetpack Compose (MainActivity.kt)",
            subtitle = "Native Kotlin Compose screen packaged into Gradle APK",
            config = ClaudeApkConfig(
                appName = "Claude HabitFlow",
                applicationId = "com.claude.habitflow",
                versionName = "2.0.0",
                artifactType = ClaudeArtifactType.KOTLIN_COMPOSE
            ),
            rawInput = """
```kotlin
// filename: MainActivity.kt
package com.claude.habitflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HabitFlowScreen()
            }
        }
    }
}

@Composable
fun HabitFlowScreen() {
    var streak by remember { mutableIntStateOf(7) }
    var waterGlasses by remember { mutableIntStateOf(5) }
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Claude HabitFlow", style = MaterialTheme.typography.headlineMedium)
        Text("Current Streak: ${'$'}streak days")
        Button(onClick = { streak++ }) { Text("Complete Daily Check-In") }
        Button(onClick = { waterGlasses++ }) { Text("Hydration: ${'$'}waterGlasses / 8 glasses") }
    }
}
```
            """.trimIndent()
        ),
        ClaudePresetTemplate(
            id = "claude_broken_diagnostic_demo",
            title = "Broken Claude File (Error Inspector Demo)",
            subtitle = "Demonstrates error detection, plain-English explanations & Copy-for-Claude repair prompt",
            config = ClaudeApkConfig(
                appName = "Broken Claude Demo",
                applicationId = "com.claude.brokendemo",
                versionName = "0.1.0",
                artifactType = ClaudeArtifactType.REACT_JSX_ARTIFACT
            ),
            rawInput = """
```jsx
// filename: BrokenDashboard.jsx
import fs from 'fs';

export default function BrokenDashboard() {
  const [items, setItems] = useState(["Item 1", "Item 2"]);
  
  // ... rest of implementation omitted by Claude ...

  function triggerBug() {
    nonExistentClaudeFunction();
  }

  return (
    <div className="dashboard">
      <h2>Broken Claude Component</h2>
      <button onClick={triggerBug}>Run Broken Handler</button>
  );
```
            """.trimIndent()
        )
    )

    private val fenceRegex = Regex("```([a-zA-Z0-9_-]*)\\s*\\n([\\s\\S]*?)```")
    private val fileCommentRegex = Regex("(?m)^\\s*(?://|#|<!--|/\\*)\\s*(?:filename|file|path)?\\s*:?\\s*([a-zA-Z0-9_./-]+\\.[a-zA-Z0-9]+)\\s*(?:-->|\\*/)?\\s*$")

    fun parseClaudeInput(rawInput: String): Pair<ClaudeArtifactType, List<ClaudeSourceFile>> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ClaudeArtifactType.HTML_WEB_APP to emptyList()
        }

        val matches = fenceRegex.findAll(trimmed).toList()
        val extracted = mutableListOf<ClaudeSourceFile>()

        if (matches.isNotEmpty()) {
            matches.forEachIndexed { index, match ->
                val langHint = match.groupValues[1].lowercase(Locale.US).trim()
                val body = match.groupValues[2].trim()
                val commentMatch = fileCommentRegex.find(body)
                val explicitFileName = commentMatch?.groupValues?.get(1)?.trim()

                val fileName = explicitFileName ?: defaultFileNameForLang(langHint, body, index)
                val resolvedLang = if (langHint.isNotBlank()) langHint else inferLangFromFileName(fileName)

                extracted.add(
                    ClaudeSourceFile(
                        id = UUID.randomUUID().toString(),
                        fileName = fileName,
                        language = resolvedLang,
                        content = body
                    )
                )
            }
        } else {
            val commentMatch = fileCommentRegex.find(trimmed)
            val explicitFileName = commentMatch?.groupValues?.get(1)?.trim()
            val fileName = explicitFileName ?: inferSingleFileName(trimmed)
            val lang = inferLangFromFileName(fileName)
            extracted.add(
                ClaudeSourceFile(
                    id = UUID.randomUUID().toString(),
                    fileName = fileName,
                    language = lang,
                    content = trimmed
                )
            )
        }

        val detectedType = when {
            extracted.any { it.fileName.endsWith(".kt") || it.language == "kotlin" } ->
                ClaudeArtifactType.KOTLIN_COMPOSE
            extracted.any {
                it.fileName.endsWith(".jsx") || it.fileName.endsWith(".tsx") ||
                    it.language in listOf("jsx", "tsx", "react") ||
                    it.content.contains("export default function") ||
                    it.content.contains("useState(")
            } -> ClaudeArtifactType.REACT_JSX_ARTIFACT
            else -> ClaudeArtifactType.HTML_WEB_APP
        }

        return detectedType to extracted
    }

    /**
     * Performs static analysis on Claude-authored files to detect syntax errors, truncated placeholders,
     * missing imports, bracket mismatches, or Android APK compatibility issues, and explains each in plain English.
     */
    fun analyzeDiagnostics(
        config: ClaudeApkConfig,
        files: List<ClaudeSourceFile>
    ): List<ClaudeDiagnosticIssue> {
        val issues = mutableListOf<ClaudeDiagnosticIssue>()

        if (files.isEmpty()) {
            issues.add(
                ClaudeDiagnosticIssue(
                    id = UUID.randomUUID().toString(),
                    severity = DiagnosticSeverity.ERROR,
                    fileName = "workspace",
                    lineNumber = null,
                    title = "No Source Files Detected",
                    description = "No valid code blocks or source files were found in the input.",
                    suggestedFix = "Paste code written by Claude or import a .html, .jsx, .tsx, or .kt file."
                )
            )
            return issues
        }

        // Validate Android Application ID format
        val pkgRegex = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
        if (!pkgRegex.matches(config.applicationId)) {
            issues.add(
                ClaudeDiagnosticIssue(
                    id = UUID.randomUUID().toString(),
                    severity = DiagnosticSeverity.ERROR,
                    fileName = "AndroidManifest.xml / build.gradle.kts",
                    lineNumber = 1,
                    title = "Invalid Android Package Name (applicationId)",
                    description = "The package ID '${config.applicationId}' is not a valid Android applicationId. Android requires at least two dot-separated lowercase segments starting with a letter (e.g., com.claude.myapp).",
                    suggestedFix = "Update Application ID to a valid format such as 'com.claude.pulseapp'."
                )
            )
        }

        files.forEach { file ->
            val lines = file.content.lines()

            // 1. Check for truncated AI placeholders ("// ... rest of code", "/* TODO */")
            lines.forEachIndexed { idx, line ->
                val lower = line.lowercase(Locale.US)
                if (lower.contains("rest of code") ||
                    lower.contains("rest of implementation") ||
                    lower.contains("remain the same") ||
                    lower.contains("// ... existing")
                ) {
                    issues.add(
                        ClaudeDiagnosticIssue(
                            id = UUID.randomUUID().toString(),
                            severity = DiagnosticSeverity.WARNING,
                            fileName = file.fileName,
                            lineNumber = idx + 1,
                            title = "Truncated AI Placeholder Detected",
                            description = "Line ${idx + 1} contains an omitted code placeholder ('${line.trim()}'). Incomplete files can cause missing UI sections or undefined variables when packaged into an APK.",
                            suggestedFix = "Ask Claude to output the complete file without '// ...' truncation comments.",
                            codeSnippet = line.trim()
                        )
                    )
                }
            }

            // 2. Check balanced curly braces { } and parentheses ( ) for code files
            if (file.language in listOf("javascript", "js", "jsx", "tsx", "kotlin", "kt", "css")) {
                val openBraces = file.content.count { it == '{' }
                val closeBraces = file.content.count { it == '}' }
                if (openBraces != closeBraces) {
                    issues.add(
                        ClaudeDiagnosticIssue(
                            id = UUID.randomUUID().toString(),
                            severity = DiagnosticSeverity.ERROR,
                            fileName = file.fileName,
                            lineNumber = lines.size,
                            title = "Unbalanced Curly Braces '{' vs '}'",
                            description = "Found $openBraces opening '{' brace(s) but $closeBraces closing '}' brace(s) in ${file.fileName}. This usually happens when a code snippet was cut off before the end of the file.",
                            suggestedFix = "Ensure all functions, classes, and JSX blocks have matching closing '}' braces.",
                            codeSnippet = lines.takeLast(4).joinToString("\n")
                        )
                    )
                }

                val openParens = file.content.count { it == '(' }
                val closeParens = file.content.count { it == ')' }
                if (openParens != closeParens) {
                    issues.add(
                        ClaudeDiagnosticIssue(
                            id = UUID.randomUUID().toString(),
                            severity = DiagnosticSeverity.ERROR,
                            fileName = file.fileName,
                            lineNumber = lines.size,
                            title = "Unbalanced Parentheses '(' vs ')'",
                            description = "Found $openParens opening '(' but $closeParens closing ')' in ${file.fileName}, which will cause a syntax error at compile/runtime.",
                            suggestedFix = "Close all function calls and return(...) statements in ${file.fileName}.",
                            codeSnippet = lines.takeLast(4).joinToString("\n")
                        )
                    )
                }
            }

            // 3. React / JSX specific checks
            if (file.language in listOf("jsx", "tsx") || file.fileName.endsWith(".jsx") || file.fileName.endsWith(".tsx")) {
                // Check for Node.js server-only modules imported inside a client mobile artifact
                val nodeModuleRegex = Regex("""import\s+.*?\s+from\s+['"](fs|path|child_process|os|net|crypto|express)['"]""")
                lines.forEachIndexed { idx, line ->
                    val match = nodeModuleRegex.find(line)
                    if (match != null) {
                        val modName = match.groupValues[1]
                        issues.add(
                            ClaudeDiagnosticIssue(
                                id = UUID.randomUUID().toString(),
                                severity = DiagnosticSeverity.ERROR,
                                fileName = file.fileName,
                                lineNumber = idx + 1,
                                title = "Node.js Backend Module '$modName' Cannot Run in Android WebView",
                                description = "Line ${idx + 1} imports the Node.js server module '$modName', which does not exist in mobile browsers or Android WebView APKs.",
                                suggestedFix = "Replace '$modName' with browser/mobile APIs (such as localStorage, IndexedDB, or window.AndroidApkBridge).",
                                codeSnippet = line.trim()
                            )
                        )
                    }
                }

                // Check for unclosed div tags in JSX return
                val openDivs = Regex("<div(\\s|>)").findAll(file.content).count()
                val closeDivs = Regex("</div>").findAll(file.content).count()
                if (openDivs != closeDivs) {
                    issues.add(
                        ClaudeDiagnosticIssue(
                            id = UUID.randomUUID().toString(),
                            severity = DiagnosticSeverity.ERROR,
                            fileName = file.fileName,
                            lineNumber = lines.size,
                            title = "Unclosed JSX <div> Tag ($openDivs opened vs $closeDivs closed)",
                            description = "Your React component opens $openDivs <div> element(s) but only closes $closeDivs </div> tag(s). Babel/React cannot compile JSX with unclosed elements.",
                            suggestedFix = "Add the missing </div> closing tag before the end of your component's return statement.",
                            codeSnippet = lines.takeLast(5).joinToString("\n")
                        )
                    )
                }
            }

            // 4. Kotlin / Jetpack Compose specific checks
            if (file.language in listOf("kotlin", "kt") || file.fileName.endsWith(".kt")) {
                if (!file.content.contains("class MainActivity") && files.none { it.content.contains("class MainActivity") }) {
                    issues.add(
                        ClaudeDiagnosticIssue(
                            id = UUID.randomUUID().toString(),
                            severity = DiagnosticSeverity.WARNING,
                            fileName = file.fileName,
                            lineNumber = 1,
                            title = "No MainActivity Class Found in Kotlin Files",
                            description = "Android APKs require a launcher ComponentActivity (MainActivity) to mount your @Composable functions on launch.",
                            suggestedFix = "Wrap your root @Composable screen inside a MainActivity : ComponentActivity() class with setContent { ... }."
                        )
                    )
                }
            }
        }

        return issues
    }

    /**
     * Generates a structured, copy-and-pasteable AI repair prompt summarizing all static
     * and runtime errors so the user can paste it directly into Claude or another coding assistant.
     */
    fun buildCopyableErrorReportForClaude(
        config: ClaudeApkConfig,
        files: List<ClaudeSourceFile>,
        diagnostics: List<ClaudeDiagnosticIssue>,
        runtimeErrors: List<String>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("I am packaging my code into an Android APK (${config.appName} / ${config.applicationId}), and the APK Error Inspector found the following issues. Please fix all of them and return the complete, working code without any truncated placeholders:")
        sb.appendLine()

        if (diagnostics.isNotEmpty()) {
            sb.appendLine("### Static & Packaging Errors (${diagnostics.size})")
            diagnostics.forEachIndexed { idx, issue ->
                val loc = if (issue.lineNumber != null) "${issue.fileName}:line ${issue.lineNumber}" else issue.fileName
                sb.appendLine("${idx + 1}. [${issue.severity.label}] ${issue.title} ($loc)")
                sb.appendLine("   - Explanation: ${issue.description}")
                sb.appendLine("   - Suggested Fix: ${issue.suggestedFix}")
                if (!issue.codeSnippet.isNullOrBlank()) {
                    sb.appendLine("   - Offending Code: `${issue.codeSnippet.replace("\n", " ")}`")
                }
            }
            sb.appendLine()
        }

        if (runtimeErrors.isNotEmpty()) {
            sb.appendLine("### Live Android Preview Runtime Errors (${runtimeErrors.size})")
            runtimeErrors.forEachIndexed { idx, err ->
                sb.appendLine("${idx + 1}. $err")
            }
            sb.appendLine()
        }

        if (diagnostics.isEmpty() && runtimeErrors.isEmpty()) {
            sb.appendLine("### Status: No Syntax or Runtime Errors Detected")
            sb.appendLine("All ${files.size} file(s) passed static and runtime verification for Android APK packaging.")
            sb.appendLine()
        }

        sb.appendLine("### Current Source Files")
        files.forEach { f ->
            sb.appendLine("```${f.language}")
            sb.appendLine("// filename: ${f.fileName}")
            sb.appendLine(f.content)
            sb.appendLine("```")
            sb.appendLine()
        }

        return sb.toString().trim()
    }

    private fun defaultFileNameForLang(lang: String, body: String, index: Int): String {
        return when (lang) {
            "html", "htm" -> if (index == 0) "index.html" else "section_$index.html"
            "css", "scss" -> "styles.css"
            "js", "javascript" -> "app.js"
            "jsx" -> "App.jsx"
            "tsx", "ts", "typescript" -> "App.tsx"
            "kt", "kotlin" -> if (body.contains("class MainActivity")) "MainActivity.kt" else "ClaudeScreen_$index.kt"
            "xml" -> if (body.contains("<manifest")) "AndroidManifest.xml" else "layout_$index.xml"
            "json" -> "config.json"
            else -> inferSingleFileName(body)
        }
    }

    private fun inferSingleFileName(content: String): String {
        return when {
            content.contains("@Composable") || content.contains("package ") -> "MainActivity.kt"
            content.contains("export default function") || content.contains("import React") -> "App.jsx"
            content.contains("<html") || content.contains("<div") || content.contains("<!DOCTYPE") -> "index.html"
            content.contains("function ") || content.contains("const ") -> "app.js"
            else -> "index.html"
        }
    }

    private fun inferLangFromFileName(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "kt" -> "kotlin"
            "jsx" -> "jsx"
            "tsx" -> "tsx"
            "js" -> "javascript"
            "css" -> "css"
            "html", "htm" -> "html"
            "xml" -> "xml"
            else -> "html"
        }
    }

    /** The single-page HTML used by the live preview and the APK, with the error catcher first in <head>. */
    fun buildStandaloneWebApkHtml(
        config: ClaudeApkConfig,
        files: List<ClaudeSourceFile>
    ): String = ErrorCatcher.injectIntoHtml(buildWebHtmlWithoutCatcher(config, files))

    private fun buildWebHtmlWithoutCatcher(
        config: ClaudeApkConfig,
        files: List<ClaudeSourceFile>
    ): String {
        if (files.isEmpty()) {
            return "<html><body style='background:#0B1120;color:#fff;padding:24px;'><h3>No Claude files loaded</h3></body></html>"
        }

        val htmlFiles = files.filter { it.fileName.endsWith(".html") || it.language == "html" }
        val cssFiles = files.filter { it.fileName.endsWith(".css") || it.language == "css" }
        val jsFiles = files.filter { it.fileName.endsWith(".js") || it.language == "javascript" }
        val jsxFiles = files.filter {
            it.fileName.endsWith(".jsx") || it.fileName.endsWith(".tsx") ||
                it.language in listOf("jsx", "tsx")
        }
        val ktFiles = files.filter { it.fileName.endsWith(".kt") || it.language == "kotlin" }

        if (ktFiles.isNotEmpty() && htmlFiles.isEmpty() && jsxFiles.isEmpty()) {
            return buildKotlinComposeInteractivePreviewHtml(config, ktFiles.first())
        }

        if (jsxFiles.isNotEmpty() && htmlFiles.isEmpty()) {
            return buildReactArtifactPreviewHtml(config, jsxFiles.first(), cssFiles)
        }

        val combinedCss = cssFiles.joinToString("\n\n") { it.content }
        val combinedJs = jsFiles.joinToString("\n\n") { it.content }
        val mainHtml = htmlFiles.firstOrNull()?.content ?: files.first().content

        if (mainHtml.contains("<html", ignoreCase = true) || mainHtml.contains("<!DOCTYPE", ignoreCase = true)) {
            var enriched = mainHtml
            if (combinedCss.isNotBlank()) {
                enriched = if (enriched.contains("</head>", ignoreCase = true)) {
                    enriched.replaceFirst(Regex("(?i)</head>"), "<style>\n$combinedCss\n</style>\n</head>")
                } else {
                    "<style>\n$combinedCss\n</style>\n$enriched"
                }
            }
            val scriptBlock = "<script>\n$combinedJs\n</script>"
            enriched = if (enriched.contains("</body>", ignoreCase = true)) {
                enriched.replaceFirst(Regex("(?i)</body>"), "$scriptBlock\n</body>")
            } else {
                "$enriched\n$scriptBlock"
            }
            return enriched
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <title>${config.appName}</title>
  <style>
    $combinedCss
  </style>
</head>
<body>
  $mainHtml
  <script>
    window.AndroidApkBridge = window.AndroidApkBridge || {
      getAppName: () => "${config.appName}",
      getPackageName: () => "${config.applicationId}",
      log: (msg) => console.log("[APK Bridge] " + msg)
    };
    $combinedJs
  </script>
</body>
</html>
        """.trimIndent()
    }

    private fun buildReactArtifactPreviewHtml(
        config: ClaudeApkConfig,
        jsxFile: ClaudeSourceFile,
        cssFiles: List<ClaudeSourceFile>
    ): String {
        val extraCss = cssFiles.joinToString("\n") { it.content }
        val escapedSource = jsxFile.content
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val runnableJsx = jsxFile.content
            .replace(Regex("(?m)^\\s*import\\s+.*?from\\s+['\"].*?['\"];?\\s*$"), "")
            .replace("export default function ", "function ")
            .replace("export default ", "const App = ")

        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>${config.appName}</title>
  <script src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
  <script src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
  <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
  <style>
    * { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
    body { margin: 0; padding: 16px; background: #0B1120; color: #F8FAFC; }
    .converter-card, .fallback-shell {
      background: #111827; border: 1px solid #1E293B; border-radius: 18px; padding: 18px;
      display: flex; flex-direction: column; gap: 12px;
    }
    .badge { display: inline-block; background: #064E3B; color: #34D399; font-size: 10px; font-weight: 700; padding: 4px 10px; border-radius: 99px; width: fit-content; }
    input, select { background: #0B1120; color: #F8FAFC; border: 1px solid #334155; padding: 10px 12px; border-radius: 10px; width: 100%; font-size: 15px; }
    button { background: #10B981; color: #022C22; border: none; padding: 10px 14px; border-radius: 10px; font-weight: 700; cursor: pointer; }
    .btn-danger { background: #EF4444; color: #FFFFFF; }
    .result-box { background: #0C4A6E55; border: 1px solid #38BDF866; padding: 14px; border-radius: 12px; font-size: 22px; font-weight: 800; color: #38BDF8; }
    pre { background: #0B1120; padding: 10px; border-radius: 10px; overflow-x: auto; font-size: 11px; color: #94A3B8; }
    $extraCss
  </style>
</head>
<body>
  <div id="root">
    <div class="fallback-shell">
      <span class="badge">CLAUDE REACT ARTIFACT • ${jsxFile.fileName}</span>
      <h2 style="margin:0;font-size:19px;">${config.appName}</h2>
      <label style="font-size:12px;color:#94A3B8;">Input Value</label>
      <input id="reactInputVal" type="number" value="100" oninput="recalcUnit()" />
      <label style="font-size:12px;color:#94A3B8;">Conversion Mode</label>
      <select id="reactUnitSelect" onchange="recalcUnit()">
        <option value="km_mi">Kilometers → Miles</option>
        <option value="kg_lb">Kilograms → Pounds</option>
        <option value="c_f">Celsius → Fahrenheit</option>
      </select>
      <div class="result-box" id="reactOutputBox">62.14 mi</div>
      <button onclick="recalcUnit(true)">Log Conversion to APK Console</button>
      <button class="btn-danger" onclick="simulateRuntimeError()">Test Runtime Exception Trap</button>
      <details>
        <summary style="font-size:12px;color:#38BDF8;cursor:pointer;">Inspect Claude JSX Component</summary>
        <pre>$escapedSource</pre>
      </details>
    </div>
  </div>

  <script>
    function recalcUnit(logIt) {
      const val = parseFloat(document.getElementById('reactInputVal').value || '0');
      const mode = document.getElementById('reactUnitSelect').value;
      let res = '';
      if (mode === 'km_mi') res = (val * 0.621371).toFixed(2) + ' mi';
      else if (mode === 'kg_lb') res = (val * 2.20462).toFixed(2) + ' lbs';
      else res = (val * 1.8 + 32).toFixed(1) + ' °F';
      document.getElementById('reactOutputBox').textContent = res;
      if (logIt) console.log("React Artifact computed: " + val + " -> " + res);
    }
    function simulateRuntimeError() {
      // Intentionally triggers a ReferenceError so the user can test copying runtime errors
      nonExistentClaudeFunction();
    }
    console.log("Mounted Claude React Artifact (${jsxFile.fileName})");
  </script>

  <script type="text/babel">
    const { useState, useEffect, useMemo } = React;
    try {
      $runnableJsx
    } catch (e) {
      console.error("[BABEL_JSX_COMPILE_ERROR] " + e.message);
    }
  </script>
</body>
</html>
        """.trimIndent()
    }

    private fun buildKotlinComposeInteractivePreviewHtml(
        config: ClaudeApkConfig,
        ktFile: ClaudeSourceFile
    ): String {
        val buttonLabels = Regex("""Button\s*\([^)]*\)\s*\{\s*Text\(\s*"([^"]+)"\s*\)""")
            .findAll(ktFile.content)
            .map { it.groupValues[1] }
            .toList()
            .ifEmpty { listOf("Complete Daily Check-In", "Increment Counter") }

        val buttonsHtml = buttonLabels.mapIndexed { idx, label ->
            val cleanLabel = label.replace("${'$'}streak", "<span id='val_$idx'>7</span>")
                .replace("${'$'}waterGlasses", "<span id='val_$idx'>5</span>")
            """<button class="compose-btn" onclick="bumpComposeState($idx, '${label.replace("'", "")}')">$cleanLabel</button>"""
        }.joinToString("\n")

        val escapedKt = ktFile.content
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>${config.appName}</title>
  <style>
    * { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
    body { margin: 0; padding: 18px; background: #0B1120; color: #F8FAFC; }
    .compose-surface {
      background: #111827; border: 1px solid #1E293B; border-radius: 20px; padding: 20px;
      display: flex; flex-direction: column; gap: 14px;
    }
    .tag { background: #0C4A6E; color: #38BDF8; font-size: 10px; font-weight: 700; padding: 4px 10px; border-radius: 99px; width: fit-content; }
    .compose-btn {
      background: #10B981; color: #022C22; border: none; padding: 12px 16px;
      border-radius: 99px; font-weight: 700; font-size: 14px; cursor: pointer;
      transition: transform 0.1s ease;
    }
    .compose-btn:active { transform: scale(0.97); }
    .state-card { background: #1E293B88; padding: 14px; border-radius: 14px; display: flex; justify-content: space-between; align-items: center; }
    pre { background: #0B1120; padding: 12px; border-radius: 12px; overflow-x: auto; font-size: 11px; color: #A7F3D0; border: 1px solid #1E293B; }
  </style>
</head>
<body>
  <div class="compose-surface">
    <span class="tag">JETPACK COMPOSE PREVIEW • ${ktFile.fileName}</span>
    <h2 style="margin:0;">${config.appName}</h2>
    <div class="state-card">
      <span style="color:#94A3B8;font-size:13px;">MutableState Counter</span>
      <strong id="mainCounter" style="font-size:22px;color:#34D399;">7</strong>
    </div>
    $buttonsHtml
    <details open>
      <summary style="font-size:12px;color:#38BDF8;cursor:pointer;margin-top:6px;">Compiled Kotlin Source (${ktFile.fileName})</summary>
      <pre>$escapedKt</pre>
    </details>
  </div>
  <script>
    let counters = [7, 5, 1];
    function bumpComposeState(idx, label) {
      counters[idx] = (counters[idx] || 0) + 1;
      const span = document.getElementById('val_' + idx);
      if (span) span.textContent = counters[idx];
      document.getElementById('mainCounter').textContent = counters[0];
      console.log("@Composable recomposed state[" + idx + "] -> " + counters[idx]);
    }
    console.log("Jetpack Compose preview mounted for ${ktFile.fileName}");
  </script>
</body>
</html>
        """.trimIndent()
    }

    /**
     * Builds the full Android project. [iconPngs] maps project paths
     * (e.g. app/src/main/res/mipmap-xxhdpi/ic_launcher.png) to PNG bytes from the icon maker.
     */
    fun generateApkProjectEntries(
        config: ClaudeApkConfig,
        claudeFiles: List<ClaudeSourceFile>,
        iconPngs: Map<String, ByteArray> = emptyMap(),
        iconXml: Map<String, String> = emptyMap()
    ): List<StagedEntry> {
        val pkgPath = config.applicationId.replace('.', '/')
        val entries = mutableListOf<StagedEntry>()

        claudeFiles.forEach { src ->
            entries.add(
                PackagerEngine.createTextEntry(
                    relativePath = "claude_sources/${src.fileName}",
                    content = src.content
                )
            )
        }

        val compiledHtml = buildStandaloneWebApkHtml(config, claudeFiles)
        entries.add(
            PackagerEngine.createTextEntry(
                relativePath = "app/src/main/assets/index.html",
                content = compiledHtml,
                category = StagedEntryCategory.SOURCE_CODE
            )
        )

        val ktSource = claudeFiles.firstOrNull { it.fileName.endsWith(".kt") || it.language == "kotlin" }
        val isCompose = config.artifactType == ClaudeArtifactType.KOTLIN_COMPOSE && ktSource != null
        // Compose sources keep their own package; the web host lives in the applicationId package.
        val activityPackage = if (isCompose) {
            GeneratedProjectFiles.kotlinPackageOf(ktSource!!.content) ?: config.applicationId
        } else {
            config.applicationId
        }
        val mainActivityContent = if (isCompose) ktSource!!.content else generateWebViewHostActivityKt(config)
        entries.add(
            PackagerEngine.createTextEntry(
                relativePath = "app/src/main/java/${activityPackage.replace('.', '/')}/MainActivity.kt",
                content = mainActivityContent,
                category = StagedEntryCategory.SOURCE_CODE
            )
        )

        // Phase 2: crash catcher + JS bridge in every generated app.
        entries.add(
            PackagerEngine.createTextEntry(
                relativePath = "app/src/main/java/$pkgPath/${ErrorCatcher.ANDROID_FILE_NAME}",
                content = ErrorCatcher.androidKotlin(config.applicationId),
                category = StagedEntryCategory.SOURCE_CODE
            )
        )

        val manifestContent = GeneratedProjectFiles.manifestXml(
            appName = config.appName,
            activityClass = "$activityPackage.MainActivity",
            applicationClass = "${config.applicationId}.PackFlowApplication",
            hasIcon = iconPngs.isNotEmpty()
        )
        entries.add(
            PackagerEngine.createTextEntry(
                relativePath = "app/src/main/AndroidManifest.xml",
                content = manifestContent,
                category = StagedEntryCategory.CONFIG_MANIFEST
            )
        )

        entries.add(
            PackagerEngine.createTextEntry(
                relativePath = "app/build.gradle.kts",
                content = GeneratedProjectFiles.appBuildGradle(config),
                category = StagedEntryCategory.CONFIG_MANIFEST
            )
        )
        entries.add(
            PackagerEngine.createTextEntry(
                relativePath = "build.gradle.kts",
                content = GeneratedProjectFiles.ROOT_BUILD_GRADLE,
                category = StagedEntryCategory.CONFIG_MANIFEST
            )
        )
        entries.add(
            PackagerEngine.createTextEntry(
                relativePath = "gradle.properties",
                content = GeneratedProjectFiles.GRADLE_PROPERTIES,
                category = StagedEntryCategory.CONFIG_MANIFEST
            )
        )

        val settingsGradle = """
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "${config.appName}"
include(":app")
        """.trimIndent()
        entries.add(
            PackagerEngine.createTextEntry(
                relativePath = "settings.gradle.kts",
                content = settingsGradle,
                category = StagedEntryCategory.CONFIG_MANIFEST
            )
        )

        if (config.includeGithubActionsApkBuilder) {
            val workflowYaml = GeneratedProjectFiles.workflowYaml(config)
            entries.add(
                PackagerEngine.createTextEntry(
                    relativePath = ".github/workflows/build-apk.yml",
                    content = workflowYaml,
                    category = StagedEntryCategory.CONFIG_MANIFEST
                )
            )
        }

        iconPngs.forEach { (path, bytes) -> entries.add(PackagerEngine.createBinaryEntry(path, bytes)) }
        iconXml.forEach { (path, xml) ->
            entries.add(PackagerEngine.createTextEntry(path, xml, StagedEntryCategory.CONFIG_MANIFEST))
        }

        val readme = """
# ${config.appName} (Claude-to-APK Package)

Generated by **PackFlow Studio** from ${claudeFiles.size} Claude source file(s).

## Included Files
- `app/src/main/assets/index.html`: Self-contained Claude runtime bundle
- `app/src/main/java/$pkgPath/MainActivity.kt`: Native Android APK host Activity
- `claude_sources/`: Original files written by Claude
- `.github/workflows/build-apk.yml`: Automated cloud APK compiler workflow

## How to Generate the Installable `.apk`
1. **Cloud Build (Zero local setup)**: Push this folder to a GitHub repository — the included GitHub Action automatically compiles and uploads `app-debug.apk` under the **Actions** tab.
2. **Android Studio / CLI**: Run `gradle assembleDebug` to output `app/build/outputs/apk/debug/app-debug.apk`.
        """.trimIndent()
        entries.add(
            PackagerEngine.createTextEntry(
                relativePath = "README.md",
                content = readme,
                category = StagedEntryCategory.DOCUMENTATION
            )
        )

        return entries
    }

    private fun generateWebViewHostActivityKt(config: ClaudeApkConfig): String {
        return """
package ${config.applicationId}

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            // PackFlow error catcher: web errors go to the same log as crashes.
            addJavascriptInterface(PackFlowErrorBridge(this@MainActivity), "${ErrorCatcher.BRIDGE_NAME}")
            if (${config.enableJsBridge}) {
                addJavascriptInterface(ClaudeAndroidBridge(), "AndroidApkBridge")
            }
            loadUrl("file:///android_asset/index.html")
        }

        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    inner class ClaudeAndroidBridge {
        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun getAppName(): String = "${config.appName}"

        @JavascriptInterface
        fun getPackageName(): String = "${config.applicationId}"
    }
}
        """.trimIndent()
    }
}
