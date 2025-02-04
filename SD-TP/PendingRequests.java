import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

class PendingRequests {
    private static final String APPROVALS_LOG = "approvals.log";

    /**
     * Adiciona um pedido pendente ao arquivo.
     * 
     * @param clientId ID do cliente que fez o pedido
     * @param requestType Tipo do pedido
     */
    public static synchronized void addRequest(String clientId, String requestType) {
        try (FileWriter fw = new FileWriter(APPROVALS_LOG, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(clientId + ":" + requestType + ":PENDING");
        } catch (IOException e) {
            System.out.println("Erro ao salvar pedido pendente: " + e.getMessage());
        }
    }

    /**
     * Remove um pedido pendente do arquivo.
     * 
     * @param clientId ID do cliente
     * @param requestType Tipo do pedido
     */
    public static synchronized void removeRequest(String clientId, String requestType) {
        File inputFile = new File(APPROVALS_LOG);
        File tempFile = new File("temp_approvals.log");

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(clientId + ":" + requestType)) {
                    writer.println(line);
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao remover pedido pendente: " + e.getMessage());
        }

        if (!inputFile.delete() || !tempFile.renameTo(inputFile)) {
            System.out.println("Erro ao atualizar arquivo de pedidos pendentes.");
        }
    }

    /**
     * Carrega apenas os pedidos pendentes do arquivo.
     * 
     * @return Lista de pedidos pendentes
     */
    public static synchronized List<String> getRequests() {
        List<String> pendingRequests = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(APPROVALS_LOG))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.endsWith(":PENDING")) {
                    pendingRequests.add(line);
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao carregar pedidos pendentes: " + e.getMessage());
        }
        return pendingRequests;
    }
}
