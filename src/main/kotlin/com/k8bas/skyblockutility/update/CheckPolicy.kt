package com.k8bas.skyblockutility.update

/**
 * When the updater may send a request, and how a response changes that (REQ-UPD-04, REQ-UPD-09).
 * Pure functions of the state and a clock reading, so every rule is testable with a fake clock.
 * Public, not `internal`, so the Java tests keep calling the plain names.
 */
object CheckPolicy {
	const val SECOND: Long = 1000
	const val MINUTE: Long = 60 * SECOND
	const val HOUR: Long = 60 * MINUTE
	const val DAY: Long = 24 * HOUR

	/** After a 200 or 304, the next automatic request waits at least this long. */
	const val INTERVAL: Long = 6 * HOUR
	const val MAX_AUTOMATIC_PER_DAY: Int = 4
	const val MIN_WAIT: Long = MINUTE
	const val MAX_WAIT: Long = DAY
	const val MAX_DOUBLING_WAIT: Long = HOUR

	/** Whether an automatic check may send a request now. A last success in the future (a wrong
	 *  clock) counts as due, so the clock can neither disable checks nor flood them. */
	@JvmStatic
	fun automaticDue(state: UpdateState, now: Long): Boolean {
		if (waiting(state, now) || automaticInLastDay(state, now) >= MAX_AUTOMATIC_PER_DAY) {
			return false
		}
		return state.lastSuccessMs > now || now - state.lastSuccessMs >= INTERVAL
	}

	/** Inside a back-off wait. A wait ending more than 24 h from now cannot be one the policy set
	 *  (the clock moved back), so it does not count. */
	@JvmStatic
	fun waiting(state: UpdateState, now: Long): Boolean = state.nextAllowedMs > now && state.nextAllowedMs - now <= MAX_WAIT

	@JvmStatic
	fun automaticInLastDay(state: UpdateState, now: Long): Int = state.automaticRequestsMs.count { it <= now && now - it < DAY }

	/** Notes an automatic request sent now; entries outside the window are dropped. */
	@JvmStatic
	fun recordAutomatic(state: UpdateState, now: Long) {
		state.automaticRequestsMs.removeIf { it > now || now - it >= DAY }
		state.automaticRequestsMs.add(now)
	}

	/** Applies a response: caches the list after a 200, sets the next allowed time after an error. */
	@JvmStatic
	fun apply(state: UpdateState, result: GitHubReleaseSource.Result, now: Long) {
		// `!!`: a null kind throws NullPointerException, as the Java switch did.
		when (result.kind!!) {
			GitHubReleaseSource.Kind.OK -> {
				state.releases = ArrayList(result.releases!!)
				state.etag = result.etag
				succeeded(state, now)
			}
			GitHubReleaseSource.Kind.NOT_MODIFIED -> succeeded(state, now)
			GitHubReleaseSource.Kind.HTTP_ERROR -> when (result.status) {
				403, 429 -> waitFor(state, now, rateLimitWait(state, result.headers!!, now))
				404 -> waitFor(state, now, DAY)
				else -> waitFor(state, now, doubling(state.errorStreak++))
			}
			GitHubReleaseSource.Kind.NO_RESPONSE, GitHubReleaseSource.Kind.MALFORMED, GitHubReleaseSource.Kind.BAD_REDIRECT ->
				waitFor(state, now, doubling(state.errorStreak++))
		}
	}

	private fun succeeded(state: UpdateState, now: Long) {
		state.lastSuccessMs = now
		state.nextAllowedMs = 0
		state.secondaryStreak = 0
		state.errorStreak = 0
	}

	/** retry-after, else the reset time when no requests remain, else the secondary-limit doubling. */
	private fun rateLimitWait(state: UpdateState, headers: Map<String, String>, now: Long): Long {
		number(headers["retry-after"])?.let { return it * SECOND }
		val reset = number(headers["x-ratelimit-reset"])
		if (headers["x-ratelimit-remaining"] == "0" && reset != null) {
			return reset * SECOND - now
		}
		return doubling(state.secondaryStreak++)
	}

	/** 1 min, doubling with each consecutive occurrence, at most 1 h. */
	@JvmStatic
	fun doubling(previous: Int): Long = minOf(MAX_DOUBLING_WAIT, MINUTE shl minOf(previous, 10))

	private fun waitFor(state: UpdateState, now: Long, wait: Long) {
		state.nextAllowedMs = now + wait.coerceIn(MIN_WAIT, MAX_WAIT)
	}

	/** Trimmed as Java's String.trim() does (chars up to U+0020), then parsed as Long.parseLong does. */
	private fun number(value: String?): Long? = value?.trim { it <= ' ' }?.toLongOrNull()
}
