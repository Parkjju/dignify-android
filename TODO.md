# Android TODO

작성 2026-09-02. iOS 쪽은 `../dignify-iOS/TODO.md`, 백엔드는 `../dignify-backend/TODO.md`.

> 🎨 **디자인 반영할 것은 `../dignify-design/app-changes.mdx`가 정본이다**(배포본: 디자인 Storybook 「가이드 › 앱 반영 목록」).
> 시안 버전별로 화면 id · AS-IS · TO-BE · 앱 파일 위치(iOS 기준, Android 파일은 같은 이름의 Screen)가 적혀 있다. 아래 버전부터 차례로 반영하고, 끝나면 그 버전 상태에 Android 버전을 적는다.

## 재방문 푸시 2종 — 신곡 알림 · 주간 추천 (2026-10-29 등록)

정본은 Confluence 03. Dev 「재방문 푸시 기능 2종: 기능 명세」(13074433)와 하위 「개발 참고: 신곡 알림」(13008898)·「개발 참고: 주간 추천 푸시」(12681240).
문서의 코드 위치는 iOS 기준이다 — 같은 역할의 Android 파일: 푸시 `feature/push/`(`DignifyMessagingService`·`Push.kt`·`PushOptInPopup.kt`), 탭 분기 `Session.onPushOpened`, 피드 `FeedScreen.kt`.
**iOS `TODO.md` 같은 이름 절과 항목이 1:1이다. 동작이 갈리지 않게 같이 갈 것.** 서버 몫은 `dignify-backend/TODO.md` P1-b.
세 푸시(큐레이션·신곡·주간) 모두 **광고성** — 서버 동의 필터는 양 앱 동의 팝업이 나간 뒤에 켠다.

**코드·실기기 끝 (2026-10-30, moto g Android 16에서 1~7 통과).** 남은 건 출시와 서버 플래그(아래 5-5 마지막 ⬜).

✅ **서버 변경 3개 배포됨**(2026-10-30 `2866463`, `/feed/tracks` 운영 200 확인):
1. **광고성 타입은 FCM data 메시지로**(`PushService.fcmMessage`) — `android.notification`이 있으면 백그라운드에선 시스템이 알림을 그리고 앱 코드가 안 돌아서 「수신거부」 액션을 달 수 없다. 앱이 `title`·`body`로 직접 만든다. 우선순위 HIGH
2. `GET /feed/tracks?ids=` — 알림의 `trackIds`를 곡으로 바꿀 엔드포인트가 없었다(`FeedService.getTracks` 재사용, 내려간 곡은 빠짐)
3. `/users/me`에 `adPushConsent` — 알림 설정 토글의 시작값. 없으면 앱은 이 기기에서 마지막으로 처리한 값을 쓴다

### 0. 광고성 정보 수신 동의

- [x] 첫 실행 동의 팝업: `FeedScreen` `Prompt.FirstRun` — 코치마크 끝(`seenCoach`) + 로그인 + 동의 false + `didOfferAdConsent` 없음. 기존 유저는 업데이트 뒤 첫 실행에. 업데이트 소식 시트가 떠 있으면 닫힌 뒤(`Session.whatsNewOpen`)
  - 알림 켜기는 `rememberEnableNotifications`(`feature/push/AdConsent.kt`): 켜져 있으면 그대로 / 시스템 창 / 그 밖은 앱 알림 설정. 영구 거부는 결과가 0.5초 안에 거절로 오는 걸로 가른다
- [x] 큐레이션 완주 권한 팝업 삭제(`justFinishedSet`·`push_optin_set_*` 문자열까지)
- [x] 결과 통지: `AdConsentNotice`(앱 안 창) / 알림 「수신거부」는 같은 알림을 결과 문구로 갈아 끼움. 날짜는 서버 `processedAt`을 기기 날짜로
- [x] 수신거부 액션: `PushNotifications`(앱이 광고성 알림을 직접 만듦) + `OptOutReceiver` → `POST /push/{pushId}/opt-out`(토큰 없음, `ApiClientAuthTest`로 고정). 앱 안 열림·피드 안 바뀜
- [x] 마이 › 알림 설정(`NotificationSettingsScreen`, `MyRoute.NOTIFICATIONS`): 광고성 토글 + 기기 알림 상태 / 「설정에서 켜기」
- 게스트에게는 동의 팝업을 띄우지 않는다(2026-10-29 확정)
- 계측 `ad_consent_shown/accepted/declined{source: first_run|hype|new_release|settings}` — `new_release`는 아래 3의 제안 팝업(문서에 없던 값)

