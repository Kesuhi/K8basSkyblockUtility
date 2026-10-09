package com.k8bas.skyblockutility.ui.notice;

import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.OptionText;
import com.k8bas.skyblockutility.ui.option.Toggle;

import java.util.List;

/**
 * The "also show as a notice" option a warning feature may offer (REQ-UI-21): an extra channel next to
 * the feature's own (chat, title, sound), always OFF by default.
 */
public final class NoticeChannel {
	private NoticeChannel() {
	}

	/**
	 * @param what what the warning is about, for the description, e.g. "the hotspot is gone"
	 */
	public static Toggle option(String id, String storageKey, String what, Binding<Boolean> binding) {
		return Toggle.of(id, storageKey, new OptionText("Also show as a notice",
				"Shows a notice in the corner of the screen when " + what + ", as well as the usual warning.",
				"Where and for how long notices show is set under General, Interface.", List.of("notice", "toast", "popup")),
				false, binding);
	}
}
