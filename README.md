# OneSignalWolf Full Alpha v0.9
Paper 1.20.1 / Java 17

Full-alpha implementation of the current OneSignalWolf design for playtesting and balancing.

## Implemented core
- No fixed wolf role. Random HO + fixed survival mission + random Mission II.
- Personal points; only the highest scorer wins.
- Color code helmets and line-of-sight/range code-name tags.
- Local chat (default 8 blocks, LOS required); meeting chat is unrestricted by the local-chat handler.
- OneSignal radio GUI + `/osw signal ...`; Signal Booster and Signaler HO add words; EMP blocks send/receive.
- Breaker blackout, Night Scope, Flashlight, EMP zones and EMP Shield detection.
- Bodies, forensic cause hint, 10-second delayed report, reporter death cancellation.
- Meeting teleport, vote GUI/command, tie=no exile, exile to spectator.
- Writable note -> sealed will on death; uncollected will expires after 180 seconds.
- Locked main inventory; consumable Backpack unlocks +3 slots, used backpacks drop on death.
- Special items: Night Scope, Backpack, Signal Booster, Emergency Battery, First Aid Kit (SELF/TARGET toggle), Body Armor, Tracker, Flashlight, Knife, Smoke, Handgun, Stun Device, EMP Shield.
- Knife backstab lethal + kill cooldown; handgun one magazine/no ammo item; stun projectile.
- Smoke hides code-name tags across the smoke volume.
- Wildlife timed events using configured spawn points; wildlife/weapon/environment death cause categories recorded for forensic HO.
- ALL SURVIVED branch; otherwise winner/cage placement and winner-only execution switch.
- `/osw stop` resets transient game state.

## Setup
`/osw setup breaker`
`/osw setup emp [radius] [seconds]`
`/osw setup wildlife <id>`
`/osw setup meeting`
`/osw setup cage <n>`
`/osw setup winner`
`/osw setup execution-switch`
`/osw setup list`
`/osw setup tp <meeting|winner|cage|wildlife> [id]`
`/osw setup remove <type> [id]`
`/osw setup remove-look <breaker|emp>`
`/osw setup test <breaker|emp|meeting|wildlife|execution> [id]`

## Admin/playtest
`/osw start`, `/osw stop`, `/osw end`, `/osw item <ID>`, `/osw meeting`, `/osw breaker`, `/osw emp`

Item IDs: `NIGHT_SCOPE BACKPACK SIGNAL_BOOSTER EMERGENCY_BATTERY FIRST_AID BODY_ARMOR TRACKER FLASHLIGHT KNIFE SMOKE HANDGUN STUN EMP_SHIELD`

## Build
GitHub Actions workflow is included at `.github/workflows/build.yml`.
The target artifact is produced by Maven with Paper API 1.20.1 and Java 17.

This is intentionally a Full Alpha: balance values are in `config.yml` and are expected to change after multiplayer tests.

## v0.11 Signal GUI
- 通信機を右クリックすると54スロットのチェスト型GUIを開く。
- 上段で送信先（ALL / 各カラー）を切り替え、英単語をクリック順に組み立てて送信する。
- 通常3語、Signal Boosterで+1語、SIGNALER HOで+1語。
- EMP圏内ではGUIは開けるが送信できず、通信不能表示になる。EMP圏内の受信者にも届かない。
- Signal GUI操作中も無敵にはならず、ダメージを受けるとGUIが閉じて未送信の入力は破棄される。
- 現在の単語数では1ページに収まるため、単語ページ送りは未使用。単語追加時にページ方式へ拡張可能。


## v0.12 Command Assist
- `/osw help` 日本語ヘルプを追加。
- `/osw` の全主要階層にTab補完を追加。
- `vote` は現在の投票候補カラーを補完。
- `bot kill/tp` は存在するBot IDを補完。
- `item` は全特殊アイテムIDを補完。
- `setup tp/remove` は登録済みcage/wildlife IDを補完。
- `setup test wildlife` は登録済み野生生物地点IDを補完。


## v0.13 GM Setup
- `/osw gm` / `/osw gm open`: ゲーム開始前のGM設定GUI
- プレイヤーヘッドから対象を選び、HO・使命II・カラーを個別指定
- 左クリックで候補送り、右クリックでランダムへ戻す
- 使命I「ゲーム終了まで生存」は全員固定
- 未指定項目だけゲーム開始時にランダム抽選
- GUIから直接ゲーム開始可能
- `/osw gm reset`: 全指定をランダムへ戻す
- ゲーム開始後は変更不可


