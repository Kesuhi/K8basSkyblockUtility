package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.ui.render.Clip;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiClock;
import com.k8bas.skyblockutility.ui.render.UiSound;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.render.UiTime;
import com.k8bas.skyblockutility.ui.widget.Button;
import com.k8bas.skyblockutility.ui.widget.ScrollArea;
import com.k8bas.skyblockutility.ui.widget.WidgetScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The confirmation screen (REQ-UPD-11): running → new version and the channel, the asset name and size, the first 12 hex
 * characters of the expected SHA-256, and the release notes as scrollable plain text. Only *Download & install on
 * restart* allows a download, for this version and the checksum shown; *Skip this version* needs a second click within
 * 3 s (REQ-UPD-12); *Open release page* goes through the game's own link confirmation; *Later* and Esc download nothing.
 * It opens only from a command or a click, never on its own.
 */
public final class UpdateScreen extends WidgetScreen {
	static final String TITLE = "K8bas Skyblock Utility update";
	static final String DOWNLOAD = "Download & install on restart";
	static final String SKIP = "Skip this version";
	static final String CONFIRM_SKIP = "Click again to skip";
	static final long SKIP_CONFIRM_MS = 3_000;
	/** A second click sooner than this is one double-click, not a decision (REQ-UPD-12). */
	static final long SKIP_MIN_GAP_MS = 300;
	private static final int PANEL_MAX_W = 380;
	private static final int PANEL_MAX_H = 270;
	private static final int PAD = 10;
	private static final int ROW = 11;
	private static final int LABEL_W = 56;
	private static final int BUTTON_H = 20;
	private static final int GAP = 4;

	private final Screen parent;
	private final CheckOutcome.Found found;
	private final Confirmation confirmation;
	/** Runs an action off the render thread and gives its lines back on it. */
	private final Function<Function<UpdateActions, List<String>>, java.util.concurrent.CompletableFuture<List<String>>> run;
	/** Shows lines once the screen is closed (the skip's answer), in the chat in the game. */
	private final java.util.function.Consumer<List<String>> say;
	private final ScrollArea notes = add(new ScrollArea());
	private final Button download;
	private final Button releasePage;
	private final Button skip;
	private final Button later;
	private final List<String> drawnText = new ArrayList<>();
	private int[] panel = new int[4];
	private int[] notesBox = new int[4];
	private List<String> noteLines = List.of();
	private List<String> reasonLines = List.of();
	private volatile List<String> status = List.of();
	private volatile boolean busy;
	private long skipArmedAt = -1;
	/** Where the last action's answer is drawn: between the notes and the buttons. */
	private int statusY;

	/**
	 * @param run runs an action on a background thread with the game's {@link UpdateActions}; the screen shows its lines
	 * @param say shows lines after the screen has closed
	 */
	UpdateScreen(Screen parent, CheckOutcome.Found found, Confirmation confirmation,
			Function<Function<UpdateActions, List<String>>, java.util.concurrent.CompletableFuture<List<String>>> run,
			java.util.function.Consumer<List<String>> say, UiTime time) {
		super(Component.literal(TITLE));
		setTime(time);
		this.parent = parent;
		this.found = found;
		this.confirmation = confirmation;
		this.run = run;
		this.say = say;
		download = new Button(DOWNLOAD, Button.Style.PRIMARY, this::download, UiSound::click);
		// Only an install that may be offered has the button (REQ-UPD-08); a notify-only screen says why instead.
		if (confirmation.getInstallOffered()) {
			add(download);
		}
		releasePage = add(new Button("Open release page", Button.Style.NORMAL, this::openReleasePage, UiSound::click));
		skip = add(new Button(SKIP, Button.Style.NORMAL, this::skip, UiSound::click));
		later = add(new Button("Later", Button.Style.NORMAL, this::onClose, UiSound::click));
	}

