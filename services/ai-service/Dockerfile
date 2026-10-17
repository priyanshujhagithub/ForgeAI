FROM python:3.13-slim
WORKDIR /service

# Dependencies first: this layer is reused until requirements.txt changes.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app app

RUN useradd --system --no-create-home forgeai
USER forgeai

# A host such as Render chooses the port through PORT; 8001 otherwise.
EXPOSE 8001
CMD ["sh", "-c", "exec python -m uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8001}"]
