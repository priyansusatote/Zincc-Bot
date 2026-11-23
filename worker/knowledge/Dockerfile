FROM python@sha256:935c3c5b12357a7e10caa9c1e6ca5692344d97cf229832ef584be2cec9fc03df
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 OMP_NUM_THREADS=1
WORKDIR /app
COPY requirements.lock .
RUN sed -i 's|http://deb.debian.org|https://deb.debian.org|g' /etc/apt/sources.list.d/debian.sources && \
    apt-get update && apt-get install -y --no-install-recommends tesseract-ocr=5.3.0-2 tesseract-ocr-eng=1:4.1.0-2 && \
    rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir --require-hashes --only-binary=:all: -r requirements.lock
ARG MODEL_REV=1110a243fdf4706b3f48f1d95db1a4f5529b4d41
RUN mkdir /model && python -c "import urllib.request as u; base='https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/${MODEL_REV}/'; u.urlretrieve(base+'tokenizer.json','/model/tokenizer.json'); u.urlretrieve(base+'onnx/model_quint8_avx2.onnx','/model/model.onnx')"
COPY worker.py .
USER 65532:65532
ENTRYPOINT ["/usr/bin/timeout","--kill-after=2s","65s","python","/app/worker.py"]
