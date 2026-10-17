package com.k8bas.skyblockutility.ui.screen;

import com.google.gson.JsonElement;
import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.hud.HudContent;
import com.k8bas.skyblockutility.hud.HudEditorModel;
import com.k8bas.skyblockutility.hud.HudElement;
import com.k8bas.skyblockutility.hud.HudPosition;
import com.k8bas.skyblockutility.hud.HudPositions;
import com.k8bas.skyblockutility.hud.HudRect;
import com.k8bas.skyblockutility.hud.HudSnap;
import com.k8bas.skyblockutility.hud.HudRegistry;
import com.k8bas.skyblockutility.hud.HudRenderer;
import com.k8bas.skyblockutility.hud.HudText;
import com.k8bas.skyblockutility.hud.TextMeasure;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiClock;
import com.k8bas.skyblockutility.ui.render.UiFade;
import com.k8bas.skyblockutility.ui.render.UiSound;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.render.UiTime;
import com.k8bas.skyblockutility.ui.widget.Button;
import com.k8bas.skyblockutility.ui.widget.PointerShape;
import com.k8bas.skyblockutility.ui.widget.SavesOnClose;
import com.k8bas.skyblockutility.ui.widget.Widget;
import com.k8bas.skyblockutility.ui.widget.WidgetScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The HUD editor (REQ-HUD-07, REQ-HUD-08, REQ-HUD-09): every registered element drawn once over a dimmed
 * backdrop, with its live content or, without any, its preview, an outline by state and its name; one
 * whose feature is off greyed with "(disabled)". Drag to move, the wheel to scale, arrows to nudge. The
 * file is written on Save, on a reset and when the editor closes by any route: Esc and an external close
 * save as Save does (R12); only Cancel puts every element back where it was. Needs no world (EC-HUD-08).
 * The buttons and the status line sit in a card at the top centre, so the hotbar stays free, with the
 * instructions in a hint panel below it that fades once the player has started (REQ-HUD-08, R47).
 */
public final class HudEditorScreen extends WidgetScreen implements SavesOnClose {
	public static final String TITLE = "Edit HUD layout";
	/** The hint panel's two lines (REQ-HUD-08). */
	static final String HINT_MOVE = "Drag to move · Scroll to scale";
	static final String HINT_KEYS = "Arrows nudge (Shift: 10 px) · Esc saves";
	static final String RESET_ALL = "Reset All";
	/** Reset All while it waits for its second click (REQ-HUD-08). */
	static final String CONFIRM_RESET = "Confirm reset";
	/** The toolbar card's distance from the top, its padding and the gap between its buttons, in GUI px. */
	static final int CARD_TOP = 6;
	static final int CARD_PAD = 6;
	private static final int BUTTON_GAP = 4;
	private static final int[] BUTTON_WIDTHS = {90, 80, 60, 60};
	private static final int BUTTON_HEIGHT = 18;
	/** The hint panel's gap below the card, in GUI px. */
	private static final int HINT_GAP = 4;
	static final String NOTHING_SELECTED = "Click a HUD to select it";
	/** The empty editor's card (REQ-UI-54): its title and its line. */
	static final String NO_ELEMENTS = "No HUD elements yet";
	static final String NO_ELEMENTS_LINE = "Features that show something on screen add it here.";
	/** Boxes in the top this many pixels have their name below them. */
	static final int LABEL_FLIP = 48;
	static final int GREY = 0xB0202226;
	static final int OUTLINE_IDLE = 0xFF8A9099;
	static final int PANEL = 0xE0141619;

