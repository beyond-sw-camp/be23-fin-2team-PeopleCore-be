# Face Recognition Module

## 설치 및 실행

```bash
# 1. 가상환경 생성
python -m venv venv

# 2. 가상환경 활성화 (Windows)
venv\Scripts\activate

# 3. 의존성 설치
pip install -r requirements.txt

# 4. 서버 실행
python face_api.py
```

서버가 `http://localhost:8001`에서 실행됩니다.

## 헬스 체크

```
GET http://localhost:8001/health
```
