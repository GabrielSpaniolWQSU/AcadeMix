CREATE TABLE IF NOT EXISTS usuarios (
 id BIGINT PRIMARY KEY,
 nome VARCHAR(255) NOT NULL,
 email VARCHAR(320) NOT NULL UNIQUE,
 senha VARCHAR(512) NOT NULL,
 perfil VARCHAR(40) NOT NULL,
 ativo BOOLEAN NOT NULL
);
CREATE TABLE IF NOT EXISTS avisos (
 id BIGINT PRIMARY KEY,
 titulo VARCHAR(500) NOT NULL,
 descricao LONGTEXT NOT NULL,
 data DATE NOT NULL,
 curso VARCHAR(120) NOT NULL
);
CREATE TABLE IF NOT EXISTS documentos (
 usuario_id BIGINT NOT NULL,
 id VARCHAR(36) NOT NULL,
 nome VARCHAR(255) NOT NULL,
 pai_id VARCHAR(36),
 pasta BOOLEAN NOT NULL,
 tamanho BIGINT NOT NULL,
 PRIMARY KEY (usuario_id,id),
 FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
 FOREIGN KEY (usuario_id,pai_id) REFERENCES documentos(usuario_id,id)
);
CREATE TABLE IF NOT EXISTS atas_atividades (
 usuario_id BIGINT NOT NULL,
 id BIGINT NOT NULL,
 tipo VARCHAR(20) NOT NULL,
 titulo VARCHAR(500) NOT NULL,
 descricao LONGTEXT NOT NULL,
 data DATE NOT NULL,
 arquivo_nome VARCHAR(255),
 PRIMARY KEY (usuario_id,id),
 FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);
CREATE TABLE IF NOT EXISTS anexos_atas (
 usuario_id BIGINT NOT NULL,
 id BIGINT NOT NULL,
 nome VARCHAR(255) NOT NULL,
 hash VARCHAR(64) NOT NULL,
 tamanho BIGINT NOT NULL,
 PRIMARY KEY (usuario_id,id),
 FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);
CREATE TABLE IF NOT EXISTS pastas_certificados (
 id VARCHAR(255) PRIMARY KEY,
 nome VARCHAR(500) NOT NULL,
 raiz_id VARCHAR(255) NOT NULL,
 pai_id VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS certificados (
 id VARCHAR(255) PRIMARY KEY,
 pasta_id VARCHAR(255) NOT NULL,
 nome VARCHAR(500) NOT NULL,
 enviado_em VARCHAR(80),
 FOREIGN KEY (pasta_id) REFERENCES pastas_certificados(id)
);
CREATE TABLE IF NOT EXISTS avaliacoes_certificados (
 certificado_id VARCHAR(255) PRIMARY KEY,
 status VARCHAR(20) NOT NULL,
 horas_aprovadas DOUBLE,
 observacao LONGTEXT,
 aprovado_em VARCHAR(40)
);
CREATE TABLE IF NOT EXISTS migracoes_dados (
 chave VARCHAR(255) PRIMARY KEY,
 concluida_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
