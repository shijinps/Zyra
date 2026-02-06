FROM python:3.11-slim

# Install system tools
RUN apt-get update && apt-get install -y \
    iputils-ping \
    nmap \
    dnsutils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy logic
COPY main.py .
COPY quantum.py .

# Expose port
EXPOSE 8000

# Run
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
