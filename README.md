# 와파 WAFA — 안드로이드 (com.weaveapp.wafa)

바로너(= 부름 안드로이드 버전 30 바탕)와 같은 방식의 "키트"입니다. 화면은 https://wafa.kr 에서 불러옵니다.

## 처음 한 번
1. `C:\baronu-app` 폴더를 통째로 복사해서 `C:\wafa-app` 으로 이름을 바꿉니다.
2. Firebase(부름 · 바로너와 같은 프로젝트) → 프로젝트 설정 → 앱 추가 → Android → 패키지 이름 `com.weaveapp.wafa`
   → `google-services.json` 을 받아 `C:\wafa-app\android\app\` 에 넣습니다(바로너 것은 덮어씁니다).
   (⚠ 이 파일은 채팅에 붙여 넣지 마세요)
3. 이 저장소를 받아(Code → Download ZIP) 풀고 `apply.bat` 를 두 번 눌러 실행합니다.
4. 필요하면 `cleanup.bat` 로 남은 다른 앱 자바 파일을 지웁니다.
5. Android Studio 로 `C:\wafa-app\android` 열기 → Sync → Build → Generate Signed App Bundle
   - 서명 키: 와파용 새 키(`C:\keys\wafa-upload.jks`)를 만들어 쓰세요. 비밀번호는 직접 보관하세요.

## 이 키트에서 바뀐 것 (바로너 → 와파)
- applicationId `com.weaveapp.wafa` · 버전 1 (1.0) · 앱 이름 WAFA · 아이콘 · 첫 화면 그림
- 첫 화면 주소 `https://wafa.kr/wafa_start.html`
- **자동결제 구독**: `AliasNative.storeSubscribe(상품, 기본요금제, 로그인한사람id)` — BillingBridge.subscribe
  (구독 상품 `wafa_plan`, 기본 요금제 `wafa-1m · wafa-3m · wafa-6m · wafa-12m`, obfuscatedAccountId 를 넣어 서버가 확인)
- 앱을 켤 때 서버가 아직 확인하지 못한 구독 영수증도 다시 넘김
