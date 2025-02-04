/**
 * Classe que representa um canal de comunicação entre clientes.
 * Permite a criação de canais, adicião e remoção de membros, e a transmissão de mensagens entre os membros.
 */
import java.util.*;
import java.util.concurrent.*;

class CommunicationChannel {
    static final String CHANNELS_FILE = "channels.txt";
    private final String channelId;
    private final Set<String> members = ConcurrentHashMap.newKeySet();

    /**
     * Construtor da classe CommunicationChannel.
     * 
     * @param channelId ID do canal a ser criado
     */
    public CommunicationChannel(String channelId) {
        this.channelId = channelId;
    }

    /**
     * Obtém o ID do canal.
     * 
     * @return ID do canal
     */
    public String getChannelId() {
        return this.channelId;
    }

    /**
     * Adiciona um membro ao canal.
     * 
     * @param clientId ID do cliente a ser adicionado ao canal
     */
    public void addMember(String clientId) {
        members.add(clientId);
    }

    /**
     * Remove um membro do canal.
     * 
     * @param clientId ID do cliente a ser removido do canal
     */
    public void removeMember(String clientId) {
        members.remove(clientId);
    }

    /**
     * Transmite uma mensagem para todos os membros do canal, exceto o remetente.
     * 
     * @param senderId ID do remetente da mensagem
     * @param message Mensagem a ser transmitida
     */
    public void broadcastMessage(String senderId, String message) {
        for (String memberId : members) {
            if (!memberId.equals(senderId)) {
                ClientHandler client = ClientHandler.clients.get(memberId);
                if (client != null) {
                    client.sendMessage(memberId, senderId + " (canal " + channelId + "): " + message);
                }
            }
        }
    }

    /**
     * Verifica se um cliente é membro do canal.
     * 
     * @param clientId ID do cliente
     * @return Verdadeiro se o cliente é membro do canal, falso caso contrário
     */
    public boolean isMember(String clientId) {
        return members.contains(clientId);
    }
}