	private final Screen parent;
	private final List<HudElement> elements;
	private final HudEditorModel model;
	private final Canvas canvas;
	private final Button resetSelected;
	private final Button resetAll;
	private final Button cancel;
	private final Button save;
	/** The ids drawn in the last frame, in order (AC-HUD-08). */
	private final List<String> drawn = new ArrayList<>();
	/** Each element's entry as written when the editor opened (null for none), for Cancel. */
	private final Map<String, JsonElement> openedEntries = new HashMap<>();
	/** Elements whose entry this editor has written (a reset); Cancel puts theirs back. */
	private final Set<String> written = new HashSet<>();
	private boolean finished;
	/**
	 * The hint panel's and the card's fades and Reset All's second click, on this screen's own clock source, read when asked:
	 * the arrow keys are handled here before {@code WidgetScreen} sets the frame's clock.
	 */
	private final HudToolbar toolbar = new HudToolbar(UiTime.of(() -> getTime().millis(), () -> getTime().motion()));
	/** Whether the element pressed now has been moved by the mouse: only then is it "dragged" for the card and the hint. */
	private boolean draggedSincePress;
	/** The toolbar card, {x, y, w, h} in GUI px, as laid out last. */
	private final int[] card = new int[4];
	/** The hint panel as drawn last, {x, y, w, h} in GUI px, and its opacity then; the card's opacity then. */
	private final int[] hintPanel = new int[4];
	private float hintAlpha = 1F;
	private float cardAlpha = 1F;
	/** The empty editor's card as drawn last, {x, y, w, h} in GUI px; null while there are elements. */
	private int[] emptyCard;
	/** Each element's name chip as drawn last, {x, y, w, h} in GUI px (for gametests). */
	private final Map<String, int[]> labelChips = new HashMap<>();
	/** The selected element's fill: the accent at 15 % (REQ-HUD-07). */
	static final int SELECTED_FILL = Math.round(0.15F * 255F);
	/** A hovered element's fill: white at 6 % (REQ-HUD-07). */
	static final int HOVER_FILL = Math.round(0.06F * 255F) << 24 | 0xFFFFFF;
	/** A name chip's padding at each side and its corners, in GUI px (REQ-HUD-07). */
	static final int CHIP_PAD = 3;
	static final int CHIP_RADIUS = 3;

	public HudEditorScreen(Screen parent) {
		this(parent, null);
	}

	/** @param select the element to start with selected (an "Edit position" button), or null */
	public HudEditorScreen(Screen parent, String select) {
		this(parent, select, UiClock.GAME);
	}

	/** On a UI clock of its own (a gametest's test clock). */
	HudEditorScreen(Screen parent, String select, UiTime time) {
		super(Component.literal(TITLE));
		setTime(time);
		this.parent = parent;
		this.elements = HudRegistry.elements();
		List<HudEditorModel.Item> items = new ArrayList<>();
		Map<String, HudPosition> opened = new LinkedHashMap<>();
		for (HudElement element : elements) {
			items.add(new HudEditorModel.Item(element.id(), element.defaultPosition()));
			opened.put(element.id(), HudPositions.LIVE.get(element.id(), element.defaultPosition()));
			openedEntries.put(element.id(), HudPositions.LIVE.raw(element.id()));
		}
		this.model = new HudEditorModel(items, opened, new HudEditorModel.Sizes() {
			@Override
			public double width(String id) {
				return shown(element(id)).width(measure());
			}

			@Override
			public double height(String id) {
				return shown(element(id)).height(measure());
			}
		});
		if (select != null && opened.containsKey(select)) {
			model.select(select);
		}
		canvas = add(new Canvas());
		resetSelected = add(new Button("Reset Selected", Button.Style.NORMAL, () -> {
			model.resetSelected();
			write();
		}, UiSound::click));
		// Two clicks (REQ-HUD-08): the first asks, a second within 3 s resets every element.
		resetAll = add(new Button(RESET_ALL, Button.Style.NORMAL, () -> {
			if (toolbar.resetAllClicked()) {
				model.resetAll();
				write();
			}
			relabelResetAll();
		}, UiSound::click));
		cancel = add(new Button("Cancel", Button.Style.NORMAL, this::cancelAndClose, UiSound::click));
		save = add(new Button("Save", Button.Style.PRIMARY, this::onClose, UiSound::click));
	}

	/** Opens the editor over the current screen, which it returns to; with {@code select} selected, or none (null). */
	public static void openEditor(String select) {
		Minecraft client = Minecraft.getInstance();
		client.gui.setScreen(new HudEditorScreen(client.gui.screen(), select));
	}

	HudEditorModel model() {
		return model;
	}

	/** The ids drawn in the last frame (for tests). */
	List<String> drawnLastFrame() {
		return List.copyOf(drawn);
	}

	Button saveButton() {
		return save;
	}

	Button cancelButton() {
		return cancel;
	}

