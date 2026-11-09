package trashpanda.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Flat TOML shapes for ktoml. Defaults are safety-neutral (0/empty); risk+action parse strictly. */
@Serializable
data class RuleFileDto(
    val schema: Int = 1,
    val neverTouch: List<String> = emptyList(),
    val rule: List<RuleDto> = emptyList(),
)

@Serializable
data class RuleDto(
    val id: String,
    val title: String,
    val explanation: String = "",
    val risk: String = "amber",
    val category: String,
    val patterns: List<String> = emptyList(),
    @SerialName("minAgeDays") val minAgeDays: Long = 0,
    @SerialName("minSizeMB") val minSizeMB: Long = 0,
    @SerialName("maxDepth") val maxDepth: Int = 8,
    val action: String = "recycle",
    val verdict: String = "review",
    @SerialName("dropWhenPaths") val dropWhenPaths: List<String> = emptyList(),
)
