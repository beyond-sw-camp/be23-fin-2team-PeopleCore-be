# loader,py -> data/ 폴더의 문서를 읽어 랭체인의 문서 리스트로 만드는 역할
# 지원 형식으로는 .txt, .md, .pdf
# RAG 파이프라인의 1단계 문서 로딩

from pathlib import Path
from sys import path

from langchain_core.documents import Document  # 랭체인 문서 표준 형식
from langchain_community.document_loaders import (
    TextLoader,  # /txt, .md 읽기
    PyPDFLoader  # .pdf 읽기 (pypdf 사용)
)
from sqlalchemy.dialects.mysql.mariadb import loader
from typer.cli import docs

from app.config import settings  # 직접 만든 설정


def load_documents(data_dir: Path | None = None) -> list[Document]:
    # data_dir 아래의 모든 파일을 읽어 Document 리스트로 반환
    # 인자가 없을 시 .env에 설정한 기본 data 폴더 사용
    data_dir = data_dir or settings.data_dir

    documents: list[Document] = []

    # rglob("*") : 하위 폴더까지 재귀적으로 읽기
    for path in data_dir.rglob("*"):
        if not path.is_file():
            continue

        suffix = path.suffix.lower()  # 읽는 파일 확장자

        try:
            if suffix in (".txt", ".md"):
                # 인코딩 utf-8 : 한글 깨짐 방지
                loader = TextLoader(str(path), encoding="utf-8")

            elif suffix == ".pdf":
                loader = PyPDFLoader(str(path))

            else:
                print(f" 건너띔(지원되지 않는 형식입니다.) : {path.name} ")
                continue

            docs = loader.load()  # 실제로 파일 읽는 부분
            documents.extend(docs)  # 결과를 모음에 추가
            print(f" 로딩 : {path.name} -> {len(docs)}개 파일")

        except Exception as e:
        # 한 파일이 잘못되어도 전체가 멈추지 않게 오류만 출력하고 계속하게
            print(f" 오류 : {path.name} - {e}")

    print(f" \n총 {len(documents)}개 Document 로딩 완료 ")
    return documents

    # 이 파일을 직접 실행하면 data/ 폴더를 읽어 결과를 보여줌
if __name__ == "__main__":
    load_documents()