	Button resetSelectedButton() {
		return resetSelected;
	}

	Button resetAllButton() {
		return resetAll;
	}

	/** The toolbar card, {x, y, w, h} in GUI px (for gametests). */
	int[] toolbarCardForTests() {
		return card.clone();
	}

	/** The hint panel as drawn last, {x, y, w, h} in GUI px (for gametests). */
	int[] hintPanelForTests() {
		return hintPanel.clone();
	}

	/** The hint panel's and the card's opacity in the last frame (for gametests). */
	float hintAlphaForTests() {
		return hintAlpha;
	}

	float cardAlphaForTests() {
		return cardAlpha;
	}

	/**
	 * REQ-HUD-08: one card centred at the top, 6 GUI px down, the status line over the four buttons; the hotbar stays free.
	 */
	@Override
	protected void layout() {
		canvas.setBounds(0, 0, width, height);
		Button[] buttons = {resetSelected, resetAll, cancel, save};
		int row = BUTTON_GAP * (buttons.length - 1);
		for (int w : BUTTON_WIDTHS) {
			row += w;
		}
		card[2] = Math.min(row + 2 * CARD_PAD, width - 4);
		card[3] = CARD_PAD + font.lineHeight + 5 + BUTTON_HEIGHT + CARD_PAD;
		card[0] = (width - card[2]) / 2;
		card[1] = CARD_TOP;
		int x = card[0] + (card[2] - row) / 2;
		int y = card[1] + card[3] - CARD_PAD - BUTTON_HEIGHT;
		for (int i = 0; i < buttons.length; i++) {
			buttons[i].setBounds(x, y, BUTTON_WIDTHS[i], BUTTON_HEIGHT);
			x += BUTTON_WIDTHS[i] + BUTTON_GAP;
		}
		resetSelected.setEnabled(model.selected() != null);
		relabelResetAll();
	}

	/** Reset All as "Confirm reset" in the destructive style while it waits for its second click, else as it was. */
	private void relabelResetAll() {
		if (toolbar.confirming()) {
			resetAll.relabel(CONFIRM_RESET, Button.Style.DESTRUCTIVE);
		} else {
			resetAll.relabel(RESET_ALL, Button.Style.NORMAL);
		}
	}

