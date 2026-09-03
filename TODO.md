# Android TODO

작성 2026-09-02. iOS 쪽은 `../dignify-iOS/TODO.md`, 백엔드는 `../dignify-backend/TODO.md`.

**현재 상태 — `versionName 1.2.0` / `versionCode 12`, 코드 반영 완료. 아직 Play 업로드 전.**
iOS 1.2.0 포트가 전부 들어갔다(`docs/1.2.0-port-checklist.md` §2 전 항목).
**실기기(moto g 2025 / Android 16)에서 핵심 경로는 확인했고 일부가 남았다 — 체크리스트 §4.**

1.1.2까지 iOS와 갈려 있던 버전 이름을 이 릴리즈에서 **다시 맞췄다**(iOS도 1.2.0).
백엔드는 어차피 `versionCode`로만 가르므로 이름 자체는 자유지만, 같은 기능이 같은 이름으로
나가는 편이 두 리포를 오갈 때 덜 헷갈린다. 앱 안 노트도 1.2.0 항목이다
(`Changelog.shouldShow`가 노트 없는 버전을 걸러내므로 이름과 노트가 어긋나면 새 소식 화면이
통째로 안 뜬다).

1.1.0은 코드 변경 없이 번호와 앱 안 노트만 붙인 릴리즈였고 **Play에는 안 올라갔다.**
그래서 Play 출시 노트의 기준이 되는 직전 업로드는 `versionCode 11`(1.1.2)이다
(`docs/release-notes.md`). **1.2.0 Play 출시 노트는 아직 안 썼다.**

**다음 업로드는 `versionCode 13`부터다.** 12는 썼으므로 그 업로드를 지워도 다시 못 쓴다.

---

## ✅ iOS 1.2.0 포트 — 코드는 끝났다. 남은 건 실기기다

**2026-09-03 반영.** 화면을 꺼도 디깅이 이어진다. 백엔드 작업은 없었다.
정본은 `docs/1.2.0-port-checklist.md`.

| | 무엇 | 어디 |
|---|---|---|
| ① | `MediaSessionService` + 포그라운드 서비스 · 매니페스트 3종 | `feature/feed/DiggingPlaybackService.kt` |
| ② | 백그라운드 일시정지 제거. 피드만 이어지고 미리듣기는 멈춘다 | `FeedAudioController`의 `isFeedSession` + `AppForeground.kt` |
| ③ | `MediaSession` 트랜스포트 + 하입 커스텀 액션 | `FeedAudioController.syncSession/remotePlayer`, `FeedScreen`의 `onRemoteSeek`·`onHype` |
| ④ | 백그라운드에선 루프 대신 다음 곡 | `Playback.handleTrackEnd` (단위 테스트 있음) |
| ⑤ | 계측에 `background` 속성 | `track_viewed`(`FeedViewModel`), `track_dwell`(`DwellTracker.markBackground`) |

Live Activity는 포트하지 않았다(체크리스트 §1). 미디어 알림이 그 자리라 `CommandButton` 하나로 끝났다.

### 직역이 안 맞아 다르게 간 세 군데

- **`track_playback_started`는 안 만들었다.** 체크리스트 §2.5는 "기존 이벤트 3종"이라 했지만
  안드로이드엔 이 이벤트가 애초에 없다. 여기선 재생 시작과 `track_viewed`가 같은 순간이라
  `background`를 하나 더 실어봐야 같은 값이 두 번 들어온다. **iOS와 이벤트 목록이 하나 어긋난 상태다** —
  대시보드에서 두 플랫폼을 같은 이벤트로 겹쳐 볼 때 이걸 기억할 것.
- **다음 곡 유무를 스냅샷으로 안 들고 있다**(`FeedAudioController.hasNextTrack` 콜백).
  끝에 닿아 페이지네이션이 뒤를 붙여도 백그라운드에선 재구성이 없어 스냅샷이 갱신될 자리가 없고,
  그러면 마지막 곡에서 영영 되감기만 한다.
- **현재 위치를 `pagerState`가 아니라 `vm.lastPage`에서 읽는다.** 잠금화면에서 연달아 넘기면
  페이저는 프레임이 없어 아직 안 움직였고, `settledPage`로 재면 제자리를 맴돈다.

### 실기기 확인 (체크리스트 §4) — moto g 2025 / Android 16

