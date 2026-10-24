FROM python:3.11-bookworm

# Set PKG_CONFIG_PATH for ARM64 to help PyAV find ffmpeg libraries
ENV PKG_CONFIG_PATH=/usr/lib/aarch64-linux-gnu/pkgconfig:/usr/lib/pkgconfig:/usr/share/pkgconfig

# Install system tools
RUN apt-get update && apt-get install -y \
    build-essential \
    python3-dev \
    pkg-config \
    libavformat-dev \
    libavcodec-dev \
    libavdevice-dev \
    libavutil-dev \
    libswscale-dev \
    libswresample-dev \
    libavfilter-dev \
    iputils-ping \
    nmap \
    dnsutils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip setuptools wheel && \
    pip install --no-cache-dir -r requirements.txt

# Copy logic
COPY *.py .
COPY *.json* .
COPY start.sh .
COPY my_contacts.vcf* .
RUN chmod +x start.sh

# Expose port
EXPOSE 8000

# Run both services
CMD ["./start.sh"]
