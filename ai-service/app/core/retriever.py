# 저장된 ChromaDB를 검색기(retriever)로 감싼다
# RAG 마지막 기반 : 질문 -> 관련 청크 top_K개 반환
from idlelib import search

from langchain_core.documents import Document
from oauthlib.uri_validate import query
from torch.fx.experimental.unification.multipledispatch.dispatcher import source

from app.config import settings
from app.core.vectorstore import get_vectorstore

def get_retriever():
    #이미 저장된 ChromaDB를 불러와 retriever로 변환
    vectorstore = get_vectorstore()
    return vectorstore.as_retriever( # 벡터 스토어를 검색기로 변환 -> 체인에 끼울 수 있는 표준 형태
        search_kwargs = {"k" : settings.retriever_top_k}, # 관련 청크 몇 개 가지고 올지 (기본은 4) 
    )

def search(query : str) -> list[Document]:
    #질문으로 관련 청크를 찾아 리스트로 반환
    retriever = get_retriever()
    return retriever.invoke(query) # invoke = 검색기 실행

#직접 실행 시 질문으로 검색 테스트
if __name__ == "__main__":
    query = "연차는 며칠 부여되나요?"
    results = search(query)

    print(f"--- '{query}' 검색 결과 {len(results)}개 ---")
    for i, doc in enumerate(results,1):
        source = doc.metadata.get("source", "?") # 출처 -> 파일 경로
        print(f"\n[{i}] (출처 : {source})")
        print(doc.page_content[:150])