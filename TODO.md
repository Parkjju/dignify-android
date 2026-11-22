# Android TODO

작성 2026-09-02. iOS 쪽은 `../dignify-iOS/TODO.md`, 백엔드는 `../dignify-backend/TODO.md`.

> 🎨 **디자인 반영할 것은 `../dignify-design/app-changes.mdx`가 정본이다**(배포본: 디자인 Storybook 「가이드 › 앱 반영 목록」).
> 시안 버전별로 화면 id · AS-IS · TO-BE · 앱 파일 위치(iOS 기준, Android 파일은 같은 이름의 Screen)가 적혀 있다. 아래 버전부터 차례로 반영하고, 끝나면 그 버전 상태에 Android 버전을 적는다.

## 🎨 푸시알림 기획 1.0 — 광고성 동의 UI 교체 (2026-11-17 등록)

정본은 `../dignify-design/app-changes.mdx` 「푸시알림 기획 1.0」 — 맨 위 **「광고성 알림 노출 규칙 — 정본」**(언제·어디서·어떤 세션에) + 항목 1~10(AS-IS·TO-BE·수치). 시안은 디자인 Storybook **Screens › 6. 광고성 알림**(동의 흐름 전부를 한 프로토타입으로). Figma 화면 플로우는 설계서와 부딪혀 참고만 한다.
**iOS `TODO.md` 같은 이름 절과 1:1이다.** 아래 「재방문 푸시 2종」의 동의 팝업·결과 통지(`AdConsentNotice`)·알림 설정 항목은 이 절이 대체한다. 서버 몫은 `dignify-backend/TODO.md` P1-c.

**코드 끝 (2026-11-17, 컴파일·유닛 테스트·lint 통과). 남은 것: 실기기 확인 → 버전 올려 업로드 → app-changes 상태에 Android 버전.**

**노출 규칙 (정본 그대로)**
- [x] A 첫 실행(`FeedScreen` `Prompt.FirstRun`): 서버 `adPushConsentAt` 미응답 + `myHypes` 첫 곡(가장 최근 좋아요)이 있어야 + 7일(`adConsentOfferedAt`). 「나중에」 = 닫기만
- [x] B 좋아요 시점: `ConsentSheet`(`PushOptInPopup.kt`). 재질문은 「일주일 동안 안 보기」 7일(`hypeOfferSnoozedAt`) — `hypeOfferArtistIds`·`hypeOfferReasked`는 버렸다. 트랙 상세가 떠 있으면 안 띄움(`canPrompt`)
- [x] C 야간: `FeedViewModel.justFinishedCuration`(특집 마지막 곡을 넘긴 순간) → `Prompt.Night`. `PUT /users/me/push-consent/night`(`Session.setNightPushConsent`)
- [x] 세션: A·B·C 실행당 하나(`Session.promptedThisSession`). A·B가 뜬 실행엔 C가 막힌다
- [x] 버튼으로만 닫힘: `DSPopup(onDismiss = null)`(딤 탭·뒤로 가기 무반응), 시트도 같음. `DSPopup`에 뒤로 가기 = 닫기를 붙였다(다른 팝업 공통)
- [x] 「알림 받기」: 동의 저장과 시스템 창을 같이 돌리고 둘 다 끝나면 토스트(`acceptAdConsent`). 거부 상태면 아무것도 안 연다(`rememberRequestNotificationPermission`, 옛 `rememberEnableNotifications` 대체)
- [x] 정본에 없는 안드로이드 전용 「다음 신곡도 알림으로 받을까요?」(`Prompt.NewRelease`, `new_release_optin_*`)는 지웠다(동의를 묻는 곳은 A·B·C + 설정뿐)

