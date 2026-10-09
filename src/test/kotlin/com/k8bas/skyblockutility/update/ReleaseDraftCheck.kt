package com.k8bas.skyblockutility.update

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.api.VersionParsingException
import net.fabricmc.loader.api.metadata.version.VersionPredicate
import java.io.IOException
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.HexFormat
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.zip.ZipFile

/**
 * The checks on a release draft before it is published (REQ-REL-07, REQ-REL-11, AC-REL-09 [A],
 * T1.17), run through the updater's own parser ([GitHubReleaseSource.parseReleases]) and
 * selection ([CandidateFinder], [AssetSelector]). It lives in the test source set, so it
 * never ships, and in this package, beside the parser it checks.
 *
 * Input is one GitHub release object as the authenticated release list returns it (drafts are
 * invisible to anonymous clients); `scripts/release-check.sh` saves it with `gh api`,
 * so this code never holds a token. Run it with
 * `./gradlew releaseDraftCheck -Pk8bas.draftJson=<file> -Pk8bas.notes=<file>`.
 */
object ReleaseDraftCheck {
	const val MOD_ID: String = "k8bas_skyblock_utility"
	const val TITLE_PREFIX: String = "K8bas Skyblock Utility v"

	/** U+FEFF, the byte order mark; written as an escape so that no editor can drop it unseen. */
	const val BOM: Char = '\uFEFF'
	private val SIDECAR: Pattern = Pattern.compile("([0-9a-f]{64})  ([^\\n]+)\\n")
	private val DIGEST: Pattern = Pattern.compile("sha256:([0-9a-f]{64})")

	/** Below every other SemVer version, so any release counts as newer. */
	private const val LOWEST_VERSION = "0.0.0-0"

	/**
	 * @param draftJson        one release object (from `GET /repos/{owner}/{repo}/releases`)
	 * @param jar              the local jar that was uploaded
	 * @param sidecar          the local `<jar>.sha256` that was uploaded
	 * @param expectedNotes    the CHANGELOG section (`scripts/changelog-lint.sh --extract`)
	 * @param modVersion       the mod version without build metadata, e.g. 1.1.0
	 * @param minecraftVersion the Minecraft version of the jar, e.g. 26.2
	 * @return one human-readable line per problem; empty if the draft may be published
	 */
	@JvmStatic
	fun check(draftJson: String, jar: Path, sidecar: Path, expectedNotes: String?, modVersion: String, minecraftVersion: String): List<String> {
		val problems = ArrayList<String>()
		val version = SemVer.parse(modVersion)
		if (version.isEmpty || modVersion.contains("+")) {
			return java.util.List.of("the mod version '$modVersion' is not a SemVer version without build metadata")
		}
		val jarName = "$MOD_ID-$modVersion+$minecraftVersion.jar"
		val sidecarName = "$jarName.sha256"

		val release: JsonObject
		try {
			val root = JsonParser.parseString(stripBom(draftJson))
			if (!root.isJsonObject) {
				return java.util.List.of("the draft JSON is not one release object (pick the entry from the release list)")
			}
			release = root.asJsonObject
		} catch (e: JsonParseException) {
			return java.util.List.of("the draft JSON cannot be parsed: " + e.message)
		}

		checkReleaseFields(release, modVersion, version.get(), problems)
		checkUpdaterSelection(release, version.get(), minecraftVersion, jarName, problems)
		val assets = checkAssets(release, jarName, sidecarName, problems)
		checkLocalFiles(assets[jarName], assets[sidecarName], jar, sidecar, jarName, sidecarName, problems)
		checkBody(release, expectedNotes, problems)
		checkModJson(jar, "$modVersion+$minecraftVersion", minecraftVersion, problems)
		return java.util.List.copyOf(problems)
	}

