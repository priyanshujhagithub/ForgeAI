# Registration Service

A deliberately small FastAPI service used as the target repository for ForgeAI demos
and evaluations. It registers users and keeps them in memory.

## Run

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload
```

## Test

```bash
pytest -q
```

## Endpoints

| Method | Path        | Description                  |
|--------|-------------|------------------------------|
| POST   | `/register` | Register a user              |
| GET    | `/users`    | List registered users' emails |

## Known gaps

The service accepts any string as an email and any password. These gaps are
intentional: they are the tasks ForgeAI is asked to fix.
