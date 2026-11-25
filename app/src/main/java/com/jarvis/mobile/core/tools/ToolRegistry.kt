package com.jarvis.mobile.core.tools

import android.util.Log

/**
 * Formal tool registry (spec: TOOL REGISTRY).
 * The agent can only call tools registered here; no hallucinated tools exist.
 */
object ToolRegistry {

    private val tools = LinkedHashMap<String, Tool>()
    private val lock = Any()

    fun registerAll(list: List<Tool>) {
        synchronized(lock) {
            require(list.map { it.spec.name }.distinct().size == list.size) { "Duplicate tool names in registration" }
            for (t in list) {
                if (tools.containsKey(t.spec.name)) Log.w(TAG, "Replacing registered tool: ${t.spec.name}")
                tools[t.spec.name] = t
            }
        }
        Log.i(TAG, "Registered ${list.size} tools: ${list.joinToString(",") { it.spec.name }}")
    }

    fun get(name: String): Tool? = synchronized(lock) { tools[name] }

    fun all(): List<Tool> = synchronized(lock) { tools.values.toList() }

    fun names(): List<String> = synchronized(lock) { tools.keys.toList() }

    fun available(): List<Tool> = all().filter { it.available() }

    /** Compact catalog rendered into the planner system prompt. */
    fun catalogPrompt(): String = available().joinToString("\n") { t ->
        val params = t.spec.params.joinToString(", ") { p ->
            "${p.name}:${p.type}${if (!p.required) "?" else ""}"
        }
        "- ${t.spec.name}($params) [${t.spec.risk}] ${t.spec.description}"
    }

    /**
     * Ultra-compact catalog for sub-1B local models: names + required args only,
     * one line per few tools. Saves hundreds of prompt tokens that a tiny model
     * would otherwise spend re-reading instead of answering.
     */
    fun catalogPromptCompact(): String = available().joinToString(" | ") { t ->
        val req = t.spec.params.filter { it.required }.joinToString(",") { "${it.name}:${it.type}" }
        if (req.isBlank()) t.spec.name else "${t.spec.name}($req)"
    }

    private const val TAG = "tools"
}
