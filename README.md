# Pista do Caos

Jogo sério de reabilitação feito com **Java + libGDX**, controlado por um dispositivo IoT (**Arduino**).
Um carro se move de um lado para o outro da pista desviando de obstáculos. O movimento do jogador,
lido pelo sensor, controla o carro.

## Como jogar
- **ENTER**: iniciar / avançar de tela
- **P**: pausar e continuar
- **R**: reiniciar
- **ESC**: voltar ao menu
- Controle: Arduino (`Sentido: 1` = direita, `Sentido: -1` = esquerda, `Sentido: 0` = parado).
  Para testar sem o Arduino, use as setas ou A/D (`TECLADO_DE_TESTE = true` em `PistaDoCaos.java`).

## Mecânicas
- 3 fases de 20 s, com obstáculos mais rápidos e frequentes a cada fase
- 3 vidas; ao bater há 1,5 s de invencibilidade
- Pontuação: 10 pontos por obstáculo desviado + bônus de consistência no fim de cada fase
- **Consistência** = porcentagem do tempo em que o jogador se movimentou no dispositivo.
  Ela é mostrada no HUD e no fim de cada fase, e serve de métrica de evolução do jogador.

## Como rodar
Requisitos: JDK 17 ou superior.

```
./gradlew lwjgl3:run        # Windows: gradlew.bat lwjgl3:run
./gradlew lwjgl3:jar        # gera o .jar em lwjgl3/build/libs/
```

## Ligando o Arduino
1. Monte o encoder KY-040: CLK no pino 2, DT no pino 3, + no 5V, GND no GND.
2. Abra `arduino/pista_do_caos_sensor/pista_do_caos_sensor.ino` na Arduino IDE e faça o upload.
3. Feche o Serial Monitor (ele ocupa a porta).
4. Em `core/src/main/java/br/mackenzie/PistaDoCaos.java`, ajuste `PORTA_SERIAL`
   (ex.: `COM3`, `/dev/ttyUSB0`) e coloque `TECLADO_DE_TESTE = false`.

## Estrutura
- `core/.../PistaDoCaos.java`: lógica e desenho do jogo
- `core/.../ArduinoReaderThread.java`: thread que lê a porta serial
- `lwjgl3/`: launcher desktop
- `arduino/`: sketch do Arduino
