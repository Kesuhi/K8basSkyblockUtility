package com.k8bas.skyblockutility.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The checks on a release draft before it is published (REQ-REL-07, REQ-REL-11, AC-REL-09 [A],
 * T1.17), run through the updater's own parser ({@link GitHubReleaseSource#parseReleases}) and
 * selection ({@link CandidateFinder}, {@link AssetSelector}). It lives in the test source set, so it
 * never ships, and in this package, so it can use the package-private parser.
 *
 * <p>Input is one GitHub release object as the authenticated release list returns it (drafts are
 * invisible to anonymous clients); {@code scripts/release-check.sh} saves it with {@code gh api},
 * so this code never holds a token. Run it with
 * {@code ./gradlew releaseDraftCheck -Pk8bas.draftJson=<file> -Pk8bas.notes=<file>}.
 */
public final class ReleaseDraftCheck {
	static final String MOD_ID = "k8bas_skyblock_utility";
	static final String TITLE_PREFIX = "K8bas Skyblock Utility v";
	/** U+FEFF, the byte order mark; written as an escape so that no editor can drop it unseen. */
	static final char BOM = '\uFEFF';
	private static final Pattern SIDECAR = Pattern.compile("([0-9a-f]{64})  ([^\\n]+)\\n");
	private static final Pattern DIGEST = Pattern.compile("sha256:([0-9a-f]{64})");
	/** Below every other SemVer version, so any release counts as newer. */
	private static final String LOWEST_VERSION = "0.0.0-0";

	private ReleaseDraftCheck() {
	}

	/**
	 * @param draftJson        one release object (from {@code GET /repos/{owner}/{repo}/releases})
	 * @param jar              the local jar that was uploaded
	 * @param sidecar          the local {@code <jar>.sha256} that was uploaded
	 * @param expectedNotes    the CHANGELOG section ({@code scripts/changelog-lint.sh --extract})
	 * @param modVersion       the mod version without build metadata, e.g. 1.1.0
	 * @param minecraftVersion the Minecraft version of the jar, e.g. 26.2
	 * @return one human-readable line per problem; empty if the draft may be published
	 */
	public static List<String> check(String draftJson, Path jar, Path sidecar, String expectedNotes, String modVersion,
			String minecraftVersion) {
		List<String> problems = new ArrayList<>();
		Optional<SemVer> version = SemVer.parse(modVersion);
		if (version.isEmpty() || modVersion.contains("+")) {
			return List.of("the mod version '" + modVersion + "' is not a SemVer version without build metadata");
		}
		String jarName = MOD_ID + "-" + modVersion + "+" + minecraftVersion + ".jar";
		String sidecarName = jarName + ".sha256";

		JsonObject release;
		try {
			JsonElement root = JsonParser.parseString(stripBom(draftJson));
			if (!root.isJsonObject()) {
				return List.of("the draft JSON is not one release object (pick the entry from the release list)");
			}
			release = root.getAsJsonObject();
		} catch (JsonParseException e) {
			return List.of("the draft JSON cannot be parsed: " + e.getMessage());
		}

		checkReleaseFields(release, modVersion, version.get(), problems);
		checkUpdaterSelection(release, version.get(), minecraftVersion, jarName, problems);
		Map<String, JsonObject> assets = checkAssets(release, jarName, sidecarName, problems);
		checkLocalFiles(assets.get(jarName), assets.get(sidecarName), jar, sidecar, jarName, sidecarName, problems);
		checkBody(release, expectedNotes, problems);
		checkModJson(jar, modVersion + "+" + minecraftVersion, minecraftVersion, problems);
		return List.copyOf(problems);
	}

	/** Draft, tag, title and pre-release flag (REQ-REL-07). */
	private static void checkReleaseFields(JsonObject release, String modVersion, SemVer version, List<String> problems) {
		if (!bool(release, "draft")) {
			problems.add("the release is not a draft (draft: false); these checks run before publishing");
		}
		String tag = string(release, "tag_name");
		if (!("v" + modVersion).equals(tag)) {
			problems.add("tag_name is " + quote(tag) + ", expected 'v" + modVersion + "'");
		}
		String name = string(release, "name");
		if (!(TITLE_PREFIX + modVersion).equals(name)) {
			problems.add("the release title is " + quote(name) + ", expected '" + TITLE_PREFIX + modVersion + "'");
		}
		boolean prerelease = bool(release, "prerelease");
		if (prerelease != version.isPreRelease()) {
			problems.add(prerelease
					? "the pre-release flag is set, but " + modVersion + " has no pre-release part"
					: "the pre-release flag is not set, but " + modVersion + " has a pre-release part");
		}
	}

