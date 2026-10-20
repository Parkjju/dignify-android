# Android TODO

작성 2026-09-02. iOS 쪽은 `../dignify-iOS/TODO.md`, 백엔드는 `../dignify-backend/TODO.md`.

> 🎨 **디자인 반영할 것은 `../dignify-design/app-changes.mdx`가 정본이다**(배포본: 디자인 Storybook 「가이드 › 앱 반영 목록」).
> 시안 버전별로 화면 id · AS-IS · TO-BE · 앱 파일 위치(iOS 기준, Android 파일은 같은 이름의 Screen)가 적혀 있다. 아래 버전부터 차례로 반영하고, 끝나면 그 버전 상태에 Android 버전을 적는다.

## 1.3.1 (`versionCode 15`) — 코드 끝, 실기기 확인 남음

**1.3.0(`versionCode 14`)은 Play 제출(2026-10-12)**, `release/1.3.0`(`e76aec2`)에서 빌드해 main에 머지됐다.
출시할 때 **1.3.1 / `versionCode 15`** 로 올리고 노트를 붙인다(14는 재사용 불가).

들어간 것:
- 🎨 feed_coachmark 1.3 — 3곡 다 고르면 안 고른 타일 비활성 + 시드 트레이(`core/designsystem/SeedTray.kt`, 온보딩·마이 「추천 기준 곡」 공용)
- 📊 픽 안 청취 `source=pick` 분리, `push_opened` → `app_opened_push{type,push_id}`·`app_opened_now_playing`·`app_opened_link`·`app_opened_direct`(`core/analytics/AppOpen.kt`, 판정 입력은 `MainActivity.captureOpenSource`). 판독 규칙은 `../09_analytics_queries.md` 규칙 18·B-2, 발송 건별 열람률은 A-14
  - `push_id`는 2026-10-19 서버부터 FCM data에 실린다. 그 전 알림은 키가 없다
  - `now_playing`은 `FeedAudioController.launchIntent()`의 표식 extra + `FLAG_ACTIVITY_SINGLE_TOP`에 기댄다(없으면 살아 있는 태스크에 인텐트가 안 넘어온다)
  - `app_opened_link`는 매니페스트에 링크 intent-filter가 없어 지금은 0

⚠️ **트레이 영문 문구(`seed_tray_*`)는 내가 썼다.** iOS가 1.3을 반영하면 xcstrings 값으로 덮어쓸 것.

실기기:
- ⬜ 트레이 커버 탭 스크롤(특히 검색 중 → 풀 복귀), 마이에서 뒤 페이지 곡으로 스크롤, 꽉 찬 상태에서 회색 타일 미리듣기
- ⬜ 꺼진 상태·백그라운드 각각 푸시 탭 → `app_opened_push` / 재생 중 미디어 알림 탭 → `app_opened_now_playing` + **피드가 다시 그려지지 않는지** / 아이콘·최근 앱 → `app_opened_direct`
- ✅ 어드민에서 본인 uid로 지정 발송 → 탭 → PostHog 이벤트의 `push_id`가 발송 이력 값과 같은지 (2026-10-19)
- 알림 탭을 서버에도 직접 알린다: `POST /push/{pushId}/opened`(인증 헤더 없이, `ApiClient.reportPushOpened`). 어드민 발송 이력의 **열람·열람률**이 된다(서버 `fbd95ff`)
- ✅ 실기기: 지정 발송 → 탭 → 어드민 발송 이력 그 행의 열람이 1 오르는지 (2026-10-19)

---

## 1.2.0 백그라운드 재생 — 실기기 남은 것

정본 `docs/1.2.0-port-checklist.md` §4. 1·2·5·6은 확인했다.

- ⬜ 3: 마이페이지 미리듣기 → 홈 누르면 **멈춘다**(`isFeedSession` 분기)
- ⬜ 4: 2분 잠가두면 곡이 알아서 넘어간다
- ⬜ 7: 알림을 밀어 없애면 재생이 깔끔히 멈춘다
- ⬜ 블루투스 헤드셋으로 다음 곡. **Play 1.2.0 문안이 「이어폰·차」를 약속했다** — 안 되면 그 줄을 지운다

기억할 것:
- **`track_playback_started`는 안드로이드에 없다**(재생 시작 = `track_viewed`). 두 플랫폼을 겹쳐 볼 때 하나 어긋난다
- 미디어 알림 액션 아이콘 색은 시스템이 틴트해서 앱이 못 정한다 — 상태는 모양으로 구분한다(`ic_hype_on.xml` 주석)
- 알림 버튼이 안 먹는 것 같으면 `dumpsys notification`의 액션 제목부터 볼 것. 상태 연동은 됐는데 아이콘이 같아 오진한 적이 있다
- `ExportedService` lint 경고는 정상(Media3 요구)

---

## 대형 화면 대응 — `large-screen` 브랜치에 있다. 다음 버전에 묶어 낸다

Play Console 경고 두 개에서 출발했다. **하나는 이미 끝나 있었고, 하나는 진짜였다.**

- **Edge-to-edge(SDK 35+)**: 할 일 없음. `enableEdgeToEdge()`가 `MainActivity`에 이미 있고,
  테마가 상태바·내비바를 투명으로 두며, 인셋을 다루는 화면 파일이 15개(=화면이 있는 파일 수)다.
  이 경고는 코드를 보고 뜨는 게 아니라 targetSdk 35+ 앱에 일괄로 붙는 안내다.