**화면 (app-changes 항목 번호)**
- [x] 1 딤 35%(`DSColor.dim`, `DSPopup` 공통)
- [x] 2 피드 배경 검정 45%(`DSColor.scrim`), 채도 조정 삭제. blur는 업스케일 원본을 32 → 24px로(minSdk 26이라 `Modifier.blur` 못 씀)
- [x] 3 상단 줄 좌우 20 · 아트워크 `maxWidth - 40.dp`(로딩 스켈레톤도) · 「좋아요 따라가는 중」
- [x] 4 `ConsentPopup` · 5 `ConsentSheet`(미리보기 그림 Figma 실측, 아이콘 = 좋아요한 곡 자켓) · 7 야간 팝업(`ConsentPopup` 재사용)
- [x] 6 `DSToast`/`DSToastHost`(탭바 위 8, 3초) → `AdConsentNotice` 삭제. 배경 블러 20은 뺐다(Compose에 뒤 흐림이 없다). 알림 「수신거부」 문구는 그대로
- [x] 8 배지 safe+90 (m 최대 10은 서버) · 9 마이: 제목 위 20, 닉네임 위아래 20, 카드 바로 아래 구분선(위아래 12), 행 50, 토글 설명 · 10 알림 설정: 야간 토글, 토스트(결과·실패·「먼저 동의」), 기기 권한 「설정 전」이면 행 무반응
- [x] `strings.xml` ko(시안)/en(뜻 맞춤 임시) — iOS가 반영되면 xcstrings 값으로 덮을 것
- [x] **테스트 빌드 초기화 행 (iOS 2026-11-18)**: 디버그 빌드는 항상, 릴리즈는 마이 버전 글자 **5번 탭**(`showsTestTools`). 안드로이드엔 `.sandbox` 같은 구분이 없어(Play 내부 테스트 = 프로덕션 AAB) 스토어 빌드에도 탭은 있다 — 열어도 자기 기기 기록·자기 계정 거부만 바뀐다. 더 막아야 하면 서버 테스터 플래그를 `/users/me`에
  - `debugForgetAnswers()`(기기만 미응답) 삭제. 초기화는 「기기 기록 지우기 + 서버 거부」, 동의 팝업 A를 다시 보려면 어드민 「광고성 동의 초기화」(`POST /internal/admin/push/users/{userId}/reset-ad-consent`) — 안내 문구도 iOS와 같다
  - 테스트 푸시 행은 디버그 빌드만(iOS 시드 고르기 행처럼)
  - 테스트 순서: 컨플루언스 「광고성 알림 동의 테스트 시나리오 (iOS 1.3.2 TestFlight)」(19595265) — 안드로이드도 같은 순서
- [x] **거부 상태 동의 → 설정 안내 팝업 (기획 0-5, iOS 세션 요청 2026-11-17)**: `acceptAdConsent`가 동의 저장 뒤 시스템 창을 못 띄웠고 알림이 꺼져 있으면 `SettingsGuide`(가로 2버튼 「닫기」·「설정 열기」, 딤 무반응, 결과 토스트는 같이). 진입점 first_run·hype·settings 공통. 계측 `push_settings_guide_shown`·`push_settings_guide_opened{source}`
- [x] **로그아웃에 기기 토큰** (발송 조건 ⑤): `POST /auth/logout` 바디 `{refreshToken, deviceToken}`(`Push.token`, 없으면 키 생략). 서버는 아직 안 읽음
- [ ] 실기기: A(신규·업데이트) / B(2회째 실행, 좋아요 → 다음 곡) / C(특집 5/5 넘김, 동의+권한 허용 상태) / 마이 › 알림 토글 4종 토스트 / 뒤로 가기·딤 무반응
- [ ] 끝나면 app-changes 상태에 Android 버전

### QA 「추가 오류 케이스」 반영 — iOS 2026-11-19 코드 들어감, 안드로이드 따라갈 것

