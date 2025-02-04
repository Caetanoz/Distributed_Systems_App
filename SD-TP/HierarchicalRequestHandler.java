/**
 * Classe que gere as solicitações hierárquicas de aprovação de operações de emergência.
 * Permite aprovar ou negar solicitações com base no nível hierárquico dos utilizadores.
 */
import java.io.*;
import java.util.*;

class HierarchicalRequestHandler {

    private static final String APPROVALS_LOG = "approvals.log";
    /**
     * Aprova ou nega uma solicitação com base no tipo de operação e no nível do utilizador.
     * 
     * @param clientId ID do cliente que está a solicitar a operação
     * @param requestType Tipo de operação solicitada
     * @param userRoles Mapa de níveis hierárquicos dos utilizadores
     * @return Verdadeiro se a solicitação for aprovada, falso caso contrário
     */
    public static boolean approveRequest(String clientId, String requestType, Map<String, Integer> userRoles) {
        Integer level = userRoles.get(clientId);
        boolean approved = false;
        if (level != null) {
            switch (requestType) {
                case "Operacao de Evacuacao em Massa":
                    approved = level == 3; // Apenas nível 3 pode aprovar
                    break;
                case "Ativacao de Comunicacoes de Emergencia":
                    approved = level == 3; // Apenas nível 3 pode aprovar
                    break;
                case "Distribuicao de Recursos de Emergencia":
                    approved = level == 2 || level == 3; // Níveis 2 e 3 podem aprovar
                    break;
                default:
                    approved = false; // Tipo de operação desconhecida
                    break;
            }
        }
        logApproval(clientId, requestType, approved);
        return approved;
    }
    

    /**
     * Regista a aprovação ou negação de uma solicitação no ficheiro de registo.
     * 
     * @param clientId ID do cliente que fez a solicitação
     * @param requestType Tipo de operação solicitada
     * @param approved Verdadeiro se a solicitação foi aprovada, falso se foi negada
     */
    private static void logApproval(String clientId, String requestType, boolean approved) {
        try (FileWriter fw = new FileWriter(APPROVALS_LOG, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println("Cliente: " + clientId + " Solicitacao: " + requestType + " Aprovado: " + approved);
        } catch (IOException e) {
            System.out.println("Erro ao registrar a aprovacao: " + e.getMessage());
        }
    }
}
