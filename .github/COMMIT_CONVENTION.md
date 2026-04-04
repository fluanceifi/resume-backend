# Commit Convention

## 형식

```
<type>(<scope>): <subject>
```

## Type

| type | 용도 |
|------|------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 코드 개선 |
| `style` | CSS, 레이아웃 변경 |
| `docs` | 문서 수정 |
| `chore` | 빌드, 설정, 의존성 변경 |

> `deploy(fe/be):` 는 infra 레포 자동 커밋 전용 — 수동 커밋에 사용 금지

## Scope (선택)

컴포넌트 또는 영역을 소문자로: `hero`, `chat`, `nav`, `skills`, `ci`, `nginx` 등

## 예시

```
feat(chat): IME 입력 중 엔터 전송 차단
fix(hero): 모바일에서 프로필 사진 비율 깨지는 문제 수정
style(nav): 탭 active 상태 색상 조정
chore(ci): GHCR 이미지 태그 SHA 7자리로 변경
docs: Phase 7 완료 체크박스 업데이트
```

## 규칙

- subject는 한국어 또는 영어, 명령형으로
- 마침표 없이
- 제목 72자 이내
