# Android TODO

작성 2026-09-02. iOS 쪽은 `../dignify-iOS/TODO.md`, 백엔드는 `../dignify-backend/TODO.md`.

**현재 상태 — `versionName 1.1.2` / `versionCode 11`.** iOS 1.1.1 포트가 코드에 다 들어갔다
(`docs/1.1.1-port-checklist.md` §2 전 항목). **남은 건 올리는 것뿐이다**(`docs/release-runbook.md`).

**버전 이름이 iOS(1.1.1)와 다르다.** 같은 포트지만 안드로이드는 1.1.2로 나간다 — 체크리스트 §4가
말한 대로 두 플랫폼의 이름은 갈려도 되고, 백엔드는 `versionCode`로만 버전을 가른다.
포트 노트도 1.1.2 항목으로 옮겨 뒀다(`Changelog.shouldShow`가 노트 없는 버전을 걸러내므로
이름과 노트가 어긋나면 새 소식 화면이 통째로 안 뜬다).

1.1.0은 코드 변경 없이 번호와 노트만 붙인 릴리즈였다. 그걸 아직 Play에 안 올렸어도 문제없다 —
What's New 시트가 로그 전체를 그리므로 업데이트 유저는 1.1.2와 1.1.0 노트를 함께 본다.

---

## P0. 1.1.2 올리기

`./gradlew testReleaseUnitTest bundleRelease` → 서명 확인 → Play Console.
빌드·서명·검증 절차는 전부 `docs/release-runbook.md`에 있다.

⚠️ **`versionCode 11`은 이미 커밋돼 있다.** 업로드 전에 또 올리지 말 것 — 한 번 쓴 번호는
그 업로드를 지워도 다시 못 쓴다.

---

## P1~P5. iOS 1.1.1 포트 — 끝났다

| | 무엇 | 어디 |
|---|---|---|
| P1 | 온보딩을 시드 풀에서 직접 고르는 한 화면으로 교체(라운드 삭제) | `feature/onboarding/SeedPoolPickerScreen.kt` |
| P2 | 픽 작성화면: 3열 그리드 → 날짜별 리스트 + 행 탭 재생 | `feature/picks/PickComposeSheet.kt` |
| P3 | 픽 카드 재생 수(5 미만 숨김) | `feature/picks/PickListScreen.kt` |
| P4 | `Your crate` → `Your hypes` / `담은 곡` → `하입한 곡` | `values*/strings.xml` |
| P5 | 신규 계측 9종(이름·키 iOS와 동일), `onboarding_sound_*` 은퇴 | 위 두 화면 |

포트하면서 같이 고친 것 하나: **픽 게시가 4xx를 성공으로 알리고 있었다.** `createPick`이
`HttpResponse`를 돌려주는데 `expectSuccess`가 없어서 400(금칙어·개수 위반)도 예외를 안 던졌다.
이제 상태 코드를 직접 보고 `pick_submit_failed{reason=server}`를 남긴다.

배포 후 볼 것은 체크리스트 §5에 있다. 요약하면 `onboarding_seed_selected`의 `from` 분포
(`search`가 높으면 고정 풀이 취향을 못 덮는다는 뜻이라 풀을 다시 골라야 한다)와,
`pick_track_selected` → `pick_title_step` → `pick_created`로 갈리는 작성화면 이탈 구간.

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

- **1.1.2 노트를 미리 적지 말 것.** 안 만든 걸 새 소식이라 말하면 그대로 거짓말이 된다
  (`Changelog.kt` 주석과 같은 규칙).
- 시드 풀은 정적이고 손으로 고른 목록이다. 바꾸는 건 배포가 아니라 운영 작업
  (`ops/onboarding-seed-pool.sql`).
