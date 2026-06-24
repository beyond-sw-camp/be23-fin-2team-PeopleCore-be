# 긴 문서를 청크로 자른다
# RAG 2단계 : 청킹, 1000자/200자 겹침으로 분할

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.config import settings

def split_documents(documents:list[Document]) -> list[Document]: # 청크로 나눠줄 도구를 준비하는 부분 -> 어떻게 자를지 세팅하는 파트
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=settings.chunk_size, # 한 청크 최대 글자 수(1000자)
        chunk_overlap=settings.chunk_overlap, #청크끼리 겹칠 글자 수
        separators=["\n\n","\n"," ",""],  #청크를 자르는 우선 순위
    )
    chunks = splitter.split_documents(documents) # 실제로 자르는 부분
    print(f"{len(documents)}개 문서 -> {len(chunks)}개 청크로 분할") # 들어온 문서 개수 와 자른 청크 개수
    return chunks # 잘린 결과를 함수 밖으로 돌려줌 -> 나중에 임베딩 단계가 이걸 받아 씀

if __name__ == "__main__": # 이 파일을 직접 실행했을 때만 도는 테스트 코드
    from app.core.loader import load_documents

    docs = load_documents()
    chunks = split_documents(docs)
    if chunks:
        print("\n 첫 청크 미리보기---")
        print(chunks[0].page_content[:200])