	/** The card's buttons fade with it while an element is dragged over it (REQ-HUD-08). */
	@Override
	protected float widgetOpacity(Widget widget) {
		return widget == canvas ? 1F : cardAlpha;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractDimmedGame(graphics, partialTick);
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		drawn.clear();
		labelChips.clear();
		String hovered = mouseX < 0 ? null : model.at(mouseX, mouseY, width, height);
		int accent = Theme.current().accent();
		// REQ-HUD-08: the toolbar card first, so an element under it is drawn above its background and below its buttons; at
		// 30 % while an element is dragged over it.
		String dragged = model.dragging() && draggedSincePress ? model.selected() : null;
		cardAlpha = toolbar.cardAlpha(dragged != null && overCard(model.rect(dragged, width, height)));
		float fade = UiFade.enter(cardAlpha);
		try {
			Shapes.shadow(graphics, card[0], card[1], card[2], card[3], Shapes.RADIUS_CARD);
			Shapes.roundedRect(graphics, card[0], card[1], card[2], card[3], Shapes.RADIUS_CARD, Theme.PANEL);
		} finally {
			UiFade.leave(fade);
		}
		graphics.nextStratum();
		List<HudRect> rects = new ArrayList<>();
		for (HudElement element : elements) {
			// REQ-HUD-07: the selected element on the accent at 15 %, a hovered one on white at 6 %, under its content.
			String id = element.id();
			int fill = id.equals(model.selected()) ? ColorMath.withAlpha(SELECTED_FILL, accent & 0xFFFFFF)
					: id.equals(hovered) ? HOVER_FILL : 0;
			if (fill != 0) {
				HudRect at = model.rect(id, width, height);
				Shapes.roundedRect(graphics, (float) at.x(), (float) at.y(), (int) Math.ceil(at.w()), (int) Math.ceil(at.h()), Shapes.RADIUS_CONTROL, fill);
				graphics.nextStratum();
			}
			rects.add(HudRenderer.drawAt(graphics, font, shown(element), model.positions().get(id)));
			drawn.add(id);
		}
		graphics.nextStratum();
		for (int i = 0; i < elements.size(); i++) {
			HudElement element = elements.get(i);
			HudRect rect = rects.get(i);
			int x = (int) rect.x();
			int y = (int) rect.y();
			int w = (int) Math.ceil(rect.w());
			int h = (int) Math.ceil(rect.h());
			boolean disabled = !element.enabled();
			if (disabled) {
				// Rounded as its outline is, so no grey corner shows outside it.
				Shapes.roundedRect(graphics, x, y, w, h, Shapes.RADIUS_CONTROL, GREY);
			}
			String id = element.id();
			int outline = id.equals(model.selected()) ? accent : id.equals(hovered) ? Theme.TEXT_PRIMARY : disabled ? Theme.TEXT_DISABLED : OUTLINE_IDLE;
			// REQ-HUD-07: a rounded hairline at the control radius.
			Shapes.hairlineAround(graphics, x, y, w, h, Shapes.RADIUS_CONTROL, outline);
			// The name above the box, below it in the top 48 px, inside its top edge when below would leave the
			// window; always inside the window, its chip too.
			String label = Ellipsis.fit(element.displayName() + (disabled ? " (disabled)" : ""), width - 4 - 2 * CHIP_PAD, font::width);
			int labelX = Math.max(2 + CHIP_PAD, Math.min(x, width - font.width(label) - 2 - CHIP_PAD));
			int labelY = y < LABEL_FLIP ? y + h + 3 : y - font.lineHeight - 2;
			if (labelY + font.lineHeight > height) {
				labelY = Math.max(2, y + 2);
			}
			// REQ-HUD-07: the name on a chip, a rounded panel-coloured box with 3 GUI px at each side.
			int[] chip = {labelX - CHIP_PAD, labelY - 2, font.width(label) + 2 * CHIP_PAD, font.lineHeight + 2};
			Shapes.roundedRect(graphics, chip[0], chip[1], chip[2], chip[3], CHIP_RADIUS, PANEL);
			labelChips.put(id, chip);
			UiText.draw(graphics, font, label, labelX, labelY, disabled ? Theme.TEXT_SECONDARY : Theme.TEXT_PRIMARY);
		}
		// While a drag is snapped, a hairline accent guide per target it lies on (REQ-HUD-14, REQ-UI-33), under the status panel.
		List<HudSnap.Guide> guides = model.guides(width, height);
		for (int i = 0; i < guides.size(); i++) {
			HudSnap.Guide guide = guides.get(i);
			if (guide.vertical()) {
				Shapes.hairlineV(graphics, guide.at(), 0, height, accent);
			} else {
				Shapes.hairlineH(graphics, 0, guide.at(), width, accent);
			}
		}
		if (elements.isEmpty()) {
			drawEmptyCard(graphics);
		}
		// The status line on the card, over any element under it, as its buttons are.
		graphics.nextStratum();
		fade = UiFade.enter(cardAlpha);
		try {
			String status = Ellipsis.fit(status(), card[2] - 2 * CARD_PAD, font::width);
			UiText.centred(graphics, font, status, card[0] + card[2] / 2, card[1] + CARD_PAD, Theme.TEXT_PRIMARY);
		} finally {
			UiFade.leave(fade);
		}
		// Resting there, not dragging an element across it.
		drawHint(graphics, dragged == null && mouseX >= 0 && overCard(new HudRect(mouseX, mouseY, 1, 1)));
	}

	/** Whether a rectangle reaches into the toolbar card. */
	private boolean overCard(HudRect rect) {
		return rect.x() < card[0] + card[2] && rect.x() + rect.w() > card[0] && rect.y() < card[1] + card[3] && rect.y() + rect.h() > card[1];
	}