	UpdateScreen(Screen parent, CheckOutcome.Found found, Confirmation confirmation,
			Function<Function<UpdateActions, List<String>>, java.util.concurrent.CompletableFuture<List<String>>> run,
			java.util.function.Consumer<List<String>> say) {
		this(parent, found, confirmation, run, say, UiClock.GAME);
	}

	private void download() {
		Consent consent = confirmation.consent();
		if (consent == null || busy) {
			return;
		}
		busy = true;
		status = List.of("Downloading and checking...");
		run.apply(actions -> actions.install(consent)).whenComplete((lines, error) -> {
			status = error != null ? List.of("Download failed, nothing changed") : lines;
			busy = false;
		});
	}

	private void skip() {
		long now = time().millis();
		if (skipArmedAt < 0 || now - skipArmedAt > SKIP_CONFIRM_MS) {
			skipArmedAt = now;
			skip.relabel(CONFIRM_SKIP, Button.Style.DESTRUCTIVE);
			return;
		}
		if (now - skipArmedAt < SKIP_MIN_GAP_MS) {
			return;
		}
		String version = found.candidate().version().toString();
		onClose();
		// The answer outlives the screen, so it goes where the player will see it.
		run.apply(actions -> actions.skip(version, true)).whenComplete((lines, error) -> say.accept(lines == null ? List.of() : lines));
	}

	private void openReleasePage() {
		ConfirmLinkScreen.confirmLinkNow(this, confirmation.getReleasePage());
	}

	@Override
	protected void layout() {
		int w = Math.min(PANEL_MAX_W, width - 2 * PAD);
		int h = Math.min(PANEL_MAX_H, height - 2 * PAD);
		panel = new int[] {(width - w) / 2, (height - h) / 2, w, h};
		// The other buttons in one row at the bottom, each as wide as its label; the one that downloads alone above them, so
		// no label is ever cut.
		int buttonsY = panel[1] + h - PAD - BUTTON_H;
		Button[] row = {releasePage, skip, later};
		int[] widths = new int[row.length];
		int total = GAP * (row.length - 1);
		for (int i = 0; i < row.length; i++) {
			widths[i] = font.width(row[i] == skip ? CONFIRM_SKIP : row[i].label()) + 16;
			total += widths[i];
		}
		int room = w - 2 * PAD;
		int x = panel[0] + (w - Math.min(total, room)) / 2;
		for (int i = 0; i < row.length; i++) {
			int bw = total > room ? (room - GAP * (row.length - 1)) / row.length : widths[i];
			row[i].setBounds(x, buttonsY, bw, BUTTON_H);
			x += bw + GAP;
		}
		int actionsTop = buttonsY;
		if (confirmation.getInstallOffered()) {
			int dw = Math.min(room, font.width(DOWNLOAD) + 24);
			actionsTop = buttonsY - GAP - BUTTON_H;
			download.setBounds(panel[0] + (w - dw) / 2, actionsTop, dw, BUTTON_H);
		}
		reasonLines = confirmation.getReason() == null ? List.of() : Confirmation.wrap(confirmation.getReason(), w - 2 * PAD, font::width);
		int top = panel[1] + PAD + font.lineHeight + 6 + ROW * 5 + ROW * reasonLines.size() + ROW + 2;
		int bottom = actionsTop - GAP - ROW;
		statusY = actionsTop - ROW + 1;
		notesBox = new int[] {panel[0] + PAD, top, w - 2 * PAD, Math.max(0, bottom - top)};
		noteLines = Confirmation.wrap(confirmation.getNotes(), notesBox[2] - 8 - ScrollArea.SCROLLBAR, font::width);
		notes.setBounds(notesBox[0], notesBox[1], notesBox[2], notesBox[3]);
		notes.setContentHeight(noteLines.size() * font.lineHeight + 8);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractDimmedGame(graphics, partialTick);
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		drawnText.clear();
		if (skipArmedAt >= 0 && time().millis() - skipArmedAt > SKIP_CONFIRM_MS) {
			skipArmedAt = -1;
			skip.relabel(SKIP, Button.Style.NORMAL);
		}
		download.setEnabled(!busy);
		Shapes.shadow(graphics, panel[0], panel[1], panel[2], panel[3], Shapes.RADIUS_PANEL);
		Shapes.roundedRect(graphics, panel[0], panel[1], panel[2], panel[3], Shapes.RADIUS_PANEL, Theme.PANEL);
		int x = panel[0] + PAD;
		int y = panel[1] + PAD;
		text(graphics, TITLE, x, y, panel[2] - 2 * PAD, Theme.TEXT_PRIMARY);
		graphics.fill(x, y + font.lineHeight + 2, panel[0] + panel[2] - PAD, y + font.lineHeight + 3, Theme.current().accent());
		y += font.lineHeight + 6;
		y = field(graphics, "Version", confirmation.getVersions(), x, y);
		y = field(graphics, "Channel", confirmation.getChannel(), x, y);
		y = field(graphics, "File", confirmation.getAsset(), x, y);
		y = field(graphics, "Size", confirmation.getSize(), x, y);
		y = field(graphics, "SHA-256", confirmation.getChecksum() == null ? "none" : confirmation.getChecksum() + "...", x, y);
		// The reason is the point of a notify-only screen: wrapped, never cut.
		for (String line : reasonLines) {
			text(graphics, line, x, y, panel[2] - 2 * PAD, Theme.CONFLICT_TEXT);
			y += ROW;
		}
		text(graphics, "Release notes", x, y, panel[2] - 2 * PAD, Theme.TEXT_SECONDARY);
		Shapes.roundedRect(graphics, notesBox[0], notesBox[1], notesBox[2], notesBox[3], Shapes.RADIUS_CONTROL, Theme.CARD);
		try (Clip clip = Clip.push(graphics, notesBox[0], notesBox[1], notesBox[2], notesBox[3])) {
			if (clip.visible()) {
				int lineY = notesBox[1] + 4 - notes.scroll();
				for (String line : noteLines) {
					if (lineY + font.lineHeight >= notesBox[1] && lineY <= notesBox[1] + notesBox[3]) {
						UiText.draw(graphics, font, line, notesBox[0] + 4, lineY, Theme.TEXT_PRIMARY);
					}
					drawnText.add(line);
					lineY += font.lineHeight;
				}
			}
		}
		for (String line : status) {
			text(graphics, line, x, statusY, panel[2] - 2 * PAD, Theme.TEXT_PRIMARY);
		}
	}

