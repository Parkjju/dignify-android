# 릴리즈 배포

Play Console 업로드까지. 현재 기준값: `versionCode 11` / `versionName 1.1.2` / `targetSdk 36`.

## 1. 버전 올리기

`app/build.gradle.kts`

| 값 | 규칙 |
|---|---|
| `versionCode` | 업로드마다 **새 번호**. 한 번 쓴 값은 그 업로드를 지워도 다시 못 쓴다. 아직 업로드 안 한 번호는 그대로 두고 재사용한다 — 미리 올려두면 그 번호가 영영 죽는다 |
| `versionName` | 유저에게 보이는 값. **What's New가 이 값으로 판단한다** — 안 올리면 노트를 넣어도 화면이 안 뜬다 |

백엔드가 UA(`dignify/<versionCode>`)에서 `versionCode`를 주워 담아 푸시를 버전별로 갈라 쏜다. 두 값이 따로 노는 건 정상이다.

## 2. What's New (새 소식을 띄울 때만)

버그 수정만이면 건너뛴다. `Changelog.shouldShow`가 노트 없는 버전은 아예 안 띄운다.

1. `Changelog.releases` **맨 앞**에 `Release` 추가 (최신이 위, 시트가 이 순서로 그린다)
2. 문자열은 `values/strings.xml` + `values-ko/strings.xml` **양쪽**

안드로이드에 실제로 나간 것만 적는다. iOS와 버전이 따로 가므로 iOS 노트를 옮기면 "원래 있던 것"을 새 소식이라 말하게 된다.

판정 로직 (`Changelog.shouldShow`):

```
노트 없는 버전        → 안 띄움
lastSeen 비어 있음    → 기존 유저에게만 (신규 설치는 튜토리얼 대상)
그 외                 → lastSeen != current 일 때
```

## 3. 빌드

```sh
./gradlew testReleaseUnitTest bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

### 서명 확인 — 조용히 실패하는 자리

`keystore.properties`는 gitignore라 **없어도 빌드가 성공한다.** 대신 서명 없이 나간다.

```sh
unzip -l app/build/outputs/bundle/release/app-release.aab | grep "META-INF/.*\.RSA"
# META-INF/DIGNIFY-.RSA  ← 있으면 서명됨
```

APK는 파일명으로 갈린다: `app-release.apk`(서명됨) vs `app-release-unsigned.apk`.

## 4. 산출물 검증

```sh
aapt2=~/Library/Android/sdk/build-tools/36.0.0/aapt2
./gradlew assembleRelease
$aapt2 dump badging app/build/outputs/apk/release/app-release.apk | grep -E "^package|targetSdk"
# package: name='com.rta.dignify' versionCode='5' versionName='1.0.1' ... compileSdkVersion='36'
# targetSdkVersion:'36'
```

### 16KB 페이지 (targetSdk 35+ 필수)

Compose가 `libandroidx.graphics.path.so`를 4개 ABI로 끌고 들어오므로 이 앱도 대상이다. AGP 8.13.2가 처리해주지만 확인은 해둔다:

```sh
~/Library/Android/sdk/build-tools/36.0.0/zipalign -c -P 16 -v 4 \
  app/build/outputs/apk/release/app-release.apk | tail -1
# Verification successful
```

## 5. 실기기 사전 확인

**릴리즈 APK를 폰에 직접 넣으려 하지 말 것.** 폰에 깔린 게 디버그 빌드면 서명이 안 맞아 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`이 뜬다.

```sh
adb shell 'pm list packages -i com.rta.dignify'
# installer=null            → adb로 깔린 디버그 빌드
# installer=com.android.vending → Play 빌드
```

```sh
./gradlew installDebug      # 같은 코드, 서명 일치, 데이터 유지
```

**기존 앱을 지우고 릴리즈를 넣지 말 것.** 앱 데이터가 날아가면 What's New(`lastSeen != current`)·로그인 유지·`seenCurationSet`을 쓰는 확인이 전부 무의미해진다. 정확히 그 화면들을 보려고 올리는 빌드인데 그걸 지우는 셈이다.

데이터 확인은 디버그 빌드에서만 된다:

```sh
adb shell "run-as com.rta.dignify cat shared_prefs/dignify.xml"
```

## 6. Play Console 업로드

**테스트 → (내부 / 비공개) → 새 버전 만들기 → AAB 업로드 → 출시 노트 → 검토 → 출시 시작**

| 트랙 | 검토 | 반영 |
|---|---|---|
| 내부 테스트 | 없음 | 몇 분 |
| 비공개 테스트 | 있음 | 몇 시간 ~ 하루 이상 |

**기존 테스터에게 따로 할 일은 없다.** 이미 트랙에 등록돼 있어 재초대·링크 재발송이 필요 없고, Play 자동 업데이트로 내려간다. 급하면 "Play 스토어 → 내 앱 → 업데이트"를 눌러달라고 한다.

## 7. 프로덕션 승격

테스트 트랙 출시만으로는 **정책 경고가 안 풀린다.** targetSdk 요구사항 같은 건 프로덕션에 올라가야 해제된다.

targetSdk 기한은 매년 8월 말이고, 최신 안드로이드 출시로부터 1년 이내를 요구한다. 못 맞추면 업데이트 자체를 못 올린다.

| 기한 | 요구 |
|---|---|
| 2026-08-30 | targetSdk 36 (Android 16) — **충족** |

## 순서 요약

```sh
# 1. versionCode / versionName 수정, 필요하면 Changelog + strings 양쪽
# 2. 빌드 + 테스트
./gradlew testReleaseUnitTest bundleRelease assembleRelease

# 3. 검증
unzip -l app/build/outputs/bundle/release/app-release.aab | grep "META-INF/.*\.RSA"
~/Library/Android/sdk/build-tools/36.0.0/aapt2 dump badging \
  app/build/outputs/apk/release/app-release.apk | grep -E "^package|targetSdk"

# 4. 실기기 확인 (데이터 유지)
./gradlew installDebug

# 5. Play Console에 app-release.aab 업로드 → 테스트 트랙 → 확인 후 프로덕션 승격
```
