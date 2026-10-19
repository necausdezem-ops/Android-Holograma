# STARK HoloOS — Android Holograma

Launcher Android experimental inspirado no protótipo `Holo.html`: interface sci-fi neon, reator animado, relógio, temas, atalhos e abertura de apps instalados.

## Requisitos
- Android Studio (versão recente)
- JDK 17
- Android SDK 35

## Compilar
Abra este repositório no Android Studio, aguarde a sincronização do Gradle e execute o app em um dispositivo Android.

Para gerar APK: **Build → Build APK(s)**.

## Definir como launcher
1. Instale e abra o aplicativo.
2. Pressione Home.
3. Escolha **HoloOS** como aplicativo de tela inicial e selecione **Sempre**, se desejar.

O Android/HiOS pode mostrar a seleção de launcher nas configurações de Aplicativos padrão. A disponibilidade de recursos varia conforme a versão do Android e as políticas do fabricante.

## Estado do projeto
Primeira base nativa: atividade HOME, interface holográfica em Jetpack Compose, relógio/data, reator animado, grade de apps instalados, temas neon e painel de configurações. Recursos que exigem permissões especiais (leitura de notificações, câmera AR, voz contínua e controle de hardware) precisam ser habilitados explicitamente e serão implementados separadamente.

**Nota:** os efeitos são visuais; o aplicativo não projeta hologramas físicos.