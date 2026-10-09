package com.k8bas.skyblockutility.hook;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AD-9, AC-XC-01 [R] and AC-GLOW-05 [R] (T3.0f), as a source scan over src/main and src/gametest:
 * <ul>
 *   <li>no {@code @Redirect}, {@code @Overwrite} or {@code cancellable = true} anywhere;</li>
 *   <li>mixins only in the mixin packages, and nothing else there (Mixin will not load other classes from them);</li>
 *   <li>every mixin {@code @Pseudo}, so a missing target is Mixin's DEBUG line and the hook's one WARN is ours;</li>
 *   <li>every injector handler named {@code k8bas$...} and unique, so the hook probe finds exactly its own;</li>
 *   <li>no mixin touches glowing or visibility: the glow goes through Render Chest (AC-GLOW-05, T1.10).</li>
 * </ul>
 */
class MixinRulesTest {
	// An explicit require, allow or @Group would turn an unmatched optional hook into a crash.
	private static final Pattern FORBIDDEN = Pattern.compile("@(?:[\\w.]+\\.)?(?:Redirect|Overwrite|Group)\\b|cancellable\\s*=\\s*true"
			+ "|\\brequire\\s*=\\s*[1-9]|\\ballow\\s*=");
	private static final Pattern INJECTOR = Pattern.compile("@(?:[\\w.]+\\.)?(?:WrapOperation|WrapMethod|ModifyReturnValue|ModifyExpressionValue"
			+ "|ModifyReceiver|WrapWithCondition|Inject|ModifyArg|ModifyArgs|ModifyVariable|ModifyConstant)\\s*\\(");
	private static final Pattern GLOW = Pattern.compile("shouldEntityAppearGlowing|isCurrentlyGlowing|hasGlowingTag|setGlowingTag"
			+ "|isInvisible|isInvisibleTo|setInvisible|getTeamColor");
	private static final Pattern NAME_BEFORE_PARAMS = Pattern.compile("([\\w$]+)\\s*$");
	private static final List<String> MIXIN_PACKAGES = List.of("com/k8bas/skyblockutility/mixin/", "com/k8bas/skyblockutility/gametest/mixin/");