	/** What the updater will make of the release once it is published (REQ-UPD-06, AC-REL-09). */
	private static void checkUpdaterSelection(JsonObject release, SemVer version, String minecraftVersion, String jarName,
			List<String> problems) {
		JsonArray list = new JsonArray();
		list.add(release);
		List<Release> parsed = GitHubReleaseSource.parseReleases(list);
		if (parsed.size() != 1) {
			problems.add("the updater skips this release (it needs tag_name and html_url)");
			return;
		}
		Release draft = parsed.getFirst();
		// The updater ignores drafts; check the release as it will be once published.
		Release published = new Release(draft.tag(), draft.htmlUrl(), false, draft.prerelease(), draft.assets());
		UpdateChannel channel = version.isPreRelease() ? UpdateChannel.BETA : UpdateChannel.STABLE;
		CandidateFinder.Outcome outcome = CandidateFinder.find(List.of(published), LOWEST_VERSION, minecraftVersion, channel, line -> {
		});
		if (outcome instanceof CandidateFinder.Candidate candidate) {
			if (!jarName.equals(candidate.jar().name())) {
				problems.add("the updater picks " + candidate.jar().name() + " instead of " + jarName);
			}
			if (!version.equals(candidate.version())) {
				problems.add("the updater reads the tag as version " + candidate.version() + ", expected " + version);
			}
		} else {
			problems.add("the updater would not offer this release on the " + channel + " channel for Minecraft " + minecraftVersion
					+ " (outcome " + outcome.getClass().getSimpleName() + "); it needs an uploaded " + jarName
					+ (channel == UpdateChannel.STABLE ? " and no pre-release flag" : ""));
		}
	}

	/** Exactly the jar and its sidecar, both uploaded (REQ-REL-07). Returns the assets by name. */
	private static Map<String, JsonObject> checkAssets(JsonObject release, String jarName, String sidecarName, List<String> problems) {
		Map<String, JsonObject> byName = new LinkedHashMap<>();
		JsonArray assets = release.get("assets") instanceof JsonArray array ? array : new JsonArray();
		for (JsonElement element : assets) {
			String name = element.isJsonObject() ? string(element.getAsJsonObject(), "name") : null;
			if (name == null) {
				problems.add("an asset has no name");
			} else if (byName.putIfAbsent(name, element.getAsJsonObject()) != null) {
				problems.add("the asset " + name + " is attached twice");
			}
		}
		if (!byName.containsKey(jarName)) {
			problems.add("the jar asset " + jarName + " is missing");
		}
		if (!byName.containsKey(sidecarName)) {
			problems.add("the sidecar asset " + sidecarName + " is missing");
		}
		for (Map.Entry<String, JsonObject> entry : byName.entrySet()) {
			String name = entry.getKey();
			if (!name.equals(jarName) && !name.equals(sidecarName)) {
				problems.add("unexpected asset " + name + (name.endsWith(".jar")
						? ": only the release jar may be attached (no sources jar, no other .jar)"
						: ": only the jar and its .sha256 sidecar are attached"));
			}
			String state = string(entry.getValue(), "state");
			if (!"uploaded".equals(state)) {
				problems.add("the asset " + name + " has state " + quote(state)
						+ ", expected 'uploaded' (delete it and upload it again, EC-REL-05)");
			}
		}
		return byName;
	}

	/**
	 * The jar asset's API digest equals the local jar's SHA-256, and so does the sidecar's hash, so all
	 * three agree; the sidecar asset's digest equals the local sidecar's; sizes match; the sidecar's
	 * exact bytes (REQ-REL-07, REQ-REL-11, EC-REL-06).
	 */
	private static void checkLocalFiles(JsonObject jarAsset, JsonObject sidecarAsset, Path jar, Path sidecar, String jarName,
			String sidecarName, List<String> problems) {
		if (!jarName.equals(fileName(jar))) {
			problems.add("the local jar is named " + fileName(jar) + ", expected " + jarName);
		}
		if (!sidecarName.equals(fileName(sidecar))) {
			problems.add("the local sidecar is named " + fileName(sidecar) + ", expected " + sidecarName);
		}
		byte[] jarBytes = read(jar, "jar", problems);
		byte[] sidecarBytes = read(sidecar, "sidecar", problems);
		String jarHex = jarBytes == null ? null : sha256(jarBytes);

		if (sidecarBytes != null) {
			checkSidecarBytes(sidecarBytes, jarName, jarHex, problems);
		}
		if (jarAsset != null && jarBytes != null) {
			checkDigest("jar", jarAsset, jarHex, jarBytes.length, problems);
		}
		if (sidecarAsset != null && sidecarBytes != null) {
			checkDigest("sidecar", sidecarAsset, sha256(sidecarBytes), sidecarBytes.length, problems);
		}
	}

