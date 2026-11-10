package br.mackenzie;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

/**
 * Pista do Caos: um carro vai de um lado para o outro da pista desviando de obstáculos.
 * - Controle: Arduino ("Sentido: 1" = direita, "Sentido: -1" = esquerda).
 * - 3 fases com dificuldade progressiva, menu, pausa (P) e reinício (R).
 * - Pontuação: obstáculos desviados + bônus de consistência (% do tempo em movimento).
 */
public class PistaDoCaos extends ApplicationAdapter {

    private enum Estado { MENU, JOGANDO, PAUSADO, FIM_FASE, GAME_OVER, VITORIA }

    private static final float W = 800f, H = 480f;

    // Mude para a porta do seu Arduino (ex.: "COM3" no Windows, "/dev/ttyUSB0" no Linux)
    private static final String PORTA_SERIAL = "COM3";
    // Teclas ← → / A D para testar sem o Arduino. Coloque false para controle somente pelo IoT.
    private static final boolean TECLADO_DE_TESTE = true;

    // Configuração das fases (índice 0 = fase 1)
    private static final int TOTAL_FASES = 3;
    private static final float[] VEL_OBSTACULO = {160f, 240f, 330f};
    private static final float[] INTERVALO_SPAWN = {1.2f, 0.85f, 0.6f};
    private static final float DURACAO_FASE = 20f;
    private static final int VIDAS_INICIAIS = 3;

    private OrthographicCamera camera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;

    private ArduinoReaderThread arduinoReader;

    private Estado estado = Estado.MENU;

    private final Rectangle carro = new Rectangle();
    private final Array<Rectangle> obstaculos = new Array<>();

    private float velocidadeCarro = 320f;
    private int fase;
    private int vidas;
    private int pontos;
    private int desviados;
    private float tempoFase;
    private float tempoMovendo;
    private float proximoSpawn;
    private float invencivel;
    private float deslocamentoPista;
    private final float[] consistenciaPorFase = new float[TOTAL_FASES];

