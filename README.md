<p align="center">
  <img src="./docs/assets/logo.png" alt="Lola Cloud Logo" width="150" />
</p>

<h1 align="center">Lola Cloud • Store</h1>

<p align="center">
  <strong>Plano de Controle de Armazenamento de Objetos S3 & Presigned URLs (Equivalente ao AWS S3)</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 25" />
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 4.1.1" />
  <img src="https://img.shields.io/badge/MinIO_S3-Engine-C72C48?style=flat-square&logo=minio&logoColor=white" alt="MinIO" />
  <img src="https://img.shields.io/badge/AWS_SDK-S3_v2-FF9900?style=flat-square&logo=amazons3&logoColor=white" alt="AWS SDK S3 v2" />
  <img src="https://img.shields.io/badge/PostgreSQL-17-4169E1?style=flat-square&logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Port-8086-blue?style=flat-square" alt="Port 8086" />
</p>

---

## 📌 Visão Geral

O **Lola Store** é o serviço de **Object Storage** da plataforma **Lola Cloud**. Ele implementa o plano de controle de armazenamento sobre o engine **MinIO**, compatível com o padrão de mercado **Amazon S3**.

O Store é projetado com arquitetura de alta performance com **desvio de tráfego massivo (Offloading via Presigned URLs)**: uploads e downloads trafegam diretamente entre o cliente (navegador/CLI) e o MinIO, sem onerar a memória ou o I/O da JVM.

---

## 🪣 Principais Responsabilidades

1. **Gestão de Buckets S3**: Criação, listagem e configuração de políticas de acesso (público/privado) e versionamento em conformidade com as regras RFC 1123 da AWS.
2. **Proteção de Buckets do Sistema**: Flag de integridade `is_protected = true` que impede a exclusão de buckets críticos da plataforma, como `lola-relate-backups` e `lola-journal-archives`.
3. **Presigned URLs (Upload & Download)**:
   - Geração de URLs temporárias pré-assinadas (`PUT` e `GET`) via `S3Presigner` do AWS SDK v2.
   - Elimina gargalos de tráfego na aplicação Spring Boot.
4. **Gerenciamento de Objetos & Pastas Virtuais**: Listagem de prefixos, exclusão e cálculo de volumetria de armazenamento.

---

## 📡 Principais Endpoints da API REST

| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `GET` | `/api/v1/store/buckets` | Lista buckets cadastrados |
| `POST` | `/api/v1/store/buckets` | Cria novo bucket S3 |
| `GET` | `/api/v1/store/buckets/{name}` | Detalhes, estatísticas e políticas do bucket |
| `DELETE` | `/api/v1/store/buckets/{name}` | Remove bucket (bloqueado para protegidos) |
| `GET` | `/api/v1/store/buckets/{name}/objects` | Lista objetos e diretórios do bucket |
| `POST` | `/api/v1/store/buckets/{name}/presign-upload` | Emite URL pré-assinada para upload direto via `PUT` |
| `GET` | `/api/v1/store/buckets/{name}/presign-download` | Emite URL pré-assinada para download direto via `GET` |
| `DELETE` | `/api/v1/store/buckets/{name}/objects` | Remove objeto do bucket |

---

## 🛠️ Stack Tecnológica

- **Linguagem**: Java 25 (OpenJDK)
- **Framework**: Spring Boot 4.1.1
- **S3 SDK**: AWS SDK for Java v2 (`software.amazon.awssdk:s3:2.29.38`) com `S3Client` e `S3Presigner`
- **Engine S3**: MinIO Server (`http://127.0.0.1:9000`, Console `:9001`)
- **Segurança**: Filtro de introspecção LCI (`LciAuthFilter`)
- **Banco de Dados**: PostgreSQL 17 (`jdbc:postgresql://localhost:5433/lola_store`)

---

## 🚀 Como Executar

```bash
# Executar a aplicação
./mvnw spring-boot:run
```
Porta padrão: **`8086`**
