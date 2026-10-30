package com.projectcat.jarvis.memory

/** Formats only user-managed facts and notes; conversation history and audit logs are excluded. */
object LocalMemoryExportPolicy {
    const val MAX_EXPORT_CHARS = 250_000

    fun format(facts: List<String>, notes: List<LocalNote>): String {
        val out = StringBuilder("Jarvis local memory export\n")
        out.append("Only explicit memory facts and saved notes are included. Chat history and action logs are excluded.\n\n")
        out.append("MEMORY FACTS\n")
        if (facts.isEmpty()) out.append("(none)\n")
        facts.take(50).forEachIndexed { index, fact ->
            out.append(index + 1).append(". ").append(fact).append('\n')
        }
        out.append("\nSAVED NOTES\n")
        if (notes.isEmpty()) out.append("(none)\n")
        notes.take(200).forEach { note ->
            out.append("#").append(note.id).append(" — ").append(note.text).append('\n')
        }
        return out.toString().take(MAX_EXPORT_CHARS)
    }
}
