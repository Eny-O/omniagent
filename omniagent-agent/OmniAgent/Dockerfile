FROM python:3.12-slim
LABEL org.opencontainers.image.source=https://github.com/Eny-O/omniagent
LABEL org.opencontainers.image.description="OmniAgent FastAPI / LangGraph agent"
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
