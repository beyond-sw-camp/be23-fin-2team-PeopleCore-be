import base64
import io
import os

import chromadb
import face_recognition
import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image
from pydantic import BaseModel

app = FastAPI(title="PeopleCore Face Recognition API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Chroma DB 초기화 ──
CHROMA_DB_PATH = os.path.join(os.path.dirname(__file__), "chroma_data")
chroma_client = chromadb.PersistentClient(path=CHROMA_DB_PATH)

face_collection = chroma_client.get_or_create_collection(
    name="face_embeddings",
    metadata={"hnsw:space": "cosine"},
)

# 유사도 임계값 (코사인 거리 기준 — 낮을수록 유사)
SIMILARITY_THRESHOLD = 0.6


# ── 요청 모델 ──
class ExtractRequest(BaseModel):
    image: str


class RegisterRequest(BaseModel):
    image: str
    emp_id: int
    emp_name: str


class RecognizeRequest(BaseModel):
    image: str


# 얼굴 크기 임계값 (픽셀 기준)
MIN_FACE_HEIGHT = 80   # 이보다 작으면 너무 멀다
MAX_FACE_HEIGHT = 300  # 이보다 크면 너무 가깝다


# ── 공통 함수: 이미지에서 벡터 추출 ──
def extract_face_vector(base64_image: str) -> list[float]:
    try:
        image_data = base64.b64decode(base64_image)
        image = Image.open(io.BytesIO(image_data))
        image_array = np.array(image)
    except Exception:
        raise HTTPException(status_code=400, detail="이미지 디코딩에 실패했습니다.")

    face_locations = face_recognition.face_locations(image_array)

    if len(face_locations) == 0:
        raise HTTPException(status_code=400, detail="얼굴을 찾을 수 없습니다. 카메라를 정면으로 바라봐 주세요.")

    if len(face_locations) > 1:
        raise HTTPException(
            status_code=400,
            detail="2개 이상의 얼굴이 감지되었습니다. 1명만 촬영해주세요.",
        )

    # 얼굴 크기 기반 거리 판단 (top, right, bottom, left)
    top, right, bottom, left = face_locations[0]
    face_height = bottom - top

    if face_height < MIN_FACE_HEIGHT:
        raise HTTPException(
            status_code=400,
            detail="얼굴이 너무 멀리 있습니다. 카메라에 가까이 다가와 주세요.",
        )

    if face_height > MAX_FACE_HEIGHT:
        raise HTTPException(
            status_code=400,
            detail="얼굴이 너무 가까이 있습니다. 카메라에서 조금 떨어져 주세요.",
        )

    encodings = face_recognition.face_encodings(image_array, face_locations)
    return encodings[0].tolist()


# ── 엔드포인트 ──
@app.get("/health")
def health_check():
    return {"status": "ok", "service": "face-recognition"}


@app.post("/extract")
def extract_embedding(request: ExtractRequest):
    embedding = extract_face_vector(request.image)
    return {
        "embedding": embedding,
        "dimension": len(embedding),
    }


@app.post("/register")
def register_face(request: RegisterRequest):
    embedding = extract_face_vector(request.image)

    doc_id = str(request.emp_id)

    existing = face_collection.get(ids=[doc_id])
    if existing["ids"]:
        face_collection.delete(ids=[doc_id])

    face_collection.add(
        ids=[doc_id],
        embeddings=[embedding],
        metadatas=[{"emp_id": request.emp_id, "emp_name": request.emp_name}],
    )

    return {
        "status": "registered",
        "emp_id": request.emp_id,
        "emp_name": request.emp_name,
        "message": f"{request.emp_name}님의 얼굴이 등록되었습니다.",
    }


@app.post("/recognize")
def recognize_face(request: RecognizeRequest):
    # 1. 등록된 얼굴이 있는지 확인
    if face_collection.count() == 0:
        raise HTTPException(status_code=404, detail="등록된 얼굴이 없습니다.")

    # 2. 벡터 추출
    embedding = extract_face_vector(request.image)

    # 3. Chroma에서 가장 유사한 벡터 검색
    results = face_collection.query(
        query_embeddings=[embedding],
        n_results=1,
    )

    # 4. 결과 확인
    if not results["ids"] or not results["ids"][0]:
        raise HTTPException(status_code=404, detail="일치하는 얼굴을 찾을 수 없습니다.")

    distance = results["distances"][0][0]
    metadata = results["metadatas"][0][0]

    # 5. 임계값 확인 (코사인 거리: 0에 가까울수록 유사)
    if distance > SIMILARITY_THRESHOLD:
        raise HTTPException(
            status_code=401,
            detail=f"얼굴이 일치하지 않습니다. (유사도 거리: {distance:.4f})",
        )

    return {
        "matched": True,
        "emp_id": metadata["emp_id"],
        "emp_name": metadata["emp_name"],
        "distance": round(distance, 4),
    }


@app.delete("/unregister/{emp_id}")
def unregister_face(emp_id: int):
    doc_id = str(emp_id)

    existing = face_collection.get(ids=[doc_id])
    if not existing["ids"]:
        raise HTTPException(status_code=404, detail="해당 사원의 얼굴 정보가 등록되어 있지 않습니다.")

    face_collection.delete(ids=[doc_id])

    return {
        "status": "deleted",
        "emp_id": emp_id,
        "message": "얼굴 정보가 삭제되었습니다.",
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8001)