	/** REQ-HUD-08: the instructions in two lines on a panel just below the card, as faded as the toolbar says. */
	private void drawHint(GuiGraphicsExtractor graphics, boolean pointerOnCard) {
		hintAlpha = toolbar.hintAlpha(pointerOnCard);
		hintPanel[2] = Math.min(Math.max(font.width(HINT_MOVE), font.width(HINT_KEYS)) + 16, width - 4);
		hintPanel[3] = 2 * 12 + 6;
		hintPanel[0] = (width - hintPanel[2]) / 2;
		hintPanel[1] = card[1] + card[3] + HINT_GAP;
		if (hintAlpha <= 0F) {
			return;
		}
		float fade = UiFade.enter(hintAlpha);
		try {
			graphics.nextStratum();
			Shapes.roundedRect(graphics, hintPanel[0], hintPanel[1], hintPanel[2], hintPanel[3], Shapes.RADIUS_CONTROL, PANEL);
			graphics.nextStratum();
			UiText.centred(graphics, font, HINT_MOVE, width / 2, hintPanel[1] + 4, Theme.TEXT_SECONDARY);
			UiText.centred(graphics, font, HINT_KEYS, width / 2, hintPanel[1] + 16, Theme.TEXT_SECONDARY);
		} finally {
			UiFade.leave(fade);
		}
	}

	/**
	 * REQ-UI-54: with no element registered, a card at the screen's centre says so; the status line stays empty then. In
	 * play the shipped "Corpse ding" is always registered, so tests show it with an empty registry.
	 */
	private void drawEmptyCard(GuiGraphicsExtractor graphics) {
		int cardW = Math.max(font.width(NO_ELEMENTS), font.width(NO_ELEMENTS_LINE)) + 24;
		int cardH = 2 * 12 + 14;
		int cardX = (width - cardW) / 2;
		int cardY = (height - cardH) / 2;
		Shapes.shadow(graphics, cardX, cardY, cardW, cardH, Shapes.RADIUS_CARD);
		Shapes.roundedRect(graphics, cardX, cardY, cardW, cardH, Shapes.RADIUS_CARD, PANEL);
		graphics.nextStratum();
		UiText.centred(graphics, font, NO_ELEMENTS, width / 2, cardY + 8, Theme.TEXT_PRIMARY);
		UiText.centred(graphics, font, NO_ELEMENTS_LINE, width / 2, cardY + 20, Theme.TEXT_SECONDARY);
		emptyCard = new int[] {cardX, cardY, cardW, cardH};
	}

	/** Selects an element, or nothing for null, as a click on it does (for gametests). */
	void selectForTests(String id) {
		model.select(id);
	}

	/** An element's name chip as drawn last, {x, y, w, h} in GUI px, or null (for gametests). */
	int[] labelChipForTests(String id) {
		return labelChips.get(id);
	}

	/** The empty editor's card as drawn last, {x, y, w, h} in GUI px, or null (for gametests). */
	int[] emptyCardForTests() {
		return emptyCard;
	}

	/** The selected element's name, place and scale, or how to select one; nothing with no element (REQ-UI-54). */
	String status() {
		if (elements.isEmpty()) {
			return "";
		}
		String selected = model.selected();
		if (selected == null) {
			return NOTHING_SELECTED;
		}
		HudRect rect = model.rect(selected, width, height);
		return String.format(Locale.ROOT, "%s   X %d   Y %d   Scale %.2f", element(selected).displayName(), (int) rect.x(), (int) rect.y(),
				model.positions().get(selected).scale());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (overlay() == null) {
			int step = shift(event) ? 10 : 1;
			switch (event.key()) {
				case GLFW.GLFW_KEY_ESCAPE -> {
					onClose();
					return true;
				}
				case GLFW.GLFW_KEY_LEFT -> {
					nudge(-step, 0);
					return true;
				}
				case GLFW.GLFW_KEY_RIGHT -> {
					nudge(step, 0);
					return true;
				}
				case GLFW.GLFW_KEY_UP -> {
					nudge(0, -step);
					return true;
				}
				case GLFW.GLFW_KEY_DOWN -> {
					nudge(0, step);
					return true;
				}
				default -> {
				}
			}
		}
		return super.keyPressed(event);
	}

	/** A nudge of the selection, which starts the hint's fade when there is one (REQ-HUD-08). */
	private void nudge(int dx, int dy) {
		if (model.selected() != null) {
			toolbar.acted();
		}
		model.nudge(dx, dy, width, height);
	}

	/** Save, and Esc: written, then back to the opener. */
	@Override
	public void onClose() {
		finish();
		minecraft.gui.setScreen(parent);
	}

