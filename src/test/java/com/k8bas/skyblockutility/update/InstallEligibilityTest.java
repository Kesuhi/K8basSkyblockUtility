package com.k8bas.skyblockutility.update;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * T4.3 (REQ-UPD-16, AC-UPD-17 [A]): whether this game may install an update itself, and if not, the reason by name.
 * A false positive only downgrades to notify-only.
 */
class InstallEligibilityTest {
	private static final String JAR = "k8bas_skyblock_utility-2.0.0+26.2.jar";

	@TempDir
	Path dir;

	private Path game;
	private Path mods;
	private Path jar;

	@BeforeEach
	void setUp() throws IOException {
		game = Files.createDirectories(dir.resolve("instances").resolve("26.2 Skyblock").resolve("minecraft"));
		mods = Files.createDirectories(game.resolve("mods"));
		jar = Files.write(mods.resolve(JAR), TestJars.mod("2.0.0+26.2"));
	}

	private InstallEligibility eligibility(boolean dev, InstallEligibility.Origin origin, String version, Path gameDir, Predicate<Path> writable) {
		return new InstallEligibility(dev, origin, version, gameDir, writable);
	}

	private UpdateHooks.Eligibility check(boolean dev, InstallEligibility.Origin origin, String version, Path gameDir, Predicate<Path> writable) {
		return eligibility(dev, origin, version, gameDir, writable).check();
	}

	private UpdateHooks.Eligibility normal() {
		return check(false, new InstallEligibility.Origin(true, List.of(jar)), "2.0.0+26.2", game, path -> true);
	}

	@Test
	void anOrdinaryInstallIsEligible() {
		assertEquals(UpdateHooks.Eligibility.ELIGIBLE, normal());
		assertEquals(jar, eligibility(false, new InstallEligibility.Origin(true, List.of(jar)), "2.0.0+26.2", game, path -> true).getRunningJar());
	}

