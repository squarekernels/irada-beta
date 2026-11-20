package com.ctxaware.irada.preview

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class PreviewRequest(
    val input: String,
    val locale: String,
    val timeZone: String,
    val defaultReminderTime: String = "09:00"
)

@Serializable
data class ProposedTask(
    val title: String,
    val notes: String?,
    val dueDate: String?,
    val reminderAt: String?,
    val suggested: Boolean,
    val reason: String?
)

@Serializable
data class PlanPreview(
    val input: String,
    val tasks: List<ProposedTask>,
    val isStub: Boolean,
    val message: String,
    val warnings: List<String> = emptyList()
)

class InvalidPreview : Exception()

object PreviewCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(request: PreviewRequest): String = json.encodeToString(request)

    fun decode(body: String, input: String): PlanPreview {
        try {
            val result = json.decodeFromString<PlanPreview>(body)
            require(result.input == input && result.tasks.size in 1..100)
            require(result.message.length <= 2000 && result.warnings.size <= 100)
            require(result.warnings.all { it.isNotBlank() && it.length <= 2000 })
            result.tasks.forEach { task ->
                require(task.title.isNotBlank() && task.title.length <= 200)
                require((task.notes?.length ?: 0) <= 5000 && (task.reason?.length ?: 0) <= 2000)
                task.dueDate?.let { require(it.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))); LocalDate.parse(it) }
                task.reminderAt?.let {
                    require(it.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?")))
                    LocalDateTime.parse(it)
                }
            }
            return result
        } catch (_: Exception) { throw InvalidPreview() }
    }
}
