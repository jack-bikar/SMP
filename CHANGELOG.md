# Changelog

Entries under dated headings are added by the crem-standards changelog hook; edit freely.

## 2026-10-02

- **Duel Kits**: Players can now fight kit duels with preset gear from `duel-kits.yml` instead of their own items. Kit selection happens before the duel starts via a menu, with random assignment for players who don't pick in time. Original gear, XP, and effects are safely stored and restored after the duel.
- **Config Write Safety**: Fixed race condition in file saves where older queued writes could land after newer ones, ensuring config data consistency across player disconnects and concurrent saves.
<!-- crem-changelog: 2987016 -->