### 1. 좋아요 직후 팝업 「혁오 신곡이 나오면 알려 드릴까요?」

- [x] 조건: 로그인 + `Push.permissionUndecided`(13+ · 미허용 · 한 번 거절한 적 없음 · 띄운 기록 없음) + `Session.launchCount >= 2` + `Session.promptedThisSession` 꺼짐 → 좋아요 후 다음 카드가 자리 잡은 직후. 동의가 없으면 같이 받는다
  - 13 미만은 "미결정"이 없어 이 팝업이 안 뜬다(첫 실행 팝업만)
- [x] 겹침: 코치마크(`seenCoach`) / 로그인 시트(`pendingSignIn`) / 하입 모션(다음 카드에서) / 첫 실행 팝업(세션 플래그) / 리뷰 요청(안드로이드엔 없음) / 온보딩 곡 고르기(다른 화면) / 미디어 알림 좋아요(`audio.onHype`는 계기 아님) / 업데이트 소식
- [x] 재질문: `hypeOfferArtistIds` + `hypeOfferReasked`. **아티스트를 이름으로 센다** — 피드 응답에 아티스트 id가 없다. 픽 게시 팝업도 세션 플래그를 본다
- [x] `PushOptInPopup` 재사용(`acceptLabel` 추가), `push_optin_*{source: "hype"}`

### 2. 알림 탭 → 피드 앞 구간

- [x] `Session.onPushOpened(type, trackIds)` → `PushOpen` 대기 칸 하나(나중 탭이 덮음) + `pushOpenTick`. 피드는 `FeedViewModel.segments`로 일반화(`curationCount` 대체). 포그라운드 도착은 알림만 뜨고 재구성 안 함
  - 큐레이션 탭도 이 길로 옮겼다 — 전엔 `feedReloadTick`으로 **커서를 버리고 처음부터** 받았다. 지금은 보던 자리 다음부터 평소 피드를 잇고 첫 장으로 간다(`jumpToTop`)
- [x] `new_release`: 신곡 → 마지막 신곡 카드에 닿을 때 `/feed/related`(좋아요한 신곡, 없으면 무작위 1곡) → 연관 구간에서 듣지도 좋아하지도 않고 5번 넘기면 남은 연관 곡 버림
  - **iOS와 다를 수 있는 곳:** "끝까지 듣기" 대신 청취 기록(5초)으로 카운터를 0으로 돌린다. 마지막 신곡 카드에서 누른 좋아요는 시드에 안 들어간다(지난 뒤 받으면 보던 평소 피드 카드가 바뀌어서)
- [x] `weekly_pick`: 5곡 → 평소 피드(앞 구간 곡은 뒤에서 뺀다)
- [x] 곡이 내려갔으면 서버가 빼고 준다, 다 없으면 평소 피드
- [x] 계측 `source`: `curation`·`new_release`·`new_release_related`·`weekly`(구간 종류 그대로). 배지 「좋아요한 아티스트의 신곡 i/n」·「이번 주 추천 i/n」, 연관 구간은 배지 없음

### 3. 알림 미허용 유저도 신곡 받기 (안 A)

- [x] 로그인 유저가 피드를 받을 때 `GET /feed/new-releases`를 큐레이션 앞에. 본 곡은 `seenNewReleaseIds`(기기). 큐레이션 알림 탭이면 신곡은 안 끼운다
- [x] 표시는 큐레이션 배지 자리(시안 나오면 바꿀 것)
- [x] 구간을 다 본 뒤 「다음 신곡도 알림으로 받을까요?」 — 동의·기기 알림 둘 다 켜져 있으면 안 띄움, 꺼진 쪽을 켠다

### 4. 마무리

