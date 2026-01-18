# Elasticsearch 인덱스 템플릿

## 개요
- 대상 인덱스: `works_v*`, `episodes_v*`, `assets_v*`
- 매핑 정책: `dynamic: strict`로 스키마 일탈을 방지합니다.
- 키워드 필드: `lowercase_normalizer`로 대소문자 차이를 흡수합니다.

## 적용 방법
```bash
bash infra/elasticsearch/apply_templates.sh
```

## 참고
- alias(`*_read`, `*_write`)는 블루/그린 재색인 전략에 맞춰 Indexing Service에서 제어합니다.
