/**
 * Classe que gere os canais de comunicação no servidor.
 * Permite adicionar, obter e persistir os canais criados pelos utilizadores.
 */
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

class ServerChannels {
    private static final Map<String, CommunicationChannel> channels = new ConcurrentHashMap<>();

    /**
     * Adiciona um novo canal ao mapa de canais.
     * 
     * @param channel Canal de comunicação a ser adicionado
     */
    public static void addChannel(CommunicationChannel channel) {
        channels.put(channel.getChannelId(), channel);
    }

    /**
     * Obtém um canal com base no ID do canal.
     * 
     * @param channelId ID do canal
     * @return Canal de comunicação correspondente ao ID fornecido, ou null se não existir
     */
    public static CommunicationChannel getChannel(String channelId) {
        return channels.get(channelId);
    }

    /**
     * Persiste o ID do canal no ficheiro de registo de canais.
     * 
     * @param channelId ID do canal a ser salvo
     */
    public static void saveChannelToFile(String channelId) {
        try (FileWriter fw = new FileWriter(CommunicationChannel.CHANNELS_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(channelId);
        } catch (IOException e) {
            System.out.println("Erro ao salvar o canal: " + e.getMessage());
        }
    }

    public static Collection<CommunicationChannel> getChannels() {
        return channels.values();
    }

    /**
     * Carrega os canais do ficheiro de registo e adiciona-os ao mapa de canais.
     */
    public static void loadChannelsFromFile() {
        try (BufferedReader br = new BufferedReader(new FileReader(CommunicationChannel.CHANNELS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                CommunicationChannel channel = new CommunicationChannel(line);
                channels.put(line, channel);
            }
        } catch (IOException e) {
            System.out.println("Erro ao carregar os canais: " + e.getMessage());
        }
    }
}