- ✅ 서버 변경 3개(위) 배포 — `2866463`
- ✅ 문자열을 iOS 1.3.2 xcstrings 값으로 덮었다(2026-10-30). 남은 임시는 iOS에 없는 자리뿐 — `new_release_optin_*`(앱 안 신곡 뒤 제안), `hype_optin_message`(동의는 있고 권한만 미결정일 때)
- ✅ 첫 업로드 `versionCode`는 16. 운영 `PUSH_AD_MIN_BUILD_ANDROID=16`은 실기기 테스트 때 이미 넣었다(2026-10-30) — **1번 배포 전 빌드엔 광고성 알림이 시스템 알림으로 떠 수신거부가 없다**
- ✅ 실기기(2026-10-30): 아래 5-6 순서(iOS `test-push/README.md` 1~7)로 전부 통과. 7은 운영 실발송 — 주간 추천 수신·탭 → `source: weekly` 5곡 → `feed`, `app_opened_push{weekly_pick, push_id}` 확인
  - 테스트 중 고친 것: 첫 실행 팝업이 서버 동의 true면 안 뜨던 것(iOS는 안 거른다) / 프로세스가 살아 있으면 다음 실행에서 「이번 실행에 띄움」이 안 꺼져 좋아요 팝업이 막히던 것(`countLaunch`에서 끈다) / 게스트 → 로그인 직후 첫 실행 팝업이 뜨던 것(`signedInFromGuest`)
  - 알게 된 것: 광고성 발송은 **기기 시간대** 9~21시에만 나간다(`PushService.isAwakeHour`). 토큰·시간대는 **프로세스가 새로 뜰 때만** 다시 등록된다 — 시간대를 바꿨으면 강제 중지 후 실행. 주간 추천은 유저당 주 1회라 다시 받으려면 `weekly_picks` 이번 주 행을 지운다


### 5. iOS 1.3.2에서 테스트하며 바꾼 것 — 따라갈 것 (2026-10-30 등록)

iOS를 시뮬레이터로 테스트하다가 바꾼 것들이다. iOS 쪽 변경은 커밋 전이다. 테스트 시나리오는 `../dignify-iOS/test-push/README.md`가 정본이고, Android에서 다르게 해야 하는 곳은 아래 5-6에 적었다.

#### 5-1. 기존 계정은 코치마크와 시드 고르기를 건너뛴다

- ✅ `MainActivity` 업데이트 판정에서 `isReturning`이면 피드 코치마크를 본 것으로 기록한다(`CoachSeen` FEED). 기존 계정이 업데이트하거나, 새로 설치하고 로그인했을 때 해당한다
  - **이유:** 코치마크는 기기 기록만 본다. 그래서 다시 설치하고 기존 계정으로 로그인하면 코치마크가 다시 떴고, 첫 실행 동의 팝업이 그 뒤로 밀렸다. 기존 유저는 What's New를 닫으면 **동의 팝업만** 봐야 한다
  - **함정:** `FeedScreen`은 `seenCoach`를 `remember`로 한 번만 읽는다. `LaunchedEffect`보다 피드가 먼저 그려지면 옛 값이 남아서 코치마크가 뜬다. 피드가 처음 그려지기 전에 기록하거나, 피드 쪽에서 값을 다시 읽게 할 것 → 판정을 `LaunchedEffect`에서 `remember`(컴포지션 중)로 옮겨 피드보다 먼저 기록한다
- ✅ 업데이트 유저에게 한 번 띄우던 시드 고르기(`updateSeedPool`, `KEY_DID_ROUNDS` 분기)를 지운다. 1.1.0 업데이트 안내용이었다. 신규 가입 온보딩의 시드 고르기는 그대로 둔다
  - iOS는 `MainTabView`에서 같은 분기를 지웠다. `pendingWhatsNew`를 거쳐 잇던 것도 없어지고, What's New만 바로 띄운다
  - 키 `didSoundRounds`는 온보딩이 계속 쓰니 그대로 둔다

#### 5-2. 마이 › 알림 — 기기 알림 행을 누를 수 있게

- ✅ 「기기 알림 권한」 **행 전체**를 버튼으로 만든다. 오른쪽에 상태 문구와 `›`를 둔다. 따로 있던 「설정에서 켜기」 버튼(`device_notifications_turn_on`)은 지운다
  - 미결정(`Push.permissionUndecided`): 시스템 권한 창(`enable("settings")`)
  - 그 밖(켜짐·꺼짐): 이 앱의 알림 설정 화면(`Settings.ACTION_APP_NOTIFICATION_SETTINGS` + `EXTRA_APP_PACKAGE`)