## v0.15 recent changes
- Game duration: 15 minutes (900s), paused through meeting selection/meeting/restart grace.
- Corpse base Y offset: -1.25.
- Discovered corpses are removed after the meeting ends.
- Meeting choice phase and meeting refusers are position-locked until meeting end.
- Closing the vote GUI before voting posts a clickable chat link to reopen it.
- Knife backstab uses direct lethal handling and bypasses Body Armor; successful backstab shows an action bar and starts kill cooldown.
- Last Survivor: blackout + illager laugh-like vanilla sound + winner title, then return to lobby/world spawn.
- All Survived: helicopter-like rotor sound approximation + challenge toast fanfare, return to lobby/world spawn, highest-scoring human player(s) receive a Crown until next game starts.

## v0.15.1 Crown/BossBar update
- Last Survivor human winner receives a red Crown in the lobby until the next game starts.
- BossBar title is now `終了まで MM:SS`; during meetings it also shows `会議中・停止`.

## v0.16 Equipment / Darkness pass
- Knife durability: 10 units; normal hit -1, backstab -2.
- Neck Warmer passive: blocks one backstab with sound/particles, then is consumed.
- Stun Device: two projectile shots; remaining uses shown by durability bar.
- Handgun: magazine remaining shown by durability bar.
- Flashlight, Night Vision Scope, EMP Shield now use battery power and stop working at 0% without breaking.
- Emergency Battery recharges electronic equipment to full.
- `/osw setup charger` registers a charging block; using it recharges electronic equipment.
- Night Vision Scope no longer functions as a vanilla zoom item.
- Darkness equipment activates by local light level as well as breaker blackout.
- Smoke Grenade density increased and continues to block custom nameplates through smoke.
- Sidebar HUD work added for job, mission status, known remaining participants, merit points, and meeting state.

## v0.17 HUD
- Night Vision Scope: dark area + HOTBAR + battery > 0 -> Night Vision + U+E001 custom-font green HUD.
- Flashlight: dark area + HAND + battery > 0 -> vision assist + U+E002 vignette HUD.
- Night Vision Scope takes priority over Flashlight.
- HUD is removed when activation conditions fail or battery reaches 0.
- Designed for OneSignalWolf-ResourcePack-v2-HUD.zip.


## v0.17.1 verification pass
- Verified U+E001/U+E002 HUD glyph dispatch exists in plugin source.
- Verified `onesignalwolf:overlay` bitmap font and both HUD textures exist in Resource Pack v2.
- Verified HUD activation is tied to darkness, equipment presence, and remaining battery.
- Verified Night Vision Scope has priority over Flashlight.
- Note: HUD uses the Minecraft Title rendering channel in this build; ending titles run after gameplay stops, so the repeating gameplay HUD no longer updates then.

## v0.17.3 World-Light vision
- Removed full-screen custom-font overlays to prevent Scoreboard/BossBar/Hotbar overlap.
- Night Vision Scope uses vanilla NIGHT_VISION while active.
- Flashlight creates temporary invisible LIGHT blocks in a narrow ~10 block beam following the player's view.
- Flashlight LIGHT blocks are removed/repositioned as the player looks around, deactivates the item, leaves darkness, or the plugin stops.
- Battery drain remains tied to active use.
- Multiple flashlight users share temporary light positions safely.


## v0.17.4 Dark Recognition
- Night Vision Scope removed.
- In darkness (light <= configured threshold, Darkness, or breaker blackout), players beyond 4 blocks are hidden per viewer.
- An active powered Flashlight reveals players in a forward cone up to 12 blocks.
- Walls and Smoke Grenade block recognition.
- Visibility is asymmetric per viewer and restored during meetings/reset/disable.

## v0.17.5 Map Tools / Indoor / Blizzard
- `/osw tool light`: right/left click blocks to toggle blackout-light registration.
- `/osw tool lightblock`: gives a sea-lantern setup block; placing it auto-registers the placed block as a blackout light, breaking it unregisters it.
- `/osw setup light-scan <radius>`: scans common light-source blocks around the GM and bulk-registers them.
- `/osw tool indoor`: left click = point A, right click = point B.
- `/osw indoor add <name>`, `remove <name>`, `list`, `show`, `info`.
- Indoor zones are protected from Blizzard visibility restrictions. Powered indoor zones recover temperature.
- During blackout, indoor heating has a 30s residual-heat grace period then cools progressively over 90s (configurable).
- Automatic Blizzard: outdoor recognition 6 blocks; flashlight cone 10 blocks.
- Blizzard corpse discovery/report range: 3 blocks; flashlight 6 blocks. Smoke/walls still block discovery.
- Temperature system: Blizzard accelerates cooling; at low temperature vanilla freeze overlay builds and environmental damage begins.
- Neck Warmer reduces temperature loss by 40% while still retaining its one-use Backstab protection.
- Admin test: `/osw blizzard on|off`.
