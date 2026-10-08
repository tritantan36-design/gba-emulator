package dev.gbalite.input

import dev.gbalite.core.GbaButton

class InputRouter(private val send: (GbaButton, Boolean) -> Unit) {
    private val sources=mutableMapOf<String,Set<GbaButton>>()
    @Synchronized fun update(source: String, buttons: Set<GbaButton>) {
        val old=sources.values.flatten().toSet()
        if(buttons.isEmpty()) sources.remove(source) else sources[source]=buttons
        val new=sources.values.flatten().toSet()
        (old-new).forEach { send(it,false) }; (new-old).forEach { send(it,true) }
    }
    @Synchronized fun setButton(button: GbaButton, down: Boolean) {
        val keys=sources["legacy"].orEmpty()
        update("legacy",if(down) keys+button else keys-button)
    }
    @Synchronized fun releaseSource(source: String) { update(source,emptySet()) }
    @Synchronized fun releasePrefix(prefix: String) { sources.keys.filter { it.startsWith(prefix) }.toList().forEach(::releaseSource) }
    @Synchronized fun releaseAll() {
        sources.values.flatten().toSet().forEach { send(it,false) }; sources.clear()
    }
}