	/** Draft, tag, title and pre-release flag (REQ-REL-07). */
	private fun checkReleaseFields(release: JsonObject, modVersion: String, version: SemVer, problems: MutableList<String>) {
		if (!bool(release, "draft")) {
			problems.add("the release is not a draft (draft: false); these checks run before publishing")
		}
		val tag = string(release, "tag_name")
		if ("v$modVersion" != tag) {
			problems.add("tag_name is " + quote(tag) + ", expected 'v" + modVersion + "'")
		}
		val name = string(release, "name")
		if (TITLE_PREFIX + modVersion != name) {
			problems.add("the release title is " + quote(name) + ", expected '" + TITLE_PREFIX + modVersion + "'")
		}
		val prerelease = bool(release, "prerelease")
		if (prerelease != version.isPreRelease) {
			problems.add(
				if (prerelease) "the pre-release flag is set, but $modVersion has no pre-release part"
				else "the pre-release flag is not set, but $modVersion has a pre-release part",
			)
		}
	}

	/** What the updater will make of the release once it is published (REQ-UPD-06, AC-REL-09). */
	private fun checkUpdaterSelection(release: JsonObject, version: SemVer, minecraftVersion: String, jarName: String, problems: MutableList<String>) {
		val list = JsonArray()
		list.add(release)
		val parsed = GitHubReleaseSource.parseReleases(list)
		if (parsed.size != 1) {
			problems.add("the updater skips this release (it needs tag_name and html_url)")
			return
		}
		val draft = parsed[0]
		// The updater ignores drafts; check the release as it will be once published.
		val published = Release(draft.tag(), draft.htmlUrl(), false, draft.prerelease(), draft.assets())
		val channel = if (version.isPreRelease) UpdateChannel.BETA else UpdateChannel.STABLE
		val outcome = CandidateFinder.find(java.util.List.of(published), LOWEST_VERSION, minecraftVersion, channel, Consumer { })
		if (outcome is CandidateFinder.Candidate) {
			if (jarName != outcome.jar.name()) {
				problems.add("the updater picks " + outcome.jar.name() + " instead of " + jarName)
			}
			if (version != outcome.version) {
				problems.add("the updater reads the tag as version " + outcome.version + ", expected " + version)
			}
		} else {
			problems.add(
				"the updater would not offer this release on the " + channel + " channel for Minecraft " + minecraftVersion +
					" (outcome " + outcome.javaClass.simpleName + "); it needs an uploaded " + jarName +
					(if (channel == UpdateChannel.STABLE) " and no pre-release flag" else ""),
			)
		}
	}

	/** Exactly the jar and its sidecar, both uploaded (REQ-REL-07). Returns the assets by name. */
	private fun checkAssets(release: JsonObject, jarName: String, sidecarName: String, problems: MutableList<String>): Map<String, JsonObject> {
		val byName = LinkedHashMap<String, JsonObject>()
		val assets = release.get("assets") as? JsonArray ?: JsonArray()
		for (element in assets) {
			val name = if (element.isJsonObject) string(element.asJsonObject, "name") else null
			if (name == null) {
				problems.add("an asset has no name")
			} else if (byName.putIfAbsent(name, element.asJsonObject) != null) {
				problems.add("the asset $name is attached twice")
			}
		}
		if (!byName.containsKey(jarName)) {
			problems.add("the jar asset $jarName is missing")
		}
		if (!byName.containsKey(sidecarName)) {
			problems.add("the sidecar asset $sidecarName is missing")
		}
		for ((name, asset) in byName) {
			if (name != jarName && name != sidecarName) {
				problems.add(
					"unexpected asset " + name +
						if (name.endsWith(".jar")) ": only the release jar may be attached (no sources jar, no other .jar)"
						else ": only the jar and its .sha256 sidecar are attached",
				)
			}
			val state = string(asset, "state")
			if ("uploaded" != state) {
				problems.add("the asset " + name + " has state " + quote(state) + ", expected 'uploaded' (delete it and upload it again, EC-REL-05)")
			}
		}
		return byName
	}