	private int field(GuiGraphicsExtractor graphics, String label, String value, int x, int y) {
		text(graphics, label, x, y, LABEL_W - 4, Theme.TEXT_SECONDARY);
		text(graphics, value, x + LABEL_W, y, panel[2] - 2 * PAD - LABEL_W, Theme.TEXT_PRIMARY);
		return y + ROW;
	}

	private void text(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int argb) {
		String shown = Ellipsis.fit(text, maxWidth, font::width);
		UiText.draw(graphics, font, shown, x, y, argb);
		drawnText.add(shown);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	/** Every text drawn in the last frame, notes included (for gametests). */
	List<String> drawnTextForTests() {
		return List.copyOf(drawnText);
	}

	Button downloadButtonForTests() {
		return download;
	}

	Button skipButtonForTests() {
		return skip;
	}

	Button laterButtonForTests() {
		return later;
	}

	/** Whether the download button is on the screen at all (a notify-only screen has none). */
	boolean hasDownloadButtonForTests() {
		return widgets().contains(download);
	}

	/** Scrolls the notes to their end (for gametests). */
	void scrollNotesToEndForTests() {
		notes.setScroll(notes.maxScroll());
	}

	/** What the last action said (for gametests). */
	List<String> statusForTests() {
		return status;
	}

	/** True while a download runs (for gametests). */
	boolean busyForTests() {
		return busy;
	}

	private UiTime time() {
		return getTime();
	}
}
