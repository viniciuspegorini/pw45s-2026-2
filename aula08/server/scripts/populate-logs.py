"""
Gera logs sintéticos no mesmo formato JSON (ECS) gravado pelo Spring Boot (logs/app.json), para popular o
dashboard "API - Observabilidade (aula08)" no Kibana sem precisar executar centenas de requisições manualmente.

Os registros simulam o log de acesso do CrudLogFilter (requestId, crudOperation, httpMethod, endpoint, status,
durationMs) e os logs de WARN/ERROR da aplicação, distribuídos ao longo do período informado (padrão: última hora,
o mesmo intervalo exibido pelo dashboard).

Cada execução cria um novo arquivo em logs/ (ex.: logs/sample-20261027-213000.json), que é lido pelo Filebeat.
Um arquivo novo é usado porque o Filebeat controla a posição de leitura de cada arquivo: reescrever um arquivo já
lido não envia os registros novamente.

Uso (na pasta server):
    python scripts/populate-logs.py                # 1500 requisições na última hora
    python scripts/populate-logs.py -n 3000 -m 180 # 3000 requisições nas últimas 3 horas
"""
import argparse
import json
import random
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

LOGS_DIR = Path(__file__).resolve().parent.parent / "logs"
FILTER_LOGGER = "br.edu.utfpr.pb.pw45s.server.filter.CrudLogFilter"
PID = 4242

# (método, endpoint, peso, (tempo mínimo, tempo máximo) em ms)
# {id} é substituído por um id entre 1 e 10
REQUESTS = [
    ("GET", "/products", 30, (15, 120)),
    ("GET", "/products/{id}", 18, (8, 60)),
    ("GET", "/products/page", 8, (20, 150)),
    ("GET", "/categories", 22, (10, 80)),
    ("GET", "/categories/{id}", 8, (5, 40)),
    ("GET", "/categories/page", 4, (15, 100)),
    ("GET", "/categories/count", 2, (3, 20)),
    ("GET", "/auth/user-info", 10, (5, 30)),
    ("GET", "/products/download/{id}", 4, (150, 900)),
    ("POST", "/products", 5, (40, 250)),
    ("POST", "/products/upload", 3, (300, 2500)),
    ("POST", "/categories", 3, (25, 150)),
    ("PUT", "/products/{id}", 3, (30, 200)),
    ("PUT", "/categories/{id}", 2, (20, 120)),
    ("DELETE", "/products/{id}", 2, (20, 120)),
    ("DELETE", "/categories/{id}", 1, (15, 90)),
    ("POST", "/login", 8, (80, 350)),
    ("POST", "/auth-social", 2, (200, 900)),
    ("POST", "/users", 2, (90, 300)),
]


def crud_operation(method, endpoint):
    if endpoint == "/login" or endpoint.startswith("/auth-social"):
        return "LOGIN"
    return {"POST": "CREATE", "GET": "READ", "PUT": "UPDATE", "PATCH": "UPDATE", "DELETE": "DELETE"}.get(method, "OTHER")


def pick_status(method, endpoint):
    r = random.random()
    if endpoint in ("/login", "/auth-social"):
        return 401 if r < 0.25 else 200
    if r < 0.03:
        return 500
    if r < 0.06:
        return 401
    if r < 0.08:
        return 403
    if "{id}" in endpoint and r < 0.13:
        return 404
    if method in ("POST", "PUT") and r < 0.18:
        return 400
    if method == "POST" and endpoint != "/products/upload":
        return 201
    if method == "DELETE":
        return 204
    return 200


def ecs(timestamp, level, logger, message, thread, extra=None):
    entry = {
        "@timestamp": timestamp.strftime("%Y-%m-%dT%H:%M:%S.%f") + "Z",
        "log": {"level": level, "logger": logger},
        "process": {"pid": PID, "thread": {"name": thread}},
        "service": {"name": "server", "node": {}},
        "message": message,
    }
    if extra:
        entry.update(extra)
    entry["ecs"] = {"version": "8.11"}
    return entry


