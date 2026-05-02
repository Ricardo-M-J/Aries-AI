package com.ai.phoneagent.core.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SemanticAction(
    val actionName: String,
    val target: String = "",
    val value: String = "",
    val appContext: String = "",
    val clickX: Int = -1,
    val clickY: Int = -1,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("actionName", actionName)
        put("target", target)
        put("value", value)
        put("appContext", appContext)
        put("clickX", clickX)
        put("clickY", clickY)
    }

    companion object {
        fun fromJson(json: JSONObject): SemanticAction = SemanticAction(
            actionName = json.optString("actionName", ""),
            target = json.optString("target", ""),
            value = json.optString("value", ""),
            appContext = json.optString("appContext", ""),
            clickX = json.optInt("clickX", -1),
            clickY = json.optInt("clickY", -1),
        )
    }
}

data class ActionMemoryEntry(
    val taskDescription: String,
    val fingerprint: String,
    val actions: List<SemanticAction>,
    val createdAt: Long = System.currentTimeMillis(),
    val successCount: Int = 0,
    val failCount: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("taskDescription", taskDescription)
        put("fingerprint", fingerprint)
        put("actions", JSONArray().apply { actions.forEach { put(it.toJson()) } })
        put("createdAt", createdAt)
        put("successCount", successCount)
        put("failCount", failCount)
    }

    companion object {
        fun fromJson(json: JSONObject): ActionMemoryEntry = ActionMemoryEntry(
            taskDescription = json.optString("taskDescription", ""),
            fingerprint = json.optString("fingerprint", ""),
            actions = json.optJSONArray("actions")?.let { arr ->
                (0 until arr.length()).map { SemanticAction.fromJson(arr.getJSONObject(it)) }
            }.orEmpty(),
            createdAt = json.optLong("createdAt", 0L),
            successCount = json.optInt("successCount", 0),
            failCount = json.optInt("failCount", 0),
        )
    }
}

class ActionMemory(context: Context) {

    private val prefs = context.getSharedPreferences("action_memory", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MEMORIES = "memories_json"
        private const val MAX_MEMORIES = 50
        private const val SIMILARITY_THRESHOLD = 0.75
    }

    fun remember(task: String, actions: List<SemanticAction>): ActionMemoryEntry {
        val fingerprint = TaskFingerprint.generate(task)
        val entry = ActionMemoryEntry(
            taskDescription = task,
            fingerprint = fingerprint,
            actions = actions,
        )
        saveEntry(entry)
        return entry
    }

    fun recall(task: String): ActionMemoryEntry? {
        val fingerprint = TaskFingerprint.generate(task)

        val exact = loadEntries().find { it.fingerprint == fingerprint }
        if (exact != null) return exact

        val normalized = TaskFingerprint.normalize(task)
        var bestMatch: ActionMemoryEntry? = null
        var bestSimilarity = 0.0

        for (entry in loadEntries()) {
            val sim = TaskFingerprint.similarity(normalized, entry.taskDescription)
            if (sim > bestSimilarity && sim >= SIMILARITY_THRESHOLD) {
                bestSimilarity = sim
                bestMatch = entry
            }
        }

        return bestMatch
    }

    fun recordSuccess(fingerprint: String) {
        val entries = loadEntries().toMutableList()
        val idx = entries.indexOfFirst { it.fingerprint == fingerprint }
        if (idx >= 0) {
            entries[idx] = entries[idx].copy(successCount = entries[idx].successCount + 1)
            saveAllEntries(entries)
        }
    }

    fun recordFailure(fingerprint: String) {
        val entries = loadEntries().toMutableList()
        val idx = entries.indexOfFirst { it.fingerprint == fingerprint }
        if (idx >= 0) {
            entries[idx] = entries[idx].copy(failCount = entries[idx].failCount + 1)
            if (entries[idx].failCount >= 3) {
                entries.removeAt(idx)
            }
            saveAllEntries(entries)
        }
    }

    fun getAll(): List<ActionMemoryEntry> = loadEntries()

    fun forget(fingerprint: String) {
        val entries = loadEntries().toMutableList()
        entries.removeAll { it.fingerprint == fingerprint }
        saveAllEntries(entries)
    }

    fun clear() {
        prefs.edit().remove(KEY_MEMORIES).apply()
    }

    private fun saveEntry(entry: ActionMemoryEntry) {
        val entries = loadEntries().toMutableList()
        val existingIdx = entries.indexOfFirst { it.fingerprint == entry.fingerprint }
        if (existingIdx >= 0) {
            entries[existingIdx] = entry
        } else {
            entries.add(entry)
            if (entries.size > MAX_MEMORIES) {
                entries.sortBy { it.createdAt }
                entries.removeAt(0)
            }
        }
        saveAllEntries(entries)
    }

    private fun loadEntries(): List<ActionMemoryEntry> {
        val json = prefs.getString(KEY_MEMORIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { ActionMemoryEntry.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAllEntries(entries: List<ActionMemoryEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_MEMORIES, arr.toString()).apply()
    }
}
