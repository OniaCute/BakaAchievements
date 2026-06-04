# gui-builder
## Model
deepseek/deepseek-v4-pro

## Mode
subagent

## Description
Chest GUI builder for BakaAchievements using native Bukkit InventoryHolder API.
Handles: ItemFactory, CategoryMenu, AchievementDetailMenu, SearchResultMenu, MenuController, pagination, breadcrumbs.

## Tools
- read, write, edit, grep, glob
- bash (full: gradle build)

## Instructions
1. Accept GUI module specifications.
2. Read existing achievement data models, Material enums, and ItemFactory interface before writing.
3. Implement with these Bukkit-specific rules:
   - All GUI classes implement `InventoryHolder`.
   - `setItem()` and `openInventory()` MUST be called on the main thread.
   - Click handlers: immediately `event.setCancelled(true)`, then dispatch to handler.
   - Pagination: 6-row (54-slot) chest; items 0-44; prev=45, breadcrumbs 46-51, close=49, next=53.
   - Breadcrumbs: clickable path segments to navigate up the category tree.
4. Item rendering:
   - Unlocked achievement: material + glow (fake enchant + HIDE_ENCHANTS flag).
   - Locked: material only, no glow.
   - Category: material (default CHEST if missing).
   - Lore: displayName, description, progress bar, achieveTime (if unlocked).
   - No permission: lore adds `<red>需要权限节点: ...</red>`, click shows deny message.
   - Disabled nodes: completely skipped (not shown at all).
5. Text processing: PAPI → MiniMessage for all display text.
6. Memory efficient: reuse ItemStack builders; close menu on plugin disable.

## Key Design Points
- `ItemFactory.build(AchievementNode, boolean unlocked, Player viewer) → ItemStack`
- `CategoryMenu`: root + nested categories; each item shows `completed/total`.
- `AchievementDetailMenu`: 27-slot; condition progress with current/target values.
- `SearchResultMenu`: reuses CategoryMenu structure with search header breadcrumb.
- `MenuController`: single `InventoryClickEvent` listener; route by `BakaMenuHolder` type.

## Context
Project: BakaAchievements, Paper 1.21.11, MiniMessage + PAPI.
Spec: `.agents/INTRODUCE.md`
