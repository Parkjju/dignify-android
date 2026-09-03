# Android TODO

작성 2026-09-02. iOS 쪽은 `../dignify-iOS/TODO.md`, 백엔드는 `../dignify-backend/TODO.md`.

**현재 상태 — `versionName 1.1.2` / `versionCode 11`, 2026-09-02 Play 출시 완료.**
iOS 1.1.1 포트가 전부 나갔다(`docs/1.1.1-port-checklist.md` §2 전 항목).

**버전 이름이 iOS(1.1.1)와 다르다.** 같은 포트지만 안드로이드는 1.1.2로 나갔다 — 체크리스트 §4가
말한 대로 두 플랫폼의 이름은 갈려도 되고, 백엔드는 `versionCode`로만 버전을 가른다.
앱 안 노트도 1.1.2 항목이다(`Changelog.shouldShow`가 노트 없는 버전을 걸러내므로 이름과
노트가 어긋나면 새 소식 화면이 통째로 안 뜬다).

1.1.0은 코드 변경 없이 번호와 앱 안 노트만 붙인 릴리즈였고 **Play에는 안 올라갔다.**
그래서 유저 기준 직전 빌드는 `versionCode 8`(1.0.1)이다. Play 출시 노트를 쓸 때 이 기준을
쓴다(`docs/release-notes.md`).

**다음 업로드는 `versionCode 12`부터다.** 11은 썼으므로 그 업로드를 지워도 다시 못 쓴다.

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

- **다음 버전(1.1.3 또는 1.2.0) 노트를 미리 적지 말 것.** 안 만든 걸 새 소식이라 말하면 그대로
  거짓말이 된다(`Changelog.kt` 주석과 같은 규칙).
- 앱 안 노트와 Play 출시 노트는 **다른 글이다.** 기준도 다르다 — 앱 안은 직전 버전, Play는
  직전에 올라간 빌드. 문안은 `docs/release-notes.md`.
- 시드 풀은 정적이고 손으로 고른 목록이다. 바꾸는 건 배포가 아니라 운영 작업
  (`ops/onboarding-seed-pool.sql`).
