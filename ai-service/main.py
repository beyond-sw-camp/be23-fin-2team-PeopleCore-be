from fastapi import FastAPI

app = FastAPI()

@app.get("/")
def hello():
    return {"message" : "AI서비스가 살아있습니다."}