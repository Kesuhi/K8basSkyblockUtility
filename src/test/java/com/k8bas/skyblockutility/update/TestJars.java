package com.k8bas.skyblockutility.update;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Jar fixtures for the download tests: a zip holding one fabric.mod.json, built in memory. */
final class TestJars {
	private TestJars() {
	}

	/** A fabric.mod.json; a null field is left out. */
	static String modJson(String id, String version, String minecraft, String loader, String environment) {
		StringBuilder json = new StringBuilder("{\"schemaVersion\": 1");
		if (id != null) {
			json.append(", \"id\": \"").append(id).append('"');
		}
		if (version != null) {
			json.append(", \"version\": \"").append(version).append('"');
		}
		if (environment != null) {
			json.append(", \"environment\": \"").append(environment).append('"');
		}
		json.append(", \"depends\": {");
		String separator = "";
		if (minecraft != null) {
			json.append("\"minecraft\": ").append(minecraft.startsWith("[") ? minecraft : "\"" + minecraft + "\"");
			separator = ", ";
		}
		if (loader != null) {
			json.append(separator).append("\"fabricloader\": \"").append(loader).append('"');
		}
		return json.append("}}").toString();
	}

	/** The mod's own jar for {@code version} ("1.2.0+26.2"), as the build writes it. */
	static byte[] mod(String version) {
		return jar(modJson("k8bas_skyblock_utility", version, "~26.2", ">=0.19.5", "client"));
	}

	static byte[] jar(String fabricModJson) {
		return jar("fabric.mod.json", fabricModJson.getBytes(StandardCharsets.UTF_8));
	}

	static byte[] jar(String entryName, byte[] content) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("com/k8bas/Placeholder.class"));
			zip.write(new byte[] {(byte) 0xCA, (byte) 0xFE});
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry(entryName));
			zip.write(content);
			zip.closeEntry();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}

	/** A jar whose fabric.mod.json inflates to {@code megabytes} MiB of zeros, a few MiB on disk: a zip bomb. */
	static byte[] bomb(int megabytes) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.setLevel(java.util.zip.Deflater.BEST_SPEED);
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			byte[] zeros = new byte[1024 * 1024];
			for (int i = 0; i < megabytes; i++) {
				zip.write(zeros);
			}
			zip.closeEntry();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}

	/** The zip with {@code entry}'s uncompressed size in the central directory rewritten to {@code size}: a lying header. */
	static byte[] withDeclaredSize(byte[] zip, String entry, int size) {
		byte[] out = zip.clone();
		byte[] name = entry.getBytes(StandardCharsets.UTF_8);
		for (int i = 0; i + 46 <= out.length; i++) {
			if (out[i] == 0x50 && out[i + 1] == 0x4b && out[i + 2] == 0x01 && out[i + 3] == 0x02) {
				int nameLength = (out[i + 28] & 0xff) | (out[i + 29] & 0xff) << 8;
				if (nameLength == name.length && java.util.Arrays.equals(out, i + 46, i + 46 + nameLength, name, 0, name.length)) {
					out[i + 24] = (byte) size;
					out[i + 25] = (byte) (size >>> 8);
					out[i + 26] = (byte) (size >>> 16);
					out[i + 27] = (byte) (size >>> 24);
					return out;
				}
			}
		}
		throw new IllegalArgumentException("no central directory entry " + entry);
	}

	static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
