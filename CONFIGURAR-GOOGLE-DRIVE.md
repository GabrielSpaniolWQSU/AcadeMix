# Configurar o Google Drive no Spring Boot

O backend está preparado para ler as configurações abaixo do ambiente:

| Variável | Valor |
| --- | --- |
| `GOOGLE_DRIVE_CLIENT_ID` | ID do cliente OAuth do tipo Aplicativo da Web |
| `GOOGLE_DRIVE_CLIENT_SECRET` | Segredo desse mesmo cliente |
| `GOOGLE_DRIVE_REDIRECT_URI` | `http://localhost:8080/api/google-drive/callback` no desenvolvimento |

## Iniciar com as credenciais

1. Na pasta do projeto, abra **configurar-google-drive.bat**.
2. Informe o **ID do cliente** e o **segredo do mesmo cliente OAuth** quando o terminal pedir. O segredo fica oculto durante a digitação.
3. Feche as janelas do projeto se ele estiver rodando e abra **iniciar-projeto.bat**. Nas próximas execuções, basta abrir o iniciador.

O segredo fica criptografado para seu usuário do Windows no arquivo local `.google-drive-credenciais.xml`, que o Git ignora. Para trocar as credenciais, execute **configurar-google-drive.bat** de novo. Se um ID e um segredo já estiverem definidos no ambiente do iniciador, o backend usa esse par.

## Endereço no Google Cloud

No cliente OAuth, em **URIs de redirecionamento autorizados**, cadastre exatamente:

```text
http://localhost:8080/api/google-drive/callback
```

## Testar a conexão

1. No Google Cloud, confira se a **Google Drive API** está ativada. Se o aplicativo estiver como **Externo** em modo de teste, adicione o Gmail que vai conectar em **Público-alvo → Usuários de teste**.
2. Abra **iniciar-projeto.bat** e acesse [http://localhost:4200](http://localhost:4200). Use o endereço com `localhost` para que o navegador mantenha a sessão durante o retorno do Google.
3. Entre no sistema, abra **Google Drive** no menu e clique em **Conectar Google Drive**.
4. Selecione a conta e autorize o acesso. Ao voltar, a tela deve mostrar **Conta Google conectada**.
5. Use **Desconectar** para apagar a conexão da sessão. Para revogar a autorização no Google, acesse as [conexões da Conta Google](https://myaccount.google.com/connections).

A autorização é lembrada por conta administrativa. Após atualizar, conecte o Google Drive uma vez para salvar a autorização. O refresh token fica criptografado pelo Windows em data/google-drive e é usado para restaurar a conexão após reiniciar. Use o mesmo usuário do Windows ao executar o servidor. Desconectar apaga a autorização salva no sistema. O Google ainda pode revogar ou expirar a autorização, exigindo nova conexão.

## Pasta fixa dos certificados

A origem dos certificados fica definida no servidor pela propriedade `google.drive.certificados-pasta-id`, em `backend/src/main/resources/application.yml`. Na instalação de outra instituição, configure o ID da pasta pai que contém as pastas dos alunos.

1. Como administrador, conecte a conta Google que tem acesso à pasta configurada.
2. Abra **Certificados**. A pasta principal é vinculada automaticamente e não pode ser desvinculada pela tela.
3. As pastas filhas identificam os alunos, e os PDFs dentro delas aparecem como certificados.
4. Use **Atualizar do Drive** para consultar novamente os arquivos. Os gerenciadores de certificados utilizam a conexão salva do administrador.
5. As aprovações, reprovações, horas e datas de aprovação ficam no banco de dados, compartilhadas entre as contas autorizadas. Elas não dependem do armazenamento do navegador.

O vínculo permanece após reiniciar o projeto. A autorização Google pode ser revogada ou expirar; nesse caso, o administrador precisa reconectar a conta. Uma aprovação antiga sem data registrada só aparece no histórico completo do relatório; os filtros por período consideram a data de aprovação.

## Pastas automáticas por usuário

Na tela Google Drive, use **Autorizar criação de pastas** e escolha a mesma conta Google já vinculada ao projeto. Essa autorização acrescenta a permissão drive.file à leitura dos metadados já usada pelos certificados. Se necessário, adicione esse escopo em Google Auth Platform → Acesso a dados, no projeto existente.

Depois da autorização, o sistema prepara uma pasta **Gestão Acadêmica**. Cada usuário recebe uma pasta com seu nome e duas subpastas, **Documentos** e **Atas e Atividades**. Usuários com nomes iguais têm vínculos distintos por ID. Cadastros existentes também são preparados.

O processamento verifica novos usuários a cada 30 segundos. A tela Google Drive mostra o resultado e permite solicitar uma nova tentativa. Os identificadores ficam salvos antes da criação remota, para reutilizar a mesma pasta após uma falha ou reinício.

A estrutura é criada na conta do administrador que autorizou ou solicitou a preparação. As pastas e os arquivos de Meus documentos são espelhados no Drive automaticamente. O servidor mantém a cópia local para download e novas tentativas. Excluir um item em Meus documentos envia a cópia correspondente para a lixeira do Drive na próxima sincronização.
## Envio dos arquivos de Documentos e Atas

Os conteúdos são verificados a cada 30 segundos enquanto o backend está rodando. A tela mostra quando o envio está pendente, concluído ou precisa de autorização do administrador. Falhas temporárias são tentadas novamente com o mesmo identificador remoto.

Os anexos antigos das atas ficam no navegador onde foram cadastrados. Para transferi-los, entre na conta correspondente nesse navegador e abra Atas e Atividades. A aba envia esses anexos ao servidor, que os sincroniza em Gestão Acadêmica / nome do usuário / Atas e Atividades. Novos anexos são enviados ao servidor ao salvar.

A sincronização mantém a estrutura das pastas em Documentos. Alterações feitas diretamente no Drive não são importadas para o sistema por esta função.