# 청크를 임베딩해서 ChromaDB에 저장하고 검색용으로 불러온다.
# 랭체인 인덱싱 API + RecordManager로 중복 생인 + 자동 정합성
# RAG 3단계 : 임베딩 + 벡터 DB 저장  RecordManager로 중복 색인 & 자동 정합성

from app.config import settings  # 우리 설정값


from langchain_huggingface import HuggingFaceEmbeddings  # 로컬 임베딩 모델
from langchain_chroma import Chroma  # 벡터 DB
from langchain_classic.indexes import SQLRecordManager, index  # 인덱싱 API
from typing import Literal



def get_embeddings() -> HuggingFaceEmbeddings:
    # 임베딩 모델 준비
    return HuggingFaceEmbeddings(
        model_name=settings.embedding_model,  # BAAI/bge-m3
        cache_folder=str(settings.model_cache_dir),  # 프로젝트 안에 임베딩 모델 다운로드
        model_kwargs={"device": "cpu"},  # GPU 없으면 cpu
        encode_kwargs={"normalize_embeddings": True},  # 벡터 길이 정규화 (검색 품질 향상을 위함)
    )


def get_vectorstore() -> Chroma:
    # 이미 저장된 ChromaDB 열기 -> 저장, 검색할 때 이 객체 하나로 처리
    return Chroma(
        collection_name="peoplecore",  # db안에 가상의 공간 이름
        persist_directory=str(settings.chroma_db_dir),  # 디스크 저장 위치 -> chroma_db/ 폴더
        embedding_function=get_embeddings(),  # 검색 시 질문을 벡터로 바꿀 변환기
    )


def _get_record_manager() -> SQLRecordManager:
    # RecordManager를 준비한다
    # 어떤 청크를 색인했는지 기록해두는 용도 -> 이걸 통해서 추가,생략,수정,삭제를 판단함
    # 함수 이름 앞에 _는 이 파일 안에서만 쓰는 내부용이라는 표시
    record_manager = SQLRecordManager(
        # namespace : 이 색인의 고유 이름표
        namespace="chroma/peoplecore",
        # dbURL :기록을 저장할 SQLite 파일 위치
        # as_posix -> 윈ㄷ우 경로를 URL용으로 바꿈
        db_url=f"sqlite:///{settings.record_db_path.as_posix()}",
    )
    record_manager.create_schema()  # 기록용 테이블을 최초 1회 생성 (이미 있으면 그냥 통과)
    return record_manager


def build_vectorstore(cleanup: Literal[
                                   "incremental", "full", "scoped_full"] | None = "full") -> Chroma:  # cleanup="full" -> 이번에 안 들어온 문서의 옛 청크까지 정리 (완전 동기화)
    # 문서 로딩 -> 청킹 -> 색인까지 수행
    # cleanup = incremental : 처리하면서 바뀐 부분만 점진적으로 정리

    # 앞 함수들을 불러오기 (순환 import 방지를 위해 함수 안에서 import
    from app.core.loader import load_documents
    from app.core.splitter import split_documents

    docs = load_documents()  # data/ 폴더의 문서를 읽기
    chunks = split_documents(docs)  # 1000자/200자 겹침으로 토막내기

    vectorstore = get_vectorstore()  # 저장할 벡터 DB 열기
    record_manager = _get_record_manager()  # 색인 기록부 준비

    print(f"색인 중 ... (cleanup={cleanup})")

    result = index(
        chunks,  # 색인할 청크들
        record_manager,  # 색인 기록부
        vectorstore,  # 저장 대상 벡터 DB
        cleanup=cleanup,  # 정합성 모드
        source_id_key="source",  # 청크를 묶는 기준 = metadate["source"](파일 경로)
        key_encoder="sha256", # 해쉬화 알고리즘 sha-256 사용
    )
    # result 예 : {'num_added' : 1, 'num_updated': 0, 'num_skipped':0 ...
    print(f"색인 결과 : {result}")
    return vectorstore

    # 직접 실행하면: 색인 만들고 -> 간단한 검색 테스트


if __name__ == "__main__":
    vs = build_vectorstore()  # 색인 실행

    query = "연차는 며칠 부여되나요?"
    result = vs.similarity_search(query, k=2)  # 의미상 가까운 청크 2개 찾기

    print(f"\n --검색 테스트 : '{query}' --- ")
    for i, doc in enumerate(result, 1):  # 1번부터 번호 매겨 출력
        print(f"[{i}] {doc.page_content[:80]}")  # 청크 앞 80자만 미리보기