	/** AC-UPD-17: each REQ-UPD-16 reason, by name. */
	@Test
	void eachReasonIsNamed() throws IOException {
		InstallEligibility.Origin fromJar = new InstallEligibility.Origin(true, List.of(jar));
		assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.DEVELOPMENT), check(true, fromJar, "2.0.0+26.2", game, path -> true));
		assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.NOT_ONE_JAR),
				check(false, new InstallEligibility.Origin(true, List.of(mods)), "2.0.0+26.2", game, path -> true), "a folder");
		assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.NOT_ONE_JAR),
				check(false, new InstallEligibility.Origin(false, List.of(jar)), "2.0.0+26.2", game, path -> true), "nested in another jar");
		assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.NOT_ONE_JAR),
				check(false, new InstallEligibility.Origin(true, List.of(jar, jar)), "2.0.0+26.2", game, path -> true), "two paths");
		assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.NOT_ONE_JAR), check(false, null, "2.0.0+26.2", game, path -> true), "unknown");
		Path zip = Files.write(mods.resolve("k8bas.zip"), new byte[] {1});
		assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.NOT_ONE_JAR),
				check(false, new InstallEligibility.Origin(true, List.of(zip)), "2.0.0+26.2", game, path -> true), "not a .jar");
		assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.UNWRITABLE),
				check(false, fromJar, "2.0.0+26.2", game, path -> !path.equals(mods)), "the jar's folder");
		assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.UNWRITABLE),
				check(false, fromJar, "2.0.0+26.2", game, path -> !path.equals(jar)), "a read-only jar can be replaced but not deleted");
		assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.NOT_SEMVER), check(false, fromJar, "1.0", game, path -> true));
		assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.NOT_SEMVER), check(false, fromJar, "unknown", game, path -> true));
	}

	/** AC-UPD-17: a Prism/packwiz index that names our jar means the launcher manages it; one that names others does not. */
	@Test
	void aPackwizIndexNamingOurJarIsLauncherManaged() throws IOException {
		Path index = Files.createDirectories(mods.resolve(".index"));
		Files.writeString(index.resolve("sodium.pw.toml"), "name = \"Sodium\"\nfilename = \"sodium-fabric-0.8.jar\"\nside = \"client\"\n");
		assertEquals(UpdateHooks.Eligibility.ELIGIBLE, normal(), "the index names other mods only");
		Files.writeString(index.resolve("k8bas.pw.toml"), "name = 'K8bas'\r\n  filename =  '" + JAR.toUpperCase() + "'  \r\n[download]\r\nhash-format = 'sha512'\r\n");
		assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.MANAGED), normal());
		assertEquals("This instance is managed by your launcher, so update it there", InstallEligibility.MANAGED);
	}

	/** REQ-UPD-16's unverified markers: a CurseForge instance file, a Modrinth App profile folder. */
	@Test
	void curseForgeAndModrinthAppInstancesAreLauncherManaged() throws IOException {
		Files.writeString(game.resolve("minecraftinstance.json"), "{}");
		assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.MANAGED), normal());
		Files.delete(game.resolve("minecraftinstance.json"));
		for (String app : List.of("ModrinthApp", "com.modrinth.theseus")) {
			Path profile = Files.createDirectories(dir.resolve(app).resolve("profiles").resolve("Skyblock"));
			Path profileMods = Files.createDirectories(profile.resolve("mods"));
			Path profileJar = Files.write(profileMods.resolve(JAR), new byte[] {1});
			assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.MANAGED),
					check(false, new InstallEligibility.Origin(true, List.of(profileJar)), "2.0.0+26.2", profile, path -> true), app);
		}
		Path notAnApp = Files.createDirectories(dir.resolve("profiles").resolve("x"));
		Path otherJar = Files.write(Files.createDirectories(notAnApp.resolve("mods")).resolve(JAR), new byte[] {1});
		assertEquals(UpdateHooks.Eligibility.ELIGIBLE,
				check(false, new InstallEligibility.Origin(true, List.of(otherJar)), "2.0.0+26.2", notAnApp, path -> true), "a folder named profiles alone");
	}

	/**
	 * AC-UPD-17: a really read-only mods folder (a Windows ACL that denies writing) and a read-only jar, through the
	 * game's own check, {@link Files#isWritable}. The read-only attribute on a folder does not stop writes on Windows.
	 */
	@Test
	void aReadOnlyModsFolderOrJarIsNotifyOnly() throws IOException {
		assumeTrue(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows"), "Windows ACLs");
		InstallEligibility.Origin fromJar = new InstallEligibility.Origin(true, List.of(jar));
		assertEquals(UpdateHooks.Eligibility.ELIGIBLE, check(false, fromJar, "2.0.0+26.2", game, Files::isWritable));
		java.nio.file.attribute.AclFileAttributeView acl = Files.getFileAttributeView(mods, java.nio.file.attribute.AclFileAttributeView.class);
		List<java.nio.file.attribute.AclEntry> original = acl.getAcl();
		java.nio.file.attribute.UserPrincipal me = mods.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
		List<java.nio.file.attribute.AclEntry> denied = new java.util.ArrayList<>();
		denied.add(java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.DENY).setPrincipal(me)
				.setPermissions(java.nio.file.attribute.AclEntryPermission.WRITE_DATA, java.nio.file.attribute.AclEntryPermission.ADD_FILE,
						java.nio.file.attribute.AclEntryPermission.APPEND_DATA, java.nio.file.attribute.AclEntryPermission.DELETE_CHILD).build());
		denied.addAll(original);
		try {
			acl.setAcl(denied);
			assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.UNWRITABLE), check(false, fromJar, "2.0.0+26.2", game, Files::isWritable));
		} finally {
			acl.setAcl(original);
		}
		Files.getFileAttributeView(jar, java.nio.file.attribute.DosFileAttributeView.class).setReadOnly(true);
		try {
			assertEquals(new UpdateHooks.Eligibility(false, InstallEligibility.UNWRITABLE), check(false, fromJar, "2.0.0+26.2", game, Files::isWritable));
		} finally {
			Files.getFileAttributeView(jar, java.nio.file.attribute.DosFileAttributeView.class).setReadOnly(false);
		}
	}

	/** No running jar is known where it is not eligible because of its origin. */
	@Test
	void theRunningJarIsKnownOnlyForASingleJar() {
		assertNull(eligibility(false, new InstallEligibility.Origin(true, List.of(mods)), "2.0.0+26.2", game, path -> true).getRunningJar());
		assertNull(eligibility(false, null, "2.0.0+26.2", game, path -> true).getRunningJar());
	}
}
