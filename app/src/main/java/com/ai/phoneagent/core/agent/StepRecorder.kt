package com.ai.phoneagent.core.agent

data class StepRecord(
    val stepNumber: Int,
    val actionName: String,
    val displayName: String,
    val actionDetails: Map<String, String>,
    val success: Boolean,
    val thinking: String?,
    val appContext: String,
    val timestamp: Long = System.currentTimeMillis(),
    val skipped: Boolean = false,
    val skipReason: String? = null,
    val clickX: Int = -1,
    val clickY: Int = -1,
)

class StepRecorder {
    private val steps = mutableListOf<StepRecord>()
    private val taskStartTime = System.currentTimeMillis()

    private val fraudKeywords = listOf(
        "银行卡", "信用卡", "储蓄卡", "验证码", "转账", "汇款",
        "安全账户", "中奖", "奖金", "红包", "返利", "退款",
        "社保卡", "身份证号", "密码", "密码器", "U盾",
        "贷款", "额度", "逾期", "催收", "法院", "公安",
        "涉嫌", "犯罪", "冻结", "解冻", "保证金",
        "刷单", "兼职", "日赚", "稳赚",
    )

    fun detectFraud(): List<String> {
        val warnings = mutableListOf<String>()
        val allText = steps.joinToString(" ") { step ->
            "${step.displayName} ${step.thinking.orEmpty()} ${step.actionDetails.values.joinToString(" ")}"
        }
        for (keyword in fraudKeywords) {
            if (allText.contains(keyword)) {
                warnings.add(keyword)
            }
        }
        return warnings
    }

    fun record(step: StepRecord) {
        steps.add(step)
    }

    fun recordSkip(stepNumber: Int, actionName: String, reason: String, appContext: String) {
        steps.add(
            StepRecord(
                stepNumber = stepNumber,
                actionName = actionName,
                displayName = actionName,
                actionDetails = emptyMap(),
                success = true,
                thinking = null,
                appContext = appContext,
                skipped = true,
                skipReason = reason,
            )
        )
    }

    fun getSteps(): List<StepRecord> = steps.toList()

    fun getStepCount(): Int = steps.size

    fun getSuccessfulCount(): Int = steps.count { it.success && !it.skipped }

    fun getTaskDurationMs(): Long = System.currentTimeMillis() - taskStartTime

    fun generateSummary(taskName: String): String {
        val sb = StringBuilder()
        val durationSec = (getTaskDurationMs() / 1000.0).let { "%.1f".format(it) }
        sb.appendLine("=== 任务执行摘要 ===")
        sb.appendLine("任务: $taskName")
        sb.appendLine("耗时: ${durationSec}秒 | 总步骤: ${steps.size}")
        sb.appendLine()

        var prevApp = ""
        for (step in steps) {
            if (step.skipped) {
                sb.appendLine("  [Step ${step.stepNumber}] ⏭️ 跳过: ${step.skipReason}")
                continue
            }

            val appChange = if (step.appContext.isNotBlank() && step.appContext != prevApp) {
                prevApp = step.appContext
                " [${step.appContext.substringAfterLast('.')}]"
            } else {
                ""
            }

            val status = if (step.success) "✅" else "❌"
            sb.appendLine("  [Step ${step.stepNumber}]$appChange $status ${step.displayName}")
        }

        // 反诈检测
        val fraudHits = detectFraud()
        if (fraudHits.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("⚠️ 安全风险提示：")
            sb.appendLine("  检测到以下敏感关键词: ${fraudHits.joinToString("、")}")
            sb.appendLine("  请注意核实操作对象的真实性，谨防诈骗！")
        }

        return sb.toString().trimEnd()
    }

    fun generateOralSummary(taskName: String): String {
        val durationSec = (getTaskDurationMs() / 1000.0).toInt()
        val successSteps = getSuccessfulCount()
        val totalSteps = steps.size

        val actionSummary = steps.filter { !it.skipped }
            .groupBy { it.actionName }
            .map { (name, group) -> "${group.size}次$name" }
            .joinToString("、")

        return "任务「$taskName」已完成，" +
                "共执行了${totalSteps}个步骤，" +
                (if (actionSummary.isNotBlank()) "包括$actionSummary，" else "") +
                "成功${successSteps}个，" +
                "耗时约${durationSec}秒。"
    }

    fun generateCriticalStepNarration(currentStepAction: String, taskText: String = ""): String {
        val completedSteps = steps.filter { !it.skipped && it.success }
        val sb = StringBuilder()

        if (completedSteps.isNotEmpty()) {
            sb.append("到目前为止，我已帮您完成以下操作：")
            for ((idx, step) in completedSteps.withIndex()) {
                val connector = when (idx) {
                    0 -> "首先"
                    1 -> "然后"
                    2 -> "接着"
                    else -> "再"
                }
                sb.append("${connector}${step.displayName}")
                if (idx < completedSteps.size - 1) sb.append("，")
                else sb.append("。")
            }
        } else {
            sb.append("这是第一个操作。")
        }

        sb.append("接下来将执行：${currentStepAction}。")

        val allCheckText = buildString {
            append(taskText)
            append(" ")
            append(currentStepAction)
            append(" ")
            for (step in completedSteps) {
                append(step.displayName)
                append(" ")
                append(step.thinking.orEmpty())
                append(" ")
                append(step.actionDetails.values.joinToString(" "))
                append(" ")
            }
        }
        val riskHits = mutableListOf<String>()
        for (keyword in fraudKeywords) {
            if (allCheckText.contains(keyword, ignoreCase = true)) {
                riskHits.add(keyword)
            }
        }
        if (riskHits.isNotEmpty()) {
            sb.append("⚠️ 警告：检测到敏感关键词「${riskHits.joinToString("、")}」，请注意安全！")
        }

        return sb.toString()
    }

    fun toShareableText(taskName: String): String {
        val sb = StringBuilder()
        sb.appendLine("任务: $taskName")
        sb.appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(taskStartTime))}")
        sb.appendLine("总步骤: ${steps.size} | 成功: ${getSuccessfulCount()}")
        sb.appendLine()
        for (step in steps) {
            val status = if (step.skipped) "SKIP" else if (step.success) "OK" else "FAIL"
            sb.appendLine("[${step.stepNumber}] $status | ${step.displayName} | app=${step.appContext}")
            if (!step.thinking.isNullOrBlank()) {
                sb.appendLine("    思考: ${step.thinking.take(120)}")
            }
        }
        return sb.toString().trimEnd()
    }

    fun clear() {
        steps.clear()
    }
}
