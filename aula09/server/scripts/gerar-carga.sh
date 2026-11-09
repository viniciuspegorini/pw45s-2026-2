#!/usr/bin/env bash
# Gera requisições variadas contra a API para popular os logs, métricas e traces no Grafana.
# Uso: ./scripts/gerar-carga.sh [rodadas] [url-base]
# Exemplo: ./scripts/gerar-carga.sh 20 http://localhost:8080

ROUNDS=${1:-10}
BASE_URL=${2:-http://localhost:8080}

call() {
  # Exibe apenas o status HTTP de cada chamada
  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" "$@")
  echo "  $status  ${*: -1}"
}

for ((i = 1; i <= ROUNDS; i++)); do
  echo "Rodada $i de $ROUNDS"

  # Sucesso (INFO): endpoints de demonstração e consultas
  call "$BASE_URL/"
  call "$BASE_URL/greet/aluno$i"
  call "$BASE_URL/categories"
  call "$BASE_URL/products"
  call "$BASE_URL/products/page?page=0&size=3"
  call "$BASE_URL/products/$((RANDOM % 8 + 1))"

  # Latência variável e falhas aleatórias (INFO, WARN e ERROR)
  call "$BASE_URL/unstable"
  call "$BASE_URL/unstable"

  # Login com sucesso e com falha (auth.login.success / auth.login.failure)
  call -X POST -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"P4ssword"}' "$BASE_URL/login"
  call -X POST -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"senha-errada"}' "$BASE_URL/login"
  call -X POST -H "Content-Type: application/json" \
    -d '{"username":"inexistente","password":"123"}' "$BASE_URL/login"

  # Acesso não autorizado e token inválido (auth.unauthorized / auth.token.invalid)
  call "$BASE_URL/users"
  call -H "Authorization: Bearer token-invalido" "$BASE_URL/users"

  # Recurso inexistente (WARN) e erro de validação (http.validation_error)
  call "$BASE_URL/products/9999"
  call -X POST -H "Content-Type: application/json" -d '{}' "$BASE_URL/categories"

  # A cada 5 rodadas, uma requisição lenta (aparece na tabela de traces > 300 ms)
  if ((i % 5 == 0)); then
    call "$BASE_URL/slow"
  fi
done
