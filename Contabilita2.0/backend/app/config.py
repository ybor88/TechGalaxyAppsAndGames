from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_name: str = "BusinessFlow ERP"
    app_version: str = "1.0.0"
    debug: bool = True

    # Database — SQLite locale (zero infrastruttura)
    database_url: str = "sqlite+aiosqlite:///./businessflow.db"

    # Redis (opzionale, usato solo da Celery)
    redis_url: str = "redis://localhost:6379/0"

    # MinIO
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
    minio_bucket: str = "businessflow-docs"

    # Ollama
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "llama3"

    class Config:
        env_file = ".env"


settings = Settings()
