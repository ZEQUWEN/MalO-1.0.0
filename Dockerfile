FROM python:3.11-alpine

WORKDIR /app

RUN apk add --no-cache bash curl

COPY public/ /app/public/
COPY .build-outputs/ /app/.build-outputs/
COPY start.sh /app/start.sh
COPY build.sh /app/build.sh

RUN chmod +x /app/start.sh /app/build.sh && sh /app/build.sh

EXPOSE 8080

ENV PORT=8080

CMD ["sh", "/app/start.sh"]
