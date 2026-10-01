#!/usr/bin/env bash
# Forbidden-reference check, run in CI on every push and PR (A-10). It fails on:
#   1. packet cancelling: `cancellable = true` or `.cancel()` in a mixin that targets a network class
#      (REQ-XC-RULES-02, AC-XC-01)
#   2. direct OpenGL: `org.lwjgl.opengl` or the blaze3d GL wrappers (REQ-PORT-12, AC-PORT-12)
#   3. wording: "ESP", "x-ray", "through walls", "cheat" or "gambling" in the README, fabric.mod.json or
#      en_us.json; "gambling" is allowed only as a search keyword (REQ-XC-RULES-07)
#   4. wiki, Fandom, hypixel.net and Reddit hosts in code that could fetch them (REQ-XC-LICENSE-05).
#      api.hypixel.net is the official API and is allowed. Bundled data `sources` records and test
#      fixtures are not code; the data validator checks those.
#
# Usage: scripts/check-forbidden.sh [<root>]     check a source tree (default: the current directory)
#        scripts/check-forbidden.sh --self-test   check that seeded violations fail and allowed cases pass
set -uo pipefail

check_tree() {
	local root=$1 found=0 f line
	hit() { echo "check-forbidden: $1" >&2; found=1; }

	# 1. Packet-cancelling mixins.
	while IFS= read -r f; do
		if grep -qE '^import net\.minecraft\.network\.|^import [a-z.]+\.[A-Za-z]*(PacketListener|Connection)[A-Za-z]*;|@Mixin\([^)]*(Packet|Connection)[A-Za-z]*\.class' "$f"; then
			while IFS= read -r line; do hit "packet-cancelling mixin: $f:$line"; done \
				< <(grep -nE 'cancellable *= *true|\.cancel\(\)' "$f")
		fi
	done < <(grep -rlE '^\s*@Mixin\(' "$root/src" --include='*.java' 2>/dev/null)

	# 2. Direct OpenGL.
	while IFS= read -r line; do hit "direct OpenGL: $line"; done \
		< <(grep -rnE 'org\.lwjgl\.opengl|com\.mojang\.blaze3d\.opengl|\bGlStateManager\b' "$root/src" --include='*.java' 2>/dev/null)

	# 3. Wording in user-facing text.
	local -a texts=()
	[[ -f $root/README.md ]] && texts+=("$root/README.md")
	[[ -f $root/src/main/resources/fabric.mod.json ]] && texts+=("$root/src/main/resources/fabric.mod.json")
	while IFS= read -r f; do texts+=("$f"); done < <(find "$root/src" -name en_us.json 2>/dev/null)
	for f in "${texts[@]}"; do
		while IFS= read -r line; do hit "forbidden wording: $f:$line"; done \
			< <(grep -nE '\bESP\b' "$f"; grep -niE 'x-?ray|through[ -]walls?|\bcheats?\b' "$f"; \
				grep -niE 'gambling' "$f" | grep -vE '^[0-9]+:\s*"[^"]*keywords?[^"]*"\s*:')
	done

	# 4. Wiki, Fandom, hypixel.net and Reddit hosts in code.
	local -a code=()
	for f in "$root/src/main/java" "$root/src/client/java" "$root/tools"; do [[ -d $f ]] && code+=("$f"); done
	if [[ ${#code[@]} -gt 0 ]]; then
		while IFS= read -r line; do hit "scraping-host reference in code: $line"; done \
			< <(grep -rnoE '([A-Za-z0-9-]+\.)*(fandom\.com|minecraft\.wiki|hypixel\.net|reddit\.com|redd\.it)' "${code[@]}" \
				| grep -vE ':api\.hypixel\.net$')
	fi
	return "$found"
}

self_test() {
	local tmp fails=0
	tmp=$(mktemp -d)
	trap 'rm -rf "$tmp"' RETURN
	mk() { mkdir -p "$(dirname "$1")"; cat > "$1"; }
	expect() { # <case> <pass|fail>
		local got=pass
		check_tree "$tmp/$1" 2>/dev/null || got=fail
		if [[ $got != "$2" ]]; then echo "self-test FAILED: $1 should $2 but did $got" >&2; fails=1; fi
	}
	mk "$tmp/net-cancel/src/main/java/a/mixin/NetMixin.java" <<'EOF'
import net.minecraft.network.Connection;
@Mixin(Connection.class)
class NetMixin { @Inject(method = "send", at = @At("HEAD"), cancellable = true) void k(CallbackInfo ci) { ci.cancel(); } }
EOF
	mk "$tmp/glow-cancel/src/main/java/a/mixin/GlowMixin.java" <<'EOF'
import net.minecraft.client.Minecraft;
@Mixin(Minecraft.class)
class GlowMixin { @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true) void g(CallbackInfoReturnable<Boolean> cir) {} }
EOF
	mk "$tmp/gl/src/main/java/a/Draw.java" <<'EOF'
import org.lwjgl.opengl.GL11;
EOF
	mk "$tmp/wording/README.md" <<'EOF'
Highlights mobs through walls.
EOF
	mk "$tmp/keyword/src/main/resources/assets/x/lang/en_us.json" <<'EOF'
{
  "option.odds.keywords": "odds, gambling, rng",
  "option.odds.title": "Rare Drop Odds"
}
EOF
	mk "$tmp/wiki-code/src/main/java/a/Fetch.java" <<'EOF'
String u = "https://hypixelskyblock.minecraft.wiki/api.php";
EOF
	mk "$tmp/forum-code/src/main/java/a/Fetch.java" <<'EOF'
String u = "https://hypixel.net/threads/123";
EOF
	mk "$tmp/api-code/src/main/java/a/Bazaar.java" <<'EOF'
String u = "https://api.hypixel.net/v2/skyblock/bazaar";
EOF
	mk "$tmp/data-source/src/main/resources/data/odds/supplied.json" <<'EOF'
{ "sources": { "s1": { "kind": "wiki_crosscheck", "url": "https://hypixelskyblock.minecraft.wiki/w/Scatha" } } }
EOF
	expect net-cancel fail
	expect glow-cancel pass
	expect gl fail
	expect wording fail
	expect keyword pass
	expect wiki-code fail
	expect forum-code fail
	expect api-code pass
	expect data-source pass
	[[ $fails -eq 0 ]] && echo "check-forbidden self-test: 9 cases as expected"
	return "$fails"
}

case "${1:-}" in
	--self-test) self_test ;;
	*)
		if check_tree "${1:-.}"; then echo "check-forbidden: clean"; else exit 1; fi ;;
esac