	private static void checkSidecarBytes(byte[] bytes, String jarName, String jarHex, List<String> problems) {
		String text;
		try {
			text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			problems.add("the sidecar is not valid UTF-8");
			return;
		}
		boolean bom = !text.isEmpty() && text.charAt(0) == BOM;
		boolean cr = text.indexOf('\r') >= 0;
		if (bom) {
			problems.add("the sidecar starts with a UTF-8 BOM (EC-REL-06); use the file ./gradlew build writes");
		}
		if (cr) {
			problems.add("the sidecar has a CR (CRLF line ending, EC-REL-06); use the file ./gradlew build writes");
		}
		Matcher matcher = SIDECAR.matcher(text.replace(String.valueOf(BOM), "").replace("\r", ""));
		if (!matcher.matches()) {
			problems.add("the sidecar is not '<64 lowercase hex><two spaces><jar name><LF>'");
			return;
		}
		if (!jarName.equals(matcher.group(2))) {
			problems.add("the sidecar names " + matcher.group(2) + ", expected " + jarName
					+ " (the asset name with '+', never the %2B form of the download URL, EC-REL-08)");
		}
		if (jarHex != null && !jarHex.equals(matcher.group(1))) {
			problems.add("the sidecar's hash " + matcher.group(1) + " differs from the local jar's SHA-256 " + jarHex);
		}
	}

	private static void checkDigest(String what, JsonObject asset, String localHex, long localSize, List<String> problems) {
		String digest = string(asset, "digest");
		if (digest == null) {
			problems.add("the " + what + " asset has no digest");
		} else {
			Matcher matcher = DIGEST.matcher(digest);
			if (!matcher.matches()) {
				problems.add("the " + what + " asset's digest " + quote(digest) + " is not 'sha256:<64 lowercase hex>'");
			} else if (!matcher.group(1).equals(localHex)) {
				problems.add("the " + what + " asset's digest " + digest + " differs from the local " + what + "'s SHA-256 " + localHex
						+ " (a different file was uploaded)");
			}
		}
		JsonElement size = asset.get("size");
		if (size == null || !size.isJsonPrimitive() || !size.getAsJsonPrimitive().isNumber() || size.getAsLong() != localSize) {
			problems.add("the " + what + " asset's size is " + size + " bytes, the local " + what + " has " + localSize);
		}
	}

	/** The body equals the CHANGELOG section; only CRLF and trailing whitespace at the end are ignored. */
	private static void checkBody(JsonObject release, String expectedNotes, List<String> problems) {
		String body = normaliseNotes(string(release, "body"));
		String expected = normaliseNotes(expectedNotes);
		if (body.equals(expected)) {
			return;
		}
		String[] got = body.split("\n", -1);
		String[] want = expected.split("\n", -1);
		int line = 0;
		while (line < got.length && line < want.length && got[line].equals(want[line])) {
			line++;
		}
		String gotLine = line < got.length ? got[line] : null;
		String wantLine = line < want.length ? want[line] : null;
		problems.add("the release body differs from the CHANGELOG section at line " + (line + 1) + ": got " + excerpt(gotLine)
				+ ", expected " + excerpt(wantLine) + firstDifference(gotLine, wantLine));
	}

	/**
	 * Where two lines first differ, with the code points in ASCII, so that a lost en dash or curly
	 * quote stays readable on any console.
	 */
	static String firstDifference(String got, String want) {
		if (got == null || want == null) {
			return "";
		}
		int column = 0;
		while (column < got.length() && column < want.length() && got.charAt(column) == want.charAt(column)) {
			column++;
		}
		return " (column " + (column + 1) + ": got " + codePoint(got, column) + ", expected " + codePoint(want, column) + ")";
	}

	private static String codePoint(String text, int index) {
		return index < text.length() ? "U+%04X".formatted(text.codePointAt(index)) : "the end of the line";
	}

	static String normaliseNotes(String text) {
		return text == null ? "" : text.replace("\r\n", "\n").stripTrailing();
	}

