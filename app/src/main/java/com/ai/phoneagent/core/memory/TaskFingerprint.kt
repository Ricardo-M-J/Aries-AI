package com.ai.phoneagent.core.memory

import java.security.MessageDigest

object TaskFingerprint {

    fun generate(task: String): String {
        val normalized = normalize(task)
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(normalized.toByteArray())
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }

    fun normalize(task: String): String {
        var s = task.trim().lowercase()

        val replacements = mapOf(
            "一" to "1", "二" to "2", "两" to "2", "三" to "3", "四" to "4",
            "五" to "5", "六" to "6", "七" to "7", "八" to "8", "九" to "9", "十" to "10",
            "第一个" to "第1个", "第二个" to "第2个", "第三个" to "第3个",
            "第一次" to "第1次", "第二次" to "第2次",
        )
        for ((from, to) in replacements) {
            s = s.replace(from, to)
        }

        s = s.replace(Regex("[\\p{P}\\s]+"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()

        return s
    }

    fun similarity(a: String, b: String): Double {
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return 1.0

        val wordsA = na.split(" ").toSet()
        val wordsB = nb.split(" ").toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0

        val intersection = wordsA.intersect(wordsB).size.toDouble()
        val union = wordsA.union(wordsB).size.toDouble()
        return if (union > 0) intersection / union else 0.0
    }
}