	/**
	 * The jar asset's API digest equals the local jar's SHA-256, and so does the sidecar's hash, so all
	 * three agree; the sidecar asset's digest equals the local sidecar's; sizes match; the sidecar's
	 * exact bytes (REQ-REL-07, REQ-REL-11, EC-REL-06).
	 */
	private fun checkLocalFiles(
		jarAsset: JsonObject?,
		sidecarAsset: JsonObject?,
		jar: Path,
		sidecar: Path,
		jarName: String,
		sidecarName: String,
		problems: MutableList<String>,
	) {
		if (jarName != fileName(jar)) {
			problems.add("the local jar is named " + fileName(jar) + ", expected " + jarName)
		}
		if (sidecarName != fileName(sidecar)) {
			problems.add("the local sidecar is named " + fileName(sidecar) + ", expected " + sidecarName)
		}
		val jarBytes = read(jar, "jar", problems)
		val sidecarBytes = read(sidecar, "sidecar", problems)
		val jarHex = if (jarBytes == null) null else sha256(jarBytes)

		if (sidecarBytes != null) {
			checkSidecarBytes(sidecarBytes, jarName, jarHex, problems)
		}
		if (jarAsset != null && jarBytes != null) {
			checkDigest("jar", jarAsset, jarHex, jarBytes.size.toLong(), problems)
		}
		if (sidecarAsset != null && sidecarBytes != null) {
			checkDigest("sidecar", sidecarAsset, sha256(sidecarBytes), sidecarBytes.size.toLong(), problems)
		}
	}

	private fun checkSidecarBytes(bytes: ByteArray, jarName: String, jarHex: String?, problems: MutableList<String>) {
		val text = try {
			StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
		} catch (e: CharacterCodingException) {
			problems.add("the sidecar is not valid UTF-8")
			return
		}
		val bom = text.isNotEmpty() && text[0] == BOM
		val innerBom = text.indexOf(BOM, 1)
		val cr = text.indexOf('\r') >= 0
		if (bom) {
			problems.add("the sidecar starts with a UTF-8 BOM (EC-REL-06); use the file ./gradlew build writes")
		}
		if (innerBom >= 0) {
			problems.add("the sidecar has a BOM (U+FEFF) at index $innerBom; use the file ./gradlew build writes")
		}
		if (cr) {
			problems.add("the sidecar has a CR (CRLF line ending, EC-REL-06); use the file ./gradlew build writes")
		}
		val matcher = SIDECAR.matcher(text.replace(BOM.toString(), "").replace("\r", ""))
		if (!matcher.matches()) {
			problems.add("the sidecar is not '<64 lowercase hex><two spaces><jar name><LF>'")
			return
		}
		if (jarName != matcher.group(2)) {
			problems.add(
				"the sidecar names " + matcher.group(2) + ", expected " + jarName +
					" (the asset name with '+', never the %2B form of the download URL, EC-REL-08)",
			)
		}
		if (jarHex != null && jarHex != matcher.group(1)) {
			problems.add("the sidecar's hash " + matcher.group(1) + " differs from the local jar's SHA-256 " + jarHex)
		}
	}

	private fun checkDigest(what: String, asset: JsonObject, localHex: String?, localSize: Long, problems: MutableList<String>) {
		val digest = string(asset, "digest")
		if (digest == null) {
			problems.add("the $what asset has no digest")
		} else {
			val matcher = DIGEST.matcher(digest)
			if (!matcher.matches()) {
				problems.add("the " + what + " asset's digest " + quote(digest) + " is not 'sha256:<64 lowercase hex>'")
			} else if (matcher.group(1) != localHex) {
				problems.add(
					"the " + what + " asset's digest " + digest + " differs from the local " + what + "'s SHA-256 " + localHex +
						" (a different file was uploaded)",
				)
			}
		}
		val size = asset.get("size")
		if (size == null || !size.isJsonPrimitive || !size.asJsonPrimitive.isNumber || size.asLong != localSize) {
			problems.add("the " + what + " asset's size is " + size + " bytes, the local " + what + " has " + localSize)
		}
	}