	/** id, version and Minecraft range of the jar's fabric.mod.json (REQ-REL-07). */
	private static void checkModJson(Path jar, String expectedVersion, String minecraftVersion, List<String> problems) {
		if (!Files.isRegularFile(jar)) {
			return;
		}
		JsonObject modJson;
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry("fabric.mod.json");
			if (entry == null) {
				problems.add("the jar has no fabric.mod.json");
				return;
			}
			try (InputStream in = zip.getInputStream(entry)) {
				JsonElement root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
				if (!root.isJsonObject()) {
					problems.add("the jar's fabric.mod.json is not a JSON object");
					return;
				}
				modJson = root.getAsJsonObject();
			}
		} catch (IOException | JsonParseException e) {
			problems.add("the jar's fabric.mod.json cannot be read: " + e.getMessage());
			return;
		}
		String id = string(modJson, "id");
		if (!MOD_ID.equals(id)) {
			problems.add("fabric.mod.json id is " + quote(id) + ", expected '" + MOD_ID + "'");
		}
		String version = string(modJson, "version");
		if (!expectedVersion.equals(version)) {
			problems.add("fabric.mod.json version is " + quote(version) + ", expected '" + expectedVersion + "'");
		}
		JsonElement minecraft = modJson.get("depends") instanceof JsonObject depends ? depends.get("minecraft") : null;
		List<String> predicates = new ArrayList<>();
		if (minecraft != null && minecraft.isJsonPrimitive()) {
			predicates.add(minecraft.getAsString());
		} else if (minecraft instanceof JsonArray array) {
			array.forEach(element -> predicates.add(element.isJsonPrimitive() ? element.getAsString() : element.toString()));
		}
		if (predicates.isEmpty()) {
			problems.add("fabric.mod.json has no depends.minecraft");
			return;
		}
		try {
			SemanticVersion mc = SemanticVersion.parse(minecraftVersion);
			boolean accepted = false;
			for (String predicate : predicates) {
				accepted |= VersionPredicate.parse(predicate).test(mc);
			}
			if (!accepted) {
				problems.add("fabric.mod.json depends.minecraft " + minecraft + " does not accept Minecraft " + minecraftVersion);
			}
		} catch (VersionParsingException e) {
			problems.add("fabric.mod.json depends.minecraft " + minecraft + " or Minecraft " + minecraftVersion
					+ " cannot be parsed: " + e.getMessage());
		}
	}

	/**
	 * {@code <draft.json> <jar> <sidecar> <notes> <mod version> <minecraft version>}; exits 0 on
	 * PASS, 1 with one line per problem, 2 on a usage error.
	 */
	public static void main(String[] args) {
		System.exit(run(args, System.out, System.err));
	}

	static int run(String[] args, PrintStream out, PrintStream err) {
		if (args.length != 6) {
			err.println("usage: ReleaseDraftCheck <draft.json> <jar> <sidecar> <notes file> <mod version> <minecraft version>");
			return 2;
		}
		String draftJson = readText(Path.of(args[0]), "draft JSON", err);
		String notes = readText(Path.of(args[3]), "notes file", err);
		if (draftJson == null || notes == null) {
			return 2;
		}
		Path jar = Path.of(args[1]);
		List<String> problems = check(draftJson, jar, Path.of(args[2]), notes, args[4], args[5]);
		if (!problems.isEmpty()) {
			err.println("release draft check: FAIL, " + problems.size() + " problem(s) with the v" + args[4] + " draft:");
			problems.forEach(problem -> err.println("  - " + problem));
			return 1;
		}
		out.println("release draft check: PASS for the v" + args[4] + " draft");
		out.println("  draft, tag, title, pre-release flag, assets, digests, sidecar, body and fabric.mod.json match");
		out.println("  " + fileName(jar) + " sha256:" + sha256(read(jar, "jar", new ArrayList<>())));
		return 0;
	}

	/** A UTF-8 input file, or null after one line on {@code err} that names the file and the reason. */
	private static String readText(Path file, String what, PrintStream err) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (CharacterCodingException e) {
			err.println("release draft check: cannot read the " + what + " " + file + " as UTF-8 (" + e.getClass().getSimpleName()
					+ "); save it as UTF-8, for example from Git Bash rather than PowerShell");
		} catch (IOException e) {
			err.println("release draft check: cannot read the " + what + " " + file + " (" + e.getClass().getSimpleName() + ")");
		}
		return null;
	}

	private static byte[] read(Path file, String what, List<String> problems) {
		try {
			return Files.readAllBytes(file);
		} catch (IOException e) {
			problems.add("the local " + what + " " + file + " cannot be read (" + e.getClass().getSimpleName() + ")");
			return null;
		}
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is always available", e);
		}
	}

	private static String fileName(Path path) {
		return path.getFileName() == null ? path.toString() : path.getFileName().toString();
	}

	private static String stripBom(String text) {
		return !text.isEmpty() && text.charAt(0) == BOM ? text.substring(1) : text;
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
	}

	private static boolean bool(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
	}

	private static String quote(String text) {
		return text == null ? "(missing)" : "'" + text + "'";
	}

	private static String excerpt(String line) {
		if (line == null) {
			return "(end of text)";
		}
		return "'" + (line.length() > 80 ? line.substring(0, 77) + "..." : line) + "'";
	}
}
