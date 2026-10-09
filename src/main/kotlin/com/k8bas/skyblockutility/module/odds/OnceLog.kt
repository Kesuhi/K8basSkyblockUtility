package com.k8bas.skyblockutility.module.odds

import java.util.function.Consumer

/** Writes each key's line once per session (REQ-ODDS-11: an unknown name is logged once). Safe from any thread. */
class OnceLog(private val sink: Consumer<String>) {
	private val seen = java.util.Collections.synchronizedSet(HashSet<String>())

	/** Writes [line] the first time [key] is seen this session. */
	fun log(key: String, line: String) {
		if (seen.add(key)) {
			sink.accept(line)
		}
	}
}