	/** The body equals the CHANGELOG section; only CRLF and trailing whitespace at the end are ignored. */
	private fun checkBody(release: JsonObject, expectedNotes: String?, problems: MutableList<String>) {
		val body = normaliseNotes(string(release, "body"))
		val expected = normaliseNotes(expectedNotes)
		if (body == expected) {
			return
		}
		// Kotlin's split keeps trailing empty strings, as Java's split(regex, -1) did.
		val got = body.split("\n")
		val want = expected.split("\n")
		var line = 0
		while (line < got.size && line < want.size && got[line] == want[line]) {
			line++
		}
		val gotLine = if (line < got.size) got[line] else null
		val wantLine = if (line < want.size) want[line] else null
		problems.add(
			"the release body differs from the CHANGELOG section at line " + (line + 1) + ": got " + excerpt(gotLine) +
				", expected " + excerpt(wantLine) + firstDifference(gotLine, wantLine),
		)
	}

	/**
	 * Where two lines first differ, with the code points in ASCII, so that a lost en dash or curly
	 * quote stays readable on any console.
	 */
	@JvmStatic
	fun firstDifference(got: String?, want: String?): String {
		if (got == null || want == null) {
			return ""
		}
		var column = 0
		while (column < got.length && column < want.length && got[column] == want[column]) {
			column++
		}
		return " (column " + (column + 1) + ": got " + codePoint(got, column) + ", expected " + codePoint(want, column) + ")"
	}

	/** In the default locale, as Java's String.formatted used (%X prints the same in every locale). */
	private fun codePoint(text: String, index: Int): String =
		if (index < text.length) java.lang.String.format("U+%04X", text.codePointAt(index)) else "the end of the line"

	/** Java's stripTrailing: Character.isWhitespace only. */
	@JvmStatic
	fun normaliseNotes(text: String?): String = text?.replace("\r\n", "\n")?.trimEnd(Character::isWhitespace) ?: ""

	/** id, version and Minecraft range of the jar's fabric.mod.json (REQ-REL-07). */
	private fun checkModJson(jar: Path, expectedVersion: String, minecraftVersion: String, problems: MutableList<String>) {
		if (!Files.isRegularFile(jar)) {
			return
		}
		val modJson: JsonObject = try {
			ZipFile(jar.toFile()).use { zip ->
				val entry = zip.getEntry("fabric.mod.json")
				if (entry == null) {
					problems.add("the jar has no fabric.mod.json")
					return
				}
				zip.getInputStream(entry).use { input ->
					val root = JsonParser.parseString(String(input.readAllBytes(), StandardCharsets.UTF_8))
					if (!root.isJsonObject) {
						problems.add("the jar's fabric.mod.json is not a JSON object")
						return
					}
					root.asJsonObject
				}
			}
		} catch (e: IOException) {
			problems.add("the jar's fabric.mod.json cannot be read: " + e.message)
			return
		} catch (e: JsonParseException) {
			problems.add("the jar's fabric.mod.json cannot be read: " + e.message)
			return
		}
		val id = string(modJson, "id")
		if (MOD_ID != id) {
			problems.add("fabric.mod.json id is " + quote(id) + ", expected '" + MOD_ID + "'")
		}
		val version = string(modJson, "version")
		if (expectedVersion != version) {
			problems.add("fabric.mod.json version is " + quote(version) + ", expected '" + expectedVersion + "'")
		}
		val minecraft = (modJson.get("depends") as? JsonObject)?.get("minecraft")
		val predicates = ArrayList<String>()
		if (minecraft != null && minecraft.isJsonPrimitive) {
			predicates.add(minecraft.asString)
		} else if (minecraft is JsonArray) {
			minecraft.forEach { element -> predicates.add(if (element.isJsonPrimitive) element.asString else element.toString()) }
		}
		if (predicates.isEmpty()) {
			problems.add("fabric.mod.json has no depends.minecraft")
			return
		}
		try {
			val mc = SemanticVersion.parse(minecraftVersion)
			var accepted = false
			for (predicate in predicates) {
				// `or`, not `||`: every predicate is parsed, as Java's |= did.
				accepted = accepted or VersionPredicate.parse(predicate).test(mc)
			}
			if (!accepted) {
				problems.add("fabric.mod.json depends.minecraft $minecraft does not accept Minecraft $minecraftVersion")
			}
		} catch (e: VersionParsingException) {
			problems.add(
				"fabric.mod.json depends.minecraft " + minecraft + " or Minecraft " + minecraftVersion + " cannot be parsed: " + e.message,
			)
		}
	}

