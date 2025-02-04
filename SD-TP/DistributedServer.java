/**
 * Classe que representa um servidor distribuído para comunicação de emergência.
 * O servidor aceita conexões de clientes e permite a troca de mensagens entre eles.
 * Também gera relatórios periódicos sobre o estado dos clientes conectados.
 */
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.time.*;

public class DistributedServer {
    private static final int PORT = 12345;

    /**
     * Método principal que inicia o servidor e gere as conexões dos clientes.
     * Cria um pool de threads para lidar com as conexões dos clientes e um agendador para relatórios periódicos.
     * 
     * @param args Argumentos da linha de comando
     */
    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("=== Servidor de Emergencia Distribuida Iniciado na Porta " + PORT + " ===");

            // Agendar relatórios periódicos a cada 60 segundos
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    generatePeriodicReport();
                } catch (IOException e) {
                    System.out.println("Erro ao gerar relatório periódico: " + e.getMessage());
                }
            }, 0, 60, TimeUnit.SECONDS);

            // Aceitar conexões de clientes
            while (true) {
                Socket clientSocket = serverSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String clientId = in.readLine();
                ClientHandler clientHandler = new ClientHandler(clientSocket, clientId);
                pool.execute(clientHandler);
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }

    /**
     * Gera relatórios periódicos sobre os clientes conectados ao servidor.
     * Regista o estado atual dos clientes e o tempo em que o relatório foi gerado.
     * 
     * @throws IOException Se ocorrer um erro ao escrever o relatório no ficheiro
     */
    private static void generatePeriodicReport() throws IOException {
        try (FileWriter fw = new FileWriter("periodic_reports.log", true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println("Relatório Periódico - " + LocalDateTime.now());
            out.println("Clientes Conectados: " + ClientHandler.clients.keySet());
            out.println("-----------------------------");
        }
    }
}