	/** Every element back where it was when the editor opened, its entry as it was written then. */
	private void cancelAndClose() {
		model.cancel();
		onClose();
	}

	@Override
	public void saveBeforeShutdown() {
		finish();
	}

	/** An external close (a disconnect, another screen) saves as Esc does (R12, EC-HUD-07). */
	@Override
	public void removed() {
		finish();
		super.removed();
	}

	private void finish() {
		if (!finished) {
			finished = true;
			write();
		}
	}

	/**
	 * The changed positions into the config, then one write; none when nothing changed. An element left where
	 * it was keeps its entry exactly as written, a malformed one included (EC-HUD-05), and one a reset wrote
	 * gets that entry back; one that had no entry and is at its default gets none.
	 */
	private void write() {
		boolean touched = false;
		for (HudElement element : elements) {
			String id = element.id();
			HudPosition position = model.positions().get(id);
			JsonElement openedEntry = openedEntries.get(id);
			if (position.equals(model.opened(id))) {
				if (written.remove(id)) {
					HudPositions.LIVE.restore(id, openedEntry);
					touched = true;
				}
				continue;
			}
			if (openedEntry == null && position.equals(element.defaultPosition())) {
				HudPositions.LIVE.restore(id, null);
			} else {
				HudPositions.LIVE.set(id, position);
			}
			written.add(id);
			touched = true;
		}
		if (touched) {
			ConfigManager.save();
		}
	}

	private HudElement element(String id) {
		for (HudElement element : elements) {
			if (element.id().equals(id)) {
				return element;
			}
		}
		throw new IllegalArgumentException(id);
	}

	/** Its live content, else its preview, else its name: always something to grab. */
	private static HudContent shown(HudElement element) {
		HudContent live = element.content();
		if (live != null) {
			return live;
		}
		HudContent preview = element.preview();
		return preview != null ? preview : HudText.of(List.of(element.displayName()), line -> true);
	}

	private static TextMeasure measure() {
		return TextMeasure.of(Minecraft.getInstance().font);
	}

	/** The whole screen under the buttons: presses, drags and the wheel go to the model. */
	private final class Canvas extends Widget {
		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		}

		/** The move pointer over an element, which a press grabs (REQ-UI-43). */
		@Override
		public PointerShape cursorAt(double mouseX, double mouseY) {
			return model.at(mouseX, mouseY, HudEditorScreen.this.width, HudEditorScreen.this.height) != null ? PointerShape.MOVE : PointerShape.DEFAULT;
		}

		/** And while one is dragged, wherever the mouse goes. */
		@Override
		public PointerShape dragCursor() {
			return model.dragging() ? PointerShape.MOVE : null;
		}

		@Override
		public boolean press(double mouseX, double mouseY, int button) {
			if (button != 0) {
				return false;
			}
			draggedSincePress = false;
			// A press on the card that misses its buttons and every element (its padding, the gap between two buttons) changes
			// nothing, rather than dropping the selection as a press on empty space does.
			if (overCard(new HudRect(mouseX, mouseY, 0, 0)) && model.at(mouseX, mouseY, HudEditorScreen.this.width, HudEditorScreen.this.height) == null) {
				return true;
			}
			model.press(mouseX, mouseY, HudEditorScreen.this.width, HudEditorScreen.this.height);
			return true;
		}

		@Override
		public void drag(double mouseX, double mouseY) {
			// Snaps unless Alt is held (REQ-HUD-14); read live, as a drag event carries the modifiers of its press.
			model.drag(mouseX, mouseY, HudEditorScreen.this.width, HudEditorScreen.this.height, !minecraft.hasAltDown());
			if (model.dragging()) {
				draggedSincePress = true;
				toolbar.acted();
			}
		}

		@Override
		public void release(double mouseX, double mouseY) {
			model.release(HudEditorScreen.this.width, HudEditorScreen.this.height);
			draggedSincePress = false;
		}

		@Override
		public boolean scroll(double mouseX, double mouseY, int notches, boolean fine) {
			boolean scaled = model.scroll(mouseX, mouseY, notches, HudEditorScreen.this.width, HudEditorScreen.this.height);
			if (scaled) {
				toolbar.acted();
			}
			return scaled;
		}
	}
}