	/**
	 * `<draft.json> <jar> <sidecar> <notes> <mod version> <minecraft version>`; exits 0 on
	 * PASS, 1 with one line per problem, 2 on a usage error.
	 */
	@JvmStatic
	fun main(args: Array<String>) {
		System.exit(run(args, System.out, System.err))
	}

	@JvmStatic
	fun run(args: Array<String>, out: PrintStream, err: PrintStream): Int {
		if (args.size != 6) {
			err.println("usage: ReleaseDraftCheck <draft.json> <jar> <sidecar> <notes file> <mod version> <minecraft version>")
			return 2
		}
		val draftJson = readText(Path.of(args[0]), "draft JSON", err)
		val notes = readText(Path.of(args[3]), "notes file", err)
		if (draftJson == null || notes == null) {
			return 2
		}
		val jar = Path.of(args[1])
		val sidecar = Path.of(args[2])
		// A missing local file (say after ./gradlew clean) is an input error: the draft was not checked.
		if (!readable(jar, "jar", err) || !readable(sidecar, "sidecar", err)) {
			return 2
		}
		val problems = check(draftJson, jar, sidecar, notes, args[4], args[5])
		if (problems.isNotEmpty()) {
			err.println("release draft check: FAIL, " + problems.size + " problem(s) with the v" + args[4] + " draft:")
			problems.forEach { problem -> err.println("  - $problem") }
			return 1
		}
		out.println("release draft check: PASS for the v" + args[4] + " draft")
		out.println("  draft, tag, title, pre-release flag, assets, digests, sidecar, body and fabric.mod.json match")
		out.println("  " + fileName(jar) + " sha256:" + sha256(read(jar, "jar", ArrayList())))
		return 0
	}

	/** A UTF-8 input file, or null after one line on `err` that names the file and the reason. */
	private fun readText(file: Path, what: String, err: PrintStream): String? {
		try {
			return Files.readString(file, StandardCharsets.UTF_8)
		} catch (e: CharacterCodingException) {
			err.println(
				"release draft check: cannot read the " + what + " " + file + " as UTF-8 (" + e.javaClass.simpleName +
					"); save it as UTF-8, for example from Git Bash rather than PowerShell",
			)
		} catch (e: IOException) {
			err.println("release draft check: cannot read the " + what + " " + file + " (" + e.javaClass.simpleName + ")")
		}
		return null
	}

	private fun readable(file: Path, what: String, err: PrintStream): Boolean = try {
		Files.readAllBytes(file)
		true
	} catch (e: IOException) {
		err.println(
			"release draft check: cannot read the local " + what + " " + file + " (" + e.javaClass.simpleName +
				"); build it again, the draft was not checked",
		)
		false
	}

	private fun read(file: Path, what: String, problems: MutableList<String>): ByteArray? = try {
		Files.readAllBytes(file)
	} catch (e: IOException) {
		problems.add("the local " + what + " " + file + " cannot be read (" + e.javaClass.simpleName + ")")
		null
	}

	private fun sha256(bytes: ByteArray?): String = try {
		HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
	} catch (e: NoSuchAlgorithmException) {
		throw IllegalStateException("SHA-256 is always available", e)
	}

	private fun fileName(path: Path): String = path.fileName?.toString() ?: path.toString()

	private fun stripBom(text: String): String = if (text.isNotEmpty() && text[0] == BOM) text.substring(1) else text

	private fun string(obj: JsonObject, key: String): String? {
		val value = obj.get(key)
		return if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
	}

	private fun bool(obj: JsonObject, key: String): Boolean {
		val value = obj.get(key)
		return value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean && value.asBoolean
	}

	private fun quote(text: String?): String = if (text == null) "(missing)" else "'$text'"

	private fun excerpt(line: String?): String {
		if (line == null) {
			return "(end of text)"
		}
		return "'" + (if (line.length > 80) line.substring(0, 77) + "..." else line) + "'"
	}
}
