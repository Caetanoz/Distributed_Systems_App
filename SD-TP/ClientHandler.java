/**
 * Classe que gere o tratamento de clientes conectados ao sistema de comunicação.
 * Implementa a interface Runnable para que cada cliente possa ser gerido numa thread separada.
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

class ClientHandler implements Runnable {
    
    private final Socket clientSocket;
    private final String clientId;
    protected static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final Map<String, String> userCredentials = new ConcurrentHashMap<>();
    private static final Map<String, Integer> userRoles = new ConcurrentHashMap<>();
    private static final String USERS_FILE = "users.txt";
    private static final String MESSAGES_FILE = "messages.log";
    private static final String USER_CHANNEL_MEMBERSHIP_FILE = "user_channel_membership.log";

    /**
     * Obtém os canais associados a um utilizador.
     * 
     * @param userId ID do utilizador
     * @return Conjunto de canais em que o utilizador participa
     */
    private static Set<String> getUserChannels(String userId) {
        Set<String> channels = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(USER_CHANNEL_MEMBERSHIP_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length >= 3 && parts[1].equals("entrou") && parts[0].equals(userId)) {
                    channels.add(parts[2]);
                } else if (parts.length >= 3 && parts[1].equals("saiu") && parts[0].equals(userId)) {
                    channels.remove(parts[2]);
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao ler os canais do utilizador: " + e.getMessage());
        }
        return channels;
    }

    /**
     * Verifica se um utilizador está num canal.
     * 
     * @param line Linha de texto contendo a informação do canal
     * @param userChannels Conjunto de canais do utilizador
     * @return Verdadeiro se o utilizador está no canal, falso caso contrário
     */
    private static boolean isUserInChannel(String line, Set<String> userChannels) {
        for (String channel : userChannels) {
            if (line.matches(".*Canal: " + channel + "(\\b|[^\\d].*)")) {
                return true;
            }
        }
        return false;
    }

    static {
        loadUsersFromFile();
        ServerChannels.loadChannelsFromFile();
    }

    /**
     * Construtor da classe ClientHandler.
     * 
     * @param socket Socket associado ao cliente
     * @param clientId ID do cliente
     */
    public ClientHandler(Socket socket, String clientId) {
        this.clientSocket = socket;
        this.clientId = clientId;
    }

    /**
     * Método que corre numa thread separada para gerir as interações do cliente.
     */
    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            out.println("=== Bem-vindo ao Sistema de Emergência ===");
            out.println("Por favor, insira a sua senha:");
            String password = in.readLine();
            if (!authenticate(clientId, password)) {
                out.println("Autenticação falhou. Conexão encerrada.");
                return;
            }

            clients.put(clientId, this);
            out.println("Autenticação bem-sucedida! Bem-vindo, " + clientId + "!");
            out.println("Digite 'ajuda' para ver os comandos disponíveis.");

            String message;
            while ((message = in.readLine()) != null) {
                handleClientMessage(message, out);
            }
        } catch (IOException e) {
            System.out.println("Erro no cliente " + clientId + ": " + e.getMessage());
        } finally {
            clients.remove(clientId);
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Gere as mensagens enviadas pelo cliente.
     * 
     * @param message Mensagem enviada pelo cliente
     * @param out PrintWriter para responder ao cliente
     */
    private void handleClientMessage(String message, PrintWriter out) {
        try {
            switch (message.toLowerCase()) {
                case "ajuda":
                    out.println("Comandos disponíveis:");
                    out.println("1. mensagem:<destinatário>:<mensagem> - Enviar mensagem para um destinatário específico");
                    out.println("2. canal:<canal_id>:<mensagem> - Enviar mensagem para um canal específico");
                    out.println("3. criar canal:<canal_id> - Criar um novo canal de comunicação");
                    out.println("4. entrar canal:<canal_id> - Entrar num canal de comunicação existente");
                    out.println("5. sair canal:<canal_id> - Sair de um canal de comunicação");
                    out.println("6. solicitar:<tipo_operação> - Solicitar aprovação para uma operação específica");
                    out.println("7. aprovar - Aprovar solicitações pendentes");
                    out.println("8. ler mensagens - Ler todas as mensagens enviadas anteriormente");
                    out.println("9. criar_user:<nome_user>:<senha>:<perfil> - Criar um novo utilizador");
                    out.println("10. mensagem emergencia:<mensagem> - Enviar uma mensagem de emergência para todos os clientes (somente Administrador)");
                    out.println("0. sair - Desconectar do sistema");
                    break;
                case "sair":
                    out.println("Desconectando...");
                    clients.remove(clientId);
                    clientSocket.close();
                    break;
                default:
                    if (message.startsWith("mensagem:")) {
                        handleSendMessage(message, out);
                    } else if (message.startsWith("criar canal:")) {
                        handleCreateChannel(message, out);
                    } else if (message.startsWith("entrar canal:")) {
                        handleJoinChannel(message, out);
                    } else if (message.startsWith("sair canal:")) {
                        handleLeaveChannel(message, out);
                    } else if (message.startsWith("canal:")) {
                        handleBroadcastChannel(message, out);
                    } else if (message.startsWith("solicitar:")) {
                        handleRequestApproval(message, out);
                    } else if (message.equalsIgnoreCase("aprovar")) {
                        handleApproveRequests(out);
                    } else if (message.equalsIgnoreCase("ler mensagens")) {
                        handleReadMessages(out);
                    } else if (message.startsWith("criar_user:")) {
                        handleCreateUser(message, out);
                    } else if (message.startsWith("mensagem emergencia:")) {
                        handleEmergencyMessage(message, out);
                    } else {
                        out.println("Comando não reconhecido. Digite 'ajuda' para ver os comandos disponíveis.");
                    }
                    break;
            }
        } catch (IOException e) {
            out.println("Erro ao processar o comando: " + e.getMessage());
        }
    }

    /**
     * Envia uma mensagem de emergência para todos os utilizadores e canais.
     * 
     * @param message Mensagem contendo o comando de emergência
     * @param out PrintWriter para responder ao cliente
     */
    private void handleEmergencyMessage(String message, PrintWriter out) {
        if (userRoles.getOrDefault(clientId, -1) != 3) { // Apenas Administradores (nivel 3)
            out.println("Você não tem permissão para enviar mensagens de emergência.");
            return;
        }

        String[] parts = message.split(":", 2);
        if (parts.length == 2) {
            String emergencyMessage = parts[1];

            // Enviar mensagem para todos os clientes conectados
            for (ClientHandler client : clients.values()) {
                try {
                    PrintWriter clientOut = new PrintWriter(client.clientSocket.getOutputStream(), true);
                    clientOut.println("[EMERGÊNCIA] Mensagem de " + clientId + ": " + emergencyMessage);
                } catch (IOException e) {
                    System.out.println("Erro ao enviar mensagem de emergência para " + client.clientId + ": " + e.getMessage());
                }
            }

            // Enviar mensagem para todos os canais
            for (CommunicationChannel channel : ServerChannels.getChannels()) {
                channel.broadcastMessage(clientId, "[EMERGÊNCIA] " + emergencyMessage);
                logBroadcastMessage(channel.getChannelId(), clientId, "[EMERGÊNCIA] " + emergencyMessage);
            }

            out.println("Mensagem de emergência enviada com sucesso.");
        } else {
            out.println("Formato inválido. Use: mensagem_emergencia:<mensagem>");
        }
    }

    /**
     * Envia uma mensagem para outro cliente.
     * 
     * @param targetClientId ID do destinatário
     * @param message Mensagem a enviar
     */
    protected synchronized void sendMessage(String targetClientId, String message) {
        ClientHandler targetClient = clients.get(targetClientId);
        if (targetClient != null) {
            try {
                PrintWriter out = new PrintWriter(targetClient.clientSocket.getOutputStream(), true);
                out.println("Mensagem recebida de " + clientId + ": " + message);
            } catch (IOException e) {
                System.out.println("Erro ao enviar mensagem para " + targetClientId + ": " + e.getMessage());
            }
        } else {
            System.out.println("Cliente não encontrado: " + targetClientId);
        }
    }

    /**
     * Verifica a autenticidade de um utilizador.
     * 
     * @param clientId ID do cliente
     * @param password Senha fornecida
     * @return Verdadeiro se a autenticação for bem-sucedida, falso caso contrário
     */
    private boolean authenticate(String clientId, String password) {
        return userCredentials.containsKey(clientId) && userCredentials.get(clientId).equals(password);
    }

    /**
     * Gere o envio de uma mensagem para um destinatário específico.
     * 
     * @param message Mensagem contendo o comando para enviar uma mensagem
     * @param out PrintWriter para responder ao cliente
     * @throws IOException Se ocorrer um erro ao processar o comando
     */
    private void handleSendMessage(String message, PrintWriter out) throws IOException {
        String[] parts = message.split(":", 3);
        if (parts.length == 3) {
            String targetClientId = parts[1];
            String msgContent = parts[2];
            sendMessage(targetClientId, clientId + ": " + msgContent);
            logMessage(clientId, targetClientId, msgContent);
        } else {
            out.println("Formato inválido. Use: mensagem:<destinatário>:<mensagem>");
        }
    }

    /**
     * Regista uma mensagem enviada para um destinatário.
     * 
     * @param senderId ID do remetente
     * @param receiverId ID do destinatário
     * @param message Mensagem enviada
     */
    private void logMessage(String senderId, String receiverId, String message) {
        try (FileWriter fw = new FileWriter(MESSAGES_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println("De: " + senderId + " Para: " + receiverId + " Mensagem: " + message);
        } catch (IOException e) {
            System.out.println("Erro ao registrar a mensagem: " + e.getMessage());
        }
    }

    /**
     * Cria um canal de comunicação.
     * 
     * @param message Mensagem contendo o comando para criar um canal
     * @param out PrintWriter para responder ao cliente
     * @throws IOException Se ocorrer um erro ao processar o comando
     */
    private void handleCreateChannel(String message, PrintWriter out) throws IOException {
        String[] parts = message.split(":", 2);
        if (parts.length == 2) {
            String channelId = parts[1];
            CommunicationChannel channel = new CommunicationChannel(channelId);
            ServerChannels.addChannel(channel);
            ServerChannels.saveChannelToFile(channelId);
            out.println("Canal " + channelId + " criado com sucesso!");
        } else {
            out.println("Formato inválido. Use: criar canal:<canal_id>");
        }
    }

    /**
     * Gere o pedido para um cliente entrar num canal.
     * 
     * @param message Mensagem contendo o comando para entrar num canal
     * @param out PrintWriter para responder ao cliente
     */
    private void handleJoinChannel(String message, PrintWriter out) {
        String[] parts = message.split(":", 2);
        if (parts.length == 2) {
            String channelId = parts[1];
            Set<String> userChannels = getUserChannels(clientId);
            if (userChannels.contains(channelId)) {
                out.println("Você já está no canal " + channelId);
                return;
            }
            CommunicationChannel channel = ServerChannels.getChannel(channelId);
            if (channel != null) {
                logUserChannelMembership(clientId, "entrou", channelId);
                channel.addMember(clientId);
                out.println("Você entrou no canal " + channelId);
            } else {
                out.println("Canal " + channelId + " não encontrado.");
            }
        } else {
            out.println("Formato inválido. Use: entrar canal:<canal_id>");
        }
    }

    /**
     * Gere o pedido para um cliente sair de um canal.
     * 
     * @param message Mensagem contendo o comando para sair de um canal
     * @param out PrintWriter para responder ao cliente
     */
    private void handleLeaveChannel(String message, PrintWriter out) {
        String[] parts = message.split(":", 2);
        if (parts.length == 2) {
            String channelId = parts[1];
            Set<String> userChannels = getUserChannels(clientId);
            if (!userChannels.contains(channelId)) {
                out.println("Você não está no canal " + channelId);
                return;
            }
            CommunicationChannel channel = ServerChannels.getChannel(channelId);
            if (channel != null) {
                channel.removeMember(clientId);
                logUserChannelMembership(clientId, "saiu", channelId);
                out.println("Você saiu do canal " + channelId);
            } else {
                out.println("Canal " + channelId + " não encontrado.");
            }
        } else {
            out.println("Formato inválido. Use: sair canal:<canal_id>");
        }
    }

    /**
     * Envia uma mensagem para um canal.
     * 
     * @param message Mensagem contendo o comando para enviar para um canal
     * @param out PrintWriter para responder ao cliente
     */
    private void handleBroadcastChannel(String message, PrintWriter out) {
        String[] parts = message.split(":", 3);
        if (parts.length == 3) {
            String channelId = parts[1];
            String msgContent = parts[2];
            CommunicationChannel channel = ServerChannels.getChannel(channelId);
            if (channel != null) {
                if (channel.isMember(clientId)) {
                    channel.broadcastMessage(clientId, msgContent);
                    logBroadcastMessage(channelId, clientId, msgContent);
                } else {
                    out.println("Você precisa entrar no canal " + channelId + " antes de enviar mensagens.");
                }
            } else {
                out.println("Canal " + channelId + " não encontrado.");
            }
        } else {
            out.println("Formato inválido. Use: canal:<canal_id>:<mensagem>");
        }
    }

    /**
     * Solicita a aprovação para uma operação específica.
     * 
     * @param message Mensagem contendo o pedido de solicitação
     * @param out PrintWriter para responder ao cliente
     */
    private void handleRequestApproval(String message, PrintWriter out) {
        String[] parts = message.split(":", 2);
        if (parts.length == 2) {
            String requestType = parts[1];
            Integer userRole = userRoles.get(clientId);
    
            if (userRole == null) {
                out.println("Erro: Cargo do utilizador não encontrado.");
                return;
            }
    
            // Verificar se o cargo permite a solicitação
            boolean canRequest = false;
            switch (requestType) {
                case "DRE": // Distribuição de Recursos de Emergência
                    canRequest = userRole >= 0; // Todos os cargos podem solicitar
                    break;
                case "ACE": // Ativação de Comunicações de Emergência
                    canRequest = userRole >= 1; // Cargo 1 ou superior
                    break;
                case "OEM": // Operação de Evacuação em Massa
                    canRequest = userRole >= 2; // Cargo 2 ou superior
                    break;
                default:
                    out.println("Tipo de operação inválido.");
                    return;
            }
    
            if (canRequest) {
                PendingRequests.addRequest(clientId, requestType); // Salvar como pendente
                out.println("Sua solicitação de " + requestType + " foi registrada e está aguardando aprovação.");
            } else {
                out.println("Você não possui permissão para solicitar " + requestType + ".");
            }
        } else {
            out.println("Formato inválido. Use: solicitar:<tipo_operação>");
        }
    }
    

    private void handleApproveRequests(PrintWriter out) {
        List<String> requests = PendingRequests.getRequests();
        if (requests.isEmpty()) {
            out.println("Nenhuma solicitação pendente no momento.");
            return;
        }
    
        out.println("Solicitações pendentes:");
        for (int i = 0; i < requests.size(); i++) {
            String[] details = requests.get(i).split(":");
            out.println((i + 1) + ". Cliente: " + details[0] + " | Tipo: " + details[1]);
        }
    
        out.println("Digite o número da solicitação para aprovar ou 'cancelar' para voltar ao menu.");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            String input = in.readLine();
            if (input.equalsIgnoreCase("cancelar")) {
                out.println("Retornando ao menu principal.");
                return;
            }
    
            int choice = Integer.parseInt(input) - 1;
            if (choice >= 0 && choice < requests.size()) {
                String[] requestDetails = requests.get(choice).split(":");
                String requestClientId = requestDetails[0];
                String requestType = requestDetails[1];
                Integer userRole = userRoles.get(clientId);
    
                if (userRole == null) {
                    out.println("Erro: Cargo do utilizador não encontrado.");
                    return;
                }
    
                // Verificar se o cargo permite aprovar a operação
                boolean canApprove = false;
                switch (requestType) {
                    case "DRE": // Distribuição de Recursos de Emergência
                        canApprove = userRole >= 1; // Cargo 1 ou superior pode aprovar
                        break;
                    case "ACE": // Ativação de Comunicações de Emergência
                        canApprove = userRole >= 2; // Cargo 2 ou superior pode aprovar
                        break;
                    case "OEM": // Operação de Evacuação em Massa
                        canApprove = userRole >= 3; // Apenas Cargo 3 pode aprovar
                        break;
                    default:
                        out.println("Tipo de operação inválido.");
                        return;
                }
    
                if (canApprove) {
                    out.println("Solicitação de " + requestType + " aprovada!");
                    PendingRequests.removeRequest(requestClientId, requestType); // Remove do arquivo
                } else {
                    out.println("Você não possui permissão para aprovar a solicitação de " + requestType + ".");
                }
            } else {
                out.println("Escolha inválida.");
            }
        } catch (IOException | NumberFormatException e) {
            out.println("Erro ao processar o comando. Tente novamente.");
        }
    }

    /**
     * Lê todas as mensagens enviadas anteriormente.
     * 
     * @param out PrintWriter para responder ao cliente
     */
    private void handleReadMessages(PrintWriter out) {
        try (BufferedReader br = new BufferedReader(new FileReader(MESSAGES_FILE))) {
            String line;
            Set<String> userChannels = getUserChannels(clientId);
            while ((line = br.readLine()) != null) {
                if ((line.contains("Para: " + clientId) || (line.contains("Canal: ") && isUserInChannel(line, userChannels)))) {
                    out.println(line);
                }
            }
        } catch (IOException e) {
            out.println("Erro ao ler as mensagens: " + e.getMessage());
        }
    }

    /**
     * Cria um novo utilizador.
     * 
     * @param message Mensagem contendo o comando para criar um utilizador
     * @param out PrintWriter para responder ao cliente
     */
    private void handleCreateUser(String message, PrintWriter out) {
        String[] parts = message.split(":", 4);
        if (parts.length == 4) {
            String newUserId = parts[1];
            String newPassword = parts[2];
            String newProfile = parts[3];
            if (!userCredentials.containsKey(newUserId)) {
                userCredentials.put(newUserId, newPassword);
                int level = 3; // Nível padrão para novos utilizadores
    
                // Adicionamos a lógica para identificar o perfil "todos"
                if ("todos".equalsIgnoreCase(newProfile)) {
                    level = 0; // Cargo 0
                } else if ("Coordenador Regional".equalsIgnoreCase(newProfile)) {
                    level = 1;
                } else if ("Operador de Nivel Medio".equalsIgnoreCase(newProfile)) {
                    level = 2;
                } else if ("Administrador".equalsIgnoreCase(newProfile)) {
                    level = 3;
                } else {
                    out.println("Perfil inválido. Use: 'todos', 'Coordenador Regional', 'Operador de Nivel Medio', ou 'Administrador'.");
                    return;
                }
    
                userRoles.put(newUserId, level);
                saveUserToFile(newUserId, newPassword, newProfile); // Salva no arquivo
                out.println("Utilizador " + newUserId + " criado com sucesso com cargo: " + newProfile + "!");
            } else {
                out.println("Utilizador já existe.");
            }
        } else {
            out.println("Formato inválido. Use: criar_user:<nome_user>:<senha>:<perfil>");
        }
    }
    

    /**
     * Regista um utilizador no ficheiro.
     * 
     * @param userId ID do utilizador
     * @param password Senha do utilizador
     * @param profile Perfil do utilizador
     */
    private static void saveUserToFile(String userId, String password, String profile) {
        try (FileWriter fw = new FileWriter(USERS_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(userId + ":" + password + ":" + profile);
        } catch (IOException e) {
            System.out.println("Erro ao salvar o utilizador: " + e.getMessage());
        }
    }

    /**
     * Carrega utilizadores do ficheiro.
     */
    private static void loadUsersFromFile() {
        try (BufferedReader br = new BufferedReader(new FileReader(USERS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":", 3);
                if (parts.length == 3) {
                    String userId = parts[0];
                    String password = parts[1];
                    String profile = parts[2];
                    userCredentials.put(userId, password);
                    int level = 3;
                    if ("Coordenador Regional".equals(profile)) {
                        level = 1;
                    } else if ("Operador de Nivel Medio".equals(profile)) {
                        level = 2;
                    }
                    userRoles.put(userId, level);
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao carregar os utilizadores: " + e.getMessage());
        }
    }

    /**
     * Regista a associação de um utilizador a um canal.
     * 
     * @param clientId ID do cliente
     * @param action Ação realizada ("entrou" ou "saiu")
     * @param channelId ID do canal
     */
    private static void logUserChannelMembership(String clientId, String action, String channelId) {
        try (FileWriter fw = new FileWriter(USER_CHANNEL_MEMBERSHIP_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(clientId + " " + action + " " + channelId);
        } catch (IOException e) {
            System.out.println("Erro ao registrar a associação do utilizador ao canal: " + e.getMessage());
        }
    }

    /**
     * Regista uma mensagem enviada para um canal.
     * 
     * @param channelId ID do canal
     * @param senderId ID do remetente
     * @param message Mensagem enviada
     */
    private static void logBroadcastMessage(String channelId, String senderId, String message) {
        try (FileWriter fw = new FileWriter(MESSAGES_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println("Canal: " + channelId + " De: " + senderId + " Mensagem: " + message);
        } catch (IOException e) {
            System.out.println("Erro ao registrar a mensagem do canal: " + e.getMessage());
        }
    }
}