QA 시트(구글 시트 「추가 오류 케이스」 #1~#10)를 iOS가 먼저 고쳤다. **#4·#7·#10은 정본 「광고성 알림 노출 규칙」을 바꾼 것**이다(사용자 결정 2026-11-19). iOS 기준 파일은 괄호 안. 계측 이름은 새로 만들지 않았다.

- [x] **#1 업데이트 소식 시트가 떠 있는 동안 재생 안 함**, 닫으면 지금 곡부터 재생 + `track_viewed`(iOS `FeedView.isOnScreen`에 `showingWhatsNew` 추가, `onChange(showingWhatsNew)`). 안드로이드는 `Session.whatsNewOpen`을 피드 재생 조건에 넣으면 된다
- [x] **#4 B는 좋아요가 서버에 반영되면 바로 띄운다** — 다음 곡으로 넘길 때까지 미루지 않는다(iOS `offerHypePushIfNeeded`, `pendingHypeOffer` 삭제). 픽 재생·잠금화면(미디어 알림) 좋아요·코치마크·트랙 상세 떠 있음 제외는 그대로
- [x] **#7 B 조건: 「기기 권한 설정 전」 → 「광고성 동의 꺼짐」**(`adPushConsent` false). 마이에서 광고성을 끈 유저도 좋아요하면 다시 뜬다. 권한이 꺼져 있으면 「알림 받기」 뒤 기존 `SettingsGuide`가 뜬다. 실행당 하나·「일주일 동안 안 보기」 7일은 그대로
- [x] **#5 B 제목 첫 줄이 한 줄에 안 들어가면** 「(아티스트) 신곡」 대신 **「좋아요한 아티스트의 신곡」**. 둘째 줄 「소식을 받아보세요!」는 그대로, 대체 문구도 한 줄에 안 들어가면 0.7까지 줄인다(iOS `ConsentSheet` `ViewThatFits`). 새 문자열 ko 「좋아요한 아티스트의 신곡\n소식을 받아보세요!」 / en 「New from artists you like\nGet notified when they drop」. 제목 좌우는 X 버튼을 비켜 52
- [x] **#6 B 우측 상단 X(닫기)** — 44 터치 영역, 위·오른쪽 8, 아이콘 17 semibold `textSecondary`. X는 **닫기만**: 7일 기록도, 이번 실행 기록(`promptedThisSession`)도 남기지 않는다 → 다음 좋아요에 또 뜬다. 계측 없음
- [x] **#3 마이 화면 스크롤 시 상태바 뒤가 비침** → 상태바 영역을 흰 지면(`DSColor.background`)으로 막는다(iOS `MyPageView` 위 safe area). 안드로이드도 edge-to-edge라 같은 증상인지 먼저 확인
- [x] **#9 마이 › 알림: 권한 「설정 전」 + 광고성 토글 켬** → 시스템 권한 창**만** 먼저. 허용해야 동의 저장 + 결과 토스트, 거부면 저장·토스트 없이 꺼진 채(iOS `NotificationSettingsView.changeAdPush`, `AppSession.promptPushAuthorization`). `ad_consent_accepted{settings}`도 허용한 경우에만 찍는다
- [x] **#10 마이 › 알림: 「설정 전」 행 탭 = 시스템 권한 창**(정본 항목 10 「무반응」을 되돌림). 위 5-2의 원래 동작과 같다 — 2026-11-17에 무반응으로 바꿨으면 되돌린다
- 안드로이드 반영 2026-11-19(컴파일·유닛 테스트·lint 통과, 실기기 2026-11-21 통과):
  - #1 피드 `settledPage` 효과에 `Session.whatsNewOpen` 키 — 열려 있으면 `audio.stop()`, 닫히면 지금 곡 재생 + `onTrackViewed`(중복은 `lastViewedTrackId`가 거른다)
  - #4·#7 `LaunchedEffect(hypedFeed)` + `!Session.adPushConsent`. **iOS와 다른 곳:** 서버 반영 뒤가 아니라 낙관적 하입 시점에 뜬다
  - #5 한 줄 넘침은 `onTextLayout.hasVisualOverflow`로 가르고, 줄이기는 0.05씩 0.7까지(BOM 2024.12라 `autoSize` 없음). `consent_sheet_title_generic` ko/en
  - #6 `ConsentSheet(onClose)` → `promptedThisSession = false`
  - #3 재현 안 됨 — `MyPageScreen`은 `statusBarsPadding()`이 `verticalScroll` 바깥이라 스크롤 내용이 상태바 밑에서 잘린다. 할 일 없음
  - #9·#10 `NotificationSettingsScreen`: 「설정 전」이면 `requestPermission` 먼저, 켠 뒤 `notificationsEnabled`가 false면 저장·토스트·계측 없이 끝
- [x] **좋아요 시트(`ConsentSheet`) UI 수정 — 실기기에서 하단 탭바에 가려짐 (2026-11-22 스크린샷)**
  - 증상: 시트 아래쪽 「일주일 동안 안 보기」가 하단 탭바(피드·픽·마이) 밑에 깔려 글자가 아이콘과 겹친다. 탭바 영역만 회색으로 보이고, 시트는 탭바 위에서 끝난 것처럼 잘려 보인다
  - 원인: `ConsentSheet`가 `FeedScreen` 안의 `Box(fillMaxSize)` 오버레이라 피드 콘텐츠 층에 그려진다. 하단 탭바는 그보다 위 층(`MainActivity` Scaffold)이라 시트와 딤을 덮는다. 시트 안 `navigationBarsPadding()`은 제스처 바만 비키고 탭바는 못 비킨다
  - 지침(정본: 시트는 **탭바까지 덮는다** — iOS도 그래서 오버레이가 아니라 `.sheet`로 띄운다):
    - 창(window) 단위로 띄운다. 이 레포에서 이미 쓰는 `ModalBottomSheet`(`WhatsNewSheet`·`PickComposeSheet`)로 바꾸는 게 가장 짧다 — 탭바·상태바 위에 딤과 시트가 깔린다
    - 버튼으로만 닫히게 유지: `dragHandle = null`, `sheetGesturesEnabled = false`, `properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)`, `onDismissRequest = {}`, `rememberModalBottomSheetState(skipPartiallyExpanded = true)`. 딤은 `scrimColor = DSColor.dim`(35%), 모서리 `DSRadius.large`, `containerColor = DSColor.background`
    - `ModalBottomSheet`가 아래 inset(제스처 바)을 직접 비키므로 시트 안 `navigationBarsPadding()`은 지운다(두 번 밀림)
    - 지금 직접 만든 `AnimatedVisibility` 딤·슬라이드와 `BackHandler {}`는 `ModalBottomSheet`가 대신하니 지운다. 닫기(`promptVisible = false`)는 시트 상태 `hide()` 뒤 컴포지션에서 빼는 순서로
  - 같이 고칠 간격: 설명 문구 → 알림 미리보기 그림 사이가 **24**여야 하는데(iOS `.padding(.top, 24)`) 지금 `spacedBy(12)` + `Spacer(12)` + `spacedBy(12)` = 36. `Spacer`를 지우고 미리보기를 12 간격 Column 밖으로 빼서 `padding(top = 24.dp)`
  - 확인: 시트가 탭바를 완전히 덮고 「일주일 동안 안 보기」 아래로 제스처 바 여백이 남는다 / 딤 35%가 상태바부터 화면 끝까지 고르게 / 딤 탭·뒤로 가기·아래로 끌기 무반응 / X·「알림 받기」·「일주일 동안 안 보기」 셋 다 닫힘 / 3버튼 내비게이션 기기에서도 버튼이 안 가림
  - 반영 2026-11-21(컴파일·유닛 테스트·lint·실기기 통과): `ModalBottomSheet` + `confirmValueChange`로 Hidden 막음(닫을 때만 `canHide`), `visible` false면 `hide()` 뒤 컴포지션에서 뺀다. 미리보기 위 24. **다른 곳:** material3 1.3.1엔 `sheetGesturesEnabled`가 없어 아래로 끌면 살짝 내려갔다 제자리로 돌아온다(닫히진 않음) — BOM 올리면 `false`로
- #2 A 아티스트는 정본 유지(서버의 가장 최근 좋아요) — 할 일 없음
- #8(설정 앱에 Dignify 알림 메뉴 없음)은 iOS 전용 — 권한을 한 번도 요청하지 않은 iOS 앱은 설정 앱에 알림 메뉴가 없다. 안드로이드는 앱 알림 설정 화면이 항상 있다
- [ ] 끝나면 app-changes 「광고성 알림 노출 규칙 — 정본」 B 조건·시점과 항목 10을 고친다(iOS·안드로이드 둘 다 반영된 뒤)

### QA 2차(2026-11-22, iOS 빌드 31 재확인) — iOS 코드 들어감(빌드·실기기 전), 안드로이드 따라갈 것

정본을 또 바꾼다(사용자 결정 2026-11-22). 위 #2·#7·#10 줄은 이 절이 덮는다. 계측 이름은 새로 만들지 않았다.

- [x] **#2·#12 A 첫 실행 동의 팝업 삭제 — 광고성 동의는 B 좋아요 시트로만 받는다.** 피드 로드·업데이트 소식 닫힘·코치마크 끝에서 부르던 A 호출과 팝업, 7일 기록(iOS `adConsentOfferedAt`), 「미응답」 판정(iOS `AppSession.adPushAnswered`)을 지운다. A 제목 문자열도 삭제. `ad_consent_*{source: first_run}`은 더 안 찍힌다
  - **C 야간 팝업·「설정 열기」 안내(`SettingsGuide`)는 그대로 둔다**
- [x] **#13(제안) 수용 → #11·#7 같이 해결: B는 실행 횟수·「한 실행에 하나」와 무관하게 뜬다**
  - 「두 번째 실행부터」 조건 삭제(iOS는 `launchCount` 카운터째 지움 — 안드로이드도 B만 쓰면 지운다)
  - 「이번 실행에 팝업 띄움」(`promptedThisSession`)을 B 조건에서 뺀다. 대신 **지금 다른 팝업·시트가 떠 있으면** 건너뛴다(야간 팝업·`SettingsGuide`·B 자신·업데이트 소식·코치마크·트랙 상세·공유)
  - B를 띄우면 `promptedThisSession = true`는 그대로 세운다 → 그 실행의 C·픽 화면 팝업은 막힌다
  - 결과: 같은 실행에서 B로 동의 → 마이에서 끔 → 다시 좋아요하면 또 뜬다(#7). 「일주일 동안 안 보기」 7일, X = 닫기만은 그대로
- [x] **#14 마이 › 알림: 권한 「꺼짐」(거부됨)에서 광고성 토글 켬 → `SettingsGuide`만 띄운다. 저장·결과 토스트·`ad_consent_accepted{settings}` 없음, 토글은 꺼진 채**(정본 0-5 「저장 + 안내」에서 바꿈). `push_settings_guide_shown{settings}`는 찍는다. iOS `NotificationSettingsView.changeAdPush`
  - B 시트 「알림 받기」는 「꺼짐」이어도 지금처럼 저장 + 안내 + 토스트(마이 토글만 바꿈)
- [x] **#10·#15 마이 › 알림: 권한 「설정 전」 행 탭 = 토스트만, 시스템 권한 창 안 띄움**(11-19 「시스템 권한 창」을 다시 바꿈). 목적: 한 번뿐인 시스템 권한 창을 여기서 쓰지 않고 B·광고성 토글 때로 미룬다
  - iOS 문구 ko 「iPhone 설정에서 알림을 허용해 주세요」 / en 「Allow notifications in iPhone Settings」. **안드로이드는 「iPhone」이 안 맞으니 문구를 따로 정할 것**(예: 「기기 설정에서 알림을 허용해 주세요」 — 기획 확인)
  - 안드로이드 12 이하는 「설정 전」이 없다(권한 기본 허용) — 13+만 해당
- 안드로이드 반영 2026-11-22(컴파일·유닛 테스트·lint 통과, 실기기 전):
  - #2·#12 `Prompt.FirstRun`·A 효과·`canOfferAdConsent`/`markAdConsentOffered`·`Session.adPushAnswered`·`signedInFromGuest`·DTO `adPushConsentAt`·`ad_consent_popup_title` 삭제
  - #13 `Session.launchCount` 삭제(`countLaunch`는 `promptedThisSession`만 끈다). B는 `promptVisible`·`SettingsGuide.source`·`shareTarget`이 있으면 건너뜀(코치마크·업데이트 소식·트랙 상세 등은 `canPrompt`)
  - #14 `NotificationSettingsScreen` 토글: 「꺼짐」이면 `SettingsGuide.showIfDenied("settings")`만
  - #15 「설정 전」 행 탭 = `device_notifications_allow_in_settings` 토스트. 문구는 초안 ko 「기기 설정에서 알림을 허용해 주세요」 / en 「Allow notifications in your device settings」 — **기획 확인 필요**
- [ ] 끝나면 app-changes 정본에 옮긴다: A 삭제 · B 「한 실행에 하나」 예외 · 「꺼짐」 토글 = 안내만 · 「설정 전」 행 탭 = 토스트

**기획 확인 끝 (2026-11-18):** 야간 시간 「오후 9:00 ~ 오전 8:00」(팝업·토글 설명) · 시트 동의 문구 시안 그대로 · 주간 추천 최대 5곡(What's New·스토어 노트) · 시트 버튼 brand(시안). 시안 없는 문구(배지·「수신거부」·설정 안내)는 초안 확정, en은 직접 다듬음

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

### 6. Confluence 기획 댓글 반영 (댓글 2026-11-01, 코드 대조 2026-11-04)

항목과 결정 대기는 iOS `TODO.md` 재방문 푸시 5와 1:1이다. 서버 몫은 `dignify-backend/TODO.md` P1-b 5. 안드로이드 코드 위치만 적는다.

- [ ] 동의 팝업 문구·「동의」「거부」 버튼: `FeedScreen` `Prompt.FirstRun`·`Prompt.Hype`·`Prompt.NewRelease`, `ad_consent_*`·`hype_optin_*` 문자열. 바깥 탭·뒤로 가기가 거부로 기록되는지 확인
- [ ] 0-5 상태별 규칙: `Push.permissionUndecided` 조건과 `rememberEnableNotifications`(`feature/push/AdConsent.kt`). `adPushConsentAt`으로 미응답과 거부 구분
- [ ] 결과 통지 팝업 → 토스트: `AdConsentNotice`. 알림 「수신거부」 결과를 같은 알림으로 갈아 끼우는 건 유지. 지금 문구는 토스트에서 잘려서 창으로 둔 것이라(`AdConsentNotice` 주석) 문구를 같이 줄인다
- [ ] 마이 › 알림 카테고리 토글·기기 알림 꺼짐 안내: `NotificationSettingsScreen`(서버 카테고리 배포 뒤). 카테고리별 OFF 계측은 iOS와 같은 이름으로
- [ ] 로그아웃 시 기기 토큰 처리: `Session`(서버 계약 뒤)
- [ ] 수신거부: 앱이 직접 만든 액션(`PushNotifications`·`OptOutReceiver`)을 iOS와 같은 결정에 맞춘다. 문구가 바뀌면 `ad_push_opt_out`, `(광고)` 위치가 바뀌면 `debugPushes`도. 이 버튼엔 PostHog 이벤트가 없어 `push_logs.opted_out`으로만 보인다

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
