/**
 * Classe que representa um cliente distribuído que se conecta a um servidor.
 * O cliente comunica-se com o servidor através de sockets e permite a troca de mensagens.
 */
import java.io.*;
import java.net.*;
import java.util.*;

class DistributedClient {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    /**
     * Construtor da classe DistributedClient.
     * 
     * @param address Endereço do servidor ao qual o cliente deve se conectar
     * @param port Porta do servidor ao qual o cliente deve se conectar
     * @throws IOException Se ocorrer um erro ao estabelecer a conexão com o servidor
     */
    public DistributedClient(String address, int port) throws IOException {
        this.socket = new Socket(address, port);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
        System.out.println("Por favor, insira seu nome de utilizador:");
        @SuppressWarnings("resource")
        String inputClientId = new Scanner(System.in).nextLine();
        this.out.println(inputClientId);
    }

    /**
     * Inicia a comunicação com o servidor.
     * Cria uma nova thread para receber mensagens do servidor e permite que o cliente envie mensagens.
     */
    public void start() {
        new Thread(() -> {
            try {
                String serverResponse;
                while ((serverResponse = in.readLine()) != null) {
                    System.out.println(serverResponse);
                }
            } catch (IOException e) {
                System.out.println("Desconectado do servidor: " + e.getMessage());
            }
        }).start();

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Insira sua senha para autenticacao:");
            String password = scanner.nextLine();
            out.println(password);

            System.out.println("Digite 'ajuda' para ver os comandos disponiveis.");
            while (true) {
                String message = scanner.nextLine();
                out.println(message);
            }
        }
    }

    /**
     * Método principal que cria um cliente e inicia a comunicação com o servidor.
     * 
     * @param args Argumentos da linha de comando
     */
    public static void main(String[] args) {
        try {
            DistributedClient client = new DistributedClient("localhost", 12345);
            client.start();
        } catch (IOException e) {
            System.err.println("Erro ao conectar ao servidor: " + e.getMessage());
        }
    }
}