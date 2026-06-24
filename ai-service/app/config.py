# config.py -> 프로젝트 전역 설정 로딩
# .env 파일에 적어둔 값(API 키, 모델명, 청크 크기 등)을 읽어서 파이썬 코드 어디서든 settings.** 형태로 꺼내 쓸 수 있게 모아두는 곳

# ex) from app.config import settings
#  print(settings.claude_model) # "claude-sonnet-4-6"

import os #운영체제 기능
from pathlib import Path
from dotenv import load_dotenv  # .env 파일을 읽어 환경변수로 올려주는 도구

# 1. env 파일 위치 찾기
BASE_DIR = Path(__file__).resolve().parent.parent  # -> ai-service  __file__ -> 현재 파일 경로 resolve-> 절대 경로로 변환 .parent -> 부모 경로로
ENV_PATH = BASE_DIR / ".env"

# 2. .env 읽어서 환경변수로 로딩
load_dotenv(ENV_PATH)


class Settings:
    # 프로그램이 쓰는 모든 설정값을 한곳에 모아둔 클래스

    # claude
    # os.getenv ("키이름","기본값") : env에 값이 있으면 그걸, 없으면 기본값
    anthropic_api_key: str = os.getenv("ANTHROPIC_API_KEY", "")
    claude_model: str = os.getenv("CLAUDE_MODEL", "claude-sonnet-4-6")

    # llm 제공자
    llm_provider: str = os.getenv("LLM_PROVIDER", "claude")
    # ollama로 돌릴 때 쓸 모델 이름
    ollama_model: str = os.getenv("OLLAMA_MODEL","gemma4:12b")

    #  임베딩 모델 (로컬 HuggingFace, 한국어 우수)
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "BAAI/bge-m3")
    # 임베딩 모델을 받을 폴더 (프로젝트 안 models/)
    model_cache_dir: Path = BASE_DIR / os.getenv("MODEL_CACHE_DIR", "./models")
    # recordManager가 뭘 색인했는지 기록할 SQLite 파일 경로
    record_db_path: Path = BASE_DIR/"record_manager.sqlite"

    # 경로 (.env 값은 문자열이라 Path 로 감싸 다루기 쉽게)
    chroma_db_dir: Path = BASE_DIR / os.getenv("CHROMA_DB_DIR", "./chroma_db")
    data_dir: Path = BASE_DIR / os.getenv("DATA_DIR", "./data")

    #  RAG 설정 (.env 값은 문자열이라 int 로 변환)
    chunk_size: int = int(os.getenv("CHUNK_SIZE", "1000"))
    chunk_overlap: int = int(os.getenv("CHUNK_OVERLAP", "200"))
    retriever_top_k: int = int(os.getenv("RETRIEVER_TOP_K", "4"))

    def check(self) -> None:
        """설정이 제대로 들어왔는지 빠르게 점검."""
        if not self.anthropic_api_key:
            print("  ANTHROPIC_API_KEY 가 비어 있습니다. .env 파일을 확인하세요.")
        else:
            # 키 전체를 출력하면 위험하니 앞 8글자만
            print(f" Claude 키 로딩됨: {self.anthropic_api_key[:8]}...")
        print(f"   모델       : {self.claude_model}")
        print(f"   임베딩     : {self.embedding_model}")
        print(f"   청크/오버랩 : {self.chunk_size} / {self.chunk_overlap}")
        print(f"   데이터 폴더 : {self.data_dir}")
        print(f"   ChromaDB   : {self.chroma_db_dir}")


#  3. 다른 파일에서 import 해서 쓸 단 하나의 설정 객체
settings = Settings()  # 설정 객체 1개 생성 -> 이걸 import해서 다른 곳에서 사용

#  4. 이 파일을 직접 실행하면 설정 점검을 보여줌
if __name__ == "__main__": # 이 파일을 직접 설정했을 때만
    settings.check() #점검 출력 만들어놓은 클래스에 메서드 실행