	private static List<Path> sources() throws IOException {
		Path project = Path.of(System.getProperty("k8bas.projectDir", "."));
		List<Path> files = new ArrayList<>();
		for (String root : List.of("src/main", "src/gametest")) {
			try (Stream<Path> walk = Files.walk(project.resolve(root))) {
				walk.filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".kt")).forEach(files::add);
			}
		}
		return files;
	}

	private static boolean isMixin(String text) {
		return Pattern.compile("^\\s*@(?:[\\w.]+\\.)?Mixin\\s*\\(", Pattern.MULTILINE).matcher(text).find();
	}

	/** The end of the parenthesised argument list starting at {@code open}, skipping string and char literals. */
	private static int closing(String text, int open) {
		int depth = 0;
		for (int i = open; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '"' || c == '\'') {
				for (i++; i < text.length() && text.charAt(i) != c; i++) {
					if (text.charAt(i) == '\\') {
						i++;
					}
				}
			} else if (c == '(') {
				depth++;
			} else if (c == ')' && --depth == 0) {
				return i;
			}
		}
		throw new IllegalArgumentException("unbalanced annotation at " + open);
	}

	/** The names of the methods each injector annotation in {@code text} is on. */
	static List<String> handlers(String text) {
		List<String> names = new ArrayList<>();
		Matcher m = INJECTOR.matcher(text);
		int from = 0;
		while (m.find(from)) {
			int end = closing(text, m.end() - 1);
			// Further annotations (e.g. @Local in no position, @Unique) may follow before the declaration.
			int i = end + 1;
			while (true) {
				String rest = text.substring(i).stripLeading();
				i = text.length() - rest.length();
				if (!rest.startsWith("@")) {
					break;
				}
				Matcher next = Pattern.compile("@[\\w.]+\\s*").matcher(rest);
				next.lookingAt();
				i += next.end();
				if (text.startsWith("(", i)) {
					i = closing(text, i) + 1;
				}
			}
			int params = text.indexOf('(', i);
			Matcher name = NAME_BEFORE_PARAMS.matcher(text.substring(i, params));
			names.add(name.find() ? name.group(1) : "<unparsed>");
			from = end;
		}
		return names;
	}

	@Test
	void noRedirectOverwriteOrCancellation() throws IOException {
		List<String> hits = new ArrayList<>();
		for (Path file : sources()) {
			Matcher m = FORBIDDEN.matcher(Files.readString(file));
			while (m.find()) {
				hits.add(file + ": " + m.group());
			}
		}
		assertEquals(List.of(), hits);
	}

	@Test
	void mixinsLiveInTheMixinPackagesOnlyAreAllPseudoAndLeaveGlowAlone() throws IOException {
		List<String> problems = new ArrayList<>();
		for (Path file : sources()) {
			String text = Files.readString(file);
			boolean isMixin = isMixin(text);
			boolean inPackage = MIXIN_PACKAGES.stream().anyMatch(file.toString().replace('\\', '/')::contains);
			if (isMixin != inPackage) {
				problems.add(file + (isMixin ? ": a mixin outside the mixin packages" : ": not a mixin, in a mixin package"));
			}
			if (isMixin && !Pattern.compile("^\\s*@(?:[\\w.]+\\.)?Pseudo\\b", Pattern.MULTILINE).matcher(text).find()) {
				problems.add(file + ": a mixin without @Pseudo");
			}
			Matcher glow = GLOW.matcher(text);
			if (isMixin && glow.find()) {
				problems.add(file + ": a mixin touches glowing or visibility (" + glow.group() + ")");
			}
		}
		assertEquals(List.of(), problems);
	}

	@Test
	void everyInjectorHandlerIsNamedForThisModAndUnique() throws IOException {
		List<String> problems = new ArrayList<>();
		Map<String, Path> seen = new HashMap<>();
		for (Path file : sources()) {
			String text = Files.readString(file);
			if (!isMixin(text)) {
				continue;
			}
			for (String handler : handlers(text)) {
				if (!handler.startsWith("k8bas$")) {
					problems.add(file + ": handler " + handler);
				}
				Path before = seen.put(handler, file);
				if (before != null) {
					problems.add(file + ": handler " + handler + " also in " + before);
				}
			}
		}
		assertEquals(List.of(), problems);
		assertTrue(seen.size() >= 5, "the gametest fixtures have handlers, so the scan sees them: " + seen.keySet());
	}

	@Test
	void theScanCatchesEachKindOfViolation() {
		assertTrue(FORBIDDEN.matcher("@Redirect(method = \"x\")").find());
		assertTrue(FORBIDDEN.matcher("@org.spongepowered.asm.mixin.injection.Redirect(method = \"x\")").find());
		assertTrue(FORBIDDEN.matcher("@Overwrite public void x() {}").find());
		assertTrue(FORBIDDEN.matcher("@Inject(method = \"x\", at = @At(\"HEAD\"), cancellable = true)").find());
		assertTrue(FORBIDDEN.matcher("@WrapOperation(method = \"x\", at = @At(\"INVOKE\"), require = 1)").find());
		assertTrue(FORBIDDEN.matcher("@ModifyReturnValue(method = \"x\", at = @At(\"RETURN\"), allow = 2)").find());
		assertTrue(FORBIDDEN.matcher("@Group(name = \"g\", min = 1)").find());
		assertTrue(!FORBIDDEN.matcher("@WrapOperation(method = \"x\", at = @At(\"INVOKE\"), require = 0)").find(), "require = 0 is the default anyway");
		assertTrue(GLOW.matcher("@ModifyReturnValue(method = \"isCurrentlyGlowing\", at = @At(\"RETURN\"))").find());
		assertTrue(isMixin("@Mixin(targets = \"a.B\")\npublic abstract class X {}"));
		assertEquals(List.of("doubled"), handlers("@ModifyReturnValue(method = \"value\", at = @At(\"RETURN\"))\n\tprivate int doubled(int original) {"));
		assertEquals(List.of("k8bas$a"), handlers("@Inject(method = {\"a\", \"b(I)V\"}, at = {@At(\"HEAD\"), @At(value = \"INVOKE\", target = \"L(;)V\")})\n"
				+ "\t@Unique\n\tprivate static void k8bas$a(CallbackInfo ci) {"));
		assertEquals(List.of("k8bas$w"), handlers("@WrapOperation(method = \"x\", at = @At(value = \"INVOKE\", target = \"Lp/Q;r()I\"))\n"
				+ "\tprivate int k8bas$w(@Coerce Object self, Operation<Integer> original) {"));
		assertEquals(List.of("k8bas$m"), handlers("@com.llamalad7.mixinextras.injector.WrapMethod(method = \"x\")\nprivate void k8bas$m(Operation<Void> o) {"));
	}
}