에뮬레이터는 포그라운드 서비스·알림·블루투스가 전부 다르게 동작해서 이걸 못 잡는다.

| | 항목 | 결과 |
|---|---|---|
| 1 | 재생 → 홈 → 잠금. 소리가 이어지고 미디어 알림이 뜬다 | ✅ |
| 2 | 알림·잠금화면에서 다음 곡 | ✅ (블루투스 헤드셋은 미확인) |
| 3 | 마이페이지 미리듣기 → 홈 누르면 **멈춘다**(`isFeedSession` 분기) | ⬜ |
| 4 | 2분 잠가두면 곡이 알아서 넘어간다 | ⬜ |
| 5 | 알림에서 하입 → 앱에도 반영 | ✅ |
| 6 | **Android 14+** — `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | ✅ 테스트 기기가 Android 16이다 |
| 7 | 알림을 밀어 없애면 재생이 깔끔히 멈춘다 | ⬜ |

`ExportedService` lint 경고는 정상이다 — Media3가 시스템 미디어 컨트롤 바인딩을 위해 요구한다.

### 잠금화면 하입 버튼 — 상태를 색으로 못 알린다

**미디어 알림 액션 아이콘의 색은 앱이 못 정한다.** 시스템이 알파를 마스크 삼아 단색으로 틴트한다.
브랜드 컬러(#4B3FD8)를 지정한 빌드를 실기기에 올려 직접 확인했고, 그대로 벗겨졌다.
하필 디자인 시스템(`HypeIcon.tsx`)은 하입 상태를 **색으로만** 구분해서, 이 지면에만 다른 언어가 필요했다.

그래서 **모양**으로 간다: 안 함 = 삽(`ic_hype`), 함 = 체크(`ic_hype_on`).
삽의 변주(외곽선 / 원 노크아웃)를 먼저 시도했다가 접었다 — 같은 그림의 변주라 24dp에서
"어느 쪽이 하입된 상태인지"가 안 읽힌다. 근거는 `ic_hype_on.xml` 주석에 남겼다.

⚠️ **상태 연동 자체는 처음부터 되고 있었다.** `dumpsys notification`의 액션 제목이
`하입` ↔ `하입 취소`로 바뀌는 걸로 확인했다. 안 보였던 건 두 상태의 아이콘이 같은 그림이어서다 —
"알림 버튼이 안 먹는다"로 오진하기 쉬우니, 다음에 비슷한 걸 의심하면 `dumpsys`부터 볼 것.

### 아직 안 한 것

- **Play 출시 노트(1.2.0).** 기준은 직전 업로드인 `versionCode 11`(1.1.2). 문안은 `docs/release-notes.md`.
- 앱 안 노트는 iOS 1.2.0 문안 4줄을 en/ko 그대로 가져와 넣었다(`whatsnew_120_*`).

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

## P1~P5. iOS 1.1.1 포트 — 끝났다

| | 무엇 | 어디 |
|---|---|---|
| P1 | 온보딩을 시드 풀에서 직접 고르는 한 화면으로 교체(라운드 삭제) | `feature/onboarding/SeedPoolPickerScreen.kt` |
| P2 | 픽 작성화면: 3열 그리드 → 날짜별 리스트 + 행 탭 재생 | `feature/picks/PickComposeSheet.kt` |
| P3 | 픽 카드 재생 수(5 미만 숨김) | `feature/picks/PickListScreen.kt` |
| P4 | `Your crate` → `Your hypes` / `담은 곡` → `하입한 곡` | `values*/strings.xml` |
| P5 | 신규 계측 9종(이름·키 iOS와 동일), `onboarding_sound_*` 은퇴 | 위 두 화면 |

같이 나간 것 하나 더: 검색을 확정하거나 빈 데를 누르면 키보드가 내려간다. 시드 고르기에서
키보드가 목록 절반을 덮은 채 안 내려가는 걸 실기기에서 밟았다.

포트하면서 같이 고친 것 하나: **픽 게시가 4xx를 성공으로 알리고 있었다.** `createPick`이
`HttpResponse`를 돌려주는데 `expectSuccess`가 없어서 400(금칙어·개수 위반)도 예외를 안 던졌다.
이제 상태 코드를 직접 보고 `pick_submit_failed{reason=server}`를 남긴다.

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
