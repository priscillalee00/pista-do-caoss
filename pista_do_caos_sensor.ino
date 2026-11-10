// Pista do Caos - sketch do Arduino (encoder rotativo KY-040)
// Liga: CLK -> pino 2, DT -> pino 3, + -> 5V, GND -> GND
// Envia pela serial (9600 baud) uma linha: "Sentido: 1", "Sentido: -1" ou "Sentido: 0"

const int PIN_CLK = 2;
const int PIN_DT  = 3;

int ultimoClk;
int direcao = 0;
unsigned long ultimoMovimento = 0;
unsigned long ultimoEnvio = 0;

void setup() {
  Serial.begin(9600);
  pinMode(PIN_CLK, INPUT_PULLUP);
  pinMode(PIN_DT, INPUT_PULLUP);
  ultimoClk = digitalRead(PIN_CLK);
}

void loop() {
  int clk = digitalRead(PIN_CLK);
  if (clk != ultimoClk && clk == LOW) {
    // se o carro andar ao contrario, troque 1 e -1
    direcao = (digitalRead(PIN_DT) != clk) ? 1 : -1;
    ultimoMovimento = millis();
  }
  ultimoClk = clk;

  // parou de girar: manda 0 para o carro parar
  if (millis() - ultimoMovimento > 200) direcao = 0;

  // envia a cada 50 ms
  if (millis() - ultimoEnvio >= 50) {
    Serial.print("Sentido: ");
    Serial.println(direcao);
    ultimoEnvio = millis();
  }
}
