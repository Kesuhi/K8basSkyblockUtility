package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.util.ChatUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.function.Function;

/**
 * `/ksu update [show | check | install <version> | skip <version> | cancel]` (REQ-UPD-10), a reserved subcommand of
 * `/ksu` (REQ-UI-10). Client-side only: nothing is sent to the server. Every action runs off the render thread and
 * answers with local chat lines; `install` opens the confirmation screen for the candidate on offer, whose click alone
 * downloads (REQ-UPD-11); `skip` asks once more.
 */
public final class UpdateCommand {
	private UpdateCommand() {
	}

	/** The `update` node under `/ksu` and `/kskyblockutility`. */
	public static LiteralArgumentBuilder<FabricClientCommandSource> node(String root) {
		return ClientCommands.literal("update")
				.executes(context -> show(context.getSource().getClient()))
				.then(ClientCommands.literal("show").executes(context -> show(context.getSource().getClient())))
				.then(ClientCommands.literal("check").executes(context -> answer(UpdateActions::check)))
				.then(ClientCommands.literal("install").executes(context -> usage("/" + root + " update install <version>"))
						.then(ClientCommands.argument("version", StringArgumentType.word())
						.executes(context -> {
							// Opens the confirmation screen for that version; its click is the only consent (REQ-UPD-11).
							Updates.requestInstall(context.getSource().getClient(), StringArgumentType.getString(context, "version"));
							return 1;
						})))
				.then(ClientCommands.literal("skip").executes(context -> usage("/" + root + " update skip <version>"))
						.then(ClientCommands.argument("version", StringArgumentType.word())
						.executes(context -> askSkip(root, StringArgumentType.getString(context, "version")))
						// A chat click runs the confirm and the cancel only after Fabric's confirmation window (R55).
						.then(ClientCommands.literal("confirm").requires(FabricClientCommandSource::attended).executes(context -> {
							String version = StringArgumentType.getString(context, "version");
							return answer(actions -> actions.skip(version, true));
						}))))
				.then(ClientCommands.literal("cancel").requires(FabricClientCommandSource::attended).executes(context -> answer(UpdateActions::cancel)));
	}

	/** A subcommand typed without its argument: how to use it; nothing reaches the server (REQ-UI-25). */
	private static int usage(String usage) {
		ChatUtils.chat(Component.literal("Usage: " + usage).withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	/** Runs an action in the background and shows its lines in the chat. */
	private static int answer(Function<UpdateActions, List<String>> action) {
		Updates.run(action).whenComplete((lines, error) -> Minecraft.getInstance().execute(() -> {
			if (error != null) {
				ChatUtils.chat(Component.literal("The update action failed: " + cause(error)).withStyle(ChatFormatting.RED));
				return;
			}
			for (String line : lines) {
				ChatUtils.chat(Component.literal(line).withStyle(ChatFormatting.YELLOW));
			}
		}));
		return 1;
	}

	/** The first step of a skip (REQ-UPD-12): what it does, and a click that confirms it. */
	private static int askSkip(String root, String version) {
		Updates.run(actions -> actions.skip(version, false)).whenComplete((lines, error) -> Minecraft.getInstance().execute(() -> {
			if (error != null || lines == null || lines.isEmpty()) {
				ChatUtils.chat(Component.literal("The update action failed" + (error == null ? "" : ": " + cause(error)))
						.withStyle(ChatFormatting.RED));
				return;
			}
			SemVer parsed = SemVer.Companion.fromTagOrNull(version);
			// Only the question gets the link; "not the update on offer" has nothing to confirm.
			boolean asks = parsed != null && lines.getFirst().equals(UpdateActions.skipQuestion(parsed));
			MutableComponent text = Component.literal(lines.getFirst() + (asks ? " " : "")).withStyle(ChatFormatting.YELLOW);
			if (asks) {
				text.append(Component.literal("[Skip it]").withStyle(Style.EMPTY
						.withColor(ChatFormatting.AQUA)
						.withUnderlined(true)
						.withClickEvent(new ClickEvent.RunCommand("/" + root + " update skip " + version + " confirm"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Skips v" + parsed)))));
			}
			ChatUtils.chat(text);
		}));
		return 1;
	}

	/** The error's own name, not the CompletionException a background action wraps it in. */
	private static String cause(Throwable error) {
		Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
		return cause.getClass().getSimpleName();
	}

	/** Opens the confirmation screen for the candidate on offer, or says there is none; the screen opens on the next frame. */
	private static int show(Minecraft client) {
		Updates.showScreen(client);
		return 1;
	}
}
