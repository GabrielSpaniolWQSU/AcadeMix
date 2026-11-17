# AcadeMix

Sistema de gestão acadêmica desenvolvido com Java 21, Spring Boot, Angular e MySQL.

## Funcionalidades

- Contas com perfis de administrador, coordenador, professor e gerenciador de certificados.
- Documentos e pastas individuais por usuário, com sincronização com o Google Drive.
- Registros de atas e atividades, anexos e criação de documentos por etapas.
- Modelos de ata de nomeação, declaração de comparecimento e ata por assunto.
- Organização e revisão de textos com IA, mediante configuração da API.
- Avisos por curso, com data de expiração e consulta ao histórico pelos editores.
- Consulta de certificados no Drive, aprovação e reprovação de horas.
- Filtros por aluno, certificado, situação e data de aprovação.
- Relatórios Excel com certificados aprovados e filtro por período.

## Requisitos

- JDK 21.
- Node.js 20 e npm compatíveis com Angular 18.
- Maven 3.9.
- MySQL 8.4 para execução com banco de dados.

Este repositório contém o código e o esquema das tabelas. As dependências, os dados de usuários, os documentos enviados, as credenciais e as instalações locais de MySQL e phpMyAdmin não são incluídos.

## Executar em desenvolvimento

### Banco de dados

Crie um banco MySQL chamado `gestao_academica` e um usuário próprio para a aplicação, com permissões para criar e atualizar as tabelas desse banco. O esquema está em `backend/src/main/resources/db/schema.sql` e é aplicado pelo perfil `mysql`.

Em um PowerShell, configure as variáveis abaixo com os dados da sua instalação:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'mysql'
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/gestao_academica?connectionTimeZone=America/Sao_Paulo'
$env:DB_USER = 'SEU_USUARIO_MYSQL'
$env:DB_PASSWORD = 'SUA_SENHA_MYSQL'
```

### Backend

No mesmo terminal, execute:

```powershell
cd backend
mvn spring-boot:run
```

A API fica disponível em `http://localhost:8080/api`.

Sem o perfil `mysql`, o perfil `local` usa arquivos na pasta `backend/data`. Ele permite testes de desenvolvimento, mas não substitui a configuração do MySQL para a execução completa do trabalho.

### Frontend

Em outro terminal:

```powershell
cd frontend
npm ci
npm start
```

Abra `http://localhost:4200` no navegador.

Em uma instalação nova, a conta inicial de desenvolvimento é `admin@faculdade.edu.br`, com senha `Admin@123`. Altere essa senha antes de disponibilizar o sistema para outras pessoas.

## Integrações

- [Configuração do Google Drive](CONFIGURAR-GOOGLE-DRIVE.md).
- [Configuração da IA](CONFIGURAR-MUSE-SPARK.md).
- [Banco MySQL e atalhos locais para Windows](CONFIGURAR-MYSQL.md).

As funcionalidades que usam Google Drive e IA exigem suas próprias credenciais. Nunca coloque chaves, tokens ou senhas em commits.

O vínculo fixo da pasta de certificados está na propriedade `google.drive.certificados-pasta-id` de `backend/src/main/resources/application.yml`. Ajuste essa propriedade para a pasta da sua instituição e conecte uma conta Google com acesso a ela.

Os atalhos `.bat` e `.ps1` atendem à instalação local no Windows. Algumas integrações usam a proteção de credenciais do Windows; migrar para outra plataforma exige adaptar esse armazenamento. Os programas portáteis de `.tools` precisam ser preparados separadamente, conforme os guias, pois não são distribuídos pelo Git.

## Verificação

```powershell
cd backend
mvn test
```

```powershell
cd frontend
npm run build
```

## Publicação

O projeto está configurado para desenvolvimento local. Antes de uso público, prepare HTTPS, endereços públicos da API e do OAuth Google, credenciais de produção, proteção de sessões e login, backups e testes no ambiente de hospedagem.

Enviar o código para o GitHub não coloca a aplicação em funcionamento na internet.
