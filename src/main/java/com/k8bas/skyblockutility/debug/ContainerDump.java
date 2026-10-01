package com.k8bas.skyblockutility.debug;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.util.ChatUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * `/ksu debug dump containers on|off` (T0.4b, REQ-GS-13). While armed, each allowlisted menu the
 * player opens is written to latest.log once its contents are stable. A command cannot be typed
 * while a menu is open, hence the arming. Passive: it only reads the open screen on the client
 * tick and never clicks, pages or opens anything. Disarms itself after 60 minutes (REQ-GS-12).
 */
public final class ContainerDump {
	private static final CaptureArm ARM = new CaptureArm(System::currentTimeMillis);
	private static final StableContents STABLE = new StableContents();
	private static Screen trackedScreen;
	private static int captureCount;

	private ContainerDump() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ContainerDump::onTick);
	}

	public static void arm() {
		ARM.arm();
		ChatUtils.chat("Container dump armed for 60 minutes; open the menus to capture");
	}

	public static void disarm() {
		ARM.disarm();
		ChatUtils.chat("Container dump disarmed");
	}

	private static void onTick(Minecraft client) {
		if (ARM.expiredNow()) {
			ChatUtils.chat("Container dump disarmed after 60 minutes");
		}
		Screen screen = client.gui.screen();
		if (!ARM.isArmed() || !(screen instanceof AbstractContainerScreen<?> containerScreen)) {
			trackedScreen = null;
			return;
		}
		String title = containerScreen.getTitle().getString();
		if (!CaptureAllowlist.matches(title)) {
			trackedScreen = null;
			return;
		}
		if (screen != trackedScreen) {
			trackedScreen = screen;
			STABLE.reset();
		}
		AbstractContainerMenu menu = containerScreen.getMenu();
		List<Slot> top = topSlots(menu);
		if (STABLE.onTick(menu.getStateId(), fingerprint(top), emptyFingerprint(top.size()))) {
			write(++captureCount, title, menu.getStateId(), top);
		}
	}

	/** The menu's own slots, without the player inventory below it. */
	private static List<Slot> topSlots(AbstractContainerMenu menu) {
		List<Slot> slots = new ArrayList<>();
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory)) {
				slots.add(slot);
			}
		}
		return slots;
	}

	private static long fingerprint(List<Slot> slots) {
		long hash = slots.size();
		for (Slot slot : slots) {
			ItemStack stack = slot.getItem();
			hash = 31 * hash + (stack.isEmpty() ? 0 : Objects.hash(BuiltInRegistries.ITEM.getKey(stack.getItem()),
					stack.getCount(), stack.getHoverName().getString(), loreLines(stack)));
		}
		return hash;
	}

	private static long emptyFingerprint(int slotCount) {
		long hash = slotCount;
		for (int i = 0; i < slotCount; i++) {
			hash = 31 * hash;
		}
		return hash;
	}

	private static void write(int capture, String title, int stateId, List<Slot> slots) {
		String prefix = DebugCommand.DUMP_TAG + " container " + capture;
		NameMasker masker = NameMasker.SESSION;
		masker.learnFrom(Minecraft.getInstance());
		K8basSkyblockUtilityClient.LOGGER.info("{} title={} slots={} state={}", prefix, masker.mask(title), slots.size(), stateId);
		for (int i = 0; i < slots.size(); i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			K8basSkyblockUtilityClient.LOGGER.info("{} slot {} item={} sbid={} count={} | {}", prefix, i,
					BuiltInRegistries.ITEM.getKey(stack.getItem()), skyblockId(stack), stack.getCount(), masker.mask(stack.getHoverName().getString()));
			List<String> lore = loreLines(stack);
			for (int line = 0; line < lore.size(); line++) {
				K8basSkyblockUtilityClient.LOGGER.info("{} slot {} lore {} | {}", prefix, i, line, masker.mask(lore.get(line)));
			}
		}
		ChatUtils.chat("Captured \"" + masker.mask(title) + "\" to latest.log");
	}

	private static List<String> loreLines(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/** SkyBlock's own item id from the custom data, or "-" for items without one. */
	private static String skyblockId(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "-" : data.copyTag().getStringOr("id", "-");
	}
}
