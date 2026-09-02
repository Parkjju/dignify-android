# Play 출시 노트

업로드할 때 Play Console에 붙여 넣는 "이 버전의 새로운 기능"이다. **앱 안의 What's New
(`Changelog.kt`)와 다른 글이다** — 이쪽은 스토어에서 업데이트 목록을 훑는 사람이 읽고,
저쪽은 이미 업데이트를 받은 사람이 읽는다. 그래서 여기엔 버그 수정도 한 줄 들어간다.

- 언어는 **한국어(대한민국)만** 낸다(`../../marketing/play/listing.md`와 같은 판단 — 주력 시장이
  한국이고 앱 화면이 한국어다). EN은 나중에 쓸 수 있게 같이 적어 둔다.
- 한 언어당 **500자**가 상한이다.
- 기준은 **직전에 Play에 올라간 빌드**지 직전 커밋이 아니다. 하입 따라가기 피드는
  `versionCode 8`로 이미 나갔으므로 새 소식이 아니다 — 1.1.0은 그 사실에 이름과 앱 안 노트만
  붙인 릴리즈였고 Play에는 올라가지 않았다.

---

## 1.1.2 (versionCode 11)

기준: `versionCode 8` (1.0.1, 마지막으로 Play에 올라간 빌드).

### KO

```
• 시작 화면이 바뀌었어요. 곡을 눌러 들어보고 마음에 드는 3곡을 고르면 첫 피드부터 그 소리로 이어집니다. 찾는 곡이 없으면 검색해서 골라도 돼요.
• 픽 만들기: 하입한 곡이 날짜별로 정리되고, 고르는 중에 바로 들어볼 수 있어요.
• 내가 만든 픽이 몇 번 재생됐는지 카드에 표시됩니다.
• 알림을 누르면 이번 주 큐레이션이나 내 픽으로 바로 갑니다.
• 픽이 올라가지 않았는데 올라간 것처럼 보이던 문제를 고쳤어요.
```

### EN (보류 — 등록정보가 KO만이라 지금은 안 올린다)

```
• New start: play a few tracks, pick the three you like, and your first feed follows that sound. Can't find one? Search for it.
• Making a pick: your hypes are sorted by day, and you can play them while you choose.
• Your picks now show how many times people played them.
• Notifications now open the weekly set or your picks directly.
• Fixed a pick looking posted when the server had rejected it.
```
