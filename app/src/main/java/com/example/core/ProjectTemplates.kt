package com.example.core

/** Starting points for new projects. */
data class ProjectTemplate(
    val id: String,
    val title: String,
    val description: String,
    /** Project-relative path -> bytes, for a game called `name`. */
    val files: (name: String) -> Map<String, ByteArray>
)

object ProjectTemplates {
    val BLANK = ProjectTemplate(
        id = "blank",
        title = "Blank canvas",
        description = "An empty HTML5 canvas game (code/index.html + main.js)."
    ) { name ->
        mapOf(
            "code/index.html" to ProjectLayout.blankIndexHtml(name).toByteArray(),
            "code/main.js" to ProjectLayout.BLANK_MAIN_JS.toByteArray()
        )
    }

    val all: List<ProjectTemplate> by lazy { listOf(BLANK, TacticsTemplate.TEMPLATE) }

    fun byId(id: String): ProjectTemplate = all.firstOrNull { it.id == id } ?: BLANK
}
