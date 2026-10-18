package com.k8bas.skyblockutility.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T4.4 (REQ-UPD-11, EC-UPD-10, AC-UPD-11's notes part): release notes as plain text, capped at 4,000 characters. */
class ReleaseNotesTest {
	@Test
	void formattingCodesAndMarkdownBecomePlainText() {
		String markdown = "## Changed\r\n\r\n- **Bold** and *italic* and `code` and ~~gone~~\r\n- [a link](https://example.com) and ![img](x.png)\r\n"
				+ "> quoted §cred§r text\r\n\r\n\r\n\r\n---\r\n```\r\nfenced\r\n```\r\n1. kept <b>tag</b>\r\nline break  \r\nsnake_case_name stays";
		assertEquals("Changed\n\n• Bold and italic and code and gone\n• a link and img\nquoted red text\n\nfenced\n1. kept tag\nline break\nsnake_case_name stays",
				ReleaseNotes.plain(markdown));
	}

	@Test
	void longNotesAreCutAt4000Characters() {
		String plain = ReleaseNotes.plain("x".repeat(5_000));
		assertEquals(ReleaseNotes.MAX_CHARS, plain.length());
		assertTrue(plain.endsWith("…"), plain.substring(plain.length() - 3));
		assertEquals("y".repeat(4_000), ReleaseNotes.plain("y".repeat(4_000)), "exactly 4,000 is not cut");
		String emoji = ReleaseNotes.plain("a".repeat(3_998) + "😀😀");
		assertTrue(!Character.isHighSurrogate(emoji.charAt(emoji.length() - 2)), "never half a character before the cut");
	}

	@Test
	void nothingIsEmpty() {
		assertEquals("", ReleaseNotes.plain(null));
		assertEquals("", ReleaseNotes.plain("  \n "));
		assertEquals("", ReleaseNotes.plain("§l§r"));
	}
}
