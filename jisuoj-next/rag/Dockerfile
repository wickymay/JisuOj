FROM docker.m.daocloud.io/library/python:3.12-slim-bookworm
WORKDIR /app
ENV HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 TOKENIZERS_PARALLELISM=false PYTHONUNBUFFERED=1
RUN pip install --no-cache-dir torch==2.14.1+cpu --index-url https://download.pytorch.org/whl/cpu
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY service.py .
RUN useradd --system --uid 1537 --create-home rag
USER rag
EXPOSE 8765
CMD ["python", "service.py"]
