FROM python@sha256:935c3c5b12357a7e10caa9c1e6ca5692344d97cf229832ef584be2cec9fc03df
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 HOME=/tmp DO_NOT_TRACK=1
WORKDIR /app
COPY requirements.lock .
RUN pip install --no-cache-dir --require-hashes --only-binary=:all: -r requirements.lock
COPY adapter.py .
USER 65532:65532
ENTRYPOINT ["/usr/bin/timeout","--kill-after=2s","100s","python","-I","/app/adapter.py"]
