# Banco de dados do projeto

## Painel no navegador

Abra **abrir-phpmyadmin.bat** na pasta do projeto. O atalho inicia o MySQL e o painel local, carrega as credenciais salvas e abre `http://127.0.0.1:8090/`.

Clique no banco **gestao_academica** e escolha uma tabela. **Procurar** mostra os registros, **Estrutura** mostra as colunas e **SQL** permite executar consultas. A instalação do painel usa o usuário da aplicação e fica acessível somente neste computador.

Se alterar diretamente no painel usuários, avisos ou certificados, reinicie o backend para recarregar os dados mantidos em memória. Para mudanças feitas pela aplicação, esse reinício não é necessário.

O projeto usa MySQL 8.4 para guardar usuários, avisos, registros de atas, metadados de documentos, anexos e certificados com suas avaliações.

## Iniciar

Abra **iniciar-projeto.bat**. O iniciador carrega a senha protegida pelo Windows, inicia o MySQL se necessário e ativa o perfil `mysql` no Spring Boot.

Nesta instalação o banco já foi preparado e validado. **configurar-banco.bat** permite concluir uma configuração incompleta. A distribuição precisa estar em `.tools/mysql-8.4.13-winx64`.

| Configuração | Valor |
| --- | --- |
| Servidor | `127.0.0.1` |
| Porta | `3307` |
| Banco | `gestao_academica` |
| Usuário da aplicação | `gestao_app` |
| Credenciais | `.mysql-credenciais.xml`, protegidas para o usuário do Windows |
| Dados físicos | `.tools/mysql-dados` |

Preserve a pasta dos dados físicos e as credenciais. O MySQL fica acessível somente no próprio computador. A interface continua em `http://localhost:4200`.

## Tabelas

- `usuarios` guarda nome, e-mail, senha protegida, perfil e situação.
- `avisos` guarda os comunicados e seus cursos.
- `documentos` guarda pastas, arquivos, dono e pasta pai. Chaves estrangeiras impedem vincular uma subpasta ao documento de outra conta.
- `atas_atividades` guarda os registros individuais.
- `anexos_atas` guarda os metadados dos anexos.
- `pastas_certificados` e `certificados` guardam a estrutura importada do Drive.
- `avaliacoes_certificados` guarda aprovações, horas e observações.
- `migracoes_dados` controla as importações já concluídas.

O esquema está em `backend/src/main/resources/db/schema.sql`. Ele cria tabelas ausentes e preserva as existentes.

## Migração

Usuários, avisos e certificados em JSON são importados na primeira inicialização. Metadados de documentos e anexos são importados quando a conta for consultada ou sincronizada. Os arquivos de origem são preservados.

Para importar atas que ainda estão no navegador, entre na conta correspondente no navegador usado anteriormente e abra **Atas e Atividades**. Os registros são enviados ao servidor uma vez. Os anexos locais são recuperados pela sincronização existente.

PDFs, documentos Word e imagens continuam no Drive e no armazenamento local usado pela sincronização. O MySQL guarda registros e metadados. As credenciais Google e os controles operacionais da sincronização permanecem nos arquivos específicos dessa integração.

## Verificação

Foram executados testes de persistência, permissões, vínculos por usuário e transações, além de um teste no MySQL real usando login, dados migrados, aviso, pasta, upload, download e exclusão dos itens temporários.

O perfil `mysql` aceita `DB_URL`, `DB_USER` e `DB_PASSWORD`. O iniciador deste computador usa a instalação local quando `.mysql-credenciais.xml` existe. O perfil `local` mantém a execução anterior baseada em arquivos para recuperação e testes.
