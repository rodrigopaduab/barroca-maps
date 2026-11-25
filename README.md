# Ceva

App Android para marcar pontos de pesca no mapa, com fotos, informações e os peixes pegos em cada ponto. Tudo fica salvo no celular e funciona sem internet. O botão **Ir até lá** abre o Google Maps já com a rota.

## Versão 0.1
- Mapa em satélite híbrido, com botão para trocar para o mapa normal
- Marcar ponto: **segurar o dedo no mapa** ou **"Marcar onde estou"** (GPS)
- Ponto: nome, informações, localização, fotos (câmera ou galeria)
- Tocar num ponto abre um cartão com fotos, total de peixes e os botões **Ir até lá** e **Detalhes**
- Peixes: espécie (com atalhos), peso, tamanho, data e hora, isca, observações, se foi solto, fotos
- Fotos reduzidas para 1920 px, sem os dados de GPS da imagem
- Cor do pino escolhida por ponto (8 cores)
- QR code do ponto: mostrar na tela, enviar como imagem e ler pelo botão de QR no mapa. Quem não tem o app lê com a câmera e abre o Google Maps no local
- Backup em arquivo .zip (pontos, peixes e fotos) e restauração, salvando onde o usuário escolher
- Configurações: cores (Padrão, Alto contraste, Noite com luz vermelha), letras maiores, atalho para baixar mapa off-line no Google Maps, procurar atualização no GitHub

## Chave do Google Maps (obrigatória para o mapa aparecer)
1. Acesse https://console.cloud.google.com e crie um projeto.
2. Ative a API **Maps SDK for Android**.
3. Em **Credenciais**, crie uma **Chave de API**.
4. Restrinja a chave a **Apps Android** com:
   - Pacote: `app.ceva.pesca`
   - SHA-1: `0D:78:9C:FD:9B:6E:5B:3E:E6:81:3C:B7:4A:A8:DB:D0:92:A8:30:3D`

   O projeto já traz a chave de assinatura (`app/debug.keystore`), então esse SHA-1 vale tanto no PC quanto no GitHub.

## Compilar no PC (Android Studio)
1. Abra a pasta do projeto no Android Studio.
2. No arquivo `local.properties` (na raiz do projeto), adicione:
   ```
   MAPS_API_KEY=sua_chave_aqui
   ```
3. Conecte o celular com a depuração USB ligada e clique em **Run**.

## Compilar pelo GitHub (sem PC)
1. Suba o projeto para um repositório no GitHub.
2. Em **Settings → Secrets and variables → Actions**, crie o segredo `MAPS_API_KEY` com a sua chave.
3. A cada envio para a branch `main`, a aba **Actions** gera o APK. Baixe-o em **Artifacts** e instale no celular.

## Atualizações pelo GitHub
Em `app/src/main/java/com/pesca/pontos/Updates.kt`, troque `SEU_USUARIO/ceva` pelo endereço do repositório. O botão "Procurar atualização" compara a versão instalada com a última versão publicada em **Releases** (a tag deve ser o número da versão, ex.: `v0.2.0`).