- ✅ 상태 문구는 세 가지다. iOS 키 `On`/`Off`/`Not set` → ko 「켜짐」/「꺼짐」/**「설정 전」**. 「아직 안 정함」은 어색해서 바꿨다
- ✅ 저장 실패 오판은 해당 없다. iOS는 저장한 직후의 토글 값으로 성공 여부를 판정해서, 성공해도 「저장에 실패했어요」가 떴다. Android는 `Session.setAdPushConsent`의 반환값(null이면 실패)으로 보니 이 버그가 없다

#### 5-3. 디버그 「업데이트 유저 / 신규 유저 상태로 초기화」 (`BuildConfig.DEBUG`에서만)

- ✅ 마이 맨 아래에 두 행을 추가한다(`debugReset`). 「직전 버전」은 `Changelog`에서 현재 바로 앞 항목 iOS `MyPageView.debugResetRow(newUser:)`와 같다. 로그인은 그대로 두고 기기 기록만 되돌린다 — **계정 삭제 없이** 신규·업데이트 흐름을 다 본다
  - 신규 유저: `didJustOnboard` = true, `lastSeenVersion` = 현재 버전, 피드 코치마크 = 안 봄 → 재실행하면 코치마크 → 동의 팝업
  - 업데이트 유저: `didJustOnboard` 삭제, `lastSeenVersion` = 직전 버전, 피드 코치마크 = 봄 → 재실행하면 What's New → 동의 팝업
  - 두 행 공통: `didOfferAdConsent`, `launchCount`, `hypeOfferArtistIds`, `hypeOfferReasked`, `seenNewReleaseIds`, `adPushConsent`를 지운다. 서버 동의는 `PUT /users/me/push-consent {adPush:false}`로 거부로 돌리고, 결과 통지는 띄우지 않는다
  - 다 되면 「앱을 완전히 껐다 켜면 … 순서로 뜹니다」 안내를 띄운다
- Android는 Play 빌드(서명이 다르다) 위에 디버그 빌드를 덮어 깔 수 없다. 그래서 실제 업데이트를 재현할 수 없고, 이 행이 업데이트 테스트의 유일한 방법이다

#### 5-4. 디버그 테스트 푸시 (iOS `test-push/*.apns` 대신)

- ✅ 광고성 알림은 FCM data 메시지다. 마이 디버그 묶음의 「테스트 푸시」 5행(`PushNotifications.debugPushes`). 그래서 `PushNotifications.show(context, data)`를 서버와 같은 data 맵으로 부르면 실제로 받은 것과 같은 경로를 탄다. 디버그 메뉴에 5개를 둔다
  - data 키: `type`, `title`, `body`, `push_id`, `trackIds`. `trackIds`는 **콤마로 이은 문자열**이다(`PushService.fcmMessage`)
  - 곡 id는 운영에 있는 것으로 확인했다(2026-10-30)

| 이름 | type | trackIds |
|---|---|---|
| 신곡 3곡 | `new_release` | `1,2,3` (Leo Kottke) |
| 신곡 1곡 | `new_release` | `11` |
| 없는 곡 | `new_release` | `999999999` |
| 주간 추천 | `weekly_pick` | `6,7,8,9,10` |
| 큐레이션 | `curation` | (없음) |

- 앱이 꺼진 상태의 탭도 디버그 메뉴로 본다 — 행을 눌러 알림을 띄운 뒤 최근 앱에서 지우고 알림을 탭한다

#### 5-5. 버전 · What's New

- ✅ 다음 업로드는 `versionCode 16`. `versionName`은 iOS와 맞춰 `1.3.2`
- ✅ Changelog 1.3.2 항목. iOS 1.3.1 노트 3줄도 합쳤다 — 그 변경은 vc15가 노트 없이 이미 실어 냈다. iOS 문구를 그대로 쓴다(`WhatsNewView.swift`, xcstrings ko)

| en | ko |
|---|---|
| New tracks from artists you like now show up first in your feed. | 좋아요한 아티스트의 신곡이 나오면 피드 맨 앞에 먼저 보여 드려요. |
| Turn on notifications and we'll let you know when they drop new tracks. | 알림을 켜 두면 좋아요한 아티스트의 신곡 소식을 바로 알려 드려요. |
| Every Monday, get 5 picks based on the tracks you like. | 매주 월요일, 좋아요한 곡을 바탕으로 고른 5곡을 보내 드려요. |
| Turn notifications on or off anytime in My Page › Notifications. | 알림은 마이 › 알림에서 언제든 켜고 끌 수 있어요. |

  - **주의:** 새 소식 화면은 현재 버전 항목만 보여 준다. 위 「다음 버전 — seed_picker_opened 1.0」(iOS 1.3.1)이 같은 업로드에 실리면, iOS 1.3.1 노트 3줄도 이 1.3.2 항목에 합친다. 안 그러면 그 변경은 안내 없이 나간다
- ⬜ 출시 후 운영 `PUSH_AD_ENABLED=true`(`PUSH_AD_MIN_BUILD_ANDROID=16`·`_IOS=29`는 들어가 있다). iOS·Android 둘 다 출시된 뒤에 켠다. 테스트 뒤 `false`로 되돌려 뒀다(2026-10-30)

#### 5-5b. iOS 테스트 정본과 맞춘 동작 (2026-10-30)

- 첫 실행 동의 팝업 「나중에」도 서버에 거부를 남기고 결과 통지를 띄운다(iOS 1-3). 좋아요·신곡 팝업의 「나중에」는 팝업만 닫는다
- 결과 통지 창 제목 「알림 설정을 저장했어요」. 실패하면 통지 없음 — 피드 팝업은 조용히, 알림 설정은 토글 그대로 + 「저장에 실패했어요」(iOS 2-12)
- 알림 설정 토글을 켤 때 기기 알림도 같이 켠다(iOS `acceptAdPush`): 미결정이면 시스템 창, 꺼져 있으면 앱 알림 설정
- 수락 버튼은 늘 「알림 받기」(iOS `PushOptInPopup`) — 따로 두던 「광고성 정보 수신동의」 버튼 문구를 지웠다
- 수신거부 실패 알림 문구는 iOS 값(「…마이 › 알림에서 광고성 알림을 꺼 주세요」). 다시 누를 버튼은 남겨 둔다

#### 5-6. 테스트 — iOS 시나리오와 다른 곳

순서와 기대 결과는 iOS `test-push/README.md`의 1~8을 그대로 따른다. 계정 삭제는 하지 않는다. 다른 곳만 적는다.

- **상태 만들기**
  - 새로 설치한 상태: `adb shell pm clear com.rta.dignify`. 데이터와 런타임 권한이 같이 초기화된다
  - 신규·업데이트 유저: 5-3 디버그 행
  - 권한만 미결정으로: `adb shell pm revoke com.rta.dignify android.permission.POST_NOTIFICATIONS` 다음 `adb shell pm clear-permission-flags com.rta.dignify android.permission.POST_NOTIFICATIONS user-set user-fixed`
- **좋아요 팝업(2-4~2-9)은 Android 13 이상에서만** 뜬다. 12 이하는 권한에 "미결정" 상태가 없다. 13 이상 에뮬레이터로 본다
- **3-9:** 연관 구간 카운터는 "28초 이상 듣기"가 아니라 청취 기록(5초)으로 0이 된다(2절에 적은 차이)
- **3-16 수신거부:** 에뮬레이터도 FCM 토큰이 있어서 서버까지 간다. 테스트 푸시의 `push_id`는 가짜라 성공·실패 중 어느 안내가 뜨는지는 서버 응답을 따른다. 결과 알림이 뜨고 앱이 안 열리는지만 본다
- **영구 거부(4-3):** 「알림 받기」를 누르면 앱 알림 설정이 열려야 한다(`rememberEnableNotifications`의 0.5초 판정)
- **7 실제 발송:** 로컬 서버를 `PUSH_AD_MIN_BUILD_ANDROID=16`으로 띄운다. 알림이 시스템 기본 모양이 아니라 앱이 만든 알림(「수신거부」 버튼 있음)인지 본다

---

## 1.3.0 / `versionCode 15` — Play 심사 제출(2026-10-20), 실기기 확인 남음

**`versionName`은 1.3.0 그대로 두고 `versionCode`만 15로 올려 냈다**(14는 2026-10-12 제출본 `release/1.3.0` `e76aec2`가 썼다).
아래 feed_coachmark 1.3 + 그 밑 seed_picker_opened 1.0(1·2차)이 이 빌드에 들어갔다. 이름이 같아 앱 안 새 소식(`Changelog` 1.3.0)도 그대로라
**이 빌드의 새 기능을 알리는 노트는 없다.** 다음 업로드는 `versionCode 16`부터.
대형 화면 대응(`large-screen` 브랜치)은 **안 들어갔다** — 여전히 다음 버전 몫.

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

## 🎨 다음 버전 — seed_picker_opened 1.0 (마이 「추천 기준 곡」 개편) · 1·2차 코드 끝(2026-10-20), 실기기 남음

정본은 `../dignify-design/app-changes.mdx` 「seed_picker_opened 1.0」 1~8번. 1.3의 마이 쪽(가로 CrateCell·비활성·커버 탭 스크롤)을 이 버전이 덮는다 — 위 1.3.1 실기기 항목 중 「마이에서 뒤 페이지 곡으로 스크롤」은 이 버전이 나가면 없어진다.
**장르(3번, 1번의 「· 장르」)는 서버 `dignify-backend/TODO.md` P2-c가 먼저다.** 그 전엔 장르 칩·장르 표시만 숨기고 나머지를 먼저 낸다. iOS와 같은 순서로 간다.

1차 — 서버 없이 (코드 끝, `./gradlew build` 통과):
- ✅ 1~2·4~7: `feature/mypage/SeedPickerScreen.kt` 새로 씀 — `LazyColumn` 체크형 행, 제목 + ⓘ `Popup` 말풍선, 꽉 차면 확인 팝업, 트레이 `background` + 남은 칸 안내 + 커버 탭 = 빼기, 빈 화면 → 피드 탭(`MyTab.onGoFeed`, 마이는 루트로 되돌림)
  - `HypeCollection`의 선택 모드(`selection`·`scrollTo`·비활성 셀·선택 배지)는 쓰는 곳이 없어져 지웠다
  - `SeedTray`: `onOpen = null`이면 커버 탭도 빼기, `hint`로 안내 문구 교체. 온보딩 호출은 그대로
- ✅ 8: `DSPopup`(`core/designsystem/DesignSystem.kt`) — 딤·카드 `AnimatedVisibility` 두 겹. `PushOptInPopup(visible = …)`과 새 확인 팝업이 같이 쓴다. `rememberReduceMotion`도 여기로 옮겼다
- 문자열은 iOS xcstrings(`60fb9bb` 이후 작업 트리) 값 그대로 — en·ko 둘 다
- ⬜ 실기기: 3곡 꽉 참 → 회색 행 탭 팝업(뜨고 닫힐 때 모션) / 0곡 저장 / 좋아요 0 계정 빈 화면 → 「곡 찾으러 가기」 / ⓘ 말풍선 2초·바깥 탭·다시 누르면 다시 세기 / 알림 권유 팝업 모션(피드 특집 끝·픽 올린 뒤) / 온보딩 트레이가 그대로인지

2차 — 장르 필터 (서버 P2-c `8423757` 배포됨, 코드 끝):
- ✅ 3. `GET /users/me/hypes/genres`로 칩(「전체」 + 좋아요한 장르, 많은 순)과 「좋아요한 곡 N」(고른 장르면 그 장르 수), `myHypes(cursor, genreId)`로 필터. 행 둘째 줄 「아티스트 · 장르」
  - 장르를 바꿔도 고른 곡은 그대로 — 받은 곡을 `seen`에 쌓아 필터 밖 고른 곡도 트레이에 남는다. 칩을 빨리 바꾸면 이전 요청은 취소, 받는 사이 칩이 바뀐 다음 페이지는 버린다
  - 장르 목록을 못 받으면 칩·「좋아요한 곡 N」만 숨긴다
- ⬜ 실기기: 칩 전환(고른 곡·트레이 유지, 뒤 칩 탭 시 그 칩이 보이게 밀림) / 장르 안에서 뒤 페이지 / 영어 로케일에서 장르 이름이 영어로 오는지
- ⬜ 출시하면 `../dignify-design/app-changes.mdx` 상태에 Android 버전 적기

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
