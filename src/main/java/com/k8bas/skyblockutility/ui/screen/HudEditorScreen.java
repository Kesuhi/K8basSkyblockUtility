package com.k8bas.skyblockutility.ui.screen;

import com.google.gson.JsonElement;
import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.hud.HudContent;
import com.k8bas.skyblockutility.hud.HudEditorModel;
import com.k8bas.skyblockutility.hud.HudElement;
import com.k8bas.skyblockutility.hud.HudPosition;
import com.k8bas.skyblockutility.hud.HudPositions;
import com.k8bas.skyblockutility.hud.HudRect;
import com.k8bas.skyblockutility.hud.HudRegistry;
import com.k8bas.skyblockutility.hud.HudRenderer;
import com.k8bas.skyblockutility.hud.HudText;
import com.k8bas.skyblockutility.hud.TextMeasure;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiSound;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.widget.Button;
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
 */
public final class HudEditorScreen extends WidgetScreen implements SavesOnClose {
	public static final String TITLE = "Edit HUD layout";
	static final String HINT = "Drag to move · Scroll to scale · Arrows nudge (Shift: 10 px) · Esc saves";
	static final String NOTHING_SELECTED = "Click a HUD to select it";
	static final String NO_ELEMENTS = "No HUD elements yet: features that show one on screen add it here";
	/** Boxes in the top this many pixels have their name below them. */
	static final int LABEL_FLIP = 48;
	private static final int GREY = 0xB0202226;
	private static final int OUTLINE_IDLE = 0xFF8A9099;
	private static final int PANEL = 0xE0141619;

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

	public HudEditorScreen(Screen parent) {
		this(parent, null);
	}

	/** @param select the element to start with selected (an "Edit position" button), or null */
	public HudEditorScreen(Screen parent, String select) {
		super(Component.literal(TITLE));
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
		resetAll = add(new Button("Reset All", Button.Style.NORMAL, () -> {
			model.resetAll();
			write();
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

	@Override
	protected void layout() {
		canvas.setBounds(0, 0, width, height);
		int[] widths = {96, 72, 64, 64};
		Button[] buttons = {resetSelected, resetAll, cancel, save};
		int total = 3 * 6;
		for (int w : widths) {
			total += w;
		}
		int x = (width - total) / 2;
		for (int i = 0; i < buttons.length; i++) {
			buttons[i].setBounds(x, height - 26, widths[i], 18);
			x += widths[i] + 6;
		}
		resetSelected.setEnabled(model.selected() != null);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractDimmedGame(graphics, partialTick);
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		drawn.clear();
		String hovered = mouseX < 0 ? null : model.at(mouseX, mouseY, width, height);
		List<HudRect> rects = new ArrayList<>();
		for (HudElement element : elements) {
			rects.add(HudRenderer.drawAt(graphics, font, shown(element), model.positions().get(element.id())));
			drawn.add(element.id());
		}
		graphics.nextStratum();
		int accent = Theme.current().accent();
		for (int i = 0; i < elements.size(); i++) {
			HudElement element = elements.get(i);
			HudRect rect = rects.get(i);
			int x = (int) rect.x();
			int y = (int) rect.y();
			int w = (int) Math.ceil(rect.w());
			int h = (int) Math.ceil(rect.h());
			boolean disabled = !element.enabled();
			if (disabled) {
				graphics.fill(x, y, x + w, y + h, GREY);
			}
			String id = element.id();
			int outline = id.equals(model.selected()) ? accent : id.equals(hovered) ? Theme.TEXT_PRIMARY : disabled ? Theme.TEXT_DISABLED : OUTLINE_IDLE;
			Shapes.outline(graphics, x - 1, y - 1, w + 2, h + 2, outline);
			// The name above the box, below it in the top 48 px, inside its top edge when below would leave the
			// window; always inside the window.
			String label = Ellipsis.fit(element.displayName() + (disabled ? " (disabled)" : ""), width - 4, font::width);
			int labelX = Math.max(2, Math.min(x, width - font.width(label) - 2));
			int labelY = y < LABEL_FLIP ? y + h + 3 : y - font.lineHeight - 2;
			if (labelY + font.lineHeight > height) {
				labelY = Math.max(2, y + 2);
			}
			UiText.draw(graphics, font, label, labelX, labelY, disabled ? Theme.TEXT_SECONDARY : Theme.TEXT_PRIMARY);
		}
		// Above the buttons, clear of the top edge where HUDs usually sit, on a panel over the game's own HUD.
		String status = status();
		int panelW = Math.max(font.width(status), font.width(HINT)) + 16;
		int panelY = height - 26 - 2 * 12 - 5;
		Shapes.roundedRect(graphics, (width - panelW) / 2, panelY, panelW, 2 * 12 + 6, Shapes.RADIUS_CONTROL, PANEL);
		graphics.nextStratum();
		UiText.centred(graphics, font, status, width / 2, panelY + 4, Theme.TEXT_PRIMARY);
		UiText.centred(graphics, font, HINT, width / 2, panelY + 16, Theme.TEXT_SECONDARY);
	}

	/** The selected element's name, place and scale, or how to select one. */
	String status() {
		if (elements.isEmpty()) {
			return NO_ELEMENTS;
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
					model.nudge(-step, 0, width, height);
					return true;
				}
				case GLFW.GLFW_KEY_RIGHT -> {
					model.nudge(step, 0, width, height);
					return true;
				}
				case GLFW.GLFW_KEY_UP -> {
					model.nudge(0, -step, width, height);
					return true;
				}
				case GLFW.GLFW_KEY_DOWN -> {
					model.nudge(0, step, width, height);
					return true;
				}
				default -> {
				}
			}
		}
		return super.keyPressed(event);
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

		@Override
		public boolean press(double mouseX, double mouseY, int button) {
			if (button != 0) {
				return false;
			}
			model.press(mouseX, mouseY, HudEditorScreen.this.width, HudEditorScreen.this.height);
			return true;
		}

		@Override
		public void drag(double mouseX, double mouseY) {
			model.drag(mouseX, mouseY, HudEditorScreen.this.width, HudEditorScreen.this.height);
		}

		@Override
		public void release(double mouseX, double mouseY) {
			model.release(HudEditorScreen.this.width, HudEditorScreen.this.height);
		}

		@Override
		public boolean scroll(double mouseX, double mouseY, int notches, boolean fine) {
			return model.scroll(mouseX, mouseY, notches, HudEditorScreen.this.width, HudEditorScreen.this.height);
		}
	}
}