- **방향/크기 제한**: 실제로 걸린다. Android 16은 targetSdk 36 앱의 `screenOrientation` 등을
  **최소 너비 600dp 이상 화면에서만** 무시한다.

### 에뮬레이터로 실측한 것 (sw 720dp / 가로 1480dp)

깨지진 않았다. 잘리거나 겹치는 데 없고 아트워크도 높이에 맞춰 줄어든다.
대신 **흩어진다** — 제목·하입은 왼쪽 끝, 공유는 오른쪽 끝으로 1480dp를 사이에 두고 벌어진다.
폰에선 아트워크 아래 모여 있던 것들이다.

→ `DignifyApp()`에 `MAX_CONTENT_WIDTH = 600.dp`를 걸어 지면을 가운데로 묶었다.
**폰은 이 상한보다 좁아서 한 픽셀도 안 변한다**(sw 393dp에서 확인). 화면마다 걸지 않고 한 곳에
둔 이유는 로그인·온보딩·탭이 전부 그 함수를 지나가기 때문이다.

### ⚠️ `screenOrientation="portrait"`는 지우지 않았다

경고가 지우라고 하지만 **지우면 폰이 깨진다.** 지워보고 실측했다 — 폰 가로(높이 393dp)에서는
상단 오버레이와 하단 텍스트·탭바를 빼면 카드에 남는 높이가 0이라 아트워크가 통째로 사라진다.

남겨두면 원하는 동작이 그대로 나온다: 폰은 세로 고정, Android 16 대형 화면은 어차피 무시돼
회전하고 그 경우는 위의 최대 폭 제한이 받아준다. **Console 경고는 계속 뜬다** — 근거를
`AndroidManifest.xml` 액티비티 주석에 적어뒀으니 보고 또 지우지 말 것.

경고를 실제로 없애려면 폰 가로 레이아웃을 따로 짜야 한다(아트워크와 텍스트를 좌우로 나누는 식).
**targetSdk 37로 올리기 전까지는 급하지 않다** — 그때는 600dp 이상에서 회피 수단이 아예 없어진다.

---

## P0. 다음 릴리즈 전에 볼 것 — 1.1.2가 실제로 먹었는지

배포 직후 며칠은 숫자가 답을 준다. 판정 기준은 체크리스트 §5에 있고 요약은 이렇다.

- `onboarding_seed_selected`의 `from` 분포. **`search` 비중이 높으면 그게 발견이다** —
  손으로 고른 고정 풀이 실제 취향을 못 덮고 있다는 뜻이라 풀을 다시 골라야 한다
  (`ops/onboarding-seed-pool.sql`).
- `pick_track_selected` → `pick_title_step` → `pick_created`. 작성화면 이탈이 "한 곡도 못 골랐다"와
  "골라놓고 못 냈다" 중 어디였는지가 1.1.2에서 처음 갈린다.
- `gcloud logging read 'textPayload:"[feed]"'`에서 안드로이드 트래픽이 온보딩 직후에도 `cold`만
  찍히면 하입이 첫 피드 요청보다 늦게 도착한 것이다(시드 고르기의 await가 헛돈 것).
- `pick_submit_failed`. 0이 아니면 그동안 성공으로 보이던 실패가 실제로 있었다는 뜻이다.

---

## P1~P5. iOS 1.1.1 포트 — 끝

`SeedPoolPickerScreen.kt`·`PickComposeSheet.kt`·`PickListScreen.kt`. 같이 고친 것: `createPick`이 4xx를 성공으로 알리던 것(→ `pick_submit_failed{reason=server}`).

---

## P6. 푸시 딥링크 — 분기는 붙었다. `pickId`가 남았다

`Session.onPushOpened`가 서버 `type`을 받아 갈라 보낸다.

| type | 가는 곳 |
|---|---|
| `curation` | 피드 탭 + 큐레이션 세트를 다시 앞세움(완주한 세트라도) |
| `pick_reaction` | 마이페이지 → 디깅 프로필(= 내 픽 목록) |
| `notice` | 분기 없음. 갈 데를 지정하지 않은 알림이라 이게 의도한 동작이다 |

⚠️ **어느 픽인지는 아직 페이로드에 없다.** `pickId`를 서버가 실어주면 픽 상세로 바로 보낼 수 있다.
iOS `TODO.md` P6에 같은 요청이 걸려 있으니 **한쪽만 정하지 말고 같이 갈 것.**

---

## 다음 릴리즈에 쌓이는 것

- **버전 이름은 iOS를 따라갈 필요가 없다.** 1.2.0에서 우연히 다시 맞았을 뿐이고, 백엔드는
  `versionCode`로만 가른다. 다만 **고른 `versionName`과 앱 안 노트의 버전 문자열이 정확히
  같아야 한다**(`Changelog.shouldShow`가 노트 없는 버전을 거른다).
- **다음 버전 노트를 미리 적지 말 것.** 안 만든 걸 새 소식이라 말하면 그대로
  거짓말이 된다(`Changelog.kt` 주석과 같은 규칙).
- 앱 안 노트와 Play 출시 노트는 **다른 글이다.** 기준도 다르다 — 앱 안은 직전 버전, Play는
  직전에 올라간 빌드. 문안은 `docs/release-notes.md`.
- 시드 풀은 정적이고 손으로 고른 목록이다. 바꾸는 건 배포가 아니라 운영 작업
  (`ops/onboarding-seed-pool.sql`).