def app_logs(timestamp, method, endpoint, status, thread, mdc):
    """Logs da aplicação gravados durante a requisição (antes do log de acesso), com os mesmos campos do MDC."""
    t = timestamp - timedelta(milliseconds=1)
    if status == 500:
        if endpoint.startswith("/products/upload") or endpoint.startswith("/products/download"):
            msg = random.choice([
                "Connection refused: no further information (http://127.0.0.1:9000/commons)",
                "The specified key does not exist. (Service: S3, Status Code: 404)",
                "Read timed out while communicating with storage endpoint http://127.0.0.1:9000",
            ])
            logger = "br.edu.utfpr.pb.pw45s.server.service.impl.ProductServiceImpl"
        else:
            msg = random.choice([
                "could not execute statement [Referential integrity constraint violation: \"FK_PRODUCT_CATEGORY\"]",
                "Unable to acquire JDBC Connection [HikariPool-1 - Connection is not available, request timed out after 30000ms]",
                "Cannot invoke \"br.edu.utfpr.pb.pw45s.server.model.Category.getId()\" because the return value is null",
            ])
            logger = "org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver"
        return [ecs(t, "ERROR", logger, msg, thread, mdc)]
    if status == 401 and endpoint == "/auth-social":
        return [ecs(t, "ERROR", "br.edu.utfpr.pb.pw45s.server.security.social.GoogleTokenVerifier",
                    "Invalid ID token: token expired", thread, mdc)]
    if status == 401 and endpoint == "/login":
        return [ecs(t, "WARN", "br.edu.utfpr.pb.pw45s.server.security.JWTAuthenticationFilter",
                    "Falha de autenticação: usuário ou senha inválidos", thread, mdc)]
    if status == 401:
        return [ecs(t, "WARN", "br.edu.utfpr.pb.pw45s.server.security.JWTAuthorizationFilter",
                    "Token JWT expirado ou inválido", thread, mdc)]
    if status == 403:
        return [ecs(t, "WARN", "org.springframework.security.web.access.ExceptionTranslationFilter",
                    "Access Denied", thread, mdc)]
    if status == 400:
        return [ecs(t, "WARN", "org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver",
                    "Resolved [org.springframework.web.bind.MethodArgumentNotValidException: Validation failed]",
                    thread, mdc)]
    if status == 404:
        return [ecs(t, "WARN", "br.edu.utfpr.pb.pw45s.server.error.ErrorHandler",
                    f"Registro não encontrado: {endpoint}", thread, mdc)]
    return []


def access_log(timestamp, method, endpoint, status, duration, thread, request_id):
    mdc = {
        "crudOperation": crud_operation(method, endpoint),
        "endpoint": endpoint,
        "httpMethod": method,
        "requestId": request_id,
    }
    access = ecs(timestamp, "INFO", FILTER_LOGGER, f"{method} {endpoint} -> {status}", thread,
                 {**mdc, "durationMs": str(duration), "status": str(status)})
    return app_logs(timestamp, method, endpoint, status, thread, mdc) + [access]


def generate(total, minutes):
    now = datetime.now(timezone.utc)
    start = now - timedelta(minutes=minutes)
    weights = [r[2] for r in REQUESTS]
    entries = []

    # picos de tráfego e um período de instabilidade (mais erros 5xx e respostas lentas)
    peaks = [start + timedelta(minutes=minutes * p) for p in (0.25, 0.6, 0.85)]
    incident = start + timedelta(minutes=minutes * 0.7)

    for _ in range(total):
        if random.random() < 0.4:
            ts = random.choice(peaks) + timedelta(seconds=random.gauss(0, minutes * 60 * 0.03))
        else:
            ts = start + timedelta(seconds=random.uniform(0, minutes * 60))
        ts = min(max(ts, start), now)

        method, path, _, (min_ms, max_ms) = random.choices(REQUESTS, weights)[0]
        endpoint = path.replace("{id}", str(random.randint(1, 10)))
        status = pick_status(method, path)
        duration = int(random.triangular(min_ms, max_ms, min_ms + (max_ms - min_ms) * 0.3))

        in_incident = abs((ts - incident).total_seconds()) < minutes * 60 * 0.04
        if in_incident:
            duration *= random.randint(3, 8)
            if status < 400 and random.random() < 0.35:
                status = 500

        thread = f"http-nio-8080-exec-{random.randint(1, 10)}"

        # requisição de preflight (CORS) enviada pelo navegador antes da requisição do cliente React
        if random.random() < 0.3:
            pre_ts = ts - timedelta(milliseconds=random.randint(5, 40))
            pre = access_log(pre_ts, "OPTIONS", endpoint, 200, random.randint(1, 8), thread, str(uuid.uuid4()))
            entries.extend((pre_ts, e) for e in pre)

        entries.extend((ts, e) for e in access_log(ts, method, endpoint, status, duration, thread, str(uuid.uuid4())))

    entries.sort(key=lambda x: x[0])
    return [e for _, e in entries]


def main():
    parser = argparse.ArgumentParser(description="Gera logs sintéticos para o dashboard do Kibana.")
    parser.add_argument("-n", "--requests", type=int, default=1500, help="quantidade de requisições (padrão: 1500)")
    parser.add_argument("-m", "--minutes", type=int, default=60, help="período em minutos até agora (padrão: 60)")
    args = parser.parse_args()

    entries = generate(args.requests, args.minutes)
    LOGS_DIR.mkdir(exist_ok=True)
    output = LOGS_DIR / f"sample-{datetime.now():%Y%m%d-%H%M%S}.json"
    with output.open("w", encoding="utf-8", newline="\n") as f:
        for e in entries:
            f.write(json.dumps(e, ensure_ascii=False) + "\n")
    print(f"{len(entries)} linhas de log gravadas em {output}")


if __name__ == "__main__":
    main()
