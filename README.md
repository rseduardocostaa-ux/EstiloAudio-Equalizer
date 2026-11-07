# Estilo Áudio Equalizer

App Android (Kotlin, minSdk 28) com equalizador de 10 bandas, compressor e limiter,
usando DynamicsProcessing e BassBoost na sessão de áudio do Spotify.

Visual claro estilo Material (fundo verde-claro, cartões tonais, verde-limão #B9FC00 como cor de destaque),
com a bandeira do Brasil e o nome "Estilo Áudio" no cabeçalho. Tocando na bandeira dá para trocar por
qualquer emoji ou pela logo do dragão (na cor do tema).

## Como compilar pelo celular (GitHub Actions)
1. Crie um repositório no GitHub e envie todos os arquivos deste projeto (mantendo as pastas, inclusive `.github/workflows`).
2. Abra a aba **Actions**, escolha **Build APK** e toque em **Run workflow** (ou faça um push na branch `main`).
3. Quando terminar, abra a execução e baixe o artefato **EstiloAudioEqualizer-debug** (um ZIP com o `.apk`).
4. Extraia o APK, instale e permita "instalar apps desconhecidos".
