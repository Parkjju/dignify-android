# 무선 ADB 재연결

Android Studio가 `Unavailable on device <기기> at <IP>:<포트>` 를 띄울 때.

```sh
adb=~/Library/Android/sdk/platform-tools/adb
```

## 왜 생기나

- **연결 포트는 무선 디버깅이 재시작될 때마다 바뀐다.** Studio는 옛 포트를 캐시하고 죽은 주소로 계속 시도한다. `Unavailable on device`는 그 결과지 원인이 아니다.
- **페어링은 일반 재부팅은 견디지만 OS 업그레이드에서는 지워진다.** 2026-08-19 moto g(2025)가 Android 16으로 올라가면서 실제로 날아갔다.

## 진단

위에서부터. 각 단계가 다음 단계를 좁힌다.

### 1. 연결 상태

```sh
$adb devices -l
```

비어 있으면 Studio가 들고 있는 주소는 죽은 참조다.

### 2. 폰이 광고 중인가

```sh
$adb mdns services      # adb-XXXX  _adb-tls-connect._tcp  192.168.0.170:43375
```

> **결과가 비었다고 "무선 디버깅 꺼짐"으로 단정하지 말 것.** 이건 캐시다. `adb kill-server` 직후에는 항상 비어 있고 다시 채워지는 데 10초쯤 걸린다. 한 번 더 확인하고 판단한다.

여기 나오는 포트가 **연결 포트**다. Studio가 보여주는 것과 다르면 그게 원인이다.

### 3. 포트가 실제로 열려 있나

```sh
nc -z -w 3 192.168.0.170 43375 && echo OPEN || echo CLOSED
```

| 포트 | `adb connect` | 진단 |
|---|---|---|
| OPEN | 성공 | 끝. 포트만 바뀌었던 것 |
| **OPEN** | **실패** | **TLS 핸드셰이크 거부 = 페어링 소실 → 4번** |
| CLOSED | — | 무선 디버깅이 꺼져 있다. 폰에서 켠다 |

포트가 열렸는데 연결이 거부되는 게 페어링 소실의 지문이다. 이때 포트를 바꿔 붙여봐야 소용없다.

## 재페어링

폰: `설정 → 개발자 옵션 → 무선 디버깅 → 페어링 코드로 기기 페어링`

```sh
$adb pair 192.168.0.170:46753 201780
# Successfully paired to 192.168.0.170:46753 [guid=adb-ZA223J7Q96-L10YRa]
```

> **페어링 포트 ≠ 연결 포트.** 위 화면에 뜨는 번호는 그 대화상자에서만 쓰는 일회용이고, `adb mdns services`가 보여주는 연결 포트와 전혀 다른 숫자다. 헷갈려서 연결 포트로 `pair`를 치면 실패한다.

페어링이 끝나면 **adb가 mDNS로 알아서 붙는다.** `adb connect`가 실패해도 무시하고 확인만 한다:

```sh
$adb devices -l
# adb-ZA223J7Q96-L10YRa._adb-tls-connect._tcp  device  model:moto_g___2025
```

## 붙은 다음 — 설치

Studio의 Run 버튼 대신 CLI로 넣을 때 서명 때문에 막힌다:

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match
```

폰에 깔린 게 무엇으로 서명됐는지부터 본다:

```sh
$adb shell 'pm list packages -i com.rta.dignify'   # installer=null → adb로 깔린 디버그 빌드
```

- `installer=null` → 디버그 빌드다. **`./gradlew installDebug`** 를 쓴다
- `installer=com.android.vending` → Play 빌드다. Play 앱 서명 키라 로컬 릴리즈 APK와 절대 안 맞는다

**릴리즈 APK를 넣겠다고 기존 걸 지우지 말 것.** 앱 데이터가 날아가고, 그러면 What's New(`lastSeen != current` 로 뜬다)·로그인 상태·`seenCurationSet`을 쓰는 테스트가 전부 무의미해진다. 같은 코드를 디버그로 올리면 서명이 맞아서 데이터를 유지한 채 업데이트된다.

데이터 확인은 디버그 빌드에서만 된다:

```sh
$adb shell "run-as com.rta.dignify cat shared_prefs/dignify.xml"
```

## USB로 넘어갈 때 (macOS)

무선이 안 되면 USB인데, 안 잡힐 때 어디까지 갔는지 보는 법.

```sh
ioreg -p IOUSB -w 0                      # 폰이 USB 버스에 올라왔나
```

아무것도 안 보이면 맥이 전기적 연결이라도 봤는지 확인한다:

```sh
/usr/bin/log show --last 5m --predicate 'process == "kernel"' --style compact \
  | grep -E "Port-USB-C@[0-9]" | grep -iE "connectionActive|registerService"
```

> `log`는 zsh에서 가로채여 `too many arguments`로 죽는다. **반드시 `/usr/bin/log`** 로 친다. 이걸 모르고 빈 출력을 "USB 이벤트 0건"의 근거로 삼으면 진단이 통째로 어긋난다.

| 로그 | 의미 |
|---|---|
| `m_connectionActive: YES` 인데 `IOUSBHostDevice` 없음 | CC 핀 협상·전력은 되는데(충전됨) 데이터 열거가 안 됨 → 폰 쪽 문제. 포트 이물질이거나 USB 게이트웨이가 물린 상태. 폰 재부팅 후 포트 청소 |
| 아무 이벤트 없음 | 케이블이 전력선만 있거나 단선 |

폰 알림창에 "USB로 이 기기 충전 중" 알림이 **뜨는지**가 가장 빠른 갈림길이다. 안 뜨면 폰도 데이터 연결을 못 보고 있는 것이다.

## 재발 방지

USB로 한 번 붙일 수 있을 때 해두면 포트가 안 바뀐다:

```sh
$adb tcpip 5555          # 케이블 뽑아도 유지. 단, 폰 재부팅하면 초기화
```
