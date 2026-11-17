# Configurar a Muse Spark

O backend lê a chave somente pela variável de ambiente `META_API_KEY`. A chave não deve ser salva no código nem enviada ao frontend.

No PowerShell, antes de iniciar o projeto:

```powershell
$env:META_API_KEY="SUA_CHAVE"
$env:META_API_BASE_URL="https://api.meta.ai/v1"
$env:META_MODEL="muse-spark-1.3"
.\iniciar-projeto.bat
```

Use em `META_API_BASE_URL` e `META_MODEL` os valores mostrados na aba **cURL** da documentação da sua conta Meta, caso sejam diferentes.

Depois, abra **Atas e Atividades** e clique em **Analisar com IA**.
