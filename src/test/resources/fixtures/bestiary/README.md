# Bestiary chat blocks

`chat-blocks.txt` holds every BESTIARY tier-up and BESTIARY MILESTONE chat block in the maintainer's game logs of the
26.2 Prism instance up to 2026-10-18 (T5.2a, AC-BEST-09). They are written as the chat reader passes server lines to a
parser: one line per chat message, empty messages included, formatting codes removed, each line trimmed.

| Count | |
|---|---|
| 501 | blocks, separated by `----` |
| 462 | tier-ups (`BESTIARY`, then `<Family> <tier> ➡ <tier>`, or `<Family> I` for a family found for the first time) |
| 86 | milestones (`BESTIARY MILESTONE`, then `CCLXI➡CCLXII` or `CCLXI ➡ CCLXII`): 47 inside a tier-up block, 39 on their own; one claims four at once (`CCLXVIII ➡ CCLXXII`) |
| 1 | block without border lines (a dungeon tier-up whose borders the log does not show) |

How they were taken from the logs (a script outside the repository):

1. Every `[CHAT]` line of the instance's logs, in order. Its copies in the `Testing` and `26.1.2 Skyblock` instances
   hold the same lines and were not read; the `Yadi 1.21.11 Skyblock` instance is another game version and was not read.
2. A block starts at each `BESTIARY` or `BESTIARY MILESTONE` line that is not inside the block before it, with the
   border and empty line just above it, and runs to the closing border (at most 16 lines when there is none), so the
   blocks do not depend on the border lines.
3. Formatting codes removed and each line trimmed: the chat reader hands parsers the text without codes, and the
   parsers trim it. The 64-character `▬` border line is written `<border>`; the test expands it again.
4. A chat-stacking mod had appended a counter (` (x2)` up to ` (x4)`) to repeated lines in the log, the `BESTIARY`
   header itself among them. The server never sends it, and the reader sees each line before that mod changes it, so the
   counter was removed.

Every tier and milestone numeral in these logs is Roman; Arabic forms are tested with synthetic lines. The blocks name
families and rewards only, with no player names, server ids or UUIDs (`scripts/privacy-scan.sh --body` is clean).
AC-BEST-09 counted 378 tier-up blocks when it was written; the logs have grown since.
