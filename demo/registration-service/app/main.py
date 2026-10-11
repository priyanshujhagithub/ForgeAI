from fastapi import FastAPI

from .models import RegisterRequest, UserResponse
from .store import UserStore

app = FastAPI(title="Registration Service")
store = UserStore()


@app.post("/register", status_code=201, response_model=UserResponse)
def register(request: RegisterRequest):
    user = store.add(request.email, request.password)
    return UserResponse(id=user.id, email=user.email)


@app.get("/users")
def list_users():
    return [user.email for user in store.all()]