    @Override
    public void create() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, W, H);
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();

        carro.width = 40;
        carro.height = 64;

        arduinoReader = new ArduinoReaderThread(PORTA_SERIAL);
        arduinoReader.start();
    }

    // ------------------------------------------------------------------ lógica

    private void iniciarJogo() {
        fase = 0;
        vidas = VIDAS_INICIAIS;
        pontos = 0;
        java.util.Arrays.fill(consistenciaPorFase, 0f);
        iniciarFase();
        estado = Estado.JOGANDO;
    }

    private void iniciarFase() {
        obstaculos.clear();
        carro.x = W / 2f - carro.width / 2f;
        carro.y = 20;
        tempoFase = 0;
        tempoMovendo = 0;
        desviados = 0;
        proximoSpawn = 1f;
        invencivel = 0;
    }

    /** Lê o Arduino (e o teclado de teste). Retorna -1, 0 ou 1. */
    private int lerDirecao() {
        int dir = 0;
        String dado = arduinoReader.getLastData();
        if ("Sentido: 1".equals(dado)) dir = 1;
        else if ("Sentido: -1".equals(dado)) dir = -1;

        if (TECLADO_DE_TESTE) {
            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) dir = 1;
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) dir = -1;
        }
        return dir;
    }

    private void atualizar(float dt) {
        int dir = lerDirecao();
        carro.x += dir * velocidadeCarro * dt;
        carro.x = MathUtils.clamp(carro.x, 0, W - carro.width);

        if (dir != 0) tempoMovendo += dt;
        tempoFase += dt;
        if (invencivel > 0) invencivel -= dt;

        float vel = VEL_OBSTACULO[fase];
        deslocamentoPista = (deslocamentoPista + vel * dt) % 60f;

        // Cria obstáculos
        proximoSpawn -= dt;
        if (proximoSpawn <= 0) {
            float larg = MathUtils.random(30f, 60f);
            obstaculos.add(new Rectangle(MathUtils.random(0f, W - larg), H, larg, 30f));
            proximoSpawn = INTERVALO_SPAWN[fase] * MathUtils.random(0.8f, 1.2f);
        }

        // Move obstáculos e trata colisões
        for (int i = obstaculos.size - 1; i >= 0; i--) {
            Rectangle o = obstaculos.get(i);
            o.y -= vel * dt;

            if (invencivel <= 0 && o.overlaps(carro)) {
                obstaculos.removeIndex(i);
                vidas--;
                invencivel = 1.5f;
                if (vidas <= 0) {
                    estado = Estado.GAME_OVER;
                    return;
                }
            } else if (o.y + o.height < 0) {
                obstaculos.removeIndex(i);
                desviados++;
                pontos += 10;
            }
        }

        // Fim da fase
        if (tempoFase >= DURACAO_FASE) {
            float consistencia = tempoMovendo / tempoFase;
            consistenciaPorFase[fase] = consistencia;
            pontos += Math.round(consistencia * 100f); // bônus de consistência
            estado = (fase == TOTAL_FASES - 1) ? Estado.VITORIA : Estado.FIM_FASE;
        }
    }

    private void tratarTeclas() {
        boolean enter = Gdx.input.isKeyJustPressed(Input.Keys.ENTER);

        switch (estado) {
            case MENU:
                if (enter) iniciarJogo();
                break;
            case JOGANDO:
                if (Gdx.input.isKeyJustPressed(Input.Keys.P)) estado = Estado.PAUSADO;
                break;
            case PAUSADO:
                if (Gdx.input.isKeyJustPressed(Input.Keys.P) || enter) estado = Estado.JOGANDO;
                break;
            case FIM_FASE:
                if (enter) {
                    fase++;
                    iniciarFase();
                    estado = Estado.JOGANDO;
                }
                break;
            case GAME_OVER:
            case VITORIA:
                if (enter) estado = Estado.MENU;
                break;
        }

        if (estado != Estado.MENU && Gdx.input.isKeyJustPressed(Input.Keys.R)) iniciarJogo();
        if (estado != Estado.MENU && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) estado = Estado.MENU;
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);

        tratarTeclas();
        if (estado == Estado.JOGANDO) atualizar(dt);

        Gdx.gl.glClearColor(0.1f, 0.4f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        desenharPista();
        if (estado != Estado.MENU) {
            desenharObstaculos();
            desenharCarro();
        }
        desenharTextos();
    }

    private void desenharPista() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.25f, 0.25f, 0.25f, 1);
        shapes.rect(0, 0, W, H);
        // Faixas tracejadas descendo
        shapes.setColor(0.9f, 0.9f, 0.9f, 1);
        for (int lane = 1; lane <= 2; lane++) {
            float x = W * lane / 3f - 3;
            for (float y = H + 60 - deslocamentoPista; y > -60; y -= 60) {
                shapes.rect(x, y, 6, 30);
            }
        }
        shapes.end();
    }

    private void desenharObstaculos() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Rectangle o : obstaculos) {
            shapes.setColor(0.85f, 0.2f, 0.1f, 1);
            shapes.rect(o.x, o.y, o.width, o.height);
            shapes.setColor(1f, 0.8f, 0.1f, 1);
            shapes.rect(o.x + 4, o.y + o.height / 2f - 3, o.width - 8, 6);
        }
        shapes.end();
    }

    private void desenharCarro() {
        // Pisca enquanto está invencível
        if (invencivel > 0 && ((int) (invencivel * 10)) % 2 == 0) return;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // rodas
        shapes.setColor(0, 0, 0, 1);
        shapes.rect(carro.x - 4, carro.y + 6, 6, 16);
        shapes.rect(carro.x + carro.width - 2, carro.y + 6, 6, 16);
        shapes.rect(carro.x - 4, carro.y + carro.height - 22, 6, 16);
        shapes.rect(carro.x + carro.width - 2, carro.y + carro.height - 22, 6, 16);
        // carroceria
        shapes.setColor(0.1f, 0.4f, 0.9f, 1);
        shapes.rect(carro.x, carro.y, carro.width, carro.height);
        // vidro
        shapes.setColor(0.7f, 0.9f, 1f, 1);
        shapes.rect(carro.x + 6, carro.y + 30, carro.width - 12, 16);
        shapes.end();
    }

    private void texto(String s, float y, float escala) {
        font.getData().setScale(escala);
        font.draw(batch, s, 0, y, W, Align.center, false);
    }

    private void desenharTextos() {
        batch.begin();
        font.setColor(1, 1, 1, 1);

        switch (estado) {
            case MENU:
                texto("PISTA DO CAOS", 340, 3f);
                texto("Desvie dos obstaculos movendo o carro de um lado pro outro", 270, 1.3f);
                texto("ENTER: iniciar   |   P: pausar   |   R: reiniciar   |   ESC: menu", 230, 1.2f);
                texto("Controle: Arduino (ou setas/A-D para teste)", 195, 1.2f);
                break;
            case JOGANDO:
                hud();
                break;
            case PAUSADO:
                hud();
                texto("PAUSADO", 280, 3f);
                texto("P ou ENTER: continuar   |   R: reiniciar   |   ESC: menu", 230, 1.2f);
                break;
            case FIM_FASE:
                texto("FASE " + (fase + 1) + " COMPLETA!", 330, 2.5f);
                texto("Consistencia: " + Math.round(consistenciaPorFase[fase] * 100) + "%", 270, 1.6f);
                texto("Pontos: " + pontos, 235, 1.6f);
                texto("ENTER: proxima fase", 190, 1.3f);
                break;
            case GAME_OVER:
                texto("FIM DE JOGO", 320, 3f);
                texto("Pontos: " + pontos + "  (fase " + (fase + 1) + ")", 260, 1.6f);
                texto("R: tentar de novo   |   ENTER: menu", 215, 1.3f);
                break;
            case VITORIA:
                texto("VOCE VENCEU!", 360, 3f);
                texto("Pontos: " + pontos, 305, 1.8f);
                for (int i = 0; i < TOTAL_FASES; i++) {
                    texto("Fase " + (i + 1) + " - consistencia: " + Math.round(consistenciaPorFase[i] * 100) + "%",
                            265 - i * 28, 1.4f);
                }
                texto("R: jogar de novo   |   ENTER: menu", 150, 1.3f);
                break;
        }
        font.getData().setScale(1f);
        batch.end();
    }

    private void hud() {
        font.getData().setScale(1.4f);
        font.draw(batch, "Fase " + (fase + 1) + "/" + TOTAL_FASES, 10, H - 10);
        font.draw(batch, "Vidas: " + vidas, 10, H - 35);
        font.draw(batch, "Pontos: " + pontos, W - 150, H - 10);
        font.draw(batch, "Tempo: " + Math.max(0, (int) Math.ceil(DURACAO_FASE - tempoFase)) + "s", W - 150, H - 35);
        int cons = tempoFase > 0 ? Math.round(tempoMovendo / tempoFase * 100f) : 0;
        font.draw(batch, "Consistencia: " + cons + "%", W - 200, H - 60);
    }

    @Override
    public void dispose() {
        // Para a thread e libera a porta serial
        arduinoReader.stopReading();
        try {
            arduinoReader.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
