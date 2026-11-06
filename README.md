# 명함 갈피 — Business Cards 🗂️

모바일 최적화된 PWA 명함 관리 앱입니다. GitHub Pages에서 바로 동작하며, 스마트폰 홈 화면에 추가하면 일반 앱처럼(주소창 없는 독립 화면) 실행됩니다.

**접속 주소:** https://jskimlam.github.io/business-cards/

## 기능

- 🔍 **검색** — 이름·한자·회사·직함·이메일·전화번호·태그 통합 검색
- 🏷️ **필터 & 정렬** — 태그(국내/일본 등)로 걸러 보기, 이름순·회사순·최근순 정렬
- 📇 **상세 보기** — 명함 스캔 이미지(탭하면 원본 크게), 연락처 목록
- 📞 **원클릭 액션** — `tel:` 전화 걸기, `mailto:` 메일 보내기
- 💾 **vCard(.vcf) 저장** — 버튼 한 번으로 연락처 파일 다운로드 → 주소록에 저장
- 📱 **PWA** — manifest + service worker 탑재, 한 번 연 후 오프라인에서도 열림

## 홈 화면에 추가하는 법 (안드로이드 Chrome)

1. 위 주소로 접속
2. 우측 상단 메뉴(⋮) → **앱 설치** 또는 **홈 화면에 추가**
3. 홈 화면의 **명함갈피** 아이콘으로 실행

## 새 명함 추가하는 법

`data/cards.json`을 편집하면 됩니다. `cards` 배열에 아래 형식의 객체를 하나 추가하고 커밋·푸시하면 사이트에 자동 반영됩니다.

```json
{
  "id": "고유-영문-id",                       // 필수 · 중복 금지 (예: "kim-minsu")
  "name": "김민수",                           // 필수 · 이름
  "nameSub": "金民洙",                        // 선택 · 한자/영문표기/로마자
  "title": "차장",                            // 선택 · 직함
  "company": "㈜대한",                         // 필수 · 회사명
  "companySub": "영업본부 · 해외팀",            // 선택 · 부서/브랜드 설명
  "phones": [                                 // 필수 · 최소 1개
    { "type": "mobile", "label": "Mobile", "number": "010-1234-5678" },
    { "type": "tel", "label": "Tel", "number": "02-123-4567" }
  ],
  "fax": "02-123-4568",                       // 선택
  "email": "minsu@example.com",               // 권장
  "website": "https://example.com",           // 선택
  "address": "서울특별시 강남구 테헤란로 123",   // 선택
  "addressDetail": "8층 · 우편번호 06123",      // 선택 · 보조 주소
  "image": "images/card-고유-영문-id",          // 선택 · images/ 내 파일 (확장자 제외)
  "tags": ["국내", "화학"],                     // 선택 · 필터용
  "added": "2026-11-06"                       // 선택 · 추가일 (최근순 정렬 기준)
}
```

### 명함 스캔 이미지 함께 올리기

1. 사진을 `images/` 폴더에 넣습니다. 가로 1200px 내외, WebP(권장) 또는 JPEG.
2. `"image"` 값은 확장자를 뺀 경로로 적습니다. 예: `images/card-kim-minsu`
   - 앱이 같은 이름의 `.webp`를 우선 사용하고 없으면 `.jpg`를 사용합니다. 둘 중 하나만 있어도 됩니다.
3. 이미지 없이 JSON만 추가해도 동작합니다(스캔본만 빠짐).

### 커밋 & 푸시

```bash
git add data/cards.json images/ && git commit -m "명함 추가: 김민수" && git push
```

푸시 후 1~2분 안에 사이트에 반영됩니다.

## 구조

```
├── index.html        # 앱 본체 (단일 페이지, 외부 라이브러리 없음)
├── manifest.json     # PWA 설치 정보
├── sw.js             # 서비스 워커 (오프라인 캐시)
├── data/cards.json   # 명함 데이터 ← 여기에 추가
├── images/           # 명함 스캔본 (webp/jpg)
└── icons/            # 앱 아이콘
```
