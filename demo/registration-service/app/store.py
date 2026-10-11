from dataclasses import dataclass


@dataclass
class User:
    id: int
    email: str
    password: str


class UserStore:
    def __init__(self):
        self._users: list[User] = []

    def add(self, email: str, password: str) -> User:
        user = User(id=len(self._users) + 1, email=email, password=password)
        self._users.append(user)
        return user

    def all(self) -> list[User]:
        return list(self._users)

    def clear(self) -> None:
        self._users.clear()
