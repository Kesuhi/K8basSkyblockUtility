package com.k8bas.skyblockutility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The source scan for "sends nothing and clicks nothing" (REQ-XC-RULES, AC-GS-01, AC-MSA-08, AC-BOB-06, AC-HOT-07):
 * the 26.2 names of a slot click, a container close, a command or chat send and a packet send (checked with javap on
 * the 26.2 jar), plus every Serverbound packet. Comments are left out, so a doc comment may say what the code never
 * does.
 */
public final class SendScan {
	public static final Map<String, Pattern> SENDS = Map.of(
			"slot click", Pattern.compile("handleInventoryMouseClick|handleInventoryButtonClick|handleSlotStateChanged|handlePlaceRecipe"
					+ "|handleContainerInput|ContainerInput\\.|ServerboundContainer\\w*"),
			"container close", Pattern.compile("(?i)closeContainer|ServerboundContainerClose|\\.onClose\\(|setScreen\\("),
			"command or chat send", Pattern.compile("\\bsend[A-Z]\\w*Command\\b|\\bsendCommand\\b|\\bsendChat\\b|ServerboundChat\\w*"),
			"packet send", Pattern.compile("\\.send\\(|\\.connection\\.send|getConnection\\(\\)\\.send|ServerboundPacket|Serverbound\\w+Packet"));

	private SendScan() {
	}

	public static String withoutComments(String source) {
		return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
	}

	/** Every Java and Kotlin file under [paths] (folders or files), relative to the project. */
	public static List<Path> sources(Path project, String... paths) throws IOException {
		List<Path> files = new ArrayList<>();
		for (String path : paths) {
			Path at = project.resolve(path);
			if (Files.isRegularFile(at)) {
				files.add(at);
			} else if (Files.isDirectory(at)) {
				try (Stream<Path> walk = Files.walk(at)) {
					walk.filter(file -> file.toString().endsWith(".java") || file.toString().endsWith(".kt")).forEach(files::add);
				}
			}
		}
		return files;
	}

	/** "file: kind (match)" for each forbidden call in [files]. */
	public static List<String> violations(Path project, List<Path> files, Map<String, Pattern> forbidden) throws IOException {
		List<String> found = new ArrayList<>();
		for (Path file : files) {
			String source = withoutComments(Files.readString(file));
			forbidden.forEach((what, pattern) -> {
				Matcher m = pattern.matcher(source);
				if (m.find()) {
					found.add(project.relativize(file) + ": " + what + " (" + m.group() + ")");
				}
			});
		}
		return found;
	}
}
